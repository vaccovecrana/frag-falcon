package io.vacco.ff.docker;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FgDockerImageMetadata {

  public FgManifest manifest;
  public FgImage config;
  public List<String> layerDigests = new ArrayList<>();
  public List<File> layerBlobFiles = new ArrayList<>();
  public String registryUrl;
  public String repository;
  public String authToken;
  public String source;

}