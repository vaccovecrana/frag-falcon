package io.vacco.ff.firecracker.machineconfiguration;

import com.google.gson.annotations.SerializedName;

/**
 * Which huge pages configuration (if any) should be used to back guest memory.
 */
public enum Huge_pages {
  None,
  @SerializedName("2M")
  Val001
}
