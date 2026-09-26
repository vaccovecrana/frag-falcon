package io.vacco.ff.vmm;

import io.vacco.ff.krun.FgKrun;
import io.vacco.ff.krun.FgKrunVm;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Per-VM launcher process.
 *
 * <p>libkrun terminates the host process with {@code _exit()} when the guest
 * shuts down, so exactly one VM must be hosted by this process. The hypervisor
 * forks this launcher (tagged with {@code FF_VMID} in the environment, so it can
 * be re-discovered under {@code /proc}) and owns VM lifecycle.
 *
 * <p>Usage:
 * <pre>
 *   ff-vmm --rootfs DIR [--tag TAG] [--vcpus N] [--ram MIB] [--workdir DIR]
 *          [--env K=V]... [--volume HOSTDIR:GUESTTAG[:ro]]... -- CMD [ARGS...]
 * </pre>
 */
public class FgVmmMain {

  public static void main(String[] args) {
    int sep = indexOf(args, "--");
    var opts = sep < 0 ? args : Arrays.copyOfRange(args, 0, sep);
    var command = sep < 0 ? new String[0] : Arrays.copyOfRange(args, sep + 1, args.length);

    String rootfs = null;
    String tag = FgKrunVm.DEFAULT_ROOTFS_TAG;
    String workdir = "/";
    int vcpus = 1;
    int ramMib = 256;
    var env = new ArrayList<String>();
    var volumes = new ArrayList<String>();

    for (int i = 0; i < opts.length; i++) {
      var o = opts[i];
      switch (o) {
        case "--rootfs" -> rootfs = opts[++i];
        case "--tag" -> tag = opts[++i];
        case "--workdir" -> workdir = opts[++i];
        case "--vcpus" -> vcpus = Integer.parseInt(opts[++i]);
        case "--ram" -> ramMib = Integer.parseInt(opts[++i]);
        case "--env" -> env.add(opts[++i]);
        case "--volume" -> volumes.add(opts[++i]);
        default -> throw new IllegalArgumentException("unknown option: " + o);
      }
    }

    if (rootfs == null) {
      throw new IllegalArgumentException("--rootfs is required");
    }
    if (command.length == 0) {
      command = new String[]{"/bin/sh"};
    }

    FgKrun.initLog(1, FgKrun.KRUN_LOG_LEVEL_WARN, FgKrun.KRUN_LOG_STYLE_ALWAYS, 0);

    var vm = new FgKrunVm()
        .rootfs(new File(rootfs))
        .rootfsTag(tag)
        .vcpus(vcpus)
        .ramMib(ramMib)
        .workdir(workdir)
        .command(command)
        .console(0, 1);

    for (var e : env) {
      var eq = e.indexOf('=');
      if (eq < 0) {
        vm.env(e, null);
      } else {
        vm.env(e.substring(0, eq), e.substring(eq + 1));
      }
    }

    for (var v : volumes) {
      var p = v.split(":");
      if (p.length < 2) {
        throw new IllegalArgumentException("invalid --volume (expected HOSTDIR:GUESTTAG[:ro]): " + v);
      }
      var readOnly = p.length > 2 && p[2].equals("ro");
      vm.volume(new File(p[0]), p[1], readOnly);
    }

    // Never returns: libkrun _exit()s this process when the guest exits.
    vm.build().run();
  }

  private static int indexOf(String[] args, String needle) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals(needle)) {
        return i;
      }
    }
    return -1;
  }
}
