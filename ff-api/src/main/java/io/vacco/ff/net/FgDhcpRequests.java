package io.vacco.ff.net;

import io.vacco.ff.schema.FgIpConfig;
import org.slf4j.*;
import java.util.*;

import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.lang.System.currentTimeMillis;
import static java.util.Objects.requireNonNull;
import static io.vacco.ff.net.FgJni.*;
import static io.vacco.ff.net.FgNetIo.*;
import static io.vacco.ff.net.FgDhcpFrames.*;

public class FgDhcpRequests {

  public static final Logger log = LoggerFactory.getLogger(FgDhcpRequests.class);

  public static long DhcpDiscoverTimeoutMs = 5000;
  public static int DhcpReadTimeoutDivisor = 8;
  public static int DhcpBufferSize = 2048;

  public static final String AnyIp = "0.0.0.0";
  public static final String AllIp = "255.255.255.255";

  public static FgIpConfig dhcpScan(int socketHandle, byte[] dst, byte[] txId, long scanTimeoutMs) {
    var t0 = currentTimeMillis();
    var txIdStr = Arrays.toString(txId);
    var buffer = new byte[DhcpBufferSize];
    var scanTimeoutSec = (int) (scanTimeoutMs / 1000);
    var readTimeoutSec = Math.max(1, scanTimeoutSec / DhcpReadTimeoutDivisor);
    while (currentTimeMillis() - t0 < scanTimeoutMs) {
      if (log.isDebugEnabled()) {
        log.debug("Awaiting DHCP response for [{}], Tx: {}", macToString(dst), txIdStr);
      }
      if (currentThread().isInterrupted()) {
        throw new IllegalStateException(format("Interrupted DHCP scan for [%s], %s", macToString(dst), txIdStr));
      }
      int bytes = rawReceive(socketHandle, buffer, readTimeoutSec);
      if (bytes > 0) {
        var ethFrame = ethernetRx(trim(buffer, bytes));
        if (Arrays.equals(dst, ethFrame.dst)) {
          var udpPacket = udpUnwrap(ethFrame.payload);
          if (udpPacket != null) {
            var lease = dhcpResponseFrame(requireNonNull(udpPacket), txId);
            if (lease != null) {
              log.info("Obtained DHCP response for [{}] {}", macToString(dst), lease);
              return lease.withGatewayMac(ethFrame.src);
            }
          }
        }
      }
    }
    log.error(
      "Obtained no DHCP response for mac [{}] after ~{}ms, Tx: {}",
      macToString(dst), scanTimeoutMs, txIdStr
    );
    return null;
  }

  public static FgIpConfig dhcpDiscover(int sock, byte[] srcMac) {
    var disc0 = dhcpDiscoverFrame(srcMac, true);
    var disc0Udp = udpWrap(disc0.packet, AnyIp, AllIp, 68, 67);
    var disc0Eth = ethernetTx(FgEthFrame.of(srcMac, BroadcastMac, disc0Udp));
    rawSocketSend(sock, srcMac, disc0Eth);
    return dhcpScan(sock, srcMac, disc0.txId(), DhcpDiscoverTimeoutMs);
  }

  public static FgIpConfig dhcpRequest(int sock, byte[] srcMac, FgIpConfig offer) {
    var req0 = dhcpRequestFrame(srcMac, offer.txId, offer.ipAddress, offer.gateway);
    var req0Udp = udpWrap(req0.packet, AnyIp, AllIp, 68, 67);
    var req0Eth = ethernetTx(FgEthFrame.of(srcMac, BroadcastMac, req0Udp));
    rawSocketSend(sock, srcMac, req0Eth);
    return dhcpScan(sock, srcMac, req0.txId(), DhcpDiscoverTimeoutMs);
  }

  public static FgIpConfig dhcpRenew(int sock, byte[] srcMac, FgIpConfig lease,
                                     long timeoutMs, boolean bootPBroadcast) {
    var targetIp = bootPBroadcast ? AllIp : lease.gateway;
    var targetMac = bootPBroadcast ? BroadcastMac : lease.gatewayMac;
    var ren0 = dhcpRenewFrame(srcMac, lease.txId, lease.ipAddress, targetIp, bootPBroadcast);
    var ren0Udp = udpWrap(ren0.packet, lease.ipAddress, targetIp, 68, 67);
    var ren0Eth = ethernetTx(FgEthFrame.of(srcMac, targetMac, ren0Udp));
    rawSocketSend(sock, srcMac, ren0Eth);
    return dhcpScan(sock, srcMac, ren0.txId(), timeoutMs);
  }

  public static void dhcpRelease(int sock, byte[] srcMac, FgIpConfig lease) {
    var rel0 = dhcpReleaseFrame(srcMac, lease.ipAddress, lease.gateway);
    var rel0Udp = udpWrap(rel0, lease.ipAddress, lease.gateway, 68, 67);
    var rel0Eth = ethernetTx(FgEthFrame.of(srcMac, lease.gatewayMac, rel0Udp));
    rawSocketSend(sock, srcMac, rel0Eth);
  }

  public static boolean dhcpIsActiveTime(FgIpConfig lease) {
    var nowMs = currentTimeMillis();
    return nowMs >= lease.grantTimeMs && nowMs <= lease.getRenewalTimestampMs();
  }

  public static boolean dhcpIsRenewalTime(FgIpConfig lease) {
    var nowMs = currentTimeMillis();
    return nowMs >= lease.getRenewalTimestampMs() && nowMs <= lease.getRebindTimestampMs();
  }

  public static boolean dhcpIsRebindTime(FgIpConfig lease) {
    var nowMs = currentTimeMillis();
    return nowMs >= lease.getRebindTimestampMs() && nowMs <= lease.getLeaseExpireTimestampMs();
  }

}
