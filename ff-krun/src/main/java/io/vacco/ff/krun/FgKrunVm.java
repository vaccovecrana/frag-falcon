package io.vacco.ff.krun;

import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fluent, lifetime-aware wrapper around the libkrun 2.0 builder API.
 *
 * <p>A VM is assembled from a rootfs directory (shared as a virtiofs device),
 * a guest command, and a small set of devices, then executed with
 * {@link #run()} — which blocks until the guest exits. Because
 * {@code krun_vmm_run} consumes the VMM and cannot be interrupted on Linux,
 * callers must run each VM on a dedicated thread/process.
 */
public class FgKrunVm implements AutoCloseable {

  public static final String DEFAULT_ROOTFS_TAG = "/dev/root";

  private final Arena arena = Arena.ofShared();
  private final List<String> args = new ArrayList<>();
  private final List<String> env = new ArrayList<>();
  private final List<Volume> volumes = new ArrayList<>();

  private int vcpus = 1;
  private int ramMib = 256;
  private String workdir = "/";
  private String rootfsTag = DEFAULT_ROOTFS_TAG;
  private File rootfsDir;
  private boolean rootfsReadOnly;
  private int consoleInFd = -1;
  private int consoleOutFd = -1;

  private MemorySegment vmm;
  private boolean ran;

  public record Volume(File hostDir, String tag, boolean readOnly) {}

  public FgKrunVm vcpus(int n) {
    this.vcpus = n;
    return this;
  }

  public FgKrunVm ramMib(int mib) {
    this.ramMib = mib;
    return this;
  }

  public FgKrunVm workdir(String dir) {
    this.workdir = Objects.requireNonNull(dir);
    return this;
  }

  public FgKrunVm rootfs(File dir) {
    this.rootfsDir = Objects.requireNonNull(dir);
    return this;
  }

  public FgKrunVm rootfsReadOnly(boolean readOnly) {
    this.rootfsReadOnly = readOnly;
    return this;
  }

  public FgKrunVm rootfsTag(String tag) {
    this.rootfsTag = Objects.requireNonNull(tag);
    return this;
  }

  public FgKrunVm command(String... argv) {
    for (var a : argv) {
      args.add(a);
    }
    return this;
  }

  public FgKrunVm env(String key, String val) {
    env.add(val == null ? key : key + "=" + val);
    return this;
  }

  /**
   * Adds a host directory shared into the guest as an extra virtiofs device.
   *
   * @param guestTag the filesystem tag visible to the guest
   */
  public FgKrunVm volume(File hostDir, String guestTag, boolean readOnly) {
    volumes.add(new Volume(hostDir, guestTag, readOnly));
    return this;
  }

  public FgKrunVm console(int inputFd, int outputFd) {
    this.consoleInFd = inputFd;
    this.consoleOutFd = outputFd;
    return this;
  }

  /** Builds the VM. Must be called before {@link #run()}. */
  public FgKrunVm build() {
    if (rootfsDir == null) {
      throw new IllegalStateException("rootfs directory is required");
    }

    var payload = FgKrun.payloadLoadKrunfw(arena);
    var overlay = FgKrun.fsOverlayNew();

    var initCell = FgKrun.ptrCell(arena, FgKrun.initConfigBuilder());
    for (var a : args) {
      FgKrun.initBuilderArg(arena, initCell, a);
    }
    for (var e : env) {
      FgKrun.initBuilderEnvVar(arena, initCell, e);
    }
    FgKrun.initBuilderWorkdir(arena, initCell, workdir);
    var initConfig = FgKrun.initBuilderBuild(initCell);
    FgKrun.initConfigApply(arena, initConfig, overlay, payload);

    var devices = FgKrun.mmioDeviceManagerNew();

    if (consoleInFd >= 0 || consoleOutFd >= 0) {
      var consoleBuilder = FgKrun.consoleDeviceBuilder();
      FgKrun.consoleAddDefaultConsole(arena, consoleBuilder, consoleInFd, consoleOutFd, consoleOutFd);
      FgKrun.mmioDeviceManagerAdd(devices, FgKrun.consoleBuilderBuild(arena, consoleBuilder));
    }

    var rootfs = FgKrun.fsDeviceNew(arena, rootfsTag, rootfsDir.getAbsolutePath(), rootfsReadOnly);
    FgKrun.fsDeviceSetOverlay(rootfs, overlay);
    FgKrun.mmioDeviceManagerAdd(devices, rootfs);

    for (var v : volumes) {
      FgKrun.mmioDeviceManagerAdd(devices,
          FgKrun.fsDeviceNew(arena, v.tag(), v.hostDir().getAbsolutePath(), v.readOnly()));
    }

    FgKrun.mmioDeviceManagerAdd(devices, FgKrun.rngDeviceNew(arena));

    var vmmCell = FgKrun.ptrCell(arena, FgKrun.vmmBuilderNew());
    FgKrun.vmmBuilderVcpus(arena, vmmCell, vcpus);
    FgKrun.vmmBuilderRamMib(arena, vmmCell, ramMib);
    FgKrun.vmmBuilderPayload(vmmCell, payload);
    FgKrun.vmmBuilderDevices(vmmCell, devices);
    this.vmm = FgKrun.vmmBuilderBuild(arena, vmmCell);

    return this;
  }

  /** Blocks until the guest exits. Call on a dedicated thread. */
  public void run() {
    if (vmm == null) {
      throw new IllegalStateException("build() must be called before run()");
    }
    ran = true;
    FgKrun.vmmRun(vmm);
  }

  @Override public void close() {
    if (vmm != null && !ran) {
      FgKrun.vmmDestroy(vmm);
    }
    vmm = null;
    arena.close();
  }
}
