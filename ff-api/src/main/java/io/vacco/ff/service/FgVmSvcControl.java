package io.vacco.ff.service;

import com.google.gson.*;
import io.vacco.ff.firecracker.NetworkInterface;
import io.vacco.ff.schema.*;
import org.slf4j.*;

import static io.vacco.ff.service.FgVmSvcStatus.ProcPath;
import static io.vacco.ff.net.FgJni.*;
import static io.vacco.ff.service.FgFirecracker.*;
import static io.vacco.ff.service.FgLogging.*;
import static io.vacco.ff.service.FgValid.*;
import static io.vacco.ff.util.FgIo.*;
import static io.vacco.ff.service.FgVmSvcStatus.*;
import static io.vacco.ff.service.FgVmSvcDhcp.*;
import static java.lang.String.format;
import static java.lang.Thread.sleep;

public class FgVmSvcControl {

  private static final Logger log = LoggerFactory.getLogger(FgVmSvcControl.class);
  private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

  public static void vmTapInit(String vmId, NetworkInterface netIf, String brId) {
    int res0 = tapCreate(netIf.host_dev_name);
    if (res0 < 0) {
      throw new IllegalStateException(format(
        "%s - Unable to initialize tap interface [%s]",
        vmId, netIf.host_dev_name
      ));
    }
    int res1 = tapAttach(netIf.host_dev_name, brId);
    if (res1 != 0) {
      throw new IllegalStateException(format(
        "%s - Unable to attach tap interface [%s] on bridge [%s]: %d",
        vmId, netIf.host_dev_name, brId, res1
      ));
    }
  }

  public static int vmTearDown(FgVmFiles vms) {
    if (vms == null) {
      return -1;
    }
    try {
      var vm = fromJson(vms.vmCfg, FgVm.class, gson);
      var netConfig = fromJson(vms.vmNetCfg, FgNetConfig.class, gson);
      int fcPid = pidOf(ProcPath, vm.tag.id);
      if (fcPid != -1) {
        terminate(fcPid);
      }
      if (vm.config.networkinterfaces != null && !vm.config.networkinterfaces.isEmpty()) {
        var netIf0 = vm.config.networkinterfaces.get(0);
        if (netConfig.dhcp) {
          vmDhcpStop(vm, netConfig, netIf0);
          netConfig.ipConfig = null;
          toJson(netConfig, vms.vmNetCfg, gson);
        }
        tapDelete(netIf0.host_dev_name);
      }
      return fcPid;
    } catch (Exception e) {
      onError(log, "Unable to tear down vm [{}]", e, vms.vmRoot);
    }
    return -1;
  }

  public static FgVmStart vmStart(FgVmStart req) {
    FgVmFiles vms = null;
    try {
      var reqErrors = validationsOf(FgVmStartVld.validate(req));
      if (!reqErrors.isEmpty()) {
        return req.withErrors(reqErrors);
      }

      vms = FgVmFiles.of(FgOptions.vmDir, req.vmId);
      var fcPid = pidOf(ProcPath, req.vmId);
      if (fcPid != -1 && fcIsMachineRunning(vms.vmSock)) {
        return req.withStatus(vmStatusOf(vms));
      }

      var vm = fromJson(vms.vmCfg, FgVm.class, gson);
      fcInit(-1, vm.tag.id, vms.vmSock, vms.vmLog);
      sleep(1000); // TODO remove after method above is fixed.
      fcMachineConfig(req, vm, vms.vmSock);

      var netConfig = fromJson(vms.vmNetCfg, FgNetConfig.class, gson);
      if (vm.config.networkinterfaces != null && !vm.config.networkinterfaces.isEmpty()) {
        var brIf = netConfig.brIf;
        var netIf0 = vm.config.networkinterfaces.get(0); // TODO add support for multiple interfaces if there is demand.
        vmTapInit(vm.tag.id, netIf0, brIf);
        fcNetIf(netIf0, vms.vmSock);
        vmDhcpInit(vm.tag.id, netConfig, netIf0);
      }
      if (vm.config.drives != null) {
        for (var drive : vm.config.drives) {
          fcDrive(req, drive, vms.vmSock);
        }
      }
      fcBootSource(vm, netConfig, vms.vmSock);
      fcVmStart(req, vms.vmSock);
      return req.withStatus(vmStatusOf(vms));
    } catch (Exception e) {
      vmTearDown(vms);
      onError(log, "Unable to start vm [{}]", e, req.vmId);
      return req.withError(messageFor(e));
    }
  }

  public static FgVmStop vmStop(FgVmStop req) {
    var reqErrors = validationsOf(FgVmStopVld.validate(req));
    if (!reqErrors.isEmpty()) {
      return req.withErrors(reqErrors);
    }
    try {
      var vms = FgVmFiles.of(FgOptions.vmDir, req.vmId);
      return req.withFcPid(vmTearDown(vms));
    } catch (Exception e) {
      onError(log, "Unable to stop vm [{}]", e, req.vmId);
      return req.withError(messageFor(e));
    }
  }

  public static FgVmLogs vmLogs(FgVmLogs req) {
    try {
      var vms = FgVmFiles.of(FgOptions.vmDir, req.vmId);
      return req.withLogData(readFile(vms.vmLog));
    } catch (Exception e) {
      onError(log, "Unable to retrieve logs for vm [{}]", e, req.vmId);
      return req.withError(messageFor(e));
    }
  }

  public static FgVmLogs vmLogsDelete(String vmId) {
    var res = new FgVmLogs();
    try {
      var vms = FgVmFiles.of(FgOptions.vmDir, vmId);
      truncateFile(vms.vmLog);
      return res;
    } catch (Exception e) {
      onError(log, "Unable to truncate logs for vm [{}]", e, vmId);
      return res.withError(messageFor(e));
    }
  }

}
