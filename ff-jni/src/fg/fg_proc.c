#define _GNU_SOURCE

#include <unistd.h>
#include <fcntl.h>
#include <spawn.h>
#include <stdint.h>
#include <pthread.h>
#include <sys/types.h>
#include <sys/resource.h>
#include <sys/wait.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

extern char **environ;

/*
 * Reaps the posix_spawn intermediate once it exits. The launcher double-forks
 * and the intermediate _exit()s almost immediately, so it would otherwise
 * linger as a zombie of the host JVM (the JDK only waitpid()s its own tracked
 * children). Runs detached, on its own stack, so the caller never blocks.
 */
static void *reap_child(void *arg) {
    pid_t pid = (pid_t)(intptr_t) arg;
    int status;
    while (waitpid(pid, &status, 0) < 0 && errno == EINTR) {
    }
    return NULL;
}

/*
 * Spawns a VM launcher process.
 *
 * Uses posix_spawn (which the C library implements with vfork/clone) rather
 * than a hand-rolled fork(). A raw fork() in the multithreaded host JVM may be
 * followed only by async-signal-safe calls and can corrupt JVM-internal state;
 * posix_spawn performs the process creation and exec atomically on our behalf.
 *
 * The launcher detaches itself (double fork) in its own main(), which is
 * single-threaded and therefore safe. This function builds argv/envp entirely
 * in the parent, redirects stdin from /dev/null and stdout/stderr to log_path,
 * and injects LD_LIBRARY_PATH through the child environment. No pid is
 * returned: the launcher is reparented to init and is discovered via
 * /proc/<pid>/comm. The posix_spawn intermediate is reaped by a detached
 * native thread.
 *
 * Returns 0 on success, -1 on failure.
 */
int spawn_process(const char *cmd, char **argv,
                  const char *log_path, const char *ld_library_path) {
    posix_spawn_file_actions_t actions;
    int log_fd = -1;
    if (posix_spawn_file_actions_init(&actions) != 0) {
        return -1;
    }

    int devnull = open("/dev/null", O_RDONLY);
    if (devnull < 0) {
        posix_spawn_file_actions_destroy(&actions);
        return -1;
    }
    posix_spawn_file_actions_adddup2(&actions, devnull, STDIN_FILENO);
    posix_spawn_file_actions_addclose(&actions, devnull);

    if (log_path != NULL) {
        log_fd = open(log_path, O_WRONLY | O_CREAT | O_APPEND, 0644);
        if (log_fd < 0) {
            close(devnull);
            posix_spawn_file_actions_destroy(&actions);
            return -1;
        }
        posix_spawn_file_actions_adddup2(&actions, log_fd, STDOUT_FILENO);
        posix_spawn_file_actions_adddup2(&actions, log_fd, STDERR_FILENO);
        posix_spawn_file_actions_addclose(&actions, log_fd);
    }

    posix_spawn_file_actions_addclosefrom_np(&actions, 3);

    int i;
    int env_n = 0;
    while (environ[env_n] != NULL) {
        env_n++;
    }
    int env_cap = env_n + 2;
    char **envp = calloc((size_t) env_cap, sizeof(char *));
    if (envp == NULL) {
        if (log_fd >= 0) {
            close(log_fd);
        }
        close(devnull);
        posix_spawn_file_actions_destroy(&actions);
        return -1;
    }
    int env_i = 0;
    for (i = 0; i < env_n; i++) {
        if (ld_library_path != NULL && strncmp(environ[i], "LD_LIBRARY_PATH=", 16) == 0) {
            continue;
        }
        envp[env_i++] = environ[i];
    }
    char *ld_env = NULL;
    if (ld_library_path != NULL) {
        size_t n = strlen("LD_LIBRARY_PATH=") + strlen(ld_library_path) + 1;
        ld_env = malloc(n);
        if (ld_env == NULL) {
            free(envp);
            if (log_fd >= 0) {
                close(log_fd);
            }
            close(devnull);
            posix_spawn_file_actions_destroy(&actions);
            return -1;
        }
        snprintf(ld_env, n, "LD_LIBRARY_PATH=%s", ld_library_path);
        envp[env_i++] = ld_env;
    }
    envp[env_i] = NULL;

    pid_t pid;
    int rc = posix_spawn(&pid, cmd, &actions, NULL, argv, envp);

    free(ld_env);
    free(envp);
    if (log_fd >= 0) {
        close(log_fd);
    }
    close(devnull);
    posix_spawn_file_actions_destroy(&actions);

    if (rc == 0) {
        pthread_t reaper;
        if (pthread_create(&reaper, NULL, reap_child, (void *)(intptr_t) pid) == 0) {
            pthread_detach(reaper);
        } else {
            waitpid(pid, NULL, WNOHANG);
        }
    }

    return rc == 0 ? 0 : -1;
}

int terminate_process(pid_t pid) {
    return kill(pid, SIGTERM);
}
