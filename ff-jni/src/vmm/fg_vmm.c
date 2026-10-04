#define _GNU_SOURCE

#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <pthread.h>
#include <sched.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mount.h>
#include <sys/prctl.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

#include <libkrun.h>
#include <libkrun_init.h>

#include "../fg/fg_tap.h"
#include "../fg/fg_root.h"

#define MAX_ITEMS 4096

/* virtio-net feature bits accepted for the TAP backend (see chroot_vm.c). */
#define FG_COMPAT_NET_FEATURES ((1u << 0) | (1u << 1) | (1u << 7) | (1u << 10) | (1u << 11) | (1u << 14))

typedef struct {
    const char *host;
    const char *guest;
    int read_only;
} volume_t;

static const char *rootfs_dir = NULL;
static const char *rootfs_tag = "/dev/root";
static const char *workdir = "/";
static int vcpus = 1;
static int ram_mib = 256;

static const char *tap_name = NULL;
static const char *bridge_name = NULL;
static const char *vm_id_arg = NULL;
static int tap_up = 0;
static int tap_down = 0;
static int dhcp_enabled = 1;
static int foreground = 0;
static const char *log_file = NULL;
static int log_lines = 4096;
static unsigned char guest_mac[6];
static int mac_set = 0;

static const char *envs[MAX_ITEMS];
static int env_count = 0;

static volume_t volumes[MAX_ITEMS];
static int volume_count = 0;

static const char *command[MAX_ITEMS];
static int command_count = 0;

static void die(const char *msg) {
    fprintf(stderr, "[fg-vmm] %s\n", msg);
    exit(125);
}

static void check_result(KrunResult result, const char *what) {
    if (result != KRUN_RESULT_SUCCESS) {
        const char *name = krun_result_name_cstr(result);
        fprintf(stderr, "[fg-vmm] %s failed: %s (%llu)\n",
                what, name ? name : "?", (unsigned long long) result);
        exit(125);
    }
}

static void check_handle(void *handle, const char *what) {
    if (handle == NULL) {
        fprintf(stderr, "[fg-vmm] %s failed\n", what);
        exit(125);
    }
}

static void write_file(const char *path, const char *data) {
    int fd = open(path, O_WRONLY);
    if (fd >= 0) {
        ssize_t n = write(fd, data, strlen(data));
        (void) n;
        close(fd);
    }
}

/* Enters a private mount namespace (plus a user namespace when unprivileged)
 * so the volume bind mounts below are isolated from the host. Must run while
 * the process is still single-threaded: unshare(CLONE_NEWUSER) is rejected once
 * other threads exist, so this is called before the log-ring thread starts. */
static void setup_namespaces(void) {
    if (volume_count == 0) {
        return;
    }
    if (geteuid() == 0) {
        if (unshare(CLONE_NEWNS) != 0) {
            fprintf(stderr, "[fg-vmm] unshare(CLONE_NEWNS): %s\n", strerror(errno));
            exit(125);
        }
    } else {
        if (unshare(CLONE_NEWUSER | CLONE_NEWNS) != 0) {
            fprintf(stderr, "[fg-vmm] unshare(user+mount): %s\n", strerror(errno));
            exit(125);
        }
        char map[64];
        write_file("/proc/self/setgroups", "deny");
        snprintf(map, sizeof(map), "0 %d 1\n", getuid());
        write_file("/proc/self/uid_map", map);
        snprintf(map, sizeof(map), "0 %d 1\n", getgid());
        write_file("/proc/self/gid_map", map);
    }
    if (mount(NULL, "/", NULL, MS_REC | MS_PRIVATE, NULL) != 0) {
        fprintf(stderr, "[fg-vmm] make / private: %s\n", strerror(errno));
        exit(125);
    }
}

