# Security

`frag-falcon` is **not meant to be exposed publicly**. It has no authentication
and manages privileged networking. Keep it on a private LAN segment or behind a
VPN.

## Design

- **Rootless.** The hypervisor runs unprivileged. The only privileged pieces are
  a file capability (`cap_net_admin`) on the launcher and membership in the `kvm`
  group — both delegated to the OS once (see [Deployment](20-deployment.md)).
- **Kernel-confined image extraction.** OCI layers are extracted with
  `openat2(RESOLVE_IN_ROOT)`-style operations, so a malicious layer cannot escape
  the target directory via symlinks or `..` traversal.
- **Hardened VM storage.** The `--vm-dir` must be mounted `nosuid,nodev,noexec`
  host-wide, so files a guest plants in its rootfs (setuid binaries, device nodes,
  executables) are inert to host-side processes. The hypervisor audits this at
  startup and warns if it is missing.
- **Writable but ephemeral rootfs.** The extracted rootfs is writable, so a guest
  can modify its own filesystem — but it is *eventually ephemeral*: deleting and
  re-provisioning a service discards it.

For depth, see
[CAVEATS §10 (not a public service)](https://github.com/vaccovecrana/frag-falcon/blob/main/CAVEATS.md),
[§13 (kernel-confined extraction)](https://github.com/vaccovecrana/frag-falcon/blob/main/CAVEATS.md),
and [§15 (ephemeral rootfs)](https://github.com/vaccovecrana/frag-falcon/blob/main/CAVEATS.md).

Next: [Development & API](80-development.md).
