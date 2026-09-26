package io.vacco.ff.schema;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

public class FgRequest {

  public List<String> errors;

  @SuppressWarnings("unchecked")
  public <T extends FgRequest> T withErrors(List<String> errors) {
    this.errors = requireNonNull(errors);
    return (T) this;
  }

  @SuppressWarnings("unchecked")
  public <T extends FgRequest> T withError(String error) {
    if (this.errors == null) {
      this.errors = new ArrayList<>();
    }
    this.errors.add(requireNonNull(error));
    return (T) this;
  }

}
