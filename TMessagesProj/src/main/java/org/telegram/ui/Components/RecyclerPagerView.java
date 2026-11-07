/* Telegram for Android. GPL v2 or later. */
package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import org.telegram.ui.ActionBar.Theme;

/**
 * ViewPagerFixed bridge for sibling-list coordination, without modifying ViewPagerFixed.
 * Use on the UI thread as a fullscreen pager in the coordinator's pager layer. Pages may contain nested
 * containers and overlays; containers on the list's ancestor path must allow vertical overscan.
 * Every adapter binding must call onPageBound() with the page root, not its nested list.
 */
public class RecyclerPagerView extends ViewPagerFixed {
    private final SiblingRecyclerPagerLayout coordinator;
    private boolean touchStartedOnCommon;
    private View cancelledCurrentPage;
    private View cancelledOtherPage;

    /** Creates a pager bound to one coordinator; install it with the coordinator's setPager(). */
    public RecyclerPagerView(Context context, Theme.ResourcesProvider resourcesProvider,
                             SiblingRecyclerPagerLayout coordinator) {
        super(context, resourcesProvider);
        this.coordinator = coordinator;
        // Allow vertical blur overscan; drawChild still isolates adjacent pages horizontally.
        setClipChildren(false);
        setClipToPadding(false);
    }

    /** Reports the fully bound adapter page root, including reuse; Pages resolves the list to reset. */
    public void onPageBound(@NonNull View page) {
        clearCancelledPagePair();
        coordinator.onPageBound(page);
    }

    /** Starts a fresh drag, including another attempt after reversing at a pager boundary. */
    @Override
    public void onStartTracking() {
        clearCancelledPagePair();
        super.onStartTracking();
    }

    /** Observes synchronous drag cancellation without mistaking release/settling for cancellation. */
    @Override
    public boolean onTouchEventInternal(MotionEvent event) {
        boolean wasTracking = isTouch();
        boolean handled = super.onTouchEventInternal(event);
        // ViewPagerFixed can abandon tracking on MOVE when reversing past the first/last
        // page. It restores translations but retains slot 1 and emits no scroll-end callback.
        // UP/CANCEL instead start an animator: those must keep the transition alive.
        if (wasTracking && !isTouch() && event != null
                && event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            rememberCancelledPagePair();
        }
        return handled;
    }

    /** Restores vertical coordination when a caller synchronously resets a tracked drag. */
    @Override
    public void resetTouch() {
        boolean wasTracking = isTouch();
        super.resetTouch();
        if (wasTracking && !isTouch()) {
            rememberCancelledPagePair();
        }
    }

    private void rememberCancelledPagePair() {
        View[] visiblePages = getViewPages();
        View current = visiblePages[0];
        View other = visiblePages[1];
        if (current == null || other == null || current.getTranslationX() != 0f
                || current.getMeasuredWidth() <= 0
                || Math.abs(other.getTranslationX()) < current.getMeasuredWidth()) {
            return;
        }
        cancelledCurrentPage = current;
        cancelledOtherPage = other;
        coordinator.debugPagerEvent("PAGER_DRAG_ABORTED");
        coordinator.onPagerChanged();
    }

    private void clearCancelledPagePair() {
        cancelledCurrentPage = null;
        cancelledOtherPage = null;
    }

    // Retained offscreen slots are not evidence of an active transition. Identity and
    // translation checks also let a subsequent programmatic animation proceed normally.
    boolean isCancelledPagePair(View current, View other) {
        return current != null && other != null && current == cancelledCurrentPage
                && other == cancelledOtherPage && !isTouch()
                && current.getTranslationX() == 0f && current.getMeasuredWidth() > 0
                && Math.abs(other.getTranslationX()) >= current.getMeasuredWidth();
    }

    /** Updates shared-list presentation after either a gesture or programmatic pager animation. */
    @Override
    public void onTabAnimationUpdate(boolean manual) {
        super.onTabAnimationUpdate(manual);
        coordinator.onPagerChanged();
    }

    @Override
    protected void onTabScrollEnd(int position) {
        super.onTabScrollEnd(position);
        coordinator.debugPagerEvent("PAGER_TAB_END");
        coordinator.onPagerChanged();
    }

    @Override
    protected void onScrollEnd() {
        super.onScrollEnd();
        coordinator.debugPagerEvent("PAGER_SCROLL_END");
        coordinator.onPagerChanged();
    }

    @Override
    protected float getAvailableTranslationX() {
        return Math.max(1, getMeasuredWidth());
    }

    /** Re-records the parent display list when a page moves its parent-space drawing clip. */
    @Override
    protected void setTranslationX(View view, float translationX) {
        if (view.getTranslationX() == translationX) {
            return;
        }
        super.setTranslationX(view, translationX);
        // drawChild records a clip in pager coordinates. A child's RenderNode translation
        // can change without re-recording that parent display list, leaving the incoming
        // page clipped at its original offscreen position. Invalidate drawing, not layout.
        invalidate();
    }

    @Override
    protected boolean canScroll(MotionEvent event) {
        return !touchStartedOnCommon && coordinator.canStartPageSwipe() && super.canScroll(event);
    }

    private void rememberTouchOrigin(MotionEvent event) {
        if (event != null && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            touchStartedOnCommon = coordinator.isCommonContentAt(event.getX(), event.getY());
            coordinator.debugPagerEvent(touchStartedOnCommon ? "PAGER_DOWN_COMMON" : "PAGER_DOWN_PAGE");
        }
    }

    /** Prevents pager interception for a gesture whose initial down hit common content. */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        rememberTouchOrigin(event);
        // Also prevent taking over a settling animation from a DOWN on common rows.
        return !touchStartedOnCommon && super.onInterceptTouchEvent(event);
    }

    /** Rejects common-origin gestures; a null event still forwards ViewPagerFixed's cancellation. */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) {
            coordinator.debugPagerEvent("PAGER_CANCEL_NULL");
            touchStartedOnCommon = false;
            return super.onTouchEvent(null);
        }
        rememberTouchOrigin(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            coordinator.debugPagerEvent(event.getActionMasked() == MotionEvent.ACTION_UP ? "PAGER_UP" : "PAGER_CANCEL");
        }
        return !touchStartedOnCommon && super.onTouchEvent(event);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        int save = canvas.save();
        canvas.clipRect(child.getX(), -coordinator.getTopBleed(), child.getX() + child.getWidth(),
                getHeight() + coordinator.getBottomBleed());
        boolean result = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(save);
        return result;
    }
}
