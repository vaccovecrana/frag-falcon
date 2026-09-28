import { useEffect, useRef, useState } from "preact/hooks"
import { RoutableProps } from "preact-router"
import { parse, stringify } from "yaml"
import { apiV1BrGet, apiV1StackIdGet, apiV1StackPost, FgService, FgStack } from "@ui/rpc"
import { uiRoot, VmIdNew } from "@ui/routes"
import { usrError } from "@ui/store"
import FfServiceCard from "@ui/components/FfServiceCard"

const ID_PATTERN = /^[A-Za-z0-9-]+$/

/**
 * UI-only service entry: a stable key decoupled from the service name so that
 * renaming a service does not remount its DOM (which would drop input focus).
 * Entries are converted to/from the {@code Map<name, FgService>} wire shape only
 * at boundaries (load, YAML parse, save/render).
 */
type Entry = { key: string, name: string, service: FgService }

let keySeq = 0
const nextKey = () => `svc-${++keySeq}`

const toEntries = (services: any): Entry[] => {
  const pairs: [string, FgService][] = services instanceof Map
    ? [...services.entries()]
    : Object.entries(services || {}) as [string, FgService][]
  return pairs.map(([name, service]) => ({ key: nextKey(), name, service }))
}

const toStack = (id: string, bridge: string, entries: Entry[]): FgStack => ({
  id,
  bridge: bridge || undefined,
  // ronove's TS types use Map, but the wire format is a plain JSON object
  // (JSON.stringify(new Map()) === "{}"), so send an object and cast.
  services: Object.fromEntries(entries.map(e => [e.name, e.service])) as any,
})

const yamlOf = (id: string, bridge: string, entries: Entry[]): string => {
  const obj: any = {}
  if (id) obj.id = id
  if (bridge) obj.bridge = bridge
  obj.services = Object.fromEntries(entries.map(e => [e.name, e.service]))
  return stringify(obj)
}

