package io.vacco.ff.initramfs;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;

import static java.nio.file.Files.*;

public class FgTarIo {

  private static final int TAR_BLOCK_SIZE = 512;

  private static void afterEntry(FgTarEntry entry, BufferedInputStream bis) {
    try {
      long skipBytes = TAR_BLOCK_SIZE - (entry.size % TAR_BLOCK_SIZE);
      if (skipBytes < TAR_BLOCK_SIZE) {
        bis.skip(skipBytes);
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void copy(FgTarEntry entry, BufferedInputStream bis, byte[] buffer,
                           File outputFile, byte[] tempBuffer) throws IOException {
    int fileSize = entry.size;
    var fileBb = ByteBuffer.allocateDirect(fileSize);
    while (fileSize > 0) {
      int len = bis.read(buffer, 0, Math.min(TAR_BLOCK_SIZE, fileSize));
      if (len == -1) {
        break;
      }
      fileBb.put(buffer, 0, len);
      fileSize -= len;
    }
    fileBb.flip();
    try (var bos = new BufferedOutputStream(new FileOutputStream(outputFile))) {
      Arrays.fill(tempBuffer, (byte) 0);
      while (fileBb.hasRemaining()) {
        int len = Math.min(tempBuffer.length, fileBb.remaining());
        fileBb.get(tempBuffer, 0, len);
        bos.write(tempBuffer, 0, len);
      }
    }
  }

  public static boolean hasCommonPathPrefix(String entryName1, String entryName2) {
    var pathComponents1 = entryName1.split("/");
    var pathComponents2 = entryName2.split("/");
    int minLength = Math.min(pathComponents1.length, pathComponents2.length);
    for (int i = 0; i < minLength; i++) {
      if (!pathComponents1[i].equals(pathComponents2[i])) {
        return i > 0; // Return true if we matched at least one component
      }
    }
    return minLength > 0;
  }

  public static List<FgTarEntry> extract(File tar, File outDir, BiConsumer<FgTarEntry, Exception> onError) {
    var entriesWithPermissions = new ArrayList<FgTarEntry>();
    var lastDirectory = "";
    byte[] tempBuffer = new byte[8192]; // 8 KB buffer for bulk writes

    try (var fis = new FileInputStream(tar);
         var bis = new BufferedInputStream(fis)) {
      var buffer = new byte[TAR_BLOCK_SIZE];
      while (true) {
        FgTarEntry entry = null;
        try {
          int bytesRead = bis.read(buffer);
          if (bytesRead == -1 || buffer[0] == 0) {
            break;
          }
          entry = new FgTarEntry(buffer);
          if (entry.name.isEmpty()) {
            continue;
          }

          if (!entry.isSymlink && !entry.isHardlink && !entry.name.startsWith(lastDirectory)) {
            var common = hasCommonPathPrefix(lastDirectory, entry.name);
            var combined = lastDirectory + entry.name;
            if (!common && combined.length() >= 100) { // Kludge! Tar entry name limit
              entry.name = combined;
            }
          }

          var outputFile = new File(outDir, entry.name).getCanonicalFile();
          if (entry.isDirectory) {
            createDirectories(outputFile.toPath());
            lastDirectory = entry.name;
          } else {
            createDirectories(outputFile.getParentFile().toPath());
            if (entry.isSymlink) {
              createSymbolicLink(outputFile.toPath(), Paths.get(entry.linkName));
            } else if (entry.isHardlink) {
              var linkFile = new File(outDir, entry.linkName).getCanonicalFile();
              createLink(outputFile.toPath(), linkFile.toPath());
            } else if (entry.isFile()) {
              copy(entry, bis, buffer, outputFile, tempBuffer);
            }
          }
          if (entry.isFile() || entry.isDirectory) {
            entriesWithPermissions.add(entry.withFsPath(outputFile.toPath()));
          }
        } catch (Exception e) {
          onError.accept(entry, e);
        } finally {
          if (entry != null) {
            afterEntry(entry, bis);
          }
        }
      }
    } catch (IOException e) {
      onError.accept(null, e);
    }
    return entriesWithPermissions;
  }

}
