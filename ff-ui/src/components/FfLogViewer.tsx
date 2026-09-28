import {useEffect, useRef} from "preact/hooks"

const stripAnsiColors = (input: string): string => {
  const ansiRegex = /\x1B\[[0-9;]*m/g
  return input.replace(ansiRegex, "")
}

const FfLogViewer = ({logData}: { logData: string }) => {
  const ref = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    const ta = ref.current
    if (ta) {
      ta.scrollTop = ta.scrollHeight
    }
  }, [logData])

  return (
    <textarea
      class="ff-log"
      ref={ref}
      value={stripAnsiColors(logData || "")}
      placeholder="(no output)"
      rows={12}
      readOnly
      spellcheck={false}
    />
  )
}

export default FfLogViewer
