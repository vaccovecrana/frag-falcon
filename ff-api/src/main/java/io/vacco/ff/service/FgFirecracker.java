package io.vacco.ff.service;

import com.google.gson.Gson;
import io.vacco.ff.firecracker.*;
import io.vacco.ff.firecracker.instanceactioninfo.Action_type;
import io.vacco.ff.schema.FgNetConfig;
import io.vacco.ff.schema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

import static io.vacco.ff.firecracker.InstanceActionInfo.instanceActionInfo;
import static io.vacco.ff.initramfs.FgConstants.*;
import static io.vacco.ff.util.FgIo.delete;
import static io.vacco.ff.service.FgVmSvcDhcp.vmDhcpKernelParams;
import static io.vacco.ff.net.FgJni.*;
import static io.vacco.ff.net.FgNetIo.*;
import static io.vacco.ff.service.FgOptions.fcPath;
import static io.vacco.murmux.http.MxMethod.*;
import static io.vacco.murmux.http.MxStatus.*;
import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

import static io.vacco.ff.firecracker.MachineConfiguration.machineConfiguration;

public class FgFirecracker {

  private static final Logger log = LoggerFactory.getLogger(FgVmSvcDhcp.class);

  public static final int ApiBufferSize = 4096;
  public static final int ApiTimeoutMs = 2000;

  private static final Pattern statusPattern = Pattern.compile("^HTTP/\\d\\.\\d (\\d{3})");
  private static final Pattern headerBodySplit = Pattern.compile("\r\n\r\n");

  private static final String consoleArgs = "console=ttyS0 reboot=k panic=1 pci=off";
  private static final Gson gson = new Gson();

  public static FcApiResponse fcApiCall(String raw) {
    int statusCode = -1;
    var body = "";
    var statusMatcher = statusPattern.matcher(raw);
    if (statusMatcher.find()) {
      statusCode = Integer.parseInt(statusMatcher.group(1));
    }
    var parts = headerBodySplit.split(raw, 2);
    if (parts.length > 1) {
      body = parts[1];
    }
    return FcApiResponse.of(statusCode, body);
  }

  public static FcApiResponse fcMachineConfigOf(File vmSocket) {
    return withUnixSocket(vmSocket, nxSock -> {
      var machineReq = httpRequest(GET.method, "/machine-config", "");
      var machineRes = fcApiCall(unixSendReceive(nxSock, machineReq, ApiBufferSize, ApiTimeoutMs));
      if (machineRes.statusCode != _200.code) {
        throw new IllegalStateException(format("Unable to retrieve machine configuration - %s", machineRes));
      }
      return machineRes;
    });
  }

  public static void fcInit(int fcPid, String vmId, File vmSock, File vmLog) {
    if (fcPid != -1) {
      return;
    }
    var vmArgs = new String[] { "--id", vmId, "--api-sock", vmSock.getAbsolutePath() };
    if (vmSock.exists()) {
      delete(vmSock, e -> { throw new IllegalStateException(e); });
    }
    fcPid = fork(vmId, fcPath.getAbsolutePath(), vmArgs, vmLog.getAbsolutePath());
    if (fcPid < 0) {
      var msg = format("%s - Unable to start VM process", vmId);
      throw new IllegalStateException(msg);
    }
    // TODO loop wait until FC socket starts responding.
  }

  public static void fcMachineConfig(FgVmStart vmStart, FgVm vm, File vmSock) {
    vmStart.withMachineConfig(withUnixSocket(vmSock, nxSock -> {
      var machineReq = httpRequest(PUT.method, "/machine-config", gson.toJson(vm.config.machineconfig));
      var machineRes = fcApiCall(unixSendReceive(nxSock, machineReq, ApiBufferSize, ApiTimeoutMs));
      if (machineRes.statusCode != _204.code) {
        throw new IllegalStateException(format("FC machine configuration error - %s", machineRes));
      }
      return machineRes;
    }));
  }

  public static boolean fcIsMachineRunning(File vmSock) {
    return withUnixSocket(vmSock, nxSock -> {
      var mc = machineConfiguration().vcpu_count(1L).mem_size_mib(256L);
      var machineReq = httpRequest(PUT.method, "/machine-config", gson.toJson(mc));
      var machineRes = fcApiCall(unixSendReceive(nxSock, machineReq, ApiBufferSize, ApiTimeoutMs));
      return machineRes.statusCode != _204.code;
    });
  }

