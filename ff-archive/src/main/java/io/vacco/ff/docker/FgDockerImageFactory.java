package io.vacco.ff.docker;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;

import static java.lang.String.*;

public class FgDockerImageFactory {

  public static final String
    dockerTld = "docker.io", dockerAuthTld = "auth.docker.io", dockerService = "registry.docker.io",
    githubTld = "ghcr.io",
    mimeTypeOciManifestV1 = "application/vnd.oci.image.manifest.v1+json",
    mimeTypeOciImageV1 = "application/vnd.oci.image.index.v1+json",
    mimeTypeOciConfigV1 = "application/vnd.oci.image.config.v1+json",
    mimeTypeDockerManifestV2 = "application/vnd.docker.distribution.manifest.v2+json";

  private static final Logger log = LoggerFactory.getLogger(FgDockerImageFactory.class);
  private static final HttpClient client = HttpClient.newBuilder()
    .followRedirects(HttpClient.Redirect.NEVER)
    .build();
  private static final Gson gson = new Gson();

  private static String getHost(String urlString) {
    try {
      var uri = URI.create(urlString);
      return uri.getHost() != null ? uri.getHost() : "";
    } catch (Exception e) {
      return "";
    }
  }

  private static String getJsonResponseStringInternal(String urlString, String authToken, String originalUrl, String... acceptHeaders) {
    try {
      var requestBuilder = HttpRequest.newBuilder()
        .uri(URI.create(urlString))
        .header("Accept", String.join(",", acceptHeaders))
        .GET();
      
      // Check if redirect is to a different domain (CDN) - don't send auth token for CDN URLs
      var originalHost = getHost(originalUrl);
      var currentHost = getHost(urlString);
      var isCdnRedirect = !originalHost.equals(currentHost);
      
      if (authToken != null && !isCdnRedirect) {
        requestBuilder.header("Authorization", "Bearer " + authToken);
      }
      
      var response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
      int statusCode = response.statusCode();
      
      if (statusCode >= 200 && statusCode < 300) {
        return response.body();
      } else if (statusCode == 401) {
        var body = response.body();
        log.error("Unauthorized request to [{}]. Response: {}", urlString, body);
        throw new IOException("Unauthorized request. Check token.");
      } else if (statusCode == 302 || statusCode == 301 || statusCode == 307 || statusCode == 308) {
        var newUrl = response.headers().firstValue("Location").orElseThrow(() ->
          new IOException("Redirected but no Location header found."));
        // Pass originalUrl to track if we're redirecting to a CDN
        return getJsonResponseStringInternal(newUrl, authToken, originalUrl, acceptHeaders);
      } else {
        var body = response.body();
        log.error("Unexpected status code {} from [{}]. Response: {}", statusCode, urlString, 
          body.length() > 500 ? body.substring(0, 500) + "..." : body);
        throw new IOException(format("Unexpected status code %d from %s: %s", statusCode, urlString, 
          body.length() > 200 ? body.substring(0, 200) : body));
      }
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(format("Unable to load JSON content: [%s]", urlString), e);
    }
  }

  private static String getJsonResponseString(String urlString, String authToken, String... acceptHeaders) {
    return getJsonResponseStringInternal(urlString, authToken, urlString, acceptHeaders);
  }

  private static String requestAuthToken(String registryTld, String ... args) {
    try {
      var baseUrl = format("https://%s/token?%s", registryTld, String.join("&", args));
      var url = URI.create(baseUrl).toURL();
      var connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      try (var is = connection.getInputStream()) {
        var responseBytes = is.readAllBytes();
        var response = new String(responseBytes);
        var authResponse = gson.fromJson(response, FgAuthTokenResponse.class);
        return authResponse.token;
      }
    } catch (IOException e) {
      throw new IllegalStateException(format("Unable to request auth token: [%s, %s]", registryTld, Arrays.toString(args)), e);
    }
  }

