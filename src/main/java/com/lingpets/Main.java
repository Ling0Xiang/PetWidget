package com.lingpets;

import com.lingpets.detect.CatFaceDetector;
import com.lingpets.detect.U2NetCutout;
import com.lingpets.model.CatHead;
import com.lingpets.model.Pet;
import com.lingpets.model.PetStore;
import com.lingpets.ui.ControlPanel;
import com.lingpets.ui.PetWidget;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Main extends Application {

    private PetStore store;
    private CatFaceDetector detector;
    private ControlPanel controlPanel;
    private final List<PetWidget> widgets = new ArrayList<>();
    private volatile U2NetCutout u2netCutout; // null until model is ready

    @Override
    public void start(Stage primaryStage) {
        store = new PetStore();
        detector = new CatFaceDetector();

        controlPanel = new ControlPanel(primaryStage, store, this::onAddFiles, this::onRemoveHead);

        // Restore any widgets that were open last session
        for (Pet pet : store.getPets()) {
            spawnWidget(pet);
        }

        // If heads exist but the pet record was lost, auto-spawn for the first head
        if (widgets.isEmpty() && !store.getHeads().isEmpty()) {
            autoSpawnForFirstHead();
        }

        controlPanel.show();
        initU2Net();
    }

    @Override
    public void stop() throws Exception {
        if (u2netCutout != null) u2netCutout.close();
    }

    // -------------------------------------------------------------------------
    // U2-Net model initialisation

    private void initU2Net() {
        Path modelPath = U2NetCutout.defaultModelPath();
        if (!Files.exists(modelPath)) {
            controlPanel.setStatus("Downloading AI model (one-time, ~176 MB)…");
        } else {
            controlPanel.setStatus("Loading AI model…");
        }
        Thread.ofVirtual().start(() -> {
            try {
                U2NetCutout.ensureModel(modelPath, bytes ->
                    Platform.runLater(() ->
                        controlPanel.setStatus("Downloading AI model… " + (bytes / 1_048_576) + " MB")));
                u2netCutout = new U2NetCutout(modelPath);
            } catch (Exception e) {
                e.printStackTrace(); // falls back to detectBody at photo-add time
            } finally {
                Platform.runLater(() -> controlPanel.setStatus(null));
            }
        });
    }

    // -------------------------------------------------------------------------
    // ControlPanel callbacks (called on FX thread)

    private void onAddFiles(List<File> files) {
        controlPanel.setStatus("Processing " + files.size() + " photo(s)…");
        AtomicInteger remaining = new AtomicInteger(files.size());

        for (File file : files) {
            // Detection is CPU-heavy — run on a virtual thread
            Thread.ofVirtual().start(() -> {
                try {
                    BufferedImage src = ImageIO.read(file);
                    if (src == null) {
                        decrement(remaining);
                        return;
                    }
                    BufferedImage tmp;
                    try {
                        tmp = u2netCutout != null
                                ? u2netCutout.cutout(src)
                                : detector.detectBody(src);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        tmp = detector.detectBody(src);
                    }
                    final BufferedImage headImg = scaleToMax(tmp, 600);

                    Platform.runLater(() -> {
                        try {
                            CatHead head = store.addHead(headImg, file.getName());
                            if (widgets.isEmpty()) {
                                Pet pet = store.addPet(head.id, randomScreenX(), randomScreenY());
                                spawnWidget(pet);
                            }
                            controlPanel.refreshList();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        } finally {
                            decrement(remaining);
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    decrement(remaining);
                }
            });
        }
    }

    private void decrement(AtomicInteger remaining) {
        if (remaining.decrementAndGet() == 0) controlPanel.setStatus(null);
    }

    private void onRemoveHead(String headId) {
        // Close widgets currently showing this head
        widgets.removeIf(w -> {
            if (w.getHeadId().equals(headId)) {
                w.close();
                return true;
            }
            return false;
        });
        try {
            store.removeHead(headId); // also removes all pets that still reference headId
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        controlPanel.refreshList();

        // Keep a widget alive as long as any heads remain
        if (widgets.isEmpty() && !store.getHeads().isEmpty()) {
            autoSpawnForFirstHead();
        }
    }

    // -------------------------------------------------------------------------
    // Widget helpers

    private void autoSpawnForFirstHead() {
        try {
            CatHead first = store.getHeads().get(0);
            Pet pet = store.addPet(first.id, randomScreenX(), randomScreenY());
            spawnWidget(pet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void spawnWidget(Pet pet) {
        if (store.findHead(pet.headId).isEmpty()) return; // orphaned pet, skip
        Image img = loadImage(pet.headId);
        PetWidget widget = new PetWidget(pet, store, img, this::loadImage, this::nextHeadId);
        widgets.add(widget);
        widget.show();
    }

    /** Loads the head image from disk as a JavaFX Image, capped at 600 px on the long side. */
    private Image loadImage(String headId) {
        return store.findHead(headId)
                    .map(h -> new Image(store.headImagePath(h).toUri().toString(),
                                        600, 600, true, true, false))
                    .orElseThrow(() -> new IllegalStateException("Missing head: " + headId));
    }

    /** Scales {@code src} so the long side is at most {@code maxSide}; returns src unchanged if already small enough. */
    private static BufferedImage scaleToMax(BufferedImage src, int maxSide) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxSide && h <= maxSide) return src;
        double scale = (double) maxSide / Math.max(w, h);
        int nw = Math.max(1, (int)(w * scale));
        int nh = Math.max(1, (int)(h * scale));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    /**
     * Returns the next headId in the pool after currentHeadId, wrapping around.
     * Returns currentHeadId unchanged when there is only one head.
     */
    private String nextHeadId(String currentHeadId) {
        List<CatHead> heads = store.getHeads();
        if (heads.size() <= 1) return currentHeadId;
        for (int i = 0; i < heads.size(); i++) {
            if (heads.get(i).id.equals(currentHeadId)) {
                return heads.get((i + 1) % heads.size()).id;
            }
        }
        return currentHeadId; // not found — shouldn't happen
    }

    /** Scatters new pets loosely around the centre of the primary screen. */
    private double randomScreenX() {
        Rectangle2D b = Screen.getPrimary().getVisualBounds();
        return b.getMinX() + b.getWidth()  * 0.5 + (Math.random() - 0.5) * 200;
    }

    private double randomScreenY() {
        Rectangle2D b = Screen.getPrimary().getVisualBounds();
        return b.getMinY() + b.getHeight() * 0.5 + (Math.random() - 0.5) * 200;
    }
}
