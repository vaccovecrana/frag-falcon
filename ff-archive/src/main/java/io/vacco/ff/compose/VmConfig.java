package io.vacco.ff.compose;

import java.util.ArrayList;
import java.util.List;

public class VmConfig {
  public String kernel = "./vmlinuz";
  public String cmdline = "console=ttyS0 root=/dev/vda rw";
  public String initramfs;
  public List<Net> net = new ArrayList<>();
  public Rng rng = new Rng();
  public List<Fs> fs = new ArrayList<>();
}
