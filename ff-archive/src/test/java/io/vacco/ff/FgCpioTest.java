package io.vacco.ff;

import io.vacco.ff.archive.FgCpio;
import io.vacco.ff.archive.FgTarIo;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.util.TreeSet;

import static j8spec.J8Spec.*;
import static org.junit.Assert.*;
import static io.vacco.ff.util.FgIo.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgCpioTest {
  static {
    it("creates a cpio archive from staged tar entries", () -> {
      var tarFile = new File("./src/test/resources/pax-sample.tar");
      var workDir = new File("./build/cpio");
      deleteRecursively(workDir, Exception::printStackTrace);
      mkDirs(workDir);

      var entries = FgTarIo.extract(tarFile, workDir, (entry, err) -> {
        System.out.printf("Unable to extract entry %s - %s%n", entry, err.getMessage());
      });

      var cpio = new File(workDir, "sample.cpio");
      FgCpio.archive(
        new TreeSet<>(entries), cpio,
        (path, err) -> System.out.printf("Unable to archive path %s - %s%n", path, err.getMessage())
      );

      assertTrue("cpio archive should exist", cpio.exists());
      assertTrue("cpio archive should not be empty", cpio.length() > 0);

      deleteRecursively(workDir, Exception::printStackTrace);
      Files.deleteIfExists(cpio.toPath());
    });
  }
}
