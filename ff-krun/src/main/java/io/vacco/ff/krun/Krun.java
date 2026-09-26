package io.vacco.ff.krun;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Minimal Java 25 FFM bindings for the libkrun 2.0 C API (see libkrun.h).
 *
 * <p>This first pass covers the smoke-test surface only; the builder API is
 * layered on top of this class as the migration progresses.
 */
public class Krun {

  public static final long KRUN_RESULT_SUCCESS = 0L;

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

  private static final Linker LINKER = Linker.nativeLinker();
  private static final SymbolLookup LOOKUP = KrunLib.krun();

  private static MethodHandle downcall(String name, FunctionDescriptor d) {
    var symbol = LOOKUP.find(name)
        .orElseThrow(() -> new UnsatisfiedLinkError("libkrun symbol not found: " + name));
    return LINKER.downcallHandle(symbol, d);
  }

  private static final MethodHandle krunCheckNestedVirt = downcall(
      "krun_check_nested_virt",
      FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN)
  );

  private static final MethodHandle krunInitLog = downcall(
      "krun_init_log",
      FunctionDescriptor.of(
          ValueLayout.JAVA_LONG,
          ValueLayout.JAVA_INT,
          ValueLayout.JAVA_INT,
          ValueLayout.JAVA_INT,
          ValueLayout.JAVA_INT,
          ValueLayout.ADDRESS
      )
  );

  private static final MethodHandle krunResultNameCstr = downcall(
      "krun_result_name_cstr",
      FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
  );

  /** @return true when nested virtualization is available on this host. */
  public static boolean checkNestedVirt() {
    try {
      return (boolean) krunCheckNestedVirt.invokeExact();
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /**
   * Configures libkrun logging.
   *
   * @param target  log target (1 = stderr, 2 = stdout, 3 = file path, ...)
   * @param level   one of the {@code KRUN_LOG_LEVEL_*} constants
   * @param style   one of the {@code KRUN_LOG_STYLE_*} constants
   * @param options {@code KRUN_LOG_OPTIONS_*} bitmask
   * @return a {@code KrunResult} value
   */
  public static long initLog(int target, int level, int style, int options) {
    try (var arena = Arena.ofConfined()) {
      var errOut = arena.allocate(ValueLayout.ADDRESS);
      return (long) krunInitLog.invokeExact(target, level, style, options, errOut);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /** Renders a {@code KrunResult} value as its symbolic name. */
  public static String resultName(long result) {
    try {
      var seg = (MemorySegment) krunResultNameCstr.invokeExact(result);
      if (seg.address() == 0) {
        return "<null>";
      }
      return seg.reinterpret(Long.MAX_VALUE).getString(0);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }
}
