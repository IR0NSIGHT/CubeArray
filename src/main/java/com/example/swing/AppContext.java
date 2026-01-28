package com.example.swing;

import com.example.ResourceUtils;

import java.awt.*;
import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class AppContext implements Serializable {
    final HashMap<File, Long> filesAndTimestamps = new HashMap<>();
    final Set<File> activeFiles = new HashSet<>();
    File lastSearchPath = ResourceUtils.getInstallPath().resolve(ResourceUtils.SCHEMATICS_ROOT).toFile();
    Rectangle guiBounds = new Rectangle(0,0,400,400);

    public static File getSaveFile() {
        File file = new File(ResourceUtils.getInstallPath().toFile(), "app.context");
        return file;
    }

    // Read AppContext from folder, default to empty if missing/corrupt
    public static AppContext read() {
        File file = getSaveFile();
        if (!file.exists()) {
            return new AppContext(); // empty context
        }

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            Object obj = in.readObject();
            if (obj instanceof AppContext ctx) {
                return ctx;
            } else {
                System.err.println("app.context is invalid, returning empty context.");
                return new AppContext();
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return new AppContext();
        }
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
