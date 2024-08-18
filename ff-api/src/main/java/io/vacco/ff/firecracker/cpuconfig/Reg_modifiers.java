package io.vacco.ff.firecracker.cpuconfig;

import java.lang.Object;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.LinkedHashMap;

@SuppressWarnings("serial")
public class Reg_modifiers extends LinkedHashMap<String, Object> {
  public Reg_modifiers kv(String key, Object value) {
    put(key, value);
    return this;
  }

  public static Reg_modifiers reg_modifiers() {
    return new Reg_modifiers();
  }
}
