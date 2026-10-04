package io.vacco.ff;

import io.vacco.ff.net.FgRoot;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;

import static j8spec.J8Spec.it;
import static org.junit.Assert.*;

/**
 * Security: the OCI extractor must confine every entry to the target root
 * (kernel openat2 RESOLVE_IN_ROOT). Hostile layers must fail provisioning
 * instead of escaping.
 */
@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgSecureExtractTest {

  /* --- minimal tar writer -------------------------------------------- */

  private static void header(byte[] buf, String name, String link, char type, long size, int mode) {
    java.util.Arrays.fill(buf, (byte) 0);
    put(buf, 0, name, 100);
    put(buf, 100, String.format("%07o", mode), 8);
    put(buf, 108, "0000000", 8);
    put(buf, 116, "0000000", 8);
    put(buf, 124, String.format("%011o", size), 12);
    put(buf, 136, "00000000000", 12);
    buf[156] = (byte) type;
    put(buf, 157, link, 100);
    put(buf, 257, "ustar", 6);
    put(buf, 263, "00", 2);
  }

  private static void put(byte[] buf, int off, String s, int len) {
    byte[] b = s.getBytes(StandardCharsets.UTF_8);
    System.arraycopy(b, 0, buf, off, Math.min(b.length, len));
  }

  private static void writeTar(File out, Object... entries) throws IOException {
    try (var fos = new FileOutputStream(out)) {
      for (var e : entries) {
        if (e instanceof Object[] entry) {
          String name = (String) entry[0];
          char type = (Character) entry[1];
          String link = (String) entry[2];
          byte[] data = type == '0' ? ((String) entry[3]).getBytes(StandardCharsets.UTF_8) : new byte[0];
          long size = data.length;
          int mode = entry.length > 4 ? (Integer) entry[4] : 0755;
          byte[] hdr = new byte[512];
          header(hdr, name, link, type, size, mode);
          fos.write(hdr);
          if (size > 0) {
            fos.write(data);
            int pad = (int) ((512 - (size % 512)) % 512);
            if (pad > 0) {
              fos.write(new byte[pad]);
            }
          }
        }
      }
      fos.write(new byte[1024]);
    }
  }

  private static Object file(String name, String content) {
    return new Object[]{name, '0', "", content};
  }

  private static Object file(String name, String content, int mode) {
    return new Object[]{name, '0', "", content, mode};
  }

  private static Object dir(String name) {
    return new Object[]{name, '5', "", ""};
  }

  private static Object dir(String name, int mode) {
    return new Object[]{name, '5', "", "", mode};
  }

  private static Object symlink(String name, String target) {
    return new Object[]{name, '2', target, ""};
  }

  private static Object hardlink(String name, String target) {
    return new Object[]{name, '1', target, ""};
  }

  private static File freshRoot() throws IOException {
    var dir = new File(FgTest.WORK, "sec-" + System.nanoTime());
    dir.mkdirs();
    return dir;
  }

  static {
    it("extracts ordinary files, directories and absolute-target symlinks", () -> {
      var root = freshRoot();
      var tar = new File(FgTest.WORK, "ok.tar");
      writeTar(tar, dir("bin"), file("bin/busybox", "BUSYBOX"), symlink("bin/ls", "/bin/busybox"));
      FgRoot.extractTar(root, tar);
      assertTrue(new File(root, "bin/busybox").isFile());
      assertTrue(java.nio.file.Files.isSymbolicLink(new File(root, "bin/ls").toPath()));
      assertEquals("/bin/busybox",
        java.nio.file.Files.readSymbolicLink(new File(root, "bin/ls").toPath()).toString());
    });

    it("rejects a parent-directory traversal entry", () -> {
      var root = freshRoot();
      var tar = new File(FgTest.WORK, "escape.tar");
      writeTar(tar, file("../escaped.txt", "nope"));
      try {
        FgRoot.extractTar(root, tar);
        fail("expected extraction to fail");
      } catch (IllegalStateException expected) {
        assertFalse("must not write outside the root", new File(root.getParentFile(), "escaped.txt").exists());
      }
    });

    it("rejects an absolute entry name", () -> {
      var root = freshRoot();
      var tar = new File(FgTest.WORK, "abs.tar");
      writeTar(tar, file("/tmp/ff-absolute.txt", "nope"));
      try {
        FgRoot.extractTar(root, tar);
        fail("expected extraction to fail");
      } catch (IllegalStateException expected) {
        assertFalse(new File("/tmp/ff-absolute.txt").exists());
      }
    });

    it("does not write through a symlinked directory", () -> {
      var root = freshRoot();
      var tar = new File(FgTest.WORK, "symdir.tar");
      // link -> /etc ; then link/ff-evil.txt must stay inside root (at root/etc/...)
      writeTar(tar, symlink("link", "/etc"), file("link/ff-evil.txt", "nope"));
      try {
        FgRoot.extractTar(root, tar);
      } catch (IllegalStateException ignored) {
      }
      assertFalse("must not write to the host /etc", new File("/etc/ff-evil.txt").exists());
    });

    it("rejects a hardlink to a host file", () -> {
      var root = freshRoot();
      var tar = new File(FgTest.WORK, "hardlink.tar");
      writeTar(tar, hardlink("steal", "/etc/hosts"));
      try {
        FgRoot.extractTar(root, tar);
        fail("expected extraction to fail");
      } catch (IllegalStateException expected) {
        assertFalse(new File(root, "steal").exists());
      }
    });

    it("adds owner-write to read-only files and owner-rwx to read-only dirs", () -> {
      var root = freshRoot();
      var tar = new File(FgTest.WORK, "normalize.tar");
      writeTar(tar,
        dir("etc", 0555),
        file("etc/resolv.conf", "", 0444),
        file("bin/tool", "x", 0555));
      FgRoot.extractTar(root, tar);

      var resolv = Files.getPosixFilePermissions(new File(root, "etc/resolv.conf").toPath());
      assertTrue("read-only file must become owner-writable", resolv.contains(PosixFilePermission.OWNER_WRITE));
      assertFalse("group/other bits must be untouched", resolv.contains(PosixFilePermission.GROUP_WRITE));

      var etc = Files.getPosixFilePermissions(new File(root, "etc").toPath());
      assertTrue("read-only dir must become owner-rwx", etc.contains(PosixFilePermission.OWNER_WRITE));
      assertTrue("read-only dir must become owner-rwx", etc.contains(PosixFilePermission.OWNER_EXECUTE));

      var tool = Files.getPosixFilePermissions(new File(root, "bin/tool").toPath());
      assertTrue("read-only file must become owner-writable", tool.contains(PosixFilePermission.OWNER_WRITE));
    });
  }
}
