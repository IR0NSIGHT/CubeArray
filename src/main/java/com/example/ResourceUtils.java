package com.example;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.net.*;

public class ResourceUtils {
    public static String TEXTURE_PACK_ROOT = "textures/Faithful_32x_1_21_7/";
    public static String SCHEMATICS_ROOT = "schematics/";

    public static Path getInstallPath() {
        String os = System.getProperty("os.name").toLowerCase();
        Path base;

        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                base = Paths.get(localAppData);
            } else {
                base = Paths.get(System.getProperty("user.home"), "AppData", "Local");
            }

        } else if (os.contains("mac")) {
            base = Paths.get(
                    System.getProperty("user.home"),
                    "Library", "Application Support"
            );

        } else {
            // Linux / Unix
            String xdg = System.getenv("XDG_DATA_HOME");
            if (xdg != null && !xdg.isEmpty()) {
                base = Paths.get(xdg);
            } else {
                base = Paths.get(
                        System.getProperty("user.home"),
                        ".local", "share"
                );
            }
        }

        Path installPath = base.resolve(CubeArrayMain.APP_NAME);

        try {
            Files.createDirectories(installPath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create install directory", e);
        }

        return installPath;
    }
    /**
     * Creates a temporary folder and copies the given resources into it.
     *
     * @param resourcePaths Array of resource paths relative to classpath (e.g., "data/config.json")
     * @return Path to the temporary folder
     * @throws IOException if an I/O error occurs
     */


    public static Path copyResourcesToFile(String... resourcePaths)
            throws IOException {

        Path folder = getInstallPath();
        ClassLoader cl = ResourceUtils.class.getClassLoader();

        for (String resourcePath : resourcePaths) {
            URL url = cl.getResource(resourcePath);

            if (url == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            try {
                if ("file".equals(url.getProtocol())) {
                    // IDE / exploded resources
                    Path sourcePath = Paths.get(url.toURI());

                    Files.walk(sourcePath).forEach(path -> {
                        try {
                            Path target = folder.resolve(resourcePath)
                                    .resolve(sourcePath.relativize(path).toString());


                            if (Files.isDirectory(path)) {
                                Files.createDirectories(target);
                            } else {
                                Files.createDirectories(target.getParent());
                                Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });

                } else if ("jar".equals(url.getProtocol())) {
                    // Running from JAR
                    JarURLConnection conn = (JarURLConnection) url.openConnection();
                    JarFile jar = conn.getJarFile();
                    String prefix = conn.getEntryName();

                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();

                        if (!entry.getName().startsWith(prefix)) {
                            continue;
                        }

                        Path target = folder.resolve(
                                entry.getName().substring(prefix.length())
                        );

                        if (entry.isDirectory()) {
                            Files.createDirectories(target);
                        } else {
                            Files.createDirectories(target.getParent());
                            try (InputStream in = jar.getInputStream(entry)) {
                                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }

                } else {
                    throw new IOException("Unsupported protocol: " + url.getProtocol());
                }
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
        }

        return folder;
    }


    // Example usage
    public static void main(String[] args) throws IOException {
        Path tmpFolder = copyResourcesToFile("textures/texture.bmp", SCHEMATICS_ROOT);
        System.out.println("Resources copied to: " + tmpFolder);

        // You can now use them as normal files:
        Path configFile = tmpFolder.resolve("textures/texture.bmp");
        System.out.println("Config exists: " + Files.exists(configFile));
    }
}
