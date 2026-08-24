package com.lingpets.detect;

import nu.pattern.OpenCV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class CatFaceDetectorTest {

    private static CatFaceDetector detector;
    private static BufferedImage patsy; // real cat photo used for detection tests

    @BeforeAll
    static void setup() throws Exception {
        OpenCV.loadLocally();
        detector = new CatFaceDetector();
        try (InputStream in = CatFaceDetectorTest.class.getResourceAsStream("/Patsy.jpeg")) {
            assertNotNull(in, "Patsy.jpeg must be present in src/test/resources");
            patsy = ImageIO.read(in);
        }
    }

    // -------------------------------------------------------------------------
    // Output dimensions — use Patsy as source

    @Test
    void defaultSizeIs120() {
        BufferedImage head = detector.detect(patsy);
        assertEquals(120, head.getWidth());
        assertEquals(120, head.getHeight());
    }

    @Test
    void customSizeIsRespected() {
        BufferedImage head = detector.detect(patsy, 64);
        assertEquals(64, head.getWidth());
        assertEquals(64, head.getHeight());
    }

    @Test
    void outputIsAlwaysSquare() {
        BufferedImage head = detector.detect(patsy, 80);
        assertEquals(head.getWidth(), head.getHeight());
    }

    @Test
    void nonSquareInputProducesSquareOutput() {
        // Crop Patsy to a wide landscape strip to force a non-square input
        BufferedImage landscape = patsy.getSubimage(0, 0, patsy.getWidth(), patsy.getHeight() / 2);
        BufferedImage head = detector.detect(landscape, 120);
        assertEquals(120, head.getWidth());
        assertEquals(120, head.getHeight());
    }

    // -------------------------------------------------------------------------
    // Alpha channel (circular mask) — use Patsy as source

    @Test
    void outputHasAlphaChannel() {
        BufferedImage head = detector.detect(patsy);
        assertTrue(head.getColorModel().hasAlpha(),
                "output must carry an alpha channel for the circular mask to work");
    }

    @Test
    void cornersAreTransparent() {
        BufferedImage head = detector.detect(patsy);
        assertTransparent(head, 0, 0,                           "top-left corner");
        assertTransparent(head, head.getWidth() - 1, 0,         "top-right corner");
        assertTransparent(head, 0, head.getHeight() - 1,        "bottom-left corner");
        assertTransparent(head, head.getWidth() - 1, head.getHeight() - 1, "bottom-right corner");
    }

    @Test
    void centerPixelIsOpaque() {
        BufferedImage head = detector.detect(patsy);
        int cx = head.getWidth()  / 2;
        int cy = head.getHeight() / 2;
        assertOpaque(head, cx, cy, "centre pixel");
    }

    // Fallback path: solid colour image has no detectable cat face,
    // so the centred-crop code path runs instead.

    @Test
    void fallbackCentredCropProducesCorrectSize() {
        BufferedImage head = detector.detect(solidImage(300, 300, Color.GRAY));
        assertEquals(120, head.getWidth());
        assertEquals(120, head.getHeight());
    }

    @Test
    void fallbackCentredCropPreservesColour() {
        BufferedImage head = detector.detect(solidImage(300, 300, Color.RED));
        int cx = head.getWidth()  / 2;
        int cy = head.getHeight() / 2;
        Color c = new Color(head.getRGB(cx, cy), true);
        assertTrue(c.getRed()   > 200, "red channel should be high");
        assertTrue(c.getGreen() < 50,  "green channel should be low");
        assertTrue(c.getBlue()  < 50,  "blue channel should be low");
    }

    // -------------------------------------------------------------------------
    // Helpers

    private static BufferedImage solidImage(int w, int h, Color colour) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(colour);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static void assertTransparent(BufferedImage img, int x, int y, String label) {
        int alpha = (img.getRGB(x, y) >> 24) & 0xFF;
        assertEquals(0, alpha, label + " should be fully transparent (alpha=0)");
    }

    private static void assertOpaque(BufferedImage img, int x, int y, String label) {
        int alpha = (img.getRGB(x, y) >> 24) & 0xFF;
        assertEquals(255, alpha, label + " should be fully opaque (alpha=255)");
    }
}
