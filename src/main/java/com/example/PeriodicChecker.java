package com.example;

import java.util.ArrayList;

public class PeriodicChecker {
    private ArrayList<Runnable> callbacks = new ArrayList<>();
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
