#include "fg_tap.h"

#include <dirent.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <stdio.h>
#include <errno.h>

#include <arpa/inet.h>
#include <net/if.h>
#include <linux/if_arp.h>
#include <linux/if_bridge.h>
#include <linux/if_tun.h>
#include <linux/rtnetlink.h>
#include <sys/ioctl.h>
#include <sys/socket.h>

#define SIOCBRADDIF	0x89a2

int create_tap_device(const char *if_name) {
    struct ifreq ifr;
    int sock;
    int err;

    int fd = open("/dev/net/tun", O_RDWR);
    if (fd < 0) {
        perror("Failed to open /dev/net/tun");
        return -errno;
    }

    memset(&ifr, 0, sizeof(ifr));
    ifr.ifr_flags = IFF_TAP | IFF_NO_PI;
    strncpy(ifr.ifr_name, if_name, IFNAMSIZ - 1);
    ifr.ifr_name[IFNAMSIZ - 1] = '\0';

    err = ioctl(fd, TUNSETIFF, &ifr);
    if (err < 0) {
        perror("Failed to set TUNSETIFF");
        close(fd);
        return -errno;
    }

    err = ioctl(fd, TUNSETPERSIST, 1);
    if (err < 0) {
        perror("Failed to set TUNSETPERSIST");
        close(fd);
        return -errno;
    }

    sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock < 0) {
        perror("Failed to open control socket");
        close(fd);
        return -errno;
    }

    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, if_name, IFNAMSIZ - 1);
    ifr.ifr_name[IFNAMSIZ - 1] = '\0';

    err = ioctl(sock, SIOCGIFFLAGS, &ifr);
    if (err < 0) {
        perror("Failed to get interface flags");
        close(sock);
        close(fd);
        return -errno;
    }

    ifr.ifr_flags |= IFF_UP | IFF_RUNNING;

    err = ioctl(sock, SIOCSIFFLAGS, &ifr);
    if (err < 0) {
        perror("Failed to set interface flags");
        close(sock);
        close(fd);
        return -errno;
    }

    close(sock);
    close(fd);

    return 0;
}

struct nl_req {
    struct nlmsghdr nlh;
    struct ifinfomsg ifi;
    char buffer[1024];
};

int tap_ifindex(const char *if_name) {
    return (int) if_nametoindex(if_name);
}

int delete_tap_device_index(int ifindex) {
    if (ifindex <= 0) {
        return -2;
    }
    int fd = socket(AF_NETLINK, SOCK_RAW, NETLINK_ROUTE);
    if (fd < 0) {
        return -3;
    }

    struct nl_req req;
    memset(&req, 0, sizeof(req));
    req.nlh.nlmsg_len = NLMSG_LENGTH(sizeof(struct ifinfomsg));
    req.nlh.nlmsg_type = RTM_DELLINK;
    req.nlh.nlmsg_flags = NLM_F_REQUEST;
    req.ifi.ifi_family = AF_UNSPEC;
    req.ifi.ifi_index = ifindex;

    struct sockaddr_nl addr;
    memset(&addr, 0, sizeof(addr));
    addr.nl_family = AF_NETLINK;
    struct iovec iov = {
        .iov_base = &req,
        .iov_len = req.nlh.nlmsg_len
    };
    struct msghdr msg = {
        .msg_name = &addr,
        .msg_namelen = sizeof(addr),
        .msg_iov = &iov,
        .msg_iovlen = 1
    };

    int result = sendmsg(fd, &msg, 0);
    close(fd);
    return (result >= 0) ? 0 : -4;
}

int delete_tap_device(const char *if_name) {
    return delete_tap_device_index(tap_ifindex(if_name));
}

int is_tap_attached_to_bridge(const char *if_name, const char *br_name) {
    char path[256];
    struct dirent *entry;
    DIR *dp;
    snprintf(path, sizeof(path), "/sys/class/net/%s/brif", br_name);
    dp = opendir(path);
    if (dp == NULL) {
        perror("opendir");
        return -1;
    }
    while ((entry = readdir(dp))) {
        if (strcmp(entry->d_name, if_name) == 0) {
            closedir(dp);
            return 1; // TAP interface is already attached to the bridge
        }
    }
    closedir(dp);
    return 0; // TAP interface is not attached to the bridge
}

int attach_tap_to_bridge(const char *if_name, const char *br_name) {
    int sockfd;
    struct ifreq ifr;

    sockfd = socket(AF_UNIX, SOCK_DGRAM, 0);
    if (sockfd < 0) {
        perror("Socket creation failed");
        return -1;
    }

    int attached = is_tap_attached_to_bridge(if_name, br_name);
    if (attached < 0) {
        close(sockfd);
        return -2;
    }
    if (attached == 1) {
        printf("TAP interface %s is already attached to bridge %s\n", if_name, br_name);
        close(sockfd);
        return 0;
    }

    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, br_name, IFNAMSIZ - 1);
    if (ioctl(sockfd, SIOCGIFINDEX, &ifr) < 0) {
        perror("Getting bridge interface index failed");
        close(sockfd);
        return -3;
    }

    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, if_name, IFNAMSIZ - 1);
    if (ioctl(sockfd, SIOCGIFINDEX, &ifr) < 0) {
        perror("Getting TAP interface index failed");
        close(sockfd);
        return -4;
    }
    int if_index = ifr.ifr_ifindex;

    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, br_name, IFNAMSIZ - 1);
    ifr.ifr_ifindex = if_index;
    if (ioctl(sockfd, SIOCBRADDIF, &ifr) < 0) {
        perror("Adding interface to bridge failed");
        close(sockfd);
        return -5;
    }

    close(sockfd);
    return 0;
}

