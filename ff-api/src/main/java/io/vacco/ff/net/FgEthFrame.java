package io.vacco.ff.net;

import java.util.Objects;

public class FgEthFrame {

  public byte[] src;
  public byte[] dst;
  public byte[] payload;

  public static FgEthFrame of(byte[] src, byte[] dst, byte[] payload) {
    var f = new FgEthFrame();
    f.src = Objects.requireNonNull(src);
    f.dst = Objects.requireNonNull(dst);
    f.payload = Objects.requireNonNull(payload);
    return f;
  }

}
