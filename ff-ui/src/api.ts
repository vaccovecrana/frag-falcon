import {RvResult} from "@ui/rpc"
import {resolveResultMessage} from "@ui/i18n"

/**
 * Unwraps a Ronove result DTO. The generated RPC methods resolve with the
 * result body regardless of HTTP status, so an error/validation payload must be
 * detected and rejected. Throwing the resolved message gives call sites a single
 * error path (`catch`), which is then surfaced as a toast.
 */
export const unwrap = <T extends RvResult>(r: T): T => {
  if (r.error !== undefined || (r.validations && r.validations.length > 0)) {
    throw new Error(resolveResultMessage(r) || "Request failed")
  }
  return r
}

/** True when the thrown value looks like a resolved backend result error. */
export const isResultError = (e: unknown): e is RvResult =>
  typeof e === "object" && e !== null && ("error" in e || "validations" in e)

/** Normalizes any thrown value (Error, result DTO, network failure) to a message. */
export const messageOf = (e: unknown): string => {
  if (isResultError(e)) {
    return resolveResultMessage(e) || "Request failed"
  }
  if (e instanceof Error) {
    return e.message
  }
  if (typeof e === "string") {
    return e
  }
  return "An unknown error occurred"
}
