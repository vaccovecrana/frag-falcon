package io.vacco.ff.firecracker;

import java.lang.String;

public class PartialDrive {
  public String drive_id;

  public String path_on_host;

  public RateLimiter rate_limiter;

  public PartialDrive drive_id(String drive_id) {
    this.drive_id = drive_id;
    return this;
  }

  /**
   * Host level path for the guest drive. This field is optional for virtio-block config and should be omitted for vhost-user-block configuration.
   */
  public PartialDrive path_on_host(String path_on_host) {
    this.path_on_host = path_on_host;
    return this;
  }

  public PartialDrive rate_limiter(RateLimiter rate_limiter) {
    this.rate_limiter = rate_limiter;
    return this;
  }

  public static PartialDrive partialDrive() {
    return new PartialDrive();
  }
}
