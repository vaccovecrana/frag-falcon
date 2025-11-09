package io.vacco.ff;

import io.vacco.ff.docker.FgDockerImageExtractor;
import io.vacco.ff.docker.FgDockerImageFactory;
import io.vacco.ff.util.FgLog;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;
import java.io.File;

import static j8spec.J8Spec.*;
import static io.vacco.ff.util.FgIo.mkDirs;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgDockerIoTest {

  public static File buildDir = new File("./build");

  private static final String[] images = new String[] {
    "ghcr.io/siderolabs/installer:v1.4.0",
    "docker.io/hashicorp/http-echo:latest",
    "docker.io/louislam/uptime-kuma:latest",
    "quay.io/argoproj/argocd:latest",
    "quay.io/jetstack/cert-manager-controller:v1.19.1",
    "docker.io/nats:latest",
    "docker.io/postgres:latest",
    "docker.io/cockroachdb/cockroach",
    "docker.io/busybox:latest",
    "docker.io/drone/drone-runner-docker:linux-amd64"
  };

  static {
    FgLog.devMode();
    it("Extracts remote Docker images to CPIO", () -> {
      for (var image : images) {
        var imgId = Integer.toHexString(image.hashCode());
        var imgDir = new File(buildDir, imgId);
        var imgCpioDir = new File(imgDir, "cpio");
        var imgCpio = new File(imgCpioDir, String.format("%s.cpio", imgId));
        mkDirs(imgCpioDir);
        
        // Get image metadata (downloads blobs to cache)
        var blobCacheDir = new File(imgDir, "blobs");
        mkDirs(blobCacheDir);
        var metadata = FgDockerImageFactory.getImageMetadata(image, "amd64", "linux", blobCacheDir);

        System.out.println("lel?");
      }
      System.out.println("done");
    });
  }
}