static void setup_volume_mounts(void) {
    if (volume_count == 0) {
        return;
    }
    long root_fd = fg_root_open(rootfs_dir);
    if (root_fd < 0) {
        fprintf(stderr, "[fg-vmm] open rootfs %s: %s\n", rootfs_dir, strerror((int) -root_fd));
        exit(125);
    }
    for (int i = 0; i < volume_count; i++) {
        const volume_t *v = &volumes[i];
        char rel[4096];
        snprintf(rel, sizeof(rel), "%s", v->guest[0] == '/' ? v->guest + 1 : v->guest);
        if (rel[0] == '\0') {
            fprintf(stderr, "[fg-vmm] invalid volume guest path\n");
            exit(125);
        }
        int rc = fg_mkdir_in_root((int) root_fd, rel, 0755);
        if (rc != 0) {
            fprintf(stderr, "[fg-vmm] mkdir %s in rootfs: %s\n", rel, strerror(-rc));
            exit(125);
        }
        char *dest = fg_realpath_in_root((int) root_fd, rel);
        if (dest == NULL) {
            fprintf(stderr, "[fg-vmm] cannot resolve %s in rootfs\n", rel);
            exit(125);
        }
        if (mount(v->host, dest, NULL, MS_BIND | MS_REC, NULL) != 0) {
            fprintf(stderr, "[fg-vmm] bind %s -> %s: %s\n", v->host, dest, strerror(errno));
            exit(125);
        }
        if (v->read_only) {
            if (mount(NULL, dest, NULL, MS_BIND | MS_REMOUNT | MS_RDONLY, NULL) != 0) {
                fprintf(stderr, "[fg-vmm] remount ro %s: %s\n", dest, strerror(errno));
                exit(125);
            }
        }
        fprintf(stderr, "[fg-vmm] volume %s -> %s%s\n", v->host, dest, v->read_only ? " (ro)" : "");
        free(dest);
    }
    close((int) root_fd);
}

/* ----- bounded console log ring -------------------------------------- */
/* Keeps the last N console lines in memory and periodically rewrites
 * vm.log atomically, so the file never grows without bound. */

#define LOG_LINE_MAX 8192

static int log_read_fd = -1;
static char **log_ring;
static int log_ring_count;
static int log_dirty;
static int log_new;
static char log_pending[LOG_LINE_MAX];
static size_t log_pending_len;
static pthread_mutex_t log_mtx = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t log_read_mtx = PTHREAD_MUTEX_INITIALIZER;

static void log_ring_add(const char *s, size_t len) {
    char *copy = malloc(len + 1);
    if (copy == NULL) {
        return;
    }
    memcpy(copy, s, len);
    copy[len] = '\0';
    pthread_mutex_lock(&log_mtx);
    if (log_ring_count == log_lines) {
        free(log_ring[0]);
        memmove(log_ring, log_ring + 1, (size_t) (log_lines - 1) * sizeof(char *));
        log_ring_count--;
    }
    log_ring[log_ring_count++] = copy;
    log_dirty = 1;
    log_new++;
    pthread_mutex_unlock(&log_mtx);
}

static void log_flush(void) {
    pthread_mutex_lock(&log_mtx);
    if (!log_dirty) {
        pthread_mutex_unlock(&log_mtx);
        return;
    }
    char tmp[4096];
    snprintf(tmp, sizeof(tmp), "%s.tmp", log_file);
    FILE *f = fopen(tmp, "w");
    if (f != NULL) {
        for (int i = 0; i < log_ring_count; i++) {
            fputs(log_ring[i], f);
            fputc('\n', f);
        }
        fclose(f);
        rename(tmp, log_file);
    }
    log_dirty = 0;
    log_new = 0;
    pthread_mutex_unlock(&log_mtx);
}

/* Splits a chunk into lines, appending completed lines to the ring. Caller
 * must hold log_read_mtx. */
static void log_feed(const char *buf, size_t n) {
    for (size_t i = 0; i < n; i++) {
        char c = buf[i];
        if (c == '\n') {
            log_ring_add(log_pending, log_pending_len);
            log_pending_len = 0;
        } else if (log_pending_len < sizeof(log_pending) - 1) {
            log_pending[log_pending_len++] = c;
        }
    }
}

/* Drains whatever is still buffered in the console pipe and flushes the ring.
 * Runs from the interposed _exit() hook, since libkrun terminates the process
 * with _exit() and never flushes on its own. */
