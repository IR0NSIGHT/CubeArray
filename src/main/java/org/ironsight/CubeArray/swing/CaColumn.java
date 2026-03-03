package org.ironsight.CubeArray.swing;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * hardcoded enum list of columns that the table (and model) can display.
 * comes with all info required to store across restarts and display to user
 */
public enum CaColumn {
    FILE("File", String.class, FileTableModel.defaultRenderer, "Name of the file", 240),
    LAST_CHANGED("Last Changed", Date.class, FileTableModel.dateRenderer, "Date when the file was last modified", 135),
    FILE_TYPE("File Type", String.class, FileTableModel.defaultRenderer, "File extension", 50),
    FILE_SIZE("File Size (MB)", Long.class, FileTableModel.fileSizeRenderer, "Size of the file", 60),
    DIMENSION_WIDTH("Width", Integer.class, FileTableModel.dimensionRenderer, "Width of the schematic (meters)", 40),
    DIMENSION_HEIGHT("Height", Integer.class, FileTableModel.dimensionRenderer, "Height of the schematic (meters)", 45),
    DIMENSION_DEPTH("Depth", Integer.class, FileTableModel.dimensionRenderer, "Depth of the schematic (meters)", 40),
    DIMENSION_DIAGONAL("Diagonal", Integer.class, FileTableModel.dimensionRenderer, "Diagonal of the schematic from edge to edge (meters)", 45),
    PATH("Path", String.class, FileTableModel.defaultRenderer, "Filepath where the file lives", 600),
    BLOCKS("Blocks", List.class, FileTableModel.stringListRenderer, "Blocktypes that are used in the schematic", 500),
    ENTITIES("Entities", List.class, FileTableModel.stringListRenderer, "Entities in the schematic", 200),
    TILE_ENTITIES("Tile Entities", List.class, FileTableModel.stringListRenderer, "Tile Entities in the schematic", 200),
    ATTRIBUTES("Attributes", HashMap.class, FileTableModel.attributesRenderer, "NBT Attributes attached to the schematic", 100),
    ;
    public final String displayName;
    public final Class<?> clazz;
    public final String tooltip;
    public final FileTableModel.StringConverter renderer;
    public final int defaultWidth;

    CaColumn(String name, Class<?> clazz, FileTableModel.StringConverter renderer, String tooltip, int defaultWidth) {
        this.tooltip = tooltip;
        this.displayName = name;
        this.clazz = clazz;
        this.defaultWidth = defaultWidth;
        this.renderer = renderer;

    }
}
