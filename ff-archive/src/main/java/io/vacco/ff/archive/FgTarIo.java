package io.vacco.ff.archive;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static java.nio.file.Files.*;

public class FgTarIo {

  private static final int TAR_BLOCK_SIZE = 512;

  private static final char PAX_EXTENDED_HEADER = 'x';

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

  private static void skipPadding(long size, BufferedInputStream bis) {
    try {
      long remainder = size % TAR_BLOCK_SIZE;
      if (remainder > 0) {
        long skipBytes = TAR_BLOCK_SIZE - remainder;
        while (skipBytes > 0) {
          long skipped = bis.skip(skipBytes);
          if (skipped <= 0) {
            break;
          }
          skipBytes -= skipped;
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static long parseOctal(byte[] header, int offset, int length) {
    return FgTarEntry.parseOctal(header, offset, length);
  }

  private static Map<String, String> readPaxHeaders(BufferedInputStream bis, int size) throws IOException {
    var data = new byte[size];
    int read = 0;
    while (read < size) {
      int len = bis.read(data, read, size - read);
      if (len == -1) {
        throw new IOException("Unexpected end of stream while reading PAX headers");
      }
      read += len;
    }
    var headers = new HashMap<String, String>();
    int offset = 0;
    while (offset < read) {
      int lenStart = offset;
      int recordLen = 0;
      while (offset < read && data[offset] != ' ') {
        recordLen = recordLen * 10 + (data[offset] - '0');
        offset++;
      }
      if (offset >= read || data[offset] != ' ') {
        break;
      }
      offset++; // skip space
      int recordStart = lenStart;
      int limit = Math.min(recordLen, read - recordStart);
      if (limit <= 0) {
        break;
      }
      var record = new String(data, recordStart, limit, StandardCharsets.UTF_8);
      int keyStart = record.indexOf(' ') + 1;
      if (keyStart <= 0) {
        offset = recordStart + recordLen;
        continue;
      }
      int equalsIdx = record.indexOf('=', keyStart);
      if (equalsIdx == -1) {
        offset = recordStart + recordLen;
        continue;
      }
      String key = record.substring(keyStart, equalsIdx);
      String value = record.substring(equalsIdx + 1);
      if (value.endsWith("\n")) {
        value = value.substring(0, value.length() - 1);
      }
      headers.put(key, value);
      offset = recordStart + recordLen;
    }
    return headers;
  }

  public static List<FgTarEntry> extract(File tar, File outDir, BiConsumer<FgTarEntry, Exception> onError) {
    var entriesWithPermissions = new ArrayList<FgTarEntry>();
    byte[] tempBuffer = new byte[8192]; // 8 KB buffer for bulk writes

    try (var fis = new FileInputStream(tar);
         var bis = new BufferedInputStream(fis)) {
      var buffer = new byte[TAR_BLOCK_SIZE];
      while (true) {
        FgTarEntry entry = null;
        Map<String, String> paxHeaders = Collections.emptyMap();
        try {
          int bytesRead = bis.read(buffer);
          if (bytesRead == -1 || buffer[0] == 0) {
            break;
          }

          char typeFlag = (char) buffer[156];
          if (typeFlag == PAX_EXTENDED_HEADER) {
            int paxSize = (int) parseOctal(buffer, 124, 12);
            paxHeaders = readPaxHeaders(bis, paxSize);
            skipPadding(paxSize, bis);
            int nextHeaderBytes = bis.read(buffer);
            if (nextHeaderBytes == -1 || buffer[0] == 0) {
              break;
            }
            typeFlag = (char) buffer[156];
          }

          entry = new FgTarEntry(buffer, paxHeaders);
          if (entry.name.isEmpty()) {
            continue;
          }

          var outputFile = new File(outDir, entry.name).getCanonicalFile();
          Path outputPath = outputFile.toPath();
          if (entry.isDirectory) {
            createDirectories(outputPath);
          } else {
            createDirectories(outputFile.getParentFile().toPath());
            if (entry.isSymlink) {
              createSymbolicLink(outputPath, Paths.get(entry.linkName));
            } else if (entry.isHardlink) {
              var linkFile = new File(outDir, entry.linkName).getCanonicalFile();
              createLink(outputPath, linkFile.toPath());
            } else if (entry.isFile()) {
              copy(entry, bis, buffer, outputFile, tempBuffer);
            }
          }
          if (entry.isFile() || entry.isDirectory || entry.isSymlink) {
            entriesWithPermissions.add(entry.withFsPath(outputPath));
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

  private static void afterEntry(FgTarEntry entry, BufferedInputStream bis) {
    skipPadding(entry.size, bis);
  }

}
