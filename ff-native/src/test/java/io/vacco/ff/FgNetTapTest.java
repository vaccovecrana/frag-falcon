package io.vacco.ff;

import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import static io.vacco.ff.net.FgJni.*;
import static io.vacco.ff.FgTest.*;
import static j8spec.J8Spec.*;
import static org.junit.Assert.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgNetTapTest {

  static { initLog(); }

  public static final String br0 = "virbr0";

  static {
    it("Creates and deletes a tap device", localTest(() -> {
      var tap03 = "tap03";
      var res0 = tapCreate(tap03);
      assertTrue(res0 > 0);
      var res1 = tapDelete(tap03);
      assertEquals(0, res1);
    }));
    it("Creates a tap device, attaches to a bridge, detaches from it, and deletes the tap device", localTest(() -> {
      var tap04 = "tap04";
      var res0 = tapCreate(tap04);
      assertTrue(res0 > 0);
      var res1 = tapAttach(tap04, br0);
      assertEquals(0, res1);
      var res2 = tapDetach(tap04, br0);
      assertEquals(0, res2);
      var res3 = tapDelete(tap04);
      assertEquals(0, res3);
    }));
  }
}
