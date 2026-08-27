package com.lingpets.detect;

import ai.onnxruntime.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.FloatBuffer;
import java.nio.file.*;
import java.time.Duration;
import java.util.Map;
import java.util.function.LongConsumer;

/**
 * Background removal using the U2-Net ONNX model.
 *
 * On first use the ~176 MB model is downloaded from the rembg GitHub release
 * and cached at ~/.catpets/u2net.onnx. Subsequent launches load it from disk.
 *
 * Thread-safe: cutout() may be called from any thread.
 */
public final class U2NetCutout implements AutoCloseable {

    private static final String MODEL_URL =
            "https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2net.onnx";
    private static final int     INPUT_SIZE = 320;
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD  = {0.229f, 0.224f, 0.225f};

    private final OrtEnvironment env;
    private final OrtSession     session;

    public U2NetCutout(Path modelPath) throws OrtException {
        env     = OrtEnvironment.getEnvironment();
        session = env.createSession(modelPath.toString(), new OrtSession.SessionOptions());
    }

    public static Path defaultModelPath() {
        return Path.of(System.getProperty("user.home"), ".catpets", "u2net.onnx");
    }

    /**
     * Downloads the model to {@code modelPath} if it doesn't already exist.
     * {@code onProgress} is called with cumulative bytes received (may be null).
     */
    public static void ensureModel(Path modelPath, LongConsumer onProgress)
            throws IOException, InterruptedException {
        if (Files.exists(modelPath)) return;
        Files.createDirectories(modelPath.getParent());
        Path tmp = modelPath.resolveSibling(modelPath.getFileName() + ".tmp");
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(MODEL_URL))
                    .timeout(Duration.ofMinutes(15))
                    .build();
            HttpResponse<InputStream> resp =
                    client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200)
                throw new IOException("HTTP " + resp.statusCode() + " from model URL");
            long received = 0;
            try (InputStream in  = resp.body();
                 OutputStream out = Files.newOutputStream(tmp)) {
                byte[] buf = new byte[65_536];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    received += n;
                    if (onProgress != null) onProgress.accept(received);
                }
            }
            Files.move(tmp, modelPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    /**
     * Removes the background from {@code src}.
     * Returns a BufferedImage with a transparent background, tight-cropped to the subject.
     */
    public BufferedImage cutout(BufferedImage src) throws OrtException {
        int origW = src.getWidth(), origH = src.getHeight();

        // 1. Resize to 320×320 (RGB).
        BufferedImage resized = new BufferedImage(INPUT_SIZE, INPUT_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, INPUT_SIZE, INPUT_SIZE, null);
        g.dispose();

        // 2. Build CHW float32 tensor, normalised with ImageNet stats.
        int plane = INPUT_SIZE * INPUT_SIZE;
        float[] chw = new float[3 * plane];
        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                int rgb = resized.getRGB(x, y);
                float r  = ((rgb >> 16) & 0xFF) / 255f;
                float gv = ((rgb >>  8) & 0xFF) / 255f;
                float b  = ( rgb        & 0xFF) / 255f;
                int idx  = y * INPUT_SIZE + x;
                chw[idx]            = (r  - MEAN[0]) / STD[0];
                chw[plane   + idx]  = (gv - MEAN[1]) / STD[1];
                chw[2*plane + idx]  = (b  - MEAN[2]) / STD[2];
            }
        }

        // 3. Run inference — take the first output (d0, highest quality).
        String inName  = session.getInputNames() .iterator().next();
        String outName = session.getOutputNames().iterator().next();
        long[] shape   = {1, 3, INPUT_SIZE, INPUT_SIZE};

        try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), shape);
             OrtSession.Result result = session.run(Map.of(inName, inputTensor))) {

            float[][][][] mask = (float[][][][]) ((OnnxTensor) result.get(outName).get()).getValue();

            // 4. Up-sample mask to original size and apply as alpha channel.
            BufferedImage out = new BufferedImage(origW, origH, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < origH; y++) {
                for (int x = 0; x < origW; x++) {
                    float mx = (x + 0.5f) * INPUT_SIZE / origW - 0.5f;
                    float my = (y + 0.5f) * INPUT_SIZE / origH - 0.5f;
                    float alpha = Math.max(0f, Math.min(1f, bilinear(mask[0][0], mx, my)));
                    int a = (int)(alpha * 255);
                    out.setRGB(x, y, (a << 24) | (src.getRGB(x, y) & 0x00FFFFFF));
                }
            }

            return tightCrop(out);
        }
    }

    @Override
    public void close() throws OrtException {
        session.close();
    }

    // -------------------------------------------------------------------------

    private static float bilinear(float[][] grid, float x, float y) {
        int h = grid.length, w = grid[0].length;
        x = Math.max(0, Math.min(w - 1, x));
        y = Math.max(0, Math.min(h - 1, y));
        int x0 = Math.min(w - 2, (int) x), y0 = Math.min(h - 2, (int) y);
        float fx = x - x0, fy = y - y0;
        return grid[y0  ][x0  ] * (1-fx) * (1-fy)
             + grid[y0  ][x0+1] *    fx  * (1-fy)
             + grid[y0+1][x0  ] * (1-fx) *    fy
             + grid[y0+1][x0+1] *    fx  *    fy;
    }

    private static BufferedImage tightCrop(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int minX = w, minY = h, maxX = 0, maxY = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((img.getRGB(x, y) >>> 24) > 10) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (minX > maxX) return img;
        int pad = Math.max(4, (maxX - minX) / 20);
        minX = Math.max(0, minX - pad);
        minY = Math.max(0, minY - pad);
        maxX = Math.min(w - 1, maxX + pad);
        maxY = Math.min(h - 1, maxY + pad);
        // Copy the subimage so it isn't backed by the full-size original.
        BufferedImage sub  = img.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
        BufferedImage copy = new BufferedImage(sub.getWidth(), sub.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copy.createGraphics().drawImage(sub, 0, 0, null);
        return copy;
    }
}