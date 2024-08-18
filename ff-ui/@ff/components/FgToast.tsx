import * as React from "preact/compat"
import { useState, useEffect } from 'preact/hooks'
import { JSX } from 'preact'

type FgToastProps = {
  message: string
  timeMs?: number
}

const FgToast = ({ message, timeMs = 5000 }: FgToastProps): JSX.Element | null => {
  const [visible, setVisible] = useState<boolean>(false)
  const [hidden, setHidden] = useState<boolean>(false)

  useEffect(() => {
    setVisible(true)
    const hideTimer = setTimeout(() => {
      setVisible(false)
      setTimeout(() => setHidden(true), 1000)
    }, timeMs)
    return () => {
      clearTimeout(hideTimer)
    };
  }, [timeMs])

  if (hidden) return null

  return (
    <div class={`toast ${visible ? "show" : ""}`}>
      {message}
    </div>
  )
}

export default FgToast
