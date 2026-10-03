import {RvResult} from "@ui/rpc"

/**
 * Minimal i18n for backend validation keys (RvValidation.key). Sentence
 * templates interpolate `{name}`-style placeholders from `params`. Unknown
 * keys fall back to the raw backend `error` string.
 */
const TEMPLATES: Record<string, string> = {
  "ff.stack.invalidId":
    "Invalid stack id '{0}'. Use letters, numbers and dashes only.",
  "ff.stack.unknownBridge":
    "Unknown Linux bridge '{0}'. Pick one of the detected bridges.",
  "ff.stack.missing": "No stack definition was provided.",
  "ff.stack.services.empty": "A stack needs at least one service.",
  "ff.stack.services.cyclic": "Service dependencies form a cycle: {0}",
  "ff.stack.service.missing": "Service '{0}' is not defined correctly.",
  "ff.stack.service.image.required": "Service '{0}' needs an image.",
  "ff.stack.service.image.invalid": "'{0}' is not a valid image reference.",
  "ff.stack.service.restart.invalid":
    "'{0}' is not a valid restart policy (use always, unless-stopped, on-failure or no).",
  "ff.stack.service.volumes.invalid":
    "'{0}' is not a valid volume (expected HOST:GUEST[:ro]).",
  "ff.stack.service.volumes.guestAbsolute":
    "Guest path '{0}' must be absolute.",
  "ff.stack.service.volumes.mode":
    "'{0}' is not a valid volume mode (only 'ro' is supported).",
  "ff.stack.service.volumes.duplicateGuest":
    "Duplicate guest path '{0}'.",
  "ff.stack.service.volumes.hostMissing":
    "Host directory '{0}' does not exist.",
  "ff.stack.service.environment.invalid":
    "'{0}' is not a valid environment entry (expected KEY or KEY=VALUE).",
  "ff.stack.service.environment.duplicate":
    "Duplicate environment variable '{0}'.",
  "ff.stack.service.list.blank":
    "An entry in '{0}' is blank.",
  "ff.stack.service.dependsOn.unknown":
    "Depends on unknown service '{0}'.",
}

const interpolate = (template: string, params?: Map<string, string> | Record<string, string>) => {
  if (!params) return template
  const get = (k: string): string | undefined => {
    // ronove types params as Map, but the wire format is a plain JSON object.
    if (typeof (params as any).get === "function") {
      return (params as Map<string, string>).get(k)
    }
    return (params as Record<string, string>)[k]
  }
  return template.replace(/\{(\w+)\}/g, (_, k: string) => get(k) ?? `{${k}}`)
}

/** Resolves the most useful user-facing message from a result payload. */
export const resolveResultMessage = (r: RvResult): string | undefined => {
  const v = r.validations?.[0]
  if (v?.key) {
    const template = TEMPLATES[v.key]
    return template ? interpolate(template, v.params) : r.error
  }
  return r.error
}
