package io.vacco.ff.service;

import com.google.gson.Gson;
import io.vacco.ff.net.FgJni;
import io.vacco.ff.net.FgProc;
import io.vacco.ff.oci.FgEnvVar;
import io.vacco.ff.oci.FgImage;
import io.vacco.ff.oci.FgOciProgress;
import io.vacco.ff.oci.FgOciStore;
import io.vacco.ff.schema.*;
import io.vacco.ff.util.FgIo;
import io.vacco.ronove.util.RvValidation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Stack orchestration: persistence (JSON), topological start / reverse stop,
 * status (derived from {@code /proc}), logs, delete, and a polling supervisor
 * that restarts services per their {@code restart} policy.
 */
public final class FgStackSvc implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(FgStackSvc.class);

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
  private final Gson gson;
  private final FgOciStore store;
  private final ExecutorService ops = Executors.newCachedThreadPool(r ->
    new Thread(r, "ff-stack-op"));
  private final Map<String, Mon> monitored = new ConcurrentHashMap<>();
  private final Map<String, FgServiceStatus> live = new ConcurrentHashMap<>();

  /**
   * Per stack-service operation lock. Claimed atomically on the API thread
   * before work is dispatched, so concurrent start/stop/update/delete (UI
   * double-clicks, rapid API calls, or the supervisor restarting a service that
   * is already being started) serialize instead of racing.
   */
  private final Set<String> active = ConcurrentHashMap.newKeySet();
  private final ExecutorService supervisor = Executors.newSingleThreadExecutor(r ->
    new Thread(r, "ff-supervisor"));

  public FgStackSvc(File vmDir, File ociDir, Gson gson) {
    this.vmDir = vmDir;
    this.gson = gson;
    this.store = new FgOciStore(ociDir, new File(vmDir, "oci-tmp"));
    FgIo.mkDirs(vmDir);
    store.sweepTmp();
    reconcile();
    supervisor.submit(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          tick();
          Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
          log.debug("poll interrupted - {}", vmDir);
          Thread.currentThread().interrupt();
        } catch (Exception e) {
          log.error("poll error - {}", vmDir, e);
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
    validate(stack);
    if (stack.bridge != null && !stack.bridge.isBlank()
      && !FgJni.getLinuxBridgeInterfaces().contains(stack.bridge)) {
      throw new FgValidationException(
        "Unknown Linux bridge: " + stack.bridge,
        RvValidation.of("ff.stack.unknownBridge").withName("bridge").withParam("0", stack.bridge)
      );
    }
    FgIo.mkDirs(stackDir(stack.id));
    FgIo.toJson(stack, stackJson(stack.id), gson);
    return stack;
  }

  /**
   * Runs the validation rules and throws a validation error listing every violation.
   */
  private static void validate(FgStack stack) {
    var violations = FgValid.validate(stack);
    if (!violations.isEmpty()) {
      throw new FgValidationException(
        "Invalid stack definition: " + violations.size() + " error(s)",
        violations.toArray(RvValidation[]::new)
      );
    }
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

  /**
   * Thrown when an operation is already in flight for a stack service.
   */
  public static class FgBusyException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public final String stackId, service;

    public FgBusyException(String stackId, String service) {
      super("Operation already in progress for " + stackId + "/" + service);
      this.stackId = stackId;
      this.service = service;
    }
  }

  private boolean claim(String id, String service) {
    return active.add(key(id, service));
  }

  private void release(String id, String service) {
    active.remove(key(id, service));
  }

  public FgStackStatus start(String id) {
    var stack = load(id);
    validate(stack);
    var order = FgStackPlan.startOrder(stack);
    for (var service : order) {
      if (!claim(id, service)) {
        throw new FgBusyException(id, service);
      }
    }
    ops.submit(() -> {
      for (var service : order) {
        var k = key(id, service);
        try {
          var vm = toVm(stack, service);
          var dir = serviceDir(id, service);
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
          FgVmSvc.start(vm, dir, store, progressListener(id, service));
          live.remove(k);
        } catch (Exception e) {
          // A failed start (bad image, missing bridge, launch error) is not a
          // crash to be restarted: stop monitoring so the supervisor does not
          // retry it in a loop. `restart:` governs post-success exits only.
          monitored.remove(k);
          live.put(
            k,
            FgServiceStatus.of(
              service, FgVmId.of(id, service), FgVmState.failed, -1).withError(e.getMessage()
            )
          );
        } finally {
          release(id, service);
        }
      }
    });
    return status(id);
  }

  public FgStackStatus stop(String id) {
    var stack = load(id);
    for (var service : FgStackPlan.stopOrder(stack)) {
      if (!claim(id, service)) {
        throw new FgBusyException(id, service);
      }
    }
    for (var service : FgStackPlan.stopOrder(stack)) {
      var k = key(id, service);
      try {
        var mon = monitored.get(k);
        if (mon != null) {
          mon.desired = false;
        }
        FgVmSvc.stop(toVm(stack, service));
        live.remove(k);
      } finally {
        release(id, service);
      }
    }
    return status(id);
  }

  /**
   * Re-provisions stopped services by discarding their extracted rootfs so the
   * next start re-pulls the (possibly newer) image. Only valid when the stack is
   * fully stopped.
   */
  public FgStackStatus update(String id) {
    var stack = load(id);
    for (var service : stack.serviceNames()) {
      if (!claim(id, service)) {
        throw new FgBusyException(id, service);
      }
    }
    for (var service : stack.serviceNames()) {
      try {
        var dir = serviceDir(id, service);
        FgIo.delete(FgVmSvc.rootfsOf(dir), e -> {
        });
        FgIo.delete(FgVmSvc.imageOf(dir), e -> {
        });
      } finally {
        release(id, service);
      }
    }
    return status(id);
  }

  public void delete(String id) {
    if (!claim(id, id)) {
      throw new FgBusyException(id, id);
    }
    try {
      stop(id);
      for (var service : load(id).serviceNames()) {
        monitored.remove(key(id, service));
      }
      FgIo.delete(stackDir(id), e -> {
        throw new IllegalStateException("Unable to delete stack " + id, e);
      });
    } finally {
      release(id, id);
    }
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
      } else if (pid <= 0 && active.contains(key(id, service))) {
        // An operation (start/restart/stop/update) is in flight but has not yet
        // reported progress: reflect it so the UI can disable its actions.
        s.state = FgVmState.provisioning;
      }
      if (s.state == FgVmState.provisioning) {
        provisioning = true;
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
    var id = stack.id;
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
    // Carry the service's entrypoint/command overrides through unmerged;
    // FgVmLaunch resolves them against the image config (Docker semantics).
    vm.entrypoint = svc.entrypoint;
    vm.command = svc.command;
    vm.network = stack.bridge != null && !stack.bridge.isBlank()
      ? FgNetConfig.of(stack.bridge, FgVmId.tapName(vmid), FgVmId.macString(vmid))
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
            log.info("reconcile re-adopted running VM {} ({}/{})", vmid, d.getName(), service);
          }
        }
      } catch (Exception e) {
        log.error("reconcile error: {}", d.getName(), e);
      }
    }
  }

  private void tick() {
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
      if (!claim(mon.stackId, mon.service)) {
        continue; // an operation (likely the start that is still provisioning) is in flight
      }
      mon.lastStart = now;
      try {
        log.info("restart: {}/{} (policy={})", mon.stackId, mon.service, mon.policy);
        FgVmSvc.start(mon.vm, mon.dir, store, FgOciProgress.NOOP);
      } catch (Exception e) {
        log.error("restart error: {}/{} {}", mon.stackId, mon.service, mon.policy, e);
      } finally {
        release(mon.stackId, mon.service);
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
      @Override
      public void onLayers(int done, int total) {
        s.provision.layersDone = done;
        s.provision.layersTotal = total;
      }

      @Override
      public void onBytes(long done, long total) {
        s.provision.bytesDone = done;
        s.provision.bytesTotal = total;
      }
    };
  }

  @Override
  public void close() {
    supervisor.shutdownNow();
    ops.shutdownNow();
    try {
      supervisor.awaitTermination(2, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
