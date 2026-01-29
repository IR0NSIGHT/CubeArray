package com.example;

import com.example.swing.AppContext;
import com.example.swing.FileRenderApp;

import static com.example.ResourceUtils.copyResourcesToFile;


public class CubeArrayMain {
    public static final String APP_NAME = "CubeArray";
    public static final PeriodicChecker periodicChecker = new PeriodicChecker();
    public static void main(String[] args) throws Exception {
        System.out.println("Hello world!");

        //copy files that are required to run the application
        copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);

        //background copying for less improtant stuff
        periodicChecker.copyDefaultSchematics();

        // background task for periodic checks
        periodicChecker.startPeriodicTask();

        //start swing GUI
        FileRenderApp.startApp(AppContext.read());

        //start rendering app
    //    final var schematicsForlder =  getInstallPath().resolve(ResourceUtils.SCHEMATICS_ROOT);
    //    var setup = SchemReader.prepareData(SchemReader.loadDefaultObjects(schematicsForlder));
    //    new InstancedCubes(setup).run();
    }



}