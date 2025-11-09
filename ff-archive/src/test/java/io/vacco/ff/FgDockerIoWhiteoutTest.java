package io.vacco.ff;

import io.vacco.ff.archive.FgTarEntry;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static io.vacco.ff.FgDockerIo.applyWhiteouts;
import static io.vacco.ff.util.FgIo.deleteRecursively;
import static org.junit.Assert.*;
import static j8spec.J8Spec.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgDockerIoWhiteoutTest {

  private static byte[] headerFor(String name, char typeFlag) {
    var header = new byte[512];
    Arrays.fill(header, (byte) 0);
    var nameBytes = name.getBytes(StandardCharsets.UTF_8);
    System.arraycopy(nameBytes, 0, header, 0, Math.min(nameBytes.length, 100));
    header[156] = (byte) typeFlag;
    header[100] = '0';
    header[101] = '0';
    header[102] = '0';
    header[103] = '6';
    header[104] = '4';
    header[105] = '4';
    header[124] = '0';
    header[125] = '0';
    header[126] = '0';
    header[127] = '0';
    header[128] = '0';
    header[129] = '0';
    header[130] = '0';
    return header;
  }

  private static FgTarEntry fileEntry(String name, Path staged, String digest) {
    return new FgTarEntry(headerFor(name, '0'), Map.of("size", "0"))
      .withFsPath(staged)
      .withDigest(digest);
  }

  private static FgTarEntry directoryEntry(String name) {
    return new FgTarEntry(headerFor(name.endsWith("/") ? name : name + "/", '5'), Map.of("size", "0"));
  }

  static {
    it("removes targeted files but keeps shared staged data when still referenced", () -> {
      var tempDir = Files.createTempDirectory("whiteout-test");
      try {
        var stagedFile = tempDir.resolve("digestA");
        Files.writeString(stagedFile, "same-content");
        var stagedWhiteout = tempDir.resolve("digestWh");
        Files.writeString(stagedWhiteout, "whiteout");

        var file = fileEntry("dir/file.txt", stagedFile, "digestA");
        var copy = fileEntry("dir/copy.txt", stagedFile, "digestA");
        var dir = directoryEntry("dir");
        var whiteout = fileEntry("dir/.wh.file.txt", stagedWhiteout, "digestWh");

        var entries = new TreeSet<FgTarEntry>();
        entries.addAll(Arrays.asList(file, copy, dir, whiteout));

        var stagedRefCounts = new HashMap<Path, Integer>();
        for (var entry : entries) {
          if (entry.fsPath != null) {
            stagedRefCounts.merge(entry.fsPath, 1, Integer::sum);
          }
        }

        var stagedToDelete = applyWhiteouts(entries, stagedRefCounts);

        assertFalse("whiteout should be removed", entries.contains(whiteout));
        assertFalse("targeted file should be removed", entries.contains(file));
        assertTrue("sibling file should remain", entries.contains(copy));
        assertFalse("shared staged content should remain", stagedToDelete.contains(stagedFile));
        assertTrue("whiteout staged file should be deleted", stagedToDelete.contains(stagedWhiteout));
      } finally {
        deleteRecursively(tempDir.toFile(), Exception::printStackTrace);
      }
    });

    it("removes entire directory tree for opaque whiteouts", () -> {
      var tempDir = Files.createTempDirectory("whiteout-opq");
      try {
        var stagedA = tempDir.resolve("digestA");
        Files.writeString(stagedA, "A");
        var stagedB = tempDir.resolve("digestB");
        Files.writeString(stagedB, "B");
        var stagedWh = tempDir.resolve("digestWh");
        Files.writeString(stagedWh, "whiteout");

        var fileA = fileEntry("folder/a.txt", stagedA, "digestA");
        var fileB = fileEntry("folder/sub/b.txt", stagedB, "digestB");
        var folder = directoryEntry("folder");
        var subFolder = directoryEntry("folder/sub");
        var opq = fileEntry("folder/.wh..wh..opq", stagedWh, "digestWh");

        var entries = new TreeSet<FgTarEntry>();
        entries.addAll(Arrays.asList(fileA, fileB, folder, subFolder, opq));

        var stagedRefCounts = new HashMap<Path, Integer>();
        for (var entry : entries) {
          if (entry.fsPath != null) {
            stagedRefCounts.merge(entry.fsPath, 1, Integer::sum);
          }
        }

        var stagedToDelete = applyWhiteouts(entries, stagedRefCounts);

        assertFalse(entries.contains(fileA));
        assertFalse(entries.contains(fileB));
        assertFalse(entries.contains(folder));
        assertFalse(entries.contains(subFolder));
        assertFalse(entries.contains(opq));
        assertTrue(stagedToDelete.contains(stagedA));
        assertTrue(stagedToDelete.contains(stagedB));
        assertTrue(stagedToDelete.contains(stagedWh));
      } finally {
        deleteRecursively(tempDir.toFile(), Exception::printStackTrace);
      }
    });
  }
}
