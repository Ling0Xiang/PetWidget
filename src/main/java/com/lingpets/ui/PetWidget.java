package com.lingpets.ui;

import com.lingpets.model.Pet;
import com.lingpets.model.PetStore;
import javafx.animation.*;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.ImageCursor;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A floating, always-on-top, transparent window that shows one circular cat head.
 *
 * Gesture contract:
 *   press + move > threshold  → drag (repositions window)
 *   press + release in place  → pet (pulse animation then rotate to next head)
 *   scroll wheel              → resize (clamped MIN_SIZE..MAX_SIZE)
 */
public class PetWidget {

    private static final double MIN_SIZE = 40;
    private static final double MAX_SIZE = 300;
    private static final double DRAG_THRESHOLD = 6; // px total movement to enter drag mode

    private final Pet pet;
    private final PetStore store;
    private final Function<String, Image> loadImage;
    private final UnaryOperator<String> nextHeadId;

    private final Stage stage;
    private final ImageView imageView;

    // drag tracking
    private double pressOffsetX, pressOffsetY;
    private double pressScreenX, pressScreenY;
    private boolean dragging;

    private double aspectRatio = 1.0; // width / height of the current head image

    private List<CursorFrame> gifFrames = List.of();
    private Timeline cursorAnimation;
    private PauseTransition restoreTransition;
    private PauseTransition sizeUpdateTransition;

    private static final class CursorFrame {
        final ImageCursor cursor;
        final int delayMs;
        CursorFrame(ImageCursor cursor, int delayMs) { this.cursor = cursor; this.delayMs = delayMs; }
    }

    public PetWidget(Pet pet, PetStore store, Image initialImage,
                     Function<String, Image> loadImage,
                     UnaryOperator<String> nextHeadId) {
        this.pet = pet;
        this.store = store;
        this.loadImage = loadImage;
        this.nextHeadId = nextHeadId;

        stage = new Stage(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);

        imageView = new ImageView(initialImage);
        imageView.setPreserveRatio(false);
        aspectRatio = imageRatio(initialImage);
        double[] initDims = scaledDims(pet.size, aspectRatio);
        imageView.setFitWidth(initDims[0]);
        imageView.setFitHeight(initDims[1]);

        StackPane root = new StackPane(imageView);
        root.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(root, initDims[0], initDims[1]);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.setX(pet.x);
        stage.setY(pet.y);

        gifFrames = loadGifFrames("/cursors/petpet-transparent.gif", (int)(pet.size * 0.2));
        wireEvents(root);
    }

    public void show()  { stage.show(); }
    public void close() { stage.close(); }
    public String getPetId()  { return pet.id; }
    public String getHeadId() { return pet.headId; }

    // -------------------------------------------------------------------------

    private void wireEvents(StackPane root) {
        root.setOnMousePressed(e -> {
            pressOffsetX = e.getScreenX() - stage.getX();
            pressOffsetY = e.getScreenY() - stage.getY();
            pressScreenX = e.getScreenX();
            pressScreenY = e.getScreenY();
            dragging = false;
            e.consume();
        });

        root.setOnMouseDragged(e -> {
            double moved = Math.abs(e.getScreenX() - pressScreenX)
                         + Math.abs(e.getScreenY() - pressScreenY);
            if (moved > DRAG_THRESHOLD) dragging = true;
            if (dragging) {
                stage.setX(e.getScreenX() - pressOffsetX);
                stage.setY(e.getScreenY() - pressOffsetY);
            }
            e.consume();
        });

        root.setOnMouseReleased(e -> {
            if (dragging) {
                pet.x = stage.getX();
                pet.y = stage.getY();
                try { store.updatePosition(pet.id, pet.x, pet.y); }
                catch (Exception ex) { ex.printStackTrace(); }
            } else {
                animatePet();
            }
            dragging = false;
            e.consume();
        });

        root.setOnMouseEntered(e -> startHoverCursor());
        root.setOnMouseExited(e -> stopHoverCursor());

        root.setOnScroll(e -> {
            double delta = Math.signum(e.getDeltaY()) * 10;
            double newSize = Math.max(MIN_SIZE, Math.min(MAX_SIZE, pet.size + delta));
            if (newSize != pet.size) {
                pet.size = newSize;
                applySize(newSize);
                try { store.updateSize(pet.id, newSize); }
                catch (Exception ex) { ex.printStackTrace(); }
            }
            e.consume();
        });
    }

    private void startHoverCursor() {
        if (gifFrames.isEmpty() || cursorAnimation != null) return;
        Scene scene = stage.getScene();
        Timeline tl = new Timeline();
        double t = 0;
        for (CursorFrame f : gifFrames) {
            ImageCursor c = f.cursor;
            tl.getKeyFrames().add(new KeyFrame(Duration.millis(t), ev -> scene.setCursor(c)));
            t += f.delayMs;
        }
        tl.setCycleCount(Timeline.INDEFINITE);
        tl.play();
        cursorAnimation = tl;
    }

    private void stopHoverCursor() {
        if (restoreTransition != null) { restoreTransition.stop(); restoreTransition = null; }
        if (cursorAnimation != null) { cursorAnimation.stop(); cursorAnimation = null; }
        stage.getScene().setCursor(javafx.scene.Cursor.DEFAULT);
    }

