package io.vacco.ff.schema;

import java.util.List;
import java.util.Objects;

public class FgVmResourceList extends FgRequest {

  public List<String> items;

  public FgVmResourceList withItems(List<String> items) {
    this.items = Objects.requireNonNull(items);
    return this;
  }

}
