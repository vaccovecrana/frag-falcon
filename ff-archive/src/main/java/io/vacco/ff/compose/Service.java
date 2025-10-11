package io.vacco.ff.compose;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Service {
  public String image;
  public List<String> command = new ArrayList<>();
  public Map<String, String> environment = new HashMap<>();
  public List<Port> ports = new ArrayList<>();
  public List<VolumeMount> volumes = new ArrayList<>();
  public List<String> networks = new ArrayList<>();
  public Ulimits ulimits = new Ulimits();
  public List<String> depends_on = new ArrayList<>();
  public String restart;
  public VmConfig vm = new VmConfig();
  public List<String> models = new ArrayList<>();
  public int cpu_count = 1;
  public String mem_limit = "1g";
}
