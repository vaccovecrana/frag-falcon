import { FgVmCreate } from "./rpc"

export const VmIdNew = "new"

export const uiRoot = "/"

export const uiVm = "/vm"
export const uiVmIdLogs = "/vm/:vmId/logs"
export const uiVmEdit = "/vm/:vmId/edit"

export const uiVmIdLogsFmt = (vmId: string) => `/vm/${vmId}/logs`
export const uiVmEditFmt = (vmId: string) => `/vm/${vmId}/edit`

export const DefaultVmTemplate: FgVmCreate = {
  vm: {
    tag: { id: undefined, label: "new-vm-00", description: "New VM description" },
    image: {
      source: "docker.io/hashicorp/http-echo:latest",
      cmd: undefined, rootDir: undefined,
      entryPoint: undefined, env: undefined,
      envUsr: undefined, workingDir: undefined
    },
    config: {
      balloon: undefined,
      bootsource: {
        boot_args: undefined,
        kernel_image_path: undefined,
        initrd_path: undefined
      },
      drives: undefined,
      logger: undefined,
      machineconfig: {
        vcpu_count: 1, mem_size_mib: 128,
        cpu_template: undefined, huge_pages: undefined,
        smt: undefined, track_dirty_pages: undefined
      },
      metrics: undefined,
      mmdsconfig: undefined,
      networkinterfaces: undefined,
      vsock: undefined
    }
  },
  network: {
    brIf: undefined,
    dhcp: true,
    ipConfig: undefined
  },
  rebuildInitRamFs: false,
  errors: undefined,
  warnings: undefined
}
