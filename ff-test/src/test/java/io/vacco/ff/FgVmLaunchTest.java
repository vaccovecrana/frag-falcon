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
  }
}
