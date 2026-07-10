package org.ironsight.cubearray.platform;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipFile;
import org.apache.commons.io.FilenameUtils;

public class ResourceUtils {
  private static final Logger logger = AppLogger.get(ResourceUtils.class);
  public static final Set<String> SUPPORTED_FILE_TYPES =
      new HashSet<>(Arrays.asList(".bo2", ".bo3", ".nbt", ".schematic", ".schem"));
  private static final Set<String> SUPPORTED_FILE_EXTENSIONS =
      SUPPORTED_FILE_TYPES.stream().map(s -> s.replace(".", "")).collect(Collectors.toSet());
  public static String TEXTURE_RESOURCES = "textures/";
  public static String TEXTURE_PACK_ROOT = "textures/Faithful_32x_1_21_7/";
  // bundled complete vanilla asset layer (models + blockstates); see
  // src/main/resources/vanilla_assets/README.md
  public static String VANILLA_ASSETS_RESOURCES = "vanilla_assets/";
  public static String SCHEMATICS_ROOT = "schematics/", SCHEMATIC_RESOURCES = "schematics/";

  public static final Predicate<? super Path> isSupportedSchematicType =
      (f) -> {
        String ext = FilenameUtils.getExtension(f.toFile().getName());
        return SUPPORTED_FILE_EXTENSIONS.contains(ext);
      };

  public static List<Path> getDefaultSchematics() {
    Path defaultSchematicsDir =
        ResourceUtils.getInstallPath().resolve(ResourceUtils.SCHEMATICS_ROOT);
    if (Files.exists(defaultSchematicsDir)) {
      try (Stream<Path> paths = Files.walk(defaultSchematicsDir)) {
        return paths
            .filter(Files::isRegularFile)
            .filter(
                f ->
                    SUPPORTED_FILE_TYPES.stream()
                        .anyMatch(
                            e ->
                                f.getFileName().toString().toLowerCase().endsWith(e.toLowerCase())))
            .collect(Collectors.toList());
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to scan schematics directory", e);
      }
    }
    return List.of();
  }

  public static Path getScreenshotPath() {
    Path main = getInstallPath().resolve("screenshots");
    try {
      Files.createDirectories(main);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return main;
  }

  public static Path getRenderPathForFile(File file) {
    String hash =
        Integer.toHexString(
            file.toPath().toAbsolutePath().normalize().toString().hashCode());
    String name = file.getName();
    int dot = name.lastIndexOf('.');
    String baseName = (dot > 0) ? name.substring(0, dot) : name;
    return getInstallPath().resolve("renders").resolve(baseName + "__" + hash + ".png");
  }

  public static boolean needsNewRender(File schematicFile) {
    Path renderPath = getRenderPathForFile(schematicFile);
    if (!Files.exists(renderPath)) return true;
    return schematicFile.lastModified() > renderPath.toFile().lastModified();
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
      base = Paths.get(System.getProperty("user.home"), "Library", "Application Support");

    } else {
      // Linux / Unix
      String xdg = System.getenv("XDG_DATA_HOME");
      if (xdg != null && !xdg.isEmpty()) {
        base = Paths.get(xdg);
      } else {
        base = Paths.get(System.getProperty("user.home"), ".local", "share");
      }
    }

    Path installPath = base.resolve(AppInfo.APP_NAME);

    try {
      Files.createDirectories(installPath);
    } catch (Exception e) {
      throw new RuntimeException("Failed to create install directory", e);
    }
    if (installPath == null) throw new RuntimeException("Failed to get installpath.");

    return installPath;
  }

  // Example usage
  public static void main(String[] args) throws IOException {
    Path tmpFolder = copyResourcesToFile("textures/texture.bmp", SCHEMATICS_ROOT);
    System.out.println("Resources copied to: " + tmpFolder); // main() test only

    // You can now use them as normal files:
    Path configFile = tmpFolder.resolve("textures/texture.bmp");
    System.out.println("Config exists: " + Files.exists(configFile)); // main() test only
  }

