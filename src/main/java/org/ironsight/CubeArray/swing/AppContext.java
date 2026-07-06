package org.ironsight.CubeArray.swing;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ironsight.CubeArray.AppLogger;
import org.ironsight.CubeArray.ResourceUtils;

public record AppContext(
    Map<File, Long> filesAndTimestamps,
    File lastSearchPath,
    Rectangle guiBounds,
    boolean neverBeforeUsed,
    ColumnContext columnContext)
    implements Serializable {

  private static final Logger logger = AppLogger.get(AppContext.class);

  public AppContext() {
    this(
        new HashMap<>(),
        new File(System.getProperty("user.home")),
        new Rectangle(0, 0, 800, 600),
        true,
        new ColumnContext());
  }

  public AppContext copy() {
    return new AppContext(
        new HashMap<>(this.filesAndTimestamps),
        this.lastSearchPath,
        new Rectangle(this.guiBounds),
        this.neverBeforeUsed,
        this.columnContext.copy());
  }

  public static AppContext read() {
    File file = getSaveFile();
    if (!file.exists()) {
      logger.info("No app context found; starting with empty context");
      return new AppContext();
    }

    try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
      Object obj = in.readObject();
      if (obj instanceof AppContext ctx) {
        logger.info("Successfully loaded app context");
        if (ctx.columnContext() == null) {
          return new AppContext(
              ctx.filesAndTimestamps(),
              ctx.lastSearchPath(),
              ctx.guiBounds(),
              ctx.neverBeforeUsed(),
              new ColumnContext());
        }
        return ctx;
      } else {
        logger.warning("app.context is invalid, returning empty context");
        return new AppContext();
      }
    } catch (IOException | ClassNotFoundException e) {
      logger.log(Level.WARNING, "app.context is invalid, returning empty context", e);
      return new AppContext();
    }
  }

  public static File getSaveFile() {
    File file = new File(ResourceUtils.getInstallPath().toFile(), "app.context");
    logger.fine("context savefile at: " + file.getAbsolutePath());
    return file;
  }

  public static void write(AppContext context) {
    File file = getSaveFile();
    File folder = file.getParentFile();
    if (!folder.exists() && !folder.mkdirs()) {
      throw new RuntimeException("Could not create folder: " + folder.getAbsolutePath());
    }

    try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
      out.writeObject(context);
      logger.info("app.context written");
    } catch (IOException e) {
      throw new RuntimeException("Failed to write context to " + file.getAbsolutePath(), e);
    }
  }
}
