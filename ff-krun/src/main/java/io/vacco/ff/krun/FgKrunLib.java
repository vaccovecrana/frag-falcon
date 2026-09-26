package io.vacco.ff.krun;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Extracts and loads the vendored libkrun native libraries.
 *
 * <p>Libraries are loaded with {@code dlopen(RTLD_NOW | RTLD_GLOBAL)} so that
 * libkrun_init's weak {@code dlsym(RTLD_DEFAULT)} lookups can resolve libkrun
 * symbols. Load order matters: {@code libkrun.so.2} dlopen()s
 * {@code libkrunfw.so.5} by soname, so the firmware library is loaded first.
 */
public class FgKrunLib {

  private static final List<String> LIBS = List.of(
      "libkrunfw.so.5",
      "libkrun.so.2",
      "libkrun_init.so"
  );

  private static final int RTLD_NOW = 2;
  private static final int RTLD_GLOBAL = 0x100;

  private static final Linker LINKER = Linker.nativeLinker();

  private static final MethodHandle dlopen = LINKER.downcallHandle(
      LINKER.defaultLookup().find("dlopen").orElseThrow(), FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
  private static final MethodHandle dlerror = LINKER.downcallHandle(
      LINKER.defaultLookup().find("dlerror").orElseThrow(), FunctionDescriptor.of(ADDRESS));

  private static final Arena ARENA = Arena.ofShared();
  private static final Path LIB_DIR;

  static {
    try {
      LIB_DIR = extract();
      for (var lib : LIBS) {
        dlopenGlobal(LIB_DIR.resolve(lib).toAbsolutePath().toString());
      }
    } catch (Throwable e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static void dlopenGlobal(String path) throws Throwable {
    try (var arena = Arena.ofConfined()) {
      var handle = (MemorySegment) dlopen.invokeExact(arena.allocateFrom(path), RTLD_NOW | RTLD_GLOBAL);
      if (handle.address() == 0) {
        throw new IllegalStateException("dlopen failed for [" + path + "]: " + dlerrorString());
      }
    }
  }

  private static String dlerrorString() throws Throwable {
    var seg = (MemorySegment) dlerror.invokeExact();
    if (seg.address() == 0) {
      return "<no dlerror>";
    }
    return seg.reinterpret(Long.MAX_VALUE).getString(0);
  }

  private static Path extract() throws IOException {
    var dir = Files.createTempDirectory("ff-krun-");
    dir.toFile().deleteOnExit();
    for (var lib : LIBS) {
      var res = "/io/vacco/ff/krun/" + lib;
      try (InputStream in = FgKrunLib.class.getResourceAsStream(res)) {
        if (in == null) {
          throw new FileNotFoundException(res);
        }
        var out = dir.resolve(lib);
        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
        out.toFile().setExecutable(true);
        out.toFile().deleteOnExit();
      }
    }
    return dir;
  }

  public static Path libDir() {
    return LIB_DIR;
  }

  public static SymbolLookup lookup(String lib) {
    return SymbolLookup.libraryLookup(LIB_DIR.resolve(lib), ARENA);
  }

  public static SymbolLookup krun() {
    return lookup("libkrun.so.2");
  }

  public static SymbolLookup krunInit() {
    return lookup("libkrun_init.so");
  }
}
