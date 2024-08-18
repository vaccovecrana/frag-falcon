package io.vacco.ff.initramfs;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.function.BiConsumer;

import static java.nio.file.Files.*;
import static java.lang.String.format;
import static io.vacco.ff.util.FgIo.*;

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

  private static void writeHeader(DataOutputStream dos, Path path, Path baseDir) throws IOException {
    var attrs = readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    var posixAttrs = readAttributes(path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    var mtime = attrs.lastModifiedTime().toMillis() / 1000;
    long size = isSymbolicLink(path)
      ? readSymbolicLink(path).toString().length()
      : attrs.isDirectory()
      ? 0
      : attrs.size();
    var name = baseDir.relativize(path).toString().replace(File.separator, "/");

    int mode = 0;
    if (attrs.isDirectory()) {
      mode |= 0040000;
    } else if (isSymbolicLink(path)) {
      mode |= 0120000;
    } else {
      mode |= 0100000;
    }

    for (var perm : posixAttrs.permissions()) {
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

    dos.writeBytes("070701");                   // Magic
    writeHex(dos, 0);                           // Inode
    writeHex(dos, mode);                        // Mode
    writeHex(dos, 0);                           // UID
    writeHex(dos, 0);                           // GID
    writeHex(dos, attrs.isDirectory() ? 2 : 1); // NLink
    writeHex(dos, mtime);                       // Mtime
    writeHex(dos, size);                        // Filesize
    writeHex(dos, 0);                           // Dev major
    writeHex(dos, 0);                           // Dev minor
    writeHex(dos, 0);                           // Rdev major
    writeHex(dos, 0);                           // Rdev minor
    writeHex(dos, name.length() + 1);           // Namesize
    writeHex(dos, 0);                           // Checksum (ignored)
    dos.writeBytes(name);
    dos.writeByte(0);                           // Null terminator for name
    padTo4Bytes(dos);
  }

  private static void writeTrailer(DataOutputStream dos) throws IOException {
    dos.writeBytes("070701");                 // Magic
    writeHex(dos, 0);                         // Inode
    writeHex(dos, 0);                         // Mode
    writeHex(dos, 0);                         // UID - root
    writeHex(dos, 0);                         // GID - root
    writeHex(dos, 0);                         // NLink
    writeHex(dos, 0);                         // Mtime
    writeHex(dos, 0);                         // Filesize
    writeHex(dos, 0);                         // Dev major
    writeHex(dos, 0);                         // Dev minor
    writeHex(dos, 0);                         // Rdev major
    writeHex(dos, 0);                         // Rdev minor
    writeHex(dos, CPIO_TRAILER.length() + 1); // Namesize
    writeHex(dos, 0);                         // Checksum (ignored)
    dos.writeBytes(CPIO_TRAILER);
    dos.writeByte(0);                         // Null terminator for name
    padTo4Bytes(dos);
  }

  private static void writeData(DataOutputStream dos, Path path) {
    try {
      if (isSymbolicLink(path)) {
        var target = readSymbolicLink(path).toString();
        var bytes = target.getBytes();
        dos.write(bytes);
      } else if (isRegularFile(path)) {
        if (!isReadable(path)) {
          addPermissions(path, PosixFilePermission.OWNER_READ);
        }
        try (var is = newInputStream(path)) {
          var buffer = new byte[4096];
          int len;
          while ((len = is.read(buffer)) > 0) {
            dos.write(buffer, 0, len);
          }
        }
      }
      padTo4Bytes(dos);
    } catch (IOException e) {
      throw new IllegalStateException(format("Unable to write path data: [%s]", path), e);
    }
  }

  public static void archive(File inputDir, File outputFile, BiConsumer<Path, Exception> onError) {
    try (var fos = new FileOutputStream(outputFile);
         var bos = new BufferedOutputStream(fos);
         var dos = new DataOutputStream(bos)) {
      var fileList = new ArrayList<Path>();
      walk(inputDir.toPath()).forEach(fileList::add);
      for (var path : fileList) {
        try {
          writeHeader(dos, path, inputDir.toPath());
          writeData(dos, path);
        } catch (Exception e) {
          onError.accept(path, e);
        }
      }
      writeTrailer(dos);
    } catch (Exception e) {
      onError.accept(null, e);
    }
  }

}
