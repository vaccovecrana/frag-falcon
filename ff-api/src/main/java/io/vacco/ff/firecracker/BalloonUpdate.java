package io.vacco.ff.firecracker;

import java.lang.Long;

/**
 * Balloon device descriptor.
 */
public class BalloonUpdate {
  public Long amount_mib;

  /**
   * Target balloon size in MiB.
   */
  public BalloonUpdate amount_mib(Long amount_mib) {
    this.amount_mib = amount_mib;
    return this;
  }

  public static BalloonUpdate balloonUpdate() {
    return new BalloonUpdate();
  }
}
