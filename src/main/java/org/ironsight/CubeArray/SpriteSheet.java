package org.ironsight.CubeArray;

import org.joml.Vector4f;
import org.pepsoft.minecraft.Material;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.lang.Math.*;

public class SpriteSheet {
    private static final String[] EXTENSIONS = {"", "s", "_block", "_top", "_block_top", "_front", "_planks",
            "_stage7", "_stage3", "_stage2", "_0"}; // Try adding these
    private static final String[] PREFIXES = {"", "infested_", "smooth_"}; // Try removing these
    private final BufferedImage textureAtlas;
    private final HashMap<Material, Vector4f> uvCoords;

    public SpriteSheet(File texturePackDir, Set<Material> materialSet) throws IOException {
        assert texturePackDir != null && texturePackDir.exists() && texturePackDir.isDirectory();
        var nameToFile = nameToFile(texturePackDir);
        HashMap<Material, File> matToFile = matToFile(nameToFile, materialSet);
        int textureSize = 16; // FIXME dont hardcode
        uvCoords = new HashMap<>(materialSet.size());
        textureAtlas = buildAtlas(matToFile, textureSize, uvCoords, nameToFile);
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

    public HashMap<Material, File> matToFile(HashMap<String, File> nameToFile, Set<Material> materialSet) {
        HashMap<Material, File> outMap = new HashMap<>();
        for (Material mat : materialSet) {
            String name = mat.simpleName;
            File textureFile = tryFindMatch(name, nameToFile);

            if (textureFile != null) {
                outMap.put(mat, textureFile);
            } else {
                System.err.println("CAN NOT LOCATE TEXTURE FOR: " + mat.simpleName);
            }
        }
        return outMap;
    }

    public BufferedImage buildAtlas(HashMap<Material, File> matToFile, int textureSize,
                                    HashMap<Material, Vector4f> uvCoords, HashMap<String, File> nameToFile) throws IOException {
        int numTextures = matToFile.size();
        int gridSize = (int) Math.ceil(Math.sqrt(numTextures));

        int atlasSize = 1;
        while (atlasSize < gridSize * textureSize) {
            atlasSize *= 2; // round up to next power of two
        }
        atlasSize*=2; // 2 textures per type (one side, one top)
        BufferedImage atlas = new BufferedImage(atlasSize, atlasSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();

        int i = 0;
        for (Map.Entry<Material, File> entry : matToFile.entrySet()) {
            int x = (i % gridSize) * textureSize;
            int y = (i / gridSize) * textureSize;

            {   //draw side
                BufferedImage tex = ImageIO.read(entry.getValue());
                if (entry.getKey().leafBlock || entry.getKey().vegetation || entry.getKey().name.contains("grass")) {
                    tex = colorizeLeaf(tex, entry.getKey().colour);
                }
                g.drawImage(tex, x, y, textureSize, textureSize, null);
            }

            File topMatFile = nameToFile.getOrDefault(entry.getValue().getName().replace(".png","") + "_top", entry.getValue());
            {   //draw top
                BufferedImage tex = ImageIO.read(topMatFile);
                if (entry.getKey().leafBlock || entry.getKey().vegetation || entry.getKey().name.contains("grass")) {
                    tex = colorizeLeaf(tex, entry.getKey().colour);
                }
                g.drawImage(tex, x + textureSize, y, textureSize, textureSize, null);
            }



            float u1 = x / (float) atlasSize;
            float v1 = y / (float) atlasSize;
            float u2 = (x + textureSize) / (float) atlasSize;
            float v2 = (y + textureSize) / (float) atlasSize;
            uvCoords.put(entry.getKey(), new Vector4f(u1, v1, u2, v2));


            i+=2;
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
                int gray  = (pixel >> 16) & 0xFF; // assume grayscale: R=G=B

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
        if (simpleName.startsWith("stripped_") && simpleName.endsWith("_wood")) //idk why but worldpainter materials
            // list "stripped_..._wood when the texture is called stripped_..._log
            simpleName = simpleName.replace("_wood", "_log");

        do {
            for (String prefix : PREFIXES) {
                String subName = simpleName.startsWith(prefix) ? simpleName.substring(prefix.length()) : simpleName;
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

    //TESTING
    public static void main(String[] args) throws IOException {
        File texturePackDir = new File("C:\\Users\\Max1M\\Downloads\\Faithful 32x - 1.21.7");
        var spriteSheet = new SpriteSheet(texturePackDir, Set.of());

    }

    public BufferedImage getTextureAtlas() {
        return textureAtlas;
    }

    public HashMap<Material, Vector4f> getUvCoords() {
        return uvCoords;
    }

    public Vector4f[] getUvCoords(HashMap<Material, Integer> materialToId) {
       Vector4f[] uvCoordList = new Vector4f[materialToId.size()];
        for (var entry : materialToId.entrySet()) {
            var uvCoord = uvCoords.getOrDefault(entry.getKey(), new Vector4f(0,0,0,0));
            assert uvCoord != null;
            uvCoordList[entry.getValue()] = uvCoord;
        }
        return uvCoordList;
    }
}
