package io.vacco.ff.oci;

/**
 * Optional progress callback for OCI image extraction, so callers (e.g. a
 * hypervisor API) can report provisioning status to a UI.
 */
public interface FgOciProgress {

  default void onLayers(int done, int total) {}

  default void onBytes(long done, long total) {}

  FgOciProgress NOOP = new FgOciProgress() {};
}