const FfStackEdit = (props: RoutableProps & { stackId?: string }) => {
  const isNew = !props.stackId || props.stackId === VmIdNew
  const id = isNew ? "" : props.stackId!

  const [stackId, setStackId] = useState(id)
  const [bridge, setBridge] = useState("")
  const [entries, setEntries] = useState<Entry[]>([])
  const [yamlText, setYamlText] = useState("")
  const [yamlError, setYamlError] = useState("")
  const [formError, setFormError] = useState("")
  const [bridges, setBridges] = useState<string[]>([])
  const [processing, setProcessing] = useState(false)

  const ready = useRef(false)

  useEffect(() => {
    apiV1BrGet().then(setBridges).catch(() => {})
    const refreshYaml = (sid: string, br: string, es: Entry[]) => {
      setYamlText(yamlOf(sid, br, es))
      ready.current = true
    }
    if (isNew) {
      const init: Entry[] = [{
        key: nextKey(), name: "app",
        service: { image: "docker.io/library/alpine:latest", restart: "unless-stopped", command: ["/bin/sh", "-c", "sleep 3600"] },
      }]
      setEntries(init)
      refreshYaml("", "", init)
    } else {
      apiV1StackIdGet(id).then(s => {
        const es = toEntries(s.services)
        setStackId(s.id || id)
        setBridge(s.bridge || "")
        setEntries(es)
        refreshYaml(s.id || id, s.bridge || "", es)
      }).catch(e => usrError(e, () => {}))
    }
  }, [id, isNew])

  // Form changes: recompute YAML from the current entries.
  const renderYaml = (sid: string, br: string, es: Entry[]) => setYamlText(yamlOf(sid, br, es))

  const addService = () => {
    const es = [...entries, { key: nextKey(), name: `service-${entries.length + 1}`, service: { image: "", restart: "unless-stopped" } }]
    setEntries(es)
    renderYaml(stackId, bridge, es)
  }

  const renameEntry = (key: string, next: string) => {
    const es = entries.map(e => e.key === key ? { ...e, name: next } : e)
    setEntries(es)
    renderYaml(stackId, bridge, es)
  }

  const patchEntry = (key: string, patch: Partial<FgService>) => {
    const es = entries.map(e => e.key === key ? { ...e, service: { ...e.service, ...patch } } : e)
    setEntries(es)
    renderYaml(stackId, bridge, es)
  }

  const removeEntry = (key: string) => {
    const es = entries.filter(e => e.key !== key)
    setEntries(es)
    renderYaml(stackId, bridge, es)
  }

  // YAML -> form: reuse existing entry keys by position so inputs keep focus.
  const onYamlEdit = (text: string) => {
    setYamlText(text)
    setFormError("")
    try {
      const obj = parse(text) || {}
      setStackId(obj.id || "")
      setBridge(obj.bridge || "")
      const names = Object.keys(obj.services || {})
      setEntries(names.map((name, i) => ({
        key: entries[i]?.key || nextKey(),
        name,
        service: (obj.services as any)[name] || {},
      })))
      setYamlError("")
    } catch (e: any) {
      setYamlError(e.message || String(e))
    }
  }

  const save = (thenStart: boolean) => {
    if (yamlError) {
      usrError(`Fix the YAML error first: ${yamlError}`, () => {})
      return
    }
    if (isNew) {
      const wantId = stackId.trim()
      if (!wantId) {
        setFormError("A stack id is required (letters, numbers and dash only).")
        return
      }
      if (!ID_PATTERN.test(wantId)) {
        setFormError(`Invalid stack id [${wantId}]: letters, numbers and dash only.`)
        return
      }
    }
    if (entries.length === 0) {
      setFormError("Add at least one service before saving.")
      return
    }
    for (const e of entries) {
      if (!e.name.trim()) {
        setFormError("Service names cannot be empty.")
        return
      }
      if (!e.service.image?.trim()) {
        setFormError(`Service [${e.name}] needs an image.`)
        return
      }
    }
    setFormError("")
    setProcessing(true)
    const payload = toStack(stackId.trim(), bridge, entries)
    apiV1StackPost(payload)
      .then(async saved => {
        if (thenStart) {
          await fetch("/api/v1/stack/start", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ stackId: saved.id }),
          })
        }
      })
      .then(() => window.location.replace(uiRoot))
      .catch(e => usrError(e, () => {}))
      .finally(() => setProcessing(false))
  }

  return (
    <section>
      <a class="vf-back" href={uiRoot}>← Stacks</a>
      <div class="vf-hero-row">
        <div>
          <h1>{isNew ? "New stack" : `Edit ${id}`}</h1>
        </div>
      </div>

      <div class="vf-hero-actions vf-mb-4">
        <button class="vf-pill vf-pill--accent" disabled={processing} onClick={() => save(true)}>Deploy</button>
        <button class="vf-pill" disabled={processing} onClick={() => save(false)}>Save</button>
      </div>

      {formError && <div class="vf-error vf-mb-4">{formError}</div>}
      {yamlError && <div class="vf-error vf-mb-4">{yamlError}</div>}

      <div class="ff-grid-2" style="max-width:720px">
        <div class="ff-field">
          <label class="vf-form-label">Stack id</label>
          <input
            class="ff-input"
            placeholder="my-stack"
            disabled={!isNew}
            value={stackId}
            onInput={(e: any) => {
              setStackId(e.target.value)
              renderYaml(e.target.value, bridge, entries)
            }}
          />
          <div class="vf-form-text vf-muted">Letters, numbers and dash only. Cannot be changed later.</div>
        </div>
        <div class="ff-field">
          <label class="vf-form-label">Bridge</label>
          <select
            class="ff-input"
            value={bridge}
            onChange={(e: any) => {
              setBridge(e.target.value)
              renderYaml(stackId, e.target.value, entries)
            }}
          >
            <option value="">(none)</option>
            {bridges.map(b => <option value={b}>{b}</option>)}
          </select>
          <div class="vf-form-text vf-muted">Linux bridge the stack's VMs attach to.</div>
        </div>
      </div>

      <div class="ff-edit">
        <div>
          <h2 class="vf-section-title">Services</h2>
          {entries.map(e => (
            <FfServiceCard
              key={e.key}
              name={e.name}
              service={e.service || {}}
              images={[]}
              canRemove={entries.length > 1}
              onRename={(next) => renameEntry(e.key, next)}
              onChange={(patch) => patchEntry(e.key, patch)}
              onRemove={() => removeEntry(e.key)}
            />
          ))}
          <button class="vf-pill" onClick={addService}>+ Add service</button>
        </div>

        <div>
          <h2 class="vf-section-title">YAML</h2>
          <textarea
            class="ff-yaml"
            spellcheck={false}
            rows={28}
            value={yamlText}
            onInput={(e: any) => onYamlEdit(e.target.value)}
          />
        </div>
      </div>
    </section>
  )
}

export default FfStackEdit
