#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <linux/vm_sockets.h>
#include <sys/un.h>
#include <errno.h>
#include <sys/select.h>

#define PODMAN_SOCKET_PATH "/run/podman/podman.sock"
#define BUFFER_SIZE 1024
#define HOST_CID 2
#define VSOCK_PORT 1234

int create_unix_socket(const char *path) {
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

int connect_to_vsock(int cid, int port) {
    int sock;
    struct sockaddr_vm sa;

    sock = socket(AF_VSOCK, SOCK_STREAM, 0);
    if (sock < 0) {
        perror("socket");
        exit(EXIT_FAILURE);
    }

    memset(&sa, 0, sizeof(sa));
    sa.svm_family = AF_VSOCK;
    sa.svm_cid = cid;
    sa.svm_port = port;

    if (connect(sock, (struct sockaddr *)&sa, sizeof(sa)) < 0) {
        perror("connect");
        close(sock);
        exit(EXIT_FAILURE);
    }

    return sock;
}

void forward_data(int src_sock, int dst_sock) {
    char buffer[BUFFER_SIZE];
    ssize_t bytes;

    while ((bytes = recv(src_sock, buffer, sizeof(buffer), 0)) > 0) {
        if (send(dst_sock, buffer, bytes, 0) != bytes) {
            perror("send");
            break;
        }
    }

    if (bytes < 0) {
        perror("recv");
    }
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
    int unix_sock, client_sock, vsock;
    struct sockaddr_un client_addr;
    socklen_t client_addr_len = sizeof(client_addr);

    // Create UNIX socket
    unix_sock = create_unix_socket(PODMAN_SOCKET_PATH);
    printf("Listening on UNIX socket: %s\n", PODMAN_SOCKET_PATH);

    while (1) {
        // Accept a connection on the UNIX socket
        client_sock = accept(unix_sock, (struct sockaddr *)&client_addr, &client_addr_len);
        if (client_sock < 0) {
            perror("accept");
            continue;
        }

        printf("Accepted connection on UNIX socket\n");

        // Connect to the VSOCK on the host
        vsock = connect_to_vsock(HOST_CID, VSOCK_PORT);
        printf("Connected to VSOCK (CID %d, Port %d)\n", HOST_CID, VSOCK_PORT);

        // Forward data between the UNIX socket and the VSOCK
        bidirectional_forwarding(client_sock, vsock);

        // Close sockets
        close(client_sock);
        close(vsock);
    }

    close(unix_sock);
    return 0;
}
