package io.vacco.ff.service;

import am.ik.yavi.core.ConstraintViolation;
import am.ik.yavi.core.ConstraintViolations;
import io.vacco.ff.schema.FgResources;
import io.vacco.ff.schema.FgService;
import io.vacco.ff.schema.FgStack;
import io.vacco.ronove.util.RvValidation;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Compose-subset validation for stack/service definitions, run before a stack is
 * persisted (and again before it starts). Simple scalar/nested rules use yavi;
 * collection and cross-service rules use explicit checks. Failures are bridged
 * to {@link RvValidation} (our {@code ff.stack.*} keys + params) so the UI can
 * render them.
 */
public class FgValid {

  public static final Pattern STACK_ID = Pattern.compile("[A-Za-z0-9-]+");
  private static final Pattern IMAGE_REF = Pattern.compile(
    "^(?:[a-zA-Z0-9.-]+(?::[0-9]+)?/)?[a-z0-9]+(?:[._-][a-z0-9]+)*"
      + "(?:/[a-z0-9]+(?:[._-][a-z0-9]+)*)*(?::[\\w][\\w.-]{0,127})?(?:@sha256:[a-f0-9]{64})?$"
  );
  private static final Pattern ENV_KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final List<String> RESTART = List.of("always", "unless-stopped", "on-failure", "no");

  private static void validateResources(String path, FgResources r, ConstraintViolations out) {
    if (r.vcpus < 1) {
      out.add(v("ff.stack.resources.vcpus", path + ".vcpus", r.vcpus));
    }
    if (r.ramMib < 128) {
      out.add(v("ff.stack.resources.ramMib", path + ".ramMib", r.ramMib));
    }
  }
  private static ConstraintViolation v(String key, String name, Object... args) {
    return new ConstraintViolation(name, key, String.valueOf(args.length > 0 ? args[0] : ""),
      args, (mk, fmt, a, l) -> mk, Locale.ROOT);
  }

  /** Validates a single service; appends to {@code out}. */
  public static void validateService(String svcName, FgService svc, ConstraintViolations out) {
    var path = "services." + svcName;
    if (svc == null) {
      out.add(v("ff.stack.service.missing", path, svcName));
      return;
    }
    if (svc.image == null || svc.image.isBlank()) {
      out.add(v("ff.stack.service.image.required", path + ".image", svcName));
    } else if (!IMAGE_REF.matcher(svc.image).matches()) {
      out.add(v("ff.stack.service.image.invalid", path + ".image", svc.image));
    }
    if (svc.restart != null && !svc.restart.isBlank() && !RESTART.contains(svc.restart)) {
      out.add(v("ff.stack.service.restart.invalid", path + ".restart", svc.restart));
    }
    if (svc.volumes != null) {
      var guestPaths = new HashSet<String>();
      for (int i = 0; i < svc.volumes.size(); i++) {
        var spec = svc.volumes.get(i);
        var f = path + ".volumes[" + i + "]";
        var parts = spec == null ? new String[0] : spec.split(":");
        if (parts.length < 2 || parts.length > 3) {
          out.add(v("ff.stack.service.volumes.invalid", f, String.valueOf(spec)));
          continue;
        }
        if (!parts[1].startsWith("/")) {
          out.add(v("ff.stack.service.volumes.guestAbsolute", f, parts[1]));
        }
        if (parts.length == 3 && !parts[2].equals("ro")) {
          out.add(v("ff.stack.service.volumes.mode", f, parts[2]));
        }
        if (!guestPaths.add(parts[1])) {
          out.add(v("ff.stack.service.volumes.duplicateGuest", f, parts[1]));
        }
        if (!new File(parts[0]).exists()) {
          out.add(v("ff.stack.service.volumes.hostMissing", f, parts[0]));
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
          out.add(v("ff.stack.service.environment.invalid", f, String.valueOf(entry)));
        } else if (!keys.add(k)) {
          out.add(v("ff.stack.service.environment.duplicate", f, k));
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
          out.add(v("ff.stack.service.list.blank", path + "." + e[0] + "[" + i + "]", e[0]));
        }
      }
    }
    if (svc.resources != null) {
      validateResources(path + ".resources", svc.resources, out);
    }
  }

  /** Validates a full stack definition. */
  public static ConstraintViolations validate(FgStack stack) {
    var out = new ConstraintViolations();
    if (stack == null) {
      out.add(v("ff.stack.missing", "stack"));
      return out;
    }
    if (stack.id == null || stack.id.isBlank()) {
      out.add(v("ff.stack.invalidId", "id", String.valueOf(stack.id)));
    } else if (!STACK_ID.matcher(stack.id).matches()) {
      out.add(v("ff.stack.invalidId", "id", stack.id));
    }
    if (stack.services == null || stack.services.isEmpty()) {
      out.add(v("ff.stack.services.empty", "services"));
      return out;
    }
    for (var e : stack.services.entrySet()) {
      validateService(e.getKey(), e.getValue(), out);
    }
    validateDependsOn(stack, out);
    validateNoCycles(stack, out);
    return out;
  }

  private static void validateDependsOn(FgStack stack, ConstraintViolations out) {
    for (var e : stack.services.entrySet()) {
      var deps = e.getValue() == null ? null : e.getValue().depends_on;
      if (deps == null) {
        continue;
      }
      for (var d : deps) {
        if (d != null && !stack.services.containsKey(d)) {
          out.add(v("ff.stack.service.dependsOn.unknown",
            "services." + e.getKey() + ".depends_on", d));
        }
      }
    }
  }

  private static void validateNoCycles(FgStack stack, ConstraintViolations out) {
    try {
      FgStackPlan.startOrder(stack);
    } catch (Exception e) {
      out.add(v("ff.stack.services.cyclic", "services", e.getMessage()));
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

  /** Bridges yavi violations to locale-agnostic {@link RvValidation}s. */
  public static List<RvValidation> validationsOf(ConstraintViolations cv) {
    var out = new ArrayList<RvValidation>();
    for (var c : cv) {
      var params = new LinkedHashMap<String, String>();
      var args = c.args();
      for (int i = 0; i < (args == null ? 0 : args.length); i++) {
        params.put(String.valueOf(i), args[i] == null ? "" : String.valueOf(args[i]));
      }
      out.add(RvValidation.of(c.messageKey()).withName(c.name()).withParams(params));
    }
    return out;
  }

  private FgValid() {
  }
}
