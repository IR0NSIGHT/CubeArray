package org.ironsight.cubearray.platform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.*;

/**
 * Central logging initialiser and logger factory for CubeArray.
 *
 * <p>Call {@link #init()} once at application startup (before anything else). It configures the JUL
 * root logger with:
 *
 * <ul>
 *   <li>A {@link ConsoleHandler} at {@code WARNING} and above (keeps existing console behaviour).
 *   <li>A rolling {@link FileHandler} at {@code INFO} and above writing to {@code
 *       <installPath>/logs/cubearray%g.log} (5 MB per file, 3 rotations, append mode).
 * </ul>
 *
 * <p>Obtain per-class loggers with {@link #get(Class)}.
 */
public final class AppLogger {

  private static volatile boolean initialized = false;

  private AppLogger() {}

  /**
   * Initialises JUL logging. Must be called once before any logger is used. Safe to call multiple
   * times — subsequent calls are no-ops.
   */
  public static synchronized void init() {
    if (initialized) return;

    // Load formatter pattern and console handler config from bundled properties
    try (InputStream is =
        AppLogger.class.getClassLoader().getResourceAsStream("logging.properties")) {
      if (is != null) LogManager.getLogManager().readConfiguration(is);
    } catch (IOException e) {
      System.err.println("[AppLogger] Could not read logging.properties: " + e.getMessage());
    }

    // Add rolling file handler pointing at <installPath>/logs/
    try {
      Path logDir = ResourceUtils.getInstallPath().resolve("logs");
      Files.createDirectories(logDir);
      String pattern = logDir.resolve("cubearray%g.log").toString();

      FileHandler fileHandler = new FileHandler(pattern, 5 * 1024 * 1024, 3, true);
      fileHandler.setLevel(Level.INFO);
      fileHandler.setFormatter(new SimpleFormatter());

      Logger root = Logger.getLogger("");
      root.addHandler(fileHandler);
      root.setLevel(Level.INFO);
    } catch (IOException e) {
      System.err.println("[AppLogger] Could not create log file handler: " + e.getMessage());
    }

    initialized = true;
  }

  /**
   * Returns a named {@link Logger} for the given class.
   *
   * @param clazz the class requesting the logger
   * @return a JUL logger named after the class
   */
  public static Logger get(Class<?> clazz) {
    return Logger.getLogger(clazz.getName());
  }
}
