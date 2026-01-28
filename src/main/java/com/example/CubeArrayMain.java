package com.example;

import java.io.IOException;

import static com.example.ResourceUtils.copyResourcesToFile;
import static com.example.ResourceUtils.getInstallPath;

public class CubeArrayMain {
    public static String APP_NAME = "CubeArray";
    public static void main(String[] args) throws Exception {
        System.out.println("Hello world!");
        //prepare files on plate
        copyResourcesToFile(ResourceUtils.SCHEMATICS_ROOT,ResourceUtils.TEXTURE_PACK_ROOT);

        //start rendering app
        final var schematicsForlder =  getInstallPath().resolve(ResourceUtils.SCHEMATICS_ROOT);
        var setup = SchemReader.prepareData(SchemReader.loadDefaultObjects(schematicsForlder));
        new InstancedCubes(setup).run();
    }
}