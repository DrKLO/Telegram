package org.telegram.utils.pip;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.Utilities;

/**
 * One show of one {@link PipContent}. Moves between the primary UI, the floating window
 * (overlay or in-app) and system PiP. Created by {@link PipCoordinator#register}.
 */
public final class PipSession {
    public static final int STATE_PRIMARY = 0;
    public static final int STATE_FLOATING = 1;
    public static final int STATE_SYSTEM = 2;
    public static final int STATE_DISMISSED = 3;

    public final PipContent content;
    final PipCoordinator coordinator;

    int state = STATE_PRIMARY;
    int stateBeforeSystem = STATE_PRIMARY;
    @Nullable FloatingHost floating;
    boolean ownFloating;
    boolean ownFloatingOverlay;

    PipSession(PipCoordinator coordinator, PipContent content) {
        this.coordinator = coordinator;
        this.content = content;
    }

    public int getState() {
        return state;
    }

    /** Shown in the host's floating window (not in a window of its own). */
    public boolean isFloating() {
        return floating != null && (state == STATE_FLOATING || state == STATE_SYSTEM && stateBeforeSystem == STATE_FLOATING);
    }

    /**
     * For content with its own floating window (a call bubble): the window is shown or hidden.
     * Lets system PiP replace an in-app window when the user leaves the app.
     */
    public void setOwnFloating(boolean shown, boolean overlay) {
        if (state == STATE_DISMISSED) {
            return;
        }
        ownFloating = shown;
        ownFloatingOverlay = overlay;
        if (state == STATE_SYSTEM) {
            stateBeforeSystem = shown ? STATE_FLOATING : STATE_PRIMARY;
        } else {
            state = shown ? STATE_FLOATING : STATE_PRIMARY;
        }
        coordinator.invalidateSystem();
    }

    public boolean isAlive() {
        return state != STATE_DISMISSED;
    }

    /**
     * Primary → floating window. The window appears at its saved place with {@code placeholder}
     * until the content routes frames into the returned target; the content hides its primary UI itself.
     * Returns null for content with its own {@link PipContent#getFloatingView()}.
     */
    public @Nullable PipTextureView startFloating(@NonNull Activity activity, @Nullable Bitmap placeholder, boolean animateAppear) {
        if (state != STATE_PRIMARY) {
            throw new IllegalStateException("startFloating in state " + state);
        }
        coordinator.dismissOtherFloating(this);
        floating = new FloatingHost(this, activity);
        final PipTextureView target = floating.show(placeholder, animateAppear);
        state = STATE_FLOATING;
        coordinator.invalidateSystem();
        return target;
    }

    /** Current floating window bounds in screen coordinates. */
    public boolean getFloatingRect(@NonNull RectF out) {
        if (floating == null) {
            return false;
        }
        floating.getRect(out);
        return true;
    }

    /** Where the floating window will appear for content of this size, in screen coordinates. */
    public static void getFloatingTargetRect(int contentWidth, int contentHeight, @NonNull RectF out) {
        FloatingHost.getTargetRect(contentWidth, contentHeight, out);
    }

    public static float getFloatingCornerRadius() {
        return FloatingHost.getCornerRadius();
    }

    /** Floating → primary, requested by the user (expand button). */
    public void expand() {
        if (state != STATE_FLOATING) {
            return;
        }
        coordinator.runWhenAppResumed(() -> {
            if (state == STATE_FLOATING) {
                content.onExpandRequested();
            }
        });
    }

    /**
     * Called by the content during its expand transition: removes the floating window and hands
     * the texture back. The content must already cover the window with its own placeholder.
     */
    public void completeExpand(@NonNull Utilities.Callback<SurfaceTexture> onTexture) {
        if (state != STATE_FLOATING || floating == null) {
            return;
        }
        final FloatingHost host = floating;
        floating = null;
        state = STATE_PRIMARY;
        host.removeForExpand(onTexture);
        coordinator.invalidateSystem();
    }

    /** Close PiP (user, system or the content). Ends the session for good. */
    public void dismiss() {
        if (state == STATE_DISMISSED) {
            return;
        }
        final int prevState = state;
        state = STATE_DISMISSED;
        if (floating != null) {
            // the host releases its texture after the content has released the producer
            final FloatingHost host = floating;
            floating = null;
            host.dismiss(prevState == STATE_FLOATING, content::onDismissed);
            coordinator.onSessionDismissed(this, prevState);
        } else {
            content.onDismissed();
            coordinator.onSessionDismissed(this, prevState);
        }
    }

    /** The content is gone while it is shown in its own UI (e.g. the viewer was closed). */
    public void release() {
        if (state == STATE_DISMISSED) {
            return;
        }
        if (state != STATE_PRIMARY) {
            dismiss();
            return;
        }
        state = STATE_DISMISSED;
        coordinator.onSessionDismissed(this, STATE_PRIMARY);
    }

    /** Enter system PiP right now (a PiP button in the content's own UI). False if not possible. */
    public boolean enterSystemPip() {
        return state != STATE_DISMISSED && coordinator.enterSystemPip(this);
    }

    /** Playback state, size or availability changed. */
    public void invalidate() {
        if (floating != null) {
            floating.invalidateState();
        }
        coordinator.invalidateSystem();
    }

    public void onVideoCompleted() {
        if (floating != null) {
            floating.onVideoCompleted();
        }
        coordinator.invalidateSystem();
    }

    /* Rewind UI, forwarded from the content's rewinder */

    public void onRewindStart(boolean forward) {
        if (floating != null) {
            floating.onRewindStart(forward);
        }
    }

    public void onRewindProgress(long timeDiff, float progress, boolean byBackSeek) {
        if (floating != null) {
            floating.onRewindProgress(progress, byBackSeek);
        }
    }

    public void onRewindCanceled() {
        if (floating != null) {
            floating.onRewindCanceled();
        }
    }
}
