package io.vacco.ff.firecracker;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class FullVmConfiguration {
  public Balloon balloon;

  @SerializedName("boot-source")
  public BootSource bootsource;

  public List<Drive> drives;

  public Logger logger;

  @SerializedName("machine-config")
  public MachineConfiguration machineconfig;

  public Metrics metrics;

  @SerializedName("mmds-config")
  public MmdsConfig mmdsconfig;

  @SerializedName("network-interfaces")
  public List<NetworkInterface> networkinterfaces;

  public Vsock vsock;

  public FullVmConfiguration balloon(Balloon balloon) {
    this.balloon = balloon;
    return this;
  }

  public FullVmConfiguration bootsource(BootSource bootsource) {
    this.bootsource = bootsource;
    return this;
  }

  /**
   * Configurations for all block devices.
   */
  public FullVmConfiguration drives(List<Drive> drives) {
    this.drives = drives;
    return this;
  }

  public FullVmConfiguration logger(Logger logger) {
    this.logger = logger;
    return this;
  }

  public FullVmConfiguration machineconfig(MachineConfiguration machineconfig) {
    this.machineconfig = machineconfig;
    return this;
  }

  public FullVmConfiguration metrics(Metrics metrics) {
    this.metrics = metrics;
    return this;
  }

  public FullVmConfiguration mmdsconfig(MmdsConfig mmdsconfig) {
    this.mmdsconfig = mmdsconfig;
    return this;
  }

  /**
   * Configurations for all net devices.
   */
  public FullVmConfiguration networkinterfaces(List<NetworkInterface> networkinterfaces) {
    this.networkinterfaces = networkinterfaces;
    return this;
  }

  public FullVmConfiguration vsock(Vsock vsock) {
    this.vsock = vsock;
    return this;
  }

  public static FullVmConfiguration fullVmConfiguration() {
    return new FullVmConfiguration();
  }
}
