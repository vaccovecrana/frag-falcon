import {useEffect, useState} from "preact/hooks"

const FfVersion = () => {
  const [version, setVersion] = useState("")

  useEffect(() => {
    fetch("/version")
      .then(r => r.text())
      .then(v => setVersion(v.trim()))
      .catch(() => setVersion(""))
  }, [])

  return <span class="vf-muted">v{version}</span>
}

export default FfVersion
