package org.telegram.ui.iv;

import android.animation.ValueAnimator;

import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * Animates a cell's nesting inset (px) when the SAME block's depth changes — e.g. a block dragged in or out of
 * a list/quote — and snaps instantly when a different block is bound (view recycling on scroll). A cell owns one
 * instance and drives it from {@code bind()} and, after a reorder, from {@link RichInsetCell#resyncBlockInset()}.
 */
class RichBlockInset {

    interface Applier {
        void apply(int px);
    }

    private long boundRowId = Long.MIN_VALUE;
    private int currentPx = -1;
    private ValueAnimator animator;

    int current() {
        return Math.max(0, currentPx);
    }

    /** Applies the row's target inset, animating (when requested) from the current value if the same block is bound. */
    void apply(BlockRow row, Applier applier) {
        apply(row, applier, true);
    }

    void apply(BlockRow row, Applier applier, boolean animated) {
        final int target = RichBlockChrome.insetFor(row);
        final long rid = row != null ? row.id : Long.MIN_VALUE;
        final boolean sameBlock = rid == boundRowId && currentPx >= 0;
        boundRowId = rid;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (animated && sameBlock && currentPx != target) {
            final int from = currentPx;
            final ValueAnimator a = ValueAnimator.ofInt(from, target);
            a.addUpdateListener(anim -> {
                currentPx = (int) anim.getAnimatedValue();
                applier.apply(currentPx);
            });
            a.setInterpolator(CubicBezierInterpolator.DEFAULT);
            a.setDuration(200);
            animator = a;
            a.start();
        } else {
            currentPx = target;
            applier.apply(target);
        }
    }
}
