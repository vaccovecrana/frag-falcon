package io.vacco.ff.net;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * JNI bindings for host primitives: process spawn/tagging, kernel-confined tar
 * extraction, and Linux bridge discovery.
 */
@SuppressWarnings("restricted")
public class FgJni {

  static {
    System.load(extractLib().toAbsolutePath().toString());
  }

  private static Path extractLib() {
    try {
      var dir = Files.createTempDirectory("ff-jni-");
      dir.toFile().deleteOnExit();
      var lib = dir.resolve("fg_jni.so");
      try (var in = FgJni.class.getResourceAsStream("/io/vacco/ff/fg_jni.so");
           var out = Files.newOutputStream(lib)) {
        Objects.requireNonNull(in, "fg_jni.so").transferTo(out);
      }
      lib.toFile().deleteOnExit();
      return lib;
    } catch (IOException e) {
      throw new IllegalStateException("Unable to extract fg_jni.so", e);
    }
  }

  // Process management
  public static native int spawnProcess(String command, String[] args, String logPath, String ldLibraryPath);

  public static native int terminate(int pid);

  public static native int waitProcess(int pid, int timeoutMs);

  public static native int reapChildren();

  /**
   * Extracts a tar archive into {@code rootDir} using kernel-confined path ops.
   */
  public static native int extractTar(String tarPath, String rootDir);

  /**
   * Maps an errno value to its message.
   */
  public static native String strerror(int err);

  public static List<String> getLinuxBridgeInterfaces() {
    var bridges = new ArrayList<String>();
    var netDirectory = Paths.get("/sys/class/net/");
    try (var ps = Files.walk(netDirectory, 1)) {
      ps
        .filter(Files::isDirectory)
        .forEach(path -> {
          var bridgePath = path.resolve("bridge");
          if (Files.isDirectory(bridgePath)) {
            bridges.add(path.getFileName().toString());
          }
        });
      return bridges;
    } catch (IOException e) {
      throw new IllegalStateException("Unable to list Linux bridges", e);
    }
  }
}
