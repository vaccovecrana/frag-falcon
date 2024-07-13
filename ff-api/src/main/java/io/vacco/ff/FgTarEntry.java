package io.vacco.ff;

import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/**
 * Blind dogma is why creativity and innovation die.
 */
public class FgTarEntry {

  public final String name;
  public final boolean isDirectory;
  public final boolean isSymlink;
  public final boolean isHardlink;
  public final String linkName;
  public final Set<PosixFilePermission> permissions;
  public final boolean isExecutable;
  public final int size;

  public FgTarEntry(byte[] header) {
    this.name = new String(header, 0, 100).trim();
    this.isDirectory = header[156] == '5';
    this.isSymlink = header[156] == '2';
    this.isHardlink = header[156] == '1';
    this.linkName = new String(header, 157, 100).trim();
    this.permissions = parsePermissions(header);
    this.isExecutable = (header[100] & 0b001001001) != 0;
    this.size = parseOctal(header, 124, 12);
  }

  public boolean isFile() {
    return !isDirectory && !isSymlink && !isHardlink;
  }

  private Set<PosixFilePermission> parsePermissions(byte[] header) {
    Set<PosixFilePermission> perms = EnumSet.noneOf(PosixFilePermission.class);
    int mode = parseOctal(header, 100, 7);
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

  private int parseOctal(byte[] header, int offset, int length) {
    int value = 0;
    for (int i = offset; i < offset + length; i++) {
      if (header[i] == 0) break;
      if (header[i] >= '0' && header[i] <= '7') {
        value = (value << 3) + (header[i] - '0');
      }
    }
    return value;
  }

  @Override public String toString() {
    return String.format(
      "%s [%s %s %s %s %s %s, %08d]",
      name,
      isDirectory ? "d" : "",
      isSymlink ? "l" : "",
      isHardlink ? "h" : "",
      linkName,
      permissions,
      isExecutable ? "x" : "",
      size
    );
  }
}
