package io.vacco.ff.firecracker;

import io.vacco.ff.firecracker.snapshotcreateparams.Snapshot_type;
import java.lang.String;

public class SnapshotCreateParams {
  public String mem_file_path;

  public String snapshot_path;

  public Snapshot_type snapshot_type;

  /**
   * Path to the file that will contain the guest memory.
   */
  public SnapshotCreateParams mem_file_path(String mem_file_path) {
    this.mem_file_path = mem_file_path;
    return this;
  }

  /**
   * Path to the file that will contain the microVM state.
   */
  public SnapshotCreateParams snapshot_path(String snapshot_path) {
    this.snapshot_path = snapshot_path;
    return this;
  }

  /**
   * Type of snapshot to create. It is optional and by default, a full snapshot is created.
   */
  public SnapshotCreateParams snapshot_type(Snapshot_type snapshot_type) {
    this.snapshot_type = snapshot_type;
    return this;
  }

  public static SnapshotCreateParams snapshotCreateParams() {
    return new SnapshotCreateParams();
  }
}
