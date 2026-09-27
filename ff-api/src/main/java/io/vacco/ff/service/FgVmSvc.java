package io.vacco.ff.service;

import io.vacco.ff.net.FgProc;
import io.vacco.ff.net.FgTap;
import io.vacco.ff.oci.FgDockerIo;
import io.vacco.ff.oci.FgImage;
import io.vacco.ff.oci.FgOciProgress;
import io.vacco.ff.oci.FgOciStore;
import io.vacco.ff.schema.FgVm;
import io.vacco.ff.schema.FgVmState;
import io.vacco.ff.schema.FgVmStatus;
import io.vacco.ff.util.FgIo;

import com.google.gson.Gson;

import java.io.File;

/**
 * Low-level VM lifecycle services. Stack orchestration (M5b) resolves the VM
 * and its directory; these methods operate on an explicit {@code vmRoot}.
 */
public class FgVmSvc {

  private static final Gson GSON = new Gson();

  public static File rootfsOf(File vmRoot) {
    return new File(vmRoot, "rootfs");
  }

  public static File logOf(File vmRoot) {
    return new File(vmRoot, "vm.log");
  }

  /** Persisted, enriched image metadata (entrypoint/cmd/env/workingDir/ports). */
  public static File imageOf(File vmRoot) {
    return new File(vmRoot, "image.json");
  }

  /** Provisions the rootfs from the VM's OCI image (image layers pulled lazily here). */
  public static FgVm build(FgVm vm, File vmRoot, FgOciStore store, FgOciProgress progress) {
    vm.image = FgDockerIo.extract(vm.image.source, rootfsOf(vmRoot), store,
        (entry, err) -> {}, progress);
    FgIo.toJson(vm.image, imageOf(vmRoot), GSON);
    return vm;
  }

  public static boolean isProvisioned(FgVm vm, File vmRoot) {
    return rootfsOf(vmRoot).isDirectory() && imageOf(vmRoot).isFile();
  }

  public static FgImage loadImage(File vmRoot) {
    var f = imageOf(vmRoot);
    return f.exists() ? FgIo.fromJson(f, FgImage.class, GSON) : null;
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
    if (isProvisioned(vm, vmRoot)) {
      var img = loadImage(vmRoot);
      if (img != null) {
        vm.image = img;
      }
    } else {
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
      try {
        FgProc.terminate(pid);
      } catch (Exception e) {
        System.err.printf("[ff] terminate %s failed: %s%n", vm.tag.id, e);
      }
    }
    if (vm.network != null && vm.network.tapName != null) {
      try {
        FgTap.down(vm.network.tapName);
      } catch (Exception e) {
        System.err.printf("[ff] tap-down %s failed: %s%n", vm.network.tapName, e);
      }
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
