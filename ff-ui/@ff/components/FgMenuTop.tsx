import * as React from "preact/compat"
import { FgIcnVm, FgLogo } from "./FgIcons"
import { uiRoot } from "@ff/ui"

const FgMenuTop = () => {
  return (
    <div class="mt16">
      <div class="p8">
        <div class="row justify-center align-center">
          <div class="col xs-2 sm-2 md-2 lg-2 xl-2">
            <div class="txc">
              <FgLogo maxHeight={64} />
            </div>
          </div>
          <div class="col auto">
            <div class="txc">
              <div>
                <a href={uiRoot}>
                  <div>
                    <FgIcnVm maxHeight={42} />
                  </div>
                </a>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default FgMenuTop
