# Overview & Concepts

`frag-falcon` is a minimal hypervisor that runs OCI/Docker images as microVMs. It
pulls an image, extracts it to a host directory, and boots it under
[libkrun](https://github.com/containers/libkrun) — the container root filesystem
is shared over virtiofs, so there are no raw disk images to manage and no
separate kernel/boot configuration.

## Why I built this

Like many others, I relied heavily on Docker containers for my compute workloads,
along with tools like Portainer for orchestration. However, I ran into issues with
Podman's preferred network backend, Netavark, particularly in maintaining
[stable DHCP assignments](https://github.com/containers/netavark/issues/811) for
containers that had to be reachable from my internal network.

That pushed me to ask whether I could move away from container runtimes
altogether. Instead of a second layer of container and networking machinery, I
explored running my Docker images and storage volumes as VMs. A minimalist,
single-binary hypervisor seemed like a natural fit for the experiment, and
pairing it with well-known Linux networking primitives — bridges and tap devices
— let me cut down the number of moving pieces.

`flc` started as a one-month experiment to see whether I could move from
container-based virtualization to a microVM-based approach. While it may not suit
everyone, it proved effective in my scenario, and it may help others facing the
same trade-offs. The original experiment used Firecracker; this version runs the
same model on libkrun, which gives a simpler boot path and a shared-filesystem
rootfs.

## Key concepts

- **Stack**: a compose-style set of services, stored as JSON on disk. The UI
  converts YAML to JSON client-side.
- **Service**: one microVM. Each service has an image, optional
  resources (vCPUs/RAM), volumes, environment, and an entrypoint/command.
- **Rootfs as a host directory**: an image is extracted into a per-service
  directory and shared into the guest over virtiofs. Volumes are host directories
  bind-mounted into that rootfs — no disk-image tooling.
- **Restart policy**: `restart: always | unless-stopped | on-failure | no`
  governs what the supervisor does when a guest exits.
- **Rootless**: `flc` runs as an unprivileged user. The only privileged pieces are
  a file capability on the per-VM launcher and membership in the `kvm` group —
  delegated to the OS once, not reimplemented in the hypervisor.

The stack model is a deliberately small subset of Docker Compose. See
[CAVEATS §11](https://github.com/vaccovecrana/frag-falcon/blob/main/CAVEATS.md)
for exactly what is and isn't supported.

Start with [Deployment](20-deployment.md), then [Using `flc`](30-usage.md).
