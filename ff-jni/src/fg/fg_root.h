#ifndef FG_ROOT_H
#define FG_ROOT_H

#include <stddef.h>

/*
 * Kernel-confined path helpers built on openat2(RESOLVE_IN_ROOT): every path is
 * resolved as if the caller were chrooted into `root_fd`. Absolute symlink
 * targets and ".." can never leave the root.
 */

/* Opens a directory root; returns an fd, or -errno. */
long fg_root_open(const char *path);

/* openat2 rooted at root_fd. Returns fd or -errno. */
int fg_open_in_root(int root_fd, const char *rel, int flags, int mode);

/* Creates a directory (and parents) confined to root. Returns 0 or -errno. */
int fg_mkdir_in_root(int root_fd, const char *rel, int mode);

/* Splits "a/b/c" into parent "a/b" and leaf "c" ("." and "" parent -> ""). */
void fg_split_parent(const char *rel, char *parent, size_t plen, char *leaf, size_t llen);

/* Resolves a path inside root to an absolute host path (malloc'd; caller frees),
 * or NULL on error. Used to hand a resolved mount target to mount(2). */
char *fg_realpath_in_root(int root_fd, const char *rel);

#endif
