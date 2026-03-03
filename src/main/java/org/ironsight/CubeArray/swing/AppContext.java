package org.ironsight.CubeArray.swing;

import org.ironsight.CubeArray.ResourceUtils;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

public record AppContext(Map<File, Long> filesAndTimestamps,
                         File lastSearchPath,
                         Rectangle guiBounds,
                         boolean neverBeforeUsed,
                        ColumnContext columnContext) implements Serializable {
    public AppContext() {
        this(new HashMap<>(),
                new File(System.getProperty("user.home")),
                new Rectangle(0, 0, 800, 600),
                true,
                new ColumnContext());
    }

    public AppContext copy() {
        return new AppContext(new HashMap<>(this.filesAndTimestamps),this.lastSearchPath,new Rectangle(this.guiBounds),this.neverBeforeUsed, this.columnContext.copy());
    }

    // Read AppContext from folder, default to empty if missing/corrupt
    public static AppContext read() {
        File file = getSaveFile();
        if (!file.exists()) {
            System.err.println("NO APP CONTEXT FOUND; ADD NEW ONE");
            AppContext context = new AppContext(); // empty context
            return context;
        }

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            Object obj = in.readObject();
            if (obj instanceof AppContext ctx) {
                System.err.println("successfully loaded app context");
                return ctx;
            } else {
                System.err.println("app.context is invalid, returning empty context.");
                return new AppContext();
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("app.context is invalid, returning empty context.");
            e.printStackTrace();
            return new AppContext();
        }
    }

    public static File getSaveFile() {
        File file = new File(ResourceUtils.getInstallPath().toFile(), "app.context");
        System.out.println("context savefile at: " + file.getAbsolutePath());
        return file;
    }

    // Write AppContext to folder
    public static void write(AppContext context) {
        File file = getSaveFile();
        File folder = file.getParentFile();
        // Ensure folder exists
        if (!folder.exists() && !folder.mkdirs()) {
            throw new RuntimeException("Could not create folder: " + folder.getAbsolutePath());
        }

        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
            out.writeObject(context);
            System.out.println("app.context written");
        } catch (IOException e) {
            throw new RuntimeException("Failed to write context to " + file.getAbsolutePath(), e);
        }

    }
}
