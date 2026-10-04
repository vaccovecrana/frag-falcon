package io.vacco.ff;

import io.vacco.ff.oci.FgDockerIo;
import io.vacco.ff.oci.FgOciStore;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

import static io.vacco.ff.FgTest.log;
import static j8spec.J8Spec.it;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Raw OCI image expansion tests.
 *
 * <p>{@link #PROBLEMATIC_IMAGES} is a manually-maintained catalog of image
 * references that are known to be awkward to pull/expand; add to it as new
 * problem images are found. Each entry is pulled, expanded into a rootfs and
 * checked for basic content. This exercises the registry client and the
 * kernel-confined tar extractor without booting a VM (no KVM / no cap needed).
 */
@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgImageExpandTest {

  private static final List<String> PROBLEMATIC_IMAGES = List.of(
    "ghcr.io/siderolabs/installer:v1.4.0",
    "docker.io/hashicorp/http-echo:latest",
    "docker.io/louislam/uptime-kuma:latest",
    "quay.io/argoproj/argocd:latest",
    "docker.io/nats:latest",
    "docker.io/postgres:latest",
    "docker.io/cockroachdb/cockroach",
    "docker.io/busybox:latest",
    "docker.io/drone/drone-runner-docker:linux-amd64",
    "docker.io/grycap/cowsay:latest"
    // add more known-problematic image refs here
  );

  private static String safeName(String ref) {
    return ref.replaceAll("[^A-Za-z0-9]+", "-");
  }

  static {
    it("expands every image in the problematic catalog", () -> {
      var store = new FgOciStore(new File(FgTest.WORK, "oci"));
      for (var ref : PROBLEMATIC_IMAGES) {
        var rootfs = FgTest.freshDir("expand-" + safeName(ref));
        try {
          var image = FgDockerIo.extract(ref, rootfs, store);
          var entries = rootfs.list();
          assertTrue(
            "[" + ref + "] expansion produced no rootfs content",
            entries != null && entries.length > 0
          );
          log.info("libkrun: expanded {} -> {}", ref, image);
        } catch (Throwable t) {
          fail("[" + ref + "] expansion failed: " + t);
        }
      }
    });
  }
}
