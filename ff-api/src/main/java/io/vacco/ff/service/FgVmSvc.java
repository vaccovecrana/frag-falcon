package io.vacco.ff.service;

import com.google.gson.Gson;
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
import io.vacco.ronove.util.RvValidation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static io.vacco.shax.logging.ShArgument.kv;

/**
 * Low-level VM lifecycle services. Stack orchestration (M5b) resolves the VM
 * and its directory; these methods operate on an explicit {@code vmRoot}.
 */
public class FgVmSvc {

  private static final Gson GSON = new Gson();
  private static final Logger log = LoggerFactory.getLogger(FgVmSvc.class);

  private static final long SPAWN_DISCOVERY_TIMEOUT_MS = 2000;
  private static final long SPAWN_DISCOVERY_POLL_MS = 50;

  /**
   * Serializes provisioning per image reference. Two services pulling the same
   * image (or a double-launch of the same service) share the blob store and can
   * otherwise expand/extract concurrently, racing on the shared layer cache.
   */
  private static final ConcurrentHashMap<String, ReentrantLock> provisioningLocks = new ConcurrentHashMap<>();

  private static String imageKey(String imageRef) {
    var ref = imageRef;
    if (!ref.contains("/")) {
      ref = "docker.io/library/" + ref;
    } else if (!ref.substring(0, ref.indexOf('/')).contains(".")
      && !ref.substring(0, ref.indexOf('/')).contains(":")) {
      ref = "docker.io/" + ref;
    }
    var last = ref.substring(ref.lastIndexOf('/') + 1);
    return last.contains(":") ? ref : ref + ":latest";
  }

  private static ReentrantLock lockFor(String imageRef) {
    return provisioningLocks.computeIfAbsent(imageKey(imageRef), _ -> new ReentrantLock());
  }

  public static File rootfsOf(File vmRoot) {
    return new File(vmRoot, "rootfs");
  }

  public static File logOf(File vmRoot) {
    return new File(vmRoot, "vm.log");
  }

  /**
   * Persisted, enriched image metadata (entrypoint/cmd/env/workingDir/ports).
   */
  public static File imageOf(File vmRoot) {
    return new File(vmRoot, "image.json");
  }

  /**
   * Provisions the rootfs from the VM's OCI image (image layers pulled lazily here).
   * Fails hard if the extracted metadata is invalid, so a corrupt image never
   * reaches boot or gets persisted.
   */
  public static FgVm build(FgVm vm, File vmRoot, FgOciStore store, FgOciProgress progress) {
    vm.image = FgDockerIo.extract(vm.image.source, rootfsOf(vmRoot), store, progress);
    var violations = FgValid.validateImage(vm.image);
    if (!violations.isEmpty()) {
      throw new FgValidationException(
        "Invalid image metadata for " + vm.image.source + ": " + violations.size() + " error(s)",
        violations.toArray(RvValidation[]::new)
      );
    }
    FgIo.toJson(vm.image, imageOf(vmRoot), GSON);
    return vm;
  }

  public static boolean isProvisioned(FgVm vm, File vmRoot) {
    return rootfsOf(vmRoot).isDirectory() && imageOf(vmRoot).isFile();
  }

  /**
   * Loads the persisted image metadata, or {@code null} if it is missing or
   * invalid. Invalid metadata is treated as "not provisioned" so the next start
   * re-extracts, self-healing a corrupt/empty {@code image.json}.
   */
  public static FgImage loadImage(File vmRoot) {
    var f = imageOf(vmRoot);
    if (!f.exists()) {
      return null;
    }
    var img = FgIo.fromJson(f, FgImage.class, GSON);
    var violations = FgValid.validateImage(img);
    if (!violations.isEmpty()) {
      log.warn(
        "image metadata [{}] is invalid ({}); will re-provision: {}",
        kv("image", f), violations.size(), kv("violations", violations)
      );
      return null;
    }
    return img;
  }

  /**
   * Starts the VM, provisioning first if needed. Idempotent when already running.
   *
   * @return the launcher pid
   */
  public static int start(FgVm vm, File vmRoot, FgOciStore store, FgOciProgress progress) {
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
      var lock = lockFor(vm.image.source);
      lock.lock();
      try {
        if (!isProvisioned(vm, vmRoot)) {
          build(vm, vmRoot, store, progress);
        } else {
          var img = loadImage(vmRoot);
          if (img != null) {
            vm.image = img;
          }
        }
      } finally {
        lock.unlock();
      }
    }
    if (vm.network != null && vm.network.tapName != null) {
      FgTap.up(vm.network.tapName, vm.network.brIf);
    }
    var rc = FgProc.spawn(vmid, FgVmLaunch.args(vm, vmRoot), logOf(vmRoot).toPath());
    if (rc != 0) {
      throw new IllegalStateException("failed to spawn launcher for " + vmid);
    }
    return awaitPid(vmid);
  }

  /**
   * Waits for the reparented launcher to publish its process name so {@link FgProc#pidOf}
   * can discover it. The launcher double-forks and only sets {@code /proc/<pid>/comm}
   * after entering its namespaces and log ring, so discovery is not instantaneous.
   *
   * @return the launcher pid, or -1 if it is not found within the timeout
   */
  private static int awaitPid(String vmid) {
    var deadline = System.currentTimeMillis() + SPAWN_DISCOVERY_TIMEOUT_MS;
    int pid;
    while ((pid = FgProc.pidOf(vmid)) <= 0) {
      if (System.currentTimeMillis() >= deadline) {
        return -1;
      }
      try {
        Thread.sleep(SPAWN_DISCOVERY_POLL_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return FgProc.pidOf(vmid);
      }
    }
    return pid;
  }

  /**
   * Stops the VM (SIGTERM) and removes its TAP.
   */
  public static int stop(FgVm vm) {
    var pid = FgProc.pidOf(vm.tag.id);
    if (pid > 0) {
      try {
        FgProc.terminate(pid);
      } catch (Exception e) {
        log.error("terminate failed: {}", vm.tag.id, e);
      }
    }
    if (vm.network != null && vm.network.tapName != null) {
      try {
        FgTap.down(vm.network.tapName);
      } catch (Exception e) {
        log.error("tap-down failed: {} {}", vm.tag.id, vm.network.tapName, e);
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
