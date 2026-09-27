package io.vacco.ff.oci;

import com.google.gson.*;
import io.vacco.ff.net.FgRoot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

import static io.vacco.ff.oci.FgOciIo.*;
import static java.lang.String.format;
import static java.lang.String.join;

/**
 * Minimal OCI/Docker registry client: resolves a manifest, downloads layer
 * blobs into a persistent {@link FgOciStore}, and extracts them into a host
 * rootfs directory suitable for sharing as a libkrun virtiofs device.
 */
public class FgDockerIo {

  public static final String
    dockerTld = "docker.io", dockerAuthTld = "auth.docker.io", dockerService = "registry.docker.io",
    githubTld = "ghcr.io",
    mimeTypeOciManifestV1 = "application/vnd.oci.image.manifest.v1+json",
    mimeTypeOciImageV1 = "application/vnd.oci.image.index.v1+json",
    mimeTypeOciConfigV1 = "application/vnd.oci.image.config.v1+json",
    mimeTypeDockerManifestV2 = "application/vnd.docker.distribution.manifest.v2+json";

  public static final String dockerOs = "linux", dockerArch = "amd64";

  private static final Logger log = LoggerFactory.getLogger(FgDockerIo.class);
  private static final HttpClient client = HttpClient.newHttpClient();

  public static void expand(File in, File out) {
    try {
      log.info("Expanding layer [{}]", in.getAbsolutePath());
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

  private static JsonObject getJsonResponse(String urlString, String authToken, String... acceptHeaders) {
    try {
      var requestBuilder = HttpRequest.newBuilder()
        .uri(URI.create(urlString))
        .header("Accept", join(",", acceptHeaders))
        .GET();
      if (authToken != null) {
        requestBuilder.header("Authorization", "Bearer " + authToken);
      }
      var response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
      int statusCode = response.statusCode();
      if (statusCode == 401) {
        throw new IOException("Unauthorized request. Check token.");
      } else if (statusCode == 302 || statusCode == 301 || statusCode == 307) {
        var newUrl = response.headers().firstValue("Location").orElseThrow(() ->
          new IOException("Redirected but no Location header found."));
        return getJsonResponse(newUrl, authToken, acceptHeaders);
      }
      var raw = response.body();
      return JsonParser.parseString(raw).getAsJsonObject();
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(format("Unable to load JSON content: [%s]", urlString), e);
    }
  }

  private static String[] parseJsonArray(JsonArray jsonArray) {
    var array = new String[jsonArray.size()];
    for (int i = 0; i < jsonArray.size(); i++) {
      array[i] = jsonArray.get(i).getAsString();
    }
    return array;
  }

  private static void downloadBlob(String registryUrl, String repository, String blobSum,
                                   File outputFile, String authToken, FgOciProgress progress,
                                   long[] bytesDone, long totalBytes) {
    try {
      var blobUrl = registryUrl + repository + "/blobs/" + blobSum;
      log.info("Downloading layer: {}", blobUrl);
      var url = url(blobUrl);
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
        downloadBlob(newUrl, repository, blobSum, outputFile, authToken, progress, bytesDone, totalBytes);
        return;
      }
      try (var in = new BufferedInputStream(connection.getInputStream());
           var out = new FileOutputStream(outputFile)) {
        var buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
          out.write(buffer, 0, bytesRead);
          bytesDone[0] += bytesRead;
          progress.onBytes(bytesDone[0], totalBytes);
        }
      }
    } catch (IOException e) {
      var msg = format("Unable to download blob [%s, %s, %s, %s]", registryUrl, repository, blobSum, outputFile);
      throw new IllegalStateException(msg, e);
    }
  }

  private static File cachedBlob(FgOciStore store, String registryUrl, String repository,
                                 String blobSum, String authToken, FgOciProgress progress,
                                 long[] bytesDone, long totalBytes) {
    var blobFile = store.blobFile(blobSum);
    if (!blobFile.exists()) {
      downloadBlob(registryUrl, repository, blobSum, blobFile, authToken, progress, bytesDone, totalBytes);
    } else {
      log.info("Cache hit for layer: {}", blobSum);
    }
    return blobFile;
  }

