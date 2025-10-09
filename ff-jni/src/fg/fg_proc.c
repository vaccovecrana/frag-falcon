#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <limits.h>
#include <dirent.h>
#include <sys/resource.h>
#include <sys/mman.h>

extern char **environ;

#define MAX_LINE_LEN 4096
#define SHM_PREFIX "/ff_log_"

typedef struct {
    char **lines;
    int capacity;
    int count;
    int head;
} CircularBuffer;

static void buf_init(CircularBuffer *buf, int capacity) {
    buf->lines = malloc(capacity * sizeof(char *));
    if (buf->lines == NULL) {
        perror("malloc");
        exit(EXIT_FAILURE);
    }
    buf->capacity = capacity;
    buf->count = 0;
    buf->head = 0;
}

static void buf_free(CircularBuffer *buf) {
    for (int i = 0; i < buf->count; i++) {
        free(buf->lines[(buf->head + i) % buf->capacity]);
    }
    free(buf->lines);
}

static void buf_add(CircularBuffer *buf, const char *line) {
    char *copy = strdup(line);
    if (copy == NULL) {
        perror("strdup");
        return;
    }
    if (buf->count == buf->capacity) {
        free(buf->lines[buf->head]);
        buf->lines[buf->head] = copy;
        buf->head = (buf->head + 1) % buf->capacity;
    } else {
        buf->lines[(buf->head + buf->count) % buf->capacity] = copy;
        buf->count++;
    }
}

static size_t buf_dump_size(CircularBuffer *buf) {
    size_t size = 0;
    for (int i = 0; i < buf->count; i++) {
        size += strlen(buf->lines[(buf->head + i) % buf->capacity]);
    }
    return size;
}

static void buf_dump_to_mem(CircularBuffer *buf, char *mem) {
    char *ptr = mem;
    for (int i = 0; i < buf->count; i++) {
        const char *line = buf->lines[(buf->head + i) % buf->capacity];
        size_t len = strlen(line);
        memcpy(ptr, line, len);
        ptr += len;
    }
    *ptr = '\0'; // Null-terminate for safety
}

