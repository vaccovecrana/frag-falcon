import {FgService} from "@ui/rpc"

export const arrayOf = (service: FgService, name: keyof FgService): string[] => {
  const v = (service as any)[name]
  return Array.isArray(v) ? [...v] : []
}
