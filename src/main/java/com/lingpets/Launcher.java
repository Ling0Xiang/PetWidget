package com.lingpets;

import javafx.application.Application;
import nu.pattern.OpenCV;

/**
 * Jar entry point. Does NOT extend Application so the shaded jar can start
 * cleanly (JavaFX needs Application.launch to be called from a non-Application class
 * when the module path is not configured, which is the case with a fat jar).
 */
public class Launcher {
    public static void main(String[] args) {
        OpenCV.loadLocally(); // extract + load native lib before any OpenCV class is touched
        Application.launch(Main.class, args);
    }
}
