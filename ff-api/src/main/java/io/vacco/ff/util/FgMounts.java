package io.vacco.ff.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code /proc/self/mountinfo} to audit the mount options covering a path.
 *
 * <p>The recommended deployment mounts the VM storage directory (or a parent)
 * {@code nosuid,nodev,noexec} once at the OS level, so files a guest plants in
 * its rootfs (setuid binaries, device nodes, executables) are inert to
 * host-side processes. The hypervisor runs unprivileged and only <em>verifies</em>
 * this; it never mounts anything itself.
 */
public class FgMounts {

  private static final String[] REQUIRED = {"nosuid", "nodev", "noexec"};

  /**
   * @param mountPoint the mount point covering the queried path
   * @param options    comma-separated per-mount options
   */
  public record MountInfo(String mountPoint, String options) {

    public boolean hardened() {
      for (var r : REQUIRED) {
        if (!hasOption(r)) {
          return false;
        }
      }
      return true;
    }

    public boolean hasOption(String option) {
      for (var o : options.split(",")) {
        if (o.trim().equals(option)) {
          return true;
        }
      }
      return false;
    }

    public List<String> missing() {
      var out = new ArrayList<String>();
      for (var r : REQUIRED) {
        if (!hasOption(r)) {
          out.add(r);
        }
      }
      return out;
    }
  }

  /**
   * Finds the mount (longest mount-point prefix) covering {@code path}.
   */
  public static MountInfo check(File path) {
    try {
      return check(path.getCanonicalPath(), readMountInfo());
    } catch (IOException e) {
      return new MountInfo("", "");
    }
  }

  static List<MountInfo> readMountInfo() throws IOException {
    return parse(Files.readString(Path.of("/proc/self/mountinfo")));
  }

  public static MountInfo check(String canonicalPath, List<MountInfo> mounts) {
    MountInfo best = null;
    for (var m : mounts) {
      var mp = m.mountPoint();
      if (canonicalPath.equals(mp) || canonicalPath.startsWith(mp.endsWith("/") ? mp : mp + "/")) {
        if (best == null || mp.length() > best.mountPoint().length()) {
          best = m;
        }
      }
    }
    return best != null ? best : new MountInfo("/", "");
  }

  public static List<MountInfo> parse(String mountInfo) {
    var out = new ArrayList<MountInfo>();
    for (var line : mountInfo.split("\n")) {
      if (line.isBlank()) {
        continue;
      }
      var f = line.split(" ");
      if (f.length < 6) {
        continue;
      }
      out.add(new MountInfo(unescape(f[4]), f[5]));
    }
    return out;
  }

  private static String unescape(String s) {
    if (s.indexOf('\\') < 0) {
      return s;
    }
    var sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      var c = s.charAt(i);
      if (c == '\\' && i + 3 < s.length()) {
        try {
          sb.append((char) Integer.parseInt(s.substring(i + 1, i + 4), 8));
          i += 3;
          continue;
        } catch (NumberFormatException ignored) {
        }
      }
      sb.append(c);
    }
    return sb.toString();
  }
}
