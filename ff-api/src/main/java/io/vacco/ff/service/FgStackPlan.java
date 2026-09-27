package io.vacco.ff.service;

import io.vacco.ff.schema.FgStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes service start/stop order from {@code depends_on} using Kahn's
 * algorithm. Ties are broken by definition order (service map iteration order).
 */
public class FgStackPlan {

  public static List<String> startOrder(FgStack stack) {
    var names = stack.serviceNames();
    var indegree = new HashMap<String, Integer>();
    var edges = new HashMap<String, List<String>>();
    for (var n : names) {
      indegree.put(n, 0);
      edges.put(n, new ArrayList<>());
    }
    for (var n : names) {
      var svc = stack.services.get(n);
      if (svc == null || svc.depends_on == null) {
        continue;
      }
      for (var dep : svc.depends_on) {
        if (!stack.services.containsKey(dep)) {
          throw new IllegalStateException(
              "Service [" + n + "] depends on unknown service [" + dep + "]");
        }
        edges.get(dep).add(n);
        indegree.merge(n, 1, Integer::sum);
      }
    }

    var ready = new ArrayDeque<String>();
    for (var n : names) {
      if (indegree.get(n) == 0) {
        ready.add(n);
      }
    }

    var order = new ArrayList<String>();
    while (!ready.isEmpty()) {
      var n = ready.poll();
      order.add(n);
      for (var m : edges.get(n)) {
        var d = indegree.merge(m, -1, Integer::sum);
        if (d == 0) {
          ready.add(m);
        }
      }
    }

    if (order.size() != names.size()) {
      var remaining = new ArrayList<>(names);
      remaining.removeAll(order);
      throw new IllegalStateException("Cyclic depends_on among services: " + remaining);
    }
    return order;
  }

  public static List<String> stopOrder(FgStack stack) {
    var order = startOrder(stack);
    var reversed = new ArrayList<>(order);
    java.util.Collections.reverse(reversed);
    return reversed;
  }

  public static Map<String, Integer> depths(FgStack stack) {
    var names = startOrder(stack);
    var depth = new HashMap<String, Integer>();
    for (var n : names) {
      int d = 0;
      var svc = stack.services.get(n);
      if (svc != null && svc.depends_on != null) {
        for (var dep : svc.depends_on) {
          d = Math.max(d, depth.getOrDefault(dep, 0) + 1);
        }
      }
      depth.put(n, d);
    }
    return depth;
  }
}
