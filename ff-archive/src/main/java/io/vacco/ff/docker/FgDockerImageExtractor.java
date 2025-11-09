package io.vacco.ff.docker;

import io.vacco.ff.archive.FgTarEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.zip.GZIPInputStream;

import static io.vacco.ff.util.FgIo.*;
import static java.lang.String.format;

public class FgDockerImageExtractor {

  private static final Logger log = LoggerFactory.getLogger(FgDockerImageExtractor.class);
  private static final ExecutorService downloadExecutor = Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors() * 2
  );

  private static void expandGzip(File in, File out) {
    try {
      try (var fis = new FileInputStream(in);
           var zis = new GZIPInputStream(new BufferedInputStream(fis));
           var fos = new FileOutputStream(out);
           var bos = new BufferedOutputStream(fos)) {
        var bytes = zis.transferTo(bos);
        log.info("Expanded layer [{}] - {} bytes", out.getAbsolutePath(), bytes);
      }
    } catch (IOException e) {
      throw new IllegalStateException(format("Unable to expand [%s -> %s]", in, out), e);
    }
  }

  private static void downloadBlob(String registryUrl, String repository, String blobSum,
                                   File outputFile, String authToken) {
    try {
      var blobUrl = registryUrl + repository + "/blobs/" + blobSum;
      log.info("Downloading layer: {}", blobUrl);
      FgDockerImageFactory.downloadBlob(blobUrl, outputFile, authToken);
    } catch (IOException e) {
      var msg = format("Unable to download blob [%s, %s, %s, %s]", registryUrl, repository, blobSum, outputFile);
      throw new IllegalStateException(msg, e);
    }
  }

  public static void extractToCpio(FgDockerImageMetadata metadata, File outputCpioFile,
                                   BiConsumer<FgTarEntry, Exception> onError) {
    // Create temporary directories for blob processing
    var blobDir = new File(outputCpioFile.getParentFile(), "blobs");
    var unzippedDir = new File(outputCpioFile.getParentFile(), "unzipped");
    mkDirs(blobDir);
    mkDirs(unzippedDir);

    // Download all blobs in parallel
    var downloadFutures = new ArrayList<CompletableFuture<FgLayer>>();
    var layers = !metadata.manifest.layers.isEmpty() ? metadata.manifest.layers : metadata.manifest.fsLayers;
    
    for (int i = 0; i < layers.size(); i++) {
      var layer = layers.get(i);
      var blobSum = layer.digest != null ? layer.digest : layer.blobSum;
      var blobFile = metadata.layerBlobFiles.get(i);
      var future = CompletableFuture.supplyAsync(() -> {
        if (!blobFile.exists()) {
          downloadBlob(metadata.registryUrl, metadata.repository, blobSum, blobFile, metadata.authToken);
        }
        return layer;
      }, downloadExecutor);
      downloadFutures.add(future);
    }

    try {
      CompletableFuture.allOf(downloadFutures.toArray(new CompletableFuture<?>[0])).join();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to download one or more blobs", e);
    }

    // TODO: extract all entries into Map<FgTarEntry, File>, where each entry points to a file with raw bytes
    //   named after it's MD5 sum, so we don't need to deal with filesystem permissions at all.

    // TODO: next, process whiteout entries - remove entries that should be deleted

    // TODO: re-enable this later. Cleanup temporary files
    // deleteRecursively(blobDir, e -> log.warn("Unable to delete blob directory [{}]: {}", blobDir, e.getMessage()));
    // deleteRecursively(unzippedDir, e -> log.warn("Unable to delete unzipped directory [{}]: {}", unzippedDir, e.getMessage()));
  }

}

