package org.ironsight.cubearray.ui;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ironsight.cubearray.edit.BlockGradient;
import org.ironsight.cubearray.edit.BlockReplacer;
import org.ironsight.cubearray.platform.AppLogger;
import org.ironsight.cubearray.render.CubeSetup;
import org.ironsight.cubearray.schematic.SchemReader;
import pitheguy.schemconvert.converter.Schematic;

public class GradientSchematicApplier {

    private static final Logger logger = AppLogger.get(GradientSchematicApplier.class);

    public static CubeSetup apply(List<File> files, BlockGradient gradient, Set<String> targetBlocks) {
        List<org.pepsoft.worldpainter.objects.WPObject> wpObjects = new ArrayList<>();
        List<File> tempFiles = new ArrayList<>();

        for (File file : files) {
            try {
                Schematic schematic = BlockReplacer.load(file);
                Schematic modified = applyGradient(schematic, gradient, targetBlocks);

                File tempFile = Files.createTempFile("gradient_", ".schem").toFile();
                tempFiles.add(tempFile);
                tempFile.deleteOnExit();

                BlockReplacer.write(modified, tempFile);

                org.pepsoft.worldpainter.objects.WPObject wpObject =
                    new org.pepsoft.worldpainter.DefaultCustomObjectProvider().loadObject(tempFile);
                wpObjects.add(wpObject);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to apply gradient to " + file.getName(), e);
            }
        }

        if (wpObjects.isEmpty()) return null;

        try {
            return SchemReader.prepareData(wpObjects, false);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to prepare CubeSetup", e);
            return null;
        }
    }

    static Schematic applyGradient(Schematic source, BlockGradient gradient, Set<String> targetBlocks) {
        int[] size = source.getSize();
        int xSize = size[0];
        int ySize = size[1];
        int zSize = size[2];

        Schematic.Builder builder = new Schematic.Builder(
            source.getSourceFile(), source.getDataVersion(), xSize, ySize, zSize);

        for (int x = 0; x < xSize; x++) {
            for (int y = 0; y < ySize; y++) {
                for (int z = 0; z < zSize; z++) {
                    String block = source.getBlock(x, y, z);
                    if (targetBlocks.contains(block)) {
                        block = gradient.getBlock(y);
                    }
                    builder.setBlockAt(x, y, z, block);
                }
            }
        }

        source.getBlockEntities()
            .forEach((pos, tag) -> builder.addBlockEntity(pos.x(), pos.y(), pos.z(), tag));
        source.getEntities()
            .forEach(entity -> builder.addEntity(entity.id(), entity.x(), entity.y(), entity.z(), entity.nbt()));

        return builder.build();
    }
}
