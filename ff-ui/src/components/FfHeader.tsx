import {uiRoot} from "@ui/routes"
import FfVersion from "@ui/components/FfVersion"

const FfHeader = () => {
  return (
    <header class="vf-header">
      <div class="vf-header-inner">
        <a href={uiRoot} class="vf-brand">
          <img src="/favicon.svg" alt="frag-falcon"/>
          <span>frag-falcon <span class="vf-brand-sub">Hypervisor</span></span>
        </a>
        <nav class="vf-nav">
          <FfVersion />
        </nav>
      </div>
    </header>
  )
}

export default FfHeader
