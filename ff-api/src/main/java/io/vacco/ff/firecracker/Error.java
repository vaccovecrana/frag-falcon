package io.vacco.ff.firecracker;

import java.lang.String;

public class Error {
  public String fault_message;

  /**
   * A description of the error condition
   */
  public Error fault_message(String fault_message) {
    this.fault_message = fault_message;
    return this;
  }

  public static Error error() {
    return new Error();
  }
}
