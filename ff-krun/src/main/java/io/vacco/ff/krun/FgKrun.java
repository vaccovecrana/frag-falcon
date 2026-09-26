package io.vacco.ff.krun;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Curated Java 25 FFM bindings for the libkrun 2.0 C API.
 *
 * <p>Signatures were validated against {@code jextract} output for
 * {@code libkrun.h} and {@code libkrun_init.h}. Opaque handles are exposed as
 * {@link MemorySegment}; the fluent, lifetime-aware wrapper lives in
 * {@link FgKrunVm}.
 */
public class FgKrun {

  public static final long KRUN_RESULT_SUCCESS = 0L;

  /* KernelFormat */

  /* LogLevel */
  public static final int KRUN_LOG_LEVEL_OFF = 0;
  public static final int KRUN_LOG_LEVEL_ERROR = 1;
  public static final int KRUN_LOG_LEVEL_WARN = 2;
  public static final int KRUN_LOG_LEVEL_INFO = 3;
  public static final int KRUN_LOG_LEVEL_DEBUG = 4;
  public static final int KRUN_LOG_LEVEL_TRACE = 5;

  /* LogStyle */
  public static final int KRUN_LOG_STYLE_AUTO = 0;
  public static final int KRUN_LOG_STYLE_ALWAYS = 1;
  public static final int KRUN_LOG_STYLE_NEVER = 2;

  /* FsDevice overlay file modes */
  public static final int MODE_0644 = 0644;
  public static final int MODE_0755 = 0755;

  public static final StructLayout KRUN_STR = MemoryLayout.structLayout(
      ADDRESS.withName("data"),
      JAVA_LONG.withName("len")
  ).withName("KrunStr");

  public static final StructLayout KRUN_BYTES = MemoryLayout.structLayout(
      ADDRESS.withName("data"),
      JAVA_LONG.withName("len")
  ).withName("KrunBytes");

  private static final Linker LINKER = Linker.nativeLinker();
  private static final SymbolLookup LOOKUP = FgKrunLib.krun();
  private static final SymbolLookup INIT_LOOKUP = FgKrunLib.krunInit();

  private static MethodHandle fn(SymbolLookup lookup, String name, FunctionDescriptor d) {
    var symbol = lookup.find(name)
        .orElseThrow(() -> new UnsatisfiedLinkError("libkrun symbol not found: " + name));
    return LINKER.downcallHandle(symbol, d);
  }

  /* ----- helpers ------------------------------------------------------ */

  /** Builds a by-value {@code KrunStr} from a Java string (or NULL for {@code null}). */
  public static MemorySegment str(Arena arena, String s) {
    var seg = arena.allocate(KRUN_STR);
    if (s == null) {
      seg.set(ADDRESS, 0, MemorySegment.NULL);
      seg.set(JAVA_LONG, 8, 0L);
    } else {
      var bytes = arena.allocateFrom(s);
      seg.set(ADDRESS, 0, bytes);
      seg.set(JAVA_LONG, 8, (long) s.length());
    }
    return seg;
  }

  /** Builds a by-value {@code KrunBytes} from a Java byte array. */
  public static MemorySegment bytes(Arena arena, byte[] b) {
    var seg = arena.allocate(KRUN_BYTES);
    if (b == null) {
      seg.set(ADDRESS, 0, MemorySegment.NULL);
      seg.set(JAVA_LONG, 8, 0L);
    } else {
      var data = arena.allocate(b.length);
      MemorySegment.copy(b, 0, data, JAVA_BYTE, 0, b.length);
      seg.set(ADDRESS, 0, data);
      seg.set(JAVA_LONG, 8, (long) b.length);
    }
    return seg;
  }

  /** Allocates a single-pointer cell, used for {@code T*} builder parameters. */
  public static MemorySegment ptrCell(Arena arena, MemorySegment value) {
    var cell = arena.allocate(ADDRESS);
    cell.set(ADDRESS, 0, value);
    return cell;
  }

  /** Allocates a single-int cell, used for {@code uint32_t*} out-parameters. */
  public static MemorySegment intCell(Arena arena) {
    return arena.allocate(JAVA_INT);
  }

  /** Allocates a single-pointer out-parameter cell initialized to NULL. */
  public static MemorySegment errCell(Arena arena) {
    var cell = arena.allocate(ADDRESS);
    cell.set(ADDRESS, 0, MemorySegment.NULL);
    return cell;
  }

