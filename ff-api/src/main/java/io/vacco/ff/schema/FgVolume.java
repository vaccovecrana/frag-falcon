package io.vacco.ff.schema;

import java.util.Objects;

/**
 * A host directory shared into the guest (virtiofs bind mount).
 */
public class FgVolume {

  public String hostPath;
  public String guestPath;
  public boolean readOnly;

  public static FgVolume of(String hostPath, String guestPath, boolean readOnly) {
    var v = new FgVolume();
    v.hostPath = Objects.requireNonNull(hostPath);
    v.guestPath = Objects.requireNonNull(guestPath);
    v.readOnly = readOnly;
    return v;
  }

  /**
   * Parses a compose-style volume spec: {@code HOST:GUEST[:ro]}.
   */
  public static FgVolume parse(String spec) {
    var parts = spec.split(":");
    if (parts.length < 2) {
      throw new IllegalArgumentException("Invalid volume spec (expected HOST:GUEST[:ro]): " + spec);
    }
    var readOnly = parts.length > 2 && parts[2].equals("ro");
    return of(parts[0], parts[1], readOnly);
  }
}