static void log_final_flush(void) {
    if (log_file == NULL || log_read_fd < 0) {
        return;
    }
    char buf[4096];
    pthread_mutex_lock(&log_read_mtx);
    for (;;) {
        ssize_t n = read(log_read_fd, buf, sizeof(buf));
        if (n > 0) {
            log_feed(buf, (size_t) n);
        } else if (n < 0 && (errno == EAGAIN || errno == EINTR)) {
            break;
        } else {
            break; /* EOF or error */
        }
    }
    if (log_pending_len > 0) {
        log_ring_add(log_pending, log_pending_len);
        log_pending_len = 0;
    }
    log_flush();
    pthread_mutex_unlock(&log_read_mtx);
}

static void *log_thread(void *arg) {
    (void) arg;
    char buf[4096];
    struct pollfd pfd;
    pfd.fd = log_read_fd;
    pfd.events = POLLIN;
    for (;;) {
        pfd.revents = 0;
        int pr = poll(&pfd, 1, 500);
        if (pr > 0 && (pfd.revents & (POLLIN | POLLHUP))) {
            pthread_mutex_lock(&log_read_mtx);
            ssize_t n = read(log_read_fd, buf, sizeof(buf));
            if (n > 0) {
                log_feed(buf, (size_t) n);
            } else if (n == 0 || (n < 0 && errno != EAGAIN && errno != EINTR)) {
                if (log_pending_len > 0) {
                    log_ring_add(log_pending, log_pending_len);
                    log_pending_len = 0;
                }
                log_flush();
                pthread_mutex_unlock(&log_read_mtx);
                break;
            }
            pthread_mutex_unlock(&log_read_mtx);
            if (log_new >= 64) {
                log_flush();
            }
        } else {
            log_flush();
        }
    }
    return NULL;
}

static void setup_log_ring(const char *path, int max_lines) {
    if (path == NULL) {
        return;
    }
    log_file = strdup(path);
    log_lines = max_lines > 0 ? max_lines : 4096;
    log_ring = calloc((size_t) log_lines, sizeof(char *));
    if (log_file == NULL || log_ring == NULL) {
        return;
    }
    int p[2];
    if (pipe(p) != 0) {
        perror("pipe");
        return;
    }
    log_read_fd = p[0];
    int flags = fcntl(log_read_fd, F_GETFL, 0);
    if (flags >= 0) {
        fcntl(log_read_fd, F_SETFL, flags | O_NONBLOCK);
    }
    dup2(p[1], STDOUT_FILENO);
    dup2(p[1], STDERR_FILENO);
    if (p[1] > STDERR_FILENO) {
        close(p[1]);
    }
    setvbuf(stdout, NULL, _IONBF, 0);
    setvbuf(stderr, NULL, _IONBF, 0);
    pthread_t t;
    if (pthread_create(&t, NULL, log_thread, NULL) == 0) {
        pthread_detach(t);
    }
}

/* libkrun terminates the process with libc::_exit() (see vmm/mod.rs), which
 * bypasses atexit handlers and destructors. Interpose it from the executable
 * (linked with -rdynamic) so we can flush the bounded log ring one last time
 * before the process is torn down. */
__attribute__((noreturn)) void _exit(int status) {
    log_final_flush();
    syscall(SYS_exit_group, status);
    __builtin_unreachable();
}

static int parse_mac(const char *s, unsigned char *out) {
    return sscanf(s, "%hhx:%hhx:%hhx:%hhx:%hhx:%hhx",
                  &out[0], &out[1], &out[2], &out[3], &out[4], &out[5]) == 6;
}

/*
 * Privileged TAP lifecycle utilities (require CAP_NET_ADMIN).
 * The hypervisor owns this in production; dev/test uses these modes via
 * ff-jni/setup-caps.sh.
 */
