package io.vacco.ff.service;

import io.vacco.ff.oci.FgEnvVar;
import io.vacco.ff.oci.FgImage;
import io.vacco.ff.schema.FgResources;
import io.vacco.ff.schema.FgService;
import io.vacco.ff.schema.FgStack;
import io.vacco.ff.schema.FgVolume;
import io.vacco.ronove.util.RvValidation;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Compose-subset validation for stack/service definitions and extracted image
 * metadata. Rules produce locale-agnostic {@link RvValidation}s (our
 * {@code ff.stack.*} / {@code ff.image.*} / {@code ff.volume.*} keys plus
 * positional params), which the API returns in a {@code RvResult} envelope and
 * the UI renders through its i18n templates.
 */
public class FgValid {

  public static final Pattern STACK_ID = Pattern.compile("[A-Za-z0-9-]+");
  private static final Pattern IMAGE_REF = Pattern.compile(
    "^(?:[a-zA-Z0-9.-]+(?::[0-9]+)?/)?[a-z0-9]+(?:[._-][a-z0-9]+)*"
      + "(?:/[a-z0-9]+(?:[._-][a-z0-9]+)*)*(?::[\\w][\\w.-]{0,127})?(?:@sha256:[a-f0-9]{64})?$"
  );
  private static final Pattern ENV_KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final Pattern EXPOSED_PORT = Pattern.compile("\\d+/(tcp|udp)");
  private static final List<String> RESTART = List.of("always", "unless-stopped", "on-failure", "no");

  private static RvValidation rv(String key, String name, Object... args) {
    var v = RvValidation.of(key).withName(name);
    for (int i = 0; i < args.length; i++) {
      v.withParam(String.valueOf(i), args[i] == null ? "" : String.valueOf(args[i]));
    }
    return v;
  }

  /* ----- image metadata ----------------------------------------------- */

  /** Validates an extracted/persisted {@link FgImage}. */
  public static List<RvValidation> validateImage(FgImage img) {
    var out = new ArrayList<RvValidation>();
    if (img == null) {
      out.add(rv("ff.image.missing", "image"));
      return out;
    }
    if (img.source == null || img.source.isBlank()) {
      out.add(rv("ff.image.source.missing", "image.source"));
    } else if (!IMAGE_REF.matcher(img.source).matches()) {
      out.add(rv("ff.image.source.invalid", "image.source", img.source));
    }
    if (img.rootDir == null || img.rootDir.isBlank()) {
      out.add(rv("ff.image.rootDir.missing", "image.rootDir"));
    } else if (!new File(img.rootDir).isDirectory()) {
      out.add(rv("ff.image.rootDir.missing", "image.rootDir", img.rootDir));
    }
    if (img.workingDir != null && !img.workingDir.isBlank() && !img.workingDir.startsWith("/")) {
      out.add(rv("ff.image.workingDir.relative", "image.workingDir", img.workingDir));
    }
    validateCommand("image.entryPoint", img.entryPoint, out);
    validateCommand("image.cmd", img.cmd, out);
    if (img.env != null) {
      var keys = new HashSet<String>();
      for (int i = 0; i < img.env.size(); i++) {
        var e = img.env.get(i);
        var path = "image.env[" + i + "]";
        if (e == null || e.key == null || e.key.isBlank() || !ENV_KEY.matcher(e.key).matches()) {
          out.add(rv("ff.image.env.invalid", path, e == null ? null : e.key));
        } else if (!keys.add(e.key)) {
          out.add(rv("ff.image.env.duplicate", path, e.key));
        }
      }
    }
    if (img.exposedPorts != null) {
      for (int i = 0; i < img.exposedPorts.size(); i++) {
        var p = img.exposedPorts.get(i);
        if (p == null || !EXPOSED_PORT.matcher(p).matches()) {
          out.add(rv("ff.image.exposedPorts.invalid", "image.exposedPorts[" + i + "]", p));
        }
      }
    }
    return out;
  }

  private static void validateCommand(String path, String[] cmd, List<RvValidation> out) {
    if (cmd == null) {
      return;
    }
    for (int i = 0; i < cmd.length; i++) {
      if (cmd[i] == null || cmd[i].isBlank()) {
        out.add(rv("ff.image.command.blank", path + "[" + i + "]", path));
      }
    }
  }

  /* ----- volumes ------------------------------------------------------ */

  /** Validates a parsed {@link FgVolume}. */
  public static void validateVolume(FgVolume v, String path, List<RvValidation> out) {
    if (v == null) {
      out.add(rv("ff.volume.missing", path));
      return;
    }
    if (v.hostPath == null || v.hostPath.isBlank()) {
      out.add(rv("ff.volume.host.missing", path + ".hostPath"));
    } else if (!new File(v.hostPath).exists()) {
      out.add(rv("ff.volume.host.missing", path + ".hostPath", v.hostPath));
    }
    if (v.guestPath == null || v.guestPath.isBlank() || !v.guestPath.startsWith("/")) {
      out.add(rv("ff.volume.guest.absolute", path + ".guestPath", v.guestPath));
    }
  }

  /* ----- services ----------------------------------------------------- */

