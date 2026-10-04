package io.vacco.ff.schema;

import java.util.List;

/**
 * A compose-style service definition (JSON over the wire; the UI converts YAML).
 *
 * <p>{@code ports} and {@code networks} are intentionally absent: each VM gets
 * its own bridge IP, so port space is the guest's concern; the bridge is chosen
 * hypervisor-wide.
 */
public class FgService {

  public String image;
  public String restart;
  public List<String> volumes;
  public List<String> environment;
  public List<String> entrypoint;
  public List<String> command;
  public List<String> depends_on;
  public FgResources resources;
}
