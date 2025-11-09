package io.vacco.ff.docker;

import io.vacco.ff.archive.FgTarEntry;
import java.util.*;

public class FgConfig {

  public List<String> Entrypoint = new ArrayList<>();
  public List<String> Cmd = new ArrayList<>();
  public List<String> Env = new ArrayList<>();
  public String WorkingDir;
  
  // Result image fields
  public String source;
  public String rootDir;
  public Set<FgTarEntry> files;

  public FgConfig withSource(String source) {
    this.source = source;
    return this;
  }

}

