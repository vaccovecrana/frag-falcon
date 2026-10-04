import {uiRoot} from "@ui/routes"
import FfVersion from "@ui/components/FfVersion"

const FfHeader = () => {
  return (
    <header class="ff-header">
      <div class="ff-header-inner">
        <a href={uiRoot} class="ff-brand">
          <img src="/favicon.svg" alt="frag-falcon"/>
          <span>frag-falcon <span class="ff-brand-sub">Hypervisor</span></span>
        </a>
        <nav class="ff-nav">
          <FfVersion/>
        </nav>
      </div>
    </header>
  )
}

export default FfHeader