  private static String requestAuthToken(String registryTld, String... args) {
    try {
      var baseUrl = format("https://%s/token?%s", registryTld, join("&", args));
      var url = url(baseUrl);
      var connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      try (var is = connection.getInputStream()) {
        var response = new String(is.readAllBytes());
        var jsonResponse = JsonParser.parseString(response).getAsJsonObject();
        return jsonResponse.get("token").getAsString();
      }
    } catch (IOException e) {
      throw new IllegalStateException(format("Unable to request auth token: [%s, %s]", registryTld, Arrays.toString(args)), e);
    }
  }

  private static JsonObject getConfigJson(String registryUrl, String repository,
                                          String configDigest, String authToken) {
    var configUrl = registryUrl + repository + "/blobs/" + configDigest;
    log.info("Retrieving config: {}", configUrl);
    return getJsonResponse(configUrl, authToken, mimeTypeOciConfigV1);
  }

  private static FgImage processManifest(JsonObject manifest, String registryUrl, String repoName,
                                         String authToken, FgOciStore store, File rootfsDir,
                                         FgOciProgress progress) {
    var configDigest = manifest.getAsJsonObject("config").get("digest").getAsString();
    var configJson = getConfigJson(registryUrl, repoName, configDigest, authToken);

    String[] entryPoint = null;
    var entryPointJson = configJson.getAsJsonObject("config").get("Entrypoint");
    if (entryPointJson != null && !(entryPointJson instanceof JsonNull)) {
      entryPoint = parseJsonArray(configJson.getAsJsonObject("config").getAsJsonArray("Entrypoint"));
    }

    String workingDir = null;
    var workingDirJson = configJson.getAsJsonObject("config").get("WorkingDir");
    if (workingDirJson != null && !(workingDirJson instanceof JsonNull)) {
      workingDir = workingDirJson.getAsString();
    }

    String[] cmd = null;
    var cmdJson = configJson.getAsJsonObject("config").get("Cmd");
    if (cmdJson != null && !(cmdJson instanceof JsonNull)) {
      cmd = parseJsonArray(configJson.getAsJsonObject("config").getAsJsonArray("Cmd"));
    }

    var env = new ArrayList<FgEnvVar>();
    if (configJson.getAsJsonObject("config").has("Env")) {
      var envArr = parseJsonArray(configJson.getAsJsonObject("config").getAsJsonArray("Env"));
      for (var e : envArr) {
        var entry = e.split("=", 2);
        env.add(FgEnvVar.of(entry[0], entry.length == 2 ? entry[1] : null));
      }
    }

    var layers = manifest.has("layers")
      ? manifest.getAsJsonArray("layers")
      : manifest.getAsJsonArray("fsLayers");

    var exposedPorts = new ArrayList<String>();
    var exposedJson = configJson.getAsJsonObject("config").get("ExposedPorts");
    if (exposedJson != null && exposedJson.isJsonObject()) {
      exposedPorts.addAll(exposedJson.getAsJsonObject().keySet());
    }

    long totalBytes = 0;
    for (var layer : layers) {
      var layerObj = layer.getAsJsonObject();
      if (layerObj.has("size")) {
        totalBytes += layerObj.get("size").getAsLong();
      }
    }
    long[] bytesDone = {0};
    progress.onLayers(0, layers.size());
    progress.onBytes(0, totalBytes);

    var unzippedDir = store.tmpDir("unzipped");
    delete(rootfsDir, e -> log.warn("Unable to clear rootfs directory [{}]", rootfsDir, e));
    mkDirs(rootfsDir);

    for (int li = 0; li < layers.size(); li++) {
      var layerObj = layers.get(li).getAsJsonObject();
      var blobSum = layerObj.has("digest")
        ? layerObj.get("digest").getAsString()
        : layerObj.get("blobSum").getAsString();

      var blobFile = cachedBlob(store, registryUrl, repoName, blobSum, authToken, progress, bytesDone, totalBytes);
      var extractedFile = new File(unzippedDir, blobFile.getName());
      expand(blobFile, extractedFile);
      FgRoot.extractTar(rootfsDir, extractedFile);
      progress.onLayers(li + 1, layers.size());
    }
    delete(unzippedDir, e -> log.warn("Unable to delete unzipped directory [{}]", unzippedDir, e));

    return FgImage.of(rootfsDir.getAbsolutePath(), entryPoint, cmd, env, workingDir)
      .withExposedPorts(exposedPorts);
  }

