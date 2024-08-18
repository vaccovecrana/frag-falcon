package io.vacco.ff.firecracker;

import java.lang.Boolean;
import java.lang.String;

/**
 * Defines the configuration used for handling snapshot resume. Exactly one of the two `mem_<code>*</code>` fields must be present in the body of the request.
 */
public class SnapshotLoadParams {
  public Boolean enable_diff_snapshots;

  public MemoryBackend mem_backend;

  public String mem_file_path;

  public Boolean resume_vm;

  public String snapshot_path;

  /**
   * Enable support for incremental (diff) snapshots by tracking dirty guest pages.
   */
  public SnapshotLoadParams enable_diff_snapshots(Boolean enable_diff_snapshots) {
    this.enable_diff_snapshots = enable_diff_snapshots;
    return this;
  }

  public SnapshotLoadParams mem_backend(MemoryBackend mem_backend) {
    this.mem_backend = mem_backend;
    return this;
  }

  /**
   * Path to the file that contains the guest memory to be loaded. It is only allowed if `mem_backend` is not present. This parameter has been deprecated and it will be removed in future Firecracker release.
   */
  public SnapshotLoadParams mem_file_path(String mem_file_path) {
    this.mem_file_path = mem_file_path;
    return this;
  }

  /**
   * When set to true, the vm is also resumed if the snapshot load is successful.
   */
  public SnapshotLoadParams resume_vm(Boolean resume_vm) {
    this.resume_vm = resume_vm;
    return this;
  }

  /**
   * Path to the file that contains the microVM state to be loaded.
   */
  public SnapshotLoadParams snapshot_path(String snapshot_path) {
    this.snapshot_path = snapshot_path;
    return this;
  }

  public static SnapshotLoadParams snapshotLoadParams() {
    return new SnapshotLoadParams();
  }
}
