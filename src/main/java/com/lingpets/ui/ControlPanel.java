package com.lingpets.ui;

import com.lingpets.model.CatHead;
import com.lingpets.model.PetStore;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Main control window. Lets the user add and remove cat photos.
 *
 * Callbacks (all called on the FX thread):
 *   onAddFiles    — called with the list of files the user selected; Main handles detection + spawning
 *   onRemoveHead  — called with the headId to remove; Main handles widget teardown + store update
 */
public class ControlPanel {

    private final Stage stage;
    private final PetStore store;
    private final Consumer<List<File>> onAddFiles;
    private final Consumer<String> onRemoveHead;

    private final ListView<CatHead> listView;
    private final Button addBtn;
    private final Label statusLabel;

    public ControlPanel(Stage stage, PetStore store,
                        Consumer<List<File>> onAddFiles,
                        Consumer<String> onRemoveHead) {
        this.stage = stage;
        this.store = store;
        this.onAddFiles = onAddFiles;
        this.onRemoveHead = onRemoveHead;

        listView = new ListView<>();
        listView.setCellFactory(lv -> new CatHeadCell());
        refreshList();

        addBtn = new Button("Add cat photos…");
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.setOnAction(e -> pickFiles());

        Button removeBtn = new Button("Remove selected");
        removeBtn.setMaxWidth(Double.MAX_VALUE);
        removeBtn.setOnAction(e -> removeSelected());

        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 11;");
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setAlignment(Pos.CENTER);

        VBox bottom = new VBox(6, addBtn, removeBtn, statusLabel);
        bottom.setPadding(new Insets(8));

        BorderPane root = new BorderPane();
        root.setCenter(listView);
        root.setBottom(bottom);

        Scene scene = new Scene(root, 280, 420);
        stage.setScene(scene);
        stage.setTitle("Desktop Cat Pets");
        stage.setOnCloseRequest(e -> { e.consume(); stage.hide(); });
    }

    public void show() { stage.show(); }

    /** Re-reads the store and refreshes the list. Call from the FX thread. */
    public void refreshList() {
        listView.getItems().setAll(store.getHeads());
    }

    /**
     * Shows a status message and disables the add button while processing.
     * Pass null or blank to clear.
     */
    public void setStatus(String msg) {
        Platform.runLater(() -> {
            boolean busy = msg != null && !msg.isBlank();
            statusLabel.setText(busy ? msg : "");
            addBtn.setDisable(busy);
        });
    }

    // -------------------------------------------------------------------------

    private void pickFiles() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Pick cat photos");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png", "*.bmp", "*.gif")
        );
        List<File> files = fc.showOpenMultipleDialog(stage);
        if (files == null || files.isEmpty()) return;
        onAddFiles.accept(files);
    }

    private void removeSelected() {
        CatHead selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.INFORMATION, "Select a cat first.").showAndWait();
            return;
        }
        onRemoveHead.accept(selected.id);
    }

    // -------------------------------------------------------------------------

    private class CatHeadCell extends ListCell<CatHead> {
        private final ImageView thumb = new ImageView();
        private final Label nameLabel = new Label();

        CatHeadCell() {
            thumb.setFitWidth(40);
            thumb.setFitHeight(40);
            thumb.setPreserveRatio(true);
        }

        @Override
        protected void updateItem(CatHead head, boolean empty) {
            super.updateItem(head, empty);
            if (empty || head == null) {
                setGraphic(null);
                return;
            }
            Path imgPath = store.headImagePath(head);
            // background=true so the list stays responsive while thumbnails load
            thumb.setImage(new Image(imgPath.toUri().toString(), 40, 40, true, true, true));
            nameLabel.setText(head.sourceName);
            HBox row = new HBox(10, thumb, nameLabel);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(2, 0, 2, 0));
            setGraphic(row);
        }
    }
}
