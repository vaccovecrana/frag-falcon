package io.vacco.ff;

import io.vacco.ff.schema.FgResources;
import io.vacco.ff.schema.FgService;
import io.vacco.ff.schema.FgStack;
import io.vacco.ff.service.FgValid;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.util.List;

import static j8spec.J8Spec.it;
import static org.junit.Assert.*;

/**
 * yavi validation of compose-subset stack/service definitions, run before a
 * stack is saved or started. Keys are exposed to the UI via RvValidation.
 */
@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgValidTest {

  private static FgService svc(String image) {
    var s = new FgService();
    s.image = image;
    return s;
  }

  private static FgStack stack(String id, FgService... services) {
    var s = new FgStack().id(id);
    for (int i = 0; i < services.length; i++) {
      s.services.put("svc" + i, services[i]);
    }
    return s;
  }

  private static List<String> keys(FgStack s) {
    return FgValid.validate(s).stream().map(v -> v.key).toList();
  }

  static {
    it("accepts a well-formed stack", () -> {
      var host = FgTest.freshDir("valid-vol");
      var s = stack("good-stack", svc("docker.io/library/alpine:latest"));
      s.services.get("svc0").volumes = List.of(host.getAbsolutePath() + ":/data:ro");
      s.services.get("svc0").environment = List.of("FOO=bar", "BAZ");
      s.services.get("svc0").restart = "unless-stopped";
      s.services.get("svc0").resources = new FgResources();
      s.services.get("svc0").resources.vcpus = 2;
      s.services.get("svc0").resources.ramMib = 256;
      assertTrue(keys(s).toString(), FgValid.validate(s).isEmpty());
    });

    it("rejects an invalid stack id", () -> {
      assertTrue(keys(stack("bad id!", svc("alpine"))).contains("ff.stack.invalidId"));
    });

    it("rejects a missing image", () -> {
      assertTrue(keys(stack("s", svc(null))).contains("ff.stack.service.image.required"));
    });

    it("rejects a malformed image reference", () -> {
      assertTrue(keys(stack("s", svc("Not An Image"))).contains("ff.stack.service.image.invalid"));
    });

    it("rejects an empty service list", () -> {
      assertTrue(keys(new FgStack().id("s")).contains("ff.stack.services.empty"));
    });

    it("rejects a bad volume spec and a missing host path", () -> {
      var s = stack("s", svc("alpine:latest"));
      s.services.get("svc0").volumes = List.of("no-colon", "/does/not/exist:/data");
      var k = keys(s);
      assertTrue(k.contains("ff.stack.service.volumes.invalid"));
      assertTrue(k.contains("ff.stack.service.volumes.hostMissing"));
    });

    it("rejects a non-absolute guest path", () -> {
      var host = FgTest.freshDir("valid-vol2");
      var s = stack("s", svc("alpine:latest"));
      s.services.get("svc0").volumes = List.of(host.getAbsolutePath() + ":data");
      assertTrue(keys(s).contains("ff.stack.service.volumes.guestAbsolute"));
    });

    it("rejects an invalid environment entry and duplicates", () -> {
      var s = stack("s", svc("alpine:latest"));
      s.services.get("svc0").environment = List.of("1BAD=x", "FOO=a", "FOO=b");
      var k = keys(s);
      assertTrue(k.contains("ff.stack.service.environment.invalid"));
      assertTrue(k.contains("ff.stack.service.environment.duplicate"));
    });

    it("rejects an invalid restart policy", () -> {
      var s = stack("s", svc("alpine:latest"));
      s.services.get("svc0").restart = "sometimes";
      assertTrue(keys(s).contains("ff.stack.service.restart.invalid"));
    });

    it("rejects bad resources", () -> {
      var s = stack("s", svc("alpine:latest"));
      s.services.get("svc0").resources = new FgResources();
      s.services.get("svc0").resources.vcpus = 0;
      s.services.get("svc0").resources.ramMib = 64;
      var k = keys(s);
      assertTrue(k.contains("ff.stack.resources.vcpus"));
      assertTrue(k.contains("ff.stack.resources.ramMib"));
    });

    it("rejects an unknown depends_on target", () -> {
      var s = stack("s", svc("alpine:latest"));
      s.services.get("svc0").depends_on = List.of("nope");
      assertTrue(keys(s).contains("ff.stack.service.dependsOn.unknown"));
    });

    it("rejects a dependency cycle", () -> {
      var a = svc("alpine:latest");
      var b = svc("alpine:latest");
      a.depends_on = List.of("svc1");
      b.depends_on = List.of("svc0");
      var s = stack("s", a, b);
      assertTrue(keys(s).contains("ff.stack.services.cyclic"));
    });

    it("carries a name and positional params for the UI", () -> {
      var vs = FgValid.validate(stack("bad id!", svc("alpine")));
      var v = vs.stream().filter(x -> x.key.equals("ff.stack.invalidId")).findFirst().orElseThrow();
      assertEquals("id", v.name);
      assertNotNull(v.params);
    });
  }
}
