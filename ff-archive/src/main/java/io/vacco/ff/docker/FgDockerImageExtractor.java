package io.vacco.ff.docker;

import io.vacco.ff.archive.FgCpio;
import io.vacco.ff.archive.FgTarEntry;
import io.vacco.ff.archive.FgTarIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
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

    // Pass 1: Extract all entries with metadata only (no content loaded)
    var allEntries = new TreeMap<String, FgTarEntry>();
    
    for (int i = 0; i < layers.size(); i++) {
      var blobFile = metadata.layerBlobFiles.get(i);
      var extractedFile = new File(unzippedDir, blobFile.getName());

      log.info("Expanding layer [{}]", blobFile.getAbsolutePath());
      expandGzip(blobFile, extractedFile);
      
      var entries = FgTarIo.extractToMemory(extractedFile, onError);
      
      // Add/update entries in map (later layers override earlier ones)
      // Paths are already normalized in extractToMemory()
      for (var entry : entries) {
        allEntries.put(entry.name, entry);
      }
    }

    // Process whiteout entries - remove entries that should be deleted
    // Paths are already normalized, so we can work with them directly
    var entriesToRemove = new HashSet<String>();
    for (var entry : allEntries.values()) {
      var entryName = entry.name;
      var lastSlash = entryName.lastIndexOf('/');
      var fileName = lastSlash >= 0 ? entryName.substring(lastSlash + 1) : entryName;
      
      if (fileName.startsWith(".wh.")) {
        // Mark the original file for deletion
        var originalName = fileName.substring(4);
        var parentPath = lastSlash >= 0 ? entryName.substring(0, lastSlash + 1) : "";
        var originalPath = parentPath + originalName;
        entriesToRemove.add(originalPath);
        // Also remove the whiteout entry itself
        entriesToRemove.add(entryName);
      } else if (fileName.equals(".wh..wh..opq")) {
        // Opaque directory - remove all entries in this directory
        var dirPath = lastSlash >= 0 ? entryName.substring(0, lastSlash + 1) : "";
        
        for (var key : new ArrayList<>(allEntries.keySet())) {
          if (key.startsWith(dirPath) && !key.equals(dirPath)) {
            entriesToRemove.add(key);
          }
        }
        // Also remove the opaque marker itself
        entriesToRemove.add(entryName);
      }
    }
    
    // Remove whiteout-marked entries
    for (var path : entriesToRemove) {
      allEntries.remove(path);
    }

    // Note: We no longer pre-load all content into memory to avoid OOM errors.
    // Content will be loaded on-demand during CPIO archive creation.
    // This allows streaming large files without holding everything in memory.

    // Build CPIO archive from in-memory entries (content loaded on-demand)
    FgCpio.archiveFromMemory(allEntries, outputCpioFile, (path, err) -> {
      log.error("Unable to archive path [{}]: {}", path, err.getMessage());
    });

    // Cleanup temporary files
    deleteRecursively(blobDir, e -> log.warn("Unable to delete blob directory [{}]: {}", blobDir, e.getMessage()));
    deleteRecursively(unzippedDir, e -> log.warn("Unable to delete unzipped directory [{}]: {}", unzippedDir, e.getMessage()));
  }

  private static void deleteRecursively(File file, java.util.function.Consumer<Exception> onError) {
    try {
      if (file.exists()) {
        if (file.isDirectory()) {
          var children = file.listFiles();
          if (children != null) {
            for (var child : children) {
              deleteRecursively(child, onError);
            }
          }
        }
        if (!file.delete()) {
          onError.accept(new IOException("Failed to delete: " + file));
        }
      }
    } catch (Exception e) {
      onError.accept(e);
    }
  }
}

