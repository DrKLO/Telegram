package org.telegram.ui.Components.chat;

import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks position changes of multiple Views relative to a specified ancestor ViewGroup.
 * Coordinates are computed manually by summing getX()/getY() up the hierarchy.
 *
 * Works through a single ViewTreeObserver.OnPreDrawListener attached to the given anchorView.
 */
public final class ViewPositionWatcher implements
        ViewTreeObserver.OnPreDrawListener,
        View.OnAttachStateChangeListener {

    /** Per-view callback invoked when a view's position relative to its parent changes. */
    public interface OnChangedListener {
        void onPositionChanged(@NonNull View view, @NonNull RectF rectInParent);
    }

    private final View anchorView;
    private ViewTreeObserver vto;
    private boolean listening;

    /** Per-view tracking state. */
    private static final class Tracked {
        final ViewGroup parent;
        final OnChangedListener listener;
        final RectF last = new RectF();
        boolean hasLast;

        Tracked(@NonNull ViewGroup parent, @NonNull OnChangedListener listener) {
            this.parent = parent;
            this.listener = listener;
        }
    }

    private final WeakHashMap<View, List<Tracked>> tracked = new WeakHashMap<>();
    private final RectF tmpRect = new RectF(); // reused for all calculations

    public ViewPositionWatcher(@NonNull View anchorView) {
        this.anchorView = anchorView;
        anchorView.addOnAttachStateChangeListener(this);
        attachIfPossible();
    }

    /** Subscribe a view for tracking relative to the given parent (must be an ancestor). */
    public void subscribe(@NonNull View view,
                          @NonNull ViewGroup parentView,
                          @NonNull OnChangedListener listener) {
        Tracked t = new Tracked(parentView, listener);
        List<Tracked> tList = tracked.get(view);
        if (tList == null) {
            tList = new ArrayList<>(1);
            tracked.put(view, tList);
        }
        tList.add(t);

        computeRectInParent(view, parentView, tmpRect);
        t.last.set(tmpRect);
        t.hasLast = true;

        ensureListening();
    }

    /** Unsubscribe a specific view. */
    public void unsubscribe(@NonNull View view) {
        tracked.remove(view);
    }

    /** Clear all subscriptions. */
    public void clear() {
        tracked.clear();
    }

    /** Stop watching entirely. */
    public void shutdown() {
        detachIfListening();
        anchorView.removeOnAttachStateChangeListener(this);
        tracked.clear();
    }

    // ─────────────── ViewTreeObserver lifecycle ───────────────

    private void attachIfPossible() {
        if (!anchorView.isAttachedToWindow()) return;
        ViewTreeObserver newVto = anchorView.getViewTreeObserver();
        if (newVto != null && newVto.isAlive()) {
            vto = newVto;
            if (!listening) {
                vto.addOnPreDrawListener(this);
                listening = true;
            }
        }
    }

    private void ensureListening() {
        if (!listening) attachIfPossible();
    }

    private void detachIfListening() {
        if (listening && vto != null && vto.isAlive()) {
            vto.removeOnPreDrawListener(this);
        }
        listening = false;
        vto = null;
    }

    @Override
    public void onViewAttachedToWindow(@NonNull View v) {
        attachIfPossible();
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull View v) {
        if (v == anchorView) {
            detachIfListening();
        }
    }

    // ─────────────── OnPreDraw ───────────────

    @Override
    public boolean onPreDraw() {
        // Reattach if VTO changed
        ViewTreeObserver current = anchorView.getViewTreeObserver();
        if (current != vto) {
            detachIfListening();
            attachIfPossible();
        }

        if (tracked.isEmpty()) return true;

        for (Map.Entry<View, List<Tracked>> e : tracked.entrySet()) {
            View view = e.getKey();
            List<Tracked> tList = e.getValue();
            if (view == null || tList == null) continue;

            for (Tracked t : tList) {
                if (!computeRectInParent(view, t.parent, tmpRect)) continue;

                if (!t.hasLast || !tmpRect.equals(t.last)) {
                    t.last.set(tmpRect);
                    t.hasLast = true;
                    try {
                        t.listener.onPositionChanged(view, new RectF(tmpRect));
                    } catch (Throwable ignored) {
                        // Do not crash UI if callback throws
                    }
                }
            }
        }
        return true;
    }

    // ─────────────── Coordinate calculation ───────────────

    public static float computeYCoordinateInParent(@NonNull View view, @NonNull ViewGroup parentView) {
        computeRectInParent(view, parentView, tmpRectF2);
        return tmpRectF2.top;
    }

    private static RectF tmpRectF2 = new RectF();
    public static boolean computeCoordinatesInParent(@NonNull View view,
                                                   @NonNull ViewGroup parentView, PointF out) {
        final boolean result = computeRectInParent(view, parentView, tmpRectF2);
        if (result) {
            out.x = tmpRectF2.left;
            out.y = tmpRectF2.top;
        }

        return result;
    }

    /**
     * Compute the view's rect in parentView coordinates
     * by summing getX()/getY() up the hierarchy until reaching parentView.
     *
     * @return false if parentView is not an ancestor of view.
     */

    private static boolean computeRectInParent(@NonNull View view,
                                               @NonNull ViewGroup parentView,
                                               @NonNull RectF out) {
        float left = 0f;
        float top = 0f;

        View current = view;
        while (current != null && current != parentView) {
            left += current.getX();
            top  += current.getY();

            ViewParent vp = current.getParent();
            if (!(vp instanceof View)) {
                return false; // parentView not found in hierarchy
            }
            current = (View) vp;
        }

        if (current != parentView) {
            // parentView not found in ancestor chain
            return false;
        }

        final float l = left;
        final float t = top;
        final float r = l + view.getWidth();
        final float b = t + view.getHeight();
        out.set(l, t, r, b);
        return true;
    }
}
