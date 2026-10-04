import {useContext, useEffect, useRef, useState} from "preact/hooks"
import {apiV1StackGet, FgStackStatus} from "@ui/rpc"
import {uiStack, uiStackNew} from "@ui/routes"
import {dedupeError, UiContext} from "@ui/store"
import {messageOf} from "@ui/api"
import FfStatus from "@ui/components/FfStatus"

const FfStackList = () => {
  const [stacks, setStacks] = useState<FgStackStatus[] | undefined>()
  const {dispatch} = useContext(UiContext)
  const pollError = useRef(dedupeError(dispatch))

  useEffect(() => {
    const load = () => apiV1StackGet()
      .then(r => {
        setStacks(r.stacks || [])
        pollError.current(null)
      })
      .catch(e => {
        setStacks([])
        pollError.current(messageOf(e))
      })
    load()
    const t = setInterval(load, 2000)
    return () => clearInterval(t)
  }, [])

  const servicesOf = (s: FgStackStatus) =>
    s.services ? Object.values(s.services as any) : []

  return (
    <section>
      <div class="ff-hero-row">
        <div>
          <h1>Stacks</h1>
          <p>Container microVM stacks managed by this hypervisor.</p>
        </div>
      </div>
      <div class="ff-hero-actions ff-mb-4">
        <a class="ff-pill ff-pill--accent" href={uiStackNew}>+ New stack</a>
      </div>

      {!stacks ? (
        <div class="ff-grid">
          {[0, 1, 2].map(i => <div key={i} class="ff-skeleton ff-skeleton-card"/>)}
        </div>
      ) : stacks.length === 0 ? (
        <div class="ff-empty">No stacks defined yet.</div>
      ) : (
        <div class="ff-grid">
          {stacks.map(s => {
            const services = servicesOf(s)
            const running = services.filter((x: any) => x.state === "running").length
            return (
              <article class="ff-card">
                <a href={uiStack(s.id)}>
                  <div class="ff-card-body">
                    <div class="ff-row">
                      <div class="ff-card-title">{s.id}</div>
                      <FfStatus status={s.state}/>
                    </div>
                    <div class="ff-card-sub">
                      {running}/{services.length} services running
                    </div>
                    <div class="ff-card-foot">
                      <small>{services.map((x: any) => x.service).join(", ") || "—"}</small>
                    </div>
                  </div>
                </a>
              </article>
            )
          })}
        </div>
      )}
    </section>
  )
}

export default FfStackList
