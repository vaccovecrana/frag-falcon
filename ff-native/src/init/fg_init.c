#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/mount.h>
#include <sys/reboot.h>
#include <sys/resource.h>
#include <sys/sysmacros.h>
#include <sys/wait.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <time.h>

#define FF_DEBUG "FF_DEBUG"
#define FF_ENTRYPOINT "FF_ENTRYPOINT"
#define FF_CMD "FF_CMD"
#define FF_WORKINGDIR "FF_WORKINGDIR"
#define FF_MOUNT "FF_MOUNT"
#define FF_NS "FF_NS"
#define FF_MAXFD "FF_MAXFD"

const char *banner = 
"--------------------------\n"
"   ________    ___  ______\n"
"  / __/ __/___/ _ \\/_  __/\n"
" / _// _//___/ , _/ / /   \n"
"/_/ /_/     /_/|_| /_/    \n"
"--------------------------\n";

struct SpMount {
    char *label;
    char *path;
    int sync;
    int read_only;
};

struct SpMount *mounts = NULL;
int num_mounts = 0;
volatile sig_atomic_t shutdown_sig = 0;

void sig_handler(int sig) {
    shutdown_sig = 1;
}

int is_debug_enabled() {
    return getenv(FF_DEBUG) != NULL;
}

char *trim(char *s) {
    while (*s == ' ' || *s == '\t') s++;
    char *e = s + strlen(s) - 1;
    while (e > s && (*e == ' ' || *e == '\t' || *e == '\n' || *e == '\r')) *e-- = 0;
    return s;
}

int mkdir_p(const char *path, mode_t mode) {
    char *dup = strdup(path);
    char *p = dup + 1; // skip leading /
    while (*p) {
        char *slash = strchr(p, '/');
        if (slash) *slash = '\0';
        if (mkdir(dup, mode) < 0 && errno != EEXIST) {
            free(dup);
            return -1;
        }
        if (slash) {
            *slash = '/';
            p = slash + 1;
        } else {
            break;
        }
    }
    free(dup);
    return 0;
}

int init_dev_null() {
    const char *dev = "/dev/null";
    struct stat st;
    if (stat(dev, &st) == 0) {
        if ((st.st_mode & S_IFMT) == S_IFCHR && major(st.st_rdev) == 1 && minor(st.st_rdev) == 3) {
            if ((st.st_mode & 0777) != 0666) chmod(dev, 0666);
            return 0;
        }
    }
    mkdir_p("/dev", 0755);
    return mknod(dev, S_IFCHR | 0666, makedev(1, 3));
}

int init_dev_mem() {
    const char *dev = "/dev/mem";
    struct stat st;
    if (stat(dev, &st) != 0) {
        if (mknod(dev, S_IFCHR | 0640, makedev(1, 1)) < 0) return -1;
        chown(dev, 0, 0);
    }
    int fd = open(dev, O_RDWR);
    if (fd < 0) return -1;
    close(fd);
    return 0;
}

int init_dev_pts() {
    const char *dev = "/dev/pts";
    struct stat st;
    if (stat(dev, &st) != 0) {
        if (mkdir(dev, 0755) < 0) return -1;
    }
    if (mount("devpts", dev, "devpts", 0, NULL) < 0) {
        if (errno != EBUSY) return -1;
    }
    return 0;
}

int init_max_file_descriptors() {
    char *maxfd_str = getenv(FF_MAXFD);
    if (!maxfd_str) return 0;
    long maxfd = atol(maxfd_str);
    if (maxfd <= 0) return -1;
    struct rlimit rl;
    if (getrlimit(RLIMIT_NOFILE, &rl) < 0) return -1;
    rl.rlim_cur = maxfd;
    if (rl.rlim_max < (rlim_t)maxfd) rl.rlim_max = maxfd;
    return setrlimit(RLIMIT_NOFILE, &rl);
}

void init_dev() {
    init_dev_mem();
    init_dev_null();
    init_dev_pts();
    init_max_file_descriptors();

    // Hardcoded essential devices
    mknod("/dev/zero", S_IFCHR | 0666, makedev(1, 5));
    mknod("/dev/full", S_IFCHR | 0666, makedev(1, 7));
    mknod("/dev/random", S_IFCHR | 0644, makedev(1, 8));
    mknod("/dev/urandom", S_IFCHR | 0644, makedev(1, 9));
    mknod("/dev/tty", S_IFCHR | 0666, makedev(5, 0));
    mknod("/dev/console", S_IFCHR | 0600, makedev(5, 1));
    mknod("/dev/ptmx", S_IFCHR | 0666, makedev(5, 2));
}

int init_kernel_params() {
    if (access("/etc/sysctl.conf", F_OK) != 0) return 0;
    FILE *f = fopen("/etc/sysctl.conf", "r");
    if (!f) return -1;
    char line[1024];
    while (fgets(line, sizeof(line), f)) {
        if (line[0] == '#' || line[0] == ';') continue;
        char *eq = strchr(line, '=');
        if (!eq) continue;
        *eq = '\0';
        char *key = trim(line);
        char *val = trim(eq + 1);
        char proc_path[256];
        snprintf(proc_path, sizeof(proc_path), "/proc/sys/%s", key);
        for (char *d = proc_path; *d; d++) if (*d == '.') *d = '/';
        int fd = open(proc_path, O_WRONLY);
        if (fd < 0) continue;
        write(fd, val, strlen(val));
        close(fd);
    }
    fclose(f);
    return 0;
}

