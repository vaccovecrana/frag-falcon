package io.vacco.ff.net;

import java.io.File;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.vacco.ff.net.FgJni.*;
import static java.lang.String.format;

public class FgNetIo {

  public static <T> T withUnixSocket(File socketPath, Function<Integer, T> fn) {
    var sock = -1;
    try {
      sock = unixOpen(socketPath.getAbsolutePath());
      return fn.apply(sock);
    } finally {
      if (sock != -1) {
        unixClose(sock);
      }
    }
  }

  public static <T> T withRawSocket(String ifName, Function<Integer, T> fn) {
    var sock = -1;
    try {
      sock = rawCreate(ifName);
      return fn.apply(sock);
    } finally {
      if (sock != -1) {
        rawClose(sock);
      }
    }
  }

  private static void setPromisc(String ifName, boolean enabled) {
    if (rawPromisc(ifName, enabled) != 0) {
      throw new IllegalStateException(format(
        "Unable to configure interface [%s] in promiscuous mode [%s]", ifName, enabled
      ));
    }
  }

  public static <T> T withPromiscIf(String ifName, Supplier<T> supp) {
    setPromisc(ifName, true);
    var result = supp.get();
    setPromisc(ifName, false);
    return result;
  }

  public static void rawSocketSend(int sock, byte[] srcMac, byte[] payload) {
    int sent = rawSend(sock, payload);
    if (sent < 0) {
      throw new IllegalStateException(format(
        "Unable to send raw: socket [%d] mac [%s], [%d]",
        sock, macToString(srcMac), sent
      ));
    }
  }

  public static String unixSendReceive(int sock, byte[] payload, int recBuffSize, int timeoutMs) {
    if (unixSend(sock, payload) == -1) {
      throw new IllegalStateException(format("Unable to send UNIX socket data, socket [%d]", sock));
    }
    var recBuff = new byte[recBuffSize];
    var recBytes = unixReceive(sock, recBuff, timeoutMs);
    if (recBytes < 0) {
      throw new IllegalStateException(format("Unable to receive UNIX socket data, socket [%d, %d]", sock, recBytes));
    }
    return new String(recBuff).trim();
  }

}
