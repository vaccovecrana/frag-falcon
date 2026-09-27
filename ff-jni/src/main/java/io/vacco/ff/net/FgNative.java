package io.vacco.ff.net;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the single directory that holds the native runtime artifacts
 * ({@code fg_jni.so}, {@code fg_vmm}, {@code libkrun*.so}).
 *
 * <p>Resolution order:
 * <ol>
 *   <li>{@code FF_NATIVE_DIR} environment variable (dev/CI/tests),</li>
 *   <li>the directory of the running executable ({@code /proc/self/exe}) for a
 *       flat distribution,</li>
 *   <li>the current working directory as a last resort.</li>
 * </ol>
 * There is no extraction step and no resource-embedding: in production the
 * natives live beside the executable.
 */
public class FgNative {

  private static final Path HOME = resolve();

  private static Path resolve() {
    var override = System.getenv("FF_NATIVE_DIR");
    if (override != null && !override.isBlank()) {
      return Path.of(override).toAbsolutePath().normalize();
    }
    try {
      var exe = Files.readSymbolicLink(Path.of("/proc/self/exe"));
      var dir = exe.toAbsolutePath().getParent();
      if (dir != null) {
        return dir;
      }
    } catch (IOException | UnsupportedOperationException ignored) {
    }
    return Path.of("").toAbsolutePath().normalize();
  }

  public static Path home() {
    return HOME;
  }

  public static Path path(String name) {
    return HOME.resolve(name);
  }

  public static String require(String name) {
    var p = path(name);
    if (!Files.exists(p)) {
      throw new IllegalStateException(
          "Missing native artifact [" + p + "]; set FF_NATIVE_DIR to the directory "
              + "holding fg_jni.so / fg_vmm / libkrun*.so");
    }
    return p.toAbsolutePath().toString();
  }
}
