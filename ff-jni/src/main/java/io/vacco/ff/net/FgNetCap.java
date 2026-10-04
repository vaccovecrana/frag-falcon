package io.vacco.ff.net;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Detects whether the TAP/bridge capability ({@code CAP_NET_ADMIN}) is available
 * to the VM launcher. The hypervisor refuses to start without it, since it cannot
 * create the per-VM network devices a stack needs.
 *
 * <p>The capability can come from any of:
 * <ul>
 *   <li>the process's own effective set (e.g. systemd {@code AmbientCapabilities});</li>
 *   <li>running as root (effective uid 0);</li>
 *   <li>the {@code cap_net_admin} file capability on {@code fg_vmm} (setcap).</li>
 * </ul>
 */
public class FgNetCap {

  /** CAP_NET_ADMIN is bit 12 in the capabilities bitmask. */
  private static final long CAP_NET_ADMIN = 1L << 12;

  public record Result(boolean ok, String source) {
  }

  private static boolean processHasCap() {
    try {
      for (var line : Files.readAllLines(Path.of("/proc/self/status"))) {
        if (line.startsWith("CapEff:")) {
          var hex = line.substring("CapEff:".length()).trim();
          return (Long.parseUnsignedLong(hex, 16) & CAP_NET_ADMIN) != 0;
        }
      }
    } catch (Exception ignored) {
    }
    return false;
  }

  private static boolean launcherFileHasCap(Path launcher) {
    var bin = launcher.toAbsolutePath().toString();
    for (var p : List.of("/sbin/getcap", "/usr/sbin/getcap")) {
      var getcap = new File(p);
      if (!getcap.exists()) {
        continue;
      }
      try {
        var proc = new ProcessBuilder(getcap.getAbsolutePath(), bin).redirectErrorStream(true).start();
        var out = new String(proc.getInputStream().readAllBytes());
        proc.waitFor();
        return out.contains("cap_net_admin");
      } catch (Exception ignored) {
      }
    }
    return false;
  }

  /** Resolves whether CAP_NET_ADMIN is available, and from where. */
  public static Result check() {
    return check(FgProc.launcherPath());
  }

  /** Same as {@link #check()}, against an explicit launcher binary (testable). */
  public static Result check(Path launcher) {
    if (processHasCap()) {
      return new Result(true, "process effective set");
    }
    if (launcher != null && launcherFileHasCap(launcher)) {
      return new Result(true, "fg_vmm file capability");
    }
    return new Result(false, "none");
  }

  public static boolean hasNetAdmin() {
    return check().ok();
  }
}