static void run_tap_utility(void) {
    if (tap_name == NULL) {
        die("--tap-up/--tap-down require --tap NAME");
    }
    if (tap_up) {
        delete_tap_device(tap_name);
        int rc = create_tap_device(tap_name);
        if (rc < 0) {
            fprintf(stderr, "[fg-vmm] unable to create tap %s: %s\n", tap_name, strerror(-rc));
            exit(125);
        }
        if (bridge_name != NULL) {
            rc = attach_tap_to_bridge(tap_name, bridge_name);
            if (rc != 0) {
                fprintf(stderr, "[fg-vmm] unable to attach %s to %s: %d\n", tap_name, bridge_name, rc);
                exit(125);
            }
        }
        fprintf(stderr, "[fg-vmm] tap %s up%s%s\n", tap_name,
                bridge_name ? " on " : "", bridge_name ? bridge_name : "");
    } else {
        int rc = delete_tap_device(tap_name);
        if (rc == -2) {
            fprintf(stderr, "[fg-vmm] tap %s already gone\n", tap_name);
        } else if (rc != 0) {
            fprintf(stderr, "[fg-vmm] unable to delete tap %s: %d\n", tap_name, rc);
            exit(125);
        } else {
            fprintf(stderr, "[fg-vmm] tap %s down\n", tap_name);
        }
    }
}

static void parse_args(int argc, char **argv) {
    int i = 1;
    for (; i < argc; i++) {
        const char *a = argv[i];
        if (strcmp(a, "--") == 0) {
            i++;
            break;
        } else if (strcmp(a, "--rootfs") == 0 && i + 1 < argc) {
            rootfs_dir = argv[++i];
        } else if (strcmp(a, "--tag") == 0 && i + 1 < argc) {
            rootfs_tag = argv[++i];
        } else if (strcmp(a, "--vcpus") == 0 && i + 1 < argc) {
            vcpus = atoi(argv[++i]);
        } else if (strcmp(a, "--ram") == 0 && i + 1 < argc) {
            ram_mib = atoi(argv[++i]);
        } else if (strcmp(a, "--workdir") == 0 && i + 1 < argc) {
            workdir = argv[++i];
        } else if (strcmp(a, "--tap") == 0 && i + 1 < argc) {
            tap_name = argv[++i];
        } else if (strcmp(a, "--vm-id") == 0 && i + 1 < argc) {
            vm_id_arg = argv[++i];
        } else if (strcmp(a, "--bridge") == 0 && i + 1 < argc) {
            bridge_name = argv[++i];
        } else if (strcmp(a, "--mac") == 0 && i + 1 < argc) {
            if (!parse_mac(argv[++i], guest_mac)) {
                die("invalid --mac (expected aa:bb:cc:dd:ee:ff)");
            }
            mac_set = 1;
        } else if (strcmp(a, "--tap-up") == 0) {
            tap_up = 1;
        } else if (strcmp(a, "--tap-down") == 0) {
            tap_down = 1;
        } else if (strcmp(a, "--no-dhcp") == 0) {
            dhcp_enabled = 0;
        } else if (strcmp(a, "--foreground") == 0) {
            foreground = 1;
        } else if (strcmp(a, "--log-file") == 0 && i + 1 < argc) {
            log_file = argv[++i];
        } else if (strcmp(a, "--log-lines") == 0 && i + 1 < argc) {
            log_lines = atoi(argv[++i]);
        } else if (strcmp(a, "--env") == 0 && i + 1 < argc) {
            if (env_count >= MAX_ITEMS) die("too many env vars");
            envs[env_count++] = argv[++i];
        } else if (strcmp(a, "--volume") == 0 && i + 1 < argc) {
            if (volume_count >= MAX_ITEMS) die("too many volumes");
            char *spec = argv[++i];
            char *host = spec;
            char *guest = strchr(spec, ':');
            if (guest == NULL) {
                die("invalid --volume (expected HOST:GUEST[:ro])");
            }
            *guest++ = '\0';
            char *ro = strchr(guest, ':');
            int read_only = 0;
            if (ro != NULL) {
                *ro++ = '\0';
                read_only = strcmp(ro, "ro") == 0;
            }
            volumes[volume_count].host = host;
            volumes[volume_count].guest = guest;
            volumes[volume_count].read_only = read_only;
            volume_count++;
        } else {
            fprintf(stderr, "[fg-vmm] unknown option: %s\n", a);
            exit(125);
        }
    }
    for (; i < argc; i++) {
        if (command_count >= MAX_ITEMS) die("too many command args");
        command[command_count++] = argv[i];
    }
    if (tap_up || tap_down) {
        return;
    }
    if (rootfs_dir == NULL) {
        die("--rootfs is required");
    }
    if (command_count == 0) {
        command[command_count++] = "/bin/sh";
    }
}

