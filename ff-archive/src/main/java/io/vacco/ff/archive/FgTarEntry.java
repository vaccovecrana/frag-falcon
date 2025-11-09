package io.vacco.ff.archive;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Blind dogma is why creativity and innovation die.
 */
public class FgTarEntry implements Comparable<FgTarEntry>, BasicFileAttributes {

  public static final String[] rwx = {"r", "w", "x"};

  private static final String PAX_PATH = "path";
  private static final String PAX_LINK_PATH = "linkpath";
  private static final String PAX_MTIME = "mtime";
  private static final String PAX_ATIME = "atime";
  private static final String PAX_CTIME = "ctime";
  private static final String PAX_SIZE = "size";

  public final String name;
  public final char typeFlag;
  public final boolean isDirectory;
  public final boolean isSymlink;
  public final boolean isHardlink;
  public final String linkName;
  public final boolean isExecutable;

  public final int mode;
  public Path fsPath;

  public final Set<PosixFilePermission> permissions;
  public final int size;
  private final FileTime lastModifiedTime;
  private final FileTime lastAccessTime;
  private final FileTime creationTime;

  public FgTarEntry(byte[] header) {
    this(header, Map.of());
  }

  public FgTarEntry(byte[] header, Map<String, String> paxHeaders) {
    Map<String, String> headers = paxHeaders == null ? Map.of() : paxHeaders;

    this.mode = (int) parseOctal(header, 100, 7);
    this.permissions = parsePermissions(mode);
    this.isExecutable = (mode & 0111) != 0;
    this.typeFlag = (char) header[156];

    String rawName = new String(header, 0, 100).trim();
    String resolvedName = headers.getOrDefault(PAX_PATH, rawName);
    boolean endsWithSlash = resolvedName.endsWith("/") && resolvedName.length() > 1;
    if (endsWithSlash) {
      resolvedName = resolvedName.substring(0, resolvedName.length() - 1);
    }
    this.name = resolvedName;

    this.isDirectory = typeFlag == '5' || endsWithSlash;
    this.isSymlink = typeFlag == '2';
    this.isHardlink = typeFlag == '1';

    String rawLinkName = new String(header, 157, 100).trim();
    this.linkName = headers.getOrDefault(PAX_LINK_PATH, rawLinkName);

    this.size = parseSize(header, headers);

    long fallbackMtime = parseOctal(header, 136, 12);
    this.lastModifiedTime = resolveFileTime(headers.get(PAX_MTIME), fallbackMtime);
    this.lastAccessTime = resolveFileTime(headers.get(PAX_ATIME), fallbackMtime);
    this.creationTime = resolveFileTime(headers.get(PAX_CTIME), fallbackMtime);
  }

  public boolean isFile() {
    return !isDirectory && !isSymlink && !isHardlink;
  }

  public FgTarEntry withFsPath(Path fsPath) {
    this.fsPath = Objects.requireNonNull(fsPath);
    return this;
  }

  private static Set<PosixFilePermission> parsePermissions(int mode) {
    Set<PosixFilePermission> perms = EnumSet.noneOf(PosixFilePermission.class);
    if ((mode & 0400) != 0) perms.add(PosixFilePermission.OWNER_READ);
    if ((mode & 0200) != 0) perms.add(PosixFilePermission.OWNER_WRITE);
    if ((mode & 0100) != 0) perms.add(PosixFilePermission.OWNER_EXECUTE);
    if ((mode & 0040) != 0) perms.add(PosixFilePermission.GROUP_READ);
    if ((mode & 0020) != 0) perms.add(PosixFilePermission.GROUP_WRITE);
    if ((mode & 0010) != 0) perms.add(PosixFilePermission.GROUP_EXECUTE);
    if ((mode & 0004) != 0) perms.add(PosixFilePermission.OTHERS_READ);
    if ((mode & 0002) != 0) perms.add(PosixFilePermission.OTHERS_WRITE);
    if ((mode & 0001) != 0) perms.add(PosixFilePermission.OTHERS_EXECUTE);
    return perms;
  }

