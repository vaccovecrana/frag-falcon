import * as ReactDOM from "preact/compat"
import Router from "preact-router"
import {useReducer} from "preact/hooks"

import {initialState, UiContext, UiReducer} from "@ui/store"
import FfHeader from "@ui/components/FfHeader"
import FfLock from "@ui/components/FfLock"
import FfStackList from "@ui/routes/FfStackList"
import FfStackDetail from "@ui/routes/FfStackDetail"
import FfStackEdit from "@ui/routes/FfStackEdit"

const FfShell = () => {
  const [state, dispatch] = useReducer(UiReducer, initialState)
  return (
    <UiContext.Provider value={{state, dispatch}}>
      <FfLock>
        <div class="vf-shell">
          <FfHeader/>
          <main class="vf-main">
            <Router>
              {/* @ts-ignore preact-router default route prop */}
              <FfStackList default/>
              <FfStackEdit path="/stack/new"/>
              <FfStackDetail path="/stack/:stackId"/>
              <FfStackEdit path="/stack/:stackId/edit"/>
            </Router>
          </main>
        </div>
      </FfLock>
    </UiContext.Provider>
  )
}

const app = document.getElementById("app")
if (app) {
  ReactDOM.render(<FfShell/>, app)
}
