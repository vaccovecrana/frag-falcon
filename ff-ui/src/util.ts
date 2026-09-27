import {FgService} from "@ui/rpc"

export const arrayOf = (service: FgService, name: keyof FgService): string[] => {
  const v = (service as any)[name]
  return Array.isArray(v) ? [...v] : []
}

export const serviceOf = (services: Map<string, FgService>, name: string): FgService => {
  if (!services.has(name)) {
    services.set(name, {})
  }
  return services.get(name)!
}

export const initials = (value?: string): string => {
  if (!value) return "?"
  const parts = value.trim().split(/\s+/).filter(Boolean)
  const letters = parts.slice(0, 2).map(p => p[0]).join("")
  return (letters || "?").toUpperCase()
}

export const stackSummary = (states: (string | undefined)[]): string => {
  if (states.length === 0) return "stopped"
  const running = states.filter(s => s === "running").length
  if (running === states.length) return "running"
  if (states.some(s => s === "provisioning")) return "provisioning"
  if (running > 0) return "partial"
  if (states.some(s => s === "failed")) return "failed"
  return "stopped"
}
