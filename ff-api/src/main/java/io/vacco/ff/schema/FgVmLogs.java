package io.vacco.ff.schema;

import java.util.Objects;

public class FgVmLogs extends FgRequest {

  public String vmId;
  public String logData;

  public FgVmLogs withLogData(String logData) {
    this.logData = Objects.requireNonNull(logData);
    return this;
  }

}