  public static void fcNetIf(NetworkInterface netIf, File vmSock) {
    withUnixSocket(vmSock, nxSock -> {
      var netReq = httpRequest(PUT.method, "/network-interfaces/eth0", gson.toJson(netIf));
      var netRes = fcApiCall(unixSendReceive(nxSock, netReq, ApiBufferSize, ApiTimeoutMs));
      if (netRes.statusCode != _204.code) {
        throw new IllegalStateException(format("FC network configuration error - %s", netRes));
      }
      return netRes;
    });
  }

  public static void fcDrive(FgVmStart vmStart, Drive drive, File vmSock) {
    vmStart.withDrive(withUnixSocket(vmSock, nxSock -> {
      var driveReq = httpRequest(PUT.method, format("/drives/%s", drive.drive_id), gson.toJson(drive));
      var driveRes = fcApiCall(unixSendReceive(nxSock, driveReq, ApiBufferSize, ApiTimeoutMs));
      if (driveRes.statusCode != _204.code) {
        throw new IllegalStateException(format("FC Drive configuration error - %s", driveRes));
      }
      return driveRes;
    }));
  }

  private static void fcEnvConfig(List<FgEnvVar> env, StringBuilder sb) {
    var envArgs = env.stream()
      .map(e -> format("%s=%s", e.key, e.val))
      .collect(joining(" "));
    sb.append(" ").append(envArgs);
  }

  public static void fcBootSource(FgVm vm, FgNetConfig netConfig, File vmSock) {
    var bootArgs = new StringBuilder().append(consoleArgs);

    if (vm.config.networkinterfaces != null) {
      var netIf0 = vm.config.networkinterfaces.get(0); // TODO add support for multiple interfaces if there is demand.
      var ipConfig = netConfig.ipConfig;
      bootArgs.append(" ").append(vmDhcpKernelParams(ipConfig, netIf0.iface_id));
      for (int k = 0; k < ipConfig.dnsServers.size(); k++) {
        var ns = ipConfig.dnsServers.get(k);
        vm.image.env.add(FgEnvVar.of(format("FF_NS%d", k), ns));
      }
    }
    if (vm.image.env != null && !vm.image.env.isEmpty()) {
      fcEnvConfig(vm.image.env, bootArgs);
    }
    if (vm.image.envUsr != null && !vm.image.envUsr.isEmpty()) {
      fcEnvConfig(vm.image.envUsr, bootArgs);
    }
    if (vm.image.entryPoint != null) {
      bootArgs.append(" ").append(format("%s=%s", FF_ENTRYPOINT, gson.toJson(vm.image.entryPoint)));
    }
    if (vm.image.cmd != null) {
      bootArgs.append(" ").append(format("%s=%s", FF_CMD, gson.toJson(vm.image.cmd)));
    }
    if (vm.image.workingDir != null) {
      bootArgs.append(" ").append(format("%s=%s", FF_WORKINGDIR, vm.image.workingDir));
    }

    vm.config.bootsource.initrd_path(vm.image.rootDir);
    vm.config.bootsource.boot_args = bootArgs.toString();

    withUnixSocket(vmSock, nxSock -> {
      var bootReq = httpRequest(PUT.method, "/boot-source", gson.toJson(vm.config.bootsource));
      var bootRes = fcApiCall(unixSendReceive(nxSock, bootReq, ApiBufferSize, ApiTimeoutMs));
      if (bootRes.statusCode != _204.code) {
        throw new IllegalStateException(
          format("%s - FC boot source configuration error - %s", vm.tag.id, bootRes)
        );
      }
      return bootRes;
    });
  }

  public static void fcVmStart(FgVmStart vmStart, File vmSock) {
    vmStart.withInit(withUnixSocket(vmSock, nxSock -> {
      var start = instanceActionInfo().action_type(Action_type.InstanceStart);
      var startReq = httpRequest(PUT.method, "/actions", gson.toJson(start));
      var startRes = fcApiCall(unixSendReceive(nxSock, startReq, ApiBufferSize, ApiTimeoutMs));
      if (startRes.statusCode != _204.code) {
        throw new IllegalStateException(format("%s - FC instance start error - %s", vmStart.vmId, startRes));
      }
      return startRes;
    }));
  }

}
