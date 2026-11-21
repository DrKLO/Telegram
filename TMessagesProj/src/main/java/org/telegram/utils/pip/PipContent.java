package org.telegram.utils.pip;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.Player;

import org.telegram.ui.Components.SeekSpeedDrawable;

/**
 * Implemented by the consumer. Describes one piece of content (currently a video)
 * that can be shown in its own full-screen UI ("primary") or in a PiP host.
 * The content knows nothing about windows; hosts know nothing about the player.
 * Main thread only.
 */
public interface PipContent {

    /* Geometry */

    int getContentWidth();

    int getContentHeight();

    /**
     * A view (e.g. a WebView) that moves into the floating window as is, for content whose frames
     * cannot be routed into a {@link PipTextureView}. Such content is never shown in system PiP.
     */
    default @Nullable View getFloatingView() {
        return null;
    }

    /** False keeps the floating window inside the app even with the overlay permission. */
    default boolean isOverlayAllowed() {
        return true;
    }

    /* Playback */

    /** Floating window shows play/pause, progress and seek gestures. */
    default boolean hasPlaybackControls() {
        return true;
    }

    /**
     * Paused with a hidden in-app window ({@link #pause}), the content goes on by itself when the
     * app is back (a live stream: there is nothing to press play on).
     */
    default boolean resumesWithApp() {
        return false;
    }

    boolean isPlaying();

    /** Playback reached the end and is not looping. */
    boolean isCompleted();

    void play();

    void pause();

    long getDuration();

    long getCurrentPosition();

    /** 0..1 */
    float getBufferedProgress();

    void seekTo(long positionMs);

    /** Long-press rewind, as in the full-screen viewer. Returns false if not supported. */
    default boolean startRewind(boolean forward, float x, @NonNull SeekSpeedDrawable seekSpeedDrawable) {
        return false;
    }

    default void updateRewind(float x) {}

    default void cancelRewind() {}

    default boolean isRewinding() {
        return false;
    }

    /** Extra layer above the video in the floating window (e.g. frames preview while rewinding). */
    default void drawFloatingOverlay(@NonNull Canvas canvas, int width, int height) {}

    /** Player for MediaSession while the content is in system PiP. */
    default @Nullable Player getMediaSessionPlayer() {
        return null;
    }

    /* Own UI (calls): the content shows its own primary UI and its own floating window */

    /** Higher wins system PiP when nothing is shown in its primary UI. Calls are above video. */
    default int getPriority() {
        return 0;
    }

    /**
     * A view that renders this content in system PiP (e.g. a WebRTC renderer), instead of routing
     * frames into a {@link PipTextureView} through {@link #releasePrimary} / {@link #restorePrimary}.
     */
    default @Nullable View createSystemView(@NonNull android.content.Context context) {
        return null;
    }

    /** Entering system PiP with {@link #createSystemView}: hide own windows, route frames into the view. */
    default void onSystemEnter(@NonNull View systemView) {}

    /**
     * The window is in PiP mode now. Entering without a source rect hint, the system view is
     * shown only from here: own full-screen UI may stay until this point, the system covers the
     * window with the app icon while it shrinks.
     */
    default void onSystemEntered(@NonNull View systemView) {}

    /** Back in the app: show own windows again and take the frames back. */
    default void onSystemExit(@NonNull View systemView) {}

    /** The view from {@link #createSystemView} is detached and not used any more. */
    default void releaseSystemView(@NonNull View systemView) {}

    /** Buttons of the system PiP window, at most {@code maxActions}. */
    default void getSystemActions(@NonNull java.util.List<PipAction> out, int maxActions) {}

    default void onSystemAction(int actionId) {}

    /* Policy */

    /**
     * The content may go to system PiP when the user leaves the app even if none of its UI is on
     * screen (an ongoing group call without the bubble).
     */
    default boolean canEnterWithoutUi() {
        return false;
    }

    /**
     * Whether the content wants to keep being shown after the user leaves the app
     * (system PiP when there is no overlay permission). For video: it is playing.
     */
    boolean needsPipOutsideApp();

    /* Primary UI */

    /** Primary UI of this content is on screen right now (used for auto-enter into system PiP). */
    boolean isPrimaryVisible();

    /** Bounds of the video inside the primary UI, in screen coordinates. Empty if unknown. */
    void getPrimaryVideoRect(@NonNull Rect outScreenRect);

    /**
     * Immediately move frames from the primary UI into {@code target} and hide the primary UI
     * (no animation, used by system PiP).
     */
    void releasePrimary(@NonNull PipTextureView target);

    /** Immediately show the primary UI again and take the frames back (used by system PiP). */
    void restorePrimary(@NonNull SurfaceTexture surfaceTexture);

    /**
     * User asked to return from the floating window to the primary UI. The content runs its own
     * transition and calls {@link PipSession#completeExpand} when it is ready to take the frames.
     */
    void onExpandRequested();

    /** The session is over (closed by the user or by the system). Release everything. */
    void onDismissed();
}
