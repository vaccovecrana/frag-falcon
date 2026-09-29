package io.vacco.ff.service;

import io.vacco.ff.schema.FgVm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Builds the native launcher argument vector for a VM.
 */
public class FgVmLaunch {

  private static final Logger log = LoggerFactory.getLogger(FgVmLaunch.class);

  public static List<String> args(FgVm vm, File vmRoot) {
    var a = new ArrayList<String>();
    a.add("--rootfs");
    a.add(FgVmSvc.rootfsOf(vmRoot).getAbsolutePath());
    a.add("--vcpus");
    a.add(Integer.toString(vm.machine.vcpus));
    a.add("--ram");
    a.add(Integer.toString(vm.machine.ramMib));

    var workdir = vm.image != null && vm.image.workingDir != null && !vm.image.workingDir.isBlank()
      ? vm.image.workingDir
      : "/";
    a.add("--workdir");
    a.add(workdir);

    a.add("--log-file");
    a.add(FgVmSvc.logOf(vmRoot).getAbsolutePath());
    a.add("--log-lines");
    a.add(Integer.toString(4096));

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

  /**
   * Resolves the guest environment following Docker semantics: image {@code ENV}
   * is inherited, and the service {@code environment:} entries override it per
   * key. The result is de-duplicated (the guest init keeps the first occurrence
   * of a key), so service values always win. A bare key (no {@code =}) is
   * emitted with an empty value ({@code KEY=}) so software that only checks for
   * key presence works; it is <em>not</em> a host-environment passthrough.
   */
  public static List<String> env(FgVm vm) {
    var merged = new LinkedHashMap<String, String>();
    if (vm.image != null && vm.image.env != null) {
      for (var e : vm.image.env) {
        merged.put(e.key, e.val == null ? "" : e.val);
      }
    }
    if (vm.env != null) {
      for (var e : vm.env) {
        if (e.val == null) {
          log.warn("environment variable [{}] has no value; passing an empty value", e.key);
        }
        merged.put(e.key, e.val == null ? "" : e.val);
      }
    }
    var out = new ArrayList<String>(merged.size());
    for (var entry : merged.entrySet()) {
      out.add(entry.getKey() + "=" + entry.getValue());
    }
    return out;
  }

  /**
   * Resolves the guest argv following Docker/compose semantics: a service
   * {@code entrypoint} replaces the image one; a service {@code command}
   * replaces the image {@code Cmd} while keeping the image {@code Entrypoint}.
   * This is the single place the full command array is resolved.
   */
  public static List<String> command(FgVm vm) {
    var out = new ArrayList<String>();
    List<String> entrypoint = vm.entrypoint != null
      ? vm.entrypoint
      : (vm.image != null && vm.image.entryPoint != null ? List.of(vm.image.entryPoint) : null);
    List<String> cmd = vm.command != null
      ? vm.command
      : (vm.image != null && vm.image.cmd != null ? List.of(vm.image.cmd) : null);
    if (entrypoint != null) {
      out.addAll(entrypoint);
    }
    if (cmd != null) {
      out.addAll(cmd);
    }
    if (out.isEmpty()) {
      out.add("/bin/sh");
    }
    return out;
  }
}
