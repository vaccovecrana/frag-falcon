package io.vacco.ff;

import java.io.*;
import java.nio.file.*;
import java.util.function.BiConsumer;

public class FgTarIo {

  private static final int TAR_BLOCK_SIZE = 512;

  public static void extract(File tar, File outDir, BiConsumer<FgTarEntry, Exception> onError) throws IOException {
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
            break;
          }
          var outputFile = new File(outDir, entry.name).getCanonicalFile();
          if (entry.isDirectory) {
            if (!outputFile.exists()) {
              outputFile.mkdirs();
            }
          } else if (entry.isSymlink) {
            Files.createDirectories(outputFile.getParentFile().toPath());
            Files.createSymbolicLink(outputFile.toPath(), Paths.get(entry.linkName));
          } else if (entry.isHardlink) {
            Files.createDirectories(outputFile.getParentFile().toPath());
            var linkFile = new File(outDir, entry.linkName).getCanonicalFile();
            Files.createLink(outputFile.toPath(), linkFile.toPath());
          } else if (entry.isFile()) {
            Files.createDirectories(outputFile.getParentFile().toPath());
            try (var bos = new BufferedOutputStream(new FileOutputStream(outputFile))) {
              int fileSize = entry.size;
              while (fileSize > 0) {
                int len = bis.read(buffer, 0, Math.min(TAR_BLOCK_SIZE, fileSize));
                if (len == -1) {
                  break;
                }
                bos.write(buffer, 0, len);
                fileSize -= len;
              }
            }
          }
          if (entry.isFile() || entry.isDirectory) {
            Files.setPosixFilePermissions(outputFile.toPath(), entry.permissions);
            if (entry.isExecutable) {
              outputFile.setExecutable(true);
            }
          }
          long skipBytes = TAR_BLOCK_SIZE - (entry.size % TAR_BLOCK_SIZE);
          if (skipBytes < TAR_BLOCK_SIZE) {
            bis.skip(skipBytes);
          }
        } catch (Exception e) {
          onError.accept(entry, e);
        }
      }
    }
  }

}
