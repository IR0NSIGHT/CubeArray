package com.example;

import java.io.IOException;
import java.util.ArrayList;

import static com.example.ResourceUtils.copyResourcesToFile;

public class PeriodicChecker {
    private ArrayList<Runnable> callbacks = new ArrayList<>();

    public void copyDefaultSchematics() {
        Thread t = new Thread(() -> {
            //prepare files on plate
            try {
                copyResourcesToFile(ResourceUtils.SCHEMATIC_RESOURCES);
                System.out.println("finished copying all schematics");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        t.start();
    }
    public void startPeriodicTask() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    //System.out.println("Checking files / saving state at " + java.time.LocalTime.now());

                    // --- your periodic logic here ---
                    for (Runnable callback : callbacks) {
                        try {
                            callback.run();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    // wait 5 seconds
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    // Exit if the thread is interrupted
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        t.setDaemon(true); // won't block JVM exit
        t.start();
    }

    public void addCallback(Runnable runnable) {
        this.callbacks.add(runnable);
    }
}
