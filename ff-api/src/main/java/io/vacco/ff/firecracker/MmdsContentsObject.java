package io.vacco.ff.firecracker;

import java.lang.Object;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.LinkedHashMap;

@SuppressWarnings("serial")
public class MmdsContentsObject extends LinkedHashMap<String, Object> {
  public MmdsContentsObject kv(String key, Object value) {
    put(key, value);
    return this;
  }

  public static MmdsContentsObject mmdsContentsObject() {
    return new MmdsContentsObject();
  }
}
