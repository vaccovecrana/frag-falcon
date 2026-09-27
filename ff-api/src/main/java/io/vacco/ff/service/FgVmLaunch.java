package io.vacco.ff.service;

import io.vacco.ff.oci.FgEnvVar;
import io.vacco.ff.schema.FgVm;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Builds the native launcher argument vector for a VM. */
public class FgVmLaunch {

  public static List<String> args(FgVm vm, File vmRoot) {
    var a = new ArrayList<String>();
    a.add("--rootfs");
    a.add(FgVmSvc.rootfsOf(vmRoot).getAbsolutePath());
    a.add("--vcpus");
    a.add(Integer.toString(vm.machine.vcpus));
    a.add("--ram");
    a.add(Integer.toString(vm.machine.ramMib));

    var workdir = vm.image != null && vm.image.workingDir != null ? vm.image.workingDir : "/";
    a.add("--workdir");
    a.add(workdir);

    for (var e : env(vm)) {
      a.add("--env");
      a.add(e);
    }
    for (var v : vm.volumes) {
      a.add("--volume");
      a.add(v.hostPath + ":" + v.guestPath + (v.readOnly ? ":ro" : ""));
    }
    if (vm.network != null && vm.network.tapName != null) {
      a.add("--tap");
      a.add(vm.network.tapName);
      a.add("--mac");
      a.add(vm.network.guestMac != null ? vm.network.guestMac : FgVmId.macString(vm.tag.id));
    }

    a.add("--");
    a.addAll(command(vm));
    return a;
  }

  public static List<String> env(FgVm vm) {
    var out = new ArrayList<String>();
    if (vm.image != null && vm.image.env != null) {
      for (var e : vm.image.env) {
        out.add(format(e));
      }
    }
    if (vm.image != null && vm.image.envUsr != null) {
      for (var e : vm.image.envUsr) {
        out.add(format(e));
      }
    }
    return out;
  }

  private static String format(FgEnvVar e) {
    return e.val == null ? e.key : e.key + "=" + e.val;
  }

  public static List<String> command(FgVm vm) {
    if (vm.command != null && !vm.command.isEmpty()) {
      return new ArrayList<>(vm.command);
    }
    var out = new ArrayList<String>();
    if (vm.image != null) {
      if (vm.image.entryPoint != null) {
        for (var s : vm.image.entryPoint) {
          out.add(s);
        }
      }
      if (vm.image.cmd != null) {
        for (var s : vm.image.cmd) {
          out.add(s);
        }
      }
    }
    if (out.isEmpty()) {
      out.add("/bin/sh");
    }
    return out;
  }
}
