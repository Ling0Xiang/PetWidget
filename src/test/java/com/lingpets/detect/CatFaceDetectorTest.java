package com.lingpets.detect;

import nu.pattern.OpenCV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class CatFaceDetectorTest {

    private static CatFaceDetector detector;
    private static BufferedImage patsy;

    @BeforeAll
    static void setup() throws Exception {
        OpenCV.loadLocally();
        detector = new CatFaceDetector();
        try (InputStream in = CatFaceDetectorTest.class.getResourceAsStream("/Patsy.jpeg")) {
            assertNotNull(in, "Patsy.jpeg must be present in src/test/resources");
            patsy = ImageIO.read(in);
        }
    }

    @Test
    void defaultSizeIs120() {
        BufferedImage head = detector.detect(patsy);
        assertEquals(120, head.getWidth());
        assertEquals(120, head.getHeight());
    }

    @Test
    void customSizeIsRespected() {
        BufferedImage head = detector.detect(patsy, 240);
        assertEquals(240, head.getWidth());
        assertEquals(240, head.getHeight());
    }

    @Test
    void outputIsSquare() {
        BufferedImage head = detector.detect(patsy);
        assertEquals(head.getWidth(), head.getHeight());
    }

    @Test
    void outputHasAlphaChannel() {
        assertTrue(detector.detect(patsy).getColorModel().hasAlpha());
    }
}
