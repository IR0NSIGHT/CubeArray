package org.ironsight.cubearray.mcmodel;

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
 * Euler angles in radians, {@code rescale} the per-axis size multiplier its {@code rescale} flag
 * implies (the stretch that fits a rotated element back to the block faces), and {@code origin} the
 * point that rotation pivots about, in the same [0,16] space as from/to (default block centre
 * (8,8,8)). All default to "none" (zero rotation, unit rescale, centre origin) for elements with no
 * rotation. These are consumed by {@code SchemReader.computePieces}, which folds them into the
 * render piece's rotation and size so the renderer needs no per-element-rotation support of its
 * own. See {@code BlockModelParser} for how they are derived.
 */
public record SubBlock(
    Vector3f from,
    Vector3f to,
    Map<Face, FaceTexture> faces,
    Vector3f rotation,
    Vector3f rescale,
    Vector3f origin) {

  public SubBlock(Vector3f from, Vector3f to, Map<Face, FaceTexture> faces) {
    this(from, to, faces, new Vector3f(0, 0, 0), new Vector3f(1, 1, 1), new Vector3f(8, 8, 8));
  }

  public SubBlock(
      Vector3f from, Vector3f to, Map<Face, FaceTexture> faces, Vector3f rotation, Vector3f rescale) {
    this(from, to, faces, rotation, rescale, new Vector3f(8, 8, 8));
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

  /** The element rotation's pivot, as an offset from the block's center, in normalized units. */
  public Vector3f originOffset() {
    return new Vector3f(origin).mul(1 / 16f).sub(0.5f, 0.5f, 0.5f);
  }
}