/*
 * Detach the launcher from any controlling terminal / parent process tree.
 *
 * This runs in main() while the process is still single-threaded, so a raw
 * fork() is safe here. It intentionally does NOT happen in the host JVM: a
 * fork() in a multithreaded process may only be followed by async-signal-safe
 * calls before exec(), and forking a running JVM (JIT/GC/JVMCI) can corrupt
 * JVM-internal state and crash unrelated threads. The JVM therefore spawns
 * this binary with posix_spawn and never forks itself.
 *
 * Double fork reparents the launcher to init/subreaper so it leaves the host's
 * descendant tree and survives the hypervisor restarting. No pid is reported;
 * discovery is /proc/<pid>/comm (the process name is set to the VM id).
 */
static void daemonize(void) {
    pid_t mid = fork();
    if (mid < 0) {
        die("fork failed");
    }
    if (mid > 0) {
        _exit(0);
    }
    if (setsid() == -1) {
        _exit(125);
    }
    pid_t child = fork();
    if (child < 0) {
        _exit(125);
    }
    if (child > 0) {
        _exit(0);
    }
}

/*
 * TAPs are persistent and the launcher cannot clean up after itself: libkrun
 * _exit()s the process on guest exit, so no atexit handler runs. This watcher
 * is forked before setup_namespaces() (so it keeps host-netns CAP_NET_ADMIN)
 * and holds the read end of a pipe whose write end stays open in the launcher;
 * when the launcher dies for any reason the pipe hits EOF and the watcher
 * deletes exactly the TAP it was started with (by ifindex, so a recreated TAP
 * for a restarted VM is never touched).
 */
static void spawn_tap_watcher(int ifindex) {
    int p[2];
    if (pipe(p) != 0) {
        return;
    }
    pid_t w = fork();
    if (w < 0) {
        close(p[0]);
        close(p[1]);
        return;
    }
    if (w == 0) {
        close(p[1]);
        char buf[1];
        while (read(p[0], buf, 1) > 0) {
        }
        close(p[0]);
        delete_tap_device_index(ifindex);
        _exit(0);
    }
    close(p[0]);
}

