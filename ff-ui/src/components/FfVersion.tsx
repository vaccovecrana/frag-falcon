import {useContext, useEffect, useState} from "preact/hooks"
import {apiV1HostGet} from "@ui/rpc"
import {UiContext, usrError} from "@ui/store"
import {messageOf} from "@ui/api"

const FfVersion = () => {
  const [version, setVersion] = useState("")
  const {dispatch} = useContext(UiContext)

  useEffect(() => {
    fetch("/version")
      .then(r => r.text())
      .then(v => setVersion(v.trim()))
      .catch(e => {
        setVersion("")
        usrError(messageOf(e), dispatch)
      })
  }, [])

  useEffect(() => {
    apiV1HostGet()
      .then(r => {
        if (r.name) {
          document.title = `frag-falcon · ${r.name}`
        }
      })
      .catch(e => usrError(messageOf(e), dispatch))
  }, [])

  return <span class="ff-muted">v{version}</span>
}

export default FfVersion
