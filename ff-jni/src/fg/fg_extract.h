#ifndef FG_EXTRACT_H
#define FG_EXTRACT_H

/*
 * Extracts a (uncompressed) tar archive into `root_fd` using only
 * openat2(RESOLVE_IN_ROOT)-confined path operations. Symlink targets are stored
 * verbatim; entry paths (and hardlink sources) can never escape the root.
 *
 * Returns 0 on success, or a negative errno on the first unsafe/invalid entry.
 */
int fg_extract_tar(int root_fd, const char *tar_path);

#endif
