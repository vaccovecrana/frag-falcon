package io.vacco.ff.compose;

import java.util.ArrayList;
import java.util.List;

public class FgVmConfig {
  public String kernel = "./vmlinuz";
  public String cmdline = "console=ttyS0 root=/dev/vda rw";
  public String initramfs;
  public List<FgNet> net = new ArrayList<>();
  public FgRng rng = new FgRng();
  public List<FgFs> fs = new ArrayList<>();
}