int spawn_process(const char *vm_id, const char *cmd, char **argv, int max_lines, char *out_shm_name) {
    int pid_pipe[2];
    int name_pipe[2];
    if (pipe(pid_pipe) == -1 || pipe(name_pipe) == -1) {
        perror("pipe");
        return -1;
    }

    pid_t pid = fork();
    if (pid == -1) {
        perror("fork");
        close(pid_pipe[0]); close(pid_pipe[1]);
        close(name_pipe[0]); close(name_pipe[1]);
        return -1;
    } else if (pid == 0) {
        // Child process
        close(pid_pipe[0]);
        close(name_pipe[0]);

        if (setsid() == -1) {
            perror("setsid");
            exit(EXIT_FAILURE);
        }

        int use_buffer = (max_lines > 0);
        if (!use_buffer) {
            // No buffer
            int null_fd = open("/dev/null", O_RDONLY);
            if (null_fd == -1) {
                perror("open /dev/null");
                exit(EXIT_FAILURE);
            }
            dup2(null_fd, STDIN_FILENO);
            close(null_fd);

            struct rlimit rlim;
            if (getrlimit(RLIMIT_NOFILE, &rlim) == 0) {
                for (int fd = 3; fd < rlim.rlim_max; fd++) {
                    close(fd);
                }
            }

            char env_var[256];
            snprintf(env_var, sizeof(env_var), "FF_VMID=%s", vm_id);
            putenv(env_var);

            pid_t self_pid = getpid();
            write(pid_pipe[1], &self_pid, sizeof(self_pid));
            close(pid_pipe[1]);
            close(name_pipe[1]);

            execve(cmd, argv, environ);
            perror("execve");
            exit(EXIT_FAILURE);
        }

        // Setup buffer
        CircularBuffer buf;
        buf_init(&buf, max_lines);

        // Create unique shm name
        char shm_name[64];
        snprintf(shm_name, sizeof(shm_name), "%s%s_%d", SHM_PREFIX, vm_id, (int)getpid());

        int shm_fd = shm_open(shm_name, O_CREAT | O_RDWR, 0600);
        if (shm_fd == -1) {
            perror("shm_open");
            exit(EXIT_FAILURE);
        }

        size_t max_size = max_lines * MAX_LINE_LEN + sizeof(size_t); // len prefix
        if (ftruncate(shm_fd, max_size) == -1) {
            perror("ftruncate");
            exit(EXIT_FAILURE);
        }

        void *shm_ptr = mmap(NULL, max_size, PROT_READ | PROT_WRITE, MAP_SHARED, shm_fd, 0);
        if (shm_ptr == MAP_FAILED) {
            perror("mmap");
            exit(EXIT_FAILURE);
        }

        // Write shm_name to parent
        write(name_pipe[1], shm_name, strlen(shm_name) + 1);
        close(name_pipe[1]);

        // Setup pipe for VM
        int output_pipe[2];
        if (pipe(output_pipe) == -1) {
            perror("pipe output");
            exit(EXIT_FAILURE);
        }

        pid_t vm_pid = fork();
        if (vm_pid == -1) {
            perror("fork vm");
            exit(EXIT_FAILURE);
        } else if (vm_pid == 0) {
            // Grandchild
            close(output_pipe[0]);
            close(pid_pipe[1]);
            close(shm_fd);

            int null_fd = open("/dev/null", O_RDONLY);
            if (null_fd == -1) {
                perror("open /dev/null");
                exit(EXIT_FAILURE);
            }
            dup2(null_fd, STDIN_FILENO);
            close(null_fd);

            dup2(output_pipe[1], STDOUT_FILENO);
            dup2(output_pipe[1], STDERR_FILENO);
            close(output_pipe[1]);

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
            perror("execve");
            exit(EXIT_FAILURE);
        }

        // Logger
        close(output_pipe[1]);
        int input_fd = output_pipe[0];

        write(pid_pipe[1], &vm_pid, sizeof(vm_pid));
        close(pid_pipe[1]);

        // Close extra FDs
        struct rlimit rlim;
        if (getrlimit(RLIMIT_NOFILE, &rlim) == 0) {
            for (int fd = 0; fd < rlim.rlim_max; fd++) {
                if (fd != input_fd && fd != shm_fd) {
                    close(fd);
                }
            }
        }

        // Read loop
        char line[MAX_LINE_LEN];
        while (1) {
            ssize_t bytes = read(input_fd, line, sizeof(line) - 1);
            if (bytes <= 0) {
                break;
            }
            line[bytes] = '\0';

            char *start = line;
            char *end;
            while ((end = strchr(start, '\n')) != NULL) {
                *end = '\0';
                char full_line[MAX_LINE_LEN + 1];
                snprintf(full_line, sizeof(full_line), "%s\n", start);
                buf_add(&buf, full_line);
                start = end + 1;
            }
            if (*start != '\0') {
                buf_add(&buf, start);
            }

            // Update shm
            size_t dump_size = buf_dump_size(&buf);
            if (dump_size + 1 <= max_size - sizeof(size_t)) {
                memcpy(shm_ptr, &dump_size, sizeof(size_t));
                buf_dump_to_mem(&buf, shm_ptr + sizeof(size_t));
            }
        }

        // Cleanup
        munmap(shm_ptr, max_size);
        close(shm_fd);
        shm_unlink(shm_name);
        close(input_fd);
        buf_free(&buf);
        waitpid(vm_pid, NULL, 0);
        exit(EXIT_SUCCESS);
    } else {
        // Parent
        close(pid_pipe[1]);
        close(name_pipe[1]);

        pid_t returned_pid;
        if (read(pid_pipe[0], &returned_pid, sizeof(returned_pid)) != sizeof(returned_pid)) {
            perror("read pid");
            close(pid_pipe[0]);
            close(name_pipe[0]);
            return -1;
        }
        close(pid_pipe[0]);

        if (max_lines > 0) {
            ssize_t name_len = read(name_pipe[0], out_shm_name, PATH_MAX - 1);
            if (name_len <= 0) {
                perror("read shm_name");
                close(name_pipe[0]);
                return -1;
            }
            out_shm_name[name_len] = '\0';
        } else {
            out_shm_name[0] = '\0';
        }
        close(name_pipe[0]);

        return returned_pid;
    }
}

int terminate_process(pid_t pid) {
    return kill(pid, SIGTERM);
}
