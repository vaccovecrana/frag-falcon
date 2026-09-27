package io.vacco.ff.net;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Per-VM process management: extracts the native launcher and libkrun shared
 * objects, spawns a detached launcher tagged with {@code FF_VMID}, and
 * re-discovers running VMs by scanning {@code /proc}.
 */
public class FgProc {

  private static final Pattern numeric = Pattern.compile("\\d+");

  private static final List<String> NATIVE_FILES = List.of(
    "fg_vmm",
    "libkrun.so.2",
    "libkrun_init.so",
    "libkrunfw.so.5"
  );

  private static Path nativeDir;

  private static synchronized Path extractNative() {
    if (nativeDir != null) {
      return nativeDir;
    }
    try {
      var dir = Files.createTempDirectory("ff-vmm-");
      dir.toFile().deleteOnExit();
      for (var name : NATIVE_FILES) {
        var target = dir.resolve(name);
        try (var in = FgProc.class.getResourceAsStream("/io/vacco/ff/" + name);
             var out = Files.newOutputStream(target)) {
          Objects.requireNonNull(in, name).transferTo(out);
        }
        target.toFile().setExecutable(true);
        target.toFile().deleteOnExit();
      }
      nativeDir = dir;
      return dir;
    } catch (IOException e) {
      throw new IllegalStateException("Unable to extract native VM launcher", e);
    }
  }

  public static Path nativeDir() {
    return extractNative();
  }

  /**
   * Path to the native launcher (FF_VMM_BIN override, else extracted).
   */
  public static Path launcherPath() {
    var override = System.getenv("FF_VMM_BIN");
    return override != null ? Path.of(override) : extractNative().resolve("fg_vmm");
  }

  /**
   * Directory holding the libkrun shared objects (FF_VMM_LIBDIR override, else extracted).
   */
  public static Path launcherLibDir() {
    var override = System.getenv("FF_VMM_LIBDIR");
    return override != null ? Path.of(override) : extractNative();
  }

  /**
   * Spawns the native launcher for a VM.
   *
   * @param vmId VM identifier, exported to the child as {@code FF_VMID}
   * @param args launcher arguments (rootfs, vcpus, ram, volumes, command, ...)
   * @param log  guest console log file (stdout/stderr of the launcher)
   * @return the child pid, or -1 on failure
   */
  public static int spawn(String vmId, List<String> args, Path log) {
    var cmd = launcherPath().toAbsolutePath().toString();
    var libPath = launcherLibDir().toAbsolutePath().toString();
    var logPath = log == null ? null : log.toAbsolutePath().toString();
    return FgJni.spawnProcess(vmId, cmd, args.toArray(String[]::new), logPath, libPath);
  }

  public static int terminate(int pid) {
    return FgJni.terminate(pid);
  }

  /**
   * Waits for a spawned launcher to exit.
   *
   * @return the guest exit code, -1 on wait error, -2 on timeout
   */
  public static int waitProcess(int pid, int timeoutMs) {
    return FgJni.waitProcess(pid, timeoutMs);
  }

  public static boolean isAlive(int pid) {
    return pid > 0 && Files.isDirectory(Path.of("/proc/" + pid));
  }

  /**
   * Finds the pid of a running VM by its {@code FF_VMID} tag, or -1.
   */
  public static int pidOf(String vmId) {
    var comm = "ff-" + vmId;
    if (comm.length() > 15) {
      comm = comm.substring(0, 15);
    }
    var expected = comm;
    try (var paths = Files.list(Path.of("/proc"))) {
      return paths
        .filter(Files::isDirectory)
        .filter(path -> numeric.matcher(path.getFileName().toString()).matches())
        .filter(path -> !path.getFileName().toString().equals("1"))
        .filter(path -> !isZombie(path))
        .filter(path -> matchesVm(path, expected, vmId))
        .mapToInt(path -> Integer.parseInt(path.getFileName().toString()))
        .findFirst()
        .orElse(-1);
    } catch (IOException e) {
      return -1;
    }
  }

  private static boolean isZombie(Path procDir) {
    try {
      var stat = Files.readString(procDir.resolve("stat"));
      int rp = stat.lastIndexOf(')');
      return rp >= 0 && rp + 2 < stat.length() && stat.charAt(rp + 2) == 'Z';
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Reaps any exited child launchers (avoids zombies in the hypervisor).
   */
  public static int reap() {
    return FgJni.reapChildren();
  }

  private static boolean matchesVm(Path procDir, String comm, String vmId) {
    // A launcher with file capabilities is non-dumpable, so /proc/<pid>/environ
    // is root-only; the process name (comm) is always readable.
    try {
      if (Files.readString(procDir.resolve("comm")).trim().equals(comm)) {
        return true;
      }
    } catch (IOException ignored) {
    }
    return hasVmId(procDir.resolve("environ"), vmId);
  }

  private static boolean hasVmId(Path environ, String vmId) {
    if (!Files.exists(environ)) {
      return false;
    }
    try {
      return Files.readString(environ).contains("FF_VMID=" + vmId + '\0');
    } catch (IOException e) {
      return false;
    }
  }
}
