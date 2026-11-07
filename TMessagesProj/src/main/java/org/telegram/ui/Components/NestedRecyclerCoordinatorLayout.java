/* Telegram for Android. GPL v2 or later. */
package org.telegram.ui.Components;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.IdentityHashMap;

/**
 * Three phases: outer only; shared outer/inner collapse; inner only.
 * Native RecyclerView owns touch coordinates, velocity tracking and the only fling clock.
 * NEVER call scrollBy on the source of a nested callback: RecyclerView reuses callback arrays.
 */
public class NestedRecyclerCoordinatorLayout extends SizeNotifierFrameLayout implements NestedScrollingParent3 {
    public enum PageSwitchPolicy { RESET_INNER_WHEN_UNPINNED, PIN_OUTER_AND_KEEP_INNER }

    public interface Section {
        @NonNull View getViewportView();
        @Nullable RecyclerView getCurrentInnerRecyclerView();
        /** Final outer boundary, NOT the tabs' pin position. Usually 0 in root coordinates. */
        float getPinnedTop(@NonNull NestedRecyclerCoordinatorLayout parent);
        @Nullable default View getPinnedHeader() { return null; }
        default float getPinnedHeaderTop(@NonNull NestedRecyclerCoordinatorLayout parent) { return getPinnedTop(parent); }
        default float getPinnedHeaderOffset(@NonNull NestedRecyclerCoordinatorLayout parent) { return 0f; }
        default float getInnerContentMaxTopInset(@NonNull NestedRecyclerCoordinatorLayout parent) {
            View header = getPinnedHeader();
            return header == null ? -1f : Math.max(getPinnedHeaderOffset(parent),
                    getPinnedHeaderTop(parent) - getPinnedTop(parent)) + header.getMeasuredHeight();
        }
        /** Override for reverse/custom LayoutManagers; infinity is valid beyond the shared range. */
        default float getInnerScrollOffset(@NonNull RecyclerView recyclerView) { return getStartOffset(recyclerView); }
        /** Override to apply to both instantiated ViewPager pages, including the incoming one. */
        default void applyInnerContentGeometry(@NonNull NestedRecyclerCoordinatorLayout parent,
                @NonNull RecyclerView recyclerView, int fixedTopInset, float translationY) {
            parent.setInnerContentGeometry(recyclerView, fixedTopInset, translationY);
        }
        default void onPinnedChanged(boolean pinned) {}
        default void onHeaderPinnedChanged(boolean pinned) {}
        default void onPinOffsetChanged(float distance) {}
        default void onHeaderPinOffsetChanged(float distance) {}
    }

    private static final class PageState { int virtualOffset; }
    private final NestedScrollingParentHelper nestedHelper = new NestedScrollingParentHelper(this);
    private final IdentityHashMap<RecyclerView, PageState> pageStates = new IdentityHashMap<>();
    private final int[] legacyConsumed = new int[2];
    private RecyclerView outerRecyclerView, innerRecyclerView, captureTarget;
    private Section section;
    private PageSwitchPolicy pageSwitchPolicy = PageSwitchPolicy.RESET_INNER_WHEN_UNPINNED;
    private View callbackSource;
    private int capturedDy, programmaticDepth;
    private boolean pinned, headerPinned;

    private final NestedScrollCoupling coupling = new NestedScrollCoupling(new NestedScrollCoupling.Host() {
        @Override public float viewportTop() { return getViewportTop(); }
        @Override public float outerStopTop() { return section.getPinnedTop(NestedRecyclerCoordinatorLayout.this); }
        @Override public float sharedStartTop() { return getSharedStartTop(); }
        @Override public float innerOffset() {
            return innerRecyclerView == null ? 0f : section.getInnerScrollOffset(innerRecyclerView);
        }
        @Override public int virtualOffset() {
            return innerRecyclerView == null ? 0 : state(innerRecyclerView).virtualOffset;
        }
        @Override public void virtualOffset(int value) {
            if (innerRecyclerView != null) state(innerRecyclerView).virtualOffset = Math.max(0, value);
        }
        @Override public int scrollOuter(int dy) { return scrollOther(outerRecyclerView, dy); }
        @Override public int scrollInner(int dy) { return scrollOther(innerRecyclerView, dy); }
    });

