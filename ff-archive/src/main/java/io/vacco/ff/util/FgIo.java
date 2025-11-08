package io.vacco.ff.util;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

import static java.lang.String.format;
import static java.nio.file.Files.*;

public class FgIo {

  public static void close(Closeable c) {
    try {
      if (c != null) {
        c.close();
      }
    } catch (IOException ignored) {}
  }

  public static void addPermissions(Path path, PosixFilePermission... perms) {
    try {
      var permissions = Files.getPosixFilePermissions(path);
      permissions.addAll(Arrays.asList(perms));
      setPosixFilePermissions(path, permissions);
    } catch (IOException e) {
      var msg = format("Unable to set path permissions: [%s] - %s", path, Arrays.toString(perms));
      throw new IllegalStateException(msg, e);
    }
  }

  public static void exists(File f) {
    if (!f.exists()) {
      var msg = format("Invalid path/file: [%s]", f);
      throw new IllegalStateException(msg);
    }
  }

  public static void exists(String path) {
    exists(new File(path));
  }

  public static void mkDirs(File f) {
    if (!f.exists()) {
      if (!f.mkdirs()) {
        var msg = format("Unable to create directories: [%s]", f);
        throw new IllegalStateException(msg);
      }
    }
  }

  public static URI uri(String uri) {
    try {
      return new URI(uri);
    } catch (URISyntaxException e) {
      throw new IllegalStateException(format("Invalid URI: [%s]", uri), e);
    }
  }

  public static URI uri(URL url) {
    try {
      return url.toURI();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(format("Invalid URI: [%s]", url), e);
    }
  }

  public static URL url(String url) {
    try {
      return uri(url).toURL();
    } catch (MalformedURLException e) {
      throw new IllegalStateException(format("Invalid URL: [%s]", url), e);
    }
  }

  public static void copyURIToFile(URI sourceURI, Path targetFile) {
    try (var inputStream = sourceURI.toURL().openStream()) {
      copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new IllegalStateException(
        format("Unable to copy URI [%s -> %s]", sourceURI, targetFile), e
      );
    }
  }

  public static String readFile(File in) {
    try {
      return Files.readString(in.toPath(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(
        format("Unable to read contents of file [%s]", in), e
      );
    }
  }

  @SuppressWarnings("try")
  public static void truncateFile(File in) {
    try (FileOutputStream fos = new FileOutputStream(in)) {
      // Opening the file with FileOutputStream in write mode without appending
      // truncates the file to zero bytes.
    } catch (IOException e) {
      throw new IllegalStateException(
        format("Unable to truncate file [%s]", in), e
      );
    }
  }

  public static void expandGzip(File in, File out) {
    try {
      try (var fis = new FileInputStream(in);
           var zis = new GZIPInputStream(new BufferedInputStream(fis));
           var fos = new FileOutputStream(out);
           var bos = new BufferedOutputStream(fos)) {
        var buffer = new byte[1024];
        int len;
        while ((len = zis.read(buffer)) > 0) {
          bos.write(buffer, 0, len);
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException(format("Unable to expand [%s -> %s]", in, out), e);
    }
  }

  public static String hostName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return "localhost";
    }
  }

}
