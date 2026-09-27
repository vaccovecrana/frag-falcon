package io.vacco.ff.service;

/**
 * Derives a VM's identity from its stack id and service name. The identity is
 * deterministic (no bookkeeping) and is used as {@code FF_VMID}, the
 * {@code /proc} comm tag, the TAP name, and the MAC seed.
 *
 * <p>Caveat: this is a Java {@code hashCode}-based derivation, so a (unlikely)
 * cross-service collision is possible.
 */
public class FgVmId {

  public static String of(String stackId, String serviceId) {
    return Integer.toHexString((stackId + serviceId).hashCode());
  }

  public static String tapName(String vmId) {
    var n = "ff" + vmId;
    return n.length() > 15 ? n.substring(0, 15) : n;
  }

  public static String procTag(String vmId) {
    var n = "ff-" + vmId;
    return n.length() > 15 ? n.substring(0, 15) : n;
  }

  public static byte[] mac(String vmId) {
    int h = vmId.hashCode();
    return new byte[]{
      0x52, 0x54, 0x00,
      (byte) ((h >> 16) & 0xff),
      (byte) ((h >> 8) & 0xff),
      (byte) (h & 0xff)
    };
  }

  public static String macString(String vmId) {
    var mac = mac(vmId);
    var sb = new StringBuilder();
    for (int i = 0; i < 6; i++) {
      if (i > 0) {
        sb.append(':');
      }
      sb.append(String.format("%02x", mac[i] & 0xff));
    }
    return sb.toString();
  }
}
