package org.telegram.ui.Components.blur3.utils;

import android.graphics.Bitmap;

import java.lang.ref.WeakReference;

/**
 * Tracks whether a Bitmap has changed since it was last recorded.
 *
 * A bitmap counts as changed when it is a different instance than the one
 * recorded, when its contents were modified (generationId advanced), when the
 * recorded instance was garbage collected, or when {@link #invalidate()} was
 * called explicitly.
 *
 * The bitmap is held weakly, so this tracker never keeps it alive.
 *
 * Not thread-safe.
 */
public class BitmapChangeTracker {

    private WeakReference<Bitmap> ref;
    private long generationId;

    /** Starts out invalidated: nothing has been recorded yet. */
    private boolean invalidated = true;

    /** Records the current state of {@code bitmap}. May be null. */
    public void set(Bitmap bitmap) {
        ref = bitmap != null ? new WeakReference<>(bitmap) : null;
        generationId = generationOf(bitmap);
        invalidated = false;
    }

    /** True if {@code bitmap} differs from the recorded state. May be null. */
    public boolean isInvalidated(Bitmap bitmap) {
        if (invalidated) {
            return true;
        }

        final Bitmap recorded = ref != null ? ref.get() : null;
        if (recorded != bitmap) {
            // Different instance, or the recorded one was collected.
            return true;
        }

        return generationOf(bitmap) != generationId;
    }

    /** Forces the next {@link #isInvalidated} call to report a change. */
    public void invalidate() {
        ref = null;
        generationId = 0;
        invalidated = true;
    }

    private static long generationOf(Bitmap bitmap) {
        return bitmap != null && !bitmap.isRecycled() ? bitmap.getGenerationId() : 0;
    }
}