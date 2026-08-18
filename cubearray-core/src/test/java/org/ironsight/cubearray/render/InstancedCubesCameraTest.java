package org.ironsight.cubearray.render;

import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;
import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import org.ironsight.cubearray.mcmodel.Face;
import org.ironsight.cubearray.render.InstancedCubes.CameraState;
import org.joml.Matrix4f;
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
        new int[] {1},
        new Vector3f[] {new Vector3f(1, 1, 1)},
        new Vector3f[] {new Vector3f(1, 1, 1)},
        new Vector3f[] {new Vector3f(0, 0, 0)},
        new Vector3f[] {new Vector3f(0, 0, 0)},
        uvCoords,
        new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
        new Vector3f(-1, -1, -1),
        new Vector3f(1, 1, 1),
        "test",
        false,
        new SchematicTransform[] {
          new SchematicTransform("global axis", new Matrix4f()),
          new SchematicTransform(
              "test schematic", new Matrix4f().translation(4, 0, 6), new Vector3f(2, 0, 3))
        });
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

  @Test
  public void schematicTransformsAreNamedAndVersioned() {
    CubeSetup setup = minimalSetup();

    assertEquals("global axis", setup.schematicTransform(0).name());
    assertEquals("test schematic", setup.schematicTransform(1).name());
    assertEquals(0L, setup.transformVersion());

    Matrix4f translation = new Matrix4f().translation(4, 5, 6);
    setup.setSchematicTransform(0, translation);
    translation.identity();

    assertEquals(1L, setup.transformVersion());
    Matrix4f stored = setup.schematicTransform(0).matrix();
    assertEquals(4f, stored.m30(), 1e-6f);
    assertEquals(5f, stored.m31(), 1e-6f);
    assertEquals(6f, stored.m32(), 1e-6f);
  }

  @Test
  public void rotatingSchematicTransformsPreservesBasePivot() {
    CubeSetup setup = minimalSetup();

    setup.rotateSchematicTransforms((float) Math.PI / 2);

    Matrix4f schematic = setup.schematicTransform(1).matrix();
    Vector3f pivot = setup.schematicTransform(1).pivot();
    schematic.transformPosition(pivot);
    assertEquals(6f, pivot.x, 1e-6f);
    assertEquals(9f, pivot.z, 1e-6f);
    assertEquals("global axis", setup.schematicTransform(0).name());
    assertEquals(0f, setup.schematicTransform(0).matrix().m00(), 1e-6f);
    assertEquals(1L, setup.transformVersion());
  }

  @Test
  public void screenAxisRotationUsesWorldAxesAndPreservesPivot() {
    CubeSetup setup = minimalSetup();

    setup.rotateSchematicTransformsAroundLocalUp((float) Math.PI / 2, 0f, new Vector3f(1, 0, 0));

    Matrix4f schematic = setup.schematicTransform(1).matrix();
    Vector3f pivot = setup.schematicTransform(1).pivot();
    schematic.transformPosition(pivot);
    assertEquals(6f, pivot.x, 1e-6f);
    assertEquals(9f, pivot.z, 1e-6f);

    Vector3f point = new Vector3f(3, 0, 3);
    schematic.transformPosition(point);
    assertEquals(6f, point.x, 1e-6f);
    assertEquals(8f, point.z, 1e-6f);
  }

  @Test
  public void horizontalRotationUsesCurrentLocalUpAxis() {
    CubeSetup setup = minimalSetup();
    Matrix4f initial = new Matrix4f().translation(4, 0, 6).rotateX((float) Math.PI / 2);
    setup.setSchematicTransform(1, initial);
    Vector3f localUpBefore = initial.transformDirection(new Vector3f(0, 1, 0)).normalize();

    setup.rotateSchematicTransformsAroundLocalUp((float) Math.PI / 2, 0f, new Vector3f(1, 0, 0));

    Vector3f localUpAfter =
        setup.schematicTransform(1).matrix().transformDirection(new Vector3f(0, 1, 0)).normalize();
    assertEquals(localUpBefore.x, localUpAfter.x, 1e-6f);
    assertEquals(localUpBefore.y, localUpAfter.y, 1e-6f);
    assertEquals(localUpBefore.z, localUpAfter.z, 1e-6f);
  }
}