int init_resolv() {
    FILE *f = fopen("/etc/resolv.conf", "w");
    if (!f) return -1;
    int i = 0;
    while (1) {
        char var[32];
        sprintf(var, "%s_%d", FF_NS, i);
        char *ns = getenv(var);
        if (!ns) break;
        fprintf(f, "nameserver %s\n", ns);
        i++;
    }
    fclose(f);
    return 0;
}

char **get_indexed_array(const char *prefix) {
    char **arr = NULL;
    int capacity = 0;
    int count = 0;
    int i = 0;
    while (1) {
        char var[64];
        snprintf(var, sizeof(var), "%s_%d", prefix, i);
        char *val = getenv(var);
        if (!val) break;
        if (count >= capacity) {
            capacity = capacity ? capacity * 2 : 8;
            arr = realloc(arr, (capacity + 1) * sizeof(char *));
        }
        arr[count++] = strdup(val);
        i++;
    }
    if (arr) arr[count] = NULL;
    return arr;
}

void free_array(char **arr) {
    if (!arr) return;
    for (int i = 0; arr[i]; i++) free(arr[i]);
    free(arr);
}

char **concat_arrays(char **a1, char **a2) {
    int len1 = 0, len2 = 0;
    if (a1) while (a1[len1]) len1++;
    if (a2) while (a2[len2]) len2++;
    char **res = malloc((len1 + len2 + 1) * sizeof(char *));
    int idx = 0;
    if (a1) for (int i = 0; i < len1; i++) res[idx++] = a1[i]; // Note: transferring ownership, not strdup
    if (a2) for (int i = 0; i < len2; i++) res[idx++] = a2[i];
    res[idx] = NULL;
    return res;
}

int init_mounts() {
    extern char **environ;
    for (char **env = environ; *env; env++) {
        char *e = strdup(*env);
        char *key = strtok(e, "=");
        char *val = strtok(NULL, "");
        if (strncmp(key, FF_MOUNT "_", strlen(FF_MOUNT) + 1) == 0) {
            char *val_dup = strdup(val);
            char *label = strtok(val_dup, ":");
            char *path = strtok(NULL, ":");
            char *sync_str = strtok(NULL, ":");
            char *ro_str = strtok(NULL, ":");
            if (!label || !path || !sync_str || !ro_str) {
                free(val_dup);
                free(e);
                continue;
            }
            int sync = strcmp(sync_str, "true") == 0;
            int ro = strcmp(ro_str, "true") == 0;
            if (mkdir_p(path, 0755) < 0) {
                free(val_dup);
                free(e);
                continue;
            }
            unsigned long flags = 0;
            if (ro) flags |= MS_RDONLY;
            if (sync) flags |= MS_SYNCHRONOUS;
            char options[256] = "trans=virtio,version=9p2000.L,cache=loose";
            if (sync) strcat(options, ",msync");
            if (mount(label, path, "9p", flags, options) < 0) {
                perror("mount");
            } else {
                mounts = realloc(mounts, (num_mounts + 1) * sizeof(struct SpMount));
                mounts[num_mounts].label = strdup(label);
                mounts[num_mounts].path = strdup(path);
                mounts[num_mounts].sync = sync;
                mounts[num_mounts].read_only = ro;
                num_mounts++;
            }
            free(val_dup);
        }
        free(e);
    }
    return 0;
}

void do_shutdown() {
    for (int i = 0; i < num_mounts; i++) {
        if (umount(mounts[i].path) < 0) {
            perror("umount");
        }
        free(mounts[i].label);
        free(mounts[i].path);
    }
    free(mounts);
    reboot(RB_POWER_OFF);
}

void monitor() {
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = sig_handler;
    sigaction(SIGRTMIN + 4, &sa, NULL);

    init_mounts();

    char **entrypoint = get_indexed_array(FF_ENTRYPOINT);
    char **cmdargs = get_indexed_array(FF_CMD);
    char **command = concat_arrays(entrypoint, cmdargs);
    // Note: concat_arrays transfers ownership, so don't free entrypoint and cmdargs here

    if (!command || !command[0]) {
        fprintf(stderr, "No command to execute\n");
        free_array(command);
        do_shutdown();
        return;
    }

    pid_t pid = fork();
    if (pid == 0) {
        char *wd = getenv(FF_WORKINGDIR);
        if (wd && wd[0]) chdir(wd);
        execvp(command[0], command);
        perror("execvp");
        exit(1);
    } else if (pid > 0) {
        int status;
        while (waitpid(pid, &status, 0) < 0) {
            if (errno != EINTR) break;
            if (shutdown_sig) {
                kill(pid, SIGINT);
                sleep(3);
                break;
            }
        }
        free_array(command); // This will free the strings from entrypoint and cmdargs
        do_shutdown();
    } else {
        perror("fork");
        free_array(command);
        do_shutdown();
    }
}

int main() {
    printf("%s", banner);
    unsigned long flags = MS_NOSUID | MS_NODEV | MS_NOEXEC | MS_RELATIME;
    if (mount("proc", "/proc", "proc", flags, NULL) < 0) {
        perror("mount proc");
        return 1;
    }
    if (mount("sysfs", "/sys", "sysfs", flags, NULL) < 0) {
        perror("mount sysfs");
        return 1;
    }
    init_resolv();
    init_kernel_params();
    init_dev();
    monitor();
    return 0;
}
