import * as React from "preact/compat"
import { Context, createContext } from "preact"

export interface FgUiState {
  uiLocked: boolean
  lastMessage: any,
  lastError: any
}

export type FgAction =
  | {type: "lockUi", payload: boolean}
  | {type: "usrMsg", payload: string}
  | {type: "usrErr", payload: any}
  | {type: "usrMsgClear"}
  | {type: "usrErrClear"}

export type FgDispatch = (action: FgAction) => void

export interface FgStore {
  state: FgUiState
  dispatch: FgDispatch
}

export const hit = (act: FgAction, d: FgDispatch): Promise<void> => {
  d(act)
  return Promise.resolve()
}

export const lockUi = (locked: boolean, d: FgDispatch) => hit({type: "lockUi", payload: locked}, d)
export const usrInfo = (payload: string, d: FgDispatch) => hit({type: "usrMsg", payload}, d)
export const usrError = (payload: any, d: FgDispatch) => hit({type: "usrErr", payload}, d)
export const usrErrorClear = (d: FgDispatch) => hit({type: "usrErrClear"}, d)
export const usrMsgClear = (d: FgDispatch) => hit({type: "usrMsgClear"}, d)

export const FgReducer: React.Reducer<FgUiState, FgAction> = (state0: FgUiState, action: FgAction): FgUiState => {
  switch (action.type) {
    case "usrMsg": return {...state0, lastMessage: action.payload}
    case "usrMsgClear": return {...state0, lastMessage: undefined}
    case "usrErr": return {...state0, lastError: action.payload}
    case "lockUi": return {...state0, uiLocked: action.payload}
  }
}

export const initialState: FgUiState = {
  lastError: undefined,
  lastMessage: undefined,
  uiLocked: false
}

export const FgContext: Context<FgStore> = createContext({
  state: initialState, dispatch: () => {}
})
