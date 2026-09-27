package io.vacco.ff.net;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Per-VM process management: spawns a detached launcher (whose process name is
 * the VM id) and re-discovers it via {@code /proc}. The native launcher and
 * libkrun shared objects live in {@link FgNative#home()}.
 */
public class FgProc {

  private static final Pattern numeric = Pattern.compile("\\d+");

  /**
   * Path to the native launcher.
   */
  public static Path launcherPath() {
    return FgNative.path("fg_vmm");
  }

  /**
   * Directory holding the libkrun shared objects.
   */
  public static Path launcherLibDir() {
    return FgNative.home();
  }

  /**
   * Spawns the native launcher for a VM.
   *
   * @param vmId VM identifier, passed to the child as {@code --vm-id} and used
   *             as its process name for discovery
   * @param args launcher arguments (rootfs, vcpus, ram, volumes, command, ...)
   * @param log  guest console log file (stdout/stderr of the launcher)
   * @return the child pid, or -1 on failure
   */
  public static int spawn(String vmId, List<String> args, Path log) {
    var cmd = launcherPath().toAbsolutePath().toString();
    var libPath = launcherLibDir().toAbsolutePath().toString();
    var logPath = log == null ? null : log.toAbsolutePath().toString();
    var argv = new ArrayList<String>(args.size() + 2);
    argv.add("--vm-id");
    argv.add(vmId);
    argv.addAll(args);
    return FgJni.spawnProcess(cmd, argv.toArray(String[]::new), logPath, libPath);
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
   * Finds the pid of a running VM by its {@code FF_VMID} / process name, or -1.
   * The launcher sets its process name ({@code /proc/<pid>/comm}) to exactly the
   * VM id; this is the only discovery mechanism.
   */
  public static int pidOf(String vmId) {
    try (var paths = Files.list(Path.of("/proc"))) {
      return paths
        .filter(Files::isDirectory)
        .filter(path -> numeric.matcher(path.getFileName().toString()).matches())
        .filter(path -> !path.getFileName().toString().equals("1"))
        .filter(path -> !isZombie(path))
        .filter(path -> matchesVm(path, vmId))
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

  private static boolean matchesVm(Path procDir, String vmId) {
    // A launcher with file capabilities is non-dumpable, so /proc/<pid>/environ
    // is root-only; the process name (comm) is always readable.
    try {
      return Files.readString(procDir.resolve("comm")).trim().equals(vmId);
    } catch (IOException e) {
      return false;
    }
  }

}
