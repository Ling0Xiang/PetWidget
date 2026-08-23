package com.lingpets;

import com.lingpets.detect.CatFaceDetector;
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
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Main extends Application {

    private PetStore store;
    private CatFaceDetector detector;
    private ControlPanel controlPanel;
    private final List<PetWidget> widgets = new ArrayList<>();

    @Override
    public void start(Stage primaryStage) {
        store = new PetStore();
        detector = new CatFaceDetector();

        controlPanel = new ControlPanel(primaryStage, store, this::onAddFiles, this::onRemoveHead);

        // Restore any widgets that were open last session
        for (Pet pet : store.getPets()) {
            spawnWidget(pet);
        }

        controlPanel.show();
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
                    BufferedImage headImg = detector.detect(src);

                    Platform.runLater(() -> {
                        try {
                            CatHead head = store.addHead(headImg, file.getName());
                            Pet pet = store.addPet(head.id, randomScreenX(), randomScreenY());
                            spawnWidget(pet);
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
    }

    // -------------------------------------------------------------------------
    // Widget helpers

    private void spawnWidget(Pet pet) {
        if (store.findHead(pet.headId).isEmpty()) return; // orphaned pet, skip
        Image img = loadImage(pet.headId);
        PetWidget widget = new PetWidget(pet, store, img, this::loadImage, this::nextHeadId);
        widgets.add(widget);
        widget.show();
    }

    /** Loads the head image from disk as a JavaFX Image. */
    private Image loadImage(String headId) {
        return store.findHead(headId)
                    .map(h -> new Image(store.headImagePath(h).toUri().toString()))
                    .orElseThrow(() -> new IllegalStateException("Missing head: " + headId));
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
