package io.vacco.ff.firecracker;

/**
 * Defines an IO rate limiter with independent bytes/s and ops/s limits. Limits are defined by configuring each of the _bandwidth_ and _ops_ token buckets. This field is optional for virtio-block config and should be omitted for vhost-user-block configuration.
 */
public class RateLimiter {
  public TokenBucket bandwidth;

  public TokenBucket ops;

  public RateLimiter bandwidth(TokenBucket bandwidth) {
    this.bandwidth = bandwidth;
    return this;
  }

  public RateLimiter ops(TokenBucket ops) {
    this.ops = ops;
    return this;
  }

  public static RateLimiter rateLimiter() {
    return new RateLimiter();
  }
}
