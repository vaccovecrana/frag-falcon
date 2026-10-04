import {useContext, useEffect, useRef, useState} from "preact/hooks"
import {RoutableProps} from "preact-router"
import {
  apiV1StackGet,
  apiV1StackIdDelete,
  apiV1StackIdPatch,
  apiV1StackLogsPost,
  apiV1StackStartPost,
  apiV1StackStopPost,
  FgStackStatus,
} from "@ui/rpc"
import {uiRoot, uiStackEdit} from "@ui/routes"
import {dedupeError, UiContext, usrError} from "@ui/store"
import {messageOf, unwrap} from "@ui/api"
import FfStatus from "@ui/components/FfStatus"
import FfLogViewer from "@ui/components/FfLogViewer"

const FfStackDetail = (props: RoutableProps & { stackId?: string }) => {
  const id = props.stackId!
  const {dispatch} = useContext(UiContext)
  const [status, setStatus] = useState<FgStackStatus | undefined>()
  const [logs, setLogs] = useState<Record<string, string> | undefined>()
  const [processing, setProcessing] = useState(false)
  const pollError = useRef(dedupeError(dispatch))

  const fetchLogs = () =>
    apiV1StackLogsPost({stackId: id})
      .then(unwrap)
      .then(r => {
        setLogs(r.logs as any)
        pollError.current(null)
      })
      .catch(e => pollError.current(messageOf(e)))

  useEffect(() => {
    let lastRunning = false
    const load = () => {
      apiV1StackGet()
        .then(r => {
          const st = r.stacks?.find(s => s.id === id)
          setStatus(st)
          pollError.current(null)
          const services = st?.services ? Object.values(st.services as any) : []
          const anyRunning = services.some((s: any) => s.state === "running")
          // Poll logs while running, plus one final fetch when it stops.
          if (anyRunning || lastRunning) {
            fetchLogs()
          }
          lastRunning = anyRunning
        })
        .catch(e => pollError.current(messageOf(e))) // background poll: dedupe toasts
    }
    load()
    const t = setInterval(load, 2000)
    return () => clearInterval(t)
  }, [id])

  const services = status?.services ? Object.values(status.services as any) : []
  const running = services.filter((s: any) => s.state === "running").length
  const provisioning = services.some((s: any) => s.state === "provisioning" || s.state === "starting")
  const canStart = !processing && !provisioning && running === 0
  const canStop = !processing && !provisioning && running > 0
  const canUpdate = !processing && !provisioning && services.length > 0
    && services.every((s: any) => s.state !== "running")
  const idle = !processing && !provisioning

  const run = (fn: () => Promise<any>) => {
    setProcessing(true)
    fn().catch(e => usrError(messageOf(e), dispatch)).finally(() => setProcessing(false))
  }

  return (
    <section>
      <a href={uiRoot}>← Stacks</a>

      <div class="ff-hero-row">
        <h1>{id}</h1>
        <div class="ff-mb-4px">
          <FfStatus status={status?.state}/>
        </div>
      </div>

      <div class="ff-hero-actions ff-mb-4">
        <button class="ff-pill ff-pill--accent" disabled={!canStart}
                title={canStart ? "" : "Already running or provisioning"}
                onClick={() => run(() => apiV1StackStartPost({stackId: id}).then(unwrap))}>Start
        </button>
        <button class="ff-pill" disabled={!canStop}
                title={canStop ? "" : "Nothing is running"}
                onClick={() => run(() => apiV1StackStopPost({stackId: id}).then(unwrap))}>Stop
        </button>
        <button class="ff-pill" disabled={!canUpdate}
                title={canUpdate ? "" : "Stop the stack first"}
                onClick={() => run(() => apiV1StackIdPatch(id).then(unwrap))}>Update
        </button>
        <a class="ff-pill" href={uiStackEdit(id)}>Edit</a>
        <button class="ff-pill" disabled={!idle}
                onClick={() => {
                  if (!window.confirm(`Delete stack "${id}"? This stops and removes its services.`)) return
                  run(() => apiV1StackIdDelete(id)
                    .then(unwrap)
                    .then(() => window.location.replace(uiRoot)))
                }}>Delete
        </button>
      </div>

      <h2 class="ff-section-title">Services</h2>
      {services.length === 0 ? (
        <div class="ff-empty">No services.</div>
      ) : (
        <div class="ff-panel-grid ff-mb-4">
          {services.map((s: any) => (
            <div class="ff-panel" key={s.service}>
              <div class="ff-row">
                <div class="ff-card-title">{s.service}</div>
                <FfStatus status={s.state}/>
              </div>
              <div class="ff-card-meta">
                id <code>{s.id}</code>{s.pid > 0 ? <> · pid <code>{s.pid}</code></> : null}
              </div>
              {s.provision && s.provision.layersTotal > 0 && s.state === "provisioning" && (
                <div class="ff-mt8">
                  <div class="ff-progress">
                    <div
                      style={{width: `${Math.round(100 * s.provision.layersDone / s.provision.layersTotal)}%`}}/>
                  </div>
                  <small class="ff-muted">
                    Layer {s.provision.layersDone}/{s.provision.layersTotal}
                    {s.provision.bytesTotal > 0 ? ` · ${Math.round(100 * s.provision.bytesDone / s.provision.bytesTotal)}%` : ""}
                  </small>
                </div>
              )}
              {s.error && <div class="ff-error ff-mt8">{s.error}</div>}
              {s.exposedPorts && s.exposedPorts.length > 0 && (
                <div class="ff-mt8">
                  {s.exposedPorts.map(
                    (p: string) => (
                      <span class="ff-intent ff-intent-port ff-me-1">{p}</span>
                    )
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {logs && Object.keys(logs).length > 0 && (
        <>
          <h2 class="ff-section-title">Logs</h2>
          <div class="ff-log-grid">
            {Object.entries(logs).map(([svc, data]) => (
              <div key={svc}>
                <div class="ff-card-meta ff-mb-2">{svc}</div>
                <FfLogViewer logData={data}/>
              </div>
            ))}
          </div>
        </>
      )}
    </section>
  )
}

export default FfStackDetail
