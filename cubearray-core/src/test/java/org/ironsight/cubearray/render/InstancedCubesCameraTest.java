package org.ironsight.cubearray.render;

import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;
import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import org.ironsight.cubearray.mcmodel.Face;
import org.ironsight.cubearray.render.InstancedCubes.CameraState;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.Test;

public class InstancedCubesCameraTest {

  private static CubeSetup minimalSetup() {
    int numTypes = 1;
    Vector4f[] uvCoords = new Vector4f[Face.values().length * numTypes];
    for (int i = 0; i < uvCoords.length; i++) uvCoords[i] = new Vector4f(0, 0, 1, 1);

    return new CubeSetup(
        new Vector3f[] {new Vector3f(0, 0, 0)},
        new int[] {0},
        new Vector3f[] {new Vector3f(1, 1, 1)},
        new Vector3f[] {new Vector3f(1, 1, 1)},
        new Vector3f[] {new Vector3f(0, 0, 0)},
        new Vector3f[] {new Vector3f(0, 0, 0)},
        uvCoords,
        new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
        new Vector3f(-1, -1, -1),
        new Vector3f(1, 1, 1),
        "test",
        false);
  }

  @Test
  public void getCameraStateReturnsInitialState() {
    InstancedCubes renderer = new InstancedCubes(minimalSetup());
    CameraState state = renderer.getCameraState();

    assertNotNull(state);
    // center = (min+max)/2 = (0,0,0)
    assertEquals(0f, state.target().x, 1e-6f);
    assertEquals(0f, state.target().y, 1e-6f);
    assertEquals(0f, state.target().z, 1e-6f);
    // dim = max-min = (2,2,2), radius = max(2,2,2)*2 = 4
    assertEquals(4f, state.radius(), 1e-6f);
    assertEquals(toRadians(210), state.yaw(), 1e-6f);
    assertEquals(toRadians(30), state.pitch(), 1e-6f);
    assertEquals(0f, state.roll(), 1e-6f);
  }

  @Test
  public void setCameraQueuesTaskAndUpdatesState() {
    InstancedCubes renderer = new InstancedCubes(minimalSetup());

    renderer.setCamera(90, 45, 100f);
    renderer.executePendingTasks();

    CameraState state = renderer.getCameraState();
    assertEquals(0f, state.target().x, 1e-6f);
    assertEquals(0f, state.target().y, 1e-6f);
    assertEquals(0f, state.target().z, 1e-6f);
    assertEquals(toRadians(90), state.yaw(), 1e-6f);
    assertEquals(toRadians(45), state.pitch(), 1e-6f);
    assertEquals(100f, state.radius(), 1e-6f);
    assertEquals(0f, state.roll(), 1e-6f);
  }

  @Test
  public void setCameraOnlyAffectsSpecifiedFields() {
    InstancedCubes renderer = new InstancedCubes(minimalSetup());
    CameraState before = renderer.getCameraState();

    // Only change yaw — pitch, radius, target should stay
    renderer.setCamera(45, (float) toDegrees(before.pitch()), before.radius());
    renderer.executePendingTasks();

    CameraState after = renderer.getCameraState();
    assertEquals(before.target().x, after.target().x, 1e-6f);
    assertEquals(before.target().y, after.target().y, 1e-6f);
    assertEquals(before.target().z, after.target().z, 1e-6f);
    assertEquals(toRadians(45), after.yaw(), 1e-6f);
    assertEquals(before.pitch(), after.pitch(), 1e-6f);
    assertEquals(before.radius(), after.radius(), 1e-6f);
    assertEquals(0f, after.roll(), 1e-6f);
  }
}
