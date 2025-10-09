#ifndef FG_PROC_H
#define FG_PROC_H

#include <sys/types.h>

int spawn_process(const char *vm_id, const char *cmd, char **argv, int max_lines, char *out_shm_name);
int terminate_process(pid_t pid);

#endif