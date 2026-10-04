#!/usr/bin/env bash
#
# Runs the ff-ui browser E2E suite against a freshly built, unprivileged
# hypervisor on a throwaway --vm-dir + --oci-dir. Builds the UI bundle + app,
# applies the launcher capability, starts the backend, runs `npm run test:e2e`,
# and stops the backend on exit.
#
#   ff-test/e2e.sh                 # full suite (requires virbr0 + /dev/kvm + cap)
#   FF_E2E_BRIDGE=virbr0 ff-test/e2e.sh
#   SHOW_LOG=1 ff-test/e2e.sh      # stream the backend log while running
#   FF_E2E_VM_DIR=... FF_E2E_OCI_DIR=... ff-test/e2e.sh
#
# Prerequisites (see CAVEATS §17): virbr0, /dev/kvm access, and cap_net_admin on
# fg_vmm (`sudo bash ff-jni/setup-caps.sh`). Set SUDOPW to auto-apply caps.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

port="${FF_E2E_PORT:-7070}"
vm_dir="${FF_E2E_VM_DIR:-$(mktemp -d /tmp/ff-e2e-XXXXXX)}"
oci_dir="${FF_E2E_OCI_DIR:-$(mktemp -d /tmp/ff-e2e-oci-XXXXXX)}"
export FF_UI_URL="http://127.0.0.1:${port}"
export FF_E2E_BRIDGE="${FF_E2E_BRIDGE:-virbr0}"
native_dir="$root/ff-jni/build/native"
backend_log="$vm_dir/backend.log"
backend_pid=""

cleanup() {
  if [ -n "$backend_pid" ] && kill -0 "$backend_pid" 2>/dev/null; then
    kill "$backend_pid" 2>/dev/null || true
    wait "$backend_pid" 2>/dev/null || true
  fi
  if [ "${KEEP_VM_DIR:-0}" != "1" ]; then
    rm -rf "$vm_dir" "$oci_dir"
  else
    echo "keeping VM dir: $vm_dir (oci dir: $oci_dir)"
  fi
}
trap cleanup EXIT

apply_caps() {
  local script="$root/ff-jni/setup-caps.sh"
  if [ -n "${SUDOPW:-}" ]; then
    printf '%s\n' "$SUDOPW" | sudo -S -p '' bash "$script"
  else
    echo "note: if tests fail with a capability error, run: sudo bash ff-jni/setup-caps.sh"
    sudo -n bash "$script" 2>/dev/null || true
  fi
}

echo "==> building UI bundle + app"
gradle --console=plain :ff-ui:processResources :flc:installDist -q

echo "==> applying launcher capabilities"
apply_caps

app="$(ls -d "$root"/ff-app/build/install/*/bin/flc | head -1)"
if [ ! -x "$app" ]; then
  echo "error: flc install not found under ff-app/build/install/*/bin" >&2
  exit 1
fi

echo "==> starting backend on :$port (vm-dir=$vm_dir, oci-dir=$oci_dir)"
FF_NATIVE_DIR="$native_dir" "$app" --vm-dir="$vm_dir" --oci-dir="$oci_dir" --api-port="$port" >"$backend_log" 2>&1 &
backend_pid=$!

for _ in $(seq 1 50); do
  if curl -sf -o /dev/null "$FF_UI_URL/"; then
    break
  fi
  if ! kill -0 "$backend_pid" 2>/dev/null; then
    echo "error: backend exited early:" >&2
    cat "$backend_log" >&2
    exit 1
  fi
  sleep 0.2
done

if ! curl -sf -o /dev/null "$FF_UI_URL/"; then
  echo "error: backend did not become ready:" >&2
  cat "$backend_log" >&2
  exit 1
fi

if [ "${SHOW_LOG:-0}" = "1" ]; then
  tail -f "$backend_log" &
  tail_pid=$!
fi

echo "==> running E2E suite"
cd "$root/ff-ui"
npm run test:e2e

if [ -n "${tail_pid:-}" ]; then
  kill "$tail_pid" 2>/dev/null || true
fi
