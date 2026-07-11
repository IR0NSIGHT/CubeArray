package org.ironsight.cubearray.platform;

import static org.ironsight.cubearray.platform.ResourceUtils.copyResourcesToFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PeriodicChecker {
  /**
   * The single application-wide checker. Lives here (not on the app entry class) so the UI and other
   * consumers depend on {@code platform}, not on the root {@code CubeArrayMain}.
   */
  public static final PeriodicChecker INSTANCE = new PeriodicChecker();

  private static final Logger logger = AppLogger.get(PeriodicChecker.class);
  private ArrayList<Runnable> callbacks = new ArrayList<>();

  public void copyDefaultSchematics() {
    Thread t =
        new Thread(
            () -> {
              // prepare files on plate
              try {
                copyResourcesToFile(ResourceUtils.SCHEMATIC_RESOURCES);
                logger.info("finished copying all schematics");
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
    t.start();
  }

  public void startPeriodicTask() {
    Thread t =
        new Thread(
            () -> {
              while (true) {
                try {
                  // System.out.println("Checking files / saving state at " +
                  // java.time.LocalTime.now());

                  // --- your periodic logic here ---
                  for (Runnable callback : callbacks) {
                    try {
                      callback.run();
                    } catch (Exception e) {
                      logger.log(Level.SEVERE, "periodic callback failed", e);
                    }
                  }
                  Thread.sleep(1000);
                } catch (InterruptedException e) {
                  // Exit if the thread is interrupted
                  break;
                } catch (Exception e) {
                  logger.log(Level.SEVERE, "unhandled exception in periodic task", e);
                }
              }
            });
    t.setDaemon(true); // won't block JVM exit
    t.start();
  }

  public void addCallback(Runnable runnable) {
    this.callbacks.add(runnable);
  }
}
