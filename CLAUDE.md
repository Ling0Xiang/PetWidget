# Desktop Cat Pets — project context

This file orients Claude Code (CLI) so it can continue the project. Read it first.

## What this is
A small **cross-platform desktop "pet" app**. The user feeds it photos of cats;
the app auto-detects the cat's head, crops it into a round icon, and shows it as a
floating, always-on-top widget on the desktop. Clicking a head plays a "petting"
hand animation and swaps that spot to a different cat from the pool.

Full spec (with diagrams): the user has it as an artifact titled "Desktop Cat Pets".

## Tech stack (already decided)
- **Java 21**, **JavaFX 21** for the UI (transparent always-on-top windows)
- **OpenCV 4.9** via `org.openpnp:opencv` (bundles native libs for Win/macOS/Linux)
- **Gson** for JSON persistence
- **Maven** build. Run with `mvn clean javafx:run`; package a fat jar with `mvn clean package`.

## User stories (the acceptance checklist)
1. Add multiple pictures (multi-select file picker → each becomes a head + a pet)
2. Delete pictures (list in the control panel, remove head file + widget)
3. Drag icons around the screen (press-and-drag a floating head)
4. Resize icons (scroll wheel over a head; clamped min/max)
- Plus: click a head → petting-hand animation → rotate to a different head.

## Key design decisions (don't silently reverse these)
- Heads are cropped to a **circular mask with transparent corners** (PNG) so they
  float cleanly. (Could switch to square icons — that's a small change.)
- **Resize = scroll wheel** is the primary gesture (no visible handle cluttering the head).
- One mouse gesture means three things — the widget distinguishes:
  press+move = drag, press+release-in-place = pet, scroll = resize.
- Detection = OpenCV **Haar cascade** (`haarcascade_frontalcatface_extended.xml`,
  fall back to the non-extended one), with a **centered-crop fallback** when no face
  is found so every image still yields a head.
- **PetStore is the single source of truth** and the only thing that writes to disk.
  The Detector is a pure image-in → head-out function (easy to unit-test headless).

## Data model
- `CatHead { id, fileName, sourceName }` — a cropped head in the pool.
- `Pet { id, headId, x, y, size }` — a placed instance on the desktop.
- On disk under `~/.catpets/`: `pets.json` ({heads:[...], pets:[...]}) + `heads/*.png`.

## Planned file layout
```
catpets/
  pom.xml                                  # DONE
  src/main/resources/cascades/*.xml        # DONE (both cascades downloaded)
  src/main/java/com/lingpets/
    Launcher.java        # DONE
    Main.java            # DONE
    model/CatHead.java   # DONE
    model/Pet.java       # DONE
    model/PetStore.java  # DONE
    detect/CatFaceDetector.java  # DONE
    ui/ControlPanel.java  # DONE
    ui/PetWidget.java     # DONE
    util/Images.java     # DONE — Mat <-> BufferedImage, circular mask
```

## Current progress
- [x] Maven `pom.xml` (JavaFX + OpenCV + Gson + shade plugin)
- [x] Both Haar cascade XML files in `src/main/resources/cascades/`
- [x] `model/CatHead.java`
- [x] `model/Pet.java`
- [x] `model/PetStore.java` — disk persistence, single source of truth
- [x] `util/Images.java` — Mat↔BufferedImage conversion, circular mask
- [x] `detect/CatFaceDetector.java` — Haar cascade + centred-crop fallback
- [x] `ui/PetWidget.java` — floating always-on-top head; drag / scroll-resize / click-to-pet
- [x] `ui/ControlPanel.java` — file picker, head list with thumbnails, remove button
- [x] `Launcher.java` — loads OpenCV native lib then calls Application.launch
- [x] `Main.java` — wires PetStore + detector + ControlPanel + PetWidgets

## Current status
All planned files are implemented and compile cleanly (`mvn compile` — 9 source files, 0 errors).

Run the app:
  mvn clean javafx:run

## Next step
Smoke-test: add a cat photo and verify the widget appears. Then test drag, scroll-resize,
and click-to-pet rotation.

## Gotcha
Running `Main` directly can fail with "JavaFX runtime components are missing" because
JavaFX is on the classpath, not the module path. Use `mvn javafx:run`, or the packaged
jar via the `Launcher` class.
