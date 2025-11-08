package io.vacco.ff.docker;

import java.util.ArrayList;
import java.util.List;

public class FgConfig {

  // Docker config fields
  public List<String> Entrypoint = new ArrayList<>();
  public List<String> Cmd = new ArrayList<>();
  public List<String> Env = new ArrayList<>();
  public String WorkingDir;
  
  // Result image fields
  public String source;
  public String rootDir;

  public FgConfig withSource(String source) {
    this.source = source;
    return this;
  }

}

