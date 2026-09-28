import {RvResult} from "@ui/rpc"

/**
 * Minimal i18n for backend validation keys (RvValidation.key). Sentence
 * templates interpolate `{name}`-style placeholders from `params`. Unknown
 * keys fall back to the raw backend `error` string.
 */
const TEMPLATES: Record<string, string> = {
  "ff.stack.invalidId":
    "Invalid stack id '{id}'. Use letters, numbers and dashes only.",
  "ff.stack.unknownBridge":
    "Unknown Linux bridge '{bridge}'. Pick one of the detected bridges.",
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
