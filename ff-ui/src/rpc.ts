/* ========================================================= */
/* ======== Generated file - do not modify directly ======== */
/* ========================================================= */

const doJsonIo = <I, O>(url: string, method: string, body: I,
                        headers: Map<string, string>, mediaType?: string): Promise<O> => {
  const options: any = {method, headers: {}}
  if (mediaType) {
    options.headers["Content-Type"] = mediaType
  }
  if (body) {
    options.body = body
  }
  headers.forEach((v, k) => options.headers[k] = v)
  return fetch(url, options)
    .then(response => Promise
      .resolve(response.json() as O)
      .catch(cause => Promise.reject({ response, cause }))
    )
}

/* ====================================== */
/* ============= RPC types ============== */
/* ====================================== */

export interface FgProvision {
  layersDone?: number;
  layersTotal?: number;
  bytesDone?: number;
  bytesTotal?: number;
}

export interface FgResources {
  vcpus?: number;
  ramMib?: number;
}

export interface FgService {
  image?: string;
  restart?: string;
  volumes?: string[];
  environment?: string[];
  entrypoint?: string[];
  command?: string[];
  depends_on?: string[];
  resources?: FgResources;
}

export interface FgServiceStatus {
  service?: string;
  id?: string;
  state?: FgVmState;
  pid?: number;
  exposedPorts?: string[];
  provision?: FgProvision;
  error?: string;
}

export interface FgStack {
  id?: string;
  bridge?: string;
  services?: Map<string, FgService>;
}

export interface FgStackRef {
  stackId?: string;
}

export const enum FgStackState {
  provisioning = "provisioning",
  running = "running",
  partial = "partial",
  stopped = "stopped",
  failed = "failed",
}

export interface FgStackStatus {
  id?: string;
  state?: FgStackState;
  services?: Map<string, FgServiceStatus>;
}

export const enum FgVmState {
  pending = "pending",
  provisioning = "provisioning",
  starting = "starting",
  running = "running",
  stopped = "stopped",
  failed = "failed",
}


/* ====================================== */
/* ============ RPC methods ============= */
/* ====================================== */

/*
Source controllers:

- io.vacco.ff.api.FgApiHdl

 */

export const apiV1StackIdDelete = (stackId: string): Promise<FgStackStatus> => {
  let path = "/api/v1/stack/{stackId}"
  path = path.replace("{ stackId }".replace(/\s+/g, ""), stackId.toString())
  return doJsonIo(path, "DELETE",
      undefined
    ,
    new Map(),
    undefined
  )
}

export const apiV1BrGet = (): Promise<string[]> => {
  let path = "/api/v1/br"
  return doJsonIo(path, "GET",
      undefined
    ,
    new Map(),
    undefined
  )
}

export const apiV1StackGet = (): Promise<FgStackStatus[]> => {
  let path = "/api/v1/stack"
  return doJsonIo(path, "GET",
      undefined
    ,
    new Map(),
    undefined
  )
}

export const apiV1StackIdGet = (stackId: string): Promise<FgStack> => {
  let path = "/api/v1/stack/{stackId}"
  path = path.replace("{ stackId }".replace(/\s+/g, ""), stackId.toString())
  return doJsonIo(path, "GET",
      undefined
    ,
    new Map(),
    undefined
  )
}

export const apiV1StackIdPatch = (stackId: string): Promise<FgStackStatus> => {
  let path = "/api/v1/stack/{stackId}"
  path = path.replace("{ stackId }".replace(/\s+/g, ""), stackId.toString())
  return doJsonIo(path, "PATCH",
      undefined
    ,
    new Map(),
    undefined
  )
}

export const apiV1StackPost = (arg0: FgStack): Promise<FgStack> => {
  let path = "/api/v1/stack"
  return doJsonIo(path, "POST",
      JSON.stringify(arg0)
    ,
    new Map(),
    "application/json"
  )
}

export const apiV1StackLogsPost = (arg0: FgStackRef): Promise<Map<string, string>> => {
  let path = "/api/v1/stack/logs"
  return doJsonIo(path, "POST",
      JSON.stringify(arg0)
    ,
    new Map(),
    "application/json"
  )
}

export const apiV1StackStartPost = (arg0: FgStackRef): Promise<FgStackStatus> => {
  let path = "/api/v1/stack/start"
  return doJsonIo(path, "POST",
      JSON.stringify(arg0)
    ,
    new Map(),
    "application/json"
  )
}

export const apiV1StackStopPost = (arg0: FgStackRef): Promise<FgStackStatus> => {
  let path = "/api/v1/stack/stop"
  return doJsonIo(path, "POST",
      JSON.stringify(arg0)
    ,
    new Map(),
    "application/json"
  )
}