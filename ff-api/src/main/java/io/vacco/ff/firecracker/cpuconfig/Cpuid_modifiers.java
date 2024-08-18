package io.vacco.ff.firecracker.cpuconfig;

import java.lang.Object;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.LinkedHashMap;

@SuppressWarnings("serial")
public class Cpuid_modifiers extends LinkedHashMap<String, Object> {
  public Cpuid_modifiers kv(String key, Object value) {
    put(key, value);
    return this;
  }

  public static Cpuid_modifiers cpuid_modifiers() {
    return new Cpuid_modifiers();
  }
}
