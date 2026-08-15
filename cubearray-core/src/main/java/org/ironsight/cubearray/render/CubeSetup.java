package org.ironsight.cubearray.render;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector3fc;

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
  public record TransformSnapshot(long version, Matrix4f[] matrices) {}

  public final String name;
  public final Vector3f[] positions;
  public final int[] colorIndices;
  /** Per-instance transform slot. Slot zero is reserved for the global axis markers. */
  public final int[] schematicIndices;
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
  private final SchematicTransform[] schematicTransforms;
  private final AtomicLong transformVersion = new AtomicLong();

  public CubeSetup(
      Vector3f[] positions,
      int[] colorIndices,
      int[] schematicIndices,
      Vector3f[] colorPalette,
      Vector3f[] sizePalette,
      Vector3f[] offsetPalette,
      Vector3f[] rotationPalette,
      Vector4f[] uvCoordsPalette,
      BufferedImage textureAtlas,
      Vector3f min,
      Vector3f max,
      String name,
      boolean useGrid,
      SchematicTransform[] schematicTransforms) {
    this.positions = positions;
    this.colorIndices = colorIndices;
    this.schematicIndices = schematicIndices;
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
    this.schematicTransforms = schematicTransforms.clone();
  }

  /** Compatibility constructor for setups containing one unnamed schematic. */
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
    this(
        positions,
        colorIndices,
        new int[positions.length],
        colorPalette,
        sizePalette,
        offsetPalette,
        rotationPalette,
        uvCoordsPalette,
        textureAtlas,
        min,
        max,
        name,
        useGrid,
        new SchematicTransform[] {new SchematicTransform("global axis", new Matrix4f())});
  }

  public synchronized List<SchematicTransform> schematicTransforms() {
    return List.copyOf(Arrays.asList(schematicTransforms));
  }

  public synchronized void setSchematicTransform(int index, Matrix4f matrix) {
    SchematicTransform current = schematicTransforms[index];
    schematicTransforms[index] = new SchematicTransform(current.name(), matrix, current.pivot());
    transformVersion.incrementAndGet();
  }

  /** Rotates every transform around the world up axis. */
  public synchronized void rotateSchematicTransforms(float angleRadians) {
    rotateSchematicTransforms(angleRadians, 0f);
  }

  /** Rotates every transform around its local pivot. */
  public synchronized void rotateSchematicTransforms(float yawRadians, float pitchRadians) {
    if ((yawRadians == 0f && pitchRadians == 0f) || schematicTransforms.length == 0) return;
    for (int i = 0; i < schematicTransforms.length; i++) {
      SchematicTransform current = schematicTransforms[i];
      Vector3f pivot = current.pivot();
      Matrix4f matrix = current.matrix();
      matrix
          .translate(pivot)
          .rotateY(yawRadians)
          .rotateX(pitchRadians)
          .translate(-pivot.x, -pivot.y, -pivot.z);
      schematicTransforms[i] =
          new SchematicTransform(current.name(), matrix, pivot);
    }
    transformVersion.incrementAndGet();
  }

  /** Rotates every transform around its local up axis and the supplied world-space pitch axis. */
  public synchronized void rotateSchematicTransformsAroundLocalUp(
      float yawRadians, float pitchRadians, Vector3fc pitchAxis) {
    if ((yawRadians == 0f && pitchRadians == 0f) || schematicTransforms.length == 0) return;
    for (int i = 0; i < schematicTransforms.length; i++) {
      SchematicTransform current = schematicTransforms[i];
      Matrix4f currentMatrix = current.matrix();
      Vector3f worldPivot = currentMatrix.transformPosition(new Vector3f(current.pivot()));
      Vector3f localUp =
          currentMatrix.transformDirection(new Vector3f(0, 1, 0)).normalize();
      Matrix4f delta =
          new Matrix4f()
              .translate(worldPivot)
              .rotate(yawRadians, localUp)
              .rotate(pitchRadians, pitchAxis)
              .translate(-worldPivot.x, -worldPivot.y, -worldPivot.z);
      schematicTransforms[i] =
          new SchematicTransform(current.name(), delta.mul(currentMatrix), current.pivot());
    }
    transformVersion.incrementAndGet();
  }

  public synchronized SchematicTransform schematicTransform(int index) {
    SchematicTransform transform = schematicTransforms[index];
    return new SchematicTransform(transform.name(), transform.matrix(), transform.pivot());
  }

  public long transformVersion() {
    return transformVersion.get();
  }

  /** Snapshot used by the render thread while preparing the GPU upload. */
  public synchronized Matrix4f[] transformMatrixSnapshot() {
    Matrix4f[] snapshot = new Matrix4f[schematicTransforms.length];
    for (int i = 0; i < snapshot.length; i++) snapshot[i] = schematicTransforms[i].matrix();
    return snapshot;
  }

  public synchronized TransformSnapshot transformSnapshot() {
    return new TransformSnapshot(transformVersion.get(), transformMatrixSnapshot());
  }
}
