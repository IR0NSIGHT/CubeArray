package com.example;

import org.joml.Vector3f;
import org.pepsoft.minecraft.Direction;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.DefaultCustomObjectProvider;
import org.pepsoft.worldpainter.objects.WPObject;

import javax.vecmath.Point3i;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.pepsoft.minecraft.Constants.MC_WEST;
import static org.pepsoft.minecraft.Material.*;

public class SchemReader {

    //TEST
    public static void main(String[] args) {
        Material mat = Material.COBBLESTONE_STAIRS;
        System.out.println(mat);
    }

    public static CubeSetup loadNbtFile() throws Exception {

        String europe = "D:\\Repos\\worldpainter_related\\Vanilla_plus_House_Pack-Dannypan\\Schematics\\Desert";
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
        List<Integer> blockTypeIndicesList = new ArrayList<>();

        HashMap<Material, Integer> mat_to_palette_idx = new HashMap<>();
        int maxColorIdx = 0;
        Point3i gridOffset = new Point3i(0, 0, 0);
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
                            //      if (!mat.name.contains("carpet"))
                            //          continue; //DEBUG
                            positions.add(new Vector3f(x + offset.x + gridOffset.x, z + offset.z,
                                    y + offset.y + gridOffset.y));
                            int materialPaletteIdx = 0;
                            if (mat_to_palette_idx.containsKey(mat)) {
                                materialPaletteIdx = mat_to_palette_idx.get(mat);
                            } else {
                                materialPaletteIdx = maxColorIdx++;
                                mat_to_palette_idx.put(mat, materialPaletteIdx);
                            }
                            blockTypeIndicesList.add(materialPaletteIdx);

                        }
                    }
                }
            }

            maxDepth = Math.max(maxDepth, object.getDimensions().y);
            gridOffset.x += object.getDimensions().x + spacer;
            index++;
        }

        Vector3f[] colorPalette = new Vector3f[mat_to_palette_idx.size()];
        Arrays.fill(colorPalette, new Vector3f(1, 1, 1));

        Vector3f[] sizePalette = new Vector3f[mat_to_palette_idx.size()];
        Arrays.fill(sizePalette, new Vector3f(1, 1, 1));

        Vector3f[] offsetPalette = new Vector3f[mat_to_palette_idx.size()];
        Arrays.fill(offsetPalette, new Vector3f(0, 0, 0));

        Vector3f[] rotationPalette = new Vector3f[mat_to_palette_idx.size()];
        Arrays.fill(rotationPalette, new Vector3f(0, 0, 0));

        for (var entry : mat_to_palette_idx.entrySet()) {
            Material mat = entry.getKey();
            int matIdx = entry.getValue();

            // ADD color to palette
            Color color = new Color(mat.colour);
            colorPalette[matIdx] = new Vector3f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f);
            System.out.println(mat);

            //ADD size and offset to palette
            if (mat.name.contains("slab") && mat.hasProperty(TYPE)) {
                if (mat.getProperty(TYPE).equals("top"))
                    offsetPalette[matIdx] = new Vector3f(0, 0.5f / 2, 0);
                if (mat.getProperty(TYPE).equals("bottom"))
                    offsetPalette[matIdx] = new Vector3f(0, -0.5f / 2f, 0);
                sizePalette[matIdx] = new Vector3f(1, 0.5f, 1);
            } else {
                sizePalette[matIdx] = new Vector3f(1, 1, 1);
            }
            if (mat.name.contains("lantern")) {
                sizePalette[matIdx] = new Vector3f(0.4f, 0.7f, 0.4f);
                offsetPalette[matIdx] = new Vector3f(0, 0.3f / 2f, 0);
            }
            if (mat.hasProperty(LEVEL)) {
                int level = mat.getProperty(LEVEL, 0);
                if (level == 8)
                    level = 0; //falling water is a fully block
                sizePalette[matIdx] = new Vector3f(1, 1 - (level / 8f), 1); //zero is full, 8 is empty
                offsetPalette[matIdx] = new Vector3f(0, -(level / 8f) / 2f, 0);
                if (mat.name.contains("water")) {
                    offsetPalette[matIdx].y -= .1f; //shift water down slighty
                }
            }

            if (mat.name.contains("carpet")) {
                sizePalette[matIdx] = new Vector3f(1, (1 / 16f), 1);
                offsetPalette[matIdx] = new Vector3f(0, -sizePalette[matIdx].y / 2f, 0);
            }

            if (mat.name.contains("fence")) {
                boolean facing = (mat.is(WEST) || mat.is(NORTH) || mat.is(EAST) || mat.is(SOUTH));
                if (!facing) {
                    sizePalette[matIdx] = new Vector3f((1 / 4f), 1f, (1 / 4f));
                }
            }

            if (mat.name.contains("banner")) {
                Vector3f size = new Vector3f(.8f, 2f, .1f);
                sizePalette[matIdx] = size;
                offsetPalette[matIdx] = new Vector3f(0, -.5f, -(1-size.z)/2f);
                colorPalette[matIdx] = new Vector3f(1, 1, 1);
            }

            if (mat.vegetation) {
                sizePalette[matIdx] = new Vector3f(0.6f, 1f, 0.6f);
            }

            //implicit: no rotation for north
            if (Direction.EAST.equals(mat.getDirection()) || mat.is(EAST)) {
                rotationPalette[matIdx] = new Vector3f(0, 90, 0);
            }
            if (Direction.SOUTH.equals(mat.getDirection()) || mat.is(SOUTH)) {
                rotationPalette[matIdx] = new Vector3f(0, 180, 0);
            }
            if (Direction.WEST.equals(mat.getDirection())  || mat.is(WEST)) {
                rotationPalette[matIdx] = new Vector3f(0, 270, 0);
            }
        }


        int[] blockTypeIndices = blockTypeIndicesList.stream().mapToInt(i -> i).toArray();

        return new CubeSetup(positions.toArray(new Vector3f[0]),
                blockTypeIndices,
                colorPalette,
                sizePalette, offsetPalette, rotationPalette);
    }

    public static List<Path> findAllFiles(Path dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream
                    .filter(Files::isRegularFile) // only files, no directories
                    .collect(Collectors.toList());
        }
    }

    public static class CubeSetup {
        Vector3f[] positions;
        int[] colorIndices;
        //block colors by type
        Vector3f[] colorPalette;
        // block dimensions by type
        Vector3f[] sizePalette;
        //how to shift blocks from their origin at 0.5 0.5 0.5 (width height depth)
        Vector3f[] offsetPalette;
        Vector3f[] rotationPalette;

        public CubeSetup(Vector3f[] positions, int[] colorIndices, Vector3f[] colorPalette, Vector3f[] sizePalette,
                         Vector3f[] offsetPalette, Vector3f[] rotationPalette) {
            this.positions = positions;
            this.colorIndices = colorIndices;
            this.colorPalette = colorPalette;
            this.sizePalette = sizePalette;
            this.offsetPalette = offsetPalette;
            this.rotationPalette = rotationPalette;
        }
    }
}
