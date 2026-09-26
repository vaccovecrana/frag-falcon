package io.vacco.ff.schema;

import io.vacco.ff.firecracker.*;
import java.util.List;

/**
 * This class exists because:
 *
 * <ul>
 *   <li>
 *     Amazon chose poor names for their FullVmConfiguration schema (e.g. fields requiring SerializedName annotations).
 *   </li>
 *   <li>
 *     Typescript, in all its *infinite* wisdom, doesn't know how to deserialize SerializedName fields.
 *   </li>
 * </ul>
 *
 * So yeah, this is a copy of Firecracker's FullVmConfiguration. Genius...
 */
public class FgConfig {

  public Balloon balloon;
  public BootSource bootsource;
  public List<Drive> drives;
  public Logger logger;
  public MachineConfiguration machineconfig;
  public Metrics metrics;
  public MmdsConfig mmdsconfig;
  public List<NetworkInterface> networkinterfaces;
  public Vsock vsock;

  public List<Drive> driveList() {
    return drives;
  }

  public List<NetworkInterface> networkInterfaces() {
    return networkinterfaces;
  }

}
