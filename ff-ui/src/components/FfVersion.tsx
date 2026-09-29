import {useEffect, useState} from "preact/hooks"
import {apiV1HostGet} from "@ui/rpc"

const FfVersion = () => {
  const [version, setVersion] = useState("")

  useEffect(() => {
    fetch("/version")
      .then(r => r.text())
      .then(v => setVersion(v.trim()))
      .catch(() => setVersion(""))
  }, [])

  useEffect(() => {
    apiV1HostGet()
      .then(r => {
        if (r.name) {
          document.title = `frag-falcon · ${r.name}`
        }
      })
      .catch(() => {})
  }, [])

  return <span class="vf-muted">v{version}</span>
}

export default FfVersion