  public static long parseOctal(byte[] header, int offset, int length) {
    long value = 0;
    for (int i = offset; i < offset + length; i++) {
      byte current = header[i];
      if (current == 0 || current == ' ') {
        break;
      }
      if (current >= '0' && current <= '7') {
        value = (value << 3) + (current - '0');
      }
    }
    return value;
  }

  private static int parseSize(byte[] header, Map<String, String> paxHeaders) {
    String paxSize = paxHeaders.get(PAX_SIZE);
    if (paxSize != null && !paxSize.isEmpty()) {
      long size = Long.parseLong(paxSize.trim());
      if (size > Integer.MAX_VALUE) {
        throw new IllegalStateException("Entry size exceeds supported range: " + size);
      }
      return (int) size;
    }
    long size = parseOctal(header, 124, 12);
    if (size > Integer.MAX_VALUE) {
      throw new IllegalStateException("Entry size exceeds supported range: " + size);
    }
    return (int) size;
  }

  private static FileTime resolveFileTime(String paxValue, long fallbackSeconds) {
    if (paxValue != null) {
      try {
        String value = paxValue.trim();
        long seconds;
        int nanos = 0;
        int decimalIdx = value.indexOf('.');
        if (decimalIdx >= 0) {
          seconds = Long.parseLong(value.substring(0, decimalIdx));
          String fractional = value.substring(decimalIdx + 1);
          if (!fractional.isEmpty()) {
            if (fractional.length() > 9) {
              fractional = fractional.substring(0, 9);
            }
            while (fractional.length() < 9) {
              fractional += "0";
            }
            nanos = Integer.parseInt(fractional);
          }
        } else {
          seconds = Long.parseLong(value);
        }
        return FileTime.from(Instant.ofEpochSecond(seconds, nanos));
      } catch (Exception ignored) {
        // fall back to header value
      }
    }
    long millis = Math.max(fallbackSeconds, 0) * 1000L;
    return FileTime.fromMillis(millis);
  }

  public static String modeToPosixString(int mode) {
    var perms = new char[9];
    for (int i = 0; i < 3; i++) {
      int shift = (2 - i) * 3;
      perms[i * 3] = ((mode & (4 << shift)) != 0) ? 'r' : '-';
      perms[i * 3 + 1] = ((mode & (2 << shift)) != 0) ? 'w' : '-';
      perms[i * 3 + 2] = ((mode & (1 << shift)) != 0) ? 'x' : '-';
    }
    return new String(perms);
  }

  @Override public int compareTo(FgTarEntry o) {
    return this.name.compareTo(o.name);
  }

  @Override public String toString() {
    return String.format(
      "[%s%s%s%s%s, %s, %08d] %s%s",
      isDirectory ? "d" : "",
      isSymlink ? "l" : "",
      isHardlink ? "h" : "",
      isFile() ? "f" : "",
      isExecutable ? "x" : "",
      modeToPosixString(mode),
      size,
      name,
      linkName != null && !linkName.isEmpty()
        ? String.format(" <-- %s", linkName)
        : ""
    );
  }

  @Override
  public FileTime lastModifiedTime() {
    return lastModifiedTime;
  }

  @Override
  public FileTime lastAccessTime() {
    return lastAccessTime;
  }

  @Override
  public FileTime creationTime() {
    return creationTime;
  }

  @Override
  public boolean isRegularFile() {
    return isFile();
  }

  @Override
  public boolean isDirectory() {
    return isDirectory;
  }

  @Override
  public boolean isSymbolicLink() {
    return isSymlink;
  }

  @Override
  public boolean isOther() {
    return !isRegularFile() && !isDirectory() && !isSymbolicLink() && !isHardlink;
  }

  @Override
  public long size() {
    return size;
  }

  @Override
  public Object fileKey() {
    return fsPath != null ? fsPath : name;
  }

}
