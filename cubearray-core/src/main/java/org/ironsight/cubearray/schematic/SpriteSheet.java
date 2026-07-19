package org.ironsight.cubearray.schematic;

import static java.lang.Math.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.ironsight.cubearray.mcmodel.FaceTexture;
import org.ironsight.cubearray.platform.AppLogger;
import org.joml.Vector4f;
import org.pepsoft.minecraft.Material;

/**
 * Builds a texture atlas holding one 16x16 cell per distinct sprite that a material's model faces
 * reference, and records each cell's atlas rect keyed by (material, sprite name). This lets the
 * renderer texture every cube face independently: {@link FaceTexture#texture()} carries the
 * resolved sprite name for a face (e.g. "block/oak_log_top"), and {@link #uvFor(Material, String)}
 * maps that back to the atlas rect the shader samples.
 *
 * <p>Cells are keyed per material (not globally) so biome-tinted grayscale sprites (grass, leaves)
 * can be colourised per-tintindex with a hardcoded colour; see {@link #colorizeLeaf}.
 */
public class SpriteSheet {
  private static final Logger logger = AppLogger.get(SpriteSheet.class);
  private static final Vector4f NO_TEXTURE = new Vector4f(0, 0, 0, 0);

  // Hardcoded tint colors for each tintindex:
  //   0 = grass (plains-style ~#91BD59)
  //   1 = foliage (oak-leaf ~#77AB2F)
  //   2 = dry foliage (brown ~#8B6B4D)
  private static final int[] HARDCODED_TINTS = {
    0xFF91BD59,
    0xFF77AB2F,
    0xFF8B6B4D,
  };

  private static final String[] EXTENSIONS = {
    "",
    "s",
    "_block",
    "_top",
    "_block_top",
    "_front",
    "_planks",
    "_stage7",
    "_stage3",
    "_stage2",
    "_0"
  }; // Try adding these
  private static final String[] PREFIXES = {"", "infested_", "smooth_"}; // Try removing these
  private final BufferedImage textureAtlas;
  // (material -> (resolved sprite name -> atlas rect)); one cell per distinct sprite per material
  private final Map<Material, Map<String, Vector4f>> rects;

  /**
   * @param materialSprites for each render material, the map of resolved sprite names to tintindex
   *     (-1 = no tint, 0 = grass, 1 = foliage, 2 = dry foliage); a material with no textured faces
   *     maps to an empty map and simply gets no atlas cells.
   */
  public SpriteSheet(File texturePackDir, Map<Material, Map<String, Integer>> materialSprites)
      throws IOException {
    assert texturePackDir != null && texturePackDir.exists() && texturePackDir.isDirectory();
    var nameToFile = nameToFile(texturePackDir);
    int textureSize = 16; // FIXME dont hardcode
    rects = new HashMap<>(materialSprites.size());
    textureAtlas = buildAtlas(materialSprites, textureSize, nameToFile);
  }

  public HashMap<String, File> nameToFile(File texturePackDir) throws IOException {
    HashMap<String, File> result = new HashMap<>();
    if (texturePackDir == null || !texturePackDir.isDirectory()) {
      throw new IOException("texture pack not found: " + texturePackDir);
    }

    // Path inside the texture pack where block textures are stored
    File blockDir = new File(texturePackDir, "assets/minecraft/textures/block");

    if (!blockDir.exists() || !blockDir.isDirectory()) {
      throw new IOException("texture pack block directory not found: " + blockDir);
    }

    File[] files = blockDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
    if (files == null) {
      throw new IOException("texture pack does not contain any png files. " + blockDir);
    }

    for (File file : files) {
      String name = file.getName();
      if (name.endsWith(".png")) {
        name = name.substring(0, name.length() - 4); // remove ".png"
      }
      result.put(name, file);
    }

    return result;
  }

  /**
   * Resolves a model sprite reference (e.g. "minecraft:block/oak_log_top" or "block/oak_log") to a
   * texture file in the pack's block directory, falling back to the fuzzy name matching that used
   * to key textures by material simple-name when no exact file exists.
   */
  private File spriteFile(String sprite, HashMap<String, File> nameToFile) {
    String name = sprite;
    int colon = name.indexOf(':');
    if (colon >= 0) name = name.substring(colon + 1); // drop namespace
    int slash = name.lastIndexOf('/');
    if (slash >= 0) name = name.substring(slash + 1); // drop "block/" path prefix
    File exact = nameToFile.get(name);
    return exact != null ? exact : tryFindMatch(name, nameToFile);
  }

