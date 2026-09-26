package io.vacco.ff.firecracker;

import io.vacco.ff.firecracker.instanceactioninfo.Action_type;

/**
 * Variant wrapper containing the real action.
 */
public class InstanceActionInfo {
  public Action_type action_type;

  /**
   * Enumeration indicating what type of action is contained in the payload
   */
  public InstanceActionInfo action_type(Action_type action_type) {
    this.action_type = action_type;
    return this;
  }

  public static InstanceActionInfo instanceActionInfo() {
    return new InstanceActionInfo();
  }
}
