#include <sys/socket.h>
#include <linux/vm_sockets.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

int vsock_open(const char *socket_addr) {
    int sockfd;
    struct sockaddr_vm addr;

    unsigned int cid, port;
    if (sscanf(socket_addr, "%u:%u", &cid, &port) != 2) {
        perror("invalid socket path format");
        return -1;
    }
    if ((sockfd = socket(AF_VSOCK, SOCK_STREAM, 0)) == -1) {
        perror("socket error");
        return -2;
    }

    memset(&addr, 0, sizeof(addr));
    addr.svm_family = AF_VSOCK;
    addr.svm_cid = cid;
    addr.svm_port = port;

    if (connect(sockfd, (struct sockaddr*)&addr, sizeof(addr)) == -1) {
        perror("connect error");
        close(sockfd);
        return -4;
    }

    return sockfd;
}

int vsock_send(int socket_handle, const void *buffer, size_t buffer_len) {
    int bytes_sent = send(socket_handle, buffer, buffer_len, 0);
    if (bytes_sent == -1) {
        perror("send error");
    }
    return bytes_sent;
}

int vsock_receive(int socket_handle, void *buffer, size_t buffer_len) {
    int bytes_received = recv(socket_handle, buffer, buffer_len, 0);
    if (bytes_received == -1) {
        perror("recv error");
    }
    return bytes_received;
}

int vsock_close(int socket_handle) {
    if (close(socket_handle) == -1) {
        perror("close error");
        return -1;
    }
    return 0;
}
