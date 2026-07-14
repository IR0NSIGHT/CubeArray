package org.ironsight.cubearray.mcmodel;

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
 *
 * @param tintindex the block model's tint index (-1 for no tint, 0=grass, 1=foliage, 2=dry foliage)
 */
public record FaceTexture(String texture, Vector4f uv, Face cullface, int tintindex) {

  public FaceTexture(String texture, Vector4f uv, Face cullface) {
    this(texture, uv, cullface, -1);
  }
}
