package io.vacco.ff.firecracker;

/**
 * The CPU Template defines a set of flags to be disabled from the microvm so that the features exposed to the guest are the same as in the selected instance type. This parameter has been deprecated and it will be removed in future Firecracker release.
 */
public enum CpuTemplate {
  C3,

  T2,

  T2S,

  T2CL,

  T2A,

  V1N1,

  None
}
