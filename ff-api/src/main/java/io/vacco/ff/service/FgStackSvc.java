package io.vacco.ff.service;

import com.google.gson.Gson;
import io.vacco.ff.net.FgProc;
import io.vacco.ff.oci.FgEnvVar;
import io.vacco.ff.oci.FgImage;
import io.vacco.ff.oci.FgOciProgress;
import io.vacco.ff.oci.FgOciStore;
import io.vacco.ff.schema.FgNetConfig;
import io.vacco.ff.schema.FgProvision;
import io.vacco.ff.schema.FgService;
import io.vacco.ff.schema.FgServiceStatus;
import io.vacco.ff.schema.FgStack;
import io.vacco.ff.schema.FgStackState;
import io.vacco.ff.schema.FgStackStatus;
import io.vacco.ff.schema.FgVm;
import io.vacco.ff.schema.FgVmMachine;
import io.vacco.ff.schema.FgVmState;
import io.vacco.ff.schema.FgVolume;
import io.vacco.ff.util.FgIo;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Stack orchestration: persistence (JSON), topological start / reverse stop,
 * status (derived from {@code /proc}), logs, delete, and a polling supervisor
 * that restarts services per their {@code restart} policy.
 */
public final class FgStackSvc implements AutoCloseable {

  private static final Pattern ID = Pattern.compile("[A-Za-z0-9-]+");
  private static final long RESTART_BACKOFF_MS = 1000;
  private static final long POLL_INTERVAL_MS = 1000;

  private static class Mon {
    String stackId;
    String service;
    String vmid;
    String policy;
    boolean desired;
    long lastStart;
    FgVm vm;
    File dir;
  }

  private final File vmDir;
  private final String bridge;
  private final Gson gson;
  private final FgOciStore store;
  private final ExecutorService ops = Executors.newCachedThreadPool(r ->
      new Thread(r, "ff-stack-op"));
  private final Map<String, Mon> monitored = new ConcurrentHashMap<>();
  private final Map<String, FgServiceStatus> live = new ConcurrentHashMap<>();
  private final ExecutorService supervisor = Executors.newSingleThreadExecutor(r ->
      new Thread(r, "ff-supervisor"));

