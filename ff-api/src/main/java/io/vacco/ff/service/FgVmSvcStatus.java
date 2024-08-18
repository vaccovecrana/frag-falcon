package io.vacco.ff.service;

import com.google.gson.*;
import io.vacco.ff.firecracker.MachineConfiguration;
import io.vacco.ff.net.FgJni;
import io.vacco.ff.schema.*;
import org.slf4j.*;
import java.io.File;
import java.util.Arrays;

import static io.vacco.ff.service.FgFirecracker.fcMachineConfigOf;
import static io.vacco.ff.service.FgLogging.*;
import static io.vacco.ff.util.FgIo.*;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

public class FgVmSvcStatus {

  public  static final String ProcPath = "/proc";
  private static final Logger log = LoggerFactory.getLogger(FgVmSvcStatus.class);
  private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

  public static FgVmStatus vmStatusOf(FgVmFiles vms) {
    var vm = fromJson(vms.vmCfg, FgVm.class, gson);
    var netCfg = fromJson(vms.vmNetCfg, FgNetConfig.class, gson);
    var fcPid = pidOf(ProcPath, vm.tag.id);
    if (fcPid != -1) {
      vm.config.machineconfig = gson.fromJson(
        fcMachineConfigOf(vms.vmSock).body, MachineConfiguration.class
      );
    }
    return new FgVmStatus()
      .withFcPid(fcPid)
      .withVm(vm)
      .withNetwork(netCfg);
  }

  public static FgVmList vmList() {
    var res = new FgVmList();
    for (var vmDir : requireNonNull(FgOptions.vmDir.listFiles())) {
      try {
        if (vmDir.isDirectory()) {
          res.vms.add(vmStatusOf(FgVmFiles.of(vmDir)));
        }
      } catch (Exception e) {
        onError(log, "Unable to retrieve status for VM [{}]", e, vmDir);
        res.withError(messageFor(e));
      }
    }
    return res;
  }

  public static FgVmResourceList brIfList() {
    var l = new FgVmResourceList();
    try {
      return l.withItems(FgJni.getLinuxBridgeInterfaces());
    } catch (Exception e) {
      onError(log, "Unable to retrieve Linux bridge list", e);
      return l.withError(messageFor(e));
    }
  }

  public static FgVmResourceList krnList() {
    var l = new FgVmResourceList();
    try {
      return l.withItems(
        Arrays.stream(requireNonNull(FgOptions.krnDir.listFiles()))
          .filter(File::isFile)
          .map(File::getAbsolutePath)
          .sorted()
          .collect(toList())
      );
    } catch (Exception e) {
      onError(log, "Unable to retrieve Linux Kernel list", e);
      return l.withError(messageFor(e));
    }
  }

}
