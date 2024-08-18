import "nord-ui"
import "nord-ui/dist/dark-theme.css"

import "../res/ui-lock.css"
import "../res/main.scss"

import * as React from "preact/compat"
import * as ReactDOM from "preact/compat"
import { useReducer } from "preact/hooks"
import Router from 'preact-router'

import { FgContext, FgReducer, initialState } from "@ff/store"
import { FgLock, FgMenuLeft, FgMenuTop } from "@ff/components"
import FgVmList from "@ff/routes/FgVmList"
import FgVmLogs from "@ff/routes/FgVmLogs"
import { uiVmEdit, uiVmIdLogs } from "@ff/ui"
import FgVmEdit from "@ff/routes/FgVmEdit"

class FgShell extends React.Component {
  public render() {
    const [state, dispatch] = useReducer(FgReducer, initialState)
    return (
      <FgContext.Provider value={{state, dispatch}}>
        <FgLock>
          <div id="app">
            <div class="row">
              <div class="col md-3 lg-2 xl-2 sm-down-hide">
                <FgMenuLeft />
              </div>
              <div class="col xs-12 sm-12 md-12 md-up-hide">
                <FgMenuTop />
              </div>
              <div class="col xs-12 sm-12 md-9 lg-10 xl-10">
                <div id="appFrame" class="p16">
                  <Router>
                    <FgVmEdit path={uiVmEdit} />
                    <FgVmLogs path={uiVmIdLogs} />
                    <FgVmList default />
                  </Router>
                </div>
              </div>
            </div>
          </div>
        </FgLock>
      </FgContext.Provider>
    )
  }
}

ReactDOM.render(<FgShell />, document.getElementById("root"))
