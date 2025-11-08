package io.vacco.ff.compose;

import java.util.HashMap;
import java.util.Map;

public class FgComposeFile { // TODO clean these up

  public String version;
  public String name;
  public Map<String, FgService> services = new HashMap<>();
  public Map<String, FgNetwork> networks = new HashMap<>();
  public Map<String, FgVolume> volumes = new HashMap<>();

}

