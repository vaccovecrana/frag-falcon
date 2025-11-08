package io.vacco.ff.docker;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FgMain {
  // Docker config fields
  public List<String> Entrypoint = new ArrayList<>();
  public List<String> Cmd = new ArrayList<>();
  public List<String> Env = new ArrayList<>();
  public String WorkingDir;
  
  // Result image fields
  public String source;
  public String rootDir;
  public List<FgEnvVar> envUsr = new ArrayList<>();

  public static FgMain of(String rootDir, List<String> entrypoint, List<String> cmd, List<FgEnvVar> env, String workingDir) {
    var img = new FgMain();
    img.rootDir = rootDir;
    if (entrypoint != null) {
      img.Entrypoint = entrypoint;
    }
    if (cmd != null) {
      img.Cmd = cmd;
    }
    if (env != null) {
      img.Env = env.stream()
        .map(e -> e.val != null ? e.key + "=" + e.val : e.key)
        .collect(Collectors.toList());
    }
    img.WorkingDir = workingDir;
    return img;
  }

  public FgMain withSource(String source) {
    this.source = source;
    return this;
  }

  public FgMain withEnvUsr(List<FgEnvVar> envUsr) {
    if (envUsr != null) {
      this.envUsr = envUsr;
    }
    return this;
  }

  public List<FgEnvVar> getEnvAsFgEnvVar() {
    return Env.stream()
      .map(e -> {
        var parts = e.split("=", 2);
        return FgEnvVar.of(parts[0], parts.length == 2 ? parts[1] : null);
      })
      .collect(Collectors.toList());
  }
}

