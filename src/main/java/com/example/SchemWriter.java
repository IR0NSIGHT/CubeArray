package com.example;

import org.jnbt.*;

import java.io.*;
import java.util.zip.DeflaterOutputStream;
import java.util.*;
import java.util.zip.GZIPOutputStream;

public class SchemWriter {

    public static void createEmptySchematic(String filename) throws IOException {
        // --- Schematic size ---
        short width = 1, height = 1, length = 1;

        // --- Blocks (air) and data ---
        byte[] blocks = new byte[width * height * length]; // 0 = air
        byte[] data = new byte[width * height * length];   // metadata

        // --- Root compound ---
        Map<String, Tag> root = new HashMap<>();
        root.put("Width", new ShortTag("Width", width));
        root.put("Height", new ShortTag("Height", height));
        root.put("Length", new ShortTag("Length", length));
        root.put("Materials", new StringTag("Materials", "Alpha"));
        root.put("Blocks", new ByteArrayTag("Blocks", blocks));
        root.put("Data", new ByteArrayTag("Data", data));
        root.put("Entities", new ListTag<>("Entities", CompoundTag.class, new ArrayList<>()));
        root.put("TileEntities", new ListTag<>("TileEntities", CompoundTag.class, new ArrayList<>()));

        CompoundTag schematic = new CompoundTag("Schematic", root);

        // --- Write proper GZIP-compressed schematic ---
        try (FileOutputStream fos = new FileOutputStream(filename);
             GZIPOutputStream gos = new GZIPOutputStream(fos);
             NBTOutputStream nbtOut = new NBTOutputStream(gos)) {

            nbtOut.writeTag(schematic);
        }

        System.out.println("Empty schematic saved (GZIP) to: " + filename);
    }

    public static void main(String[] args) throws IOException {
        createEmptySchematic("example.schem");
    }
}
