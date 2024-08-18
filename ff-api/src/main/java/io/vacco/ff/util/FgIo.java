package io.vacco.ff.util;

import com.google.gson.Gson;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static java.nio.file.Files.*;
import static java.lang.Integer.parseInt;

public class FgIo {

  private static final Pattern numeric = Pattern.compile("\\d+");

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

  public static void delete(File f, Consumer<Exception> onError) {
    try {
      Files.walkFileTree(f.toPath(), new SimpleFileVisitor<>() {
        @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }
        @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          if (exc != null) throw exc;
          Files.delete(dir);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      onError.accept(e);
    }
  }

  public static <T> T fromJson(File in, Class<T> clazz, Gson g) {
    try (var fr = new FileReader(in)) {
      return g.fromJson(fr, clazz);
    } catch (IOException e) {
      throw new IllegalStateException(format("Unable to read JSON data from [%s]", in), e);
    }
  }

  public static void toJson(Object obj, File out, Gson g) {
    try (var fw = new FileWriter(out)) {
      g.toJson(obj, fw);
    } catch (IOException e) {
      throw new IllegalStateException(format("Unable to write JSON data to [%s]", out), e);
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

  private static boolean contains(Path[] paths, String vmId) {
    var environPath = paths[0];
    if (Files.exists(environPath)) {
      try {
        var environment = readString(environPath);
        if (environment.contains("FF_VMID=" + vmId + '\0')) {
          return true;
        }
      } catch (IOException ignore) {
        return false;
      }
    }
    return false;
  }

  public static int pidOf(String procDir, String vmId) {
    try (var paths = list(Path.of(procDir))) {
      return paths
        .filter(path -> isDirectory(path) && numeric.matcher(path.getFileName().toString()).matches())
        .filter(path -> parseInt(path.getFileName().toString()) != 1) // Skip init process
        .map(path -> new Path[] { path.resolve("environ"), path })
        .filter(pathsArray -> contains(pathsArray, vmId))
        .findFirst()
        .map(pathArray -> Integer.parseInt(pathArray[1].getFileName().toString()))
        .orElse(-1);
    } catch (IOException e) {
      return -1;
    }
  }

}
