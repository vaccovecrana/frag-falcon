package io.vacco.ff.schema;

/**
 * Network configuration for a VM on the hypervisor's bridge.
 */
public class FgNetConfig {

  public String brIf;
  public String tapName;
  public String guestMac;

  public static FgNetConfig of(String brIf, String tapName, String guestMac) {
    var n = new FgNetConfig();
    n.brIf = brIf;
    n.tapName = tapName;
    n.guestMac = guestMac;
    return n;
  }
}
