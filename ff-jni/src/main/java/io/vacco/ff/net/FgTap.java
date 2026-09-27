package io.vacco.ff.net;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the host lifecycle of a VM's TAP device by invoking the native
 * launcher's privileged utility modes ({@code --tap-up}/{@code --tap-down}).
 *
 * <p>A TAP can be attached by only one process, so the device is created
 * persistent here (fd closed) and later opened by the VM launcher.
 */
public class FgTap {

  public static void up(String tapName, String bridge) {
    var args = new ArrayList<String>();
    args.add("--tap-up");
    args.add("--tap");
    args.add(tapName);
    if (bridge != null) {
      args.add("--bridge");
      args.add(bridge);
    }
    run(args);
  }

  public static void down(String tapName) {
    run(List.of("--tap-down", "--tap", tapName));
  }

  private static void run(List<String> args) {
    var pb = new ProcessBuilder();
    pb.command().add(FgProc.launcherPath().toAbsolutePath().toString());
    pb.command().addAll(args);
    pb.environment().put("LD_LIBRARY_PATH", FgProc.launcherLibDir().toAbsolutePath().toString());
    pb.redirectErrorStream(true);
    try {
      var p = pb.start();
      var out = new String(p.getInputStream().readAllBytes());
      int code = p.waitFor();
      if (code != 0) {
        throw new IllegalStateException("tap command " + args + " failed (" + code + "):\n" + out);
      }
    } catch (Exception e) {
      throw new IllegalStateException("Unable to run tap command " + args, e);
    }
  }
}
