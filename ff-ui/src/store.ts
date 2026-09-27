import {Context, createContext} from "preact"

export interface UiState {
  uiLocked: boolean
  lastMessage: any
}

export type UiDispatch = (action: UiAction) => void

export interface UiStore {
  state: UiState
  dispatch: UiDispatch
}

export type UiAction =
  | { type: "lockUi", payload: boolean }
  | { type: "usrMsg", payload: string }
  | { type: "usrMsgClear" }

export const hit = (act: UiAction, d: UiDispatch): Promise<void> => {
  d(act)
  return Promise.resolve()
}

export const lockUi = (locked: boolean, d: UiDispatch) => hit({type: "lockUi", payload: locked}, d)
export const usrInfo = (payload: string, d: UiDispatch) => hit({type: "usrMsg", payload}, d)
export const usrError = (payload: any, d: UiDispatch) => hit({type: "usrMsg", payload}, d)
export const usrMsgClear = (d: UiDispatch) => hit({type: "usrMsgClear"}, d)

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
