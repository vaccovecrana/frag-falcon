package io.vacco.ff.oci;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Consumer;

import static java.lang.String.format;

/**
 * Small file/URI helpers used by the OCI extraction pipeline.
 */
public class FgOciIo {

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

  public static URL url(String url) {
    try {
      return new URI(url).toURL();
    } catch (URISyntaxException | MalformedURLException e) {
      throw new IllegalStateException(format("Invalid URL: [%s]", url), e);
    }
  }
}
