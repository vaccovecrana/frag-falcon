#ifndef FG_TAP_H
#define FG_TAP_H

int create_tap_device(const char *if_name);
int delete_tap_device(const char *if_name);
int delete_tap_device_index(int ifindex);
int tap_ifindex(const char *if_name);
int attach_tap_to_bridge(const char *if_name, const char *br_name);

#endif
