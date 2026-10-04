package io.vacco.ff;

import io.vacco.ff.oci.FgEnvVar;
import io.vacco.ff.oci.FgImage;
import io.vacco.ff.schema.FgVolume;
import io.vacco.ff.service.FgValid;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

import static j8spec.J8Spec.it;
import static org.junit.Assert.assertTrue;

/**
 * Validation of extracted image metadata ({@code image.json}) and parsed
 * volumes. Runs at the build boundary (hard fail) and the load boundary
 * (invalid -> re-provision).
 */
@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgImageValidTest {

  private static FgImage valid(File rootDir) {
    var img = FgImage.of(rootDir.getAbsolutePath(), new String[]{"/opt1x"}, null,
      List.of(FgEnvVar.of("PATH", "/bin")), null);
    img.source = "docker.io/library/alpine:latest";
    img.exposedPorts = List.of("7070/tcp");
    return img;
  }

  private static List<String> keys(FgImage img) {
    return FgValid.validateImage(img).stream().map(v -> v.key).toList();
  }

  static {
    it("accepts a well-formed image", () -> {
      var root = FgTest.freshDir("img-ok");
      assertTrue(keys(valid(root)).toString(), FgValid.validateImage(valid(root)).isEmpty());
    });

    it("rejects a missing source and rootDir (the corrupted image.json case)", () -> {
      var k = keys(new FgImage());
      assertTrue(k.contains("ff.image.source.missing"));
      assertTrue(k.contains("ff.image.rootDir.missing"));
    });

    it("rejects a syntactically invalid source", () -> {
      var root = FgTest.freshDir("img-bad-src");
      var img = valid(root);
      img.source = "Not An Image";
      assertTrue(keys(img).contains("ff.image.source.invalid"));
    });

    it("rejects a rootDir that does not exist", () -> {
      var img = valid(new File("/tmp/does-not-exist-img"));
      assertTrue(keys(img).contains("ff.image.rootDir.missing"));
    });

    it("rejects a relative workingDir", () -> {
      var root = FgTest.freshDir("img-rel-wd");
      var img = valid(root);
      img.workingDir = "srv/app";
      assertTrue(keys(img).contains("ff.image.workingDir.relative"));
    });

    it("rejects a blank command entry", () -> {
      var root = FgTest.freshDir("img-blank-cmd");
      var img = valid(root);
      img.cmd = new String[]{"-c", " "};
      assertTrue(keys(img).contains("ff.image.command.blank"));
    });

    it("rejects an invalid env key and duplicates", () -> {
      var root = FgTest.freshDir("img-env");
      var img = valid(root);
      img.env = List.of(FgEnvVar.of("1BAD", "x"), FgEnvVar.of("FOO", "a"), FgEnvVar.of("FOO", "b"));
      var k = keys(img);
      assertTrue(k.contains("ff.image.env.invalid"));
      assertTrue(k.contains("ff.image.env.duplicate"));
    });

    it("rejects a malformed exposed port", () -> {
      var root = FgTest.freshDir("img-ports");
      var img = valid(root);
      img.exposedPorts = List.of("7070", "abc/udp");
      assertTrue(keys(img).contains("ff.image.exposedPorts.invalid"));
    });

    it("validates a parsed volume", () -> {
      var out = new java.util.ArrayList<io.vacco.ronove.util.RvValidation>();
      FgValid.validateVolume(new FgVolume(), "vol", out);
      var k = out.stream().map(v -> v.key).toList();
      assertTrue(k.contains("ff.volume.host.missing"));
      assertTrue(k.contains("ff.volume.guest.absolute"));
    });
  }
}
