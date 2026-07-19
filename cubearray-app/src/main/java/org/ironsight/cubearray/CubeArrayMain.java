package org.ironsight.cubearray;

import java.nio.file.Path;
import java.util.List;
import org.ironsight.cubearray.platform.AppLogger;
import org.ironsight.cubearray.platform.PeriodicChecker;
import org.ironsight.cubearray.platform.ResourceUtils;
import org.ironsight.cubearray.render.CubeSetup;
import org.ironsight.cubearray.render.InstancedCubes;
import org.ironsight.cubearray.schematic.SchemReader;
import org.ironsight.cubearray.ui.AppContext;
import org.ironsight.cubearray.ui.FileRenderApp;

public class CubeArrayMain {

  public static void main(String[] args) throws Exception {
    if (args.length >= 2 && args[0].equals("--render")) {
      Path schematicPath = Path.of(args[1]);
      Path outputPath = args.length >= 3 ? Path.of(args[2]) : Path.of("output.png");
      int imgWidth = args.length >= 4 ? Integer.parseInt(args[3]) : 1920;
      int imgHeight = args.length >= 5 ? Integer.parseInt(args[4]) : 1080;

      ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);

      CubeSetup setup =
          SchemReader.prepareData(
              SchemReader.loadSchematics(
                  List.of(schematicPath), f -> System.err.println("can not load " + f)));
      if (setup == null) {
        System.err.println("Failed to load schematic: " + schematicPath);
        System.exit(1);
      }
      InstancedCubes.renderToFile(setup, outputPath, imgWidth, imgHeight);
      return;
    }

    AppLogger.init();
    ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);
    PeriodicChecker.INSTANCE.copyDefaultSchematics();
    PeriodicChecker.INSTANCE.startPeriodicTask();
    FileRenderApp.startApp(AppContext.read());
  }
}
