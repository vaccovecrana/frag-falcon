package io.vacco.ff.firecracker;

import io.vacco.ff.firecracker.logger.Level;
import java.lang.Boolean;
import java.lang.String;

/**
 * Describes the configuration option for the logging capability.
 */
public class Logger {
  public Level level;

  public String log_path;

  public String module;

  public Boolean show_level;

  public Boolean show_log_origin;

  /**
   * Set the level. The possible values are case-insensitive.
   */
  public Logger level(Level level) {
    this.level = level;
    return this;
  }

  /**
   * Path to the named pipe or file for the human readable log output.
   */
  public Logger log_path(String log_path) {
    this.log_path = log_path;
    return this;
  }

  /**
   * The module path to filter log messages by.
   */
  public Logger module(String module) {
    this.module = module;
    return this;
  }

  /**
   * Whether or not to output the level in the logs.
   */
  public Logger show_level(Boolean show_level) {
    this.show_level = show_level;
    return this;
  }

  /**
   * Whether or not to include the file path and line number of the log&#39;s origin.
   */
  public Logger show_log_origin(Boolean show_log_origin) {
    this.show_log_origin = show_log_origin;
    return this;
  }

  public static Logger logger() {
    return new Logger();
  }
}
