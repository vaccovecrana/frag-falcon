package io.vacco.ff.schema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A stack: a set of named services plus runtime-derived per-service ids.
 *
 * <p>Stored as JSON (the UI edits YAML and converts client-side). The
 * {@code networks} compose key is ignored.
 */
public class FgStack {

  public String id;
  public Map<String, FgService> services = new LinkedHashMap<>();

  public FgStack id(String id) {
    this.id = id;
    return this;
  }

  public List<String> serviceNames() {
    return List.copyOf(services.keySet());
  }
}
