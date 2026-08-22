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
    Launcher.java        # jar entry point (does NOT extend Application)
    Main.java            # JavaFX Application
    model/CatHead.java   # DONE
    model/Pet.java
    model/PetStore.java
    detect/CatFaceDetector.java
    ui/ControlPanel.java
    ui/PetWidget.java
    util/Images.java     # Mat <-> BufferedImage, circular mask
```

## Current progress
- [x] Maven `pom.xml` (JavaFX + OpenCV + Gson + shade plugin)
- [x] Both Haar cascade XML files in `src/main/resources/cascades/`
- [ ] `model/CatHead.java`
- 
- [ ] Everything else (Launcher, Main, Pet, PetStore, CatFaceDetector, Images, ControlPanel, PetWidget)

## Next step
Implement `util/Images.java` and `detect/CatFaceDetector.java` first (the core, testable
part), verify by running the detector on a sample cat photo headlessly, then build the
JavaFX UI (ControlPanel + PetWidget) and wire in PetStore persistence.

## Gotcha
Running `Main` directly can fail with "JavaFX runtime components are missing" because
JavaFX is on the classpath, not the module path. Use `mvn javafx:run`, or the packaged
jar via the `Launcher` class.
