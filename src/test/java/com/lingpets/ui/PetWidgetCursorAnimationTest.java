package com.lingpets.ui;

import com.lingpets.model.Pet;
import javafx.animation.Animation;
import javafx.animation.Timeline;
import javafx.embed.swing.JFXPanel;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.*;

import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PetWidget.playCursorAnimation via reflection (private method).
 *
 * All JavaFX operations must run on the FX thread via runOnFX().
 * The GIF resource may not load in headless CI, so tests that require non-empty
 * gifFrames guard themselves with Assumptions.assumeTrue().
 */
class PetWidgetCursorAnimationTest {

    private static Method playCursorAnimMethod;
    private static Field gifFramesField;
    private static Field cursorAnimationField;

    private PetWidget widget;

    @BeforeAll
    static void bootstrapFX() throws Exception {
        // JFXPanel must be constructed on the AWT EDT to bootstrap the FX toolkit
        CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> { new JFXPanel(); latch.countDown(); });
        latch.await(5, TimeUnit.SECONDS);
        playCursorAnimMethod = PetWidget.class.getDeclaredMethod(
                "playCursorAnimation", double.class, Runnable.class);
        playCursorAnimMethod.setAccessible(true);
        gifFramesField = PetWidget.class.getDeclaredField("gifFrames");
        gifFramesField.setAccessible(true);
        cursorAnimationField = PetWidget.class.getDeclaredField("cursorAnimation");
        cursorAnimationField.setAccessible(true);
    }

    @BeforeEach
    void buildWidget() throws Exception {
        Pet pet = new Pet();
        pet.id = "test";
        pet.headId = "h1";
        pet.x = 100;
        pet.y = 100;
        pet.size = 120;
        WritableImage img = new WritableImage(10, 10);
        // PetStore is only touched by drag/scroll handlers — null is safe for cursor tests
        widget = runOnFX(() -> new PetWidget(pet, null, img, id -> img, id -> id));
    }

    @AfterEach
    void closeWidget() throws Exception {
        runOnFX(() -> widget.close());
    }

    // -------------------------------------------------------------------------
    // Reflection helpers — wrap checked exceptions so lambdas stay clean

    private void invokeAnim(double durationMs, Runnable onComplete) {
        try { playCursorAnimMethod.invoke(widget, durationMs, onComplete); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    @SuppressWarnings("unchecked")
    private List<?> gifFrames() {
        try { return (List<?>) gifFramesField.get(widget); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private Timeline cursorAnimation() {
        try { return (Timeline) cursorAnimationField.get(widget); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private void clearGifFrames() {
        try { gifFramesField.set(widget, List.of()); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    // -------------------------------------------------------------------------
    // FX-thread utility

    @FunctionalInterface
    interface FXTask<T> { T call() throws Exception; }

    static <T> T runOnFX(FXTask<T> task) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        javafx.application.Platform.runLater(() -> {
            try { f.complete(task.call()); }
            catch (Throwable t) { f.completeExceptionally(t); }
        });
        return f.get(5, TimeUnit.SECONDS);
    }

    static void runOnFX(Runnable r) throws Exception {
        runOnFX(() -> { r.run(); return null; });
    }

    // =========================================================================
    // Tests: empty gifFrames

    @Test
    void emptyFrames_onCompleteRunsImmediately() throws Exception {
        runOnFX(this::clearGifFrames);
        AtomicBoolean called = new AtomicBoolean(false);
        runOnFX(() -> invokeAnim(500, () -> called.set(true)));
        assertTrue(called.get(), "onComplete must fire synchronously when frames are empty");
    }

    @Test
    void emptyFrames_cursorAnimationRemainsNull() throws Exception {
        runOnFX(this::clearGifFrames);
        runOnFX(() -> invokeAnim(500, null));
        assertNull(cursorAnimation(), "cursorAnimation must stay null when frames are empty");
    }

    @Test
    void emptyFrames_nullOnCompleteDoesNotThrow() throws Exception {
        runOnFX(this::clearGifFrames);
        assertDoesNotThrow(() -> runOnFX(() -> invokeAnim(500, null)));
    }

    // =========================================================================
    // Tests: non-empty gifFrames (skipped in headless CI if GIF was not loaded)

    @Test
    void nonEmptyFrames_timelineIsRunning() throws Exception {
        Assumptions.assumeFalse(gifFrames().isEmpty(), "GIF not loaded — skipping");
        runOnFX(() -> invokeAnim(2000, null));
        Timeline tl = cursorAnimation();
        assertNotNull(tl);
        assertEquals(Animation.Status.RUNNING, tl.getStatus());
        runOnFX(tl::stop);
    }

    @Test
    void nonEmptyFrames_onCompleteFiresAfterDuration() throws Exception {
        Assumptions.assumeFalse(gifFrames().isEmpty(), "GIF not loaded — skipping");
        AtomicBoolean called = new AtomicBoolean(false);
        CompletableFuture<Void> done = new CompletableFuture<>();
        runOnFX(() -> invokeAnim(300, () -> { called.set(true); done.complete(null); }));
        done.get(3, TimeUnit.SECONDS);
        assertTrue(called.get(), "onComplete must fire after durationMs");
    }

    @Test
    void nonEmptyFrames_cursorAnimationNullAfterCompletion() throws Exception {
        Assumptions.assumeFalse(gifFrames().isEmpty(), "GIF not loaded — skipping");
        CompletableFuture<Void> done = new CompletableFuture<>();
        runOnFX(() -> invokeAnim(200, () -> done.complete(null)));
        done.get(3, TimeUnit.SECONDS);
        assertNull(cursorAnimation(), "cursorAnimation must be null after animation ends");
    }

    @Test
    void nonEmptyFrames_nullOnCompleteDoesNotThrow() throws Exception {
        Assumptions.assumeFalse(gifFrames().isEmpty(), "GIF not loaded — skipping");
        assertDoesNotThrow(() -> runOnFX(() -> invokeAnim(300, null)));
        Timeline tl = cursorAnimation();
        if (tl != null) runOnFX(tl::stop);
    }

    @Test
    void reentrant_previousTimelineIsStopped() throws Exception {
        Assumptions.assumeFalse(gifFrames().isEmpty(), "GIF not loaded — skipping");
        runOnFX(() -> invokeAnim(5000, null));
        Timeline first = cursorAnimation();
        assertNotNull(first);

        runOnFX(() -> invokeAnim(5000, null));
        assertEquals(Animation.Status.STOPPED, first.getStatus(),
                "Previous timeline must be stopped on re-entry");

        Timeline second = cursorAnimation();
        if (second != null) runOnFX(second::stop);
    }

    @Test
    void multipleClicks_onlyOneAnimationActiveAtATime() throws Exception {
        Assumptions.assumeFalse(gifFrames().isEmpty(), "GIF not loaded — skipping");

        List<Timeline> seen = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final int click = i;
            runOnFX(() -> invokeAnim(5000, null));
            Timeline current = cursorAnimation();

            assertNotNull(current, "click " + click + ": cursorAnimation must not be null");
            assertEquals(Animation.Status.RUNNING, current.getStatus(),
                    "click " + click + ": animation must be RUNNING immediately after click");

            for (int j = 0; j < seen.size(); j++) {
                assertEquals(Animation.Status.STOPPED, seen.get(j).getStatus(),
                        "click " + j + "'s timeline must be STOPPED after click " + click + " interrupted it");
            }
            seen.add(current);
        }

        runOnFX(cursorAnimation()::stop);
    }

    @Test
    void multipleClicks_lastAnimationCompletesAndCallsOnComplete() throws Exception {
        Assumptions.assumeFalse(gifFrames().isEmpty(), "GIF not loaded — skipping");

        // clicks 1–19: long-lived, never intended to complete
        for (int i = 0; i < 19; i++) {
            runOnFX(() -> invokeAnim(10_000, null));
        }

        // click 20: short duration — must complete and fire onComplete
        AtomicBoolean completed = new AtomicBoolean(false);
        CompletableFuture<Void> done = new CompletableFuture<>();
        runOnFX(() -> invokeAnim(300, () -> { completed.set(true); done.complete(null); }));

        done.get(3, TimeUnit.SECONDS);
        assertTrue(completed.get(), "20th click's onComplete must fire after its duration");
        assertNull(cursorAnimation(), "cursorAnimation must be null after last animation ends");
    }

    @Test
    void reentrant_newTimelineIsDistinctAndRunning() throws Exception {
        Assumptions.assumeFalse(gifFrames().isEmpty(), "GIF not loaded — skipping");
        runOnFX(() -> invokeAnim(5000, null));
        Timeline first = cursorAnimation();

        runOnFX(() -> invokeAnim(5000, null));
        Timeline second = cursorAnimation();

        assertNotNull(second);
        assertNotSame(first, second, "Re-entry must create a new Timeline");
        assertEquals(Animation.Status.RUNNING, second.getStatus());

        runOnFX(second::stop);
    }
}
