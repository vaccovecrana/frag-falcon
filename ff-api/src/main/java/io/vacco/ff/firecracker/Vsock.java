package io.vacco.ff.firecracker;

import java.lang.Long;
import java.lang.String;

/**
 * Defines a vsock device, backed by a set of Unix Domain Sockets, on the host side. For host-initiated connections, Firecracker will be listening on the Unix socket identified by the path `uds_path`. Firecracker will create this socket, bind and listen on it. Host-initiated connections will be performed by connection to this socket and issuing a connection forwarding request to the desired guest-side vsock port (i.e. `CONNECT 52\n`, to connect to port 52). For guest-initiated connections, Firecracker will expect host software to be bound and listening on Unix sockets at `uds_path_&#60;PORT&#62;`. E.g. &#34;/path/to/host_vsock.sock_52&#34; for port number 52.
 */
public class Vsock {
  public Long guest_cid;

  public String uds_path;

  public String vsock_id;

  /**
   * Guest Vsock CID
   */
  public Vsock guest_cid(Long guest_cid) {
    this.guest_cid = guest_cid;
    return this;
  }

  /**
   * Path to UNIX domain socket, used to proxy vsock connections.
   */
  public Vsock uds_path(String uds_path) {
    this.uds_path = uds_path;
    return this;
  }

  /**
   * This parameter has been deprecated and it will be removed in future Firecracker release.
   */
  public Vsock vsock_id(String vsock_id) {
    this.vsock_id = vsock_id;
    return this;
  }

  public static Vsock vsock() {
    return new Vsock();
  }
}
