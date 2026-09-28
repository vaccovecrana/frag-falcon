import {useContext, useEffect, useState} from "preact/hooks"
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
import {UiContext, usrError} from "@ui/store"
import {messageOf, unwrap} from "@ui/api"
import FfStatus from "@ui/components/FfStatus"
import FfLogViewer from "@ui/components/FfLogViewer"

const FfStackDetail = (props: RoutableProps & { stackId?: string }) => {
  const id = props.stackId!
  const {dispatch} = useContext(UiContext)
  const [status, setStatus] = useState<FgStackStatus | undefined>()
  const [logs, setLogs] = useState<Record<string, string> | undefined>()
  const [processing, setProcessing] = useState(false)

  useEffect(() => {
    const load = () => {
      apiV1StackGet()
        .then(r => setStatus(r.stacks?.find(s => s.id === id)))
        .catch(() => {
        }) // background poll: stay silent
    }
    load()
    const t = setInterval(load, 2000)
    return () => clearInterval(t)
  }, [id])

  const services = status?.services ? Object.values(status.services as any) : []
  const canUpdate = services.length > 0 && services.every((s: any) => s.state !== "running")

  const run = (fn: () => Promise<any>) => {
    setProcessing(true)
    fn().catch(e => usrError(messageOf(e), dispatch)).finally(() => setProcessing(false))
  }

  return (
    <section>
      <a class="vf-back" href={uiRoot}>← Stacks</a>

      <div class="vf-hero-row">
        <div>
          <h1>{id} <FfStatus status={status?.state}/></h1>
        </div>
      </div>

      <div class="vf-hero-actions vf-mb-4">
        <button class="vf-pill vf-pill--accent" disabled={processing}
                onClick={() => run(() => apiV1StackStartPost({stackId: id}).then(unwrap))}>Start
        </button>
        <button class="vf-pill" disabled={processing}
                onClick={() => run(() => apiV1StackStopPost({stackId: id}).then(unwrap))}>Stop
        </button>
        <button class="vf-pill" disabled={processing || !canUpdate}
                title={canUpdate ? "" : "Stop the stack first"}
                onClick={() => run(() => apiV1StackIdPatch(id).then(unwrap))}>Update
        </button>
        <a class="vf-pill" href={uiStackEdit(id)}>Edit</a>
        <button class="vf-pill" disabled={processing}
                onClick={() => run(() => apiV1StackLogsPost({stackId: id})
                  .then(unwrap)
                  .then(r => setLogs(r.logs as any)))}>Logs
        </button>
        <button class="vf-pill" disabled={processing}
                onClick={() => run(() => apiV1StackIdDelete(id)
                  .then(unwrap)
                  .then(() => window.location.replace(uiRoot)))}>Delete
        </button>
      </div>

      <h2 class="vf-section-title">Services</h2>
      {services.length === 0 ? (
        <div class="vf-empty">No services.</div>
      ) : (
        services.map((s: any) => (
          <div class="vf-panel vf-mb-4" key={s.service}>
            <div class="ff-row">
              <div class="vf-card-title">{s.service}</div>
              <FfStatus status={s.state}/>
            </div>
            <div class="vf-card-meta">
              id <code>{s.id}</code>{s.pid > 0 ? <> · pid <code>{s.pid}</code></> : null}
            </div>
            {s.provision && s.provision.layersTotal > 0 && s.state === "provisioning" && (
              <div class="vf-mt8">
                <div class="vf-progress">
                  <div
                    style={{width: `${Math.round(100 * s.provision.layersDone / s.provision.layersTotal)}%`}}/>
                </div>
                <small class="vf-muted">
                  Layer {s.provision.layersDone}/{s.provision.layersTotal}
                  {s.provision.bytesTotal > 0 ? ` · ${Math.round(100 * s.provision.bytesDone / s.provision.bytesTotal)}%` : ""}
                </small>
              </div>
            )}
            {s.error && <div class="vf-error vf-mt8">{s.error}</div>}
            {s.exposedPorts && s.exposedPorts.length > 0 && (
              <div class="vf-mt8">{s.exposedPorts.map((p: string) => <span
                class="vf-intent vf-intent-fan_funded vf-me-1">{p}</span>)}</div>
            )}
          </div>
        ))
      )}

      {logs && (
        <>
          <h2 class="vf-section-title">Logs</h2>
          {Object.entries(logs).map(([svc, data]) => (
            <div class="vf-mb-4" key={svc}>
              <div class="vf-card-meta vf-mb-2">{svc}</div>
              <FfLogViewer logData={data}/>
            </div>
          ))}
        </>
      )}
    </section>
  )
}

export default FfStackDetail
