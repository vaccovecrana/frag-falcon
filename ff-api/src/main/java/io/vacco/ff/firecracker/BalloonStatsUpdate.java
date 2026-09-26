package io.vacco.ff.firecracker;

import java.lang.Long;

/**
 * Update the statistics polling interval, with the first statistics update scheduled immediately. Statistics cannot be turned on/off after boot.
 */
public class BalloonStatsUpdate {
  public Long stats_polling_interval_s;

  /**
   * Interval in seconds between refreshing statistics.
   */
  public BalloonStatsUpdate stats_polling_interval_s(Long stats_polling_interval_s) {
    this.stats_polling_interval_s = stats_polling_interval_s;
    return this;
  }

  public static BalloonStatsUpdate balloonStatsUpdate() {
    return new BalloonStatsUpdate();
  }
}
