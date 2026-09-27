import {uiRoot} from "@ui/routes"

const FfHeader = () => {
  return (
    <header class="vf-header">
      <div class="vf-header-inner">
        <a href={uiRoot} class="vf-brand">
          <img src="/favicon.svg" alt="frag-falcon"/>
          <span>frag-falcon <span class="vf-brand-sub">Hypervisor</span></span>
        </a>
        <nav class="vf-nav">
          <a class="vf-pill vf-pill--accent" href={uiRoot}>Stacks</a>
        </nav>
      </div>
    </header>
  )
}

export default FfHeader
