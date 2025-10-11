#ifndef FG_VSOCK_H
#define FG_VSOCK_H

int vsock_open(const char *socket_addr);
int vsock_send(int socket_handle, const void *buffer, size_t buffer_len);
int vsock_receive(int socket_handle, void *buffer, size_t buffer_len);
int vsock_close(int socket_handle);

#endif
