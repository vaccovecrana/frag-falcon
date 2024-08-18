package io.vacco.ff.firecracker;

import io.vacco.ff.firecracker.memorybackend.Backend_type;
import java.lang.String;

public class MemoryBackend {
  public String backend_path;

  public Backend_type backend_type;

  /**
   * Based on &#39;backend_type&#39; it is either 1) Path to the file that contains the guest memory to be loaded 2) Path to the UDS where a process is listening for a UFFD initialization control payload and open file descriptor that it can use to serve this process&#39;s guest memory page faults
   */
  public MemoryBackend backend_path(String backend_path) {
    this.backend_path = backend_path;
    return this;
  }

  public MemoryBackend backend_type(Backend_type backend_type) {
    this.backend_type = backend_type;
    return this;
  }

  public static MemoryBackend memoryBackend() {
    return new MemoryBackend();
  }
}
