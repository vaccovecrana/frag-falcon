package io.vacco.ff.net;

import io.vacco.ff.schema.FgIpConfig;
import java.util.*;

import static io.vacco.ff.net.FgJni.parseIpAddress;
import static java.lang.String.format;
import static java.lang.System.*;
import static java.util.Arrays.fill;

public class FgDhcpFrames {

  private static final Random rnd = new Random();

  public static FgIpConfig dhcpResponseFrame(byte[] data, byte[] transactionId0) {
    if (data.length < 20) {
      return null;
    }
    var ipAddress = format("%d.%d.%d.%d", data[16] & 0xFF, data[17] & 0xFF, data[18] & 0xFF, data[19] & 0xFF);
    var subnetMask = (String) null;
    var gateway = (String) null;
    var dnsServers = new ArrayList<String>();
    int leaseTimeSeconds = -1;
    int renewalTimeSeconds = -1;
    int rebindTimeSeconds = -1;
    int optionIndex = 240; // Starting point of options after the magic cookie
    var transactionId = new byte[4];

    arraycopy(data, 4, transactionId, 0, 4);

    if (!Arrays.equals(transactionId, transactionId0)) {
      return null;
    }

    while (optionIndex < data.length && (data[optionIndex] & 0xFF) != 255) {
      int optionType = data[optionIndex] & 0xFF;
      int optionLen = data[optionIndex + 1] & 0xFF;
      switch(optionType) {
        case 1: // Subnet Mask
          subnetMask = format("%d.%d.%d.%d",
            data[optionIndex + 2] & 0xFF, data[optionIndex + 3] & 0xFF,
            data[optionIndex + 4] & 0xFF, data[optionIndex + 5] & 0xFF
          );
          break;
        case 3: // Router
          gateway = format("%d.%d.%d.%d",
            data[optionIndex + 2] & 0xFF, data[optionIndex + 3] & 0xFF,
            data[optionIndex + 4] & 0xFF, data[optionIndex + 5] & 0xFF
          );
          break;
        case 6: // DNS Servers
          for (int i = 0; i < optionLen; i += 4) {
            dnsServers.add(format("%d.%d.%d.%d",
              data[optionIndex + 2 + i] & 0xFF, data[optionIndex + 3 + i] & 0xFF,
              data[optionIndex + 4 + i] & 0xFF, data[optionIndex + 5 + i] & 0xFF)
            );
          }
          break;
        case 51: // Lease Time
          leaseTimeSeconds = ((data[optionIndex + 2] & 0xFF) << 24)
            | ((data[optionIndex + 3] & 0xFF) << 16)
            | ((data[optionIndex + 4] & 0xFF) << 8)
            | (data[optionIndex + 5] & 0xFF);
          break;
        case 58: // Renewal Time (T1)
          renewalTimeSeconds = ((data[optionIndex + 2] & 0xFF) << 24)
            | ((data[optionIndex + 3] & 0xFF) << 16)
            | ((data[optionIndex + 4] & 0xFF) << 8)
            | (data[optionIndex + 5] & 0xFF);
          break;
        case 59: // Rebinding Time (T2)
          rebindTimeSeconds = ((data[optionIndex + 2] & 0xFF) << 24)
            | ((data[optionIndex + 3] & 0xFF) << 16)
            | ((data[optionIndex + 4] & 0xFF) << 8)
            | (data[optionIndex + 5] & 0xFF);
          break;
      }
      optionIndex += 2 + optionLen; // Move to the next option
    }
    return new FgIpConfig(
      ipAddress, subnetMask, gateway, dnsServers, currentTimeMillis(),
      leaseTimeSeconds, renewalTimeSeconds, rebindTimeSeconds,
      transactionId
    );
  }