int main(int argc, char **argv) {
    parse_args(argc, argv);

    if (tap_up || tap_down) {
        run_tap_utility();
        return 0;
    }

    if (!foreground) {
        daemonize();
    }

    if (tap_name != NULL) {
        int idx = tap_ifindex(tap_name);
        if (idx > 0) {
            spawn_tap_watcher(idx);
        }
    }

    setup_namespaces();

    setup_log_ring(log_file, log_lines);

    const char *vm_id = vm_id_arg;
    if (vm_id != NULL) {
        char comm[16];
        snprintf(comm, sizeof(comm), "%s", vm_id);
        prctl(PR_SET_NAME, (unsigned long) comm, 0, 0, 0);
    }
    fprintf(stderr, "[fg-vmm] starting vm=%s rootfs=%s vcpus=%d ram=%d\n",
            vm_id ? vm_id : "-", rootfs_dir, vcpus, ram_mib);

    if (!mac_set) {
        guest_mac[0] = 0x52;
        guest_mac[1] = 0x54;
        guest_mac[2] = 0x00;
        unsigned v = (unsigned) getpid();
        guest_mac[3] = (v >> 16) & 0xff;
        guest_mac[4] = (v >> 8) & 0xff;
        guest_mac[5] = v & 0xff;
        mac_set = 1;
    }

    KrunError krun_err = NULL;

    check_result(krun_init_log(1, KRUN_LOG_LEVEL_WARN, KRUN_LOG_STYLE_NEVER, 0, &krun_err),
                 "krun_init_log");

    setup_volume_mounts();

    struct rlimit rlim;
    if (getrlimit(RLIMIT_NOFILE, &rlim) == 0) {
        rlim.rlim_cur = rlim.rlim_max;
        setrlimit(RLIMIT_NOFILE, &rlim);
    }

    KrunPayload payload = krun_payload_load_krunfw(&krun_err);
    check_handle(payload, "krun_payload_load_krunfw");

    KrunFsOverlay overlay = krun_fs_overlay_new();
    check_handle(overlay, "krun_fs_overlay_new");

    KrunInitBuilder init_builder = krun_init_config_builder();
    check_handle(init_builder, "krun_init_config_builder");
    for (int i = 0; i < command_count; i++) {
        krun_init_builder_arg(&init_builder, KRUN_STR(command[i]));
    }
    for (int i = 0; i < env_count; i++) {
        krun_init_builder_env_var(&init_builder, KRUN_STR(envs[i]));
    }
    krun_init_builder_workdir(&init_builder, KRUN_STR(workdir));
    if (tap_name != NULL && dhcp_enabled) {
        krun_init_builder_dhcp(&init_builder, true);
    }
    KrunInitConfig init_config = krun_init_builder_build(&init_builder);
    check_handle(init_config, "krun_init_builder_build");
    check_result(krun_init_config_apply(init_config, overlay, payload, &krun_err),
                 "krun_init_config_apply");

    KrunMmioDeviceManager devices = krun_mmio_device_manager_new();
    check_handle(devices, "krun_mmio_device_manager_new");

    KrunConsoleBuilder console_builder = krun_console_device_builder();
    check_handle(console_builder, "krun_console_device_builder");
    check_result(krun_console_builder_add_default_console(console_builder, 0, 1, 2, &krun_err),
                 "krun_console_builder_add_default_console");
    KrunConsoleDevice console = krun_console_builder_build(console_builder, &krun_err);
    check_handle(console, "krun_console_builder_build");
    krun_mmio_device_manager_add(devices, console);

    KrunFsDevice rootfs = krun_fs_device_new(KRUN_STR(rootfs_tag), KRUN_STR(rootfs_dir), &krun_err);
    check_handle(rootfs, "krun_fs_device_new");
    krun_fs_device_set_overlay(rootfs, overlay);
    krun_mmio_device_manager_add(devices, rootfs);

    KrunRngDevice rng = krun_rng_device_new(&krun_err);
    if (rng != NULL) {
        krun_mmio_device_manager_add(devices, rng);
    }

    if (tap_name != NULL) {
        KrunNetDevice net = krun_net_device_new_tap(
            KRUN_STR("net0"), KRUN_STR(tap_name), KRUN_BYTES(guest_mac),
            FG_COMPAT_NET_FEATURES, &krun_err);
        check_handle(net, "krun_net_device_new_tap");
        krun_mmio_device_manager_add(devices, net);
        fprintf(stderr, "[fg-vmm] net0 -> tap %s (dhcp=%d)\n", tap_name, dhcp_enabled);
    }

    KrunVmmBuilder vmm_builder = krun_vmm_builder_new();
    check_handle(vmm_builder, "krun_vmm_builder_new");
    check_result(krun_vmm_builder_vcpus(&vmm_builder, (uint8_t) vcpus, &krun_err),
                 "krun_vmm_builder_vcpus");
    check_result(krun_vmm_builder_ram_mib(&vmm_builder, (uint32_t) ram_mib, &krun_err),
                 "krun_vmm_builder_ram_mib");
    krun_vmm_builder_payload(&vmm_builder, payload);
    krun_vmm_builder_devices(&vmm_builder, devices);

    KrunVmm vmm = krun_vmm_builder_build(&vmm_builder, &krun_err);
    check_handle(vmm, "krun_vmm_builder_build");

    /* Never returns: libkrun _exit()s this process when the guest exits. */
    krun_vmm_run(vmm);
    return 0;
}
