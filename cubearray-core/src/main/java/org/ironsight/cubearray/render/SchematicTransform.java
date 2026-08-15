package org.ironsight.cubearray.render;

import java.util.Objects;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** CPU-side transform and display name for one schematic transform slot. */
public record SchematicTransform(String name, Matrix4f matrix, Vector3f pivot) {
  public SchematicTransform {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(matrix, "matrix");
    Objects.requireNonNull(pivot, "pivot");
    matrix = new Matrix4f(matrix);
    pivot = new Vector3f(pivot);
  }

  public SchematicTransform(String name, Matrix4f matrix) {
    this(name, matrix, new Vector3f());
  }

  /** Returns a copy so callers cannot mutate the stored transform without versioning it. */
  @Override
  public Matrix4f matrix() {
    return new Matrix4f(matrix);
  }

  @Override
  public Vector3f pivot() {
    return new Vector3f(pivot);
  }
}
