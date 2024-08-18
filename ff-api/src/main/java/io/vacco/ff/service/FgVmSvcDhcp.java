package io.vacco.ff.service;

import com.google.gson.*;
import io.vacco.ff.firecracker.NetworkInterface;
import io.vacco.ff.schema.*;
import java.util.*;
import java.util.concurrent.*;

import static io.vacco.ff.service.FgLogging.onError;
import static io.vacco.ff.net.FgDhcpRequests.*;
import static io.vacco.ff.net.FgNetIo.withPromiscIf;
import static io.vacco.ff.service.FgVmSvcStatus.vmList;
import static io.vacco.ff.net.FgNetIo.withRawSocket;
import static io.vacco.ff.net.FgJni.*;
import static io.vacco.ff.util.FgIo.*;
import static java.lang.String.format;
import static java.lang.Thread.*;
import static java.lang.System.currentTimeMillis;

public class FgVmSvcDhcp {

  private static final Map<String, Thread> dhcpTaskIdx = new ConcurrentHashMap<>();
  private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

  private static String vmDhcpTaskIdOf(String vmId, String guestMac) {
    return format("%s-%s", vmId, guestMac);
  }

  public static void vmDhcpStop(FgVm vm, FgNetConfig netConfig, NetworkInterface netIf0) {
    var taskId = vmDhcpTaskIdOf(vm.tag.id, netIf0.guest_mac);
    var task = dhcpTaskIdx.remove(taskId);
    if (task != null) {
      task.interrupt();
    }
    withRawSocket(netConfig.brIf, sock -> {
      if (netConfig.dhcp && netConfig.ipConfig != null) {
        dhcpRelease(sock, macToBytes(netIf0.guest_mac), netConfig.ipConfig);
      }
      return sock;
    });
  }

  private static FgIpConfig vmDhcpRequest(String brIf, byte[] ifMac) {
    log.info("Requesting DHCP lease for [{}] on interface [{}]", macToString(ifMac), brIf);
    return withPromiscIf(brIf, () -> withRawSocket(
      brIf, rawSock -> dhcpRequest(rawSock, ifMac, dhcpDiscover(rawSock, ifMac)))
    );
  }

  private static FgIpConfig vmDhcpRenew(String brIf, FgIpConfig lease, byte[] ifMac,
                                        long timeoutMs, boolean bootPBroadcast) {
    log.info("Renewing DHCP lease for [{}] on interface [{}], broadcast: {}", macToString(ifMac), brIf, bootPBroadcast);
    return withPromiscIf(brIf, () -> withRawSocket(
      brIf, rawSock -> dhcpRenew(rawSock, ifMac, lease, timeoutMs, bootPBroadcast)
    ));
  }

  public static FgIpConfig vmDhcpConfigure(String brIf, String guestMac, FgIpConfig lease) {
    try {
      var ifMac = macToBytes(guestMac);
      if (lease == null) {
        return vmDhcpRequest(brIf, ifMac);
      }
      if (dhcpIsActiveTime(lease)) {
        sleep(lease.getRenewalTimestampMs() - currentTimeMillis());
      }
      if (dhcpIsRenewalTime(lease)) {
        var retryMs = (lease.getLeaseTimeMs() - lease.getRenewalTimeMs()) / DhcpReadTimeoutDivisor;
        while (dhcpIsRenewalTime(lease)) {
          var serverLease = vmDhcpRenew(brIf, lease, ifMac, retryMs, false);
          if (serverLease != null) {
            return serverLease;
          }
        }
      }
      if (dhcpIsRebindTime(lease)) {
        var retryMs = (lease.getLeaseTimeMs() - lease.getRebindTimeMs()) / DhcpReadTimeoutDivisor;
        while (dhcpIsRebindTime(lease)) {
          var anyLease = vmDhcpRenew(brIf, lease, ifMac, retryMs, true);
          if (anyLease != null) {
            return anyLease;
          }
        }
      }
      return vmDhcpRequest(brIf, ifMac);
    } catch (Exception e) {
      onError(log, "Interrupted DHCP configuration for [{}, {}, {}]", e, brIf, guestMac, lease);
      return null;
    }
  }

  private static void vmDhcpSave(String vmId, FgNetConfig netCfg, FgIpConfig lease) {
    if (lease != null) {
      var vms = FgVmFiles.of(FgOptions.vmDir, vmId);
      toJson(netCfg.withIpConfig(lease), vms.vmNetCfg, gson);
    } else {
      throw new IllegalStateException(format("Stopped DHCP configuration for vm [%s] - %s", vmId, netCfg));
    }
  }

  public static void vmDhcpInit(String vmId, FgNetConfig netCfg, NetworkInterface netIf0) {
    if (!netCfg.dhcp) {
      return;
    }
    vmDhcpSave(vmId, netCfg, vmDhcpConfigure(netCfg.brIf, netIf0.guest_mac, null));
    var taskId = vmDhcpTaskIdOf(vmId, netIf0.guest_mac);
    var task = dhcpTaskIdx.computeIfAbsent(taskId, id -> new Thread(() -> {
      try {
        while (!currentThread().isInterrupted()) {
          vmDhcpSave(vmId, netCfg, vmDhcpConfigure(netCfg.brIf, netIf0.guest_mac, netCfg.ipConfig));
        }
      } catch (Exception e) {
        onError(log, "DHCP management task stopped", e);
      }
    }, format("dhcp-%s", taskId)));
    if (!task.isAlive()) {
      task.start();
    }
  }

  public static void vmDhcpStart() {
    for (var st : vmList().vms) {
      if (st.fcPid != -1) {
        vmDhcpInit(st.vm.tag.id, st.network, st.vm.config.networkinterfaces.get(0));
      }
    }
  }

  public static void vmDhcpClose() {
    var tasks = new ArrayList<>(dhcpTaskIdx.keySet());
    for (var t : tasks) {
      dhcpTaskIdx.get(t).interrupt();
      dhcpTaskIdx.remove(t);
    }
  }

  public static String vmDhcpKernelParams(FgIpConfig config, String ifName) {
    var params = new StringBuilder();
    if (config.ipAddress != null && config.subnetMask != null) {
      params
        .append("ip=").append(config.ipAddress).append("::").append(config.gateway)
        .append(":").append(config.subnetMask).append(":").append(ifName).append(" ");
    }
    if (config.dnsServers != null && !config.dnsServers.isEmpty()) {
      var dns = String.join(",", config.dnsServers);
      params.append("dns=").append(dns);
    }
    return params.toString();
  }

}
