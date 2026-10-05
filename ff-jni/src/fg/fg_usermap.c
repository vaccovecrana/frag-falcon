#define _GNU_SOURCE

#include <errno.h>
#include <fcntl.h>
#include <pwd.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define MAX_RANGES 64

typedef struct {
    unsigned long start;
    unsigned long count;
} subid_range;

static int parse_ul(const char *s, unsigned long *out) {
    char *end = NULL;
    errno = 0;
    unsigned long v = strtoul(s, &end, 10);
    if (errno != 0 || end == s || *end != '\0') {
        return -1;
    }
    *out = v;
    return 0;
}

static int collect_ranges(const char *path, unsigned long id, const char *name,
                          subid_range *out, int max) {
    FILE *f = fopen(path, "r");
    if (f == NULL) {
        return -1;
    }
    char idbuf[32];
    snprintf(idbuf, sizeof(idbuf), "%lu", id);
    char *line = NULL;
    size_t cap = 0;
    int n = 0;
    while (getline(&line, &cap, f) >= 0) {
        char *nl = strchr(line, '\n');
        if (nl != NULL) {
            *nl = '\0';
        }
        if (line[0] == '\0' || line[0] == '#') {
            continue;
        }
        char *f0 = line;
        char *f1 = strchr(f0, ':');
        if (f1 == NULL) {
            continue;
        }
        *f1++ = '\0';
        char *f2 = strchr(f1, ':');
        if (f2 == NULL) {
            continue;
        }
        *f2++ = '\0';
        if (strcmp(f0, idbuf) != 0 && (name == NULL || strcmp(f0, name) != 0)) {
            continue;
        }
        unsigned long start, count;
        if (parse_ul(f1, &start) != 0 || parse_ul(f2, &count) != 0 || count == 0) {
            continue;
        }
        if (n < max) {
            out[n].start = start;
            out[n].count = count;
            n++;
        }
    }
    free(line);
    fclose(f);
    return n;
}

static int build_map(const subid_range *r, int n, unsigned long self,
                     char *buf, size_t buflen) {
    int off = snprintf(buf, buflen, "0 %lu 1\n", self);
    if (off < 0 || (size_t) off >= buflen) {
        return -1;
    }
    unsigned long inside = 1;
    for (int i = 0; i < n; i++) {
        int w = snprintf(buf + off, buflen - (size_t) off, "%lu %lu %lu\n",
                         inside, r[i].start, r[i].count);
        if (w < 0 || (size_t) (off + w) >= buflen) {
            return -1;
        }
        off += w;
        inside += r[i].count;
    }
    return 0;
}

static int write_str(const char *path, const char *data) {
    int fd = open(path, O_WRONLY);
    if (fd < 0) {
        return -1;
    }
    size_t len = strlen(data);
    ssize_t w = write(fd, data, len);
    close(fd);
    return (w == (ssize_t) len) ? 0 : -1;
}

static int target_uid(const char *pid, unsigned long *uid_out) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%s/status", pid);
    FILE *f = fopen(path, "r");
    if (f == NULL) {
        return -1;
    }
    char *line = NULL;
    size_t cap = 0;
    int ok = -1;
    while (getline(&line, &cap, f) >= 0) {
        if (strncmp(line, "Uid:", 4) == 0) {
            unsigned long real;
            if (sscanf(line + 4, "%lu", &real) == 1) {
                *uid_out = real;
                ok = 0;
            }
            break;
        }
    }
    free(line);
    fclose(f);
    return ok;
}

int main(int argc, char **argv) {
    if (argc != 3) {
        fprintf(stderr, "usage: fg_usermap <target_pid> <ready_fd>\n");
        return 2;
    }
    const char *pid = argv[1];
    int ready_fd = atoi(argv[2]);
    char path[64];

    unsigned long target;
    if (target_uid(pid, &target) != 0) {
        fprintf(stderr, "fg_usermap: cannot read /proc/%s/status\n", pid);
        return 3;
    }
    if (target != (unsigned long) getuid()) {
        fprintf(stderr, "fg_usermap: target uid %lu is not the caller's uid %lu\n",
                target, (unsigned long) getuid());
        return 3;
    }

    char c;
    if (read(ready_fd, &c, 1) != 1) {
        fprintf(stderr, "fg_usermap: sync pipe closed\n");
        return 6;
    }

    struct passwd *pw = getpwuid(getuid());
    const char *name = pw != NULL ? pw->pw_name : NULL;

    subid_range ur[MAX_RANGES], gr[MAX_RANGES];
    int un = collect_ranges("/etc/subuid", (unsigned long) getuid(), name, ur, MAX_RANGES);
    int gn = collect_ranges("/etc/subgid", (unsigned long) getgid(), name, gr, MAX_RANGES);
    if (un <= 0 || gn <= 0) {
        fprintf(stderr, "fg_usermap: no subuid/subgid range for %s\n",
                name != NULL ? name : "this user");
        return 5;
    }

    char umap[4096], gmap[4096];
    if (build_map(ur, un, (unsigned long) getuid(), umap, sizeof(umap)) != 0 ||
        build_map(gr, gn, (unsigned long) getgid(), gmap, sizeof(gmap)) != 0) {
        fprintf(stderr, "fg_usermap: mapping too large\n");
        return 7;
    }

    snprintf(path, sizeof(path), "/proc/%s/setgroups", pid);
    (void) write_str(path, "deny");

    snprintf(path, sizeof(path), "/proc/%s/uid_map", pid);
    int fd = open(path, O_WRONLY);
    if (fd < 0) {
        fprintf(stderr, "fg_usermap: open uid_map: %s\n", strerror(errno));
        return 8;
    }
    ssize_t uw = write(fd, umap, strlen(umap));
    close(fd);
    if (uw != (ssize_t) strlen(umap)) {
        fprintf(stderr, "fg_usermap: writing uid_map: %s\n", strerror(errno));
        return 8;
    }
    snprintf(path, sizeof(path), "/proc/%s/gid_map", pid);
    fd = open(path, O_WRONLY);
    if (fd < 0) {
        fprintf(stderr, "fg_usermap: open gid_map: %s\n", strerror(errno));
        return 9;
    }
    ssize_t gw = write(fd, gmap, strlen(gmap));
    close(fd);
    if (gw != (ssize_t) strlen(gmap)) {
        fprintf(stderr, "fg_usermap: writing gid_map: %s\n", strerror(errno));
        return 9;
    }
    return 0;
}
