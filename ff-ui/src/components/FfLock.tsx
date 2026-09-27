import {RenderableProps} from "preact"
import {useContext} from "preact/hooks"
import {UiContext, usrMsgClear} from "@ui/store"

const resolveMessage = (error: any): string => {
  if (typeof error === "string") {
    return error
  } else if (error && typeof error.error === "string") {
    return error.error
  } else if (error && typeof error.message === "string") {
    return error.message
  }
  return "An unknown error occurred"
}

const FfLock = (props: RenderableProps<{}>) => {
  const {dispatch: d, state} = useContext(UiContext)
  return (
    <div>
      {props.children}
      {state.uiLocked && (
        <div class="vf-lock-overlay">
          <div class="vf-lock-spinner"/>
        </div>
      )}
      {state.lastMessage && (
        <div class="vf-toast" role="status">
          <span>{resolveMessage(state.lastMessage)}</span>
          <button
            class="vf-toast-close"
            aria-label="Close"
            onClick={() => usrMsgClear(d)}
          >
            ×
          </button>
        </div>
      )}
    </div>
  )
}

export default FfLock
