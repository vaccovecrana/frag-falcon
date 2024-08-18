import * as React from "preact/compat"
import { RenderableProps } from "preact"
import { useContext } from "preact/hooks"
import { stringify, parse } from "yaml"

import { FgContext, FgStore, lockUi, usrError } from "@ff/store"
import { FgIcnSave } from "@ff/components/FgIcons"
import { apiV1BrGet, apiV1KrnGet, apiV1VmIdGet, apiV1VmPost, FgVmCreate } from "@ff/rpc"
import { DefaultVmTemplate, uiRoot, VmIdNew } from "@ff/ui"
import { checkResult } from "@ff/util"

type FgVmEditProps = RenderableProps<{ s?: FgStore, vmId?: string }>
type FgVmEditState = {
  vmCfgTxt: string
  vmCfg: FgVmCreate,
  brIf?: string, brIfList?: string[],
  kernel?: string, kernelList?: string[]
}

class FgVmEdit extends React.Component<FgVmEditProps, FgVmEditState> {

  componentDidMount(): void {
    const {dispatch: d} = this.props.s
    lockUi(true, d)
      .then(() => Promise.all([apiV1BrGet(), apiV1KrnGet()]))
      .then(([brIfRes, kernelRes]) => {
        checkResult(brIfRes)
        checkResult(kernelRes)
        this.setState({...this.state,
          brIfList: brIfRes.items,
          kernelList: kernelRes.items
        })
      })
      .then(() => this.props.vmId === VmIdNew
        ? Promise.resolve(DefaultVmTemplate)
        : apiV1VmIdGet(this.props.vmId)
      )
      .then(vmCfg => {
        checkResult(vmCfg)
        this.setState({vmCfg, vmCfgTxt: stringify(vmCfg)}, () => lockUi(false, d))
      })
      .catch(err => usrError({...err, location: window.location}, d))
  }

  public onEdit(e: React.JSX.TargetedEvent<HTMLTextAreaElement, Event>) {
    const target = e.target as any
    this.setState({...this.state, vmCfgTxt: target.value})
  }

  public onSave() {
    var vmCfg: FgVmCreate = parse(this.state.vmCfgTxt)
    if (!vmCfg.vm.tag.id) {
      vmCfg.vm.tag.id = VmIdNew
    }
    if (this.state.kernel) {
      vmCfg.vm.config.bootsource.kernel_image_path = this.state.kernel
    }
    if (this.state.brIf) {
      vmCfg.network.brIf = this.state.brIf
    }
    const {dispatch: d} = this.props.s
    lockUi(true, d)
      .then(() => apiV1VmPost(vmCfg))
      .then((vmCfg) => {
        checkResult(vmCfg)
        if (vmCfg.warnings && vmCfg.warnings.length > 0) {
          this.setState({...this.state, vmCfg}, () => lockUi(false, d))
        } else {
          window.location.replace(uiRoot) // TODO implement Vm details view.
        }
      })
      .catch(err => usrError({...err, location: window.location}, d))
  }

  public renderDropDown(label: string, values: string[], onSelect: (val: string) => void) {
    return (
      <div class="form-group">
        <label class="form-label">{label}</label>
        <select class="form-control"
          onChange={(e: any) => onSelect(e.target.value)}>
          <option>---</option>
          {values ? values.map(val => <option value={val}>{val}</option>) : []}
        </select>
      </div>
    )
  }

  public render() {
    const { vmId } = this.props
    const { vmCfg } = this.state
    const isNew = vmId === VmIdNew
    return (
      <div>
        <div class="row align-center">
          <div class="col xs-11">
            <h1>
              VM {isNew ? "create" : "edit - "} {isNew ? "" : <code>{vmId}</code>}
            </h1>
          </div>
          <div class="col auto">
            <div class="txr">
              <a class="ptr" onClick={() => this.onSave()}>
                <FgIcnSave maxHeight={32} />
              </a>
            </div>
          </div>
        </div>
        <div class="mt16">
          {vmCfg && vmCfg.warnings && vmCfg.warnings.length > 0 ? (
            <div class="callout danger">
              <ul>
                <code>{vmCfg.warnings.map(err => <li>{err}</li>)}</code>
              </ul>
            </div>
          ) : []}
          <div class="row gutter-tiny">
            <div class="col xs-12 sm-6">
              {this.renderDropDown(
                "Kernel", this.state.kernelList,
                (val) => this.setState({...this.state, kernel: val})
              )}
            </div>
            <div class="col auto">
              {this.renderDropDown(
                "Network Bridge", this.state.brIfList,
                (val) => this.setState({...this.state, brIf: val})
              )}
            </div>
            <div class="col xs-12">
              <textarea class="editor"
                rows={32} spellCheck={false}
                autoComplete="off" autocorrect="off" autocapitalize="off"
                onChange={(e) => this.onEdit(e)}>
                {this.state.vmCfgTxt || ""}
              </textarea>
            </div>
          </div>
        </div>
      </div>
    )
  }

}

export default (props: FgVmEditProps) => <FgVmEdit s={useContext(FgContext)} vmId={props.vmId} />
