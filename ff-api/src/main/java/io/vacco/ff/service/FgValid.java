package io.vacco.ff.service;

import am.ik.yavi.builder.ValidatorBuilder;
import am.ik.yavi.constraint.*;
import am.ik.yavi.core.*;
import io.vacco.ff.firecracker.*;
import io.vacco.ff.schema.*;
import java.util.*;

import static am.ik.yavi.core.NullAs.VALID;
import static io.vacco.ff.net.FgJni.macToBytes;
import static java.util.stream.Collectors.toList;
import static io.vacco.ff.net.FgJni.getLinuxBridgeInterfaces;
import static io.vacco.ff.util.FgIo.exists;

public class FgValid {

  /**
   * Not null, not empty, not blank.
   *
   * @param c constraint
   * @return constraint
   * @param <T> input type
   */
  public static <T> CharSequenceConstraint<T, String> nnNeNb(CharSequenceConstraint<T, String> c) {
    return c.notNull().notBlank().notEmpty();
  }

  public static <T> CharSequenceConstraint<T, String> file(CharSequenceConstraint<T, String> c) {
    c.predicates().add(ConstraintPredicate.of(x -> {
      try {
        exists(x);
        return true;
      } catch (Exception e) {
        return false;
      }
    }, ViolationMessage.of("file.exists", "[{0}] not found"), () -> new Object[] {}, VALID));
    return c;
  }

  public static <T> CharSequenceConstraint<T, String> macAddress(CharSequenceConstraint<T, String> c) {
    c.predicates().add(ConstraintPredicate.of(x -> {
      try {
        var mac = macToBytes(x);
        return mac.length == 6;
      } catch (Exception e) {
        return false;
      }
    }, ViolationMessage.of("mac.valid", "[{0}] invalid MAC address"), () -> new Object[] {}, VALID));
    return c;
  }

  public static <T> CharSequenceConstraint<T, String> bridge(CharSequenceConstraint<T, String> c) {
    c.predicates().add(ConstraintPredicate.of(brId -> {
      var bridgeSet = new HashSet<>(getLinuxBridgeInterfaces());
      return bridgeSet.contains(brId);
    }, ViolationMessage.of("brId.exists", "[{0}] is not a Linux bridge"), () -> new Object[] {}, VALID));
    return c;
  }

  public static final Validator<FgVmTag> FgVmTagVld = ValidatorBuilder.<FgVmTag>of()
    ._string(t -> t.id, "id", c -> nnNeNb(c).lessThanOrEqual(4))
    ._string(t -> t.label, "label", c -> nnNeNb(c).lessThanOrEqual(128))
    ._string(t -> t.description, "description", c -> nnNeNb(c).lessThanOrEqual(2048))
    .build();

  public static final Validator<FgEnvVar> FgEnvVarVld = ValidatorBuilder.<FgEnvVar>of()
    ._string(v -> v.key, "key", FgValid::nnNeNb)
    .build();

  public static final Validator<FgImage> FgImageVld = ValidatorBuilder.<FgImage>of()
    ._string(img -> img.source, "source", c -> nnNeNb(c).lessThanOrEqual(256))
    .forEachIfPresent(
      FgImage::entryPointList, "entryPoint",
      c -> c._string(v -> v, "val", c0 -> nnNeNb(c0).lessThanOrEqual(128))
    )
    .forEachIfPresent(
      FgImage::cmdList, "cmd",
      c -> c._string(v -> v, "val", c0 -> nnNeNb(c0).lessThanOrEqual(128))
    )
    .forEachIfPresent(FgImage::envList, "env", FgEnvVarVld)
    .forEachIfPresent(FgImage::envUsrList, "envUsr", FgEnvVarVld)
    .build();

  public static final Validator<Drive> DriveVld = ValidatorBuilder.<Drive>of()
    ._string(d -> d.drive_id, "drive_id", FgValid::nnNeNb)
    ._string(d -> d.path_on_host, "path_on_host", FgValid::file)
    ._object(d -> d.is_root_device, "is_root_device", Constraint::notNull)
    .build();

  public static final Validator<MachineConfiguration> MachineConfigurationVld =
    ValidatorBuilder.<MachineConfiguration>of()
      ._long(mc -> mc.vcpu_count, "vcpu_count", c -> c.notNull().greaterThan(0L))
      ._long(mc -> mc.mem_size_mib, "mem_size_mib", c -> c.notNull().greaterThan(8L))
      .build();

