package io.vacco.ff.firecracker;

import java.lang.String;

/**
 * Defines a network interface.
 */
public class NetworkInterface {
  public String guest_mac;

  public String host_dev_name;

  public String iface_id;

  public RateLimiter rx_rate_limiter;

  public RateLimiter tx_rate_limiter;

  public NetworkInterface guest_mac(String guest_mac) {
    this.guest_mac = guest_mac;
    return this;
  }

  /**
   * Host level path for the guest network interface
   */
  public NetworkInterface host_dev_name(String host_dev_name) {
    this.host_dev_name = host_dev_name;
    return this;
  }

  public NetworkInterface iface_id(String iface_id) {
    this.iface_id = iface_id;
    return this;
  }

  public NetworkInterface rx_rate_limiter(RateLimiter rx_rate_limiter) {
    this.rx_rate_limiter = rx_rate_limiter;
    return this;
  }

  public NetworkInterface tx_rate_limiter(RateLimiter tx_rate_limiter) {
    this.tx_rate_limiter = tx_rate_limiter;
    return this;
  }

  public static NetworkInterface networkInterface() {
    return new NetworkInterface();
  }
}
