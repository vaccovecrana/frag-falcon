package io.vacco.ff.schema;

import java.util.Objects;

public class FgEnvVar {

  public String key, val;

  public static FgEnvVar of(String key, String val) {
    var v = new FgEnvVar();
    v.key = Objects.requireNonNull(key);
    v.val = val;
    return v;
  }

  @Override public String toString() {
    return String.format(
      "%s%s", key,
      val != null
        ? String.format(" -> %s", val)
        : ""
    );
  }
}
