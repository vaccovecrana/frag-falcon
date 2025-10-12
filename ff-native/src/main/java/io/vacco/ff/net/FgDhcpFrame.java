package io.vacco.ff.net;

import static java.lang.System.arraycopy;

public class FgDhcpFrame {

  public byte[] packet;

  public byte[] txId() {
    var txId = new byte[4];
    arraycopy(packet, 4, txId, 0, 4);
    return txId;
  }

}
