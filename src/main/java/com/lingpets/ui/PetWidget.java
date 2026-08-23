package com.lingpets.ui;

import com.lingpets.model.Pet;
import com.lingpets.model.PetStore;
import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.function.Function;
import java.util.function.UnaryOperator;

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
    private final Text handEmoji;

    // drag tracking
    private double pressOffsetX, pressOffsetY;
    private double pressScreenX, pressScreenY;
    private boolean dragging;

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
        imageView.setFitWidth(pet.size);
        imageView.setFitHeight(pet.size);
        imageView.setPreserveRatio(false);

        handEmoji = new Text("✋"); // raised hand
        handEmoji.setFont(Font.font(pet.size * 0.45));
        handEmoji.setOpacity(0);

        StackPane root = new StackPane(imageView, handEmoji);
        root.setBackground(null);
        StackPane.setAlignment(handEmoji, Pos.CENTER);

        Scene scene = new Scene(root, pet.size, pet.size);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.setX(pet.x);
        stage.setY(pet.y);

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

    private void applySize(double size) {
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        handEmoji.setFont(Font.font(size * 0.45));
        stage.setWidth(size);
        stage.setHeight(size);
    }

    private void animatePet() {
        ScaleTransition pulse = new ScaleTransition(Duration.millis(120), imageView);
        pulse.setFromX(1.0); pulse.setFromY(1.0);
        pulse.setToX(1.25);  pulse.setToY(1.25);
        pulse.setCycleCount(2);
        pulse.setAutoReverse(true);

        FadeTransition fadeIn  = new FadeTransition(Duration.millis(80),  handEmoji);
        fadeIn.setFromValue(0); fadeIn.setToValue(1.0);
        FadeTransition hold    = new FadeTransition(Duration.millis(280), handEmoji);
        hold.setFromValue(1.0); hold.setToValue(1.0);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(180), handEmoji);
        fadeOut.setFromValue(1.0); fadeOut.setToValue(0);
        SequentialTransition hand = new SequentialTransition(fadeIn, hold, fadeOut);

        // rotate the head partway through the animation
        PauseTransition rotateTrigger = new PauseTransition(Duration.millis(320));
        rotateTrigger.setOnFinished(ev -> rotateHead());

        new ParallelTransition(pulse, hand, rotateTrigger).play();
    }

    private void rotateHead() {
        String newHeadId = nextHeadId.apply(pet.headId);
        if (newHeadId == null || newHeadId.equals(pet.headId)) return;
        pet.headId = newHeadId;
        try { store.updateHead(pet.id, newHeadId); }
        catch (Exception ex) { ex.printStackTrace(); }
        imageView.setImage(loadImage.apply(newHeadId));
    }
}