  private static FgImage getImageMetadata(String registryUrl, String repository,
                                         String configDigest, String authToken) {
    var configUrl = registryUrl + repository + "/blobs/" + configDigest;
    log.info("Retrieving config: {}", configUrl);
    var jsonString = getJsonResponseString(configUrl, authToken, mimeTypeOciConfigV1);
    try {
      return gson.fromJson(jsonString, FgImage.class);
    } catch (JsonSyntaxException e) {
      log.error("Failed to parse JSON from [{}]. Response was: {}", configUrl, 
        jsonString.length() > 500 ? jsonString.substring(0, 500) + "..." : jsonString);
      throw new IllegalStateException(format("Invalid JSON response from config endpoint [%s]", configUrl), e);
    }
  }

  public static FgDockerImageMetadata getImageMetadata(String dockerImageUri, String architecture, String os, File blobCacheDir) {
    var metadata = new FgDockerImageMetadata();
    metadata.source = dockerImageUri;

    // Parse Docker image URI
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

    metadata.registryUrl = registryUrl;
    metadata.repository = repoName;

    // Request auth token
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
    metadata.authToken = authToken;

    log.info("Retrieving manifest: {}", manifestUrl);
    var manifestJson = getJsonResponseString(manifestUrl, authToken, mimeTypeDockerManifestV2, mimeTypeOciManifestV1, mimeTypeOciImageV1);
    
    // Check if it's an OCI image index (multi-arch manifest)
    var jsonObj = JsonParser.parseString(manifestJson).getAsJsonObject();
    if (jsonObj.has("manifests")) {
      var index = gson.fromJson(manifestJson, FgOciImageIndex.class);
      var oDigest = index.manifests.stream()
        .filter(desc -> desc.platform != null 
          && architecture.equals(desc.platform.architecture) 
          && os.equals(desc.platform.os))
        .map(desc -> desc.digest)
        .findFirst();
      if (oDigest.isPresent()) {
        var digestUrl = format("%s%s/manifests/%s", registryUrl, repoName, oDigest.get());
        var manifestJson0 = getJsonResponseString(digestUrl, authToken, mimeTypeOciManifestV1);
        metadata.manifest = gson.fromJson(manifestJson0, FgManifest.class);
      } else {
        throw new IllegalStateException(format(
          "Unable to find OCI V1 manifest for %s, %s, %s",
          dockerImageUri, architecture, os
        ));
      }
    } else {
      // Regular manifest
      metadata.manifest = gson.fromJson(manifestJson, FgManifest.class);
    }

    // Get image config
    var imgDigest = metadata.manifest.config.digest;
    metadata.config = getImageMetadata(registryUrl, repoName, imgDigest, authToken);

    // Extract layer digests and prepare blob file references
    var layers = !metadata.manifest.layers.isEmpty() ? metadata.manifest.layers : metadata.manifest.fsLayers;
    for (var layer : layers) {
      var blobSum = layer.digest != null ? layer.digest : layer.blobSum;
      metadata.layerDigests.add(blobSum);
      metadata.layerBlobFiles.add(new File(blobCacheDir, blobSum));
    }

    return metadata;
  }

  public static void downloadBlob(String blobUrl, File outputFile, String authToken) throws IOException {
    downloadBlobFromUrl(blobUrl, outputFile, authToken, blobUrl);
  }

  private static void downloadBlobFromUrl(String blobUrl, File outputFile, String authToken, String originalUrl) throws IOException {
    var url = URI.create(blobUrl).toURL();
    var connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");
    
    // Check if redirect is to a different domain (CDN) - don't send auth token for CDN URLs
    var originalHost = getHost(originalUrl);
    var currentHost = getHost(blobUrl);
    var isCdnRedirect = !originalHost.equals(currentHost);
    
    if (authToken != null && !isCdnRedirect) {
      connection.setRequestProperty("Authorization", "Bearer " + authToken);
    }
    
    int responseCode = connection.getResponseCode();
    if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
      throw new IOException("Unauthorized request. Check token.");
    } else if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
      var newUrl = connection.getHeaderField("Location");
      if (newUrl == null) {
        throw new IOException("Redirected but no Location header found.");
      }
      // Redirect URL is already a full URL, use it directly
      // Pass originalUrl to track if we're redirecting to a CDN
      downloadBlobFromUrl(newUrl, outputFile, authToken, originalUrl);
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
}

