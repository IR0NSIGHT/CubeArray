package org.ironsight.CubeArray;

import java.util.List;

/**
 * A full block model made up of an arbitrary number of {@link SubBlock} cuboids, mirroring how
 * vanilla block model JSON lists multiple "elements" per model (e.g. fence_side.json has a "top
 * bar" and a "lower bar" element).
 */
public record BlockModel(List<SubBlock> subBlocks) {

  public BlockModel(SubBlock... subBlocks) {
    this(List.of(subBlocks));
  }
}
