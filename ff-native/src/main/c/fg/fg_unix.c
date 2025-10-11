#include <errno.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>

int unix_open(const char *socket_path) {
    int sockfd;
    struct sockaddr_un addr;\
    if ((sockfd = socket(AF_UNIX, SOCK_STREAM, 0)) == -1) {
        perror("socket error");
        return -1;
    }
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, socket_path, sizeof(addr.sun_path) - 1);
    if (connect(sockfd, (struct sockaddr*)&addr, sizeof(addr)) == -1) {
        perror("connect error");
        close(sockfd);
        return -2;
    }
    return sockfd;
}

int unix_send(int socket_handle, const void *buffer, size_t buffer_len) {
    int bytes_sent = send(socket_handle, buffer, buffer_len, 0);
    if (bytes_sent == -1) {
        perror("send error");
    }
    return bytes_sent;
}

int unix_receive(int socket_handle, void *buffer, size_t buffer_len, int timeout_ms) {
    struct timeval tv;
    tv.tv_sec = timeout_ms / 1000;           // Convert milliseconds to seconds
    tv.tv_usec = (timeout_ms % 1000) * 1000; // Remainder to microseconds
    if (setsockopt(socket_handle, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv)) < 0) {
        perror("setsockopt failed");
        return -2;
    }
    int bytes_received = recv(socket_handle, buffer, buffer_len, 0);
    if (bytes_received == -1) {
        if (errno == EWOULDBLOCK || errno == EAGAIN) {
            perror("recv timeout");
            return -3;
        } else {
            perror("recv error");
        }
    }
    return bytes_received;
}

int unix_close(int socket_handle) {
    if (close(socket_handle) == -1) {
        perror("close error");
        return -1;
    }
    return 0;
}
