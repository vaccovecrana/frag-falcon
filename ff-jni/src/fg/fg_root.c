#define _GNU_SOURCE

#include "fg_root.h"

#include <errno.h>
#include <fcntl.h>
#include <linux/openat2.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>

#ifndef SYS_openat2
#define SYS_openat2 437
#endif

int fg_open_in_root(int root_fd, const char *rel, int flags, int mode) {
    struct open_how how;
    memset(&how, 0, sizeof(how));
    how.flags = (unsigned long long) flags;
    how.mode = (unsigned long long) mode;
    how.resolve = RESOLVE_IN_ROOT | RESOLVE_NO_MAGICLINKS;
    long r = syscall(SYS_openat2, root_fd, rel, &how, sizeof(how));
    if (r < 0) {
        return -errno;
    }
    return (int) r;
}

long fg_root_open(const char *path) {
    int fd = open(path, O_PATH | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (fd < 0) {
        return -errno;
    }
    return fd;
}

void fg_split_parent(const char *rel, char *parent, size_t plen, char *leaf, size_t llen) {
    size_t n = strlen(rel);
    while (n > 0 && rel[n - 1] == '/') {
        n--;
    }
    size_t slash = (size_t) -1;
    for (size_t i = 0; i < n; i++) {
        if (rel[i] == '/') {
            slash = i;
        }
    }
    if (slash == (size_t) -1) {
        parent[0] = '\0';
        snprintf(leaf, llen, "%.*s", (int) n, rel);
    } else {
        snprintf(parent, plen, "%.*s", (int) slash, rel);
        snprintf(leaf, llen, "%.*s", (int) (n - slash - 1), rel + slash + 1);
    }
}

int fg_mkdir_in_root(int root_fd, const char *rel, int mode) {
    if (rel == NULL || rel[0] == '\0' || strcmp(rel, ".") == 0) {
        return 0;
    }
    int existing = fg_open_in_root(root_fd, rel, O_PATH | O_DIRECTORY | O_NOFOLLOW, 0);
    if (existing >= 0) {
        close(existing);
        return 0;
    }

    char parent[4096];
    char leaf[512];
    fg_split_parent(rel, parent, sizeof(parent), leaf, sizeof(leaf));

    int rc = fg_mkdir_in_root(root_fd, parent, mode);
    if (rc != 0) {
        return rc;
    }
    int parent_fd = fg_open_in_root(root_fd, parent[0] ? parent : ".", O_PATH | O_DIRECTORY, 0);
    if (parent_fd < 0) {
        return parent_fd;
    }
    int mk = mkdirat(parent_fd, leaf, mode);
    int saved = errno;
    close(parent_fd);
    if (mk != 0 && saved != EEXIST) {
        return -saved;
    }
    return 0;
}

char *fg_realpath_in_root(int root_fd, const char *rel) {
    int fd = fg_open_in_root(root_fd, rel, O_PATH | O_DIRECTORY, 0);
    if (fd < 0) {
        return NULL;
    }
    char proc[64];
    snprintf(proc, sizeof(proc), "/proc/self/fd/%d", fd);
    char link[4096];
    ssize_t n = readlink(proc, link, sizeof(link) - 1);
    close(fd);
    if (n < 0) {
        return NULL;
    }
    link[n] = '\0';
    return strdup(link);
}
