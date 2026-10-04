# Deployment

`frag-falcon` (the `flc` executable) ships as a flat `tar.gz`: the GraalVM native
hypervisor plus the native launcher and libkrun shared objects, all in one
directory.

```
frag-falcon-{{gsVersion}}/
  flc                 # the hypervisor (GraalVM native executable)
  fg_vmm              # per-VM native launcher
  fg_jni.so           # host primitives (JNI)
  libkrun.so.2
  libkrun_init.so
  libkrunfw.so.5
```

Unpack it and run the executable from that directory — it finds `fg_vmm` and the
libkrun libraries beside itself. Download the latest archive from the
[releases page](https://github.com/vaccovecrana/frag-falcon/releases).

## Rootless by design

`flc` is designed to run **unprivileged**. Privilege is delegated to the OS once,
not reimplemented in the hypervisor:

- a dedicated service user owns the VM storage dir (`--vm-dir`);
- the `fg_vmm` launcher carries `cap_net_admin` (for TAP devices) — via `setcap`,
  or systemd `AmbientCapabilities=CAP_NET_ADMIN`;
- the service user is in the **`kvm`** group (for `/dev/kvm`);
- the VM storage dir is mounted **`nosuid,nodev,noexec`** (host-wide, e.g. via
  fstab) so files a guest plants in its rootfs — setuid binaries, device nodes,
  executables — are inert to host-side processes. The hypervisor audits this at
  startup and logs a warning if it is missing.

`deploy/setup.sh <user> <vm-dir> [install-dir]` performs the one-time setup, and
`deploy/flc.service` is a sample systemd unit. The hypervisor never needs root.

`flc` **refuses to start** if the launcher lacks `CAP_NET_ADMIN` (checked via
`setcap`, systemd `AmbientCapabilities`, or the process effective set), since it
could not create the per-VM TAP devices a stack needs.

## Quick start (Debian/systemd)

```bash
# 1. Unpack the release somewhere persistent
sudo mkdir -p /opt/frag-falcon
sudo tar -xzf frag-falcon-{{gsVersion}}.tar.gz -C /opt/frag-falcon --strip-components=1

# 2. One-time root setup: service user, kvm group, vm-dir, cap_net_admin
sudo bash /opt/frag-falcon/deploy/setup.sh flc /var/lib/flc /opt/frag-falcon

# 3. Harden the vm-dir (host-wide, so a private mount namespace is not enough)
sudo mount --bind /var/lib/flc /var/lib/flc
sudo mount -o remount,bind,nosuid,nodev,noexec /var/lib/flc

# 4. Install and start the service
sudo cp /opt/frag-falcon/deploy/flc.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now flc
```

Using the sample `deploy/flc.service` directly (service user, `kvm` group,
`AmbientCapabilities=CAP_NET_ADMIN`) avoids the `setcap` step entirely. The
`nosuid,nodev,noexec` mount should be made permanent in `/etc/fstab`.

## Command-line options

```
--vm-dir=PATH        VM storage directory (required)
--api-host=HOST      API bind address (default 127.0.0.1)
--api-port=PORT      API port (default 7070)
--log-format=FORMAT  text|json (default text)
--log-level=LEVEL    error|warning|info|debug|trace (default info)
```

For the full packaging and hardening rationale, see
[CAVEATS §14 and §15](https://github.com/vaccovecrana/frag-falcon/blob/main/CAVEATS.md).

Next: [Using `flc`](30-usage.md).
