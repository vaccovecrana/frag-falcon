#define _GNU_SOURCE

#include "fg_extract.h"
#include "fg_root.h"

#include <errno.h>
#include <fcntl.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <dirent.h>
#include <limits.h>

#define TAR_BLOCK 512
#define NAME_MAX_LEN 4096

static int read_exact(int fd, void *buf, size_t n) {
    size_t off = 0;
    while (off < n) {
        ssize_t r = read(fd, (char *) buf + off, n - off);
        if (r < 0) {
            if (errno == EINTR) {
                continue;
            }
            return -errno;
        }
        if (r == 0) {
            return -EIO;
        }
        off += (size_t) r;
    }
    return 0;
}

static long long parse_octal(const unsigned char *p, int len) {
    if (len > 0 && (p[0] & 0x80)) {
        long long v = p[0] & 0x7f;
        for (int i = 1; i < len; i++) {
            v = (v << 8) | p[i];
        }
        return v;
    }
    long long v = 0;
    int i = 0;
    while (i < len && (p[i] == ' ' || p[i] == '\0')) {
        i++;
    }
    for (; i < len; i++) {
        if (p[i] < '0' || p[i] > '7') {
            break;
        }
        v = (v << 3) + (p[i] - '0');
    }
    return v;
}

static void split_parent_local(const char *rel, char *parent, size_t plen, char *leaf, size_t llen) {
    fg_split_parent(rel, parent, plen, leaf, llen);
}

/* Creates the parent dirs and opens the parent directory confined to root. */
static int open_parent(int root_fd, const char *path, char *leaf, size_t llen) {
    char parent[NAME_MAX_LEN];
    split_parent_local(path, parent, sizeof(parent), leaf, llen);
    int rc = fg_mkdir_in_root(root_fd, parent, 0755);
    if (rc != 0) {
        return rc;
    }
    return fg_open_in_root(root_fd, parent[0] ? parent : ".", O_PATH | O_DIRECTORY, 0);
}

