package io.vacco.ff;

import io.vacco.ff.archive.FgCpio;
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
    it("Extracts remote Docker images to CPIO archives", () -> {
      for (var image : images) {
        var imgId = Integer.toHexString(image.hashCode());
        var imgDir = new File(buildDir, imgId);
        var imgConfig = FgDockerIo.pull(image, imgDir, "amd64", "linux", (ent, e) -> {
          System.out.printf("Docker tar file entry error: %s %s", ent, e.getMessage());
        });

        var imgCpioDir = new File(imgDir, "cpio");
        var imgCpio = new File(imgCpioDir, String.format("%s.cpio", imgId));
        mkDirs(imgCpioDir);
        FgCpio.archive(imgConfig.files, imgCpio, (path, e) -> {
          System.out.printf("Docker cpio file entry error: %s %s", path, e.getMessage());
        });
      }
    });
  }
}
