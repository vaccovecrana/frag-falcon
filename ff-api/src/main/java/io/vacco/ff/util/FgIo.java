package io.vacco.ff.util;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.function.Consumer;

import static java.lang.String.format;
import static java.nio.file.Files.setPosixFilePermissions;

public class FgIo {

  public static void mkDirs(File f) {
    if (!f.exists() && !f.mkdirs()) {
      throw new IllegalStateException(format("Unable to create directories: [%s]", f));
    }
  }

  public static void delete(File f, Consumer<Exception> onError) {
    if (!f.exists()) {
      return;
    }
    try {
      Files.walkFileTree(f.toPath(), new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          if (exc != null) {
            throw exc;
          }
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

  public static void addPermissions(Path path, PosixFilePermission... perms) {
    try {
      var permissions = Files.getPosixFilePermissions(path);
      permissions.addAll(java.util.Arrays.asList(perms));
      setPosixFilePermissions(path, permissions);
    } catch (IOException e) {
      throw new IllegalStateException(format("Unable to set path permissions: [%s]", path), e);
    }
  }

  public static String readFile(File in) {
    try {
      return Files.readString(in.toPath(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(format("Unable to read file [%s]", in), e);
    }
  }

  public static void truncateFile(File in) {
    try {
      if (in.exists()) {
        Files.newOutputStream(in.toPath()).close();
      }
    } catch (IOException e) {
      throw new IllegalStateException(format("Unable to truncate file [%s]", in), e);
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
