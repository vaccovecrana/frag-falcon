package io.vacco.ff.firecracker.drive;

/**
 * Type of the IO engine used by the device. &#34;Async&#34; is supported on host kernels newer than 5.10.51. This field is optional for virtio-block config and should be omitted for vhost-user-block configuration.
 */
public enum Io_engine {
  Sync,

  Async
}
