#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <net/if.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <netpacket/packet.h>
#include <net/ethernet.h>
#include <errno.h>
#include <stdio.h>
#include <arpa/inet.h>

#include "fg_raw.h"

int create_raw_socket(const char *iface) {
    int sock;
    struct sockaddr_ll socket_address;
    struct ifreq ifr;
    int ifindex;

    // Create a raw socket
    sock = socket(AF_PACKET, SOCK_RAW, htons(ETH_P_ALL));
    if (sock == -1) {
        perror("Socket creation failed");
        return -1;
    }

    // Set socket to blocking mode
    int flags = fcntl(sock, F_GETFL);
    if (flags == -1 || fcntl(sock, F_SETFL, flags & ~O_NONBLOCK) == -1) {
        perror("Failed to set socket to blocking mode");
        close(sock);
        return -2;
    }

    // Get interface index
    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, iface, IFNAMSIZ - 1);
    ifr.ifr_name[IFNAMSIZ - 1] = '\0';

    if (ioctl(sock, SIOCGIFINDEX, &ifr) == -1) {
        perror("Getting interface index failed");
        close(sock);
        return -3;
    }
    ifindex = ifr.ifr_ifindex;

    // Prepare sockaddr_ll
    memset(&socket_address, 0, sizeof(struct sockaddr_ll));
    socket_address.sll_family = AF_PACKET;
    socket_address.sll_protocol = htons(ETH_P_ALL);
    socket_address.sll_ifindex = ifindex;

    // Bind the socket to the network interface
    if (bind(sock, (struct sockaddr *)&socket_address, sizeof(struct sockaddr_ll)) == -1) {
        perror("Socket bind failed");
        close(sock);
        return -4;
    }

    return sock;
}

int send_raw_packet(int socketHandle, const unsigned char *buffer, size_t len) {
    int sent = send(socketHandle, buffer, len, 0);
    if (sent < 0) {
        return -1;
    }
    return sent;
}

int receive_raw_packet(int socketHandle, unsigned char *buffer, size_t bufferSize, int timeoutSeconds) {
    struct timeval timeout;
    timeout.tv_sec = timeoutSeconds;
    timeout.tv_usec = 0;
    if (setsockopt(socketHandle, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout)) < 0) {
        perror("Failed to set socket timeout");
        return -1;
    }
    int received = recv(socketHandle, buffer, bufferSize, 0);
    if (received == -1) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            fprintf(stderr, "Receive timed out after %d seconds\n", timeoutSeconds);
        } else {
            perror("Failed to receive data");
        }
        return -2;
    }
    return received;
}

void close_raw_socket(int socketHandle) {
    close(socketHandle);
}

int set_promiscuous_mode(const char *interface, int enable) {
    int sock;
    struct ifreq ifr;

    if ((sock = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
        perror("Socket error");
        return -1;
    }

    strncpy(ifr.ifr_name, interface, IFNAMSIZ - 1);
    ifr.ifr_name[IFNAMSIZ - 1] = '\0';
    if (ioctl(sock, SIOCGIFFLAGS, &ifr) < 0) {
        perror("ioctl error getting flags");
        close(sock);
        return -2;
    }
    if (enable) {
        ifr.ifr_flags |= IFF_PROMISC;
    } else {
        ifr.ifr_flags &= ~IFF_PROMISC;
    }
    if (ioctl(sock, SIOCSIFFLAGS, &ifr) < 0) {
        perror("ioctl error setting flags");
        close(sock);
        return -3;
    }

    close(sock);
    return 0;
}
