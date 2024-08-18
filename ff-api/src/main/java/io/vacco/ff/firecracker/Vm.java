package io.vacco.ff.firecracker;

import io.vacco.ff.firecracker.vm.State;

/**
 * Defines the microVM running state. It is especially useful in the snapshotting context.
 */
public class Vm {
  public State state;

  public Vm state(State state) {
    this.state = state;
    return this;
  }

  public static Vm vm() {
    return new Vm();
  }
}
