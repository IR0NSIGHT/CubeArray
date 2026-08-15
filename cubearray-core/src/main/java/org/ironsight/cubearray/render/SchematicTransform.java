package org.ironsight.cubearray.render;

import java.util.Objects;
import org.joml.Matrix4f;

/** CPU-side transform and display name for one schematic transform slot. */
public record SchematicTransform(String name, Matrix4f matrix) {
  public SchematicTransform {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(matrix, "matrix");
    matrix = new Matrix4f(matrix);
  }

  /** Returns a copy so callers cannot mutate the stored transform without versioning it. */
  @Override
  public Matrix4f matrix() {
    return new Matrix4f(matrix);
  }
}
