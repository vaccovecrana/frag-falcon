package io.vacco.ff.firecracker;

/**
 * Defines an entropy device.
 */
public class EntropyDevice {
  public RateLimiter rate_limiter;

  public EntropyDevice rate_limiter(RateLimiter rate_limiter) {
    this.rate_limiter = rate_limiter;
    return this;
  }

  public static EntropyDevice entropyDevice() {
    return new EntropyDevice();
  }
}
