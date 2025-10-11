package io.vacco.ff.compose;

import java.util.HashMap;
import java.util.Map;

public class ComposeFile { // TODO clean these up
  public String version;
  public String name;
  public Map<String, Service> services = new HashMap<>();
  public Map<String, Network> networks = new HashMap<>();
  public Map<String, Volume> volumes = new HashMap<>();
  public Map<String, Model> models = new HashMap<>();
}

