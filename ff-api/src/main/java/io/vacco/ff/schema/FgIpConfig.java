package io.vacco.ff.schema;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static java.lang.String.format;

public class FgIpConfig {

  public String ipAddress;
  public String subnetMask;
  public String gateway;

  public List<String> dnsServers;

  public long grantTimeMs;
  public int leaseTimeSec;
  public int renewalTimeSec;
  public int rebindTimeSec;

  public byte[] txId;
  public byte[] gatewayMac;

  public FgIpConfig(String ipAddress, String subnetMask, String gateway,
                    List<String> dnsServers, long grantTimeMs,
                    int leaseTimeSec, int renewalTimeSec, int rebindTimeSec,
                    byte[] txId) {
    this.ipAddress      = Objects.requireNonNull(ipAddress);
    this.subnetMask     = Objects.requireNonNull(subnetMask);
    this.gateway        = Objects.requireNonNull(gateway);
    this.dnsServers     = Objects.requireNonNull(dnsServers);
    this.txId = Objects.requireNonNull(txId);
    this.grantTimeMs = grantTimeMs;
    this.leaseTimeSec = leaseTimeSec;
    this.renewalTimeSec = renewalTimeSec;
    this.rebindTimeSec = rebindTimeSec;
  }

  public FgIpConfig withGatewayMac(byte[] gatewayMac) {
    this.gatewayMac = Objects.requireNonNull(gatewayMac);
    return this;
  }

  public List<String> getDnsServers() {
    return dnsServers;
  }

  public long getLeaseTimeMs() {
    return leaseTimeSec * 1000L;
  }

  public long getRenewalTimeMs() {
    return renewalTimeSec * 1000L;
  }

  public long getRebindTimeMs() {
    return rebindTimeSec * 1000L;
  }

  public long getLeaseExpireTimestampMs() {
    return grantTimeMs + (leaseTimeSec * 1000L);
  }

  public long getRenewalTimestampMs() {
    return grantTimeMs + (renewalTimeSec * 1000L);
  }

  public long getRebindTimestampMs() {
    return grantTimeMs + (rebindTimeSec * 1000L);
  }

  @Override public String toString() {
    return format(
      "IP: %s, Sub: %s, Gw: %s, DNS: %s%s",
      ipAddress, subnetMask, gateway, dnsServers,
      txId != null ? format(", Tx: %s", Arrays.toString(txId)) : ""
    );
  }

}