  public static FgDhcpDiscover dhcpDiscoverFrame(byte[] macAddress, boolean bootPBroadcast) {
    var buffer = new byte[300]; // Typical minimum DHCP packet size
    fill(buffer, (byte) 0);
    buffer[0] = 0x01; // Message type: 1 for request
    buffer[1] = 0x01; // Hardware type: 1 for Ethernet
    buffer[2] = 0x06; // Hardware address length: 6 for MAC
    buffer[3] = 0x00; // Hops: 0

    var transactionId = new byte[4];

    rnd.nextBytes(transactionId);
    arraycopy(transactionId, 0, buffer, 4, 4); // Transaction ID
    fill(buffer, 8, 8 + 2, (byte) 0); // Seconds elapsed

    buffer[10] = 0x00; // Bootp flags byte 1
    if (bootPBroadcast) {
      buffer[11] = (byte) 0x80; // Set BROADCAST_FLAG to true
    } else {
      buffer[11] = 0x00; // Set BROADCAST_FLAG to false
    }

    fill(buffer, 12, 12 + 4, (byte) 0); // Client IP address: 0.0.0.0
    fill(buffer, 16, 16 + 4, (byte) 0); // Your (client) IP address: 0.0.0.0
    fill(buffer, 20, 20 + 4, (byte) 0); // Next server IP address: 0.0.0.0
    fill(buffer, 24, 24 + 4, (byte) 0); // Relay agent IP address: 0.0.0.0
    arraycopy(macAddress, 0, buffer, 28, 6); // Client MAC address
    fill(buffer, 34, 34 + 202, (byte) 0); // Remaining bootp fields zeroed

    // DHCP Magic Cookie
    buffer[236] = (byte) 99;
    buffer[237] = (byte) 130;
    buffer[238] = (byte) 83;
    buffer[239] = (byte) 99;

    // DHCP options - Option 53 DHCP Message Type
    buffer[240] = 53; // Option: DHCP Message Type
    buffer[241] = 1;  // Length
    buffer[242] = 1;  // DHCPDISCOVER

    // End Option
    buffer[243] = (byte) 255;

    var req = new FgDhcpDiscover();
    req.packet = buffer;

    return req;
  }

  public static FgDhcpDiscover dhcpRequestFrame(byte[] macAddress, byte[] transactionId,
                                                String requestedIp, String serverIp) {
    var msg = dhcpDiscoverFrame(macAddress, true); // Start with a basic DHCP Discover message template
    var buffer = msg.packet;
    // Set the transaction ID passed from the previous stage
    arraycopy(transactionId, 0, buffer, 4, 4);

    // Start setting DHCP options right after the DHCP magic cookie
    int optionIndex = 240; // This should be immediately after the magic cookie
    buffer[optionIndex++] = (byte) 53; // DHCP Message Type option ID
    buffer[optionIndex++] = 1;        // Length of the DHCP Message Type option
    buffer[optionIndex++] = (byte) 3; // DHCPREQUEST

    // Requested IP Address Option
    buffer[optionIndex++] = (byte) 50; // Option ID for Requested IP Address
    buffer[optionIndex++] = 4;         // Length

    var requestedIpBytes = parseIpAddress(requestedIp);
    arraycopy(requestedIpBytes, 0, buffer, optionIndex, 4);
    optionIndex += 4;
    buffer[optionIndex++] = (byte) 54; // Option ID for DHCP Server Identifier
    buffer[optionIndex++] = 4;         // Length

    var serverIpBytes = parseIpAddress(serverIp);
    arraycopy(serverIpBytes, 0, buffer, optionIndex, 4);
    optionIndex += 4;

    buffer[optionIndex] = (byte) 255; // End of options marker
    if (optionIndex + 1 < buffer.length) {
      fill(buffer, optionIndex + 1, buffer.length, (byte) 0);
    }
    return msg;
  }

  public static byte[] dhcpReleaseFrame(byte[] macAddress, String yourIp, String serverIp) {
    var msg = dhcpDiscoverFrame(macAddress, true); // Start with the DHCPDISCOVER template
    var buffer = msg.packet;
    buffer[242] = 7; // DHCPRELEASE
    var yourIpBytes = parseIpAddress(yourIp); // Set 'ciaddr' (Client IP Address) to yourIp
    arraycopy(yourIpBytes, 0, buffer, 12, 4); // 'ciaddr' starts at byte 12
    buffer[243] = 54; // Option: DHCP Server Identifier
    buffer[244] = 4;  // Length
    var serverIpBytes = parseIpAddress(serverIp);
    arraycopy(serverIpBytes, 0, buffer, 245, 4); // Set server IP in options
    buffer[249] = (byte) 255; // End Option
    return buffer;
  }

  public static FgDhcpDiscover dhcpRenewFrame(byte[] macAddress, byte[] txId, String yourIp, String serverIp, boolean bootPBroadcast) {
    var msg = dhcpDiscoverFrame(macAddress, bootPBroadcast); // Reuse the discover message template
    var buffer = msg.packet;
    arraycopy(txId, 0, buffer, 4, 4); // Transaction ID
    buffer[242] = 3; // DHCPREQUEST
    var yourIpBytes = parseIpAddress(yourIp); // Set 'ciaddr' (Client IP Address) to yourIp for renewal
    arraycopy(yourIpBytes, 0, buffer, 12, 4); // 'ciaddr' starts at byte 12
    buffer[243] = 54; // Option: DHCP Server Identifier
    buffer[244] = 4;  // Length
    var serverIpBytes = parseIpAddress(serverIp);
    arraycopy(serverIpBytes, 0, buffer, 245, 4); // Set server IP in options
    buffer[249] = (byte) 255; // End Option
    return msg;
  }

}
