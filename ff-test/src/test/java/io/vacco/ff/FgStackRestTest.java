package io.vacco.ff;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vacco.ff.api.FgApi;
import io.vacco.ff.schema.FgService;
import io.vacco.ff.schema.FgStack;
import io.vacco.ff.schema.FgStackStatus;
import io.vacco.ff.service.FgStackSvc;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static io.vacco.ff.FgTest.log;
import static j8spec.J8Spec.it;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * M5b: stack-oriented REST round-trip (create → start → running → logs → stop →
 * delete). Requires cap_net_admin + virbr0; no-ops otherwise.
 */
@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgStackRestTest {

  private static final String BRIDGE = "virbr0";
  private static final Gson G = new Gson();
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  private static HttpResponse<String> post(String url, Object body) throws Exception {
    var req = HttpRequest.newBuilder(URI.create(url))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(G.toJson(body)))
      .build();
    return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
  }

  private static HttpResponse<String> get(String url) throws Exception {
    return HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
      HttpResponse.BodyHandlers.ofString());
  }

  static {
    it("manages a stack end-to-end over REST", () -> {
      if (!FgTest.hasNetCap()) {
        log.info("libkrun: skipping stack REST test (no cap_net_admin)");
        return;
      }
      var base = "http://127.0.0.1:" + (17070 + (int) (ProcessHandle.current().pid() % 1000));
      var vmDir = FgTest.freshDir("m5b");
      var svc = new FgStackSvc(vmDir, G);
      var stackId = "m5brest";

      try (svc; var _ = new FgApi(svc, G, "127.0.0.1", URI.create(base).getPort())) {
        var stack = new FgStack().id(stackId).bridge(BRIDGE);
        var s = new FgService();
        s.image = "alpine:latest";
        s.restart = "no";
        s.command = List.of("/bin/sh", "-c", "echo m5b-ok; sleep 3");
        stack.services.put("svc", s);

        assertEquals(200, post(base + "/api/v1/stack", stack).statusCode());

        List<FgStackStatus> list = G.fromJson(get(base + "/api/v1/stack").body(),
          new TypeToken<List<FgStackStatus>>() {
          }.getType());
        assertTrue(list.stream().anyMatch(x -> stackId.equals(x.id)));

        assertEquals(200, post(base + "/api/v1/stack/start",
          Map.of("stackId", stackId)).statusCode());

        var running = false;
        for (int i = 0; i < 300 && !running; i++) {
          List<FgStackStatus> st = G.fromJson(get(base + "/api/v1/stack").body(),
            new TypeToken<List<FgStackStatus>>() {
            }.getType());
          var mine = st.stream().filter(x -> stackId.equals(x.id)).findFirst().orElse(null);
          running = mine != null && mine.services.values().stream()
            .anyMatch(y -> y.state == io.vacco.ff.schema.FgVmState.running);
          Thread.sleep(100);
        }
        assertTrue("expected a running service", running);

        var logBody = "";
        for (int i = 0; i < 200; i++) {
          logBody = post(base + "/api/v1/stack/logs", Map.of("stackId", stackId)).body();
          if (logBody.contains("m5b-ok")) {
            break;
          }
          Thread.sleep(100);
        }
        log.info("libkrun: m5b stack logs: {}", logBody);
        assertTrue("expected guest output in logs", logBody.contains("m5b-ok"));

        assertEquals(200, post(base + "/api/v1/stack/stop", Map.of("stackId", stackId)).statusCode());
        assertEquals(200, HTTP.send(
          HttpRequest.newBuilder(URI.create(base + "/api/v1/stack/" + stackId)).DELETE().build(),
          HttpResponse.BodyHandlers.ofString()).statusCode()
        );
        assertEquals(400, get(base + "/api/v1/stack/" + stackId).statusCode());
      }
    });
  }
}
