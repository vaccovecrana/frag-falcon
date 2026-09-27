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
 * Forks a detached VM launcher process.
 *
 * The child has stdin from /dev/null and stdout/stderr redirected to log_path,
 * and gets LD_LIBRARY_PATH set so it can resolve the vendored libkrun shared
 * objects. The VM id is passed to the launcher as a command-line argument, not
 * via the environment.
 */
int spawn_process(const char *cmd, char **argv,
                  const char *log_path, const char *ld_library_path) {
    pid_t pid = fork();
    if (pid == -1) {
        return -1;
    } else if (pid == 0) {
        if (setsid() == -1) {
            perror("setsid");
            exit(EXIT_FAILURE);
        }

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
                exit(EXIT_FAILURE);
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
        exit(EXIT_FAILURE);
    } else {
        return pid;
    }
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
