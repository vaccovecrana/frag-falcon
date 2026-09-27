package io.vacco.ff.service;

import io.vacco.ff.net.FgProc;
import io.vacco.ff.net.FgTap;
import io.vacco.ff.oci.FgDockerIo;
import io.vacco.ff.oci.FgOciProgress;
import io.vacco.ff.oci.FgOciStore;
import io.vacco.ff.schema.FgVm;
import io.vacco.ff.schema.FgVmState;
import io.vacco.ff.schema.FgVmStatus;
import io.vacco.ff.util.FgIo;

import java.io.File;

/**
 * Low-level VM lifecycle services. Stack orchestration (M5b) resolves the VM
 * and its directory; these methods operate on an explicit {@code vmRoot}.
 */
public class FgVmSvc {

  public static File rootfsOf(File vmRoot) {
    return new File(vmRoot, "rootfs");
  }

  public static File logOf(File vmRoot) {
    return new File(vmRoot, "vm.log");
  }

  /** Provisions the rootfs from the VM's OCI image (image layers pulled lazily here). */
  public static FgVm build(FgVm vm, File vmRoot, FgOciStore store, FgOciProgress progress) {
    vm.image = FgDockerIo.extract(vm.image.source, rootfsOf(vmRoot), store,
        (entry, err) -> {}, progress);
    return vm;
  }

  public static boolean isProvisioned(FgVm vm, File vmRoot) {
    return rootfsOf(vmRoot).isDirectory();
  }

  /**
   * Starts the VM, provisioning first if needed. Idempotent when already running.
   *
   * @return the launcher pid
   */
  public static int start(FgVm vm, File vmRoot, FgOciStore store, String bridge, FgOciProgress progress) {
    var vmid = vm.tag.id;
    var running = FgProc.pidOf(vmid);
    if (running > 0) {
      return running;
    }
    FgIo.mkDirs(vmRoot);
    if (!isProvisioned(vm, vmRoot)) {
      build(vm, vmRoot, store, progress);
    }
    if (vm.network != null && vm.network.tapName != null) {
      FgTap.up(vm.network.tapName, bridge);
    }
    return FgProc.spawn(vmid, FgVmLaunch.args(vm, vmRoot), logOf(vmRoot).toPath());
  }

  /** Stops the VM (SIGTERM) and removes its TAP. */
  public static int stop(FgVm vm) {
    var pid = FgProc.pidOf(vm.tag.id);
    if (pid > 0) {
      FgProc.terminate(pid);
    }
    if (vm.network != null && vm.network.tapName != null) {
      FgTap.down(vm.network.tapName);
    }
    return pid;
  }

  public static FgVmStatus statusOf(FgVm vm) {
    var pid = FgProc.pidOf(vm.tag.id);
    var state = pid > 0 ? FgVmState.running : FgVmState.stopped;
    return FgVmStatus.of(vm, pid, state);
  }

  public static String logs(File vmRoot) {
    var log = logOf(vmRoot);
    return log.exists() ? FgIo.readFile(log) : "";
  }

  public static void logsDelete(File vmRoot) {
    FgIo.truncateFile(logOf(vmRoot));
  }
}
