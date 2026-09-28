import {RenderableProps} from "preact"
import {useContext, useEffect, useRef} from "preact/hooks"
import {UiContext, usrMsgClear} from "@ui/store"

const AUTO_DISMISS_MS = 6000

const FfLock = (props: RenderableProps<{}>) => {
  const {dispatch: d, state} = useContext(UiContext)
  const msg = state.lastMessage
  const timer = useRef<number | undefined>(undefined)

  useEffect(() => {
    window.clearTimeout(timer.current)
    if (msg && msg.severity === "info") {
      timer.current = window.setTimeout(() => usrMsgClear(d), AUTO_DISMISS_MS)
    }
    return () => window.clearTimeout(timer.current)
  }, [msg])

  return (
    <div>
      {props.children}
      {state.uiLocked && (
        <div class="vf-lock-overlay">
          <div class="vf-lock-spinner"/>
        </div>
      )}
      {msg && (
        <div class={`vf-toast vf-toast--${msg.severity}`} role="alert">
          <span class="vf-toast-msg">{msg.message}</span>
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
