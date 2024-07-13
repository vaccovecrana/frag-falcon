package io.vacco.ff;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.file.FileAlreadyExistsException;
import java.util.zip.GZIPInputStream;

public class FgDockerIo {

  private static final Logger log = LoggerFactory.getLogger(FgDockerIo.class);

  public static void expand(File in, File out) throws IOException {
    log.info("Extracting layer [{}]", in.getAbsolutePath());
    try (var fis = new FileInputStream(in);
         var gzis = new GZIPInputStream(new BufferedInputStream(fis));
         var fos = new FileOutputStream(out);
         var bos = new BufferedOutputStream(fos)) {
      var buffer = new byte[1024];
      int len;
      while ((len = gzis.read(buffer)) > 0) {
        bos.write(buffer, 0, len);
      }
    }
  }

  private static JsonObject getJsonResponse(String urlString) throws IOException {
    var url = new URL(urlString);
    var connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");
    connection.setRequestProperty("Accept", "application/vnd.docker.distribution.manifest.v2+json");
    try (var is = connection.getInputStream()) {
      var responseBytes = is.readAllBytes();
      var response = new String(responseBytes);
      return JsonParser.parseString(response).getAsJsonObject();
    }
  }

  private static void downloadBlob(String registryUrl, String repository, String blobSum, File outputFile) throws IOException {
    var blobUrl = registryUrl + repository + "/blobs/" + blobSum;
    log.info("Downloading layer: {}", blobUrl);
    var url = new URL(blobUrl);
    var connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");
    try (var in = new BufferedInputStream(connection.getInputStream());
         var out = new FileOutputStream(outputFile)) {
      var buffer = new byte[1024];
      int bytesRead;
      while ((bytesRead = in.read(buffer, 0, 1024)) != -1) {
        out.write(buffer, 0, bytesRead);
      }
    }
  }

  public static void extract(String dockerImageUri, File outDir) throws IOException {
    var uriParts = dockerImageUri.split("/", 2);
    var registryUrl = "https://" + uriParts[0] + "/v2/";
    var repository = uriParts[1];
    var repoParts = repository.split(":");
    var repoName = repoParts[0];
    var imageTag = repoParts.length > 1 ? repoParts[1] : "latest";
    var manifestUrl = registryUrl + repoName + "/manifests/" + imageTag;

    log.info("Retrieving manifest: {}", manifestUrl);

    var manifest = getJsonResponse(manifestUrl);
    var layers = manifest.has("layers")
      ? manifest.getAsJsonArray("layers")
      : manifest.getAsJsonArray("fsLayers");

    for (var layer : layers) {
      var layerObj = layer.getAsJsonObject();
      var blobSum = layerObj.has("digest")
        ? layerObj.get("digest").getAsString()
        : layerObj.get("blobSum").getAsString();
      var downloadDir = new File(outDir, "blobs");
      var blobFile = new File(downloadDir, blobSum);

      downloadDir.mkdirs();
      if (!blobFile.exists()) {
        downloadBlob(registryUrl, repoName, blobSum, blobFile);
      }

      var extractedDir = new File(outDir, "unzipped");
      var extractedFile = new File(extractedDir, blobFile.getName());

      extractedDir.mkdirs();
      expand(blobFile, extractedFile);

      var untarDir = new File(outDir, "extract");
      FgTarIo.extract(extractedFile, untarDir, ((tarEntry, ex) -> {
        if (ex instanceof FileAlreadyExistsException) {
          log.info("File already exists: {}", tarEntry.name);
        } else {
          log.error("Unable to extract entry {}", tarEntry, ex);
        }
      }));
    }
  }

}
