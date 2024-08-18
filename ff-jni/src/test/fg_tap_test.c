#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <linux/if.h>
#include <linux/if_tun.h>
#include <netinet/ip.h>
#include <netinet/udp.h>
#include <arpa/inet.h>
#include <assert.h>
#include <sys/socket.h>
#include <linux/if_packet.h>
#include <net/ethernet.h>
#include "../fg/fg_tap.h"

#define BUFFER_SIZE 1600

void send_dummy_udp_packet(int tap_fd) {
    char buffer[BUFFER_SIZE];
    memset(buffer, 0, sizeof(buffer));
    struct iphdr *iph = (struct iphdr *)buffer;
    struct udphdr *udph = (struct udphdr *)(buffer + sizeof(struct iphdr));

    // Fill in the IP Header
    iph->ihl = 5;
    iph->version = 4;
    iph->tos = 0;
    iph->tot_len = htons(sizeof(struct iphdr) + sizeof(struct udphdr));
    iph->id = htonl(54321);
    iph->frag_off = 0;
    iph->ttl = 255;
    iph->protocol = IPPROTO_UDP;
    iph->check = 0; // Set to 0 before calculating checksum
    iph->saddr = inet_addr("192.168.1.2");
    iph->daddr = inet_addr("192.168.1.1");

    // Fill in the UDP Header
    udph->source = htons(12345);
    udph->dest = htons(80);
    udph->len = htons(sizeof(struct udphdr));
    udph->check = 0; // No checksum for simplicity

    // Send the dummy packet
    if (write(tap_fd, buffer, sizeof(struct iphdr) + sizeof(struct udphdr)) < 0) {
        perror("Writing to TAP interface");
        close(tap_fd);
        exit(EXIT_FAILURE);
    }
}

void test_tap_device_lifecycle(const char *tap_name, const char *bridge_name) {
    int result;

    printf("Creating TAP device %s\n", tap_name);
    int tap_fd = create_tap_device(tap_name, 1);
    if (tap_fd < 0) {
        fprintf(stderr, "Failed to create TAP device %s: %d\n", tap_name, tap_fd);
        exit(EXIT_FAILURE);
    }
    printf("Created TAP device %s\n", tap_name);

    printf("Attaching TAP device %s to bridge %s\n", tap_name, bridge_name);
    result = attach_tap_to_bridge(tap_name, bridge_name);
    if (result < 0) {
        fprintf(stderr, "Failed to attach TAP device %s to bridge %s: %d\n", tap_name, bridge_name, result);
        exit(EXIT_FAILURE);
    }
    printf("Attached TAP device %s to bridge %s\n", tap_name, bridge_name);

    // Send dummy UDP packet through the tap device
    for (int i = 0; i < 60; i++) {
        printf("Sending dummy UDP packet through %s\n", tap_name);
        send_dummy_udp_packet(tap_fd);
        sleep(1);
    }

    close(tap_fd);

    printf("Detaching TAP device %s from bridge %s\n", tap_name, bridge_name);
    result = detach_tap_from_bridge(tap_name, bridge_name);
    if (result < 0) {
        fprintf(stderr, "Failed to detach TAP device %s from bridge %s: %d\n", tap_name, bridge_name, result);
        exit(EXIT_FAILURE);
    }
    printf("Detached TAP device %s from bridge %s\n", tap_name, bridge_name);

    printf("Deleting TAP device %s\n", tap_name);
    result = delete_tap_device(tap_name);
    if (result < 0) {
        fprintf(stderr, "Failed to delete TAP device %s: %d\n", tap_name, result);
        exit(EXIT_FAILURE);
    }
    printf("Deleted TAP device %s\n", tap_name);
}

int main() {
    const char *tap_name = "tap04";
    const char *bridge_name = "br0";

    test_tap_device_lifecycle(tap_name, bridge_name);

    printf("Test completed successfully\n");
    return 0;
}
