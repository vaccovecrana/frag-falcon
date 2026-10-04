import {Context, createContext} from "preact"

export type ToastSeverity = "error" | "info"

export interface Toast {
  message: string
  severity: ToastSeverity
}

export interface UiState {
  uiLocked: boolean
  lastMessage: Toast | undefined
}

export type UiDispatch = (action: UiAction) => void

export interface UiStore {
  state: UiState
  dispatch: UiDispatch
}

export type UiAction =
  | { type: "lockUi", payload: boolean }
  | { type: "usrMsg", payload: Toast }
  | { type: "usrMsgClear" }

export const hit = (act: UiAction, d: UiDispatch): Promise<void> => {
  d(act)
  return Promise.resolve()
}

export const lockUi = (locked: boolean, d: UiDispatch) => hit({type: "lockUi", payload: locked}, d)
export const usrInfo = (message: string, d: UiDispatch) =>
  hit({type: "usrMsg", payload: {message, severity: "info"}}, d)
export const usrError = (message: string, d: UiDispatch) =>
  hit({type: "usrMsg", payload: {message, severity: "error"}}, d)
export const usrMsgClear = (d: UiDispatch) => hit({type: "usrMsgClear"}, d)

/**
 * Deduplicating error reporter for recurring background polls. The returned
 * function toasts only when the failure message changes; a `null` clears the
 * last-seen message (call it on a successful tick) so a later identical failure
 * is surfaced again. This keeps a sustained outage to a single toast instead of
 * one per poll interval.
 */
export const dedupeError = (d: UiDispatch) => {
  let last: string | undefined
  return (message: string | null): void => {
    if (message === null) {
      last = undefined
      return
    }
    if (message === last) return
    last = message
    usrError(message, d)
  }
}

export const UiReducer = (state0: UiState, action: UiAction): UiState => {
  switch (action.type) {
    case "usrMsg":
      return {...state0, lastMessage: action.payload}
    case "usrMsgClear":
      return {...state0, lastMessage: undefined}
    case "lockUi":
      return {...state0, uiLocked: action.payload}
  }
}

export const initialState: UiState = {
  lastMessage: undefined,
  uiLocked: false
}

export const UiContext: Context<UiStore> = createContext({
  state: initialState, dispatch: (_action: UiAction) => {
  }
})
