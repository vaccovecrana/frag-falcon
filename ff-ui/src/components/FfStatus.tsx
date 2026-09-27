const LABELS: Record<string, string> = {
  pending: "Pending",
  provisioning: "Provisioning",
  starting: "Starting",
  running: "Running",
  partial: "Partial",
  stopped: "Stopped",
  failed: "Failed",
}

const FfStatus = ({status}: { status?: string }) => {
  const key = status || "stopped"
  return (
    <span class={`vf-status vf-status-${key}`}>
      {LABELS[key] || key}
    </span>
  )
}

export default FfStatus
