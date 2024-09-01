package io.vacco.ff;

import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.file.FileAlreadyExistsException;

import static j8spec.J8Spec.*;
import static io.vacco.ff.FgTest.*;
import static io.vacco.ff.util.FgIo.mkDirs;
import static io.vacco.ff.initramfs.FgDockerIo.extract;
import static io.vacco.ff.initramfs.FgCpio.archive;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgDockerIoTest {

  public static File buildDir = new File("./build");

  private static final String[] images = new String[] {
    "ghcr.io/siderolabs/installer:v1.4.0",
    "docker.io/hashicorp/http-echo:latest",
    "docker.io/louislam/uptime-kuma:latest",
    "quay.io/argoproj/argocd:latest",
    "docker.io/nats:latest",
    "docker.io/postgres:latest",
    "docker.io/cockroachdb/cockroach",
    "docker.io/busybox:latest",
    "docker.io/drone/drone-runner-docker:linux-amd64"
  };

  static {
    initLog();
    it("Extracts remote Docker images", () -> {
      for (var image : images) {
        var imgId = Integer.toHexString(image.hashCode());
        var imgDir = new File(buildDir, imgId);
        var imgExtractDir = new File(imgDir, "extract");
        var imgCpioDir = new File(imgDir, "cpio");
        var imgCpio = new File(imgCpioDir, String.format("%s.cpio", imgId));
        mkDirs(imgCpioDir);
        extract(image, imgDir, "amd64", "linux", (tarEntry, ex) -> {
          if (ex instanceof FileAlreadyExistsException) {
            System.out.printf("File already exists: %s%n", tarEntry.name);
          } else {
            System.out.printf("Unable to extract entry %s - %s%n", tarEntry, ex.getMessage());
          }
        });
        archive(imgExtractDir, imgCpio, (file, e) -> System.out.printf("Unable to archive path: %s, %s%n", file, e.getMessage()));
      }
      System.out.println("done");
    });
  }
}
