package io.vacco.ff.docker;

import java.util.ArrayList;
import java.util.List;

public class FgMain {
  // Docker config fields
  public List<String> Entrypoint = new ArrayList<>();
  public List<String> Cmd = new ArrayList<>();
  public List<String> Env = new ArrayList<>();
  public String WorkingDir;
  
  // Result image fields
  public String source;
  public String rootDir;
  public String[] entryPoint;
  public String[] cmd;
  public List<FgEnvVar> env = new ArrayList<>();
  public List<FgEnvVar> envUsr = new ArrayList<>();

  public static FgMain of(String rootDir, String[] entryPoint, String[] cmd, List<FgEnvVar> env, String workingDir) {
    var img = new FgMain();
    img.rootDir = rootDir;
    img.entryPoint = entryPoint;
    img.cmd = cmd;
    if (env != null) {
      img.env = env;
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
}

