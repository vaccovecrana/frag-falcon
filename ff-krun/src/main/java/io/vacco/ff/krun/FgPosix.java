package io.vacco.ff.krun;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Minimal libc helpers needed to hand raw file descriptors to libkrun devices.
 *
 * <p>TODO: relocate to a host-primitives module (ff-host) once it exists.
 */
public class FgPosix {

  public record Temp(int fd, Path path) {}

  private static final Linker LINKER = Linker.nativeLinker();
  private static final SymbolLookup LIBC = LINKER.defaultLookup();

  private static MethodHandle fn(String name, FunctionDescriptor d) {
    return LINKER.downcallHandle(
        LIBC.find(name).orElseThrow(() -> new UnsatisfiedLinkError(name)), d);
  }

  private static final MethodHandle mkstemp = fn("mkstemp", FunctionDescriptor.of(JAVA_INT, ADDRESS));
  private static final MethodHandle close = fn("close", FunctionDescriptor.of(JAVA_INT, JAVA_INT));

  /**
   * Creates a unique temporary file, returning its open (O_RDWR) descriptor and path.
   * Caller owns the descriptor and must {@link #close(int)} it.
   */
  public static Temp mkstemp(String template) {
    try (var arena = Arena.ofConfined()) {
      var buf = arena.allocateFrom(template);
      int fd = (int) mkstemp.invokeExact(buf);
      if (fd < 0) {
        throw new IllegalStateException("mkstemp failed for template " + template);
      }
      return new Temp(fd, Path.of(buf.getString(0)));
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static void close(int fd) {
    try {
      close.invokeExact(fd);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /** Returns a writable {@link MemorySegment} view of the given string (null-terminated). */
  public static MemorySegment cstr(Arena arena, String s) {
    return arena.allocateFrom(s);
  }
}
