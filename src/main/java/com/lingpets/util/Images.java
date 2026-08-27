package com.lingpets.util;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.List;

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
        // OpenCV BGRA bytes are [B,G,R,A]; Java TYPE_4BYTE_ABGR expects [A,B,G,R].
        // Read into a temp buffer, then reorder into the image's backing array.
        byte[] bgraBytes = new byte[w * h * 4];
        bgra.get(0, 0, bgraBytes);

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
        byte[] abgr = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < bgraBytes.length; i += 4) {
            abgr[i]     = bgraBytes[i + 3]; // A
            abgr[i + 1] = bgraBytes[i];     // B
            abgr[i + 2] = bgraBytes[i + 1]; // G
            abgr[i + 3] = bgraBytes[i + 2]; // R
        }
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

    /**
     * Takes a BGRA mat (cat silhouette with alpha channel), finds the tight bounding
     * box of the non-transparent pixels, crops to it with a small padding, then scales
     * the result to fit inside {@code size}×{@code size} preserving aspect ratio and
     * centres it on a transparent square canvas.
     */
    public static BufferedImage fitInSquare(Mat bgra, int size) {
        // Find bounding box of non-transparent pixels via the alpha channel.
        List<Mat> ch = new ArrayList<>();
        Core.split(bgra, ch);
        byte[] alphaData = new byte[(int) ch.get(3).total()];
        ch.get(3).get(0, 0, alphaData);

        int minX = bgra.cols(), minY = bgra.rows(), maxX = 0, maxY = 0;
        boolean found = false;
        for (int row = 0; row < bgra.rows(); row++) {
            for (int col = 0; col < bgra.cols(); col++) {
                if ((alphaData[row * bgra.cols() + col] & 0xFF) > 0) {
                    if (col < minX) minX = col;
                    if (col > maxX) maxX = col;
                    if (row < minY) minY = row;
                    if (row > maxY) maxY = row;
                    found = true;
                }
            }
        }

        if (!found) {
            minX = 0; minY = 0; maxX = bgra.cols() - 1; maxY = bgra.rows() - 1;
        }

        // Add a small padding around the tight bounds.
        int padX = Math.max(4, (maxX - minX) / 20);
        int padY = Math.max(4, (maxY - minY) / 20);
        int x0 = Math.max(0, minX - padX);
        int y0 = Math.max(0, minY - padY);
        int x1 = Math.min(bgra.cols(), maxX + padX + 1);
        int y1 = Math.min(bgra.rows(), maxY + padY + 1);

        Mat cropped = bgra.submat(y0, y1, x0, x1);

        // Scale to fit inside size×size preserving aspect ratio.
        double scale  = Math.min((double) size / cropped.cols(), (double) size / cropped.rows());
        int scaledW   = Math.max(1, (int)(cropped.cols() * scale));
        int scaledH   = Math.max(1, (int)(cropped.rows() * scale));
        Mat scaled    = new Mat();
        Imgproc.resize(cropped, scaled, new Size(scaledW, scaledH), 0, 0, Imgproc.INTER_AREA);

        // Centre on a fully transparent size×size canvas.
        int offsetX = (size - scaledW) / 2;
        int offsetY = (size - scaledH) / 2;
        Mat canvas  = Mat.zeros(size, size, CvType.CV_8UC4);
        scaled.copyTo(canvas.submat(offsetY, offsetY + scaledH, offsetX, offsetX + scaledW));

        return matToBufferedImage(canvas);
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