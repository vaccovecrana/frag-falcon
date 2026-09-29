package io.vacco.ff.schema;

import io.vacco.ff.oci.FgImage;

import java.util.ArrayList;
import java.util.List;

/**
 * A single microVM. {@link #tag}.id is the derived VM identity (see
 * {@code FgVmId}) and doubles as the {@code FF_VMID} process tag.
 */
public class FgVm {

  public FgVmTag tag = new FgVmTag();
  public FgImage image;
  public FgVmMachine machine = new FgVmMachine();
  public List<FgVolume> volumes = new ArrayList<>();
  public FgNetConfig network;

  /**
   * Service-level entrypoint override. {@code null} inherits the image's
   * {@code Entrypoint}; an empty list clears it (Docker semantics).
   */
  public List<String> entrypoint;

  /**
   * Service-level command override (replaces the image's {@code Cmd} while
   * keeping the image {@code Entrypoint}). {@code null} inherits the image's.
   */
  public List<String> command;

  /**
   * User-supplied environment (stack service environment), applied last.
   */
  public List<io.vacco.ff.oci.FgEnvVar> env = new ArrayList<>();
}
