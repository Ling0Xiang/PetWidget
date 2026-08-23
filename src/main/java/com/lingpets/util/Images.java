package com.lingpets.util;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

public final class Images {

    private Images() {}

    /** OpenCV Mat (BGR or BGRA) → BufferedImage (ARGB). */
    public static BufferedImage matToBufferedImage(Mat mat) {
        Mat src = mat;
        if (mat.channels() == 1) {
            src = new Mat();
            Imgproc.cvtColor(mat, src, Imgproc.COLOR_GRAY2BGR);
        }

        Mat bgra = new Mat();
        if (src.channels() == 3) {
            Imgproc.cvtColor(src, bgra, Imgproc.COLOR_BGR2BGRA);
        } else {
            bgra = src;
        }

        int w = bgra.cols(), h = bgra.rows();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
        byte[] data = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        bgra.get(0, 0, data);
        return img;
    }

    /** BufferedImage → OpenCV Mat (BGR). */
    public static Mat bufferedImageToMat(BufferedImage img) {
        BufferedImage bgr = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = bgr.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();

        Mat mat = new Mat(bgr.getHeight(), bgr.getWidth(), CvType.CV_8UC3);
        byte[] data = ((DataBufferByte) bgr.getRaster().getDataBuffer()).getData();
        mat.put(0, 0, data);
        return mat;
    }

    /**
     * Crops the region from {@code mat}, scales it to {@code size}×{@code size},
     * and returns a BufferedImage with a circular mask (corners transparent).
     */
    public static BufferedImage cropCircle(Mat mat, int x, int y, int w, int h, int size) {
        // clamp to image bounds
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        int x1 = Math.min(mat.cols(), x + w);
        int y1 = Math.min(mat.rows(), y + h);
        Mat roi = mat.submat(y0, y1, x0, x1);

        Mat scaled = new Mat();
        Imgproc.resize(roi, scaled, new Size(size, size), 0, 0, Imgproc.INTER_AREA);

        BufferedImage square = matToBufferedImage(scaled);
        return applyCircularMask(square);
    }

    /** Returns a copy of {@code img} with corners made transparent (circle inscribed in the image). */
    public static BufferedImage applyCircularMask(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setClip(new Ellipse2D.Float(0, 0, w, h));
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return out;
    }
}