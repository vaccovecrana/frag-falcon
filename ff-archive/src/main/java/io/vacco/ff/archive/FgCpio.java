package io.vacco.ff.archive;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;

import static java.nio.file.Files.*;
import static java.lang.String.format;

public class FgCpio {

  private static final String CPIO_TRAILER = "TRAILER!!!";

  private static void writeHex(DataOutputStream dos, long value) throws IOException {
    dos.writeBytes(String.format("%08X", value));
  }

  private static void padTo4Bytes(DataOutputStream dos) throws IOException {
    int pad = (4 - (dos.size() % 4)) % 4;
    for (int i = 0; i < pad; i++) {
      dos.writeByte(0);
    }
  }

  private static long dataSize(FgTarEntry entry) throws IOException {
    if (entry.isDirectory()) {
      return 0;
    }
    if (entry.isSymbolicLink()) {
      return entry.linkName != null ? entry.linkName.getBytes(StandardCharsets.UTF_8).length : 0;
    }
    if (entry.isFile()) {
      if (entry.fsPath != null) {
        return size(entry.fsPath);
      }
      return entry.size;
    }
    return 0;
  }

  private static int computeMode(FgTarEntry entry) {
    int mode = entry.mode;
    if (entry.isDirectory()) {
      mode |= 0040000;
    } else if (entry.isSymbolicLink()) {
      mode |= 0120000;
    } else {
      mode |= 0100000;
    }
    return mode;
  }

  private static void writeHeaderFromEntry(DataOutputStream dos, FgTarEntry entry) throws IOException {
    var name = entry.name != null ? entry.name : "";
    long fileSize = dataSize(entry);
    long mtimeSeconds = entry.lastModifiedTime().toMillis() / 1000;

    dos.writeBytes("070701");                 // Magic
    writeHex(dos, 0);                          // Inode (unused)
    writeHex(dos, computeMode(entry));         // Mode
    writeHex(dos, 0);                          // UID
    writeHex(dos, 0);                          // GID
    writeHex(dos, entry.isDirectory() ? 2 : 1);// Nlink
    writeHex(dos, mtimeSeconds);               // Mtime
    writeHex(dos, fileSize);                   // Filesize
    writeHex(dos, 0);                          // Dev major
    writeHex(dos, 0);                          // Dev minor
    writeHex(dos, 0);                          // Rdev major
    writeHex(dos, 0);                          // Rdev minor
    writeHex(dos, name.length() + 1L);         // Namesize (include null)
    writeHex(dos, 0);                          // Checksum (ignored)
    dos.writeBytes(name);
    dos.writeByte(0);
    padTo4Bytes(dos);
  }

  private static void writeDataFromEntry(DataOutputStream dos, FgTarEntry entry) throws IOException {
    if (entry.isDirectory()) {
      padTo4Bytes(dos);
      return;
    }
    if (entry.isSymbolicLink()) {
      var bytes = entry.linkName != null ? entry.linkName.getBytes(StandardCharsets.UTF_8) : new byte[0];
      dos.write(bytes);
      padTo4Bytes(dos);
      return;
    }
    if (entry.isFile()) {
      if (entry.fsPath == null) {
        throw new IllegalStateException(format("Missing staged content for entry [%s]", entry.name));
      }
      try (var is = newInputStream(entry.fsPath)) {
        var buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) {
          dos.write(buffer, 0, len);
        }
      }
      padTo4Bytes(dos);
    } else {
      padTo4Bytes(dos);
    }
  }

  private static void writeTrailer(DataOutputStream dos) throws IOException {
    dos.writeBytes("070701");                 // Magic
    writeHex(dos, 0);                          // Inode
    writeHex(dos, 0);                          // Mode
    writeHex(dos, 0);                          // UID
    writeHex(dos, 0);                          // GID
    writeHex(dos, 0);                          // NLink
    writeHex(dos, 0);                          // Mtime
    writeHex(dos, 0);                          // Filesize
    writeHex(dos, 0);                          // Dev major
    writeHex(dos, 0);                          // Dev minor
    writeHex(dos, 0);                          // Rdev major
    writeHex(dos, 0);                          // Rdev minor
    writeHex(dos, CPIO_TRAILER.length() + 1);  // Namesize
    writeHex(dos, 0);                          // Checksum
    dos.writeBytes(CPIO_TRAILER);
    dos.writeByte(0);
    padTo4Bytes(dos);
  }

  public static void archive(Set<FgTarEntry> files, File outputCpioFile, BiConsumer<Path, Exception> onError) {
    try (var fos = new FileOutputStream(outputCpioFile);
         var bos = new BufferedOutputStream(fos);
         var dos = new DataOutputStream(bos)) {

      var sortedEntries = new ArrayList<>(files);
      sortedEntries.sort(Comparator.comparing(e -> e.name));

      for (var entry : sortedEntries) {
        try {
          writeHeaderFromEntry(dos, entry);
          writeDataFromEntry(dos, entry);
        } catch (Exception e) {
          var path = entry.fsPath != null ? entry.fsPath : Path.of(entry.name != null ? entry.name : "");
          onError.accept(path, e);
        }
      }

      writeTrailer(dos);
    } catch (Exception e) {
      onError.accept(null, e);
    }
  }
}
