package io.vacco.ff.firecracker.instanceinfo;

import com.google.gson.annotations.SerializedName;

/**
 * The current detailed state (Not started, Running, Paused) of the Firecracker instance. This value is read-only for the control-plane.
 */
public enum State {
  @SerializedName("Not started")
  Val000,

  Running,

  Paused
}
