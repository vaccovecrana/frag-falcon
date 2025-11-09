package io.vacco.ff;

import io.vacco.ff.archive.FgTarEntry;
import io.vacco.ff.archive.FgTarIo;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import static j8spec.J8Spec.*;
import static org.junit.Assert.*;
import static io.vacco.ff.util.FgIo.deleteRecursively;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgTarIoTest {

  private static void logExtractionError(FgTarEntry entry, Exception err) {
    System.out.printf("Unable to extract entry %s - %s%n", entry, err.getMessage());
  }

  static {
    it("extracts classic tar data", () -> {
      var tarFile = new File("./src/test/resources/blob.tar");
      var outDir = new File("./build/untar");
      deleteRecursively(outDir, Exception::printStackTrace);
      var entries = FgTarIo.extract(tarFile, outDir, FgTarIoTest::logExtractionError);
      assertFalse("expected entries", entries.isEmpty());
    });

    it("honors pax headers for names, links and timestamps", () -> {
      var tarFile = new File("./src/test/resources/pax-sample.tar");
      var outDir = new File("./build/untar-pax");
      deleteRecursively(outDir, Exception::printStackTrace);

      var entries = FgTarIo.extract(tarFile, outDir, FgTarIoTest::logExtractionError);
      assertFalse("expected pax entries", entries.isEmpty());

      var byName = entries.stream()
        .collect(Collectors.toMap(e -> e.name, e -> e, (a, b) -> b));

      var dirEntry = byName.get("dir");
      assertNotNull("missing pax directory entry", dirEntry);
      assertTrue(dirEntry.isDirectory());
      assertFalse(dirEntry.isFile());

      var fileEntry = byName.get("dir/subdir/file.txt");
      assertNotNull("missing pax file entry", fileEntry);
      assertTrue(fileEntry.isRegularFile());
      assertEquals(14, fileEntry.size());
      assertEquals(Instant.parse("2024-01-02T03:04:05Z"), fileEntry.lastModifiedTime().toInstant());

      var linkEntry = byName.get("dir/link.txt");
      assertNotNull("missing pax symlink entry", linkEntry);
      assertTrue(linkEntry.isSymbolicLink());
      assertEquals("file.txt", linkEntry.linkName);

      assertTrue("file should exist on disk", Files.exists(new File(outDir, "dir/subdir/file.txt").toPath()));
      assertEquals("hello from pax", Files.readString(new File(outDir, "dir/subdir/file.txt").toPath()).trim());
    });
  }

}
