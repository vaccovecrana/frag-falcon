#ifndef FG_RAW_H
#define FG_RAW_H

int create_raw_socket(const char *iface);
int send_raw_packet(int socketHandle, const unsigned char *buffer, size_t len);
int receive_raw_packet(int socketHandle, unsigned char *buffer, size_t bufferSize, int timeoutSeconds);
void close_raw_socket(int socketHandle);
int set_promiscuous_mode(const char *interface, int enable);

#endif
