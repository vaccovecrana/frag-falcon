package io.vacco.ff.schema;

/**
 * Per-service compute resources. This is a frag-falcon extension (not part of
 * standard docker compose); when omitted, 1 vCPU / 512 MiB is assigned.
 */
public class FgResources {

  public int vcpus;
  public int ramMib;
}
