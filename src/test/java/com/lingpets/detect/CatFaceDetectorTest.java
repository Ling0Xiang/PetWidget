package com.lingpets.detect;

import nu.pattern.OpenCV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests for CatFaceDetector.
 *
 * All cases use programmatically generated images so the test suite has no
 * external file dependencies. A solid-colour image triggers the centred-crop
 * fallback (no Haar match), which is the code path that must always succeed.
 */
class CatFaceDetectorTest {

    private static CatFaceDetector detector;

    @BeforeAll
    static void setup() {
        OpenCV.loadLocally();
        detector = new CatFaceDetector();
    }

    // -------------------------------------------------------------------------
    // Output dimensions

    @Test
    void defaultSizeIs120() {
        BufferedImage head = detector.detect(solidImage(300, 300, Color.GRAY));
        assertEquals(120, head.getWidth());
        assertEquals(120, head.getHeight());
    }

    @Test
    void customSizeIsRespected() {
        BufferedImage head = detector.detect(solidImage(300, 300, Color.GRAY), 64);
        assertEquals(64, head.getWidth());
        assertEquals(64, head.getHeight());
    }

    @Test
    void squareInputProducesSquareOutput() {
        BufferedImage head = detector.detect(solidImage(200, 200, Color.BLUE), 80);
        assertEquals(head.getWidth(), head.getHeight());
    }

    @Test
    void nonSquareInputProducesSquareOutput() {
        // wide landscape image
        BufferedImage head = detector.detect(solidImage(640, 320, Color.GREEN), 120);
        assertEquals(120, head.getWidth());
        assertEquals(120, head.getHeight());
    }

    // -------------------------------------------------------------------------
    // Alpha channel (circular mask)

    @Test
    void outputHasAlphaChannel() {
        BufferedImage head = detector.detect(solidImage(200, 200, Color.RED));
        assertTrue(head.getColorModel().hasAlpha(),
                "output must carry an alpha channel for the circular mask to work");
    }

    @Test
    void cornersAreTransparent() {
        BufferedImage head = detector.detect(solidImage(300, 300, Color.RED));
        // The four corner pixels lie outside the inscribed circle and must be fully transparent.
        assertTransparent(head, 0, 0,                           "top-left corner");
        assertTransparent(head, head.getWidth() - 1, 0,         "top-right corner");
        assertTransparent(head, 0, head.getHeight() - 1,        "bottom-left corner");
        assertTransparent(head, head.getWidth() - 1, head.getHeight() - 1, "bottom-right corner");
    }

    @Test
    void centerPixelIsOpaque() {
        // A fully red source image should yield a red, fully opaque centre after cropping.
        BufferedImage head = detector.detect(solidImage(300, 300, Color.RED));
        int cx = head.getWidth()  / 2;
        int cy = head.getHeight() / 2;
        assertOpaque(head, cx, cy, "centre pixel");
    }

    @Test
    void centerPixelPreservesSourceColour() {
        // Verify the crop doesn't swap channels — red in, red out.
        BufferedImage head = detector.detect(solidImage(300, 300, Color.RED));
        int cx = head.getWidth()  / 2;
        int cy = head.getHeight() / 2;
        Color c = new Color(head.getRGB(cx, cy), true);
        assertTrue(c.getRed() > 200,   "red channel should be high");
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