  /**
   * copies the given resources into it the install folder on plate, unzips all zips.
   *
   * @param resourcePaths Array of resource paths relative to classpath (e.g., "data/config.json")
   * @return Path to the temporary folder
   * @throws IOException if an I/O error occurs
   */
  public static Path copyResourcesToFile(String... resourcePaths) throws IOException {

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

          Files.walk(sourcePath)
              .forEach(
                  path -> {
                    try {
                      Path target =
                          folder
                              .resolve(resourcePath)
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
            Path target =
                folder.resolve(resourcePath).resolve(relativePath.replace("/", File.separator));

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
        logger.info("Resources copied to: " + folder);
        logger.fine("Copied files: " + copiedFiles);
        copiedFiles.stream()
            .filter(f -> f.getPath().endsWith(".zip"))
            .forEach(f -> unzip(f.toPath()));
      } catch (URISyntaxException e) {
        throw new IOException(e);
      }
    }

    return folder;
  }

  private static void unzip(Path zipPath) {
    try (ZipFile zip = new ZipFile(zipPath.toFile())) {
      zip.stream()
          .parallel()
          .forEach(
              entry -> {
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
      logger.info("unzip item: " + zipPath);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a human-readable label for the schematic format of the given file, e.g. "schem (Sponge
   * v3)", "schem (Sponge v2)", "nbt", "litematic", "bo2", etc.
   *
   * <p>For .schem files the version is detected by peeking at the gzip-compressed NBT structure
   * without fully parsing it: - root compound contains "Schematic" key → Sponge v3 - root compound
   * has "Version" == 2 → Sponge v2 - root compound has "Version" == 1 → Sponge v1
   *
   * <p>All other extensions are returned as-is (lowercased, without the dot).
   */
  public static String detectSchematicType(File file) {
    String name = file.getName();
    String ext = FilenameUtils.getExtension(name).toLowerCase();
    if (!ext.equals("schem")) {
      return ext;
    }
    // Peek inside the gzip NBT to distinguish Sponge v2 vs v3
    try {
      int spongeVersion = readSpongeVersion(file);
      return switch (spongeVersion) {
        case 3 -> "sponge3";
        case 2 -> "sponge2";
        case 1 -> "sponge1";
        default -> "schem";
      };
    } catch (IOException e) {
      return "schem";
    }
  }

  /**
   * Reads just enough of the gzip-compressed NBT to determine the Sponge version.
   *
   * <p>NBT binary layout (after gzip decompression): byte tagType (10 = TAG_Compound for the root)
   * short nameLen bytes name[nameLen] ... child tags ...
   *
   * <p>Sponge v3 wraps everything under a child compound named "Schematic". Sponge v2 stores
   * "Version" (TAG_Int = 3) directly as the first or second child.
   *
   * <p>We scan forward through the root compound's direct children looking for: - a TAG_Compound
   * (10) named "Schematic" → v3 - a TAG_Int ( 3) named "Version" → read its int value
   *
   * <p>We stop after reading enough to make a decision (a few hundred bytes at most).
   */
  private static int readSpongeVersion(File file) throws IOException {
    try (InputStream fis = java.nio.file.Files.newInputStream(file.toPath());
        GZIPInputStream gzip = new GZIPInputStream(fis);
        DataInputStream in = new DataInputStream(gzip)) {

      // read root tag: must be TAG_Compound (10)
      int rootType = in.readUnsignedByte();
      if (rootType != 10) return 0; // not a compound, give up
      skipNbtString(in); // skip root compound name

      // iterate direct children of root compound
      for (int guard = 0; guard < 64; guard++) {
        int childType = in.readUnsignedByte();
        if (childType == 0) break; // TAG_End

        String childName = readNbtString(in);

        if (childType == 10 && "Schematic".equals(childName)) {
          return 3; // Sponge v3: root wraps everything under "Schematic"
        }
        if (childType == 3 && "Version".equals(childName)) {
          return in.readInt(); // TAG_Int value
        }

        // skip over the payload of this child so we can read the next one
        skipNbtPayload(in, childType);
      }
      return 0;
    }
  }

  private static String readNbtString(DataInputStream in) throws IOException {
    int len = in.readUnsignedShort();
    byte[] bytes = in.readNBytes(len);
    return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
  }

  private static void skipNbtString(DataInputStream in) throws IOException {
    int len = in.readUnsignedShort();
    in.skipNBytes(len);
  }

  /**
   * Skips over a single NBT tag payload of the given type. We only need to handle the types that
   * plausibly appear before "Version" or "Schematic" in a real-world Sponge schematic root
   * compound.
   */
  private static void skipNbtPayload(DataInputStream in, int type) throws IOException {
    switch (type) {
      case 1 -> in.skipNBytes(1); // TAG_Byte
      case 2 -> in.skipNBytes(2); // TAG_Short
      case 3 -> in.skipNBytes(4); // TAG_Int
      case 4 -> in.skipNBytes(8); // TAG_Long
      case 5 -> in.skipNBytes(4); // TAG_Float
      case 6 -> in.skipNBytes(8); // TAG_Double
      case 7 -> in.skipNBytes(in.readInt()); // TAG_Byte_Array
      case 8 -> skipNbtString(in); // TAG_String
      case 9 -> skipNbtList(in); // TAG_List
      case 10 -> skipNbtCompound(in); // TAG_Compound
      case 11 -> in.skipNBytes((long) in.readInt() * 4); // TAG_Int_Array
      case 12 -> in.skipNBytes((long) in.readInt() * 8); // TAG_Long_Array
      default -> throw new IOException("Unknown NBT tag type: " + type);
    }
  }

  private static void skipNbtList(DataInputStream in) throws IOException {
    int elemType = in.readUnsignedByte();
    int size = in.readInt();
    for (int i = 0; i < size; i++) skipNbtPayload(in, elemType);
  }

  private static void skipNbtCompound(DataInputStream in) throws IOException {
    for (; ; ) {
      int t = in.readUnsignedByte();
      if (t == 0) return; // TAG_End
      skipNbtString(in);
      skipNbtPayload(in, t);
    }
  }
}
