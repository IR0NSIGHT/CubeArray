package org.ironsight.CubeArray;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.net.*;
import java.util.zip.ZipFile;

public class ResourceUtils {
    public static final Set<String> SUPPORTED_FILE_TYPES =
            new HashSet<>(Arrays.asList(
                    ".bo2",
                    ".bo3",
                    ".nbt",
                    ".schematic",
                    ".schem"
            ));
    public static String TEXTURE_RESOURCES = "textures/";
    public static String TEXTURE_PACK_ROOT = "textures/Faithful_32x_1_21_7/";
    public static String SCHEMATICS_ROOT, SCHEMATIC_RESOURCES = "schematics/";

    // Example usage
    public static void main(String[] args) throws IOException {
        Path tmpFolder = copyResourcesToFile("textures/texture.bmp", SCHEMATICS_ROOT);
        System.out.println("Resources copied to: " + tmpFolder);

        // You can now use them as normal files:
        Path configFile = tmpFolder.resolve("textures/texture.bmp");
        System.out.println("Config exists: " + Files.exists(configFile));
    }

    /**
     * copies the given resources into it the install folder on plate, unzips all zips.
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
                List<File> copiedFiles = new LinkedList<>();

                if ("file".equals(url.getProtocol())) {
                    // IDE / exploded resources
                    Path sourcePath = Paths.get(url.toURI());

                    Files.walk(sourcePath).forEach(path -> {
                        try {
                            Path target = folder.resolve(resourcePath)
                                    .resolve(sourcePath.relativize(path).toString());

                            if (Files.isDirectory(path)) {
                                Files.createDirectories(target);
                            } else if (!target.toFile().exists()) {
                                Files.createDirectories(target.getParent());
                                Files.copy(path, target);
                                copiedFiles.add(target.toFile());
                            }
                        } catch (FileAlreadyExistsException ignored) {
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });

                } else if ("jar".equals(url.getProtocol())) {
                    JarURLConnection conn = (JarURLConnection) url.openConnection();
                    JarFile jar = conn.getJarFile();
                    String prefix = conn.getEntryName();
                    if (prefix == null) prefix = "";
                    if (!prefix.isEmpty() && !prefix.endsWith("/")) {
                        prefix += "/";
                    }

                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();

                        if (!name.startsWith(prefix)) continue;

                        String relativePath = name.substring(prefix.length());
                        if (relativePath.isEmpty()) continue; // skip the root itself

                        // FIX: prepend resourcePath to preserve top-level folder
                        Path target = folder.resolve(resourcePath).resolve(relativePath.replace("/", File.separator));

                        if (entry.isDirectory()) {
                            Files.createDirectories(target);
                        } else {
                            Files.createDirectories(target.getParent());
                            try (InputStream in = jar.getInputStream(entry)) {
                                Files.copy(in, target);
                                copiedFiles.add(target.toFile());
                            } catch (FileAlreadyExistsException ignored) {
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }
                    }
                } else {
                    throw new IOException("Unsupported protocol: " + url.getProtocol());
                }
                System.out.println(getInstallPath());
                System.out.println(copiedFiles);
                copiedFiles.stream().filter(f -> f.getPath().endsWith(".zip")).forEach(f -> unzip(f.toPath()));
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
        }

        return folder;
    }

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

    private static void unzip(Path zipPath) {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            zip.stream().parallel().forEach(entry -> {
                try {
                    Path out = zipPath.getParent().resolve(entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectories(out);
                        return;
                    }
                    Files.createDirectories(out.getParent());
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            System.out.println("unzip item: " + zipPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
