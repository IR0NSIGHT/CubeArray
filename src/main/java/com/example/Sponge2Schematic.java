package com.example;

import org.jnbt.*;
import org.pepsoft.minecraft.AbstractNBTItem;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.objects.WPObject;

import javax.vecmath.Point3i;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import static org.pepsoft.minecraft.Constants.*;

public class Sponge2Schematic extends AbstractNBTItem {
    public static final String TAG_WIDTH = "Width";
    public static final String TAG_HEIGHT = "Height";
    public static final String TAG_LENGTH = "Length";
    public final int width, length, height;
    private final int[] blockData;
    HashMap<String, Integer> palette = new HashMap<>();

    public Sponge2Schematic(WPObject original) {
        this((short) original.getDimensions().x, (short) original.getDimensions().z,
                (short) original.getDimensions().y);
        Point3i dims = original.getDimensions();
        for (int x = 0; x < dims.x; x++) {
            for (int y = 0; y < dims.y; y++) {
                for (int z = 0; z < dims.z; z++) {
                    setBlockAt(x, y, z, materialToString(original.getMaterial(x, y, z)));
                }
            }
        }
    }

    public Sponge2Schematic(short width, short height, short length) {
        super(new CompoundTag("Schematic", new HashMap<>()));
        this.width = width;
        this.height = height;
        this.length = length;

        this.blockData = new int[width * length * height];
        setTag(TAG_VERSION, new IntTag(TAG_VERSION, 2));
        setTag(TAG_DATA_VERSION, new IntTag(TAG_DATA_VERSION, 1343/*mc 1.12.2*/));

        setTag(TAG_WIDTH, new ShortTag(TAG_WIDTH, width));
        setTag(TAG_HEIGHT, new ShortTag(TAG_HEIGHT, height));
        setTag(TAG_LENGTH, new ShortTag(TAG_LENGTH, length));
    }

    /**
     * @param x     width
     * @param y     height
     * @param z     length
     * @param block
     */
    public void setBlockAt(int x, int y, int z, String block) {
        if (!palette.containsKey(block))
            palette.put(block, palette.size());
        blockData[x + y * width + z * width * length] = palette.get(block);
    }

    public static void main(String[] args) throws IOException {
        Sponge2Schematic schem = new Sponge2Schematic((short) 10, (short) 20, (short) 30);
        String[] blockTypes = new String[]{
                "minecraft:oak_planks", "minecraft:stone_bricks", "minecraft:glass", "minecraft:glowstone",
                "minecraft:bookshelf"
        };
        int blockType = 0;
        for (int x = 0; x < schem.width; x++) {
            for (int y = 0; y < schem.height; y++) {
                for (int z = 0; z < schem.length; z++) {
                    schem.setBlockAt(x, y, z, blockTypes[blockType++ % blockTypes.length]);
                }
            }
        }
        schem.save("./testSchems/example.schem");
    }

    public void save(String filename) throws IOException {
        generatePaletteTag();
        setTag("BlockData", new IntArrayTag("BlockData", blockData));

        try (FileOutputStream fos = new FileOutputStream(filename);
             GZIPOutputStream gos = new GZIPOutputStream(fos);
             NBTOutputStream nbtOut = new NBTOutputStream(gos)) {
            nbtOut.writeTag(this.toNBT());
        }

        System.out.println("Schematic with single dirt block saved to: " + filename);
    }

    private void generatePaletteTag() {
        // material name to index (int)
        HashMap<String, Tag> tagPalette = new HashMap<>();
        for (String entry : this.palette.keySet()) {
            tagPalette.put(entry, new IntTag(entry, palette.get(entry)));
        }
        setTag(TAG_PALETTE, new CompoundTag(TAG_PALETTE, tagPalette));
        setTag("PaletteMax", new IntTag("PaletteMax", tagPalette.size()));
    }

    public static Sponge2Schematic createEmptySchematic(short width, short height, short length) {
        var schematic = new Sponge2Schematic(width, height, length);


        return schematic;
    }

    public String materialToString(Material material) {
        StringBuilder b = new StringBuilder(material.name);
        if (material.propertyDescriptors != null)
                b.append("[")
                .append(material.propertyDescriptors.keySet().stream()
                        .map(prop -> prop + "=" + material.getProperty(prop))
                        .collect(Collectors.joining(", ")))
                .append("]");
        return b.toString();
    }
}
