package io.vacco.ff.service;

import org.slf4j.Logger;

public class FgLogging {

  public static Throwable rootCauseOf(Throwable t){
    var root = t;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    return root;
  }

  public static String messageFor(Throwable t) {
    var x = rootCauseOf(t);
    return x.getMessage() != null && !x.getMessage().isEmpty()
      ? x.getMessage()
      : x.getClass().getSimpleName();
  }

  private static Object[] merge(boolean exceptionAsMessage, Exception e, Object ... args) {
    var args0 = new Object[args.length + 1];
    args0[args0.length - 1] = exceptionAsMessage ? messageFor(e) : e;
    System.arraycopy(args, 0, args0, 0, args.length);
    return args0;
  }

  public static void onError(Logger log, String message, Exception e, Object ... args) {
    if (log.isDebugEnabled()) {
      log.debug(message, merge(false, e, args));
    } else if (e != null && args != null) {
      log.warn(String.format("%s - {}", message), merge(true, e, args));
    } else {
      log.warn(message, args);
    }
  }

}
