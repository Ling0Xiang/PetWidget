package com.lingpets.detect;

import nu.pattern.OpenCV;
import org.junit.jupiter.api.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the Java GrabCut cutout and the Python reference on the same images,
 * saving checkerboard debug composites so results can be compared visually.
 *
 * Output directory: target/cutout-verify/
 *   <name>-java-cutout.png        — Java transparent PNG
 *   <name>-java-checkerboard.png  — Java result on grey checkerboard
 *   <name>-py-cutout.png          — Python transparent PNG  (if Python available)
 *   <name>-py-checkerboard.png    — Python result on grey checkerboard
 */
class CatCutoutTest {

    private static final Path OUT = Path.of("target/cutout-verify");

    @BeforeAll
    static void init() throws Exception {
        OpenCV.loadLocally();
        Files.createDirectories(OUT);
    }

    // -------------------------------------------------------------------------
    // Image 1: Patsy — cat fills most of the frame

    @Test
    void patsy_cutout() throws Exception {
        runBoth("patsy", loadTestResource("/Patsy.jpeg"));
    }

    // Image 2: Patsy with heavy white padding — cat does NOT fill the frame

    @Test
    void patsyPadded_cutout() throws Exception {
        BufferedImage base = loadTestResource("/Patsy.jpeg");
        BufferedImage padded = addWhitePadding(base, base.getWidth() / 2);
        runBoth("patsy-padded", padded);
    }

    // -------------------------------------------------------------------------
    // Shape / alpha assertions (Java output)

    @Test
    void javaOutput_hasAlphaChannel() throws Exception {
        BufferedImage result = CatCutout.cutout(loadTestResource("/Patsy.jpeg"));
        assertTrue(result.getColorModel().hasAlpha());
    }

    @Test
    void javaOutput_somePixelsAreTransparent() throws Exception {
        BufferedImage result = CatCutout.cutout(loadTestResource("/Patsy.jpeg"));
        long transparent = countPixelsByAlpha(result, 0);
        assertTrue(transparent > 0, "background should be transparent");
    }

    @Test
    void javaOutput_mostPixelsAreOpaque() throws Exception {
        BufferedImage result = CatCutout.cutout(loadTestResource("/Patsy.jpeg"));
        long total  = (long) result.getWidth() * result.getHeight();
        long opaque = countPixelsByAlpha(result, 255);
        assertTrue((double) opaque / total > 0.4,
                "majority of crop should be cat, got " + opaque + "/" + total);
    }

    // -------------------------------------------------------------------------

    /** Runs Java cutout + Python reference (if available) and saves all debug images. */
    private void runBoth(String name, BufferedImage src) throws Exception {
        // --- Java ---
        long t0 = System.nanoTime();
        BufferedImage javaCutout = CatCutout.cutout(src);
        long javaMs = (System.nanoTime() - t0) / 1_000_000;

        ImageIO.write(javaCutout, "PNG", OUT.resolve(name + "-java-cutout.png").toFile());
        BufferedImage javaBoard = CatCutout.renderOnCheckerboard(javaCutout, 20);
        ImageIO.write(javaBoard, "PNG", OUT.resolve(name + "-java-checkerboard.png").toFile());

        System.out.printf("[Java]   %-18s %4d ms  %dx%d px%n",
                name + ":", javaMs, javaCutout.getWidth(), javaCutout.getHeight());

        // --- Python (skip gracefully if not available) ---
        Path tmpSrc = OUT.resolve(name + "-src.png");
        ImageIO.write(src, "PNG", tmpSrc.toFile());

        Path pyScript = Path.of("target/cutout_reference.py");
        Path pyOut    = OUT.resolve("py-" + name);

        if (Files.exists(pyScript)) {
            String python = findPython();
            if (python != null) {
                Process proc = new ProcessBuilder(python, pyScript.toString(),
                        tmpSrc.toString(), pyOut.toString())
                        .redirectErrorStream(true)
                        .start();
                String output = new String(proc.getInputStream().readAllBytes());
                int exit = proc.waitFor();
                if (exit == 0) {
                    System.out.print(output);
                } else {
                    System.out.println("[Python] failed: " + output);
                }
            } else {
                System.out.println("[Python] skipped — python3 not on PATH");
            }
        } else {
            System.out.println("[Python] skipped — cutout_reference.py not found");
        }

        Files.deleteIfExists(tmpSrc);
    }

    // -------------------------------------------------------------------------
    // Helpers

    private static BufferedImage loadTestResource(String path) throws Exception {
        try (InputStream in = CatCutoutTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " must be present in src/test/resources");
            return ImageIO.read(in);
        }
    }

    /** Adds a solid white border of {@code pad} pixels on all four sides. */
    private static BufferedImage addWhitePadding(BufferedImage src, int pad) {
        int w = src.getWidth() + 2 * pad, h = src.getHeight() + 2 * pad;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.drawImage(src, pad, pad, null);
        g.dispose();
        return out;
    }

    private static long countPixelsByAlpha(BufferedImage img, int alpha) {
        long count = 0;
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++)
                if (((img.getRGB(x, y) >> 24) & 0xFF) == alpha) count++;
        return count;
    }

    private static String findPython() {
        for (String candidate : List.of("python3", "python")) {
            try {
                int exit = new ProcessBuilder(candidate, "--version")
                        .redirectErrorStream(true).start().waitFor();
                if (exit == 0) return candidate;
            } catch (Exception ignored) {}
        }
        return null;
    }
}