  public static void validateService(String svcName, FgService svc, List<RvValidation> out) {
    var path = "services." + svcName;
    if (svc == null) {
      out.add(rv("ff.stack.service.missing", path, svcName));
      return;
    }
    if (svc.image == null || svc.image.isBlank()) {
      out.add(rv("ff.stack.service.image.required", path + ".image", svcName));
    } else if (!IMAGE_REF.matcher(svc.image).matches()) {
      out.add(rv("ff.stack.service.image.invalid", path + ".image", svc.image));
    }
    if (svc.restart != null && !svc.restart.isBlank() && !RESTART.contains(svc.restart)) {
      out.add(rv("ff.stack.service.restart.invalid", path + ".restart", svc.restart));
    }
    if (svc.volumes != null) {
      var guestPaths = new HashSet<String>();
      for (int i = 0; i < svc.volumes.size(); i++) {
        var spec = svc.volumes.get(i);
        var f = path + ".volumes[" + i + "]";
        var parts = spec == null ? new String[0] : spec.split(":");
        if (parts.length < 2 || parts.length > 3) {
          out.add(rv("ff.stack.service.volumes.invalid", f, String.valueOf(spec)));
          continue;
        }
        if (!parts[1].startsWith("/")) {
          out.add(rv("ff.stack.service.volumes.guestAbsolute", f, parts[1]));
        }
        if (parts.length == 3 && !parts[2].equals("ro")) {
          out.add(rv("ff.stack.service.volumes.mode", f, parts[2]));
        }
        if (!guestPaths.add(parts[1])) {
          out.add(rv("ff.stack.service.volumes.duplicateGuest", f, parts[1]));
        }
        if (!new File(parts[0]).exists()) {
          out.add(rv("ff.stack.service.volumes.hostMissing", f, parts[0]));
        }
      }
    }
    if (svc.environment != null) {
      var keys = new HashSet<String>();
      for (int i = 0; i < svc.environment.size(); i++) {
        var entry = svc.environment.get(i);
        var f = path + ".environment[" + i + "]";
        var k = entry == null ? "" : (entry.contains("=") ? entry.substring(0, entry.indexOf('=')) : entry);
        if (k.isEmpty() || !ENV_KEY.matcher(k).matches()) {
          out.add(rv("ff.stack.service.environment.invalid", f, String.valueOf(entry)));
        } else if (!keys.add(k)) {
          out.add(rv("ff.stack.service.environment.duplicate", f, k));
        }
      }
    }
    var lists = new ArrayList<String[]>();
    addList(lists, "entrypoint", svc.entrypoint);
    addList(lists, "command", svc.command);
    addList(lists, "depends_on", svc.depends_on);
    for (var e : lists) {
      for (int i = 0; i < e.length - 1; i++) {
        var val = e[i + 1];
        if (val == null || val.isBlank()) {
          out.add(rv("ff.stack.service.list.blank", path + "." + e[0] + "[" + i + "]", e[0]));
        }
      }
    }
    if (svc.resources != null) {
      validateResources(path + ".resources", svc.resources, out);
    }
  }

  private static void validateResources(String path, FgResources r, List<RvValidation> out) {
    if (r.vcpus < 1) {
      out.add(rv("ff.stack.resources.vcpus", path + ".vcpus", r.vcpus));
    }
    if (r.ramMib < 128) {
      out.add(rv("ff.stack.resources.ramMib", path + ".ramMib", r.ramMib));
    }
  }

  /* ----- stacks ------------------------------------------------------- */

  /** Validates a full stack definition. */
  public static List<RvValidation> validate(FgStack stack) {
    var out = new ArrayList<RvValidation>();
    if (stack == null) {
      out.add(rv("ff.stack.missing", "stack"));
      return out;
    }
    if (stack.id == null || stack.id.isBlank()) {
      out.add(rv("ff.stack.invalidId", "id", String.valueOf(stack.id)));
    } else if (!STACK_ID.matcher(stack.id).matches()) {
      out.add(rv("ff.stack.invalidId", "id", stack.id));
    }
    if (stack.services == null || stack.services.isEmpty()) {
      out.add(rv("ff.stack.services.empty", "services"));
      return out;
    }
    for (var e : stack.services.entrySet()) {
      validateService(e.getKey(), e.getValue(), out);
    }
    validateDependsOn(stack, out);
    validateNoCycles(stack, out);
    return out;
  }

  private static void validateDependsOn(FgStack stack, List<RvValidation> out) {
    for (var e : stack.services.entrySet()) {
      var deps = e.getValue() == null ? null : e.getValue().depends_on;
      if (deps == null) {
        continue;
      }
      for (var d : deps) {
        if (d != null && !stack.services.containsKey(d)) {
          out.add(rv("ff.stack.service.dependsOn.unknown", "services." + e.getKey() + ".depends_on", d));
        }
      }
    }
  }

  private static void validateNoCycles(FgStack stack, List<RvValidation> out) {
    try {
      FgStackPlan.startOrder(stack);
    } catch (Exception e) {
      out.add(rv("ff.stack.services.cyclic", "services", e.getMessage()));
    }
  }

  private static void addList(List<String[]> target, String name, List<String> values) {
    if (values == null) {
      return;
    }
    var row = new String[values.size() + 1];
    row[0] = name;
    for (int i = 0; i < values.size(); i++) {
      row[i + 1] = values.get(i);
    }
    target.add(row);
  }

  private FgValid() {
  }
}
