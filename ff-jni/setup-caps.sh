#!/usr/bin/env bash
#
# Developer setup: grants the VM launcher the network capabilities it needs to
# create/attach/open TAP devices without root, and the fg_usermap helper the
# CAP_SETUID/CAP_SETGID it needs to map subuid/subgid ranges into the launcher's
# user namespace (so guest images can run as arbitrary uids). Run once, and again
# after every rebuild (`gradle :ff-jni:nativeBuild`).
#
#   sudo bash ff-jni/setup-caps.sh
#
# Production does NOT use this: operators run `deploy/setup.sh`, which installs
# fg_usermap root-owned (0750) and adds the service user's subuid/subgid range.
#
# Without this, networking integration tests skip themselves and the launcher
# falls back to a single-uid mapping (images using non-root uids fail to chown).
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# The launcher is executed from wherever FgNative resolves its home: the Gradle
# build dirs (dev/tests, gradle run) or a flat distribution. Cap every copy that
# exists so any spawn path works.
vmm_candidates=(
  "$here/out/fg_vmm"
  "$here/build/native/fg_vmm"
  "$here/build/resources/main/io/vacco/ff/fg_vmm"
)
usermap_candidates=(
  "$here/out/fg_usermap"
  "$here/build/native/fg_usermap"
  "$here/build/resources/main/io/vacco/ff/fg_usermap"
)

applied=0
for bin in "${vmm_candidates[@]}"; do
  if [ -f "$bin" ]; then
    setcap 'cap_net_admin+ep' "$bin"
    echo "applied cap_net_admin to $bin"
    getcap "$bin"
    applied=1
  fi
done

# fg_usermap needs CAP_SETUID/CAP_SETGID. Restrict execution (0750) so it is not
# a world-executable setuid-class binary; keep it user-owned in dev so the next
# Gradle Sync can still overwrite it.
for bin in "${usermap_candidates[@]}"; do
  if [ -f "$bin" ]; then
    chmod 0750 "$bin"
    setcap 'cap_setuid,cap_setgid+ep' "$bin"
    echo "applied cap_setuid,cap_setgid to $bin"
    getcap "$bin"
  fi
done

if [ "$applied" -eq 0 ]; then
  echo "error: no fg_vmm found; build it first with: make -C $here" >&2
  exit 1
fi