  public FgStackSvc(File vmDir, String bridge, Gson gson) {
    this.vmDir = vmDir;
    this.bridge = bridge;
    this.gson = gson;
    this.store = new FgOciStore(new File(vmDir, "oci"));
    FgIo.mkDirs(vmDir);
    reconcile();
    supervisor.submit(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          tick();
          Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } catch (Exception e) {
          System.err.printf("[ff] supervisor error: %s%n", e);
        }
      }
    });
  }

  /* ----- paths -------------------------------------------------------- */

  public File stackDir(String id) {
    return new File(vmDir, id);
  }

  private File stackJson(String id) {
    return new File(stackDir(id), "stack.json");
  }

  public File serviceDir(String id, String service) {
    return new File(stackDir(id), service);
  }

  private static String key(String id, String service) {
    return id + "/" + service;
  }

  /* ----- persistence -------------------------------------------------- */

  public FgStack save(FgStack stack) {
    var id = stack.tag != null ? stack.tag.id : null;
    if (id == null || !ID.matcher(id).matches()) {
      throw new IllegalArgumentException("Invalid stack id (letters, numbers, dash only): " + id);
    }
    FgIo.mkDirs(stackDir(id));
    FgIo.toJson(stack, stackJson(id), gson);
    return stack;
  }

  public FgStack load(String id) {
    var f = stackJson(id);
    if (!f.isFile()) {
      throw new IllegalArgumentException("No such stack: " + id);
    }
    return FgIo.fromJson(f, FgStack.class, gson);
  }

  public List<FgStackStatus> list() {
    var out = new ArrayList<FgStackStatus>();
    var dirs = vmDir.listFiles(File::isDirectory);
    if (dirs != null) {
      for (var d : dirs) {
        if (stackJson(d.getName()).isFile()) {
          out.add(status(d.getName()));
        }
      }
    }
    return out;
  }

  /* ----- lifecycle ---------------------------------------------------- */

  public FgStackStatus start(String id) {
    var stack = load(id);
    var order = FgStackPlan.startOrder(stack);
    ops.submit(() -> {
      for (var service : order) {
        try {
          var vm = toVm(stack, service);
          var dir = serviceDir(id, service);
          var k = key(id, service);
          var mon = new Mon();
          mon.stackId = id;
          mon.service = service;
          mon.vmid = vm.tag.id;
          mon.policy = stack.services.get(service).restart;
          mon.desired = true;
          mon.lastStart = System.currentTimeMillis();
          mon.vm = vm;
          mon.dir = dir;
          monitored.put(k, mon);
          FgVmSvc.start(vm, dir, store, bridge, progressListener(id, service));
          live.remove(k);
        } catch (Exception e) {
          live.put(key(id, service), FgServiceStatus.of(service,
              FgVmId.of(id, service), FgVmState.failed, -1)
              .withError(e.getMessage()));
        }
      }
    });
    return status(id);
  }

  public FgStackStatus stop(String id) {
    var stack = load(id);
    for (var service : FgStackPlan.stopOrder(stack)) {
      var k = key(id, service);
      var mon = monitored.get(k);
      if (mon != null) {
        mon.desired = false;
      }
      FgVmSvc.stop(toVm(stack, service));
      live.remove(k);
    }
    return status(id);
  }

  public void delete(String id) {
    stop(id);
    for (var service : load(id).serviceNames()) {
      monitored.remove(key(id, service));
    }
    FgIo.delete(stackDir(id), e -> {
      throw new IllegalStateException("Unable to delete stack " + id, e);
    });
  }

  /* ----- status / logs ------------------------------------------------ */

  public FgStackStatus status(String id) {
    var stack = load(id);
    var st = FgStackStatus.of(id);
    int running = 0;
    boolean provisioning = false;
    for (var service : stack.serviceNames()) {
      var vmid = FgVmId.of(id, service);
      var pid = FgProc.pidOf(vmid);
      var s = FgServiceStatus.of(service, vmid, pid > 0 ? FgVmState.running : FgVmState.stopped, pid);
      var img = FgVmSvc.loadImage(serviceDir(id, service));
      if (img != null) {
        s.exposedPorts = img.exposedPorts;
      }
      var l = live.get(key(id, service));
      if (l != null && pid <= 0) {
        s.state = l.state;
        s.provision = l.provision;
        s.error = l.error;
        if (l.state == FgVmState.provisioning) {
          provisioning = true;
        }
      }
      st.services.put(service, s);
      if (s.state == FgVmState.running) {
        running++;
      }
    }
    int total = st.services.size();
    st.state = total == 0 ? FgStackState.stopped
        : running == total ? FgStackState.running
        : provisioning ? FgStackState.provisioning
        : running > 0 ? FgStackState.partial
        : FgStackState.stopped;
    return st;
  }

  public Map<String, String> logs(String id) {
    var stack = load(id);
    var out = new LinkedHashMap<String, String>();
    for (var service : stack.serviceNames()) {
      out.put(service, FgVmSvc.logs(serviceDir(id, service)));
    }
    return out;
  }

  public void logsDelete(String id, String service) {
    FgVmSvc.logsDelete(serviceDir(id, service));
  }

  /* ----- stack -> VM mapping ----------------------------------------- */

  public FgVm toVm(FgStack stack, String service) {
    var svc = stack.services.get(service);
    if (svc == null) {
      throw new IllegalArgumentException("No such service: " + service);
    }
    var id = stack.tag.id;
    var vmid = FgVmId.of(id, service);
    var vm = new FgVm();
    vm.tag.id = vmid;
    vm.tag.label = id + "/" + service;
    vm.image = new FgImage();
    vm.image.source = svc.image;
    vm.machine = new FgVmMachine();
    if (svc.resources != null) {
      vm.machine.vcpus = svc.resources.vcpus;
      vm.machine.ramMib = svc.resources.ramMib;
    }
    vm.machine.effective();
    if (svc.volumes != null) {
      for (var v : svc.volumes) {
        vm.volumes.add(FgVolume.parse(v));
      }
    }
    if (svc.environment != null) {
      for (var e : svc.environment) {
        var eq = e.indexOf('=');
        vm.env.add(eq < 0 ? FgEnvVar.of(e, null) : FgEnvVar.of(e.substring(0, eq), e.substring(eq + 1)));
      }
    }
    if (svc.entrypoint != null || svc.command != null) {
      var cmd = new ArrayList<String>();
      if (svc.entrypoint != null) {
        cmd.addAll(svc.entrypoint);
      }
      if (svc.command != null) {
        cmd.addAll(svc.command);
      }
      vm.command = cmd;
    }
    vm.network = bridge != null
        ? FgNetConfig.of(bridge, FgVmId.tapName(vmid), FgVmId.macString(vmid))
        : null;
    // Reuse enriched image metadata if the service was already provisioned.
    var img = FgVmSvc.loadImage(serviceDir(id, service));
    if (img != null) {
      vm.image = img;
    }
    return vm;
  }

  /* ----- supervisor --------------------------------------------------- */

  private void reconcile() {
    var dirs = vmDir.listFiles(File::isDirectory);
    if (dirs == null) {
      return;
    }
    for (var d : dirs) {
      if (!stackJson(d.getName()).isFile()) {
        continue;
      }
      try {
        var stack = load(d.getName());
        for (var service : stack.serviceNames()) {
          var vmid = FgVmId.of(d.getName(), service);
          if (FgProc.pidOf(vmid) > 0) {
            var mon = new Mon();
            mon.stackId = d.getName();
            mon.service = service;
            mon.vmid = vmid;
            mon.policy = stack.services.get(service).restart;
            mon.desired = true;
            mon.lastStart = System.currentTimeMillis();
            mon.vm = toVm(stack, service);
            mon.dir = serviceDir(d.getName(), service);
            monitored.put(key(d.getName(), service), mon);
            System.out.printf("[ff] re-adopted running VM %s (%s/%s)%n", vmid, d.getName(), service);
          }
        }
      } catch (Exception e) {
        System.err.printf("[ff] reconcile error for %s: %s%n", d.getName(), e);
      }
    }
  }

  private void tick() {
    FgProc.reap();
    long now = System.currentTimeMillis();
    for (var mon : monitored.values()) {
      if (!mon.desired || !restartEnabled(mon.policy)) {
        continue;
      }
      if (FgProc.pidOf(mon.vmid) > 0) {
        continue;
      }
      if (now - mon.lastStart < RESTART_BACKOFF_MS) {
        continue;
      }
      mon.lastStart = now;
      try {
        System.out.printf("[ff] restarting %s/%s (policy=%s)%n", mon.stackId, mon.service, mon.policy);
        FgVmSvc.start(mon.vm, mon.dir, store, bridge, FgOciProgress.NOOP);
      } catch (Exception e) {
        System.err.printf("[ff] restart failed for %s/%s: %s%n", mon.stackId, mon.service, e);
      }
    }
  }

  private static boolean restartEnabled(String policy) {
    return "always".equals(policy) || "unless-stopped".equals(policy) || "on-failure".equals(policy);
  }

  private FgOciProgress progressListener(String id, String service) {
    var k = key(id, service);
    var s = FgServiceStatus.of(service, FgVmId.of(id, service), FgVmState.provisioning, -1);
    s.provision = new FgProvision();
    live.put(k, s);
    return new FgOciProgress() {
      @Override public void onLayers(int done, int total) {
        s.provision.layersDone = done;
        s.provision.layersTotal = total;
      }
      @Override public void onBytes(long done, long total) {
        s.provision.bytesDone = done;
        s.provision.bytesTotal = total;
      }
    };
  }

  @Override public void close() {
    supervisor.shutdownNow();
    ops.shutdownNow();
    try {
      supervisor.awaitTermination(2, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
