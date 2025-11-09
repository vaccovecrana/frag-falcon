package io.vacco.ff.archive;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.BiConsumer;

import static java.nio.file.Files.*;

public class FgTarIo {

  private static final int TAR_BLOCK_SIZE = 512;

  private static final char PAX_EXTENDED_HEADER = 'x';

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

  private static String toHex(byte[] bytes) {
    var sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
      sb.append(Character.forDigit(b & 0x0F, 16));
    }
    return sb.toString();
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

  private static Path stageFile(FgTarEntry entry, BufferedInputStream bis, byte[] buffer, Path stagingDir)
    throws IOException, NoSuchAlgorithmException {
    var md = MessageDigest.getInstance("MD5");
    var tempFile = createTempFile(stagingDir, "tar-", ".tmp");
    int remaining = entry.size;

    try (var os = newOutputStream(tempFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
      while (remaining > 0) {
        int len = bis.read(buffer, 0, Math.min(buffer.length, remaining));
        if (len == -1) {
          throw new EOFException("Unexpected end of stream reading entry: " + entry.name);
        }
        md.update(buffer, 0, len);
        os.write(buffer, 0, len);
        remaining -= len;
      }
    }

    var digestHex = toHex(md.digest());
    entry.withDigest(digestHex);

    var target = stagingDir.resolve(digestHex);
    if (exists(target)) {
      deleteIfExists(tempFile);
    } else {
      move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
    }
    return target;
  }

  private static void afterEntry(FgTarEntry entry, BufferedInputStream bis) {
    skipPadding(entry.size, bis);
  }

  public static List<FgTarEntry> extract(File tar, File outDir, BiConsumer<FgTarEntry, Exception> onError) {
    var entriesWithPermissions = new ArrayList<FgTarEntry>();
    byte[] dataBuffer = new byte[8192]; // 8 KB buffer for bulk writes

    try (var fis = new FileInputStream(tar);
         var bis = new BufferedInputStream(fis)) {
      var stagingDir = outDir.toPath().resolve("staging");
      createDirectories(stagingDir);
      var headerBuffer = new byte[TAR_BLOCK_SIZE];
      while (true) {
        FgTarEntry entry = null;
        Map<String, String> paxHeaders = Collections.emptyMap();
        try {
          int bytesRead = bis.read(headerBuffer);
          if (bytesRead == -1 || headerBuffer[0] == 0) {
            break;
          }

          char typeFlag = (char) headerBuffer[156];
          if (typeFlag == PAX_EXTENDED_HEADER) {
            int paxSize = (int) parseOctal(headerBuffer, 124, 12);
            paxHeaders = readPaxHeaders(bis, paxSize);
            skipPadding(paxSize, bis);
            int nextHeaderBytes = bis.read(headerBuffer);
            if (nextHeaderBytes == -1 || headerBuffer[0] == 0) {
              break;
            }
            typeFlag = (char) headerBuffer[156];
          }

          entry = new FgTarEntry(headerBuffer, paxHeaders);
          if (entry.name.isEmpty()) {
            continue;
          }

          if (entry.isFile()) {
            var stagedPath = stageFile(entry, bis, dataBuffer, stagingDir);
            entriesWithPermissions.add(entry.withFsPath(stagedPath));
          } else {
            entriesWithPermissions.add(entry);
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
