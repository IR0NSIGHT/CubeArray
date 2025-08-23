package com.example;

import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.jnbt.*;

import org.joml.Vector3f; // or your own Vector3f class
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.DefaultCustomObjectProvider;
import org.pepsoft.worldpainter.layers.bo2.Schem;
import org.pepsoft.worldpainter.objects.WPObject;

import javax.vecmath.Point3i;

public class SchemReader {

    public static Vector3f getColorForBlockType(String blockType) {
        switch (blockType) {
            case "minecraft:stone":
                return new Vector3f(0.5f, 0.5f, 0.5f); // gray
            case "minecraft:tuff":
                return new Vector3f(0.4f, 0.4f, 0.5f); // bluish-gray
            case "minecraft:andesite":
                return new Vector3f(0.6f, 0.6f, 0.6f); // light gray
            case "minecraft:granite":
                return new Vector3f(0.7f, 0.5f, 0.5f); // pinkish
            case "minecraft:diorite":
                return new Vector3f(0.9f, 0.9f, 0.9f); // white
            case "minecraft:grass_block":
                return new Vector3f(0.3f, 0.6f, 0.2f); // green
            case "minecraft:dirt":
                return new Vector3f(0.55f, 0.27f, 0.07f); // brown
            case "minecraft:sand":
                return new Vector3f(0.9f, 0.85f, 0.6f); // light tan
            case "minecraft:water":
                return new Vector3f(0.2f, 0.4f, 0.8f); // blue
            case "minecraft:leaves":
            case "minecraft:oak_leaves":
                return new Vector3f(0.2f, 0.5f, 0.2f); // dark green
            default:
                System.out.println("Unknown block type: " + blockType);
                return new Vector3f(0.5f, 0.0f, 0.5f); // purple (default)
        }
    }

    public static List<Path> findAllFiles(Path dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream
                    .filter(Files::isRegularFile) // only files, no directories
                    .collect(Collectors.toList());
        }
    }

    public static Vector3f[][] loadNbtFile() throws Exception {

        String europe = "D:\\Repos\\worldpainter_related";
        String jerusalem = "C:/Users/Max1M/curseforge/minecraft/Instances/neoforge 1.12.1 camboi " +
                "shaders/config/worldedit/schematics";
        File dir = new File(europe);
        List<Path> pathList = findAllFiles(dir.toPath());
        ArrayList<WPObject> schematics = new ArrayList<>();
        for (Path path : pathList) {
            File file = path.toFile();
            if (file.isFile()) {
                assert file.exists();
                if (!file.getPath().toLowerCase().endsWith(".schem"))
                    continue;
                WPObject schematic = new DefaultCustomObjectProvider().loadObject(file);
                schematics.add(schematic);
            }
        }


        List<Vector3f> positions = new ArrayList<>();
        List<Vector3f> blockTypes = new ArrayList<>();
        Point3i gridOffset = new Point3i(0,0,0);
        int spacer = 10;
        int maxDepth = 0;
        int index = 0;
        for (WPObject object : schematics) {

            if (index % 20 == 0) {
                gridOffset.y += maxDepth;
                gridOffset.x = 0;
                maxDepth = 0;
            }

            var offset = object.getOffset();
            for (int x = 0; x < object.getDimensions().x; x++) {
                for (int y = 0; y < object.getDimensions().y; y++) {
                    for (int z = object.getDimensions().z - 1; z >= 0; z--) {
                        Material mat = object.getMaterial(x, y, z);
                        if (mat != null && mat != Material.AIR) {
                            positions.add(new Vector3f(x + offset.x + gridOffset.x, z + offset.z, y + offset.y + gridOffset.y));
                            Color c = new Color(mat.colour);
                            blockTypes.add(new Vector3f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f));
                        }
                    }
                }
            }


            maxDepth = Math.max(maxDepth,object.getDimensions().y);
            gridOffset.x += object.getDimensions().x + spacer;
            index++;
        }


        Vector3f[] colors = blockTypes.toArray(Vector3f[]::new);
        Vector3f[] result = positions.toArray(new Vector3f[0]);
        System.out.println("Loaded " + result.length + " block positions.");
        return new Vector3f[][]{result, colors};
    }
}
