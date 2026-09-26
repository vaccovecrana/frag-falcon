import * as React from "preact/compat"

type FgNoDataProps = {
  label: string
}

export const FgNoData = (props: FgNoDataProps) => (
  <div class="frame">
    <div class="txc p16">
      {props.label}
    </div>
  </div>
)
