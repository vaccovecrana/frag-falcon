package io.vacco.ff;

import io.vacco.ff.schema.FgService;
import io.vacco.ff.schema.FgStack;
import io.vacco.ff.schema.FgStackTag;
import io.vacco.ff.service.FgStackPlan;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.util.List;

import static j8spec.J8Spec.it;
import static org.junit.Assert.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgStackPlanTest {

  private static FgService svc(String... dependsOn) {
    var s = new FgService();
    s.depends_on = dependsOn.length == 0 ? null : List.of(dependsOn);
    return s;
  }

  private static FgStack stackWith(String... names) {
    var s = new FgStack();
    s.tag = FgStackTag.of("s");
    for (var n : names) {
      s.services.put(n, svc());
    }
    return s;
  }

  static {
    it("orders dependencies before dependents", () -> {
      var s = stackWith("server", "agent-0", "agent-1");
      s.services.get("agent-0").depends_on = List.of("server");
      s.services.get("agent-1").depends_on = List.of("server");

      var order = FgStackPlan.startOrder(s);
      assertTrue(order.indexOf("server") < order.indexOf("agent-0"));
      assertTrue(order.indexOf("server") < order.indexOf("agent-1"));

      var stop = FgStackPlan.stopOrder(s);
      assertTrue(stop.indexOf("server") > stop.indexOf("agent-0"));
    });

    it("detects cycles", () -> {
      var s = stackWith("a", "b");
      s.services.get("a").depends_on = List.of("b");
      s.services.get("b").depends_on = List.of("a");
      try {
        FgStackPlan.startOrder(s);
        fail("expected a cycle error");
      } catch (IllegalStateException expected) {
        assertTrue(expected.getMessage().toLowerCase().contains("cyclic"));
      }
    });

    it("rejects depends_on to unknown services", () -> {
      var s = stackWith("a");
      s.services.get("a").depends_on = List.of("ghost");
      try {
        FgStackPlan.startOrder(s);
        fail("expected an unknown-service error");
      } catch (IllegalStateException expected) {
        assertTrue(expected.getMessage().contains("ghost"));
      }
    });

    it("computes dependency depths", () -> {
      var s = stackWith("db", "app", "web");
      s.services.get("app").depends_on = List.of("db");
      s.services.get("web").depends_on = List.of("app");
      var d = FgStackPlan.depths(s);
      assertEquals(Integer.valueOf(0), d.get("db"));
      assertEquals(Integer.valueOf(1), d.get("app"));
      assertEquals(Integer.valueOf(2), d.get("web"));
    });
  }
}
