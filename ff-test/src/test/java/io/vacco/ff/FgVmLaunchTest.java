package io.vacco.ff;

import io.vacco.ff.oci.FgImage;
import io.vacco.ff.schema.FgVm;
import io.vacco.ff.service.FgVmLaunch;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

import static j8spec.J8Spec.it;
import static org.junit.Assert.assertEquals;

/**
 * Regression: an OCI image whose config carries an empty {@code WorkingDir}
 * (e.g. grycap/cowsay) must not produce {@code --workdir ""}. The guest init
 * {@code chdir("")} fails and exits before the workload runs, so the VM appears
 * to hang with no console output.
 */
@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgVmLaunchTest {

  private static FgVm vmWith(String workingDir) {
    var vm = new FgVm();
    vm.tag.id = "t";
    vm.image = new FgImage();
    vm.image.source = "x";
    vm.image.workingDir = workingDir;
    vm.machine = new io.vacco.ff.schema.FgVmMachine();
    vm.machine.effective();
    return vm;
  }

  private static String workdirOf(FgVm vm) {
    var args = FgVmLaunch.args(vm, new File("/tmp/none"));
    int i = args.indexOf("--workdir");
    return i >= 0 ? args.get(i + 1) : null;
  }

  static {
    it("falls back to / for an empty image working dir", () -> {
      assertEquals("/", workdirOf(vmWith("")));
    });

    it("falls back to / for a blank image working dir", () -> {
      assertEquals("/", workdirOf(vmWith("   ")));
    });

    it("falls back to / for a null image working dir", () -> {
      assertEquals("/", workdirOf(vmWith(null)));
    });

    it("keeps an explicit image working dir", () -> {
      assertEquals("/srv/app", workdirOf(vmWith("/srv/app")));
    });

    it("emits the full command from entrypoint + cmd", () -> {
      var vm = vmWith(null);
      vm.image.entryPoint = new String[]{"/bin/sh", "-c"};
      vm.image.cmd = new String[]{"echo hi"};
      assertEquals(List.of("/bin/sh", "-c", "echo hi"), FgVmLaunch.command(vm));
    });

    it("keeps the image entrypoint when a service command overrides Cmd (opt1x)", () -> {
      var vm = vmWith(null);
      vm.image.entryPoint = new String[]{"/opt1x-linux-amd64"};
      vm.image.cmd = null;
      vm.command = List.of("--api-host=0.0.0.0", "--jdbc-url=jdbc:h2:file:/data/opt1x");
      assertEquals(
        List.of("/opt1x-linux-amd64", "--api-host=0.0.0.0", "--jdbc-url=jdbc:h2:file:/data/opt1x"),
        FgVmLaunch.command(vm)
      );
    });

    it("service command replaces image Cmd but keeps image entrypoint", () -> {
      var vm = vmWith(null);
      vm.image.entryPoint = new String[]{"/entry"};
      vm.image.cmd = new String[]{"default-cmd"};
      vm.command = List.of("custom");
      assertEquals(List.of("/entry", "custom"), FgVmLaunch.command(vm));
    });

    it("service entrypoint replaces image entrypoint and keeps image Cmd", () -> {
      var vm = vmWith(null);
      vm.image.entryPoint = new String[]{"/entry"};
      vm.image.cmd = new String[]{"default-cmd"};
      vm.entrypoint = List.of("/other");
      assertEquals(List.of("/other", "default-cmd"), FgVmLaunch.command(vm));
    });

    it("service entrypoint + command both override the image", () -> {
      var vm = vmWith(null);
      vm.image.entryPoint = new String[]{"/entry"};
      vm.image.cmd = new String[]{"default-cmd"};
      vm.entrypoint = List.of("/other");
      vm.command = List.of("custom");
      assertEquals(List.of("/other", "custom"), FgVmLaunch.command(vm));
    });

    it("uses the image Cmd alone when the image has no entrypoint", () -> {
      var vm = vmWith(null);
      vm.image.entryPoint = null;
      vm.image.cmd = new String[]{"cmd", "arg"};
      assertEquals(List.of("cmd", "arg"), FgVmLaunch.command(vm));
    });

    it("an empty service entrypoint clears the image entrypoint", () -> {
      var vm = vmWith(null);
      vm.image.entryPoint = new String[]{"/entry"};
      vm.image.cmd = new String[]{"default-cmd"};
      vm.entrypoint = List.of();
      assertEquals(List.of("default-cmd"), FgVmLaunch.command(vm));
    });

    it("falls back to /bin/sh when nothing is set", () -> {
      var vm = vmWith(null);
      vm.image.entryPoint = null;
      vm.image.cmd = null;
      assertEquals(List.of("/bin/sh"), FgVmLaunch.command(vm));
    });
  }
}
