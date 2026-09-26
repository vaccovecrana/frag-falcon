package io.vacco.ff.schema;

import java.util.Objects;

public class FgNetConfig {

  public String brIf;
  public FgIpConfig ipConfig;
  public boolean dhcp;

  public FgNetConfig withIpConfig(FgIpConfig ipConfig) {
    this.ipConfig = Objects.requireNonNull(ipConfig);
    return this;
  }

}
