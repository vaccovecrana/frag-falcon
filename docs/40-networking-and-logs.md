# Networking & Logs

## Networking

Each VM gets a **virtio-net interface backed by a TAP device** attached to a Linux
bridge. You choose the bridge per stack (`bridge:` field); the guest obtains its
address over DHCP.

Requirements and constraints:

- Creating/attaching TAP devices needs **`CAP_NET_ADMIN`**. `flc` refuses to start
  without it (see [Deployment](20-deployment.md)).
- A TAP can be **attached by one process only** (`EBUSY`). `flc` creates the tap
  persistently and hands it to the launcher.
- The bridge must forward traffic; on a plain bridge the tap only gains carrier
  when the VM starts, so DHCP may take a moment. libkrun's guest DHCP client is
  patched to retry (see
  [CAVEATS §1](https://github.com/vaccovecrana/frag-falcon/blob/main/CAVEATS.md)).
- `ports`/`networks` are ignored — each VM owns its bridge IP and its own port
  space. Publishing/NAT is the operator's concern.
- No authentication: keep `flc` on a private LAN segment or behind a VPN
  ([Security](50-security.md)).

See [CAVEATS §3 and §8](https://github.com/vaccovecrana/frag-falcon/blob/main/CAVEATS.md)
for the TAP/bridge details.

## Logs

Each VM's console output is captured by the launcher into a **bounded log ring**:
the launcher keeps the last `--log-lines` lines (default 4096) and rewrites
`vm.log` atomically. The API returns at most that tail, and the launcher flushes
the ring on exit so fast-exiting workloads are not truncated.

The UI polls logs automatically while a stack is running — there is no "Logs"
button to press; the log pane populates and updates on its own. When the stack
stops, the last captured output remains visible.

See [CAVEATS §12](https://github.com/vaccovecrana/frag-falcon/blob/main/CAVEATS.md)
for the log-ring design.

Next: [Security](50-security.md).
