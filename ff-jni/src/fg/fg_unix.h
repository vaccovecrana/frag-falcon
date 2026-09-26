#ifndef FG_UNIX_H
#define FG_UNIX_H

int unix_open(const char *socket_path);
int unix_send(int socket_handle, const void *buffer, size_t buffer_len);
int unix_receive(int socket_handle, void *buffer, size_t buffer_len, int timeout_ms);
int unix_close(int socket_handle);

#endif
