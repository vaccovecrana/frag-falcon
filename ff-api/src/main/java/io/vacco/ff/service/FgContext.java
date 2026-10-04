package io.vacco.ff.service;

import com.google.gson.Gson;
import io.vacco.ff.api.FgApi;
import io.vacco.ff.net.FgNetCap;
import io.vacco.ff.net.FgProc;
import io.vacco.ff.util.FgIo;
import io.vacco.ff.util.FgMounts;
import io.vacco.shax.logging.ShOption;
import org.slf4j.LoggerFactory;

import java.io.Closeable;

import static io.vacco.shax.logging.ShOption.IO_VACCO_SHAX_DEVMODE;
import static io.vacco.shax.logging.ShOption.IO_VACCO_SHAX_LOGLEVEL;

public class FgContext implements Closeable {

  private FgStackSvc svc;
  private FgApi api;

  public void init() {
    ShOption.setSysProp(IO_VACCO_SHAX_DEVMODE, Boolean.toString(FgOptions.logFormat == FgOptions.LogFormat.text));
    ShOption.setSysProp(IO_VACCO_SHAX_LOGLEVEL, FgOptions.logLevel.toString());
    var log = LoggerFactory.getLogger(FgContext.class);

    log.info("frag-falcon hypervisor starting");
    FgIo.mkDirs(FgOptions.vmDir);
    FgIo.mkDirs(FgOptions.ociDir);
    log.info("vm-dir: {}, oci-dir: {}, api: {}:{}", FgOptions.vmDir, FgOptions.ociDir, FgOptions.host, FgOptions.port);

    var mount = FgMounts.check(FgOptions.vmDir);
    if (mount.hardened()) {
      log.info("vm-dir mount [{}] is hardened ({})", mount.mountPoint(), mount.options());
    } else {
      log.warn(
        "vm-dir [{}] is not mounted nosuid,nodev,noexec (mountpoint [{}], options [{}]; missing {}). "
          + "Guest-written setuid files/device nodes/executables would then be usable from the host. "
          + "Mount the vm-dir (or a parent) with nosuid,nodev,noexec once at the OS level.",
        FgOptions.vmDir, mount.mountPoint(), mount.options(), mount.missing()
      );
    }

    var cap = FgNetCap.check();
    if (!cap.ok()) {
      log.error(
        "vm launcher [{}] cannot create TAP devices: CAP_NET_ADMIN is not available. "
          + "Grant it once, then restart:\n"
          + "  sudo setcap cap_net_admin+ep {}\n"
          + "or run under systemd with AmbientCapabilities=CAP_NET_ADMIN, or as root. "
          + "See deploy/setup.sh.",
        FgProc.launcherPath(), FgProc.launcherPath()
      );
      throw new IllegalStateException("CAP_NET_ADMIN unavailable for " + FgProc.launcherPath());
    }
    log.info("vm launcher capability ok ({})", cap.source());

    svc = new FgStackSvc(FgOptions.vmDir, FgOptions.ociDir, new Gson());
    api = new FgApi(svc, new Gson(), FgOptions.host, FgOptions.port);
  }

  @Override
  public void close() {
    if (api != null) {
      api.close();
    }
    if (svc != null) {
      svc.close();
    }
  }
}
