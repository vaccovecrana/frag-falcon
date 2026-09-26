package io.vacco.ff.firecracker;

import java.lang.Boolean;
import java.lang.Long;

/**
 * Balloon device descriptor.
 */
public class Balloon {
  public Long amount_mib;

  public Boolean deflate_on_oom;

  public Long stats_polling_interval_s;

  /**
   * Target balloon size in MiB.
   */
  public Balloon amount_mib(Long amount_mib) {
    this.amount_mib = amount_mib;
    return this;
  }

  /**
   * Whether the balloon should deflate when the guest has memory pressure.
   */
  public Balloon deflate_on_oom(Boolean deflate_on_oom) {
    this.deflate_on_oom = deflate_on_oom;
    return this;
  }

  /**
   * Interval in seconds between refreshing statistics. A non-zero value will enable the statistics. Defaults to 0.
   */
  public Balloon stats_polling_interval_s(Long stats_polling_interval_s) {
    this.stats_polling_interval_s = stats_polling_interval_s;
    return this;
  }

  public static Balloon balloon() {
    return new Balloon();
  }
}
