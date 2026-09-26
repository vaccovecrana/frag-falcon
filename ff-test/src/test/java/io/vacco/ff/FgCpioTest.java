package io.vacco.ff;

import io.vacco.ff.initramfs.FgCpio;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;
import java.io.File;

import static j8spec.J8Spec.*;
import static io.vacco.ff.util.FgIo.mkDirs;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgCpioTest {
  static {
    it("creates a cpio archive from a file tree", () -> {
      var root = new File("./etc/cpio/tree");
      var cpioDir = new File("./build/cpio");
      var cpio = new File(cpioDir, "tree.cpio");
      mkDirs(cpioDir);
      FgCpio.archive(
        root, cpio,
        (path, err) -> System.out.printf("Unable to archive path %s - %s%n", path, err.getMessage())
      );
    });
  }
}
