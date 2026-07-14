package org.ironsight.cubearray.debug;

import java.nio.file.Path;
import java.util.List;
import org.ironsight.cubearray.platform.ResourceUtils;
import org.ironsight.cubearray.render.CubeSetup;
import org.ironsight.cubearray.render.InstancedCubes;
import org.joml.Vector3f;
import org.ironsight.cubearray.schematic.SchemReader;

public class DebugReplaceMain {

  public static void main(String[] args) throws Exception {
    ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);
    ResourceUtils.copyResourcesToFile(ResourceUtils.SCHEMATIC_RESOURCES);

    Path schemDir = ResourceUtils.getInstallPath().resolve("schematics");
    Path pathA = schemDir.resolve("Dannypan/house_4.schem");
    Path pathB = schemDir.resolve("Paleozoey/Abies_alba4.schem");

    CubeSetup setupA =
        SchemReader.prepareData(
            SchemReader.loadSchematics(List.of(pathA), f -> {}));
    CubeSetup setupB =
        SchemReader.prepareData(
            SchemReader.loadSchematics(List.of(pathB), f -> {}));

    InstancedCubes renderer = new InstancedCubes(setupA);
    new Thread(
            () -> {
              try {
                renderer.run();
              } catch (Exception e) {
                e.printStackTrace();
              }
            })
        .start();

    Thread.sleep(500);

    var baseTarget = renderer.getCameraState().target();
    boolean onFirst = false;
    boolean up = true;
    int tick = 0;
    while (true) {
      Thread.sleep(2000);
      renderer.replaceData(onFirst ? setupA : setupB);
      onFirst = !onFirst;
      tick++;
      if (tick % 2 == 0) {
        float y = up ? baseTarget.y + 10 : baseTarget.y - 10;
        renderer.setCameraTarget(new Vector3f(baseTarget.x, y, baseTarget.z));
        up = !up;
      }
    }
  }
}