  private BufferedImage buildAtlas(
      Map<Material, Map<String, Integer>> materialSprites,
      int textureSize,
      HashMap<String, File> nameToFile)
      throws IOException {
    // one atlas cell per (material, sprite) pair
    int numTextures = 0;
    for (var sprites : materialSprites.values()) numTextures += sprites.size();
    int gridSize = Math.max(1, (int) Math.ceil(Math.sqrt(numTextures)));

    int atlasSize = 1;
    while (atlasSize < gridSize * textureSize) {
      atlasSize *= 2; // round up to next power of two
    }
    BufferedImage atlas = new BufferedImage(atlasSize, atlasSize, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = atlas.createGraphics();

    int i = 0;
    for (var matEntry : materialSprites.entrySet()) {
      Map<String, Vector4f> matRects = new LinkedHashMap<>();
      rects.put(matEntry.getKey(), matRects);

      for (var spriteEntry : matEntry.getValue().entrySet()) {
        String sprite = spriteEntry.getKey();
        int tintIndex = spriteEntry.getValue();

        File file = spriteFile(sprite, nameToFile);
        if (file == null) {
          matRects.put(sprite, NO_TEXTURE);
          continue;
        }

        int x = (i % gridSize) * textureSize;
        int y = (i / gridSize) * textureSize;
        BufferedImage tex = ImageIO.read(file);
        if (tintIndex >= 0 && tintIndex < HARDCODED_TINTS.length) {
          tex = colorizeLeaf(tex, HARDCODED_TINTS[tintIndex]);
        }
        g.drawImage(tex, x, y, textureSize, textureSize, null);

        float u1 = x / (float) atlasSize;
        float v1 = y / (float) atlasSize;
        float u2 = (x + textureSize) / (float) atlasSize;
        float v2 = (y + textureSize) / (float) atlasSize;
        matRects.put(sprite, new Vector4f(u1, v1, u2, v2));
        i++;
      }
    }
    g.dispose();

    return atlas;
  }

  public static BufferedImage colorizeLeaf(BufferedImage leafTexture, int avgColor) {
    int width = leafTexture.getWidth();
    int height = leafTexture.getHeight();

    BufferedImage colored = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

    int avgR = (avgColor >> 16) & 0xFF;
    int avgG = (avgColor >> 8) & 0xFF;
    int avgB = avgColor & 0xFF;

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int pixel = leafTexture.getRGB(x, y);

        int alpha = (pixel >> 24) & 0xFF;
        int gray = (pixel >> 16) & 0xFF; // assume grayscale: R=G=B

        // Scale average color by grayscale value
        int r = (avgR * gray) / 255;
        int g = (avgG * gray) / 255;
        int b = (avgB * gray) / 255;

        int newPixel = (alpha << 24) | (r << 16) | (g << 8) | b;
        colored.setRGB(x, y, newPixel);
      }
    }

    return colored;
  }

  private File tryFindMatch(String simpleName, HashMap<String, File> nameToFile) {
    if (simpleName.startsWith("stripped_")
        && simpleName.endsWith("_wood")) // idk why but worldpainter materials
      // list "stripped_..._wood when the texture is called stripped_..._log
      simpleName = simpleName.replace("_wood", "_log");

    do {
      for (String prefix : PREFIXES) {
        String subName =
            simpleName.startsWith(prefix) ? simpleName.substring(prefix.length()) : simpleName;
        for (String extension : EXTENSIONS) {
          File match = nameToFile.get(subName + extension);
          if (match != null) {
            return match;
          }
        }
      }
      simpleName = simpleName.substring(0, max(simpleName.lastIndexOf('_'), 0));
    } while (!simpleName.isEmpty());
    return null;
  }

  // TESTING
  public static void main(String[] args) throws IOException {
    File texturePackDir = new File("C:\\Users\\Max1M\\Downloads\\Faithful 32x - 1.21.7");
    var spriteSheet = new SpriteSheet(texturePackDir, Map.of());
  }

  public BufferedImage getTextureAtlas() {
    return textureAtlas;
  }

  /** The atlas rect for a material's sprite, or (0,0,0,0) if it has no cell (renders flat colour). */
  public Vector4f uvFor(Material mat, String sprite) {
    return rects.getOrDefault(mat, Map.of()).getOrDefault(sprite, NO_TEXTURE);
  }
}
