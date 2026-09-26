#define _GNU_SOURCE

#include <errno.h>
#include <fcntl.h>
#include <sched.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mount.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <unistd.h>

#include <libkrun.h>
#include <libkrun_init.h>

#define MAX_ITEMS 4096

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

static void mkdir_p(const char *path) {
    char tmp[4096];
    size_t len = strlen(path);
    if (len == 0 || len >= sizeof(tmp)) {
        die("invalid path");
    }
    memcpy(tmp, path, len + 1);
    if (tmp[len - 1] == '/') {
        tmp[len - 1] = '\0';
    }
    for (char *p = tmp + 1; *p; p++) {
        if (*p == '/') {
            *p = '\0';
            if (mkdir(tmp, 0755) != 0 && errno != EEXIST) {
                fprintf(stderr, "[fg-vmm] mkdir %s: %s\n", tmp, strerror(errno));
                exit(125);
            }
            *p = '/';
        }
    }
    if (mkdir(tmp, 0755) != 0 && errno != EEXIST) {
        fprintf(stderr, "[fg-vmm] mkdir %s: %s\n", tmp, strerror(errno));
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

static void setup_volumes(void) {
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
    for (int i = 0; i < volume_count; i++) {
        const volume_t *v = &volumes[i];
        char dest[8192];
        snprintf(dest, sizeof(dest), "%s%s%s", rootfs_dir,
                 v->guest[0] == '/' ? "" : "/", v->guest);
        mkdir_p(dest);
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
    if (rootfs_dir == NULL) {
        die("--rootfs is required");
    }
    if (command_count == 0) {
        command[command_count++] = "/bin/sh";
    }
}

int main(int argc, char **argv) {
    parse_args(argc, argv);

    const char *vm_id = getenv("FF_VMID");
    fprintf(stderr, "[fg-vmm] starting vm=%s rootfs=%s vcpus=%d ram=%d\n",
            vm_id ? vm_id : "-", rootfs_dir, vcpus, ram_mib);

    KrunError krun_err = NULL;

    check_result(krun_init_log(1, KRUN_LOG_LEVEL_WARN, KRUN_LOG_STYLE_NEVER, 0, &krun_err),
                 "krun_init_log");

    setup_volumes();

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