static int remove_in_root(int root_fd, const char *path) {
    char parent[NAME_MAX_LEN];
    char leaf[512];
    split_parent_local(path, parent, sizeof(parent), leaf, sizeof(leaf));
    int parent_fd = fg_open_in_root(root_fd, parent[0] ? parent : ".", O_PATH | O_DIRECTORY, 0);
    if (parent_fd < 0) {
        return 0; /* nothing to remove */
    }

    int dir_fd = openat(parent_fd, leaf, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (dir_fd >= 0) {
        DIR *d = fdopendir(dir_fd);
        if (d != NULL) {
            struct dirent *e;
            while ((e = readdir(d)) != NULL) {
                if (strcmp(e->d_name, ".") == 0 || strcmp(e->d_name, "..") == 0) {
                    continue;
                }
                char child[NAME_MAX_LEN];
                snprintf(child, sizeof(child), "%s/%s", path, e->d_name);
                remove_in_root(root_fd, child);
            }
            closedir(d);
        }
        unlinkat(parent_fd, leaf, AT_REMOVEDIR);
    } else {
        unlinkat(parent_fd, leaf, 0);
    }
    close(parent_fd);
    return 0;
}

static int clear_dir(int root_fd, const char *dir) {
    int dir_fd = fg_open_in_root(root_fd, dir, O_RDONLY | O_DIRECTORY | O_NOFOLLOW, 0);
    if (dir_fd < 0) {
        return 0;
    }
    /* Collect names first (removing while iterating is unsafe). */
    char *names[4096];
    int count = 0;
    DIR *d = fdopendir(dir_fd);
    if (d != NULL) {
        struct dirent *e;
        while ((e = readdir(d)) != NULL && count < 4096) {
            if (strcmp(e->d_name, ".") == 0 || strcmp(e->d_name, "..") == 0) {
                continue;
            }
            names[count++] = strdup(e->d_name);
        }
        closedir(d);
    } else {
        close(dir_fd);
        return 0;
    }
    for (int i = 0; i < count; i++) {
        char child[NAME_MAX_LEN];
        int n = snprintf(child, sizeof(child), "%s/%s", dir[0] ? dir : "", names[i]);
        (void) n;
        remove_in_root(root_fd, child[0] == '/' ? child + 1 : child);
        free(names[i]);
    }
    return 0;
}

static int make_dir(int root_fd, const char *path, int mode, int uid, int gid) {
    int rc = fg_mkdir_in_root(root_fd, path, mode);
    if (rc != 0) {
        return rc;
    }
    char leaf[512];
    int parent_fd = open_parent(root_fd, path, leaf, sizeof(leaf));
    if (parent_fd < 0) {
        return parent_fd;
    }
    fchownat(parent_fd, leaf, uid, gid, AT_SYMLINK_NOFOLLOW);
    fchmodat(parent_fd, leaf, mode & 07777, 0);
    close(parent_fd);
    return 0;
}

static int make_file(int root_fd, const char *path, int mode, int uid, int gid,
                     int tar_fd, long long size) {
    char leaf[512];
    int parent_fd = open_parent(root_fd, path, leaf, sizeof(leaf));
    if (parent_fd < 0) {
        return parent_fd;
    }
    unlinkat(parent_fd, leaf, 0);
    int fd = openat(parent_fd, leaf, O_WRONLY | O_CREAT | O_TRUNC | O_NOFOLLOW | O_CLOEXEC,
                    mode & 07777);
    close(parent_fd);
    if (fd < 0) {
        return -errno;
    }
    fchown(fd, uid, gid);
    fchmod(fd, mode & 07777);

    char buf[65536];
    long long remaining = size;
    while (remaining > 0) {
        size_t want = (size_t) (remaining > (long long) sizeof(buf) ? (long long) sizeof(buf) : remaining);
        int rc = read_exact(tar_fd, buf, want);
        if (rc != 0) {
            close(fd);
            return rc;
        }
        size_t off = 0;
        while (off < want) {
            ssize_t w = write(fd, buf + off, want - off);
            if (w < 0) {
                if (errno == EINTR) {
                    continue;
                }
                close(fd);
                return -errno;
            }
            off += (size_t) w;
        }
        remaining -= (long long) want;
    }
    close(fd);
    return 0;
}

static int make_symlink(int root_fd, const char *path, const char *target) {
    char leaf[512];
    int parent_fd = open_parent(root_fd, path, leaf, sizeof(leaf));
    if (parent_fd < 0) {
        return parent_fd;
    }
    unlinkat(parent_fd, leaf, 0);
    int rc = symlinkat(target, parent_fd, leaf);
    int saved = errno;
    close(parent_fd);
    return rc == 0 ? 0 : -saved;
}

static int make_hardlink(int root_fd, const char *path, const char *target_rel) {
    int source_fd = fg_open_in_root(root_fd, target_rel, O_PATH | O_NOFOLLOW, 0);
    if (source_fd < 0) {
        return source_fd;
    }
    char leaf[512];
    int parent_fd = open_parent(root_fd, path, leaf, sizeof(leaf));
    if (parent_fd < 0) {
        close(source_fd);
        return parent_fd;
    }
    unlinkat(parent_fd, leaf, 0);
    int rc = linkat(source_fd, "", parent_fd, leaf, AT_EMPTY_PATH);
    int saved = errno;
    close(parent_fd);
    close(source_fd);
    return rc == 0 ? 0 : -saved;
}

static int contains_dotdot(const char *path) {
    const char *p = path;
    while (*p) {
        if (p[0] == '.' && p[1] == '.' && (p[2] == '\0' || p[2] == '/') && (p == path || p[-1] == '/')) {
            return 1;
        }
        p++;
    }
    return 0;
}

static int skip_padding(int fd, long long size) {
    long long pad = (TAR_BLOCK - (size % TAR_BLOCK)) % TAR_BLOCK;
    if (pad == 0) {
        return 0;
    }
    char buf[TAR_BLOCK];
    return read_exact(fd, buf, (size_t) pad);
}

static int read_entry_data(int fd, long long size, char *out, size_t out_len) {
    size_t want = (size_t) (size < (long long) (out_len - 1) ? size : (long long) (out_len - 1));
    int rc = read_exact(fd, out, want);
    if (rc != 0) {
        return rc;
    }
    out[want] = '\0';
    if (want < (size_t) size) {
        char buf[65536];
        long long rem = size - (long long) want;
        while (rem > 0) {
            size_t w = (size_t) (rem > (long long) sizeof(buf) ? (long long) sizeof(buf) : rem);
            rc = read_exact(fd, buf, w);
            if (rc != 0) {
                return rc;
            }
            rem -= (long long) w;
        }
    }
    return skip_padding(fd, size);
}

static void parse_pax(const char *buf,
                      char *path, bool *has_path, char *link, bool *has_link, long long *size, bool *has_size) {
    const char *p = buf;
    while (*p) {
        char *end = NULL;
        long len = strtol(p, &end, 10);
        if (end == NULL || *end != ' ' || len <= 0) {
            break;
        }
        const char *rec = end + 1;
        const char *rec_end = p + len;
        if (rec_end > p + (long) strlen(p) || rec_end <= rec) {
            break;
        }
        const char *eq = memchr(rec, '=', (size_t) (rec_end - rec));
        if (eq != NULL) {
            size_t klen = (size_t) (eq - rec);
            size_t vlen = (size_t) (rec_end - eq - 1);
            if (vlen > 0 && eq[1 + vlen - 1] == '\n') {
                vlen--;
            }
            if (klen == 4 && strncmp(rec, "path", 4) == 0) {
                snprintf(path, NAME_MAX_LEN, "%.*s", (int) vlen, eq + 1);
                *has_path = true;
            } else if (klen == 8 && strncmp(rec, "linkpath", 8) == 0) {
                snprintf(link, NAME_MAX_LEN, "%.*s", (int) vlen, eq + 1);
                *has_link = true;
            } else if (klen == 4 && strncmp(rec, "size", 4) == 0) {
                *size = strtoll(eq + 1, NULL, 10);
                *has_size = true;
            }
        }
        p += len;
    }
}

int fg_extract_tar(int root_fd, const char *tar_path) {
    int fd = open(tar_path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        return -errno;
    }

    unsigned char hdr[TAR_BLOCK];
    char long_name[NAME_MAX_LEN];
    char long_link[NAME_MAX_LEN];
    char pax_path[NAME_MAX_LEN];
    char pax_link[NAME_MAX_LEN];
    bool has_long_name = false, has_long_link = false;
    bool has_pax_path = false, has_pax_link = false, has_pax_size = false;
    long long pax_size = 0;

    int result = 0;
    for (;;) {
        int rc = read_exact(fd, hdr, TAR_BLOCK);
        if (rc != 0) {
            result = rc == -EIO ? 0 : rc; /* clean EOF */
            break;
        }
        bool empty = true;
        for (int i = 0; i < TAR_BLOCK; i++) {
            if (hdr[i] != 0) {
                empty = false;
                break;
            }
        }
        if (empty) {
            break;
        }

        char type = (char) hdr[156];
        long long size = parse_octal(hdr + 124, 12);

        if (type == 'L' || type == 'K') {
            char *dst = (type == 'L') ? long_name : long_link;
            rc = read_entry_data(fd, size, dst, NAME_MAX_LEN);
            if (rc != 0) {
                result = rc;
                break;
            }
            if (type == 'L') {
                has_long_name = true;
            } else {
                has_long_link = true;
            }
            continue;
        }
        if (type == 'x' || type == 'g') {
            char *buf = malloc((size_t) size + 1);
            if (buf == NULL) {
                result = -ENOMEM;
                break;
            }
            rc = read_entry_data(fd, size, buf, (size_t) size + 1);
            if (rc != 0) {
                free(buf);
                result = rc;
                break;
            }
            if (type == 'x') {
                parse_pax(buf, pax_path, &has_pax_path, pax_link, &has_pax_link, &pax_size, &has_pax_size);
            }
            free(buf);
            continue;
        }

        char name[NAME_MAX_LEN];
        char link[NAME_MAX_LEN];
        if (has_long_name) {
            snprintf(name, sizeof(name), "%s", long_name);
        } else if (has_pax_path) {
            snprintf(name, sizeof(name), "%s", pax_path);
        } else {
            char prefix[156];
            memcpy(prefix, hdr + 345, 155);
            prefix[155] = '\0';
            if (prefix[0] != '\0' && strncmp((char *) hdr + 257, "ustar", 5) == 0) {
                snprintf(name, sizeof(name), "%s/%s", prefix, hdr);
            } else {
                snprintf(name, sizeof(name), "%s", hdr);
            }
        }
        if (has_long_link) {
            snprintf(link, sizeof(link), "%s", long_link);
        } else if (has_pax_link) {
            snprintf(link, sizeof(link), "%s", pax_link);
        } else {
            memcpy(link, hdr + 157, 100);
            link[100] = '\0';
        }
        if (has_pax_size) {
            size = pax_size;
        }

        has_long_name = has_long_link = has_pax_path = has_pax_link = has_pax_size = false;

        int mode = (int) parse_octal(hdr + 100, 8);
        int uid = (int) parse_octal(hdr + 108, 8);
        int gid = (int) parse_octal(hdr + 116, 8);

        /* Whiteouts (OCI): delete the named path instead of creating an entry. */
        const char *base = strrchr(name, '/');
        base = base ? base + 1 : name;
        if (strcmp(base, ".wh..wh..opq") == 0) {
            char parent[NAME_MAX_LEN];
            char leaf[512];
            split_parent_local(name, parent, sizeof(parent), leaf, sizeof(leaf));
            clear_dir(root_fd, parent);
            rc = skip_padding(fd, size);
            if (rc != 0) {
                result = rc;
                break;
            }
            continue;
        }
        if (strncmp(base, ".wh.", 4) == 0) {
            char target[NAME_MAX_LEN];
            size_t plen = (size_t) (base - name);
            snprintf(target, sizeof(target), "%.*s%s", (int) plen, name, base + 4);
            remove_in_root(root_fd, target);
            rc = skip_padding(fd, size);
            if (rc != 0) {
                result = rc;
                break;
            }
            continue;
        }

        /* Reject unsafe entry paths: absolute paths or ".." components.
         * Symlink targets are stored verbatim (e.g. /etc/mtab -> ../proc/mounts),
         * so only hardlink sources are additionally checked. */
        if (name[0] == '/' || contains_dotdot(name)
            || (type == '1' && contains_dotdot(link))) {
            result = -EINVAL;
            break;
        }

        if (type == '5') {
            result = make_dir(root_fd, name, mode, uid, gid);
        } else if (type == '2') {
            result = make_symlink(root_fd, name, link);
        } else if (type == '1') {
            result = make_hardlink(root_fd, name, link);
        } else if (type == '0' || type == '\0' || type == '7') {
            result = make_file(root_fd, name, mode, uid, gid, fd, size);
        } else {
            /* char/block/fifo or unknown: skip (no mknod). */
            char buf[65536];
            long long rem = size;
            while (rem > 0) {
                size_t want = (size_t) (rem > (long long) sizeof(buf) ? (long long) sizeof(buf) : rem);
                rc = read_exact(fd, buf, want);
                if (rc != 0) {
                    result = rc;
                    break;
                }
                rem -= (long long) want;
            }
        }
        if (result != 0) {
            break;
        }
        rc = skip_padding(fd, size);
        if (rc != 0) {
            result = rc;
            break;
        }
    }

    close(fd);
    return result;
}
