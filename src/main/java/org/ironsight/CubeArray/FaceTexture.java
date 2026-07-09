package org.ironsight.CubeArray;

import org.joml.Vector4f;

/**
 * The texture assigned to one face of a {@link SubBlock}, mirroring one entry of a block model
 * JSON element's "faces" map, e.g. from assets/minecraft/models/block/acacia_fence_post.json:
 *
 * <pre>
 * "textures": { "side": "block/acacia_fence_side", "top": "block/acacia_fence_top" },
 * "faces": {
 *   "north": {"uv": [6, 0, 10, 16], "texture": "#side"},
 *   "up":    {"uv": [6, 6, 10, 10], "texture": "#top", "cullface": "up"}
 * }
 * </pre>
 *
 * <p>{@code texture} is the sprite name already resolved through the model's "textures" table
 * (e.g. "block/acacia_fence_side"), not the raw "#side" reference used in the JSON.
 */
public record FaceTexture(String texture, Vector4f uv, Face cullface) {

  public FaceTexture(String texture, float u1, float v1, float u2, float v2) {
    this(texture, new Vector4f(u1, v1, u2, v2), null);
  }
}
