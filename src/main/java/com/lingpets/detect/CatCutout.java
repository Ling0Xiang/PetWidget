package com.lingpets.detect;

import com.lingpets.util.Images;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Port of the Python GrabCut cutout pipeline (source of truth below).
 *
 * Steps (constants match the Python reference exactly):
 *   1. Downscale to max side 900 with INTER_AREA
 *   2. Seed rect = whole frame inset by 2%
 *   3. GrabCut, 7 iterations, GC_INIT_WITH_RECT
 *   4. Morph close then open with 7×7 ellipse
 *   5. Keep only the largest connected component
 *   6. 5×5 Gaussian blur → becomes alpha channel
 *   7. Tight-crop (fg > 10) → write BGRA PNG
 */
public final class CatCutout {

    private CatCutout() {}

    // Constants — DO NOT change without updating the Python reference too
    private static final int    MAX_SIDE      = 900;
    private static final double INSET_RATIO   = 0.02;
    private static final int    GRABCUT_ITERS = 7;
    private static final int    MORPH_SIZE    = 7;   // 7×7 ellipse kernel
    private static final int    BLUR_SIZE     = 5;   // 5×5 Gaussian
    private static final int    ALPHA_THRESH  = 10;  // crop boundary threshold

    // -------------------------------------------------------------------------
    // Public API

    /** BufferedImage in → transparent-background BufferedImage out. */
    public static BufferedImage cutout(BufferedImage src) {
        return Images.matToBufferedImage(pipeline(Images.bufferedImageToMat(src)));
    }

    /** File path in → transparent PNG written to {@code outPath}. */
    public static void cutout(String srcPath, String outPath) {
        Mat orig = Imgcodecs.imread(srcPath);
        if (orig.empty()) throw new IllegalArgumentException("Cannot read: " + srcPath);
        Imgcodecs.imwrite(outPath, pipeline(orig));
    }

    // -------------------------------------------------------------------------
    // Core pipeline (matches Python step-for-step)

    private static Mat pipeline(Mat orig) {
        int OH = orig.rows(), OW = orig.cols();

        // Step 1: downscale to max side 900 with INTER_AREA
        double scale = (double) MAX_SIDE / Math.max(OW, OH);
        Mat img;
        if (scale < 1.0) {
            img = new Mat();
            Imgproc.resize(orig, img,
                    new Size((int)(OW * scale), (int)(OH * scale)),
                    0, 0, Imgproc.INTER_AREA);
        } else {
            img = orig.clone();
        }
        int H = img.rows(), W = img.cols();

        // Step 2: seed rect = whole frame inset by 2%
        int m = (int)(Math.min(W, H) * INSET_RATIO);
        Rect rect = new Rect(m, m, W - 2 * m, H - 2 * m);

        // Step 3: GrabCut, 7 iterations, INIT_WITH_RECT
        Mat mask     = Mat.zeros(H, W, CvType.CV_8UC1);
        Mat bgdModel = new Mat();
        Mat fgdModel = new Mat();
        Imgproc.grabCut(img, mask, rect, bgdModel, fgdModel, GRABCUT_ITERS, Imgproc.GC_INIT_WITH_RECT);

        // Build binary fg mask: 255 where GC_FGD(1) or GC_PR_FGD(3), else 0
        byte[] maskBytes = new byte[H * W];
        mask.get(0, 0, maskBytes);
        byte[] fgBytes = new byte[H * W];
        for (int i = 0; i < maskBytes.length; i++) {
            int v = maskBytes[i] & 0xFF;
            fgBytes[i] = (v == 1 || v == 3) ? (byte) 255 : 0;
        }
        Mat fg = new Mat(H, W, CvType.CV_8UC1);
        fg.put(0, 0, fgBytes);

        // Step 4: morphological close then open, 7×7 ellipse kernel
        Mat kernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE, new Size(MORPH_SIZE, MORPH_SIZE));
        Imgproc.morphologyEx(fg, fg, Imgproc.MORPH_CLOSE, kernel);
        Imgproc.morphologyEx(fg, fg, Imgproc.MORPH_OPEN,  kernel);

        // Step 5: keep only the largest connected component
        Mat labels    = new Mat();
        Mat stats     = new Mat();
        Mat centroids = new Mat();
        int n = Imgproc.connectedComponentsWithStats(
                fg, labels, stats, centroids, 8, CvType.CV_32S);
        if (n > 1) {
            int biggestLabel = 1, biggestArea = 0;
            for (int i = 1; i < n; i++) {
                int area = (int) stats.get(i, Imgproc.CC_STAT_AREA)[0];
                if (area > biggestArea) { biggestArea = area; biggestLabel = i; }
            }
            int[] labelsData = new int[H * W];
            labels.get(0, 0, labelsData);
            for (int i = 0; i < labelsData.length; i++) {
                fgBytes[i] = (labelsData[i] == biggestLabel) ? (byte) 255 : 0;
            }
            fg.put(0, 0, fgBytes);
        }

        // Step 6: soften edge with 5×5 Gaussian blur → this becomes the alpha channel
        Imgproc.GaussianBlur(fg, fg, new Size(BLUR_SIZE, BLUR_SIZE), 0);

        // Step 7: tight-crop to subject (fg > ALPHA_THRESH), then assemble BGRA
        // Crop bounds use max index as exclusive end — matches Python img[y0:y1, x0:x1]
        // where y1=ys.max(), x1=xs.max().
        byte[] blurred = new byte[H * W];
        fg.get(0, 0, blurred);
        int minX = W, minY = H, maxX = 0, maxY = 0;
        for (int row = 0; row < H; row++) {
            for (int col = 0; col < W; col++) {
                if ((blurred[row * W + col] & 0xFF) > ALPHA_THRESH) {
                    if (col < minX) minX = col;
                    if (col > maxX) maxX = col;
                    if (row < minY) minY = row;
                    if (row > maxY) maxY = row;
                }
            }
        }

        Mat crop  = img.submat(minY, maxY, minX, maxX);
        Mat alpha = fg.submat(minY, maxY, minX, maxX);

        Mat bgra = new Mat();
        Imgproc.cvtColor(crop, bgra, Imgproc.COLOR_BGR2BGRA);
        List<Mat> channels = new ArrayList<>();
        Core.split(bgra, channels);
        channels.set(3, alpha);
        Core.merge(channels, bgra);
        return bgra;
    }

    // -------------------------------------------------------------------------
    // Debug helper

    /**
     * Composites {@code cutout} over a grey checkerboard so transparency is
     * visible in a plain image viewer.
     */
    public static BufferedImage renderOnCheckerboard(BufferedImage cutout, int squareSize) {
        int w = cutout.getWidth(), h = cutout.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        Color light = new Color(204, 204, 204);
        Color dark  = new Color(153, 153, 153);
        for (int y = 0; y < h; y += squareSize) {
            for (int x = 0; x < w; x += squareSize) {
                g.setColor(((x / squareSize + y / squareSize) % 2 == 0) ? light : dark);
                g.fillRect(x, y, Math.min(squareSize, w - x), Math.min(squareSize, h - y));
            }
        }
        g.drawImage(cutout, 0, 0, null);
        g.dispose();
        return out;
    }

    // -------------------------------------------------------------------------

    /** Takes {@code <input-image> <output.png>}. */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: CatCutout <input-image> <output.png>");
            System.exit(1);
        }
        nu.pattern.OpenCV.loadLocally();
        cutout(args[0], args[1]);
        System.out.println("Written: " + args[1]);
    }
}
