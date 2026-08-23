package com.lingpets.detect;

import com.lingpets.util.Images;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Pure function: BufferedImage in → circular head BufferedImage out.
 * No side effects; safe to call from any thread after OpenCV is loaded.
 */
public final class CatFaceDetector {

    private static final String[] CASCADE_RESOURCES = {
        "/cascades/haarcascade_frontalcatface_extended.xml",
        "/cascades/haarcascade_frontalcatface.xml"
    };

    private static final int DEFAULT_HEAD_SIZE = 120; // px

    private final CascadeClassifier classifier;

    public CatFaceDetector() {
        this.classifier = loadClassifier();
    }

    /**
     * Detects the cat face in {@code source}, crops it to a circle, and returns it
     * at {@code headSize} × {@code headSize} pixels. Falls back to a centred square
     * crop when no face is detected.
     */
    public BufferedImage detect(BufferedImage source, int headSize) {
        Mat mat = Images.bufferedImageToMat(source);
        Rect face = findBestFace(mat);

        if (face == null) {
            face = centredSquare(mat);
        }

        // Expand the detected rect slightly so ears aren't clipped.
        face = expand(face, 0.25, mat.cols(), mat.rows());

        return Images.cropCircle(mat, face.x, face.y, face.width, face.height, headSize);
    }

    public BufferedImage detect(BufferedImage source) {
        return detect(source, DEFAULT_HEAD_SIZE);
    }

    // -------------------------------------------------------------------------

    private Rect findBestFace(Mat mat) {
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.equalizeHist(gray, gray);

        MatOfRect faces = new MatOfRect();
        // scaleFactor=1.1, minNeighbors=3; loose enough for varied cat photos
        classifier.detectMultiScale(gray, faces, 1.1, 3);

        Rect[] rects = faces.toArray();
        if (rects.length == 0) return null;

        // Pick the largest detected face.
        Rect best = rects[0];
        for (Rect r : rects) {
            if (r.area() > best.area()) best = r;
        }
        return best;
    }

    /** Square centred on the image — used as a fallback when no face is found. */
    private static Rect centredSquare(Mat mat) {
        int side = (int) (Math.min(mat.cols(), mat.rows()) * 0.8);
        int x = (mat.cols() - side) / 2;
        int y = (int) ((mat.rows() - side) / 2.0 * 0.85); // bias slightly upward
        return new Rect(x, y, side, side);
    }

    /** Expands a rect by {@code ratio} on each side, clamped to image bounds. */
    private static Rect expand(Rect r, double ratio, int imgW, int imgH) {
        int dx = (int) (r.width * ratio);
        int dy = (int) (r.height * ratio);
        int x = Math.max(0, r.x - dx);
        int y = Math.max(0, r.y - dy);
        int w = Math.min(imgW - x, r.width + 2 * dx);
        int h = Math.min(imgH - y, r.height + 2 * dy);
        return new Rect(x, y, w, h);
    }

    /**
     * Tries each cascade resource in order; returns the first that loads.
     * Extracts to a temp file because OpenCV needs a filesystem path, not a stream.
     */
    private static CascadeClassifier loadClassifier() {
        for (String resource : CASCADE_RESOURCES) {
            try (InputStream in = CatFaceDetector.class.getResourceAsStream(resource)) {
                if (in == null) continue;
                Path tmp = Files.createTempFile("cascade_", ".xml");
                tmp.toFile().deleteOnExit();
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                CascadeClassifier cc = new CascadeClassifier(tmp.toString());
                if (!cc.empty()) return cc;
            } catch (IOException e) {
                // try next
            }
        }
        throw new IllegalStateException("No Haar cascade could be loaded from resources.");
    }
}
