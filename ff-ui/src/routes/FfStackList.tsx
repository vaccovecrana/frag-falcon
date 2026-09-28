import {useEffect, useState} from "preact/hooks"
import {apiV1StackGet, FgStackStatus} from "@ui/rpc"
import {uiStack, uiStackNew} from "@ui/routes"
import FfStatus from "@ui/components/FfStatus"

const FfStackList = () => {
  const [stacks, setStacks] = useState<FgStackStatus[] | undefined>()

  useEffect(() => {
    const load = () => apiV1StackGet().then(r => setStacks(r.stacks || [])).catch(() => setStacks([]))
    load()
    const t = setInterval(load, 2000)
    return () => clearInterval(t)
  }, [])

  const servicesOf = (s: FgStackStatus) =>
    s.services ? Object.values(s.services as any) : []

  return (
    <section>
      <div class="vf-hero-row">
        <div>
          <h1>Stacks</h1>
          <p>Container microVM stacks managed by this hypervisor.</p>
        </div>
      </div>
      <div class="vf-hero-actions vf-mb-4">
        <a class="vf-pill vf-pill--accent" href={uiStackNew}>+ New stack</a>
      </div>

      {!stacks ? (
        <div class="vf-grid">
          {[0, 1, 2].map(i => <div key={i} class="vf-skeleton vf-skeleton-card"/>)}
        </div>
      ) : stacks.length === 0 ? (
        <div class="vf-empty">No stacks defined yet.</div>
      ) : (
        <div class="vf-grid">
          {stacks.map(s => {
            const services = servicesOf(s)
            const running = services.filter((x: any) => x.state === "running").length
            return (
              <article class="vf-card">
                <a href={uiStack(s.id)}>
                  <div class="vf-card-body">
                    <div class="ff-row">
                      <div class="vf-card-title">{s.id}</div>
                      <FfStatus status={s.state}/>
                    </div>
                    <div class="vf-card-sub">
                      {running}/{services.length} services running
                    </div>
                    <div class="vf-card-foot">
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
