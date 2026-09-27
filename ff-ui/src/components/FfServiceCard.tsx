import {FgService} from "@ui/rpc"
import FfArrayInput from "@ui/components/FfArrayInput"

const RESTART_POLICIES = ["always", "unless-stopped", "on-failure", "no"]

/**
 * Per-service form card. Field edits are reported through {@code onChange} as a
 * patch, so the editor can rebuild its state (and the YAML view) rather than
 * mutating the shared object in place.
 */
const FfServiceCard = (
  {name, service, images, onRename, onChange, onRemove, canRemove}:
  {
    name: string, service: FgService, images: string[],
    onRename: (next: string) => void,
    onChange: (patch: Partial<FgService>) => void,
    onRemove: () => void, canRemove: boolean,
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
          onInput={(e: any) => onChange({image: e.target.value})}
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
          onChange={(e: any) => onChange({restart: e.target.value})}
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
            onInput={(e: any) => onChange({
              resources: {vcpus: parseInt(e.target.value || "1", 10), ramMib: service.resources?.ramMib ?? 512},
            })}
          />
        </div>
        <div class="ff-field">
          <label class="form-label">RAM (MiB)</label>
          <input
            class="ff-input" type="number" min={128} step={128}
            value={service.resources?.ramMib ?? 512}
            onInput={(e: any) => onChange({
              resources: {vcpus: service.resources?.vcpus ?? 1, ramMib: parseInt(e.target.value || "512", 10)},
            })}
          />
        </div>
      </div>

      <FfArrayInput service={service} name="volumes" displayName="Volumes" placeholder="HOST:GUEST[:ro]"
                    setList={(v) => onChange({volumes: v})}/>
      <FfArrayInput service={service} name="environment" displayName="Environment" placeholder="KEY=VALUE"
                    setList={(v) => onChange({environment: v})}/>
      <FfArrayInput service={service} name="entrypoint" displayName="Entrypoint" placeholder="/bin/sh"
                    setList={(v) => onChange({entrypoint: v})}/>
      <FfArrayInput service={service} name="command" displayName="Command" placeholder="-c"
                    setList={(v) => onChange({command: v})}/>
      <FfArrayInput service={service} name="depends_on" displayName="Depends on" placeholder="service name"
                    setList={(v) => onChange({depends_on: v})}/>
    </div>
  )
}

export default FfServiceCard
