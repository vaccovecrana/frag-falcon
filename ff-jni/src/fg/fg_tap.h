#ifndef FG_TAP_H
#define FG_TAP_H

int create_tap_device(const char *if_name);
int delete_tap_device(const char *if_name);
int attach_tap_to_bridge(const char *if_name, const char *br_name);
int detach_tap_from_bridge(const char *if_name, const char *br_name);
int get_mac_address(const char *if_name, unsigned char *mac);

#endif
