import io.vacco.ff.FgDockerIo;
import io.vacco.shax.logging.ShOption;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;
import java.io.File;

import static j8spec.J8Spec.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgDockerIoTest {
  static {
    ShOption.setSysProp(ShOption.IO_VACCO_SHAX_DEVMODE, "true");
  }
  static {
    it("Extracts a remote Docker image", () -> {
      var image = "quay.io/argoproj/argocd:latest";
      FgDockerIo.extract(image, new File("./build"));
    });
  }
}
