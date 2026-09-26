package io.vacco.ff.firecracker;

import io.vacco.ff.firecracker.mmdsconfig.Version;
import java.lang.String;
import java.util.List;

/**
 * Defines the MMDS configuration.
 */
public class MmdsConfig {
  public String ipv4_address;

  public List<String> network_interfaces;

  public Version version;

  /**
   * A valid IPv4 link-local address.
   */
  public MmdsConfig ipv4_address(String ipv4_address) {
    this.ipv4_address = ipv4_address;
    return this;
  }

  /**
   * List of the network interface IDs capable of forwarding packets to the MMDS. Network interface IDs mentioned must be valid at the time of this request. The net device model will reply to HTTP GET requests sent to the MMDS address via the interfaces mentioned. In this case, both ARP requests and TCP segments heading to `ipv4_address` are intercepted by the device model, and do not reach the associated TAP device.
   */
  public MmdsConfig network_interfaces(List<String> network_interfaces) {
    this.network_interfaces = network_interfaces;
    return this;
  }

  /**
   * Enumeration indicating the MMDS version to be configured.
   */
  public MmdsConfig version(Version version) {
    this.version = version;
    return this;
  }

  public static MmdsConfig mmdsConfig() {
    return new MmdsConfig();
  }
}
