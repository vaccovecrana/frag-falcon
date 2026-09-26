import * as React from "preact/compat"
// import { useContext } from "preact/hooks"

import { FgIcnVm, FgLogo } from "./FgIcons"
import { uiRoot } from "@ff/ui"
import FgVersion from "./FgVersion"
// import { FgContext } from "@ff/store"

const FgMenuLeft = () => {
  // const {state} = useContext(FgContext)
  return (
    <div id="menuLeft" class="p16">
      <div class="txc">
        <div class="pl16">
          <div class="row align-center">
            <div class="col xs-2 sm-2 md-2 lg-2 xl-2">
              <FgLogo maxHeight={72} />
            </div>
            <div class="col auto txl pl16">
              frag-falcon
            </div>
          </div>
        </div>
        <div class="m8 pt8">
          <a href={uiRoot} class="btn small secondary block">
            <FgIcnVm maxHeight={24} /> &nbsp; VMs
          </a>
        </div>
        <div class="m8 pt8 txc">
          <FgVersion />
        </div>
      </div>
    </div>
  )
}

export default FgMenuLeft
