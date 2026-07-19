package org.ironsight.cubearray.render;

import java.awt.image.BufferedImage;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * GPU-ready render data for a grid of cubes: per-instance positions and block-type indices, plus
 * per-type palettes (color, size, offset, rotation, face UVs) and the shared texture atlas. This is
 * the shared data contract between the schematic-loading pipeline ({@code schematic.SchemReader},
 * which produces it) and the OpenGL renderer ({@link InstancedCubes}, which consumes it) — neither
 * depends on the other, only on this record.
 *
 * <p>Fields are public so callers in other packages (UI, tests) can read them directly (e.g. {@code
 * setup.positions.length}); {@link InstancedCubes} reads them per-instance during upload.
 */
public class CubeSetup {
  public final String name;
  public final Vector3f[] positions;
  public final int[] colorIndices;
  // block colors by type
  public final Vector3f[] colorPalette;
  // block dimensions by type
  public final Vector3f[] sizePalette;
  // how to shift blocks from their origin at 0.5 0.5 0.5 (width height depth)
  public final Vector3f[] offsetPalette;
  public final Vector3f[] rotationPalette;
  // atlas rect (u1,v1,u2,v2) per (face, block type), row-major by Face ordinal:
  // uvCoordsPalette[face.ordinal() * colorPalette.length + typeIdx]; uploaded as a 2D palette
  public final Vector4f[] uvCoordsPalette;
  public final BufferedImage textureAtlas;
  public final Vector3f min;
  public final Vector3f max;
  public final boolean useGrid;

  public CubeSetup(
      Vector3f[] positions,
      int[] colorIndices,
      Vector3f[] colorPalette,
      Vector3f[] sizePalette,
      Vector3f[] offsetPalette,
      Vector3f[] rotationPalette,
      Vector4f[] uvCoordsPalette,
      BufferedImage textureAtlas,
      Vector3f min,
      Vector3f max,
      String name,
      boolean useGrid) {
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
    this.name = name;
    this.useGrid = useGrid;
  }
}
