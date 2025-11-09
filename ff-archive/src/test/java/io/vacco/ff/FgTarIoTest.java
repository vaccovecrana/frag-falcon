package io.vacco.ff;

import io.vacco.ff.archive.*;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.stream.Collectors;
import java.security.MessageDigest;

import static j8spec.J8Spec.*;
import static org.junit.Assert.*;
import static io.vacco.ff.util.FgIo.deleteRecursively;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgTarIoTest {

  private static void logExtractionError(FgTarEntry entry, Exception err) {
    System.out.printf("Unable to extract entry %s - %s%n", entry, err.getMessage());
  }

  private static String md5Hex(byte[] content) {
    try {
      var md = MessageDigest.getInstance("MD5");
      md.update(content);
      var digest = md.digest();
      var sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(Character.forDigit((b >>> 4) & 0x0F, 16));
        sb.append(Character.forDigit(b & 0x0F, 16));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new IllegalStateException("Unable to compute MD5", e);
    }
  }

  private static byte[] readAllBytes(Path path) {
    try {
      return Files.readAllBytes(path);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to read staged file: " + path, e);
    }
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

      var duplicateEntry = byName.get("dir/copy.txt");
      assertNotNull("missing pax duplicate entry", duplicateEntry);
      assertTrue(duplicateEntry.isRegularFile());

      var stagingDir = new File(outDir, "staging");
      assertTrue("staging directory missing", stagingDir.isDirectory());

      var expectedDigest = md5Hex("hello from pax".getBytes(StandardCharsets.UTF_8));
      assertEquals(expectedDigest, fileEntry.digest);
      assertEquals(expectedDigest, duplicateEntry.digest);
      assertEquals(stagingDir.toPath().resolve(expectedDigest), fileEntry.fsPath);
      assertEquals(fileEntry.fsPath, duplicateEntry.fsPath);
      assertArrayEquals("file content should be staged",
        "hello from pax".getBytes(StandardCharsets.UTF_8),
        readAllBytes(fileEntry.fsPath)
      );

      var linkEntry = byName.get("dir/link.txt");
      assertNotNull("missing pax symlink entry", linkEntry);
      assertTrue(linkEntry.isSymbolicLink());
      assertEquals("subdir/file.txt", linkEntry.linkName);
    });
  }

}
