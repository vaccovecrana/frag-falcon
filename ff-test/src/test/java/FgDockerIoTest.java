import io.vacco.ff.FgCpio;
import io.vacco.ff.FgDockerIo;
import io.vacco.shax.logging.ShOption;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.FileAlreadyExistsException;

import static j8spec.J8Spec.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgDockerIoTest {
  static {
    ShOption.setSysProp(ShOption.IO_VACCO_SHAX_DEVMODE, "true");
  }

  private static final Logger log = LoggerFactory.getLogger(FgDockerIoTest.class);

  public static File buildDir = new File("./build");
  public static File extractDir = new File(buildDir, "extract");
  public static File cpioArchive = new File(buildDir, "out.cpio");

  static {
    it("Extracts a remote Docker image", () -> {
      // var image = "quay.io/argoproj/argocd:latest";
      // var image = "docker.io/nats:latest";
      var image = "docker.io/traefik:latest";
      FgDockerIo.extract(image, buildDir, (tarEntry, ex) -> {
        if (ex instanceof FileAlreadyExistsException) {
          log.info("File already exists: {}", tarEntry.name);
        } else {
          log.error("Unable to extract entry {}", tarEntry, ex);
        }
      });
      FgCpio.archive(extractDir, cpioArchive, (file, e) -> {
        log.info("Unable to archive path: {} - {}", file, e.getMessage());
      });
      System.out.println("done");
    });
  }
}
