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

export interface Balloon {
  amount_mib: number;
  deflate_on_oom: boolean;
  stats_polling_interval_s: number;
}

export interface BootSource {
  boot_args: string;
  initrd_path: string;
  kernel_image_path: string;
}

export const enum Cache_type {
  Unsafe = "Unsafe",
  Writeback = "Writeback",
}

export const enum CpuTemplate {
  C3 = "C3",
  T2 = "T2",
  T2S = "T2S",
  T2CL = "T2CL",
  T2A = "T2A",
  V1N1 = "V1N1",
  None = "None",
}

export interface Drive {
  cache_type: Cache_type;
  drive_id: string;
  io_engine: Io_engine;
  is_read_only: boolean;
  is_root_device: boolean;
  partuuid: string;
  path_on_host: string;
  rate_limiter: RateLimiter;
  socket: string;
}

export interface FcApiResponse {
  statusCode: number;
  body: string;
}

export interface FgConfig {
  balloon: Balloon;
  bootsource: BootSource;
  drives: Drive[];
  logger: Logger;
  machineconfig: MachineConfiguration;
  metrics: Metrics;
  mmdsconfig: MmdsConfig;
  networkinterfaces: NetworkInterface[];
  vsock: Vsock;
}

export interface FgEnvVar {
  key: string;
  val: string;
}

export interface FgImage {
  source: string;
  rootDir: string;
  workingDir: string;
  entryPoint: string[];
  cmd: string[];
  env: FgEnvVar[];
  envUsr: FgEnvVar[];
}

export interface FgIpConfig {
  ipAddress: string;
  subnetMask: string;
  gateway: string;
  dnsServers: string[];
  grantTimeMs: number;
  leaseTimeSec: number;
  renewalTimeSec: number;
  rebindTimeSec: number;
  txId: number[];
  gatewayMac: number[];
}

export interface FgNetConfig {
  brIf: string;
  ipConfig: FgIpConfig;
  dhcp: boolean;
}

export interface FgVm {
  tag: FgVmTag;
  image: FgImage;
  config: FgConfig;
}

export interface FgVmCreate {
  vm: FgVm;
  network: FgNetConfig;
  rebuildInitRamFs: boolean;
  warnings: string[];
  errors: string[];
}

export interface FgVmList {
  vms: FgVmStatus[];
  errors: string[];
}

export interface FgVmLogs {
  vmId: string;
  logData: string;
  errors: string[];
}

export interface FgVmResourceList {
  items: string[];
  errors: string[];
}

export interface FgVmStart {
  vmId: string;
  status: FgVmStatus;
  machineConfig: FcApiResponse;
  bootConfig: FcApiResponse;
  init: FcApiResponse;
  drives: FcApiResponse[];
  errors: string[];
}

export interface FgVmStatus {
  fcPid: number;
  vm: FgVm;
  network: FgNetConfig;
}

export interface FgVmStop {
  fcPid: number;
  vmId: string;
  errors: string[];
}

export interface FgVmTag {
  id: string;
  label: string;
  description: string;
}

export const enum Huge_pages {
  None = "None",
  Val001 = "Val001",
}

export const enum Io_engine {
  Sync = "Sync",
  Async = "Async",
}

export const enum Level {
  Error = "Error",
  Warning = "Warning",
  Info = "Info",
  Debug = "Debug",
  Trace = "Trace",
  Off = "Off",
}

export interface Logger {
  level: Level;
  log_path: string;
  module: string;
  show_level: boolean;
  show_log_origin: boolean;
}

export interface MachineConfiguration {
  cpu_template: CpuTemplate;
  huge_pages: Huge_pages;
  mem_size_mib: number;
  smt: boolean;
  track_dirty_pages: boolean;
  vcpu_count: number;
}

export interface Metrics {
  metrics_path: string;
}

export interface MmdsConfig {
  ipv4_address: string;
  network_interfaces: string[];
  version: Version;
}

export interface NetworkInterface {
  guest_mac: string;
  host_dev_name: string;
  iface_id: string;
  rx_rate_limiter: RateLimiter;
  tx_rate_limiter: RateLimiter;
}

export interface RateLimiter {
  bandwidth: TokenBucket;
  ops: TokenBucket;
}

export interface TokenBucket {
  one_time_burst: number;
  refill_time: number;
  size: number;
}

export const enum Version {
  V1 = "V1",
  V2 = "V2",
}

export interface Vsock {
  guest_cid: number;
  uds_path: string;
  vsock_id: string;
}


/* ====================================== */
/* ============ RPC methods ============= */
/* ====================================== */

/*
Source controllers:

- io.vacco.ff.api.FgApiHdl

 */

export const apiV1VmLogsDelete = (vmId: string): Promise<FgVmLogs> => {
  let path = "/api/v1/vm/logs"
  const qParams = new URLSearchParams()
  if (vmId) {
    qParams.append("vmId", vmId.toString())
  }
  path = `${path}?${qParams.toString()}`
  return doJsonIo(path, "DELETE",
      undefined
    ,
    new Map(),
    undefined
  )
}

export const apiV1BrGet = (): Promise<FgVmResourceList> => {
  let path = "/api/v1/br"
  return doJsonIo(path, "GET",
      undefined
    ,
    new Map(),
    undefined
  )
}

export const apiV1KrnGet = (): Promise<FgVmResourceList> => {
  let path = "/api/v1/krn"
  return doJsonIo(path, "GET",
      undefined
    ,
    new Map(),
    undefined
  )
}

export const apiV1VmGet = (): Promise<FgVmList> => {
  let path = "/api/v1/vm"
  return doJsonIo(path, "GET",
      undefined
    ,
    new Map(),
    undefined
  )
}

export const apiV1VmIdGet = (vmId: string): Promise<FgVmCreate> => {
  let path = "/api/v1/vm/{vmId}"
  path = path.replace("{ vmId }".replace(/\s+/g, ""), vmId.toString())
  return doJsonIo(path, "GET",
      undefined
    ,
    new Map(),
    undefined
  )
}

export const apiV1VmPost = (arg0: FgVmCreate): Promise<FgVmCreate> => {
  let path = "/api/v1/vm"
  return doJsonIo(path, "POST",
      JSON.stringify(arg0)
    ,
    new Map(),
    undefined
  )
}

export const apiV1VmLogsPost = (arg0: FgVmLogs): Promise<FgVmLogs> => {
  let path = "/api/v1/vm/logs"
  return doJsonIo(path, "POST",
      JSON.stringify(arg0)
    ,
    new Map(),
    undefined
  )
}

export const apiV1VmStartPost = (arg0: FgVmStart): Promise<FgVmStart> => {
  let path = "/api/v1/vm/start"
  return doJsonIo(path, "POST",
      JSON.stringify(arg0)
    ,
    new Map(),
    undefined
  )
}

export const apiV1VmStopPost = (arg0: FgVmStop): Promise<FgVmStop> => {
  let path = "/api/v1/vm/stop"
  return doJsonIo(path, "POST",
      JSON.stringify(arg0)
    ,
    new Map(),
    undefined
  )
}