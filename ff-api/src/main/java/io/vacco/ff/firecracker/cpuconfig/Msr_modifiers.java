package io.vacco.ff.firecracker.cpuconfig;

import java.lang.Object;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.LinkedHashMap;

@SuppressWarnings("serial")
public class Msr_modifiers extends LinkedHashMap<String, Object> {
  public Msr_modifiers kv(String key, Object value) {
    put(key, value);
    return this;
  }

  public static Msr_modifiers msr_modifiers() {
    return new Msr_modifiers();
  }
}
