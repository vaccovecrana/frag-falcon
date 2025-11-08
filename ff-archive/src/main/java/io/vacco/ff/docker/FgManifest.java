package io.vacco.ff.docker;

import java.util.ArrayList;
import java.util.List;

public class FgManifest {

  public Integer schemaVersion;
  public String mediaType;
  public FgConfigDescriptor config;
  public List<FgLayer> layers = new ArrayList<>();
  public List<FgLayer> fsLayers = new ArrayList<>();

}

