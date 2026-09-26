#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <limits.h>
#include <dirent.h>
#include <sys/resource.h>

extern char **environ;

int spawn_process(const char *vm_id, const char *cmd, char **argv, const char *log_path) {
    pid_t pid = fork();
    if (pid == -1) {
        return -1;
    } else if (pid == 0) {
        if (setsid() == -1) {
            perror("setsid");
            exit(EXIT_FAILURE);
        }
        if (log_path != NULL) {
            int log_fd = open(log_path, O_WRONLY | O_CREAT | O_APPEND, 0644);
            if (log_fd != -1) {
                dup2(log_fd, STDIN_FILENO);
                dup2(log_fd, STDOUT_FILENO);
                dup2(log_fd, STDERR_FILENO);
                close(log_fd); // No longer needed after duplication
            } else {
                perror("Failed to open log file");
                exit(EXIT_FAILURE);
            }
        }

        struct rlimit rlim;
        if (getrlimit(RLIMIT_NOFILE, &rlim) == 0) {
            for (int fd = 3; fd < rlim.rlim_max; fd++) {
                close(fd);
            }
        }

        char env_var[256];
        snprintf(env_var, sizeof(env_var), "FF_VMID=%s", vm_id);
        putenv(env_var);

        execve(cmd, argv, environ);
        perror("execve"); // execve only returns on error
        exit(EXIT_FAILURE);
    } else {
        // Parent process
        return pid;
    }
}

int terminate_process(pid_t pid) {
    return kill(pid, SIGTERM);
}
