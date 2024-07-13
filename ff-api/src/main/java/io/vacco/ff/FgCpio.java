package io.vacco.ff;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.function.BiConsumer;

public class FgCpio {

  private static final String CPIO_TRAILER = "TRAILER!!!";

  public static void archive(File inputDir, File outputFile, BiConsumer<Path, Exception> onError) {
    try (var fos = new FileOutputStream(outputFile);
         var bos = new BufferedOutputStream(fos);
         var dos = new DataOutputStream(bos)) {
      var fileList = new ArrayList<Path>();
      Files.walk(inputDir.toPath()).forEach(fileList::add);
      for (var file : fileList) {
        try {
          if (Files.isDirectory(file)) continue;
          writeHeader(dos, file, inputDir.toPath());
          if (!Files.isSymbolicLink(file)) {
            try (var is = Files.newInputStream(file)) {
              var buffer = new byte[4096];
              int len;
              while ((len = is.read(buffer)) > 0) {
                dos.write(buffer, 0, len);
              }
            }
          } else {
            var target = Files.readSymbolicLink(file).toString();
            dos.writeBytes(target);
          }
          padTo4Bytes(dos);
        } catch (Exception e) {
          onError.accept(file, e);
        }
      }
      writeTrailer(dos);
    } catch (Exception e) {
      onError.accept(null, e);
    }
  }

  private static void writeHeader(DataOutputStream dos, Path file, Path baseDir) throws IOException {
    var attrs = Files.readAttributes(file, PosixFileAttributes.class);
    var mtime = attrs.lastModifiedTime();
    long size = Files.isSymbolicLink(file) ? Files.readSymbolicLink(file).toString().length() : attrs.size();
    var name = baseDir.relativize(file).toString();

    int mode = 0;
    if (attrs.isDirectory()) {
      mode |= 0040000;
    } else if (attrs.isSymbolicLink()) {
      mode |= 0120000;
    } else {
      mode |= 0100000;
    }
    for (PosixFilePermission perm : attrs.permissions()) {
      switch (perm) {
        case OWNER_READ:      mode |= 0400; break;
        case OWNER_WRITE:     mode |= 0200; break;
        case OWNER_EXECUTE:   mode |= 0100; break;
        case GROUP_READ:      mode |= 0040; break;
        case GROUP_WRITE:     mode |= 0020; break;
        case GROUP_EXECUTE:   mode |= 0010; break;
        case OTHERS_READ:     mode |= 0004; break;
        case OTHERS_WRITE:    mode |= 0002; break;
        case OTHERS_EXECUTE:  mode |= 0001; break;
      }
    }

    dos.writeBytes(String.format("070701"));
    dos.writeBytes(String.format("%08X", 0)); // inode
    dos.writeBytes(String.format("%08X", mode)); // mode
    dos.writeBytes(String.format("%08X", 0)); // uid
    dos.writeBytes(String.format("%08X", 0)); // gid
    dos.writeBytes(String.format("%08X", Files.isSymbolicLink(file) ? 1 : attrs.isDirectory() ? 2 : 1)); // nlink
    dos.writeBytes(String.format("%08X", mtime.toMillis() / 1000)); // mtime
    dos.writeBytes(String.format("%08X", size)); // filesize
    dos.writeBytes(String.format("%08X", 0)); // dev major
    dos.writeBytes(String.format("%08X", 0)); // dev minor
    dos.writeBytes(String.format("%08X", 0)); // rdev major
    dos.writeBytes(String.format("%08X", 0)); // rdev minor
    dos.writeBytes(String.format("%08X", name.length() + 1)); // namesize
    dos.writeBytes("00000000"); // checksum (ignored)
    dos.writeBytes(name);
    dos.writeByte(0); // null-terminator for name
    padTo4Bytes(dos);
  }

  private static void padTo4Bytes(DataOutputStream dos) throws IOException {
    long offset = dos.size();
    int pad = (int) (4 - (offset % 4));
    if (pad != 4) {
      for (int i = 0; i < pad; i++) {
        dos.writeByte(0);
      }
    }
  }

  private static void writeTrailer(DataOutputStream dos) throws IOException {
    dos.writeBytes(String.format("070701"));
    dos.writeBytes(String.format("%08X", 0)); // inode
    dos.writeBytes(String.format("%08X", 0)); // mode
    dos.writeBytes(String.format("%08X", 0)); // uid
    dos.writeBytes(String.format("%08X", 0)); // gid
    dos.writeBytes(String.format("%08X", 1)); // nlink
    dos.writeBytes(String.format("%08X", 0)); // mtime
    dos.writeBytes(String.format("%08X", 0)); // filesize
    dos.writeBytes(String.format("%08X", 0)); // dev major
    dos.writeBytes(String.format("%08X", 0)); // dev minor
    dos.writeBytes(String.format("%08X", 0)); // rdev major
    dos.writeBytes(String.format("%08X", 0)); // rdev minor
    dos.writeBytes(String.format("%08X", CPIO_TRAILER.length() + 1)); // namesize
    dos.writeBytes("00000000"); // checksum (ignored)
    dos.writeBytes(CPIO_TRAILER);
    dos.writeByte(0); // null-terminator for name
    padTo4Bytes(dos);
  }

}
