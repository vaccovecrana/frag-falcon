package io.vacco.ff.service;

import io.vacco.ff.schema.*;
import java.io.*;

public class FgVmSvc implements Closeable {

  public FgVmCreate vmBuild(FgVmCreate vmCreate) {
    return FgVmSvcBuild.vmBuild(vmCreate);
  }

  public FgVmCreate vmGet(String vmId) {
    return FgVmSvcBuild.vmGet(vmId);
  }

  public FgVmStart vmStart(FgVmStart req) {
    return FgVmSvcControl.vmStart(req);
  }

  public FgVmStop vmStop(FgVmStop req) {
    return FgVmSvcControl.vmStop(req);
  }

  public FgVmLogs vmLogs(FgVmLogs req) {
    return FgVmSvcControl.vmLogs(req);
  }

  public FgVmLogs vmLogsDelete(String vmId) {
    return FgVmSvcControl.vmLogsDelete(vmId);
  }

  public FgVmList vmList() {
    return FgVmSvcStatus.vmList();
  }

  public FgVmResourceList brIfList() {
    return FgVmSvcStatus.brIfList();
  }

  public FgVmResourceList krnList() {
    return FgVmSvcStatus.krnList();
  }

  public void start() {
    FgVmSvcDhcp.vmDhcpStart();
  }

  @Override public void close() {
    FgVmSvcDhcp.vmDhcpClose();
  }

}
