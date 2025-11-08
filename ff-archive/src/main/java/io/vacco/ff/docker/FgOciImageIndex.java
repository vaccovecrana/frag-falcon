package io.vacco.ff.docker;

import java.util.ArrayList;
import java.util.List;

public class FgOciImageIndex {

  public Integer schemaVersion;
  public String mediaType;
  public List<FgOciManifestDescriptor> manifests = new ArrayList<>();

}
