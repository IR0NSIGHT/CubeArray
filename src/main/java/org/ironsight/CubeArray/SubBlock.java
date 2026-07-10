package org.ironsight.CubeArray;

import java.util.Map;
import org.joml.Vector3f;

/**
 * One cuboid element of a block model, in the same [0,16] "from"/"to" coordinate space used by
 * vanilla block model JSON (e.g. assets/minecraft/models/block/fence_post.json has an element
 * with "from": [6,0,6], "to": [10,16,10]). {@code faces} mirrors that same element's "faces" map,
 * keyed by {@link Face}; a face missing from the map has no texture assigned (e.g. it's hidden by
 * "cullface" in the source model).
 *
 * <p>{@code rotation} is the element's own local rotation (vanilla's {@code "rotation"} block) as
 * Euler angles in radians, and {@code rescale} the per-axis size multiplier its {@code rescale}
 * flag implies (the stretch that fits a rotated element back to the block faces). Both default to
 * "none" (zero rotation, unit rescale) for elements with no rotation. These are consumed by {@code
 * SchemReader.computePieces}, which folds them into the render piece's rotation and size so the
 * renderer needs no per-element-rotation support of its own. See {@code BlockModelParser} for how
 * they are derived; only rotation about the X or Y axis is representable downstream (matching what
 * the vanilla {@code block/cross} grass/flower model needs).
 */
public record SubBlock(
    Vector3f from, Vector3f to, Map<Face, FaceTexture> faces, Vector3f rotation, Vector3f rescale) {

  public SubBlock(Vector3f from, Vector3f to, Map<Face, FaceTexture> faces) {
    this(from, to, faces, new Vector3f(0, 0, 0), new Vector3f(1, 1, 1));
  }

  public SubBlock(Vector3f from, Vector3f to) {
    this(from, to, Map.of());
  }

  public SubBlock(float fromX, float fromY, float fromZ, float toX, float toY, float toZ) {
    this(new Vector3f(fromX, fromY, fromZ), new Vector3f(toX, toY, toZ), Map.of());
  }

  /** Size of this cuboid in normalized block units (a full 16x16x16 element has size (1,1,1)). */
  public Vector3f size() {
    return new Vector3f(to).sub(from).mul(1 / 16f);
  }

  /** Offset of this cuboid's center from the block's center, in normalized block units. */
  public Vector3f offset() {
    return new Vector3f(from).add(to).mul(0.5f / 16f).sub(0.5f, 0.5f, 0.5f);
  }
}
