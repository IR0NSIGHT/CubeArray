package org.ironsight.cubearray.edit;

import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class BlockGradientTest {

  @Test
  public void gradientInterpolation() {
    List<BlockGradient.GradientRow> rows =
        List.of(
            new BlockGradient.GradientRow("minecraft:grass_block", 0, 100),
            new BlockGradient.GradientRow("minecraft:gravel", 50, 50),
            new BlockGradient.GradientRow("minecraft:stone", 100, 100));
    BlockGradient gradient = new BlockGradient(rows);

    assertDistribution(gradient, 0, "minecraft:grass_block", 1.0, 0.05, "", 0, 0);
    assertDistribution(
        gradient, 10, "minecraft:grass_block", 0.80, 0.10, "minecraft:gravel", 0.20, 0.10);
    assertDistribution(
        gradient, 25, "minecraft:grass_block", 0.67, 0.10, "minecraft:gravel", 0.33, 0.10);
    assertDistribution(gradient, 50, "minecraft:gravel", 1.0, 0.05, "", 0, 0);
    assertDistribution(gradient, 75, "minecraft:gravel", 0.33, 0.10, "minecraft:stone", 0.67, 0.10);
    assertDistribution(gradient, 100, "minecraft:stone", 1.0, 0.05, "", 0, 0);
  }

  @Test
  public void gradientClampingBelow() {
    List<BlockGradient.GradientRow> rows =
        List.of(new BlockGradient.GradientRow("minecraft:grass_block", 10, 100));
    BlockGradient gradient = new BlockGradient(rows);
    assertDistribution(gradient, 0, "minecraft:grass_block", 1.0, 0.05, "", 0, 0);
  }

  @Test
  public void gradientClampingAbove() {
    List<BlockGradient.GradientRow> rows =
        List.of(new BlockGradient.GradientRow("minecraft:stone", 10, 100));
    BlockGradient gradient = new BlockGradient(rows);
    assertDistribution(gradient, 100, "minecraft:stone", 1.0, 0.05, "", 0, 0);
  }

  @Test
  public void gradientEmpty() {
    BlockGradient gradient = new BlockGradient(List.of());
    assertDistribution(gradient, 0, "minecraft:air", 1.0, 0.05, "", 0, 0);
  }

  private void assertDistribution(
      BlockGradient gradient,
      int y,
      String blockA,
      double expectedA,
      double toleranceA,
      String blockB,
      double expectedB,
      double toleranceB) {
    int countA = 0, countB = 0;
    int samples = 1000;
    for (int i = 0; i < samples; i++) {
      String result = gradient.getBlock(y);
      if (result.equals(blockA)) countA++;
      else if (result.equals(blockB)) countB++;
    }
    double actualA = (double) countA / samples;
    double actualB = (double) countB / samples;
    assertTrue(
        String.format("blockA %.2f not within %.2f +/- %.2f", actualA, expectedA, toleranceA),
        Math.abs(actualA - expectedA) <= toleranceA);
    if (expectedB > 0) {
      assertTrue(
          String.format("blockB %.2f not within %.2f +/- %.2f", actualB, expectedB, toleranceB),
          Math.abs(actualB - expectedB) <= toleranceB);
    }
  }

  private void assertDistribution(
      BlockGradient gradient, int y, String blockA, double expectedA, double toleranceA) {
    assertDistribution(gradient, y, blockA, expectedA, toleranceA, "", 0, 0);
  }
}
