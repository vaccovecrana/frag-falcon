import * as React from "preact/compat"
import { RenderableProps } from "preact"
import { useContext } from "preact/hooks"
import { FgContext, FgStore, lockUi, usrError } from "@ff/store"
import { apiV1VmGet, apiV1VmStartPost, apiV1VmStopPost, FgVmStart, FgVmStatus, FgVmStop } from "@ff/rpc"
import { FgIcnAdd, FgIcnLogs, FgIcnPlay, FgIcnStop, FgIconEdit } from "@ff/components/FgIcons"
import { toMap } from "@ff/util"
import { uiVmEditFmt, uiVmIdLogsFmt, VmIdNew } from "@ff/ui"
import { FgNoData } from "@ff/components/FgNoData"

type FgVmListProps = RenderableProps<{ s?: FgStore }>
interface FgVmListState {
  vms: Map<String, FgVmStatus>
}

class FgVmList extends React.Component<FgVmListProps, FgVmListState> {

  componentDidMount(): void {
    const {dispatch: d} = this.props.s
    lockUi(true, d)
      .then(() => apiV1VmGet())
      .then(res => {
        if (res.errors && res.errors.length > 0) {
          throw res
        }
        this.setState(
          {vms: toMap(
            res.vms.sort((st0, st1) => st0.vm.tag.id.localeCompare(st1.vm.tag.id)),
            st => st.vm.tag.id
          )},
          () => lockUi(false, d)
        )
      })
      .catch(err => usrError({...err, location: window.location}, d))
  }

  public vmStatusBadge(st: FgVmStatus) {
    return (
      <div class={`pill ${st.fcPid !== -1 ? "pill-green" : "pill-pale"}`}>
        {st.fcPid !== -1 ? "running" : "stopped"}
      </div>
    )
  }

  public vmAddress(st: FgVmStatus) {
    const netIf0 = st.vm.config.networkinterfaces && st.vm.config.networkinterfaces[0]
    const addr = st.network.ipConfig
      ? st.network.ipConfig.ipAddress
      : (netIf0 || {}).guest_mac;
    return (
      <code class="small">
        {addr}
      </code>
    )
  }

  public onVmStart(st: FgVmStatus) {
    const {dispatch: d} = this.props.s
    const req: FgVmStart = {
      vmId: st.vm.tag.id,
      status: undefined, machineConfig: undefined,
      bootConfig: undefined, drives: undefined,
      errors: undefined, init: undefined
    }
    lockUi(true, d)
      .then(() => apiV1VmStartPost(req))
      .then(res => {
        if (res.errors) {
          throw res
        }
        var vms = new Map(this.state.vms)
        vms.set(res.vmId, res.status)
        this.setState({...this.state, vms}, () => lockUi(false, d))
      })
      .catch(err => usrError({...err, location: window.location}, d))
  }

  public onVmStop(st: FgVmStatus) {
    const {dispatch: d} = this.props.s
    var req: FgVmStop = {
      vmId: st.vm.tag.id,
      fcPid: undefined,
      errors: undefined
    }
    lockUi(true, d)
      .then(() => apiV1VmStopPost(req))
      .then(res => {
        const vms = new Map(this.state.vms)
        vms.get(res.vmId).fcPid = -1
        this.setState({...this.state, vms}, () => lockUi(false, d))
      })
      .catch(err => usrError({...err, location: window.location}, d))
  }

  public vmControls(maxHeight: number, st: FgVmStatus) {
    return (
      <div class="row justify-center align-center">
        {st.fcPid === -1 ? (
          <div class="col auto txc mh4">
            <a class="ptr" onClick={() => this.onVmStart(st)}>
              <FgIcnPlay maxHeight={maxHeight} />
            </a>
          </div>
        ) : (
          <div class="col auto txc mh4">
            <a class="ptr" onClick={() => this.onVmStop(st)}>
              <FgIcnStop maxHeight={maxHeight} />
            </a>
          </div>
        )}
        <div class="col auto txc mh4">
          <a href={uiVmIdLogsFmt(st.vm.tag.id)}>
            <FgIcnLogs maxHeight={maxHeight} />
          </a>
        </div>
        {st.fcPid === -1 ? (
          <div class="col auto txc mh4">
            <a href={uiVmEditFmt(st.vm.tag.id)}>
              <FgIconEdit maxHeight={maxHeight} />
            </a>
          </div>
        ) : []}
      </div>
    )
  }

  public renderTable(vms: FgVmStatus[]) {
    return (
      <table class="table interactive">
        <thead>
          <th>Id</th>
          <th>Name</th>
          <th>State</th>
          <th>Image</th>
          <th>Address</th>
          <th>Actions</th>
        </thead>
        <tbody>
          {vms.map(st => (
            <tr>
              <td><code>{st.vm.tag.id}</code></td>
              <td>{st.vm.tag.label}</td>
              <td>{this.vmStatusBadge(st)}</td>
              <td><code>{st.vm.image.source}</code></td>
              <td>{this.vmAddress(st)}</td>
              <td>{this.vmControls(24, st)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    )
  }

  public renderCard(st: FgVmStatus) {
    return (
      <div class="card minimal mt16">
        <div class="card-body">
          <h4>
            <div class="row align-center">
              <div class="col xs-9 sm-9">
                {st.vm.tag.label}
              </div>
              <div class="col auto">
                {this.vmStatusBadge(st)}
              </div>
            </div>
          </h4>
          <div class="row">
            <div class="col xs-9 sm9 mt8">
              <div>{st.vm.tag.description}</div>
              <div>{this.vmAddress(st)}</div>
              <div>
                <code class="small">
                  {st.vm.config.machineconfig.vcpu_count} vCPU,&nbsp;
                  {st.vm.config.machineconfig.mem_size_mib} MiB
                </code>
              </div>
              <div><code>{st.vm.image.source}</code></div>
            </div>
            <div class="col auto mt8">
              {this.vmControls(24, st)}
            </div>
          </div>
        </div>
      </div>
    )
  }
  
  public render() {
    var {vms} = this.state
    return (
      <div>
        <div class="row align-center">
          <div class="col xs-11">
            <h1>Virtual Machines</h1>
          </div>
          <div class="col auto txr">
            <a href={uiVmEditFmt(VmIdNew)}>
              <FgIcnAdd maxHeight={32} />
            </a>
          </div>
        </div>
        {vms && vms.size > 0 ? (
          <div class="row">
            <div class="col auto sm-down-hide">
              <div class="frame">
                {this.renderTable([...vms.values()])}
              </div>
            </div>
            <div class="col auto md-up-hide">
              {[...vms.values()].map(vm => this.renderCard(vm))}
            </div>
          </div>
        ) : (
          <FgNoData label="No VMs defined" />
        )}
      </div>
    )
  }

}

export default (props: FgVmListProps) => <FgVmList s={useContext(FgContext)} />