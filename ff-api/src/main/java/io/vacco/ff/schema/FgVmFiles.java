package io.vacco.ff.schema;

import java.io.File;
import java.util.Objects;

public class FgVmFiles {

  public static final String VmCfgJson    = "vm.json";
  public static final String VmNetCfgJson = "net.json";
  public static final String VmFcSock     = "fc.sock";
  public static final String VmLog        = "vm.log";
  public static final String VmInitRamFs  = "initramfs.cpio";

  public File vmRoot, vmCfg, vmNetCfg, vmSock, vmLog, vmInitRamFs;

  public static FgVmFiles of(File vmRoot) {
    var vmf         = new FgVmFiles();
    vmf.vmRoot      = Objects.requireNonNull(vmRoot);
    vmf.vmCfg       = new File(vmf.vmRoot, VmCfgJson);
    vmf.vmNetCfg    = new File(vmf.vmRoot, VmNetCfgJson);
    vmf.vmSock      = new File(vmf.vmRoot, VmFcSock);
    vmf.vmLog       = new File(vmf.vmRoot, VmLog);
    vmf.vmInitRamFs = new File(vmf.vmRoot, VmInitRamFs);
    return vmf;
  }

  public static FgVmFiles of(File vmDir, String vmId) {
    return of(new File(vmDir, vmId));
  }

}
