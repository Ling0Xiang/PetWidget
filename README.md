# Desktop Cat Pets

A cross-platform desktop app that turns your cat photos into floating, always-on-top widgets. Drop in a photo, the app auto-detects the cat's face, crops it into a round icon, and places it on your desktop. Click it to pet your cat; scroll to resize it; drag it anywhere.

![Platform](https://img.shields.io/badge/platform-macOS%20%7C%20Windows%20%7C%20Linux-blue)
![Java](https://img.shields.io/badge/Java-21-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-21-green)

---

## Features

- **Auto face detection** — OpenCV Haar cascade finds the cat's head; falls back to a centred crop if no face is detected, so every photo produces a widget.
- **Circular floating icons** — heads are masked to a circle with transparent corners so they float cleanly over any window.
- **Drag** — press and drag a head to reposition it anywhere on screen.
- **Resize** — scroll wheel over a head to grow or shrink it (40 px – 300 px).
- **Pet** — click a head without dragging: a ✋ animation plays and the widget rotates to the next cat in your pool.
- **Persistent** — positions, sizes, and the head pool survive restarts (stored under `~/.catpets/`).
- **Multi-cat** — add as many photos as you like; each becomes its own floating widget.

---

## Requirements

| Dependency | Version |
|------------|---------|
| Java (JDK) | 21+     |
| Maven      | 3.6+    |

No manual OpenCV or JavaFX installation needed — both are bundled as Maven dependencies.

---

## Running in development

```bash
git clone <repo-url>
cd PetWidget
mvn clean javafx:run
```

The **Desktop Cat Pets** control panel will appear. From there:

1. Click **Add cat photos…** and pick one or more image files (JPG, PNG, BMP, GIF).
2. The app detects the cat face, crops a circular head, and places a floating widget on screen.
3. Interact with your cats:
   - **Drag** a head to move it.
   - **Scroll** over a head to resize it.
   - **Click** a head to pet it and cycle to the next cat.
4. To remove a cat, select it in the control panel list and click **Remove selected**.

> **Note:** Do not run `Main` directly from an IDE — JavaFX requires the module path to be configured. Always use `mvn javafx:run` for development, or the packaged jar (see below) for distribution.

---

## Building a standalone jar

```bash
mvn clean package
java -jar target/catpets.jar
```

The shade plugin produces a fat jar with all dependencies (including the platform-native OpenCV library) bundled in. The jar is OS-specific — build on the OS you intend to run it on.

---

## Data storage

Everything is stored under `~/.catpets/`:

```
~/.catpets/
  pets.json       # head pool + widget positions/sizes
  heads/          # cropped circular head PNGs
    <uuid>.png
    ...
```

Delete this directory to reset the app to a clean state.

---

## Tech stack

| Layer | Technology |
|-------|-----------|
| UI | JavaFX 21 (transparent always-on-top stages) |
| Face detection | OpenCV 4.9 via `org.openpnp:opencv` (native libs bundled) |
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
    CatFaceDetector.java      # Haar cascade detection + centred-crop fallback
  ui/
    ControlPanel.java         # main window: file picker + head list + remove button
    PetWidget.java            # floating transparent window per cat
  util/
    Images.java               # Mat <-> BufferedImage conversion; circular mask
src/main/resources/cascades/
  haarcascade_frontalcatface_extended.xml
  haarcascade_frontalcatface.xml
```
