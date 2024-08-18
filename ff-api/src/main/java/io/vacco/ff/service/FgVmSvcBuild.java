package io.vacco.ff.service;

import com.google.gson.*;
import io.vacco.ff.schema.*;
import org.slf4j.*;
import java.io.File;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

import static io.vacco.ff.api.FgRoute.VmIdNew;
import static io.vacco.ff.firecracker.NetworkInterface.networkInterface;
import static io.vacco.ff.initramfs.FgConstants.*;
import static io.vacco.ff.initramfs.FgCpio.archive;
import static io.vacco.ff.initramfs.FgDockerIo.extract;
import static io.vacco.ff.net.FgJni.*;
import static io.vacco.ff.service.FgLogging.*;
import static io.vacco.ff.service.FgValid.*;
import static io.vacco.ff.util.FgIo.*;
import static java.lang.String.format;

public class FgVmSvcBuild {

  private static final Logger log = LoggerFactory.getLogger(FgVmSvcBuild.class);
  private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private static final Random rnd = new Random();

  public static String newId() {
    var n = (short) rnd.nextInt(Short.MAX_VALUE + 1);
    return Integer.toHexString(n & 0xFFFF);
  }

  private static FgImage vmInitRamFs(File workDir, String dockerImageUri) {
    if (!workDir.exists() || !workDir.isDirectory()) {
      throw new IllegalArgumentException("Invalid work directory: " + workDir.getAbsolutePath());
    }
    var img = extract(
      dockerImageUri, workDir, dockerArch, dockerOs,
      (entry, err) -> log.warn("Unable to extract entry [{}] - {}", entry, err.getMessage())
    );
    var extractDir = new File(workDir, pExtract);
    var ffRtUri = uri(Objects.requireNonNull(FgVmSvc.class.getResource("/io/vacco/ff/ffrt")));
    var ffRtPath = new File(extractDir, "init");
    copyURIToFile(ffRtUri, ffRtPath.toPath());
    addPermissions(ffRtPath.toPath(), PosixFilePermission.OWNER_EXECUTE);

    var initRamFs = new File(workDir, fInitRamFs);
    archive(
      extractDir, initRamFs,
      (path, err) -> log.warn("Unable to archive path [{}] - {}", path, err.getMessage())
    );
    img.rootDir = initRamFs.getAbsolutePath();
    delete(extractDir, e -> onError(log, "Unable to delete extract directory [{}]", e, extractDir));
    return img;
  }

  private static FgImage vmUpdateInitRamFs(File vmRoot, String source, List<FgEnvVar> envUsr) {
    var ramFs = vmInitRamFs(vmRoot, source).withEnvUsr(envUsr);
    if (ramFs.entryPoint == null) {
      log.warn("Docker image [{}] has no entry point. Trying to use CMD instead", ramFs.source);
      ramFs.entryPoint = ramFs.cmd;
      ramFs.cmd = null;
    }
    return ramFs;
  }

  private static FgVmCreate vmUpdate(FgVmCreate req, FgVmFiles vms) {
    var vm0 = fromJson(vms.vmCfg, FgVm.class, gson);
    if (!vm0.image.source.equals(req.vm.image.source) || req.rebuildInitRamFs) {
      delete(vms.vmInitRamFs, e -> { throw new IllegalStateException("Unable to delete VM initramfs", e); });
      req.vm.image = vmUpdateInitRamFs(vms.vmRoot, req.vm.image.source, req.vm.image.envUsr);
    }
    toJson(req.vm, vms.vmCfg, gson);
    toJson(req.network, vms.vmNetCfg, gson);
    return req;
  }

  public static FgVmCreate vmBuild(FgVmCreate req)  {
    try {
      var warnings = validationsOf(FgVmCreateVld.validate(req));
      if (!warnings.isEmpty()) {
        return req.withWarnings(warnings);
      }
      if (!VmIdNew.equals(req.vm.tag.id)) {
        return vmUpdate(req, FgVmFiles.of(FgOptions.vmDir, req.vm.tag.id));
      }

      req.vm.tag.id = newId();
      var vms = FgVmFiles.of(FgOptions.vmDir, req.vm.tag.id);
      mkDirs(vms.vmRoot);

      req.vm.image = vmUpdateInitRamFs(vms.vmRoot, req.vm.image.source, req.vm.image.envUsr);
      req.vm.config.networkinterfaces = List.of( // TODO allow for multiple interfaces if there is demand.
        networkInterface()
          .host_dev_name(format("tap%s.%d", req.vm.tag.id, 0))
          .guest_mac(macToString(newMacAddress()))
          .iface_id(format("eth%d", 0))
      );

      toJson(req.vm, vms.vmCfg, gson);
      toJson(req.network, vms.vmNetCfg, gson);
      return req;
    } catch (Exception e) {
      onError(log, "Unable to build VM {}", e, req);
      return req.withError(messageFor(e));
    }
  }

  public static FgVmCreate vmGet(String vmId) {
    var res = new FgVmCreate();
    try {
      var vms = FgVmFiles.of(FgOptions.vmDir, vmId);
      var vm = fromJson(vms.vmCfg, FgVm.class, gson);
      var net = fromJson(vms.vmNetCfg, FgNetConfig.class, gson);
      var c = new FgVmCreate().withVm(vm).withNetwork(net);
      if (c.network.dhcp) {
        c.network.ipConfig = null;
      }
      return res.withVm(vm).withNetwork(net);
    } catch (Exception e) {
      onError(log, "Unable to retrieve metadata for VM {}", e, vmId);
      return res.withError(messageFor(e));
    }
  }

}
