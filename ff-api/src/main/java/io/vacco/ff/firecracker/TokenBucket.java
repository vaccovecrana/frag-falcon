package io.vacco.ff.firecracker;

import java.lang.Long;

/**
 * Defines a token bucket with a maximum capacity (size), an initial burst size (one_time_burst) and an interval for refilling purposes (refill_time). The refill-rate is derived from size and refill_time, and it is the constant rate at which the tokens replenish. The refill process only starts happening after the initial burst budget is consumed. Consumption from the token bucket is unbounded in speed which allows for bursts bound in size by the amount of tokens available. Once the token bucket is empty, consumption speed is bound by the refill_rate.
 */
public class TokenBucket {
  public Long one_time_burst;

  public Long refill_time;

  public Long size;

  /**
   * The initial size of a token bucket.
   */
  public TokenBucket one_time_burst(Long one_time_burst) {
    this.one_time_burst = one_time_burst;
    return this;
  }

  /**
   * The amount of milliseconds it takes for the bucket to refill.
   */
  public TokenBucket refill_time(Long refill_time) {
    this.refill_time = refill_time;
    return this;
  }

  /**
   * The total number of tokens this bucket can hold.
   */
  public TokenBucket size(Long size) {
    this.size = size;
    return this;
  }

  public static TokenBucket tokenBucket() {
    return new TokenBucket();
  }
}
