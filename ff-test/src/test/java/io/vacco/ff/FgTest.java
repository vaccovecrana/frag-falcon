package io.vacco.ff;

import io.vacco.ff.net.FgProc;
import io.vacco.ff.oci.FgDockerIo;
import io.vacco.ff.oci.FgOciStore;
import io.vacco.shax.logging.ShOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class FgTest {

  static {
    ShOption.setSysProp(ShOption.IO_VACCO_SHAX_DEVMODE, "true");
    ShOption.setSysProp(ShOption.IO_VACCO_SHAX_PRETTYPRINT, "true");
  }

  public static final Logger log = LoggerFactory.getLogger(FgTest.class);

  public record RunResult(int exitCode, String console) {
  }

  public static final File WORK = new File("./build/it");

  private static File rootfs;

  public static synchronized File rootfs() {
    try {
      Files.createDirectories(WORK.toPath());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    if (rootfs == null) {
      var r = new File(WORK, "rootfs");
      FgDockerIo.extract("alpine:latest", r, new FgOciStore(new File(WORK, "oci")));
      rootfs = r;
    }
    return rootfs;
  }

  public static List<String> baseArgs(String vmId) {
    var args = new ArrayList<String>();
    args.add("--rootfs");
    args.add(rootfs().getAbsolutePath());
    args.add("--vcpus");
    args.add("2");
    args.add("--ram");
    args.add("256");
    args.add("--workdir");
    args.add("/");
    args.add("--env");
    args.add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
    return args;
  }

  public static RunResult runVm(String vmId, List<String> args) throws Exception {
    var logFile = new File(WORK, vmId + ".log");
    if (logFile.exists()) {
      logFile.delete();
    }
    int pid = FgProc.spawn(vmId, args, logFile.toPath());
    if (pid <= 0) {
      throw new IllegalStateException("spawn failed for " + vmId + ": " + pid);
    }
    int code = FgProc.waitProcess(pid, 120_000);
    var out = logFile.exists() ? Files.readString(logFile.toPath()) : "";
    log.info("libkrun: vm={} exit={} console:\n{}", vmId, code, out);
    return new RunResult(code, out);
  }

  /**
   * Runs the launcher in a privileged utility mode (e.g. --tap-up / --tap-down).
   */
  public static void runLauncherTool(List<String> args) throws Exception {
    var bin = io.vacco.ff.net.FgProc.launcherPath();
    var pb = new ProcessBuilder();
    pb.command().add(bin.toAbsolutePath().toString());
    pb.command().addAll(args);
    pb.environment().put("LD_LIBRARY_PATH",
        io.vacco.ff.net.FgProc.launcherLibDir().toAbsolutePath().toString());
    pb.redirectErrorStream(true);
    var p = pb.start();
    var out = new String(p.getInputStream().readAllBytes());
    int code = p.waitFor();
    if (code != 0) {
      throw new IllegalStateException("launcher tool failed (" + code + ") for " + args + ":\n" + out);
    }
    log.info("libkrun: {} -> {}", args, out.trim());
  }

  /**
   * True when the configured launcher carries the cap_net_admin file capability.
   */
  public static boolean hasNetCap() {
    var bin = io.vacco.ff.net.FgProc.launcherPath().toAbsolutePath().toString();
    if (bin == null) {
      return false;
    }
    var setcap = new File("/sbin/getcap");
    if (!setcap.exists()) {
      setcap = new File("/usr/sbin/getcap");
    }
    if (!setcap.exists()) {
      return false;
    }
    try {
      var p = new ProcessBuilder(setcap.getAbsolutePath(), bin).redirectErrorStream(true).start();
      var out = new String(p.getInputStream().readAllBytes());
      p.waitFor();
      return out.contains("cap_net_admin");
    } catch (Exception e) {
      return false;
    }
  }

  public static File freshDir(String name) throws Exception {
    var dir = new File(WORK, name);
    if (dir.exists()) {
      deleteRecursively(dir);
    }
    Files.createDirectories(dir.toPath());
    return dir;
  }

  private static void deleteRecursively(File f) throws Exception {
    try (var paths = Files.walk(f.toPath())) {
      paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
        .forEach(p -> {
          try {
            Files.deleteIfExists(p);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    }
  }
}