  public static final Validator<BootSource> BootSourceVld = ValidatorBuilder.<BootSource>of()
    ._string(b -> b.kernel_image_path, "kernel_image_path", c -> file(nnNeNb(c)))
    .build();

  public static final Validator<NetworkInterface> NetworkInterfaceVld = ValidatorBuilder.<NetworkInterface>of()
    ._string(n -> n.guest_mac, "guest_mac", c -> macAddress(nnNeNb(c)))
    ._string(n -> n.iface_id, "iface_id", FgValid::nnNeNb)
    .build();

  public static final Validator<FgConfig> FgConfigVld = ValidatorBuilder.<FgConfig>of()
    ._object(cfg -> cfg.machineconfig, "machineconfig", Constraint::notNull)
    ._object(cfg -> cfg.bootsource, "bootsource", Constraint::notNull)
    .nest(cfg -> cfg.machineconfig, "machineconfig", MachineConfigurationVld)
    .nest(cfg -> cfg.bootsource, "bootsource", BootSourceVld)
    .forEachIfPresent(FgConfig::driveList, "drives", DriveVld)
    .forEachIfPresent(FgConfig::networkInterfaces, "networkinterfaces", NetworkInterfaceVld)
    .build();

  public static final Validator<FgVm> FgVmVld = ValidatorBuilder.<FgVm>of()
    ._object(vm -> vm.config, "config", Constraint::notNull)
    ._object(vm -> vm.tag, "tag", Constraint::notNull)
    ._object(vm -> vm.image, "image", Constraint::notNull)
    .nest(vm -> vm.image, "image", FgImageVld)
    .nest(vm -> vm.tag, "tag", FgVmTagVld)
    .nest(vm -> vm.config, "config", FgConfigVld)
    .build();

  public static final Validator<FgIpConfig> FgIpConfigVld = ValidatorBuilder.<FgIpConfig>of()
    ._string(ipc -> ipc.ipAddress, "ipAddress", FgValid::nnNeNb)
    ._string(ipc -> ipc.subnetMask, "subnetMask", FgValid::nnNeNb)
    ._string(ipc -> ipc.gateway, "gateway", FgValid::nnNeNb)
    .forEachIfPresent(
      FgIpConfig::getDnsServers, "dnsServers",
      c -> c._string(dns -> dns, "address", FgValid::nnNeNb)
    )
    .build();

  public static final Validator<FgNetConfig> FgNetConfigVld = ValidatorBuilder.<FgNetConfig>of()
    ._string(net -> net.brIf, "brIf", c ->  bridge(nnNeNb(c)))
    .nestIfPresent(net -> net.ipConfig, "ipConfig", FgIpConfigVld)
    .build();

  public static final Validator<FgVmCreate> FgVmCreateVld = ValidatorBuilder.<FgVmCreate>of()
    ._object(vmc -> vmc.vm, "vm", Constraint::notNull)
    ._object(vmc -> vmc.network, "network", Constraint::notNull)
    .nest(vmc -> vmc.vm, "vm", FgVmVld)
    .nest(vmc -> vmc.network, "network", FgNetConfigVld)
    .build();

  public static final Validator<FgVmStart> FgVmStartVld = ValidatorBuilder.<FgVmStart>of()
    ._string(vs -> vs.vmId, "vmId", FgValid::nnNeNb)
    ._object(vs -> vs.status, "status", Constraint::isNull)
    ._object(vs -> vs.drives, "drives", Constraint::isNull)
    ._object(vs -> vs.bootConfig, "bootConfig", Constraint::isNull)
    ._object(vs -> vs.machineConfig, "machineConfig", Constraint::isNull)
    ._object(vs -> vs.init, "init", Constraint::isNull)
    ._object(vs -> vs.errors, "errors", Constraint::isNull)
    .build();

  public static final Validator<FgVmStop> FgVmStopVld = ValidatorBuilder.<FgVmStop>of()
    ._string(vs -> vs.vmId, "vmId", FgValid::nnNeNb)
    .build();

  public static List<String> validationsOf(ConstraintViolations cv) {
    return cv.stream().map(ConstraintViolation::message).collect(toList());
  }

}
