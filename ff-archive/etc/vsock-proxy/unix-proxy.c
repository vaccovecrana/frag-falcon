#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <errno.h>
#include <sys/select.h>

#define LISTEN_SOCKET_PATH "./fc_podman.sock_1234"
#define PODMAN_SOCKET_PATH "/run/podman/podman.sock"
#define BUFFER_SIZE 1024

int create_unix_listener(const char *path) {
    int sock;
    struct sockaddr_un addr;

    if ((sock = socket(AF_UNIX, SOCK_STREAM, 0)) == -1) {
        perror("socket");
        exit(EXIT_FAILURE);
    }

    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, path, sizeof(addr.sun_path) - 1);
    unlink(path);

    if (bind(sock, (struct sockaddr *)&addr, sizeof(addr)) == -1) {
        perror("bind");
        close(sock);
        exit(EXIT_FAILURE);
    }

    if (listen(sock, 5) == -1) {
        perror("listen");
        close(sock);
        exit(EXIT_FAILURE);
    }

    return sock;
}

int connect_to_unix_socket(const char *path) {
    int sock;
    struct sockaddr_un addr;

    if ((sock = socket(AF_UNIX, SOCK_STREAM, 0)) == -1) {
        perror("socket");
        return -1;
    }

    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, path, sizeof(addr.sun_path) - 1);

    if (connect(sock, (struct sockaddr *)&addr, sizeof(addr)) == -1) {
        perror("connect");
        close(sock);
        return -1;
    }

    return sock;
}

void bidirectional_forwarding(int sock1, int sock2) {
    fd_set readfds;
    int maxfd = (sock1 > sock2 ? sock1 : sock2) + 1;
    char buffer[BUFFER_SIZE];
    ssize_t bytes;

    while (1) {
        FD_ZERO(&readfds);
        FD_SET(sock1, &readfds);
        FD_SET(sock2, &readfds);

        if (select(maxfd, &readfds, NULL, NULL, NULL) < 0) {
            perror("select");
            break;
        }

        if (FD_ISSET(sock1, &readfds)) {
            bytes = recv(sock1, buffer, sizeof(buffer), 0);
            if (bytes <= 0) {
                if (bytes < 0) perror("recv");
                break;
            }
            if (send(sock2, buffer, bytes, 0) != bytes) {
                perror("send");
                break;
            }
        }

        if (FD_ISSET(sock2, &readfds)) {
            bytes = recv(sock2, buffer, sizeof(buffer), 0);
            if (bytes <= 0) {
                if (bytes < 0) perror("recv");
                break;
            }
            if (send(sock1, buffer, bytes, 0) != bytes) {
                perror("send");
                break;
            }
        }
    }
}

int main() {
    int listen_sock, client_sock, podman_sock;
    struct sockaddr_un client_addr;
    socklen_t client_addr_len = sizeof(client_addr);

    // Create UNIX socket listener
    listen_sock = create_unix_listener(LISTEN_SOCKET_PATH);
    printf("Listening on UNIX socket: %s\n", LISTEN_SOCKET_PATH);

    while (1) {
        // Accept a connection on the listening UNIX socket
        client_sock = accept(listen_sock, (struct sockaddr *)&client_addr, &client_addr_len);
        if (client_sock < 0) {
            perror("accept");
            continue;
        }

        printf("Accepted connection on UNIX socket\n");

        // Connect to the real Podman UNIX socket
        podman_sock = connect_to_unix_socket(PODMAN_SOCKET_PATH);
        if (podman_sock < 0) {
            close(client_sock);
            continue;
        }

        printf("Connected to Podman UNIX socket\n");

        // Forward data between the listening UNIX socket and the Podman UNIX socket
        bidirectional_forwarding(client_sock, podman_sock);

        // Close sockets
        close(client_sock);
        close(podman_sock);
    }

    close(listen_sock);
    return 0;
}
