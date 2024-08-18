package io.vacco.ff.firecracker;

import io.vacco.ff.firecracker.drive.Cache_type;
import io.vacco.ff.firecracker.drive.Io_engine;
import java.lang.Boolean;
import java.lang.String;

public class Drive {
  public Cache_type cache_type;

  public String drive_id;

  public Io_engine io_engine;

  public Boolean is_read_only;

  public Boolean is_root_device;

  public String partuuid;

  public String path_on_host;

  public RateLimiter rate_limiter;

  public String socket;

  /**
   * Represents the caching strategy for the block device.
   */
  public Drive cache_type(Cache_type cache_type) {
    this.cache_type = cache_type;
    return this;
  }

  public Drive drive_id(String drive_id) {
    this.drive_id = drive_id;
    return this;
  }

  /**
   * Type of the IO engine used by the device. &#34;Async&#34; is supported on host kernels newer than 5.10.51. This field is optional for virtio-block config and should be omitted for vhost-user-block configuration.
   */
  public Drive io_engine(Io_engine io_engine) {
    this.io_engine = io_engine;
    return this;
  }

  /**
   * Is block read only. This field is required for virtio-block config and should be omitted for vhost-user-block configuration.
   */
  public Drive is_read_only(Boolean is_read_only) {
    this.is_read_only = is_read_only;
    return this;
  }

  public Drive is_root_device(Boolean is_root_device) {
    this.is_root_device = is_root_device;
    return this;
  }

  /**
   * Represents the unique id of the boot partition of this device. It is optional and it will be taken into account only if the is_root_device field is true.
   */
  public Drive partuuid(String partuuid) {
    this.partuuid = partuuid;
    return this;
  }

  /**
   * Host level path for the guest drive. This field is required for virtio-block config and should be omitted for vhost-user-block configuration.
   */
  public Drive path_on_host(String path_on_host) {
    this.path_on_host = path_on_host;
    return this;
  }

  public Drive rate_limiter(RateLimiter rate_limiter) {
    this.rate_limiter = rate_limiter;
    return this;
  }

  /**
   * Path to the socket of vhost-user-block backend. This field is required for vhost-user-block config should be omitted for virtio-block configuration.
   */
  public Drive socket(String socket) {
    this.socket = socket;
    return this;
  }

  public static Drive drive() {
    return new Drive();
  }
}
