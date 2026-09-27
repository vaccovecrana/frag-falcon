import {arrayOf, serviceOf} from "@ui/util"
import {FgService} from "@ui/rpc"

/**
 * Repeatable string-list editor bound directly to a service field (ports,
 * volumes, environment, ...). Mirrors dockge's ArrayInput: the field only
 * materializes an array once the user adds an item.
 */
const FfArrayInput = (
  {service, name, displayName, placeholder}:
  { service: FgService, name: keyof FgService, displayName: string, placeholder?: string },
) => {
  const list = arrayOf(service, name)

  const update = (next: string[]) => {
    ;(service as any)[name] = next.length > 0 ? next : undefined
  }

  return (
    <div class="mb-4">
      <label class="form-label">{displayName}</label>
      {list.length > 0 && (
        <ul class="ff-list">
          {list.map((value, index) => (
            <li key={index} class="ff-list-item">
              <input
                class="ff-input"
                placeholder={placeholder}
                value={value}
                onInput={(e: any) => {
                  const next = [...list]
                  next[index] = e.target.value
                  update(next)
                }}
              />
              <button
                class="ff-btn-danger"
                onClick={() => update(list.filter((_, i) => i !== index))}
                aria-label="Remove"
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      <button class="vf-pill mt8" onClick={() => update([...list, ""])}>
        + Add {displayName}
      </button>
    </div>
  )
}

export default FfArrayInput
export {serviceOf}
