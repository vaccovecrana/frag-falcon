import {useEffect, useState} from "preact/hooks"
import {RoutableProps} from "preact-router"
import {parse, stringify} from "yaml"
import {apiV1BrGet, apiV1StackIdGet, apiV1StackPost, FgService, FgStack} from "@ui/rpc"
import {uiRoot, VmIdNew} from "@ui/routes"
import {usrError} from "@ui/store"
import FfServiceCard from "@ui/components/FfServiceCard"

const NEW_STACK: FgStack = {
  id: "",
  services: new Map<string, FgService>([
    ["app", {
      image: "docker.io/library/alpine:latest",
      restart: "unless-stopped",
      command: ["/bin/sh", "-c", "sleep 3600"]
    }],
  ]),
}

/** YAML <-> JSON conversion preserving the map shape ronove expects. */
const toPlain = (stack: FgStack): any => ({
  id: stack.id,
  services: Object.fromEntries((stack.services as any) || []),
})

const fromPlain = (obj: any): FgStack => ({
  id: obj.id,
  services: new Map<string, FgService>(Object.entries(obj.services || {})),
})

const ID_PATTERN = /^[A-Za-z0-9-]+$/

const FfStackEdit = (props: RoutableProps & { stackId?: string }) => {
  const isNew = !props.stackId || props.stackId === VmIdNew
  const id = isNew ? "" : props.stackId!

  const [stack, setStack] = useState<FgStack>(NEW_STACK)
  const [yamlText, setYamlText] = useState(stringify(toPlain(NEW_STACK)))
  const [yamlError, setYamlError] = useState("")
  const [formError, setFormError] = useState("")
  const [bridges, setBridges] = useState<string[]>([])
  const [processing, setProcessing] = useState(false)

  useEffect(() => {
    apiV1BrGet().then(setBridges).catch(() => {
    })
    if (!isNew) {
      apiV1StackIdGet(id).then(s => {
        setStack(s)
        setYamlText(stringify(toPlain(s)))
      }).catch(e => usrError(e, () => {
      }))
    }
  }, [id, isNew])

  // Form -> YAML: whenever the service model changes, re-render YAML.
  const mutate = (fn: (s: FgStack) => void) => {
    const next = fromPlain(toPlain(stack))
    fn(next)
    setStack(next)
    setYamlText(stringify(toPlain(next)))
  }

  // YAML -> form: parse on edit and refresh the model.
  const onYamlEdit = (text: string) => {
    setYamlText(text)
    setFormError("")
    try {
      const obj = parse(text) || {}
      setStack(fromPlain(obj))
      setYamlError("")
    } catch (e: any) {
      setYamlError(e.message || String(e))
    }
  }

  const serviceNames = [...(stack.services as any).keys()] as string[]

  const addService = () => {
    let n = 1
    while ((stack.services as any).has(`service-${n}`)) n++
    mutate(s => serviceMap(s).set(`service-${n}`, {image: "", restart: "unless-stopped"}))
  }

  const renameService = (from: string, to: string) => {
    if (from === to) return
    mutate(s => {
      const m = serviceMap(s)
      const entries = [...m.entries()].map(([k, v]) => [k === from ? to : k, v] as [string, FgService])
      m.clear()
      entries.forEach(([k, v]) => m.set(k, v))
    })
  }

  const removeService = (name: string) => {
    mutate(s => serviceMap(s).delete(name))
  }

  const patchService = (name: string, patch: Partial<FgService>) => {
    mutate(s => {
      const cur = serviceMap(s).get(name) || {}
      serviceMap(s).set(name, {...cur, ...patch} as FgService)
    })
  }

  const save = (thenStart: boolean) => {
    if (yamlError) {
      usrError(`Fix the YAML error first: ${yamlError}`, () => {
      })
      return
    }
    const payload = fromPlain(toPlain(stack))
    const wantId = (payload.id || "").trim()
    if (isNew) {
      if (!wantId) {
        setFormError("A stack id is required (letters, numbers and dash only).")
        return
      }
      if (!ID_PATTERN.test(wantId)) {
        setFormError(`Invalid stack id [${wantId}]: letters, numbers and dash only.`)
        return
      }
      payload.id = wantId
    }
    if (!payload.services || (payload.services as any).size === 0) {
      setFormError("Add at least one service before saving.")
      return
    }
    for (const [name, svc] of (payload.services as any).entries()) {
      if (!name.trim()) {
        setFormError("Service names cannot be empty.")
        return
      }
      if (!(svc as FgService).image?.trim()) {
        setFormError(`Service [${name}] needs an image.`)
        return
      }
    }
    setFormError("")
    setProcessing(true)
    apiV1StackPost(payload)
      .then(async saved => {
        if (thenStart) {
          await fetch("/api/v1/stack/start", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({stackId: saved.id}),
          })
        }
      })
      .then(() => window.location.replace(uiRoot))
      .catch(e => usrError(e, () => {
      }))
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

      <div class="vf-hero-actions mb-4">
        <button class="vf-pill vf-pill--accent" disabled={processing} onClick={() => save(true)}>Deploy</button>
        <button class="vf-pill" disabled={processing} onClick={() => save(false)}>Save</button>
      </div>

      {formError && <div class="vf-error mb-4">{formError}</div>}
      {yamlError && <div class="vf-error mb-4">{yamlError}</div>}

      <div class="ff-field" style="max-width:420px">
        <label class="form-label">Stack id</label>
        <input
          class="ff-input"
          placeholder="my-stack"
          disabled={!isNew}
          value={stack.id || ""}
          onInput={(e: any) => {
            const next = fromPlain(toPlain(stack))
            next.id = e.target.value
            setStack(next)
            setYamlText(stringify(toPlain(next)))
          }}
        />
        <div class="form-text vf-muted">Letters, numbers and dash only. Cannot be changed later.</div>
      </div>

      {bridges.length > 0 && <div class="vf-card-meta mb-4">Bridge: {bridges.join(", ")}</div>}

      <div class="ff-edit">
        <div>
          <h2 class="vf-section-title">Services</h2>
          {serviceNames.map(name => (
            <FfServiceCard
              key={name}
              name={name}
              service={(stack.services as any).get(name)}
              images={[]}
              canRemove={serviceNames.length > 1}
              onRename={(next) => renameService(name, next)}
              onChange={(patch) => patchService(name, patch)}
              onRemove={() => removeService(name)}
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

const serviceMap = (s: FgStack): Map<string, FgService> => s.services as any