    private final RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
        @Override public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
            if (view == captureTarget) capturedDy += dy;
            if (callbackSource == null && programmaticDepth == 0) updateGeometry();
        }
    };
    private final ViewTreeObserver.OnPreDrawListener preDrawListener = () -> {
        resolveInnerRecyclerView();
        updateGeometry();
        return true;
    };

    public NestedRecyclerCoordinatorLayout(Context context) { super(context); }

    /**
     * Required outer LM. Clamp BEFORE RecyclerView reports consumption, not in onPreDraw.
     * Chunking discovers a not-yet-attached viewport even during a large one-shot delta.
     * Rows after the viewport do not extend the permitted scroll range.
     */
    public LinearLayoutManager createOuterLayoutManager(Context context) {
        return new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
                if (dy <= 0 || section == null) return super.scrollVerticallyBy(dy, recycler, state);
                int used = 0;
                while (used < dy) {
                    int request = Math.min(dy - used, Math.max(1, getHeight() / 2));
                    if (isSectionAttached()) {
                        request = Math.min(request, Math.max(0, Math.round(getViewportTop()
                                - section.getPinnedTop(NestedRecyclerCoordinatorLayout.this))));
                    }
                    if (request == 0) break;
                    int step = super.scrollVerticallyBy(request, recycler, state);
                    used += step;
                    if (step < request) break;
                }
                return used;
            }
        };
    }

    public void setOuterRecyclerView(@Nullable RecyclerView view) {
        stopScrolling();
        if (outerRecyclerView != null) outerRecyclerView.removeOnScrollListener(scrollListener);
        outerRecyclerView = view;
        if (view != null) view.addOnScrollListener(scrollListener);
        resolveInnerRecyclerView();
        updateGeometry();
    }
    public void setSection(@Nullable Section value) {
        stopScrolling();
        section = value;
        resolveInnerRecyclerView();
        updateGeometry();
    }
    public void setPageSwitchPolicy(@NonNull PageSwitchPolicy policy) { pageSwitchPolicy = policy; }

    /** Call once from Adapter.bindView, before the incoming/cached page is drawn. */
    public void prepareInnerRecyclerView(@NonNull RecyclerView view) {
        state(view);
        if (section != null && !isPinnedPosition()) {
            if (pageSwitchPolicy == PageSwitchPolicy.RESET_INNER_WHEN_UNPINNED) {
                view.stopScroll();
                state(view).virtualOffset = 0;
                RecyclerView.LayoutManager manager = view.getLayoutManager();
                if (manager instanceof LinearLayoutManager) {
                    ((LinearLayoutManager) manager).scrollToPositionWithOffset(0, 0);
                } else {
                    view.scrollToPosition(0);
                }
            } else if (view.canScrollVertically(-1) && isSectionAttached()) {
                scrollOther(outerRecyclerView, Math.max(0, Math.round(getViewportTop() - section.getPinnedTop(this))));
            }
        }
        updateGeometry();
    }
    public void onSectionChanged() { stopScrolling(); resolveInnerRecyclerView(); updateGeometry(); }
    /** Geometry only: never scrollToPosition on every horizontal animation frame. */
    public void onSectionTransition() { stopScrolling(); resolveInnerRecyclerView(); updateGeometry(); }
    /** Call when discarding a cached page or rebinding it to a different data set. */
    public void forgetInnerRecyclerView(@NonNull RecyclerView view) { pageStates.remove(view); }
    public boolean isPinned() { return pinned; }
    public boolean isHeaderPinned() { return headerPinned; }
    public float getDistanceToPinFloat() {
        return isSectionAttached() ? Math.max(0f, getViewportTop() - section.getPinnedTop(this)) : Float.POSITIVE_INFINITY;
    }

    private float getSharedStartTop() {
        float stop = section.getPinnedTop(this);
        return section.getPinnedHeader() == null ? stop
                : Math.max(stop, section.getPinnedHeaderTop(this) - section.getPinnedHeaderOffset(this));
    }
    private float getViewportTop() { return getYRelativeTo(section.getViewportView(), this); }
    private boolean isPinnedPosition() {
        return isSectionAttached() && getViewportTop() <= section.getPinnedTop(this) + 0.5f;
    }
    private boolean isSectionAttached() {
        return section != null && outerRecyclerView != null && section.getViewportView().isAttachedToWindow()
                && !Float.isInfinite(getYRelativeTo(section.getViewportView(), this));
    }
    private PageState state(RecyclerView view) {
        PageState value = pageStates.get(view);
        if (value == null) { value = new PageState(); pageStates.put(view, value); }
        return value;
    }
    private void resolveInnerRecyclerView() {
        RecyclerView resolved = section == null ? null : section.getCurrentInnerRecyclerView();
        if (resolved == innerRecyclerView) return;
        if (innerRecyclerView != null) {
            innerRecyclerView.stopScroll();
            innerRecyclerView.removeOnScrollListener(scrollListener);
        }
        innerRecyclerView = resolved;
        if (resolved != null && resolved != outerRecyclerView) {
            state(resolved);
            resolved.addOnScrollListener(scrollListener);
        }
    }

    /** Exact near the start; avoids estimated scrollbar offsets for variable-height rows. */
    public static float getStartOffset(@NonNull RecyclerView view) {
        RecyclerView.LayoutManager manager = view.getLayoutManager();
        if (manager == null || !view.canScrollVertically(-1)) return 0f;
        View first = manager.findViewByPosition(0);
        if (first == null) return Float.POSITIVE_INFINITY;
        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) first.getLayoutParams();
        return Math.max(0f, view.getPaddingTop() - manager.getDecoratedTop(first) + params.topMargin);
    }
    /** Static geometry changes only, not driven by vertical scrolling. */
    public void setInnerContentTopInset(@NonNull RecyclerView view, int topInset) {
        topInset = Math.max(0, topInset);
        if (view.getPaddingTop() == topInset) return;
        RecyclerView.LayoutManager manager = view.getLayoutManager();
        View anchor = manager == null || manager.getChildCount() == 0 ? null : manager.getChildAt(0);
        int position = anchor == null ? RecyclerView.NO_POSITION : manager.getPosition(anchor);
        int offset = anchor == null ? 0 : manager.getDecoratedTop(anchor) - view.getPaddingTop()
                - ((RecyclerView.LayoutParams) anchor.getLayoutParams()).topMargin;
        view.setPadding(view.getPaddingLeft(), topInset, view.getPaddingRight(), view.getPaddingBottom());
        if (manager instanceof LinearLayoutManager && position != RecyclerView.NO_POSITION) {
            ((LinearLayoutManager) manager).scrollToPositionWithOffset(position, offset);
        }
    }
    public void setInnerContentGeometry(@NonNull RecyclerView view, int fixedTopInset, float translationY) {
        setInnerContentTopInset(view, fixedTopInset);
        float value = translationY - state(view).virtualOffset;
        if (view.getTranslationY() != value) view.setTranslationY(value);
    }

    private void updateGeometry() {
        if (section == null) return;
        View header = section.getPinnedHeader();
        if (!isSectionAttached()) {
            if (header != null) header.setVisibility(View.INVISIBLE);
            setPinned(false);
            setHeaderPinned(false);
            section.onPinOffsetChanged(Float.POSITIVE_INFINITY);
            section.onHeaderPinOffsetChanged(Float.POSITIVE_INFINITY);
            return;
        }
        float top = getViewportTop();
        float stop = section.getPinnedTop(this);
        if (header != null) {
            float natural = top + section.getPinnedHeaderOffset(this);
            float pin = section.getPinnedHeaderTop(this);
            float headerTop = Math.max(natural, pin);
            header.setTranslationY(headerTop - header.getTop());
            header.setVisibility(View.VISIBLE);
            float inset = section.getInnerContentMaxTopInset(this);
            if (innerRecyclerView != null && inset >= 0f && !Float.isNaN(inset) && !Float.isInfinite(inset)) {
                int padding = Math.max(0, Math.round(inset));
                float baseTranslation = headerTop + header.getMeasuredHeight() - top - padding;
                section.applyInnerContentGeometry(this, innerRecyclerView, padding, baseTranslation);
            }
            setHeaderPinned(natural <= pin + 0.5f);
            section.onHeaderPinOffsetChanged(Math.max(0f, natural - pin));
        } else {
            setHeaderPinned(false);
        }
        setPinned(top <= stop + 0.5f);
        section.onPinOffsetChanged(Math.max(0f, top - stop));
    }
    private void setPinned(boolean value) {
        if (pinned != value) { pinned = value; if (section != null) section.onPinnedChanged(value); }
    }
    private void setHeaderPinned(boolean value) {
        if (headerPinned != value) { headerPinned = value; if (section != null) section.onHeaderPinnedChanged(value); }
    }

    private int scrollOther(@Nullable RecyclerView view, int dy) {
        if (view == null || dy == 0) return 0;
        if (view == callbackSource) throw new IllegalStateException("Reentrant scrollBy on nested-scroll source");
        RecyclerView previousCapture = captureTarget;
        int previousDy = capturedDy;
        captureTarget = view;
        capturedDy = 0;
        programmaticDepth++;
        int result;
        try {
            view.scrollBy(0, dy);
            result = capturedDy;
        } finally {
            programmaticDepth--;
            captureTarget = previousCapture;
            capturedDy = previousDy;
        }
        return dy > 0 ? Math.max(0, Math.min(dy, result)) : Math.min(0, Math.max(dy, result));
    }
    private boolean knownTarget(View target) {
        resolveInnerRecyclerView();
        return target == outerRecyclerView || target == innerRecyclerView;
    }
    @Override public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes, int type) {
        return programmaticDepth == 0 && (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0 && knownTarget(target);
    }
    @Override public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes, int type) {
        nestedHelper.onNestedScrollAccepted(child, target, axes, type);
    }
    @Override public void onStopNestedScroll(@NonNull View target, int type) { nestedHelper.onStopNestedScroll(target, type); }

    @Override public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
        if (programmaticDepth != 0 || !knownTarget(target) || !isSectionAttached()) return;
        callbackSource = target;
        try {
            consumed[1] += coupling.preScroll(target == outerRecyclerView, dy);
        } finally {
            callbackSource = null;
            updateGeometry();
        }
    }
    @Override public void onNestedScroll(@NonNull View target, int dx, int dy, int ux, int uy, int type, @NonNull int[] consumed) {
        if (programmaticDepth != 0 || !knownTarget(target) || !isSectionAttached()) return;
        callbackSource = target;
        try {
            consumed[1] += coupling.postScroll(target == outerRecyclerView, dy, uy);
        } finally {
            callbackSource = null;
            updateGeometry();
        }
    }
    @Override public boolean onNestedPreFling(@NonNull View target, float vx, float vy) { return false; }
    @Override public boolean onNestedFling(@NonNull View target, float vx, float vy, boolean consumed) { return false; }
    @Override public int getNestedScrollAxes() { return nestedHelper.getNestedScrollAxes(); }
    @Override public boolean onStartNestedScroll(View child, View target, int axes) {
        return onStartNestedScroll(child, target, axes, ViewCompat.TYPE_TOUCH);
    }
    @Override public void onNestedScrollAccepted(View child, View target, int axes) {
        onNestedScrollAccepted(child, target, axes, ViewCompat.TYPE_TOUCH);
    }
    @Override public void onStopNestedScroll(View target) { onStopNestedScroll(target, ViewCompat.TYPE_TOUCH); }
    @Override public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        onNestedPreScroll(target, dx, dy, consumed, ViewCompat.TYPE_TOUCH);
    }
    @Override public void onNestedScroll(View target, int dx, int dy, int ux, int uy, int type) {
        legacyConsumed[0] = legacyConsumed[1] = 0;
        onNestedScroll(target, dx, dy, ux, uy, type, legacyConsumed);
    }
    @Override public void onNestedScroll(View target, int dx, int dy, int ux, int uy) {
        onNestedScroll(target, dx, dy, ux, uy, ViewCompat.TYPE_TOUCH);
    }
    private void stopScrolling() {
        if (outerRecyclerView != null) outerRecyclerView.stopScroll();
        if (innerRecyclerView != null) innerRecyclerView.stopScroll();
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) stopScrolling();
        return super.dispatchTouchEvent(event);
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(preDrawListener);
    }
    @Override protected void onDetachedFromWindow() {
        stopScrolling();
        getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
        super.onDetachedFromWindow();
    }
    private static float getYRelativeTo(View view, View ancestor) {
        float y = 0f;
        View current = view;
        while (current != ancestor) {
            y += current.getY();
            ViewParent parent = current.getParent();
            if (!(parent instanceof View)) return Float.POSITIVE_INFINITY;
            current = (View) parent;
            y -= current.getScrollY();
        }
        return y;
    }
}
