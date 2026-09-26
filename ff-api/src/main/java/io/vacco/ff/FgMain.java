package io.vacco.ff;

import io.vacco.ff.service.FgOptions;
import io.vacco.ff.service.FgContext;
import java.util.Arrays;

public class FgMain {

  public static void main(String[] args) {
    if (args == null || args.length == 0 || Arrays.asList(args).contains("--help")) {
      System.out.println(FgOptions.usage());
      return;
    }
    FgOptions.setFrom(args);
    var ctx = new FgContext();
    try { // TODO add UNIX SIGTERM handler
      ctx.init();
    } catch (Exception e) {
      System.out.printf("Application error - %s %s%n",
        e.getClass().getSimpleName(), e.getMessage()
      );
      ctx.close();
    }
  }

}
