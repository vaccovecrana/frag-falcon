import {FgService} from "@ui/rpc"
import FfArrayInput from "@ui/components/FfArrayInput"

const RESTART_POLICIES = ["always", "unless-stopped", "on-failure", "no"]

/**
 * Per-service form card bound directly to the shared FgService object, so
 * edits here and in the YAML editor stay in sync (dockge-style two-way edit).
 */
const FfServiceCard = (
  {name, service, images, onRename, onRemove, canRemove}:
  {
    name: string, service: FgService, images: string[],
    onRename: (next: string) => void, onRemove: () => void, canRemove: boolean,
  },
) => {
  return (
    <div class="vf-panel mb-4">
      <div class="ff-row">
        <input
          class="ff-input ff-service-name"
          value={name}
          onInput={(e: any) => onRename(e.target.value)}
        />
        {canRemove && (
          <button class="ff-btn-danger" onClick={onRemove} aria-label="Remove service">×</button>
        )}
      </div>

      <div class="ff-field">
        <label class="form-label">Image</label>
        <input
          class="ff-input"
          list="ff-images"
          placeholder="docker.io/library/alpine:latest"
          value={service.image || ""}
          onInput={(e: any) => service.image = e.target.value}
        />
        <datalist id="ff-images">
          {images.map(img => <option value={img}/>)}
        </datalist>
      </div>

      <div class="ff-field">
        <label class="form-label">Restart policy</label>
        <select
          class="ff-input"
          value={service.restart || "unless-stopped"}
          onChange={(e: any) => service.restart = e.target.value}
        >
          {RESTART_POLICIES.map(p => <option value={p}>{p}</option>)}
        </select>
      </div>

      <div class="ff-grid-2">
        <div class="ff-field">
          <label class="form-label">vCPUs</label>
          <input
            class="ff-input" type="number" min={1}
            value={service.resources?.vcpus ?? 1}
            onInput={(e: any) => {
              const n = parseInt(e.target.value || "1", 10)
              service.resources = {...service.resources, vcpus: n}
            }}
          />
        </div>
        <div class="ff-field">
          <label class="form-label">RAM (MiB)</label>
          <input
            class="ff-input" type="number" min={128} step={128}
            value={service.resources?.ramMib ?? 512}
            onInput={(e: any) => {
              const n = parseInt(e.target.value || "512", 10)
              service.resources = {...service.resources, ramMib: n}
            }}
          />
        </div>
      </div>

      <FfArrayInput service={service} name="volumes" displayName="Volumes" placeholder="HOST:GUEST[:ro]"/>
      <FfArrayInput service={service} name="environment" displayName="Environment" placeholder="KEY=VALUE"/>
      <FfArrayInput service={service} name="entrypoint" displayName="Entrypoint" placeholder="/bin/sh"/>
      <FfArrayInput service={service} name="command" displayName="Command" placeholder="-c"/>
      <FfArrayInput service={service} name="depends_on" displayName="Depends on" placeholder="service name"/>
    </div>
  )
}

export default FfServiceCard
