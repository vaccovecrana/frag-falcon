#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/resource.h>
#include <sys/wait.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

extern char **environ;

/* Static storage so no allocation happens between fork() and execve() in a
 * multi-threaded host process. */
static char ff_ld_env[8192];

/*
 * Forks a detached VM launcher process (double-fork / daemonize).
 *
 * A single fork() + setsid() leaves the launcher a *descendant* of the host
 * JVM, so a process-tree teardown on the caller side (e.g. Gradle's `run` task
 * killing the app JVM and ProcessHandle.descendants() on cancellation) also
 * reaps the launcher — even though setsid() moved it to its own session. A
 * double fork reparents the launcher to init/subreaper immediately, removing it
 * from the host's descendant tree so it survives the host going away.
 *
 * The intermediate child reports the grandchild's real pid back over a pipe and
 * then exits at once, so the grandchild (the launcher) is reparented. The
 * returned pid is therefore the launcher's, not the intermediate's.
 *
 * The launcher has stdin from /dev/null, stdout/stderr redirected to log_path,
 * and gets LD_LIBRARY_PATH set so it can resolve the vendored libkrun shared
 * objects. The VM id is passed to the launcher as a command-line argument, not
 * via the environment.
 *
 * Note: because the launcher is reparented, the host cannot waitpid() on it.
 * Guest exit is observed through /proc/<pid>/comm discovery (FgProc.pidOf); the
 * host never blocks on a launcher.
 */
int spawn_process(const char *cmd, char **argv,
                  const char *log_path, const char *ld_library_path) {
    int pipefd[2];
    if (pipe(pipefd) != 0) {
        return -1;
    }

    pid_t mid = fork();
    if (mid == -1) {
        close(pipefd[0]);
        close(pipefd[1]);
        return -1;
    }

    if (mid > 0) {
        /* Parent: read the launcher pid, then reap the intermediate. */
        close(pipefd[1]);
        pid_t child_pid = -1;
        ssize_t n = read(pipefd[0], &child_pid, sizeof(child_pid));
        close(pipefd[0]);
        waitpid(mid, NULL, 0);
        if (n != (ssize_t) sizeof(child_pid)) {
            return -1;
        }
        return child_pid;
    }

    /* Intermediate child. */
    close(pipefd[0]);
    if (setsid() == -1) {
        perror("setsid");
        _exit(EXIT_FAILURE);
    }

    pid_t child = fork();
    if (child == -1) {
        _exit(EXIT_FAILURE);
    } else if (child > 0) {
        /* Report the grandchild pid, then exit so the grandchild is reparented. */
        ssize_t ignored = write(pipefd[1], &child, sizeof(child));
        (void) ignored;
        close(pipefd[1]);
        _exit(EXIT_SUCCESS);
    }

    /* Grandchild: becomes the detached launcher. */
    close(pipefd[1]);

    int devnull = open("/dev/null", O_RDONLY);
    if (devnull != -1) {
        dup2(devnull, STDIN_FILENO);
    }

    if (log_path != NULL) {
        int log_fd = open(log_path, O_WRONLY | O_CREAT | O_APPEND, 0644);
        if (log_fd != -1) {
            dup2(log_fd, STDOUT_FILENO);
            dup2(log_fd, STDERR_FILENO);
            if (log_fd > STDERR_FILENO) {
                close(log_fd);
            }
        } else {
            perror("Failed to open log file");
            _exit(EXIT_FAILURE);
        }
    }

    struct rlimit rlim;
    if (getrlimit(RLIMIT_NOFILE, &rlim) == 0) {
        for (int fd = 3; fd < (int) rlim.rlim_max; fd++) {
            close(fd);
        }
    }

    if (ld_library_path != NULL) {
        snprintf(ff_ld_env, sizeof(ff_ld_env), "LD_LIBRARY_PATH=%s", ld_library_path);
        putenv(ff_ld_env);
    }

    execve(cmd, argv, environ);
    perror("execve");
    _exit(EXIT_FAILURE);
}

int terminate_process(pid_t pid) {
    return kill(pid, SIGTERM);
}

/* Reaps any exited children (non-blocking). Returns the number reaped. */
int reap_children(void) {
    int status;
    int reaped = 0;
    while (waitpid(-1, &status, WNOHANG) > 0) {
        reaped++;
    }
    return reaped;
}
