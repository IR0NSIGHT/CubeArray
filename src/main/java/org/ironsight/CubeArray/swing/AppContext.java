package org.ironsight.CubeArray.swing;

import org.ironsight.CubeArray.ResourceUtils;
import org.ironsight.CubeArray.swing.FileTableModel.CaColumn;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

public class AppContext implements Serializable {
    AppContext() {

    }

    public AppContext(AppContext other) {

        // Deep copy collections
        this.filesAndTimestamps.putAll(other.filesAndTimestamps);
        this.activeFiles.addAll(other.activeFiles);

        // Immutable enough (File is effectively immutable)
        this.lastSearchPath = other.lastSearchPath;

        // Mutable object → copy
        this.guiBounds = new Rectangle(other.guiBounds);

        // Primitive / simple values
        this.neverBeforeUsed = other.neverBeforeUsed;

        // Copy list
        this.displayedColumns =
                new ArrayList<>(other.displayedColumns);

        this.columnWidths = new ArrayList<>(other.columnWidths);

        this.orderAscending = other.orderAscending;
        this.orderedColumn = other.orderedColumn;
    }

    final HashMap<File, Long> filesAndTimestamps = new HashMap<>();
    final Set<File> activeFiles = new HashSet<>();
    File lastSearchPath = new File(System.getProperty("user.home"));
    Rectangle guiBounds = new Rectangle(0,0,800,600);
    boolean neverBeforeUsed = true;
    ArrayList<CaColumn> displayedColumns = new ArrayList<>(List.of(CaColumn.values()));
    ArrayList<Integer> columnWidths = new ArrayList<>(Arrays.stream(CaColumn.values()).mapToInt(c -> c.defaultWidth).boxed().toList());
    CaColumn orderedColumn = CaColumn.FILE;
    boolean orderAscending = false;


    public static File getSaveFile() {
        File file = new File(ResourceUtils.getInstallPath().toFile(), "app.context");
        System.out.println("context savefile at: " + file.getAbsolutePath());
        return file;
    }

    // Read AppContext from folder, default to empty if missing/corrupt
    public static AppContext read() {
        File file = getSaveFile();
        if (!file.exists()) {
            AppContext context = new AppContext(); // empty context
            return context;
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
            System.err.println("app.context is invalid, returning empty context.");
            e.printStackTrace();
            return new AppContext();
        }
    }

    // Write AppContext to folder
    public static void write(AppContext contextOrg) {
        AppContext context;
        synchronized (contextOrg) {
            context = new AppContext(contextOrg);
        }
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
