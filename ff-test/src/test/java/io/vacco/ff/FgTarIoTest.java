package io.vacco.ff;

import io.vacco.ff.initramfs.FgTarIo;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.TreeSet;

import static j8spec.J8Spec.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgTarIoTest {
  static {
    it("Extracts tar data", () -> {
      var tarFile = new File("./src/test/resources/blob.tar");
      var outDir = new File("./build/untar");
      var entries = FgTarIo.extract(tarFile, outDir, (entry, err) -> {
        System.out.printf("Unable to extract entry %s - %s%n", entry, err.getMessage());
      });
      var entrySet = new TreeSet<>(entries);
      for (var e : entrySet) {
        System.out.println(e.toString());
      }
    });
  }
}
