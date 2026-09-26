package io.vacco.ff.firecracker;

import io.vacco.ff.firecracker.instanceinfo.State;
import java.lang.String;

/**
 * Describes MicroVM instance information.
 */
public class InstanceInfo {
  public String app_name;

  public String id;

  public State state;

  public String vmm_version;

  /**
   * Application name.
   */
  public InstanceInfo app_name(String app_name) {
    this.app_name = app_name;
    return this;
  }

  /**
   * MicroVM / instance ID.
   */
  public InstanceInfo id(String id) {
    this.id = id;
    return this;
  }

  /**
   * The current detailed state (Not started, Running, Paused) of the Firecracker instance. This value is read-only for the control-plane.
   */
  public InstanceInfo state(State state) {
    this.state = state;
    return this;
  }

  /**
   * MicroVM hypervisor build version.
   */
  public InstanceInfo vmm_version(String vmm_version) {
    this.vmm_version = vmm_version;
    return this;
  }

  public static InstanceInfo instanceInfo() {
    return new InstanceInfo();
  }
}