  public static void check(long result, String op) {
    if (result != KRUN_RESULT_SUCCESS) {
      throw new IllegalStateException(op + " failed: " + resultName(result) + " (" + result + ")");
    }
  }

  /* ----- free functions ---------------------------------------------- */

  private static final MethodHandle checkNestedVirt = fn(LOOKUP, "krun_check_nested_virt",
      FunctionDescriptor.of(JAVA_BOOLEAN));
  private static final MethodHandle initLog = fn(LOOKUP, "krun_init_log",
      FunctionDescriptor.of(JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS));
  private static final MethodHandle resultNameCstr = fn(LOOKUP, "krun_result_name_cstr",
      FunctionDescriptor.of(ADDRESS, JAVA_LONG));

  public static boolean checkNestedVirt() {
    try {
      return (boolean) checkNestedVirt.invokeExact();
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  private static boolean logInitialized;

  /**
   * Configures libkrun logging. libkrun only allows this once per process; repeat
   * calls are ignored and report success.
   */
  public static synchronized long initLog(int target, int level, int style, int options) {
    if (logInitialized) {
      return KRUN_RESULT_SUCCESS;
    }
    try (var arena = Arena.ofConfined()) {
      var result = (long) initLog.invokeExact(target, level, style, options, errCell(arena));
      logInitialized = true;
      return result;
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static String resultName(long result) {
    try {
      var seg = (MemorySegment) resultNameCstr.invokeExact(result);
      if (seg.address() == 0) {
        return "<null>";
      }
      return seg.reinterpret(Long.MAX_VALUE).getString(0);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /* ----- payload ------------------------------------------------------ */

  private static final MethodHandle payloadLoadKrunfw = fn(LOOKUP, "krun_payload_load_krunfw",
      FunctionDescriptor.of(ADDRESS, ADDRESS));
  private static final MethodHandle payloadAppendCmdline = fn(LOOKUP, "krun_payload_append_cmdline",
      FunctionDescriptor.ofVoid(ADDRESS, KRUN_STR));

  public static MemorySegment payloadLoadKrunfw(Arena arena) {
    try {
      var err = errCell(arena);
      var payload = (MemorySegment) payloadLoadKrunfw.invokeExact(err);
      if (payload.address() == 0) {
        throw new IllegalStateException("krun_payload_load_krunfw failed (is libkrunfw.so.5 loadable?)");
      }
      return payload;
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static void payloadAppendCmdline(Arena arena, MemorySegment payload, String extra) {
    try {
      payloadAppendCmdline.invokeExact(payload, str(arena, extra));
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /* ----- fs overlay / devices ---------------------------------------- */

  private static final MethodHandle fsOverlayNew = fn(LOOKUP, "krun_fs_overlay_new",
      FunctionDescriptor.of(ADDRESS));
  private static final MethodHandle fsOverlayAddFile = fn(LOOKUP, "krun_fs_overlay_add_file",
      FunctionDescriptor.of(JAVA_LONG, ADDRESS, KRUN_STR, KRUN_BYTES, JAVA_INT, JAVA_BOOLEAN, ADDRESS));
  private static final MethodHandle fsDeviceNew = fn(LOOKUP, "krun_fs_device_new",
      FunctionDescriptor.of(ADDRESS, KRUN_STR, KRUN_STR, ADDRESS));
  private static final MethodHandle fsDeviceNewReadOnly = fn(LOOKUP, "krun_fs_device_new_read_only",
      FunctionDescriptor.of(ADDRESS, KRUN_STR, KRUN_STR, ADDRESS));
  private static final MethodHandle fsDeviceSetOverlay = fn(LOOKUP, "krun_fs_device_set_overlay",
      FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

  public static MemorySegment fsOverlayNew() {
    try {
      return (MemorySegment) fsOverlayNew.invokeExact();
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static void fsOverlayAddFile(Arena arena, MemorySegment overlay, String path,
                                      byte[] data, int mode, boolean oneShot) {
    try {
      var result = (long) fsOverlayAddFile.invokeExact(
          overlay, str(arena, path), bytes(arena, data), mode, oneShot, errCell(arena));
      check(result, "krun_fs_overlay_add_file(" + path + ")");
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static MemorySegment fsDeviceNew(Arena arena, String tag, String hostPath, boolean readOnly) {
    try {
      var err = errCell(arena);
      var handle = readOnly
          ? (MemorySegment) fsDeviceNewReadOnly.invokeExact(str(arena, tag), str(arena, hostPath), err)
          : (MemorySegment) fsDeviceNew.invokeExact(str(arena, tag), str(arena, hostPath), err);
      if (handle.address() == 0) {
        throw new IllegalStateException("krun_fs_device_new failed for tag " + tag);
      }
      return handle;
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static void fsDeviceSetOverlay(MemorySegment device, MemorySegment overlay) {
    try {
      fsDeviceSetOverlay.invokeExact(device, overlay);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /* ----- console ------------------------------------------------------ */

  private static final MethodHandle consoleDeviceBuilder = fn(LOOKUP, "krun_console_device_builder",
      FunctionDescriptor.of(ADDRESS));
  private static final MethodHandle consoleAddInoutPort = fn(LOOKUP, "krun_console_builder_add_inout_port",
      FunctionDescriptor.of(JAVA_LONG, ADDRESS, KRUN_STR, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
  private static final MethodHandle consoleAddDefaultConsole = fn(LOOKUP, "krun_console_builder_add_default_console",
      FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS));
  private static final MethodHandle consoleBuilderBuild = fn(LOOKUP, "krun_console_builder_build",
      FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

  public static MemorySegment consoleDeviceBuilder() {
    try {
      return (MemorySegment) consoleDeviceBuilder.invokeExact();
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static int consoleAddInoutPort(Arena arena, MemorySegment builder, String name,
                                        int inputFd, int outputFd) {
    try {
      var resultCell = intCell(arena);
      var result = (long) consoleAddInoutPort.invokeExact(
          builder, str(arena, name), inputFd, outputFd, resultCell, errCell(arena));
      check(result, "krun_console_builder_add_inout_port");
      return resultCell.get(JAVA_INT, 0);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static MemorySegment consoleBuilderBuild(Arena arena, MemorySegment builder) {
    try {
      var err = errCell(arena);
      var device = (MemorySegment) consoleBuilderBuild.invokeExact(builder, err);
      if (device.address() == 0) {
        throw new IllegalStateException("krun_console_builder_build failed");
      }
      return device;
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /**
   * Adds the default console: port 0 plus the named redirect ports
   * ({@code krun-stdin}/{@code krun-stdout}/{@code krun-stderr}) that libkrun's
   * guest init maps onto the workload's stdio.
   */
  public static void consoleAddDefaultConsole(Arena arena, MemorySegment builder,
                                              int stdin, int stdout, int stderr) {
    try {
      var result = (long) consoleAddDefaultConsole.invokeExact(builder, stdin, stdout, stderr, errCell(arena));
      check(result, "krun_console_builder_add_default_console");
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /* ----- mmio device manager / misc devices --------------------------- */

  private static final MethodHandle mmioDeviceManagerNew = fn(LOOKUP, "krun_mmio_device_manager_new",
      FunctionDescriptor.of(ADDRESS));
  private static final MethodHandle mmioDeviceManagerAdd = fn(LOOKUP, "krun_mmio_device_manager_add",
      FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
  private static final MethodHandle rngDeviceNew = fn(LOOKUP, "krun_rng_device_new",
      FunctionDescriptor.of(ADDRESS, ADDRESS));
  private static final MethodHandle balloonDeviceNew = fn(LOOKUP, "krun_balloon_device_new",
      FunctionDescriptor.of(ADDRESS, ADDRESS));

  public static MemorySegment mmioDeviceManagerNew() {
    try {
      return (MemorySegment) mmioDeviceManagerNew.invokeExact();
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static void mmioDeviceManagerAdd(MemorySegment manager, MemorySegment device) {
    try {
      mmioDeviceManagerAdd.invokeExact(manager, device);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static MemorySegment rngDeviceNew(Arena arena) {
    try {
      return (MemorySegment) rngDeviceNew.invokeExact(errCell(arena));
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /* ----- vmm builder / vmm ------------------------------------------- */

  private static final MethodHandle vmmBuilderNew = fn(LOOKUP, "krun_vmm_builder_new",
      FunctionDescriptor.of(ADDRESS));
  private static final MethodHandle vmmBuilderVcpus = fn(LOOKUP, "krun_vmm_builder_vcpus",
      FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_BYTE, ADDRESS));
  private static final MethodHandle vmmBuilderRamMib = fn(LOOKUP, "krun_vmm_builder_ram_mib",
      FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, ADDRESS));
  private static final MethodHandle vmmBuilderPayload = fn(LOOKUP, "krun_vmm_builder_payload",
      FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
  private static final MethodHandle vmmBuilderDevices = fn(LOOKUP, "krun_vmm_builder_devices",
      FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
  private static final MethodHandle vmmBuilderBuild = fn(LOOKUP, "krun_vmm_builder_build",
      FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
  private static final MethodHandle vmmRun = fn(LOOKUP, "krun_vmm_run",
      FunctionDescriptor.ofVoid(ADDRESS));
  private static final MethodHandle vmmDestroy = fn(LOOKUP, "krun_vmm_destroy",
      FunctionDescriptor.ofVoid(ADDRESS));

  public static MemorySegment vmmBuilderNew() {
    try {
      return (MemorySegment) vmmBuilderNew.invokeExact();
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static void vmmBuilderVcpus(Arena arena, MemorySegment builderCell, int count) {
    try {
      var result = (long) vmmBuilderVcpus.invokeExact(builderCell, (byte) count, errCell(arena));
      check(result, "krun_vmm_builder_vcpus");
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static void vmmBuilderRamMib(Arena arena, MemorySegment builderCell, int mib) {
    try {
      var result = (long) vmmBuilderRamMib.invokeExact(builderCell, mib, errCell(arena));
      check(result, "krun_vmm_builder_ram_mib");
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static void vmmBuilderPayload(MemorySegment builderCell, MemorySegment payload) {
    try {
      vmmBuilderPayload.invokeExact(builderCell, payload);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static void vmmBuilderDevices(MemorySegment builderCell, MemorySegment devices) {
    try {
      vmmBuilderDevices.invokeExact(builderCell, devices);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static MemorySegment vmmBuilderBuild(Arena arena, MemorySegment builderCell) {
    try {
      var err = errCell(arena);
      var vmm = (MemorySegment) vmmBuilderBuild.invokeExact(builderCell, err);
      if (vmm.address() == 0) {
        throw new IllegalStateException("krun_vmm_builder_build failed");
      }
      return vmm;
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /** Blocks until the VM exits. Must be called on a dedicated thread. */
  public static void vmmRun(MemorySegment vmm) {
    try {
      vmmRun.invokeExact(vmm);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static void vmmDestroy(MemorySegment vmm) {
    try {
      vmmDestroy.invokeExact(vmm);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /* ----- init config (libkrun_init.so) -------------------------------- */

  private static final MethodHandle initConfigBuilder = fn(INIT_LOOKUP, "krun_init_config_builder",
      FunctionDescriptor.of(ADDRESS));
  private static final MethodHandle initBuilderArg = fn(INIT_LOOKUP, "krun_init_builder_arg",
      FunctionDescriptor.ofVoid(ADDRESS, KRUN_STR));
  private static final MethodHandle initBuilderEnvVar = fn(INIT_LOOKUP, "krun_init_builder_env_var",
      FunctionDescriptor.ofVoid(ADDRESS, KRUN_STR));
  private static final MethodHandle initBuilderWorkdir = fn(INIT_LOOKUP, "krun_init_builder_workdir",
      FunctionDescriptor.ofVoid(ADDRESS, KRUN_STR));
  private static final MethodHandle initBuilderBuild = fn(INIT_LOOKUP, "krun_init_builder_build",
      FunctionDescriptor.of(ADDRESS, ADDRESS));
  private static final MethodHandle initConfigApply = fn(INIT_LOOKUP, "krun_init_config_apply",
      FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

  public static MemorySegment initConfigBuilder() {
    try {
      return (MemorySegment) initConfigBuilder.invokeExact();
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static void initBuilderArg(Arena arena, MemorySegment builderCell, String arg) {
    try {
      initBuilderArg.invokeExact(builderCell, str(arena, arg));
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static void initBuilderEnvVar(Arena arena, MemorySegment builderCell, String var) {
    try {
      initBuilderEnvVar.invokeExact(builderCell, str(arena, var));
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static void initBuilderWorkdir(Arena arena, MemorySegment builderCell, String dir) {
    try {
      initBuilderWorkdir.invokeExact(builderCell, str(arena, dir));
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static MemorySegment initBuilderBuild(MemorySegment builderCell) {
    try {
      var config = (MemorySegment) initBuilderBuild.invokeExact(builderCell);
      if (config.address() == 0) {
        throw new IllegalStateException("krun_init_builder_build failed");
      }
      return config;
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static void initConfigApply(Arena arena, MemorySegment config, MemorySegment overlay,
                                     MemorySegment payload) {
    try {
      var result = (long) initConfigApply.invokeExact(config, overlay, payload, errCell(arena));
      check(result, "krun_init_config_apply");
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }
}
