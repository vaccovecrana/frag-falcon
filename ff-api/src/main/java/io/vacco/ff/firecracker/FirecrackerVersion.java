package io.vacco.ff.firecracker;

import java.lang.String;

/**
 * Describes the Firecracker version.
 */
public class FirecrackerVersion {
  public String firecracker_version;

  /**
   * Firecracker build version.
   */
  public FirecrackerVersion firecracker_version(String firecracker_version) {
    this.firecracker_version = firecracker_version;
    return this;
  }

  public static FirecrackerVersion firecrackerVersion() {
    return new FirecrackerVersion();
  }
}
