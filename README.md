# Desktop Cat Pets

A cross-platform desktop app that turns your cat photos into floating, always-on-top widgets. Drop in photos, the app cuts out the cat body using a neural network, and places a single floating icon on your desktop. Click it to pet your cat and cycle through your photo pool; scroll to resize; drag anywhere.

![Platform](https://img.shields.io/badge/platform-macOS%20%7C%20Windows%20%7C%20Linux-blue)
![Java](https://img.shields.io/badge/Java-21-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-21-green)

---

## Features

- **AI body cutout** — U2-Net neural network removes the background and cuts out the full cat silhouette with a transparent background. Falls back to GrabCut if the model is unavailable.
- **Floating widget** — one icon floats over all your windows; add as many photos as you like and click to cycle through them.
- **Drag** — press and drag the icon to reposition it anywhere on screen.
- **Resize** — scroll wheel over the icon to grow or shrink it (40 px – 300 px).
- **Pet** — click without dragging: a ✋ animation plays and the widget rotates to the next cat in your pool.
- **Persistent** — positions, sizes, and the photo pool survive restarts (stored under `~/.catpets/`).

---

## Requirements

| Dependency | Version |
|------------|---------|
| Java (JDK) | 21+     |
| Maven      | 3.6+    |
| Internet   | First launch only (model download, ~176 MB) |

No manual OpenCV, JavaFX, or Python installation needed — all dependencies are bundled as Maven dependencies.

---

## First-launch model download

On the very first run the app downloads the **U2-Net ONNX model** (~176 MB) from the [rembg](https://github.com/danielgatis/rembg) GitHub release and saves it to `~/.catpets/u2net.onnx`. This happens once; every subsequent launch loads it from disk in a few seconds.

While the download is in progress the **Add cat photos…** button shows a progress message and is temporarily disabled. Once the model is ready the button activates automatically.

---

## Running in development

```bash
git clone <repo-url>
cd PetWidget
mvn clean javafx:run
```

The **Desktop Cat Pets** control panel will appear. From there:

1. Wait for the model to download (first run only) — the status bar shows progress.
2. Click **Add cat photos…** and pick one or more image files (JPG, PNG, BMP, GIF).
3. The app cuts out each cat and adds it to the floating widget on screen.
4. Interact with your cats:
   - **Drag** the icon to move it.
   - **Scroll** over it to resize it.
   - **Click** it to pet and cycle to the next cat photo.
5. To remove a photo, select it in the control panel list and click **Remove selected**.

> **Note:** Do not run `Main` directly from an IDE — JavaFX requires the module path to be configured. Always use `mvn javafx:run` for development, or the packaged jar (see below).

---

## Building a standalone jar

```bash
mvn clean package
java -jar target/catpets.jar
```

The shade plugin produces a fat jar with all dependencies (including the platform-native OpenCV and ONNX Runtime libraries) bundled in. The jar is OS-specific — build on the OS you intend to run it on.

---

## Data storage

Everything is stored under `~/.catpets/`:

```
~/.catpets/
  pets.json       # photo pool + widget position/size
  u2net.onnx      # AI model, downloaded once (~176 MB)
  heads/          # cutout PNGs (transparent background)
    <uuid>.png
    ...
```

Delete this directory to reset the app to a clean state (the model will be re-downloaded on next launch).

---

## Tech stack

| Layer | Technology |
|-------|-----------|
| UI | JavaFX 21 (transparent always-on-top stages) |
| Background removal | U2-Net via ONNX Runtime 1.19 (`com.microsoft.onnxruntime`) |
| Fallback cutout | OpenCV 4.9 GrabCut via `org.openpnp:opencv` (native libs bundled) |
| Persistence | Gson 2.11 (JSON) |
| Build | Maven 3 + javafx-maven-plugin + maven-shade-plugin |

---

## Project structure

```
src/main/java/com/lingpets/
  Launcher.java               # jar entry point; loads OpenCV then launches JavaFX
  Main.java                   # JavaFX Application; wires all components together
  model/
    CatHead.java              # head metadata (id, filename, source name)
    Pet.java                  # placed widget instance (position, size, headId)
    PetStore.java             # single source of truth; all disk I/O
  detect/
    U2NetCutout.java          # U2-Net ONNX inference; downloads model on first use
    CatFaceDetector.java      # Haar cascade + GrabCut fallback body cutout
    CatCutout.java            # standalone GrabCut pipeline
  ui/
    ControlPanel.java         # main window: file picker + photo list + remove button
    PetWidget.java            # floating transparent window
  util/
    Images.java               # Mat <-> BufferedImage conversion; masking utilities
src/main/resources/cascades/
  haarcascade_frontalcatface_extended.xml
  haarcascade_frontalcatface.xml
```
