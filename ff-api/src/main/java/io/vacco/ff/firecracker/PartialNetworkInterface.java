package io.vacco.ff.firecracker;

import java.lang.String;

/**
 * Defines a partial network interface structure, used to update the rate limiters for that interface, after microvm start.
 */
public class PartialNetworkInterface {
  public String iface_id;

  public RateLimiter rx_rate_limiter;

  public RateLimiter tx_rate_limiter;

  public PartialNetworkInterface iface_id(String iface_id) {
    this.iface_id = iface_id;
    return this;
  }

  public PartialNetworkInterface rx_rate_limiter(RateLimiter rx_rate_limiter) {
    this.rx_rate_limiter = rx_rate_limiter;
    return this;
  }

  public PartialNetworkInterface tx_rate_limiter(RateLimiter tx_rate_limiter) {
    this.tx_rate_limiter = tx_rate_limiter;
    return this;
  }

  public static PartialNetworkInterface partialNetworkInterface() {
    return new PartialNetworkInterface();
  }
}
