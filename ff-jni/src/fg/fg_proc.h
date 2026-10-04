#ifndef FG_PROC_H
#define FG_PROC_H

#include <sys/types.h>

int spawn_process(const char *cmd, char **argv,
                  const char *log_path, const char *ld_library_path);
int terminate_process(pid_t pid);
int reap_children(void);

#endif
