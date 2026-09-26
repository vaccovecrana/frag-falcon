package io.vacco.ff.net;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.lang.System.arraycopy;

public class FgJni {

  private static final Random rng = new Random();

  public static final byte[] BroadcastMac = new byte[] {
    (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff
  };

  static {
    var libPath = new File("./fg_jni.so");
    if (!libPath.exists()) {
      try (var in = FgJni.class.getResourceAsStream("/io/vacco/ff/fg_jni.so");
           var out = new FileOutputStream(libPath)) {
        Objects.requireNonNull(in).transferTo(out);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
    System.load(libPath.getAbsolutePath());
  }

  // Process management
  public static native int fork(String vmId, String command, String[] args, String logPath);
  public static native int terminate(int pid);

  // Tap interface management
  public static native int tapCreate(String ifName);
  public static native int tapAttach(String ifName, String brId);
  public static native int tapDetach(String ifName, String brId);
  public static native int tapDelete(String ifName);

  public static native byte[] getMacAddress(String ifName);

  // Raw socket communication
  public static native int rawCreate(String interfaceName);
  public static native int rawSend(int socketHandle, byte[] payload);
  public static native int rawReceive(int socketHandle, byte[] buffer, int timeoutSeconds);
  public static native void rawClose(int socketHandle);
  public static native int rawPromisc(String interfaceName, boolean enabled);

  // UNIX socket communication
  public static native int unixOpen(String socketPath);
  public static native int unixSend(int socketHandle, byte[] buffer);
  public static native int unixReceive(int socketHandle, byte[] buffer, int timeoutMs);
  public static native int unixClose(int socketHandle);

  // VSOCK communication
  public static native int vSocketOpen(String address);
  public static native int vSockSend(int socketHandle, byte[] buffer);
  public static native int vSockReceive(int socketHandle, byte[] buffer);
  public static native int vSocketClose(int socketHandle);

  public static byte[] httpRequest(String method, String path, String body) {
    var request = new StringBuilder();
    request.append(method).append(" ").append(path).append(" HTTP/1.1\r\n");
    request.append("Host: localhost\r\n");
    request.append("Connection: close\r\n");
    if (body != null && !body.isEmpty()) {
      request.append("Content-Length: ").append(body.length()).append("\r\n");
      request.append("Content-Type: text/plain\r\n");
      request.append("\r\n");
      request.append(body);
    } else {
      request.append("\r\n");
    }
    return request.toString().getBytes();
  }

  public static byte[] trim(byte[] buffer, int byteCount) {
    if (buffer == null || byteCount < 0 || byteCount > buffer.length) {
      throw new IllegalArgumentException("Invalid buffer or byteCount");
    }
    return Arrays.copyOfRange(buffer, 0, byteCount);
  }

  public static byte[] ethernetTx(FgEthFrame frame) {
    var etherType = new byte[] {(byte) 0x08, (byte) 0x00}; // EtherType for IPv4
    var ethernetFrame = new byte[frame.dst.length + frame.src.length + etherType.length + frame.payload.length];
    arraycopy(frame.dst, 0, ethernetFrame, 0, frame.dst.length);
    arraycopy(frame.src, 0, ethernetFrame, frame.dst.length, frame.src.length);
    arraycopy(etherType, 0, ethernetFrame, frame.dst.length + frame.src.length, etherType.length);
    arraycopy(frame.payload, 0, ethernetFrame, frame.dst.length + frame.src.length + etherType.length, frame.payload.length);
    return ethernetFrame;
  }

  public static FgEthFrame ethernetRx(byte[] ethernetFrame) {
    if (ethernetFrame == null || ethernetFrame.length <= 14) {
      return null;
    }
    var frame = new FgEthFrame();
    frame.dst = new byte[6];
    frame.src = new byte[6];
    arraycopy(ethernetFrame, 0, frame.dst, 0, 6); // Copy Destination MAC
    arraycopy(ethernetFrame, 6, frame.src, 0, 6); // Copy Source MAC
    int payloadLength = ethernetFrame.length - 14;
    frame.payload = new byte[payloadLength];
    arraycopy(ethernetFrame, 14, frame.payload, 0, payloadLength);
    return frame;
  }

  public static byte[] newMacAddress() {
    var macAddress = new byte[6];
    macAddress[0] = (byte) (rng.nextInt(256) & 0xFC | 0x02);
    for (int i = 1; i < 6; i++) {
      macAddress[i] = (byte) rng.nextInt(256);
    }
    return macAddress;
  }

  public static String macToString(byte[] mac) {
    if (mac == null || mac.length != 6) {
      throw new IllegalArgumentException("Invalid MAC address");
    }
    return format("%02x:%02x:%02x:%02x:%02x:%02x",
      mac[0] & 0xFF, mac[1] & 0xFF, mac[2] & 0xFF,
      mac[3] & 0xFF, mac[4] & 0xFF, mac[5] & 0xFF);
  }

  public static byte[] macToBytes(String macStr) {
    var hexParts = macStr.split(":");
    if (hexParts.length != 6) {
      throw new IllegalArgumentException("Invalid MAC address format");
    }
    var macBytes = new byte[6];
    for (int i = 0; i < 6; i++) {
      int hexVal = parseInt(hexParts[i], 16);
      macBytes[i] = (byte) hexVal;
    }
    return macBytes;
  }

  public static byte[] parseIpAddress(String ipAddress) {
    var parts = ipAddress.split("\\.");
    if (parts.length != 4) {
      throw new IllegalArgumentException("Invalid IP address.");
    }
    var ipBytes = new byte[4];
    for (int i = 0; i < 4; i++) {
      ipBytes[i] = (byte) Integer.parseInt(parts[i]);
    }
    return ipBytes;
  }

  private static void setIpChecksum(byte[] ipHeader) {
    int length = ipHeader.length;
    int i = 0;
    long sum = 0;
    while (length > 1) {
      sum += ((ipHeader[i] << 8 & 0xFF00) | (ipHeader[i + 1] & 0xFF));
      if ((sum & 0xFFFF0000) > 0) {
        sum = sum & 0xFFFF;
        sum += 1;
      }
      i += 2;
      length -= 2;
    }
    if (length > 0) {
      sum += (ipHeader[i] << 8 & 0xFF00);
      if ((sum & 0xFFFF0000) > 0) {
        sum = sum & 0xFFFF;
        sum += 1;
      }
    }
    sum = ~sum;
    sum = sum & 0xFFFF;
    ipHeader[10] = (byte) (sum >> 8);
    ipHeader[11] = (byte) (sum & 0xFF);
  }

  public static byte[] udpWrap(byte[] dhcpMessage, String srcIp, String destIp, int srcPort, int destPort) {
    var ipHeader = new byte[20]; // Standard IP header length without options
    var udpHeader = new byte[8]; // Standard UDP header length
    var packet = new byte[ipHeader.length + udpHeader.length + dhcpMessage.length];

    ipHeader[0] = 0x45; // IPv4 + 5 words (20 bytes) header length
    ipHeader[1] = 0x00; // Type of service
    int totalLength = ipHeader.length + udpHeader.length + dhcpMessage.length;
    ipHeader[2] = (byte) (totalLength >> 8);
    ipHeader[3] = (byte) (totalLength & 0xFF);
    ipHeader[4] = 0x00; // ID
    ipHeader[5] = 0x00; // ID continuation
    ipHeader[6] = 0x00; // Flags
    ipHeader[7] = 0x00; // Fragment offset
    ipHeader[8] = 0x40; // TTL (64)
    ipHeader[9] = 0x11; // Protocol: UDP
    arraycopy(parseIpAddress(srcIp), 0, ipHeader, 12, 4);
    arraycopy(parseIpAddress(destIp), 0, ipHeader, 16, 4);
    setIpChecksum(ipHeader);

    udpHeader[0] = (byte) (srcPort >> 8);
    udpHeader[1] = (byte) (srcPort & 0xFF);
    udpHeader[2] = (byte) (destPort >> 8);
    udpHeader[3] = (byte) (destPort & 0xFF);
    int udpLength = udpHeader.length + dhcpMessage.length;
    udpHeader[4] = (byte) (udpLength >> 8);
    udpHeader[5] = (byte) (udpLength & 0xFF);

    // Construct the final packet
    arraycopy(ipHeader, 0, packet, 0, ipHeader.length);
    arraycopy(udpHeader, 0, packet, ipHeader.length, udpHeader.length);
    arraycopy(dhcpMessage, 0, packet, ipHeader.length + udpHeader.length, dhcpMessage.length);
    return packet;
  }

  public static byte[] udpUnwrap(byte[] packet) {
    // Assuming the IP header is always 20 bytes and UDP header is 8 bytes
    int headerTotal = 20 + 8;
    if (packet.length <= headerTotal) {
      return null; // Not enough data
    }
    var dhcpData = new byte[packet.length - headerTotal];
    arraycopy(packet, headerTotal, dhcpData, 0, dhcpData.length);
    return dhcpData;
  }

  public static List<String> getLinuxBridgeInterfaces() {
    var bridges = new ArrayList<String>();
    var netDirectory = Paths.get("/sys/class/net/");
    try (var ps = Files.walk(netDirectory, 1)) {
        ps
        .filter(Files::isDirectory)
        .forEach(path -> {
          var bridgePath = path.resolve("bridge");
          if (Files.isDirectory(bridgePath)) {
            bridges.add(path.getFileName().toString());
          }
        });
      return bridges;
    } catch (IOException e) {
      throw new IllegalStateException("Unable to list Linux bridges", e);
    }
  }

}
