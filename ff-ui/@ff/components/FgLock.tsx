import * as React from "preact/compat"
import {RenderableProps} from "preact"
import { useContext } from "preact/hooks"
import { FgContext, usrErrorClear, usrMsgClear } from "@ff/store"

const FgLock = (props: RenderableProps<{}>) => {
  const {dispatch: d, state} = useContext(FgContext)
  if (state && state.lastMessage) {
    alert(JSON.stringify(state.lastMessage, null, 2))
    usrMsgClear(d)
  }
  if (state && state.lastError) {
    console.log(state.lastError)
    usrErrorClear(d)
  }
  return ( // TODO add FgToast error message display
    <div>
      {props.children}
      {state && state.uiLocked ? <div class="uiLock" /> : []}
    </div>
  )
}

export default FgLock
