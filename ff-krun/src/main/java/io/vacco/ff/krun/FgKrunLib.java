package io.vacco.ff.krun;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Extracts and loads the vendored libkrun native libraries.
 *
 * <p>Load order matters: {@code libkrun.so.2} dlopen()s {@code libkrunfw.so.5}
 * by soname, so the firmware library is registered first via an absolute
 * {@link System#load(String)}.
 */
public class FgKrunLib {

  private static final List<String> LIBS = List.of(
      "libkrunfw.so.5",
      "libkrun.so.2",
      "libkrun_init.so"
  );

  private static final Arena ARENA = Arena.ofShared();
  private static final Path LIB_DIR;

  static {
    try {
      LIB_DIR = extract();
      for (var lib : LIBS) {
        System.load(LIB_DIR.resolve(lib).toAbsolutePath().toString());
      }
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static Path extract() throws IOException {
    var dir = Files.createTempDirectory("ff-krun-");
    dir.toFile().deleteOnExit();
    for (var lib : LIBS) {
      var res = "/io/vacco/ff/krun/" + lib;
      try (var in = FgKrunLib.class.getResourceAsStream(res)) {
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
