package com.example;

import org.joml.Vector3f;
import org.joml.Vector4f;
import org.pepsoft.minecraft.Direction;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.DefaultCustomObjectProvider;
import org.pepsoft.worldpainter.objects.WPObject;

import javax.vecmath.Point3i;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipException;

import static org.pepsoft.minecraft.Material.*;

public class SchemReader {

    //TEST
    public static void main(String[] args) {
        Material mat = Material.COBBLESTONE_STAIRS;
        System.out.println(mat);
    }
    public static List<WPObject> loadDefaultObjects(Path schemFolder) throws IOException {
        String self = "D:\\Repos\\cubeArray\\testSchems";
        String europe = "D:\\Repos\\worldpainter_related\\Market_Stalls_v0_1";
        String jerusalem = "C:/Users/Max1M/curseforge/minecraft/Instances/neoforge 1.12.1 camboi " +
                "shaders/config/worldedit/schematics";
        String dannyHouses = "D:\\Repos\\worldpainter_related\\Vanilla_plus_House_Pack-Dannypan";
        String server = "D:\\Repos\\mc_server_paper_1_19\\plugins\\WorldEdit\\schematics";
        File dir = schemFolder.toFile();
        List<Path> pathList = findAllFiles(dir.toPath());
        ArrayList<WPObject> schematics = new ArrayList<>();
        for (Path path : pathList) {
            File file = path.toFile();
            if (file.isFile()) {
                assert file.exists();
                try {
                    WPObject schematic = new DefaultCustomObjectProvider().loadObject(file);
                    schematics.add(schematic);
                } catch (IllegalArgumentException ex) {
                    System.out.println("ignore non-schem:" + file.getName());
                } catch (ZipException ex) {
                    System.err.println("cant load file:" + file.getName());
                    System.err.println(ex);
                }
            }
        }
        return schematics;
    }
    public static CubeSetup prepareData(List<WPObject> schematics) throws Exception {
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
            var dimensions = object.getDimensions();
            var offset = object.getOffset();
            for (int x = 0; x < object.getDimensions().x; x++) {
                for (int y = 0; y < object.getDimensions().y; y++) {
                    for (int z = object.getDimensions().z - 1; z >= 0; z--) {
                        Material mat = object.getMaterial(x, y, z);
                        if (mat != null && mat != Material.AIR) {

                            //test neighbours
                            boolean hasNonSolidNeighbour = false;
                            for (int xN = -1; xN <= 1 && !hasNonSolidNeighbour; xN++)
                                for (int yN = -1; yN <= 1 && !hasNonSolidNeighbour; yN++)
                                    for (int zN = -1; zN <= 1 && !hasNonSolidNeighbour; zN++) {
                                        if (xN == 0 && yN == 0 && zN == 0)
                                            continue;

                                        int xNN = x+xN, yNN = y+yN, zNN = z+zN;

                                        //always render edge blocks
                                        if (xNN < 0 || yNN < 0|| zNN < 0) {
                                            hasNonSolidNeighbour = true;
                                            continue;
                                        }
                                        if (xNN >= dimensions.x || yNN >= dimensions.y|| zNN  >= dimensions.z) {
                                            hasNonSolidNeighbour = true;
                                            continue;
                                        }

                                        Material neighbour = object.getMaterial(xNN, yNN, zNN);
                                        if (neighbour != null && !neighbour.solid)
                                            hasNonSolidNeighbour = true;
                                    }
                            if (!hasNonSolidNeighbour)
                                continue;

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

                            if (mat.name.contains("stairs")) { //add a bottom slab

                                Material slab = Material.get(mat.name.replace("stairs", "slab"));
                                if (Objects.equals(mat.getProperty(HALF), "top")) {
                                    slab = slab.withProperty(TYPE, "top");
                                } else {
                                    slab = slab.withProperty(TYPE, "bottom");
                                }
                                if (mat_to_palette_idx.containsKey(slab)) {
                                    materialPaletteIdx = mat_to_palette_idx.get(slab);
                                } else {
                                    materialPaletteIdx = maxColorIdx++;
                                    mat_to_palette_idx.put(slab, materialPaletteIdx);
                                }

                                positions.add(new Vector3f(x + offset.x + gridOffset.x, z + offset.z,
                                        y + offset.y + gridOffset.y));
                                blockTypeIndicesList.add(materialPaletteIdx);
                            }

                            if (mat.is(WATERLOGGED) || mat.watery) {
                                Material water = WATER;
                                if (mat_to_palette_idx.containsKey(water)) {
                                    materialPaletteIdx = mat_to_palette_idx.get(water);
                                } else {
                                    materialPaletteIdx = maxColorIdx++;
                                    mat_to_palette_idx.put(water, materialPaletteIdx);
                                }

                                positions.add(new Vector3f(x + offset.x + gridOffset.x, z + offset.z,
                                        y + offset.y + gridOffset.y));
                                blockTypeIndicesList.add(materialPaletteIdx);
                            }
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

            //ADD size and offset to palette
            if (mat.name.contains("slab") && mat.hasProperty(TYPE)) {
                if (Objects.equals(mat.getProperty(TYPE),"top")) {
                    offsetPalette[matIdx] = new Vector3f(0, 0.5f / 2, 0);
                    sizePalette[matIdx] = new Vector3f(1, 0.5f, 1);
                } else if (Objects.equals(mat.getProperty(TYPE),"bottom")) {
                    offsetPalette[matIdx] = new Vector3f(0, -0.5f / 2f, 0);
                    sizePalette[matIdx] = new Vector3f(1, 0.5f, 1);
                }

            } else {
                sizePalette[matIdx] = new Vector3f(1, 1, 1);
            }

            if (mat.name.contains("torch")) {
                sizePalette[matIdx] = new Vector3f(0.1f, 0.5f, 0.1f);
                offsetPalette[matIdx] = new Vector3f(0, 0.25f / 2f, 0);
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
            }
            if (mat.name.contains("water")) {
                offsetPalette[matIdx].y -= .1f; //shift water down slighty
                sizePalette[matIdx].x = 0.999f; //slightly less wide than 1x1 for waterlogged blocks
                sizePalette[matIdx].z = 0.999f;
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
                Vector3f size = new Vector3f(.8f, 1.8f, .1f);
                sizePalette[matIdx] = size;
                offsetPalette[matIdx] = new Vector3f(0, -.5f, (1 - size.z) / 2f);
                colorPalette[matIdx] = new Vector3f(1, 1, 1);
            }
            if (mat.name.endsWith("_door")) {
                Vector3f size = new Vector3f(1f, 1f, .2f);
                sizePalette[matIdx] = size;
                offsetPalette[matIdx] = new Vector3f(0, 0, (1 - size.z) / 2f);
            }

            if (mat.name.contains("ladder")) {
                Vector3f size = new Vector3f(1f, 1f, .01f);
                sizePalette[matIdx] = size;
                offsetPalette[matIdx] = new Vector3f(0, 0, (1 - size.z) / 2f);
            }

            if (mat.name.contains("stairs")) {
                Vector3f size = new Vector3f(1, .5f, .5f);
                sizePalette[matIdx] = size;
                if (Objects.equals(mat.getProperty(HALF), "top")) {
                    offsetPalette[matIdx] = new Vector3f(0, -(1 - size.y) / 2f, -(1 - size.z) / 2f); //shift up on y
                } else {
                    offsetPalette[matIdx] = new Vector3f(0, (1 - size.y) / 2f, -(1 - size.z) / 2f); //shift down on y
                }
            }

            if (mat.name.contains("_grass") || mat.vegetation && !mat.leafBlock)  {
                sizePalette[matIdx] = new Vector3f(1, 1, 0.0f);
                rotationPalette[matIdx] = new Vector3f( 0,(float) Math.toRadians(45),0);
            }

            //implicit: no rotation for north
            if (Direction.EAST.equals(mat.getDirection()) || mat.is(EAST)) {
                rotationPalette[matIdx] = new Vector3f(0, (float) Math.toRadians(90), 0);
            }
            if (Direction.SOUTH.equals(mat.getDirection()) || mat.is(SOUTH)) {
                rotationPalette[matIdx] = new Vector3f(0, (float) Math.toRadians(180), 0);
            }
            if (Direction.WEST.equals(mat.getDirection()) || mat.is(WEST)) {
                rotationPalette[matIdx] = new Vector3f(0, (float) Math.toRadians(270), 0);
            }

            if (mat.name.endsWith("_wall")) {
                Vector3f size = new Vector3f(0.5f, 1, 0.5f);
                Vector3f offset = new Vector3f(0, 0, 0);
                Vector3f rotation = new Vector3f(0, 0, 0);

                boolean north = !Objects.equals(mat.getProperty("north"), "none");
                boolean south = !Objects.equals(mat.getProperty("south"), "none");

                boolean east = !Objects.equals(mat.getProperty("east"), "none");
                boolean west = !Objects.equals(mat.getProperty("west"), "none");

                if (east) {
                    size.x += .25f;
                    offset.x += .125f;
                }
                if (west) {
                    size.x += .25f;
                    offset.x -= .125f;
                }
                if (south) {
                    size.z += .25f;
                    offset.z += .125f;
                }
                if (north) {
                    size.z += .25f;
                    offset.z -= .125f;
                }

                if (!(north || south || east || west)) {
                    size = new Vector3f(0.5f, .7f, 0.5f);
                }
                if (mat.is(UP))
                    size.y = 1;

                offset.y = -(1 - size.y) / 2f;

                sizePalette[matIdx] = size;
                offsetPalette[matIdx] = offset;
                rotationPalette[matIdx] = rotation;
            }
            if (mat.name.endsWith("_trapdoor")) {
                Vector3f size = new Vector3f(1f, 1f, .2f); //open to north
                Vector3f offset = new Vector3f(0, 0, .4f);
                Vector3f rotation = new Vector3f(0, 0, 0);

                if (!Objects.equals(mat.getProperty("open"), "true")) {
                    size = new Vector3f(1f, .2f, 1f);
                    offset.z = 0;
                    if (Objects.equals(mat.getProperty(HALF), "top")) {
                        offset.y = 0.4f;
                    } else {
                        offset.y = -.4f;
                    }
                } else if (mat.getProperty(FACING) == Direction.SOUTH) {
                    rotation.y = (float) Math.toRadians(180);
                } else if (mat.getProperty(FACING) == Direction.NORTH) {
                    rotation.y = (float) Math.toRadians(0);
                } else if (mat.getProperty(FACING) == Direction.EAST) {
                    rotation.y = (float) Math.toRadians(90);
                } else if (mat.getProperty(FACING) == Direction.WEST) {
                    rotation.y = (float) Math.toRadians(270);
                }

                sizePalette[matIdx] = size;
                offsetPalette[matIdx] = offset;
                rotationPalette[matIdx] = rotation;
            }
        }

        mat_to_palette_idx.keySet().stream().map(m -> m.name).sorted().forEach(System.out::println);
        SpriteSheet spriteSheet = new SpriteSheet(new File("C:\\Users\\Max1M\\Downloads\\Faithful 32x - 1.21.7"), mat_to_palette_idx.keySet());

        int[] blockTypeIndices = blockTypeIndicesList.stream().mapToInt(i -> i).toArray();

        Vector3f min = new Vector3f(Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE);
        Vector3f max = new Vector3f(Float.MIN_VALUE,Float.MIN_VALUE,Float.MIN_VALUE);
        positions.stream().forEach(p -> {
            min.x = Math.min(p.x,min.x); min.y= Math.min(p.y,min.y); min.z = Math.min(p.z,min.z);
            max.x = Math.max(p.x,max.x); max.y= Math.max(p.y,max.y); max.z = Math.max(p.z,max.z);
        });

        return new CubeSetup(positions.toArray(new Vector3f[0]),
                blockTypeIndices,
                colorPalette,
                sizePalette, offsetPalette, rotationPalette, spriteSheet.getUvCoords(mat_to_palette_idx), spriteSheet.getTextureAtlas(), min, max);
    }

    public static List<Path> findAllFiles(Path dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream
                    .filter(Files::isRegularFile) // only files, no directories
                    .collect(Collectors.toList());
        }
    }

    public static class CubeSetup {
        final Vector3f[] positions;
        final int[] colorIndices;
         //block colors by type
        final Vector3f[] colorPalette;
         // block dimensions by type
        final Vector3f[] sizePalette;
         //how to shift blocks from their origin at 0.5 0.5 0.5 (width height depth)
        final Vector3f[] offsetPalette;
        final Vector3f[] rotationPalette;
        final Vector4f[] uvCoordsPalette; //uv1 uv2 for each block type
        final BufferedImage textureAtlas;
        final Vector3f min;
        final Vector3f max;

        public CubeSetup(Vector3f[] positions, int[] colorIndices, Vector3f[] colorPalette, Vector3f[] sizePalette,
                         Vector3f[] offsetPalette, Vector3f[] rotationPalette, Vector4f[] uvCoordsPalette, BufferedImage textureAtlas, Vector3f min, Vector3f max) {
            this.positions = positions;
            this.colorIndices = colorIndices;
            this.colorPalette = colorPalette;
            this.sizePalette = sizePalette;
            this.offsetPalette = offsetPalette;
            this.rotationPalette = rotationPalette;
            this.uvCoordsPalette = uvCoordsPalette;
            this.textureAtlas = textureAtlas;
            this.min = min;

            this.max = max;
        }
    }
}
