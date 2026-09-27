package io.vacco.ff;

import io.vacco.ff.service.FgContext;
import io.vacco.ff.service.FgOptions;

public class FgMain {

  static void main(String[] args) {
    if (args.length == 0) {
      System.out.println(FgOptions.usage());
      return;
    }
    for (var a : args) {
      if (a.equals("--help") || a.equals("-h")) {
        System.out.println(FgOptions.usage());
        return;
      }
    }
    FgOptions.setFrom(args);
    var ctx = new FgContext();
    Runtime.getRuntime().addShutdownHook(new Thread(ctx::close, "ff-shutdown"));
    try {
      ctx.init();
    } catch (Exception e) {
      System.err.printf("frag-falcon failed to start: %s%n", e);
      ctx.close();
      System.exit(1);
    }
  }
}
