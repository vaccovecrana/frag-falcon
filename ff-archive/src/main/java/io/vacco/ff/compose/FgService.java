package io.vacco.ff.compose;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FgService {

  public String image;
  public List<String> command = new ArrayList<>();
  public Map<String, String> environment = new HashMap<>();
  public List<FgPort> ports = new ArrayList<>();
  public List<FgVolumeMount> volumes = new ArrayList<>();
  public List<String> networks = new ArrayList<>();
  public FgUlimits ulimits = new FgUlimits();
  public List<String> depends_on = new ArrayList<>();
  public String restart;
  public FgVmConfig vm = new FgVmConfig();
  public List<String> models = new ArrayList<>();
  public int cpu_count = 1;
  public String mem_limit = "1g";

}
