import {arrayOf} from "@ui/util"
import {FgService} from "@ui/rpc"

/**
 * Repeatable string-list editor. Reports changes to the parent via {@code setList}
 * so the editor can rebuild state (and the YAML view).
 */
const FfArrayInput = (
  {service, name, displayName, placeholder, setList}:
  {
    service: FgService, name: keyof FgService, displayName: string,
    placeholder?: string, setList: (next: string[]) => void,
  },
) => {
  const list = arrayOf(service, name)

  const update = (next: string[]) => setList(next)

  return (
    <div class="ff-field">
      <label class="vf-form-label">{displayName}</label>
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
      <button class="vf-pill vf-mt8" onClick={() => update([...list, ""])}>
        + Add {displayName}
      </button>
    </div>
  )
}

export default FfArrayInput