    // Called after rotation: update imageView only, never the stage.
    // Stage resize (which invalidates macOS cursor tracking) is deferred to scroll.
    private void updateImageViewSize() {
        double[] dims = scaledDims(pet.size, aspectRatio);
        imageView.setFitWidth(dims[0]);
        imageView.setFitHeight(dims[1]);
    }

    private void applySize(double size) {
        double[] dims = scaledDims(size, aspectRatio);
        imageView.setFitWidth(dims[0]);
        imageView.setFitHeight(dims[1]);
        stage.setWidth(dims[0]);
        stage.setHeight(dims[1]);
    }

    private static double imageRatio(Image img) {
        double h = img.getHeight();
        return (h > 0) ? img.getWidth() / h : 1.0;
    }

    /** Returns {width, height} so the longest side equals {@code size}. */
    private static double[] scaledDims(double size, double ratio) {
        if (ratio >= 1.0) return new double[]{size, size / ratio};
        else              return new double[]{size * ratio, size};
    }

    private void animatePet() {
        ScaleTransition pulse = new ScaleTransition(Duration.millis(120), imageView);
        pulse.setFromX(1.0); pulse.setFromY(1.0);
        pulse.setToX(1.25);  pulse.setToY(1.25);
        pulse.setCycleCount(2);
        pulse.setAutoReverse(true);

        PauseTransition rotateTrigger = new PauseTransition(Duration.millis(320));
        rotateTrigger.setOnFinished(ev -> rotateHead());

        // Cancel any in-flight size update so rapid clicks don't stack them
        if (sizeUpdateTransition != null) sizeUpdateTransition.stop();
        sizeUpdateTransition = new PauseTransition(Duration.millis(540));
        sizeUpdateTransition.setOnFinished(ev -> { updateImageViewSize(); sizeUpdateTransition = null; });
        sizeUpdateTransition.play();

        // Cursor is owned by hover — ensure it's running (covers the edge case where
        // gifFrames finished loading after mouseEntered already fired)
        startHoverCursor();

        new ParallelTransition(pulse, rotateTrigger).play();
    }

    private void playCursorAnimation(double durationMs, Runnable onComplete) {
        if (gifFrames.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        if (restoreTransition != null) { restoreTransition.stop(); restoreTransition = null; }
        if (cursorAnimation != null) { cursorAnimation.stop(); cursorAnimation = null; }
        Scene scene = stage.getScene();
        Timeline tl = new Timeline();
        double t = 0;
        for (CursorFrame f : gifFrames) {
            ImageCursor c = f.cursor;
            tl.getKeyFrames().add(new KeyFrame(Duration.millis(t), ev -> scene.setCursor(c)));
            t += f.delayMs;
        }
        tl.setCycleCount(Timeline.INDEFINITE);
        tl.play();
        cursorAnimation = tl;
        PauseTransition restore = new PauseTransition(Duration.millis(durationMs));
        restore.setOnFinished(ev -> {
            tl.stop();
            cursorAnimation = null;
            restoreTransition = null;
            scene.setCursor(javafx.scene.Cursor.DEFAULT);
            if (onComplete != null) onComplete.run();
        });
        restore.play();
        restoreTransition = restore;
    }

    private List<CursorFrame> loadGifFrames(String resource, int targetSize) {
        int size = Math.max(8, targetSize);
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) return List.of();
            ImageInputStream iis = ImageIO.createImageInputStream(in);
            Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("gif");
            if (!it.hasNext()) return List.of();
            ImageReader reader = it.next();
            reader.setInput(iis);
            int n = reader.getNumImages(true);
            List<CursorFrame> frames = new ArrayList<>();
            BufferedImage canvas = null;
            for (int i = 0; i < n; i++) {
                BufferedImage raw = reader.read(i);
                if (canvas == null)
                    canvas = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = canvas.createGraphics();
                g.setComposite(AlphaComposite.Clear);
                g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
                g.setComposite(AlphaComposite.SrcOver);
                g.drawImage(raw, 0, 0, null);
                g.dispose();
                BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                Graphics2D gs = scaled.createGraphics();
                gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                gs.drawImage(canvas, 0, 0, size, size, null);
                gs.dispose();
                WritableImage fx = SwingFXUtils.toFXImage(scaled, null);
                ImageCursor cursor = new ImageCursor(fx, size / 2.0, 2);
                frames.add(new CursorFrame(cursor, gifFrameDelayMs(reader.getImageMetadata(i))));
            }
            reader.dispose();
            return frames;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static int gifFrameDelayMs(IIOMetadata meta) {
        try {
            Node tree = meta.getAsTree("javax_imageio_gif_image_1.0");
            NodeList children = tree.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if ("GraphicControlExtension".equals(child.getNodeName())) {
                    Node d = child.getAttributes().getNamedItem("delayTime");
                    if (d != null) return Integer.parseInt(d.getNodeValue()) * 10;
                }
            }
        } catch (Exception ignored) {}
        return 100;
    }

    private void rotateHead() {
        String newHeadId = nextHeadId.apply(pet.headId);
        if (newHeadId == null || newHeadId.equals(pet.headId)) return;
        pet.headId = newHeadId;
        try { store.updateHead(pet.id, newHeadId); }
        catch (Exception ex) { ex.printStackTrace(); }
        Image newImg = loadImage.apply(newHeadId);
        aspectRatio = imageRatio(newImg);
        imageView.setImage(newImg);
        // window resize is deferred to after the cursor animation via playCursorAnimation callback
    }
}
