package io.vacco.ff;

import com.google.gson.*;
import org.slf4j.*;
import java.io.*;
import java.net.*;
import java.util.function.BiConsumer;
import java.util.zip.GZIPInputStream;

public class FgDockerIo {

  public static final String dockerTld = "docker.io";

  private static final Logger log = LoggerFactory.getLogger(FgDockerIo.class);

  public static void expand(File in, File out) throws IOException {
    log.info("Expanding layer [{}]", in.getAbsolutePath());
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

  private static JsonObject getJsonResponse(String urlString, String authToken) throws IOException {
    var url = new URL(urlString);
    var connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");
    connection.setRequestProperty("Accept", "application/vnd.docker.distribution.manifest.v2+json");
    if (authToken != null) {
      connection.setRequestProperty("Authorization", "Bearer " + authToken);
    }
    int responseCode = connection.getResponseCode();
    if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
      throw new IOException("Unauthorized request. Check token.");
    } else if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
      var newUrl = connection.getHeaderField("Location");
      return getJsonResponse(newUrl, authToken);
    }
    try (var is = connection.getInputStream()) {
      var responseBytes = is.readAllBytes();
      var response = new String(responseBytes);
      return JsonParser.parseString(response).getAsJsonObject();
    }
  }

  private static void downloadBlob(String registryUrl, String repository, String blobSum,
                                   File outputFile, String authToken) throws IOException {
    var blobUrl = registryUrl + repository + "/blobs/" + blobSum;
    log.info("Downloading layer: {}", blobUrl);
    var url = new URL(blobUrl);
    var connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");
    if (authToken != null) {
      connection.setRequestProperty("Authorization", "Bearer " + authToken);
    }
    int responseCode = connection.getResponseCode();
    if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
      throw new IOException("Unauthorized request. Check token.");
    } else if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
      var newUrl = connection.getHeaderField("Location");
      downloadBlob(newUrl, repository, blobSum, outputFile, authToken);
      return;
    }
    try (var in = new BufferedInputStream(connection.getInputStream());
         var out = new FileOutputStream(outputFile)) {
      var buffer = new byte[1024];
      int bytesRead;
      while ((bytesRead = in.read(buffer, 0, 1024)) != -1) {
        out.write(buffer, 0, bytesRead);
      }
    }
  }

  private static String requestAuthToken(String repository) throws IOException {
    var authUrl = "https://auth.docker.io/token?service=registry.docker.io&scope=repository:" + repository + ":pull";
    var url = new URL(authUrl);
    var connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");
    try (var is = connection.getInputStream()) {
      var responseBytes = is.readAllBytes();
      var response = new String(responseBytes);
      var jsonResponse = JsonParser.parseString(response).getAsJsonObject();
      return jsonResponse.get("token").getAsString();
    }
  }

  private static JsonObject getConfigJson(String registryUrl, String repository, String configDigest, String authToken) throws IOException {
    var configUrl = registryUrl + repository + "/blobs/" + configDigest;
    log.info("Retrieving config: {}", configUrl);
    return getJsonResponse(configUrl, authToken);
  }

  public static FgDockerImage extract(String dockerImageUri, File outDir, BiConsumer<FgTarEntry, Exception> onError) throws IOException {
    var uriParts = dockerImageUri.split("/", 2);
    var registryUrl = "https://" + (uriParts[0].equals(dockerTld) ? "registry-1.docker.io" : uriParts[0]) + "/v2/";
    var repository = uriParts[1].contains("/") ? uriParts[1] : "library/" + uriParts[1];
    var repoParts = repository.split(":");
    var repoName = repoParts[0];
    var imageTag = repoParts.length > 1 ? repoParts[1] : "latest";
    var manifestUrl = registryUrl + repoName + "/manifests/" + imageTag;

    String authToken = null;
    if (registryUrl.contains(dockerTld)) {
      authToken = requestAuthToken(repoName);
    }

    log.info("Retrieving manifest: {}", manifestUrl);

    var manifest = getJsonResponse(manifestUrl, authToken);
    var configDigest = manifest.getAsJsonObject("config").get("digest").getAsString();
    var configJson = getConfigJson(registryUrl, repoName, configDigest, authToken);

    String[] entryPoint = null;
    if (configJson.getAsJsonObject("config").has("Entrypoint")) {
      entryPoint = parseJsonArray(configJson.getAsJsonObject("config").getAsJsonArray("Entrypoint"));
    }

    String[] cmd = null;
    if (configJson.getAsJsonObject("config").has("Cmd")) {
      cmd = parseJsonArray(configJson.getAsJsonObject("config").getAsJsonArray("Cmd"));
    }

    var layers = manifest.has("layers")
      ? manifest.getAsJsonArray("layers")
      : manifest.getAsJsonArray("fsLayers");

    var downloadDir = new File(outDir, "blobs");
    var extractedDir = new File(outDir, "unzipped");
    var untarDir = new File(outDir, "extract");

    for (var layer : layers) {
      var layerObj = layer.getAsJsonObject();
      var blobSum = layerObj.has("digest")
        ? layerObj.get("digest").getAsString()
        : layerObj.get("blobSum").getAsString();
      var blobFile = new File(downloadDir, blobSum);

      downloadDir.mkdirs();
      if (!blobFile.exists()) {
        downloadBlob(registryUrl, repoName, blobSum, blobFile, authToken);
      }

      var extractedFile = new File(extractedDir, blobFile.getName());
      extractedDir.mkdirs();
      expand(blobFile, extractedFile);
      FgTarIo.extract(extractedFile, untarDir, onError);
    }

    return FgDockerImage.of(untarDir, entryPoint, cmd);
  }

  private static String[] parseJsonArray(JsonArray jsonArray) {
    var array = new String[jsonArray.size()];
    for (int i = 0; i < jsonArray.size(); i++) {
      array[i] = jsonArray.get(i).getAsString();
    }
    return array;
  }
}
