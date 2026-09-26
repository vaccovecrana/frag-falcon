import * as React from "preact/compat"
import { RenderableProps } from "preact"
import { useContext } from "preact/hooks"
import { FgContext, FgStore, lockUi, usrError } from "@ff/store"
import { apiV1VmLogsDelete, apiV1VmLogsPost, FgVmLogs } from "@ff/rpc"
import FgLogViewer from "@ff/components/FgLogViewer"
import { FgNoData } from "@ff/components/FgNoData"
import { FgIcnTrash } from "@ff/components/FgIcons"

type FgVmLogsProps = RenderableProps<{ s?: FgStore, vmId?: string }>
type FgVmLogsState = { logData: string }

class FgVmLogsV extends React.Component<FgVmLogsProps, FgVmLogsState> {

  componentDidMount(): void {
    const {dispatch: d} = this.props.s
    const req: FgVmLogs = {
      vmId: this.props.vmId,
      logData: undefined, errors: undefined
    }
    lockUi(true, d)
      .then(() => apiV1VmLogsPost(req))
      .then(res => {
        if (res.errors && res.errors.length > 0) {
          throw res
        }
        this.setState(
          {logData: res.logData},
          () => lockUi(false, d)
        )
      })
      .catch(err => usrError({...err, location: window.location}, d))
  }

  public onLogsDelete() {
    const {dispatch: d} = this.props.s
    lockUi(true, d)
      .then(() => apiV1VmLogsDelete(this.props.vmId))
      .then(res => {
        if (res.errors && res.errors.length > 0) {
          throw res
        }
        this.setState(
          {logData: res.logData},
          () => lockUi(false, d)
        )
      })
      .catch(err => usrError({...err, location: window.location}, d))
  }

  public render() {
    return (
      <div>
        <div class="row align-center">
          <div class="col xs-11">
            <h1>VM Logs - <code>{this.props.vmId}</code></h1>
          </div>
          <div class="col auto">
            <div class="txr">
              <a class="ptr txr" onClick={() => this.onLogsDelete()}>
                <FgIcnTrash maxHeight={32} />
              </a>
            </div>
          </div>
        </div>
        {this.state.logData ? (
          <div class="mt16">
            <FgLogViewer logData={this.state.logData} />
          </div>
        ) : (
          <FgNoData label="No logs available" />
        )}
      </div>
    )
  }

}

export default (props: FgVmLogsProps) => <FgVmLogsV s={useContext(FgContext)} vmId={props.vmId} />