  /**
   * Pulls and extracts an OCI image into {@code rootfsDir}.
   *
   * @param dockerImageUri image reference, e.g. {@code alpine:latest} or {@code ghcr.io/org/img:tag}
   * @param rootfsDir      destination directory for the extracted root filesystem
   * @param store          persistent blob cache
   */
  public static FgImage extract(String dockerImageUri, File rootfsDir, FgOciStore store,
                                String architecture, String os,
                                FgOciProgress progress) {
    if (!dockerImageUri.contains("/")) {
      dockerImageUri = dockerTld + "/library/" + dockerImageUri;
    }
    var uriParts = dockerImageUri.split("/", 2);
    var registryUrl = "https://" + (uriParts[0].equals(dockerTld) ? "registry-1.docker.io" : uriParts[0]) + "/v2/";

    var repository = uriParts[1];
    if (uriParts[0].equals(dockerTld) && !uriParts[1].contains("/")) {
      repository = "library/" + uriParts[1];
    }

    var repoParts = repository.split(":");
    var repoName = repoParts[0];
    var imageTag = repoParts.length > 1 ? repoParts[1] : "latest";
    var manifestUrl = registryUrl + repoName + "/manifests/" + imageTag;

    String authToken = null;
    if (registryUrl.contains(dockerTld)) {
      authToken = requestAuthToken(
        dockerAuthTld,
        format("service=%s", dockerService),
        format("scope=repository:%s:pull", repoName)
      );
    } else if (registryUrl.contains(githubTld)) {
      authToken = requestAuthToken(
        githubTld,
        format("scope=repository:%s:pull", repoName)
      );
    }

    log.info("Retrieving manifest: {}", manifestUrl);

    var manifest = getJsonResponse(manifestUrl, authToken, mimeTypeDockerManifestV2, mimeTypeOciManifestV1, mimeTypeOciImageV1);

    if (manifest.has("manifests")) {
      var oDigest = manifest.getAsJsonArray("manifests")
        .asList().stream()
        .map(JsonElement::getAsJsonObject)
        .filter(obj -> {
          var platform = obj.getAsJsonObject("platform");
          var arch = platform.getAsJsonPrimitive("architecture").getAsString();
          var osp = platform.getAsJsonPrimitive("os").getAsString();
          return arch.equals(architecture) && osp.equals(os);
        })
        .map(obj -> obj.getAsJsonPrimitive("digest").getAsString())
        .findFirst();
      if (oDigest.isPresent()) {
        var digestUrl = format("%s%s/manifests/%s", registryUrl, repoName, oDigest.get());
        var manifest0 = getJsonResponse(digestUrl, authToken, mimeTypeOciManifestV1);
        return processManifest(manifest0, registryUrl, repoName, authToken, store, rootfsDir, progress)
          .withSource(dockerImageUri);
      }
      throw new IllegalStateException(format(
        "Unable to find OCI V1 manifest for %s, %s, %s",
        dockerImageUri, architecture, os
      ));
    }
    return processManifest(manifest, registryUrl, repoName, authToken, store, rootfsDir, progress)
      .withSource(dockerImageUri);
  }

  public static FgImage extract(String dockerImageUri, File rootfsDir, FgOciStore store) {
    return extract(dockerImageUri, rootfsDir, store, dockerArch, dockerOs, FgOciProgress.NOOP);
  }

  public static FgImage extract(String dockerImageUri, File rootfsDir, FgOciStore store,
                                FgOciProgress progress) {
    return extract(dockerImageUri, rootfsDir, store, dockerArch, dockerOs, progress);
  }
}
