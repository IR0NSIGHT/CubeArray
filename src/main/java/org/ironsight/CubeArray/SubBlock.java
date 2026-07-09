package org.ironsight.CubeArray;

import java.util.Map;
import org.joml.Vector3f;

/**
 * One cuboid element of a block model, in the same [0,16] "from"/"to" coordinate space used by
 * vanilla block model JSON (e.g. assets/minecraft/models/block/fence_post.json has an element
 * with "from": [6,0,6], "to": [10,16,10]). {@code faces} mirrors that same element's "faces" map,
 * keyed by {@link Face}; a face missing from the map has no texture assigned (e.g. it's hidden by
 * "cullface" in the source model).
 */
public record SubBlock(Vector3f from, Vector3f to, Map<Face, FaceTexture> faces) {

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
