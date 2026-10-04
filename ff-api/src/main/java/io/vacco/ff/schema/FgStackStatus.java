package io.vacco.ff.schema;

import java.util.LinkedHashMap;
import java.util.Map;

public class FgStackStatus {

  public String id;
  public FgStackState state = FgStackState.stopped;
  public Map<String, FgServiceStatus> services = new LinkedHashMap<>();

  public static FgStackStatus of(String id) {
    var s = new FgStackStatus();
    s.id = id;
    return s;
  }
}
