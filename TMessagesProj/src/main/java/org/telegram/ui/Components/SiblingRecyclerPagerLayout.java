/* Telegram for Android. GPL v2 or later. */
package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Three sibling layers: fullscreen pager, common RecyclerView, tabs.
 * Common RV contains a transparent viewport-sized anchor after the accessible common rows.
 * Native RV scrolling stops the anchor at the tabs; real inner scrolling translates
 * the common RV out of view. Short pages therefore cannot invent a collapse range.
 *
 * During a swipe the common RV is drawn once. A visible header stays shared across
 * the full width; a previously hidden header enters with the incoming page.
 * No reparenting, bitmap capture, second adapter, or recursive RenderNode graph.
 * Native RecyclerView remains the only touch/fling clock.
 *
 * <p>All calls must run on the UI thread. Install Geometry, the common list with
 * createCommonLayoutManager(), a RecyclerPagerView with Pages, and detached tabs.
 * The pager adapter must report every page binding through onPageBound().
 *
 * <p>By default the coordinator owns vertical list margins, padding and translationY.
 * setCommonInsetsManagedExternally() leaves common-list margins and padding with the caller.
 * Horizontal list padding is preserved, but not copied between lists. A page may contain
 * nested containers and sibling controls; Pages resolves its list without a child-index lookup.
 * Containers on the list's ancestor path must preserve the fullscreen page viewport and allow
 * vertical overscan. The list requires MATCH_PARENT MarginLayoutParams in its own parent.
 * Only the list receives vertical presentation translation; sibling controls stay in place.
 * See Geometry and setVerticalBleed() before combining this with another inset owner.
 */
public class SiblingRecyclerPagerLayout extends FrameLayout implements NestedScrollingParent3 {
    private static final String DEBUG_TAG = "SiblingScroll";
    private static final long DEBUG_SAMPLE_INTERVAL_MS = 250;

    private boolean debugLoggingEnabled;
    private int debugTraceFrames;
    private int debugDrawSequence;
    private long debugLastScrollLogTime;
    private long debugLastBlockedLogTime;
    private long debugTransitionStartTime;
    private long debugProgressChangeTime;
    private int debugGestureId;
    private int debugTransitionId;

    /** Enables diagnostic Logcat output; disabled mode does not construct log messages. */
    public void setDebugLoggingEnabled(boolean enabled) {
        debugLoggingEnabled = enabled;
        if (enabled) {
            debugLastScrollLogTime = 0;
            debugLastBlockedLogTime = 0;
            debugTransitionStartTime = SystemClock.uptimeMillis();
            debugProgressChangeTime = debugTransitionStartTime;
            debugEvent("DEBUG_ENABLED", null, 0, 0, true);
        }
    }

    /** Emits an immediate snapshot on the UI thread when diagnostic logging is enabled. */
    public void logDebugState() {
        debugEvent("SNAPSHOT", null, 0, 0, true);
    }

    /** Records the next pre-draw snapshots without requesting extra frames or layouts. */
    public void traceNextDraws(String reason, int frames) {
        if (!debugLoggingEnabled) {
            return;
        }
        debugTraceFrames = Math.max(debugTraceFrames, Math.min(60, Math.max(0, frames)));
        debugEvent(reason, null, 0, 0, true);
    }

    private String debugBoundary() {
        int position = commonContent == null ? RecyclerView.NO_POSITION : commonContent.boundaryPosition();
        RecyclerView.LayoutManager manager = commonView == null ? null : commonView.getLayoutManager();
        View anchor = manager == null || position == RecyclerView.NO_POSITION
                ? null : manager.findViewByPosition(position);
        return " row=" + position + " anchor=" + debugView(anchor)
                + " anchorTop=" + (anchor == null ? 0 : anchor.getTop())
                + " anchorHeight=" + (anchor == null ? 0 : anchor.getHeight())
                + " decoratedTop=" + (anchor == null ? 0 : manager.getDecoratedTop(anchor))
                + " adapterPosition=" + (anchor == null ? RecyclerView.NO_POSITION
                        : commonView.getChildAdapterPosition(anchor))
                + " pageOffset=" + (activeView == null || pages == null ? 0f : pages.scrollOffset(activeView))
                + " tabsTop=" + tabsTop + " draw=" + debugDrawSequence;
    }

    private void debugEvent(String event, View target, int requestedDy, int consumedDy, boolean immediate) {
        if (!debugLoggingEnabled) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (!immediate) {
            if (now - debugLastScrollLogTime < DEBUG_SAMPLE_INTERVAL_MS) {
                return;
            }
            debugLastScrollLogTime = now;
        }
        View currentPage = pager == null ? null : pager.getViewPages()[0];
        View otherPage = pager == null ? null : pager.getViewPages()[1];
        Log.d(DEBUG_TAG, "root=" + Integer.toHexString(System.identityHashCode(this))
                + " gesture=" + debugGestureId + " transition=" + debugTransitionId
                + " event=" + event + " target=" + debugView(target)
                + " dy=" + requestedDy + " used=" + consumedDy
                + " transitioning=" + transitioning + " progress=" + transition.getProgress()
                + " ageMs=" + (transitioning ? now - debugTransitionStartTime : 0)
                + " unchangedMs=" + (transitioning ? now - debugProgressChangeTime : 0)
                + " depth=" + programmaticDepth + " updating=" + updating
                + " callback=" + debugView(callbackSource)
                + " common=" + debugView(commonView) + " active=" + debugView(activeView)
                + " current=" + debugView(currentPage) + " other=" + debugView(otherPage)
                + " source=" + debugView(sourcePage) + " incoming=" + debugView(incomingPage)
                + " boundary=" + nativeBoundaryTop() + " pin=" + pinTop + " tail=" + commonTail
                + " alignPending=" + alignBoundaryPending + debugBoundary());
    }

    private static String debugView(View view) {
        if (view == null) {
            return "null";
        }
        String state = view.getClass().getSimpleName() + "@"
                + Integer.toHexString(System.identityHashCode(view))
                + "(x=" + view.getTranslationX() + ",y=" + view.getTranslationY()
                + ",w=" + view.getWidth() + ",visibility=" + view.getVisibility()
                + ",attached=" + view.isAttachedToWindow()
                + ",top=" + view.getTop() + ",height=" + view.getHeight()
                + ",layoutRequested=" + view.isLayoutRequested();
        if (view instanceof RecyclerView) {
            RecyclerView list = (RecyclerView) view;
            state += ",scrollState=" + list.getScrollState()
                    + ",up=" + list.canScrollVertically(-1) + ",down=" + list.canScrollVertically(1)
                    + ",layout=" + list.isComputingLayout()
                    + ",pendingUpdates=" + list.hasPendingAdapterUpdates()
                    + ",animating=" + list.isAnimating()
                    + ",paddingTop=" + list.getPaddingTop()
                    + ",count=" + (list.getAdapter() == null ? 0 : list.getAdapter().getItemCount());
        }
        return state + ")";
    }

    private void debugBlockedScroll(String event, View target, int dy, int type) {
        if (!debugLoggingEnabled) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - debugLastBlockedLogTime < DEBUG_SAMPLE_INTERVAL_MS) {
            return;
        }
        debugLastBlockedLogTime = now;
        debugEvent(event + (type == ViewCompat.TYPE_TOUCH ? "_TOUCH" : "_NON_TOUCH"), target, dy, dy, true);
    }

    // Called only for touch boundaries, never for every MOVE or animation frame.
    void debugPagerEvent(String event) {
        debugEvent(event, pager, 0, 0, true);
    }
    /** Captures a list in root coordinates, without capturing tabs or other overlay layers. */
    public interface ScrollCapture {
        /** Draws the supplied list using root-coordinate bounds and its actual view position. */
        void capture(Canvas canvas, RectF bounds, RecyclerView list);
    }
    /** Supplies current pixel geometry; getters must be cheap, stable and side-effect free. */
    public interface Geometry {
        /** Returns the tabs' pinned top in root pixels; keep it within the visible height. */
        float tabsPinnedTop(SiblingRecyclerPagerLayout parent);
        /** Returns the common content's hide boundary; require finite {@code 0 <= hidden <= pin}. */
        default float commonHiddenTop(SiblingRecyclerPagerLayout parent) {
            return 0f;
        }
        /** Returns common top padding excluding bleed. This does not define the tabs' pin. */
        default int commonTopInset(SiblingRecyclerPagerLayout parent) {
            return 0;
        }
        /** Returns page bottom padding excluding bleed, e.g. navigation inset plus a button. */
        default int pageBottomInset(SiblingRecyclerPagerLayout parent) {
            return 0;
        }
        /** Reports state changes only; no initial notification is guaranteed on registration. */
        default void onTabsPinnedChanged(boolean pinned) {}
        /** Reports root Y on geometry updates, including unchanged values; avoid relayout here. */
        default void onTabsPositionChanged(float top) {}
    }

    /** Locates the common list's boundary in its current adapter data. */
    public interface CommonContent {
        /** Returns the spacer's adapter position, or NO_POSITION when absent; later rows are unreachable. */
        int boundaryPosition();
    }

    /** Maps composite pager pages to their active scrollable content and defines the entry reset. */
    public interface Pages {
        /**
         * Returns the page's non-null scrolling list at any descendant depth; page is the adapter's
         * root View, not necessarily a RecyclerView or the list's immediate parent. Prefer a stored
         * reference: this method is called during scroll/animation updates. Fullscreen wrappers
         * must preserve the viewport origin and size, allow vertical overscan, and let nested scroll
         * reach the coordinator. Sibling buttons/overlays are not part of vertical list motion.
         */
        RecyclerView recyclerView(View page);
        /**
         * Returns nonnegative pixels scrolled from the padded start; override for reverse/custom
         * managers. Positive infinity is valid only beyond the common content's collapse range.
         */
        default float scrollOffset(RecyclerView view) {
            return getStartOffset(view);
        }
        /**
         * Resets every bound incoming page, including cached pages, before display. The default
         * aligns item zero with padding for ordinary vertical LinearLayoutManager subclasses.
         * Override with scrollOffset() for another layout; this may schedule a layout pass.
         */
        default void resetToStart(RecyclerView view) {
            view.stopScroll();
            RecyclerView.LayoutManager manager = view.getLayoutManager();
            if (manager instanceof LinearLayoutManager) {
                ((LinearLayoutManager) manager).scrollToPositionWithOffset(0, 0);
            } else {
                view.scrollToPosition(0);
            }
        }
    }

    private final RectF commonCaptureBounds = new RectF();
    private final CommonLayer commonLayer;
    private final NestedScrollingParentHelper nestedHelper = new NestedScrollingParentHelper(this);
    private final int[] legacyConsumed = new int[2];
    private final RecyclerPagerModel.Transition transition = new RecyclerPagerModel.Transition();
    private RecyclerView commonView;
    private RecyclerView activeView;
    // The listener measures actual consumption of a synchronous scrollOther() call.
    private RecyclerView capturedView;
    private ViewPagerFixed pager;
    private View pagerContainer;
    private View tabs;
    private View sourcePage;
    private View incomingPage;
    private Geometry geometry;
    private CommonContent commonContent;
    private Pages pages;
    private View callbackSource;
    private int programmaticDepth;
    private int capturedDy;
    private int topBleed;
    private int bottomBleed;
    private float pinTop;
    private float hiddenTop;
    private float boundaryTop;
    private float commonTail;
    private float tabsTop;
    private boolean updating;
    private boolean transitioning;
    private boolean tabsPinned;
    private boolean metricsInitialized;
    private boolean alignBoundaryPending;
    private boolean commonInsetsManagedExternally;

    private final RecyclerPagerModel.ScrollHost scrollHost = new RecyclerPagerModel.ScrollHost() {
        @Override
        public float distanceToTabs() {
            float top = nativeBoundaryTop();
            return Float.isInfinite(top) ? top : Math.max(0f, top - pinTop);
        }
        @Override
        public int scrollCommon(int dy) {
            return scrollOther(commonView, dy);
        }
        @Override
        public int scrollPage(int dy) {
            return commonContent == null || commonContent.boundaryPosition() == RecyclerView.NO_POSITION
                    ? 0 : scrollOther(activeView, dy);
        }
    };

    private final RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
            if (view == capturedView) {
                capturedDy += dy;
            }
            if (callbackSource == null && programmaticDepth == 0) {
                updateGeometry();
            }
        }
    };

    private final ViewTreeObserver.OnPreDrawListener preDrawListener = () -> {
        if (debugLoggingEnabled && debugTraceFrames > 0) {
            debugEvent("PRE_DRAW_BEFORE", null, 0, 0, true);
        }
        onPagerChanged();
        // Geometry/configuration changes are reconciled outside nested/layout callbacks.
        if (alignBoundaryPending && commonView != null && !commonView.isComputingLayout()) {
            float top = nativeBoundaryTop();
            if (!Float.isInfinite(top)) {
                alignBoundaryPending = false;
                scrollOther(commonView, Math.round(top - pinTop));
                updateGeometry();
            }
        }
        if (debugLoggingEnabled && debugTraceFrames > 0) {
            debugEvent("PRE_DRAW_AFTER", null, 0, 0, true);
            debugTraceFrames--;
            debugDrawSequence++;
        }
        return true;
    };

    /** Creates the sibling-layer root; lists, pager, tabs and geometry are installed separately. */
    public SiblingRecyclerPagerLayout(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        commonLayer = new CommonLayer(context);
        addView(commonLayer, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    /** Sets the live geometry provider and requests layout; call invalidateGeometry() on changes. */
    public void setGeometry(@NonNull Geometry value) {
        geometry = value;
        requestLayout();
        updateGeometry();
    }

    /**
     * Leaves common-list margins and padding with the caller when enabled. Page bleed remains
     * configured by setVerticalBleed(); call invalidateGeometry() after applying external insets.
     */
    public void setCommonInsetsManagedExternally(boolean value) {
        if (commonInsetsManagedExternally == value) {
            return;
        }
        commonInsetsManagedExternally = value;
        updateGeometry();
        requestLayout();
    }

    /**
     * Sets shared top/bottom overscan magnitudes in pixels, clamped to zero. These become
     * negative vertical margins and compensating padding on page lists and, unless externally
     * managed, the common list. In external mode pass the magnitudes of its negative margins.
     * Configure before binding pages; later changes request layout and preserve a linear anchor.
     */
    public void setVerticalBleed(int top, int bottom) {
        topBleed = Math.max(0, top);
        bottomBleed = Math.max(0, bottom);
        requestLayout();
        updateGeometry();
    }

    /** Returns the configured nonnegative top overscan in pixels. */
    public int getTopBleed() {
        return topBleed;
    }
    /** Returns the configured nonnegative bottom overscan in pixels. */
    public int getBottomBleed() {
        return bottomBleed;
    }
    /** Returns the last computed pin state; false while the boundary is not attached. */
    public boolean areTabsPinned() {
        return tabsPinned;
    }
    /** Returns the last computed tabs top in root pixels; meaningful only while tabs are visible. */
    public float getTabsTop() {
        return tabsTop;
    }

    /**
     * Reapplies changed insets, pin coordinates or tab height and requests layout. Visible lists
     * update now; cached pages update on binding. Call outside layout/nested-scroll callbacks.
     */
    public void invalidateGeometry() {
        updateGeometry();
        requestLayout();
        invalidate();
    }

    /**
     * Restores the common boundary at the pin after adapter rows change. Call after notifications
     * when the boundary was pinned before the update; the current page's own offset is preserved.
     */
    public void alignCommonBoundaryToPin() {
        debugEvent("ALIGN_BOUNDARY", commonView, 0, 0, true);
        if (commonView == null || commonContent == null || geometry == null) {
            return;
        }
        int position = commonContent.boundaryPosition();
        RecyclerView.LayoutManager manager = commonView.getLayoutManager();
        if (position == RecyclerView.NO_POSITION || !(manager instanceof LinearLayoutManager)) {
            return;
        }
        updateMetrics();
        ((LinearLayoutManager) manager).scrollToPositionWithOffset(position,
                Math.round(pinTop) - commonView.getTop() - commonView.getPaddingTop());
        alignBoundaryPending = true;
        requestLayout();
    }

    /**
     * Installs a detached common list with createCommonLayoutManager() and a boundary spacer.
     * Replaces its LayoutParams and enables nested scrolling. The caller owns clipToPadding.
     */
    public void setCommonRecyclerView(@NonNull RecyclerView view, @NonNull CommonContent content) {
        if (commonView != null) {
            commonView.stopScroll();
            commonView.removeOnScrollListener(scrollListener);
            commonLayer.removeView(commonView);
        }
        commonView = view;
        commonContent = content;
        commonLayer.addView(view, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        view.addOnScrollListener(scrollListener);
        view.setNestedScrollingEnabled(true);
        updateGeometry();
    }

    /**
     * Installs a detached fullscreen pager below the common layer. Prefer RecyclerPagerView;
     * a custom bridge must provide equivalent binding, gesture, drawing and transition hooks.
     */
    public void setPager(@NonNull ViewPagerFixed value, @NonNull Pages pageProvider) {
        setPager(value, value, pageProvider);
    }

    /**
     * Installs a detached fullscreen container holding the pager and optional controls. The pager
     * must fill that container with the same viewport origin; its existing parent is preserved.
     */
    public void setPager(@NonNull View container, @NonNull ViewPagerFixed value, @NonNull Pages pageProvider) {
        if (pagerContainer != null) {
            removeView(pagerContainer);
        }
        pagerContainer = container;
        pager = value;
        pages = pageProvider;
        addView(container, 0, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        updateGeometry();
    }

    /**
     * Captures visible page lists, then the common list with its canvas-only swipe translation.
     * The callback captures each list relative to the caller's root, for example with
     * Blur3Utils.captureRelativeParent. Canvas and bounds are adjusted only for the common
     * layer's extra horizontal movement. Tabs and overlays are excluded. Call on the UI thread.
     */
    public void captureScrollingContent(Canvas canvas, RectF bounds, ScrollCapture capture) {
        if (pager != null && pages != null && pagerContainer.getVisibility() == VISIBLE
                && pager.getVisibility() == VISIBLE) {
            View[] visible = pager.getViewPages();
            for (int i = 0; i < visible.length; i++) {
                View page = visible[i];
                if (page == null || page.getParent() != pager || page.getVisibility() != VISIBLE) {
                    continue;
                }
                capture.capture(canvas, bounds, pages.recyclerView(page));
            }
        }
        if (commonView != null && commonView.getVisibility() == VISIBLE) {
            float x = transitioning ? transition.commonTranslationX(incomingPage.getX()) : 0f;
            if (x == 0f) {
                capture.capture(canvas, bounds, commonView);
            } else {
                // captureRelativeParent sees View properties, not CommonLayer.drawChild's translation.
                commonCaptureBounds.set(bounds);
                commonCaptureBounds.offset(-x, 0f);
                int saveCount = canvas.save();
                try {
                    canvas.translate(x, 0f);
                    capture.capture(canvas, commonCaptureBounds, commonView);
                } finally {
                    canvas.restoreToCount(saveCount);
                }
            }
        }
    }

    /**
     * Installs detached tabs above both lists, with height in pixels or WRAP_CONTENT.
     * Replaces their LayoutParams and owns translationY; invalidate geometry after resizing.
     */
    public void setTabs(@NonNull View value, int height) {
        if (tabs != null) {
            removeView(tabs);
        }
        tabs = value;
        addView(value, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, height, Gravity.TOP));
        updateGeometry();
    }

    /** Creates the transparent adapter boundary, measured to the root height without tab subtraction. */
    public View createBoundarySpacer(Context context) {
        return new View(context) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                setMeasuredDimension(MeasureSpec.getSize(widthSpec), SiblingRecyclerPagerLayout.this.getMeasuredHeight());
            }
        };
    }

    /**
     * Creates the required ordinary vertical common-list manager. It clamps native forward
     * scrolling at the boundary before consumption is reported; no custom-manager hook exists.
     */
    public LinearLayoutManager createCommonLayoutManager(Context context) {
        return new LinearLayoutManager(context, RecyclerView.VERTICAL, false) {
            @Override
            public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
                if (dy <= 0 || commonContent == null) {
                    return super.scrollVerticallyBy(dy, recycler, state);
                }
                int totalConsumed = 0;
                while (totalConsumed < dy) {
                    // An unattached anchor cannot cross the pin in one discovery step.
                    int stepLimit = Math.max(1, Math.round((SiblingRecyclerPagerLayout.this.getHeight() - pinTop) * 0.5f));
                    int requestedStep = Math.min(dy - totalConsumed, stepLimit);
                    float boundaryY = nativeBoundaryTop();
                    if (!Float.isInfinite(boundaryY)) {
                        requestedStep = Math.min(requestedStep, Math.max(0, Math.round(boundaryY - pinTop)));
                    }
                    if (requestedStep == 0) {
                        break;
                    }
                    int consumedStep = super.scrollVerticallyBy(requestedStep, recycler, state);
                    totalConsumed += consumedStep;
                    if (consumedStep < requestedStep) {
                        break;
                    }
                }
                return totalConsumed;
            }
        };
    }

    /**
     * Applies list insets and resets to start; pass the adapter's page root after every binding,
     * including reuse. Pages resolves the nested list; no children are searched or reparented.
     */
    public void onPageBound(@NonNull View page) {
        debugEvent("PAGE_BOUND", page, 0, 0, true);
        if (pages == null) {
            return;
        }
        if (transitioning) {
            finishTransition("page_bound");
        }
        RecyclerView view = pages.recyclerView(page);
        applyPageInsets(view);
        pages.resetToStart(view);
        onPagerChanged();
    }

    /** Reports whether page content is reachable; the caller must also reject hits on common rows. */
    public boolean canStartPageSwipe() {
        return pager != null && pager.getCurrentView() != null
                && !Float.isInfinite(nativeBoundaryTop()) && tabsTop < getHeight();
    }

    /** Hit-tests the drawn common layer in root pixels, including its entrance during a swipe. */
    public boolean isCommonContentAt(float x, float y) {
        float left = transitioning ? transition.commonTranslationX(incomingPage.getX()) : 0f;
        return y >= 0f && y < commonTail && x >= Math.max(0f, left)
                && x < Math.min(getWidth(), left + getWidth());
    }

    /**
     * Reconciles page identity and presentation from actual pager translations. Called by the
     * bridge and pre-draw; stops vertical scrolling on entry and may reapply changed geometry.
     */
    public void onPagerChanged() {
        if (updating || pager == null || pages == null || geometry == null) {
            return;
        }
        View[] visiblePages = pager.getViewPages();
        View currentPage = visiblePages[0];
        View otherPage = visiblePages[1];
        boolean dragAborted = pager instanceof RecyclerPagerView
                && ((RecyclerPagerView) pager).isCancelledPagePair(currentPage, otherPage);
        if (transitioning && dragAborted) {
            finishTransition("drag_aborted");
        }
        if (transitioning && (otherPage == null
                || (currentPage != sourcePage && currentPage != incomingPage)
                || (otherPage != sourcePage && otherPage != incomingPage))) {
            finishTransition("page_slots_changed");
        }
        resolveActive();
        if (!transitioning && !dragAborted && currentPage != null && otherPage != null) {
            float nativeTop = nativeBoundaryTop();
            if (!Float.isInfinite(nativeTop)) {
                updateMetrics();
                stopScrolling();
                sourcePage = currentPage;
                incomingPage = otherPage;
                float top = normalizedBoundary(nativeTop);
                transition.begin(top, pages.scrollOffset(pages.recyclerView(currentPage)), pinTop, hiddenTop);
                transitioning = true;
                if (debugLoggingEnabled) {
                    debugTransitionId++;
                    debugTransitionStartTime = SystemClock.uptimeMillis();
                    debugProgressChangeTime = debugTransitionStartTime;
                    debugLastBlockedLogTime = 0;
                    debugEvent("TRANSITION_BEGIN", currentPage, 0, 0, true);
                }
                commonLayer.invalidate();
            }
        }
        if (transitioning) {
            float width = Math.max(1, sourcePage.getWidth());
            float previousProgress = transition.getProgress();
            transition.setProgress(Math.abs(sourcePage.getTranslationX()) / width);
            if (previousProgress != transition.getProgress()) {
                commonLayer.invalidate();
                if (debugLoggingEnabled) {
                    debugProgressChangeTime = SystemClock.uptimeMillis();
                }
            }
        }
        updateGeometry();
    }

    private void finishTransition(String reason) {
        // Keep the native common position on both commit and cancel, including an expanded
        // header. The current page's actual offset determines the resting presentation.
        if (debugLoggingEnabled) {
            debugEvent("TRANSITION_END_" + reason, null, 0, 0, true);
        }
        transitioning = false;
        sourcePage = incomingPage = null;
        commonLayer.invalidate();
    }

    private void updateMetrics() {
        if (geometry == null) {
            return;
        }
        float nextPin = geometry.tabsPinnedTop(this);
        float nextHidden = geometry.commonHiddenTop(this);
        if (Float.isNaN(nextPin) || Float.isInfinite(nextPin) || Float.isNaN(nextHidden)
                || Float.isInfinite(nextHidden) || nextPin < nextHidden || nextHidden < 0f) {
            throw new IllegalArgumentException("Require finite 0 <= commonHiddenTop <= tabsPinnedTop");
        }
        if (metricsInitialized && (nextPin != pinTop || nextHidden != hiddenTop)) {
            float top = nativeBoundaryTop();
            if (!Float.isInfinite(top)) {
                boolean atPin = top <= pinTop + 0.5f || top < nextPin;
                if (atPin && nextPin != pinTop) {
                    alignBoundaryPending = true;
                }
                if (transitioning) {
                    float progress = transition.getProgress();
                    transition.begin(atPin ? nextPin : top, pages.scrollOffset(pages.recyclerView(sourcePage)),
                            nextPin, nextHidden);
                    transition.setProgress(progress);
                    commonLayer.invalidate();
                }
            }
        }
        pinTop = nextPin;
        hiddenTop = nextHidden;
        metricsInitialized = true;
    }

    private void resolveActive() {
        RecyclerView next = pager == null || pages == null || pager.getCurrentView() == null
                ? null : pages.recyclerView(pager.getCurrentView());
        if (next == activeView) {
            return;
        }
        debugEvent("ACTIVE_CHANGE", next, 0, 0, true);
        if (activeView != null) {
            activeView.stopScroll();
            activeView.removeOnScrollListener(scrollListener);
        }
        activeView = next;
        if (next != null) {
            next.addOnScrollListener(scrollListener);
        }
    }

    /** Root Y without the common RecyclerView's presentation translation. */
    private float nativeBoundaryTop() {
        if (commonView == null || commonContent == null || commonView.getLayoutManager() == null) {
            return Float.POSITIVE_INFINITY;
        }
        RecyclerView.LayoutManager manager = commonView.getLayoutManager();
        int position = commonContent.boundaryPosition();
        if (position == RecyclerView.NO_POSITION) {
            return Float.POSITIVE_INFINITY;
        }
        View anchor = manager.findViewByPosition(position);
        if (anchor == null) {
            return Float.POSITIVE_INFINITY;
        }
        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) anchor.getLayoutParams();
        return commonView.getTop() + manager.getDecoratedTop(anchor) - params.topMargin;
    }

    private float normalizedBoundary(float top) {
        return top <= pinTop + 0.5f ? pinTop : top;
    }

    private void updateGeometry() {
        if (updating || geometry == null) {
            return;
        }
        updating = true;
        try {
            float previousTail = commonTail;
            updateMetrics();
            if (commonView != null && !commonInsetsManagedExternally) {
                setBleed(commonView);
                setPaddingIfChanged(commonView, topBleed + geometry.commonTopInset(this), bottomBleed);
            }
            if (pager == null || pages == null || tabs == null) {
                return;
            }
            // Prepare hidden pages too: their first layout must already use the final insets.
            // Otherwise new padding is mistaken for scroll before children are laid out again.
            View[] visiblePages = pager.getViewPages();
            for (int i = 0; i < visiblePages.length; i++) {
                View page = visiblePages[i];
                if (page != null && page.getParent() == pager) {
                    applyPageInsets(pages.recyclerView(page));
                }
            }
            resolveActive();
            float nativeTop = nativeBoundaryTop();
            if (Float.isInfinite(nativeTop)) {
                commonTail = Float.POSITIVE_INFINITY;
                pagerContainer.setVisibility(INVISIBLE);
                tabs.setVisibility(INVISIBLE);
                if (commonView != null) {
                    commonView.setTranslationY(0f);
                }
                setTabsPinned(false);
                if (previousTail != commonTail) {
                    commonLayer.invalidate();
                }
                return;
            }
            boundaryTop = normalizedBoundary(nativeTop);
            pagerContainer.setVisibility(VISIBLE);
            tabs.setVisibility(VISIBLE);
            if (transitioning) {
                commonTail = transition.commonTail();
                tabsTop = transition.tabsTop();
                setPageGeometry(sourcePage, transition.outgoingPageTranslation());
                setPageGeometry(incomingPage, transition.incomingPageTranslation());
            } else {
                float offset = activeView == null ? 0f : pages.scrollOffset(activeView);
                commonTail = boundaryTop - RecyclerPagerModel.collapse(offset, pinTop, hiddenTop);
                tabsTop = Math.max(pinTop, commonTail);
                setPageGeometry(pager.getCurrentView(), RecyclerPagerModel.pageTranslation(boundaryTop, pinTop));
            }
            commonView.setTranslationY(commonTail - nativeTop);
            tabs.setTranslationY(tabsTop - tabs.getTop());
            setTabsPinned(tabsTop <= pinTop + 0.5f);
            geometry.onTabsPositionChanged(tabsTop);
            if (previousTail != commonTail) {
                commonLayer.invalidate();
            }
        } finally {
            updating = false;
        }
    }

    private void setTabsPinned(boolean value) {
        if (tabsPinned == value) {
            return;
        }
        tabsPinned = value;
        debugEvent(value ? "TABS_PINNED" : "TABS_UNPINNED", commonView, 0, 0, true);
        geometry.onTabsPinnedChanged(value);
    }

    private void setPageGeometry(View page, float translation) {
        if (page == null) {
            return;
        }
        RecyclerView view = pages.recyclerView(page);
        // Insets were prepared before reading the page's scroll offset.
        // RV padding is integer, root geometry is float.
        view.setTranslationY(translation + pinTop - Math.round(pinTop));
    }

    private void applyPageInsets(RecyclerView view) {
        if (geometry == null || view == null) {
            return;
        }
        updateMetrics();
        int tabsHeight = tabs == null ? 0 : (tabs.getHeight() > 0 ? tabs.getHeight() : tabs.getLayoutParams().height);
        setBleed(view);
        setPaddingIfChanged(view, topBleed + Math.round(pinTop) + Math.max(0, tabsHeight),
                bottomBleed + geometry.pageBottomInset(this));
        if (!view.isNestedScrollingEnabled()) {
            view.setNestedScrollingEnabled(true);
        }
    }

    private void setBleed(RecyclerView view) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (!(raw instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) raw;
        if (params.topMargin != -topBleed || params.bottomMargin != -bottomBleed) {
            params.topMargin = -topBleed;
            params.bottomMargin = -bottomBleed;
            view.setLayoutParams(params);
        }
    }

    private static void setPaddingIfChanged(RecyclerView view, int top, int bottom) {
        if (view.getPaddingTop() == top && view.getPaddingBottom() == bottom) {
            return;
        }
        RecyclerView.LayoutManager manager = view.getLayoutManager();
        View anchor = manager == null || manager.getChildCount() == 0 ? null : manager.getChildAt(0);
        int position = anchor == null ? RecyclerView.NO_POSITION : manager.getPosition(anchor);
        int offset = anchor == null ? 0 : manager.getDecoratedTop(anchor) - view.getPaddingTop()
                - ((RecyclerView.LayoutParams) anchor.getLayoutParams()).topMargin;
        view.setPadding(view.getPaddingLeft(), top, view.getPaddingRight(), bottom);
        if (position != RecyclerView.NO_POSITION && manager instanceof LinearLayoutManager) {
            ((LinearLayoutManager) manager).scrollToPositionWithOffset(position, offset);
        }
    }

    /**
     * Returns pixels from the padded start for ordinary vertical linear/grid layouts, or positive
     * infinity when item zero is detached. This is a collapse metric, not an absolute scroll offset.
     */
    public static float getStartOffset(RecyclerView view) {
        RecyclerView.LayoutManager manager = view.getLayoutManager();
        if (manager == null || !view.canScrollVertically(-1)) {
            return 0f;
        }
        View first = manager.findViewByPosition(0);
        if (first == null) {
            return Float.POSITIVE_INFINITY;
        }
        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) first.getLayoutParams();
        return Math.max(0f, view.getPaddingTop() - manager.getDecoratedTop(first) + params.topMargin);
    }

    private final class CommonLayer extends FrameLayout {
        CommonLayer(Context context) {
            super(context);
            setClipChildren(false);
            setClipToPadding(false);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    && (transitioning || event.getY() >= commonTail)) return false;
            return super.dispatchTouchEvent(event);
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            float x = transitioning ? transition.commonTranslationX(incomingPage.getX()) : 0f;
            int save = canvas.save();
            canvas.clipRect(Math.max(0f, x), -topBleed, Math.min(getWidth(), x + getWidth()),
                    Math.min(getHeight() + bottomBleed, commonTail));
            canvas.translate(x, 0f);
            boolean result = super.drawChild(canvas, child, drawingTime);
            canvas.restoreToCount(save);
            return result;
        }
    }

    private int scrollOther(RecyclerView view, int requestedDy) {
        if (view == null || requestedDy == 0) {
            if (requestedDy != 0) {
                debugEvent("TRANSFER_NO_TARGET", view, requestedDy, 0, false);
            }
            return 0;
        }
        if (view == callbackSource) {
            debugEvent("TRANSFER_REENTRY", view, requestedDy, 0, true);
            throw new IllegalStateException("Reentrant scrollBy on nested source");
        }
        RecyclerView previousView = capturedView;
        int previousDy = capturedDy;
        capturedView = view;
        capturedDy = 0;
        programmaticDepth++;
        int actualConsumed;
        try {
            view.scrollBy(0, requestedDy);
            actualConsumed = capturedDy;
        } finally {
            programmaticDepth--;
            capturedView = previousView;
            capturedDy = previousDy;
        }
        int boundedConsumption = requestedDy > 0 ? Math.max(0, Math.min(requestedDy, actualConsumed)) : Math.min(0, Math.max(requestedDy, actualConsumed));
        debugEvent(actualConsumed == 0 ? "TRANSFER_ZERO" : "TRANSFER", view, requestedDy, boundedConsumption, false);
        return boundedConsumption;
    }

    private boolean knownTarget(View target) {
        return target == commonView || target == activeView;
    }
    /** Accepts vertical nested scrolling only from the common list or current page. */
    @Override
    public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes, int type) {
        boolean vertical = (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
        boolean accepted = programmaticDepth == 0 && vertical && knownTarget(target);
        if (debugLoggingEnabled) {
            debugEvent((accepted ? "NESTED_START" : "NESTED_REJECT")
                    + "_axes=" + axes + "_type=" + type, target, 0, 0, true);
        }
        return accepted;
    }
    /** Records the accepted axes and touch/fling type for the nested-scrolling contract. */
    @Override
    public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes, int type) {
        nestedHelper.onNestedScrollAccepted(child, target, axes, type);
    }
    /** Clears the helper's accepted axes for the completed scroll type. */
    @Override
    public void onStopNestedScroll(@NonNull View target, int type) {
        debugEvent(type == ViewCompat.TYPE_TOUCH ? "NESTED_STOP_TOUCH" : "NESTED_STOP_NON_TOUCH", target, 0, 0, true);
        nestedHelper.onStopNestedScroll(target, type);
    }
    /** Routes pre-consumption to the other list; freezes vertical input during a page transition. */
    @Override
    public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
        if (programmaticDepth != 0 || !knownTarget(target)) {
            debugEvent("PRE_IGNORED", target, dy, 0, false);
            return;
        }
        if (transitioning) {
            debugBlockedScroll("PRE_BLOCKED", target, dy, type);
            consumed[1] += dy;
            return;
        }
        callbackSource = target;
        try {
            int parentConsumed = RecyclerPagerModel.preScroll(scrollHost, target == commonView, dy);
            consumed[1] += parentConsumed;
            debugEvent("PRE", target, dy, parentConsumed, false);
        } finally {
            callbackSource = null;
            updateGeometry();
        }
    }
    /** Routes the source's unconsumed vertical distance without scrolling the source recursively. */
    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type, @NonNull int[] consumed) {
        if (programmaticDepth != 0 || !knownTarget(target)) {
            debugEvent("POST_IGNORED", target, dyUnconsumed, 0, false);
            return;
        }
        if (transitioning) {
            debugBlockedScroll("POST_BLOCKED", target, dyUnconsumed, type);
            consumed[1] += dyUnconsumed;
            return;
        }
        callbackSource = target;
        try {
            int parentConsumed = RecyclerPagerModel.postScroll(scrollHost, target == commonView, dyConsumed, dyUnconsumed);
            consumed[1] += parentConsumed;
            debugEvent("POST", target, dyUnconsumed, parentConsumed, false);
        } finally {
            callbackSource = null;
            updateGeometry();
        }
    }
    /** Leaves fling ownership with the source RecyclerView. */
    @Override
    public boolean onNestedPreFling(@NonNull View target, float vx, float vy) {
        return false;
    }
    /** Does not start a second fling; native non-touch nested scrolling drives both lists. */
    @Override
    public boolean onNestedFling(@NonNull View target, float vx, float vy, boolean consumed) {
        return false;
    }
    /** Returns the axes currently accepted by the nested-scrolling helper. */
    @Override
    public int getNestedScrollAxes() {
        return nestedHelper.getNestedScrollAxes();
    }
    /** Legacy touch overload of the typed nested-scroll acceptance check. */
    @Override
    public boolean onStartNestedScroll(View child, View target, int axes) {
        return onStartNestedScroll(child, target, axes, ViewCompat.TYPE_TOUCH);
    }
    /** Legacy touch overload of nested-scroll registration. */
    @Override
    public void onNestedScrollAccepted(View child, View target, int axes) {
        onNestedScrollAccepted(child, target, axes, ViewCompat.TYPE_TOUCH);
    }
    /** Legacy touch overload of nested-scroll completion. */
    @Override
    public void onStopNestedScroll(View target) {
        onStopNestedScroll(target, ViewCompat.TYPE_TOUCH);
    }
    /** Legacy touch overload of vertical pre-consumption. */
    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        onNestedPreScroll(target, dx, dy, consumed, ViewCompat.TYPE_TOUCH);
    }
    /** Parent2 overload; handles the remainder without reporting additional parent consumption. */
    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type) {
        legacyConsumed[0] = legacyConsumed[1] = 0;
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, legacyConsumed);
    }
    /** Legacy touch overload of remainder handling. */
    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, ViewCompat.TYPE_TOUCH);
    }

    private void stopScrolling() {
        debugEvent("STOP_SCROLLING", null, 0, 0, true);
        if (commonView != null) {
            commonView.stopScroll();
        }
        if (pager != null && pages != null) {
            View[] visible = pager.getViewPages();
            for (int i = 0; i < visible.length; i++) {
                if (visible[i] != null) {
                    pages.recyclerView(visible[i]).stopScroll();
                }
            }
        }
    }

    /** Stops vertical motion on a new touch; common-row gestures are never forwarded to the pager. */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (debugLoggingEnabled) {
                debugGestureId++;
                debugLastBlockedLogTime = 0;
                debugEvent("TOUCH_DOWN", null, 0, 0, true);
            }
            stopScrolling();
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            debugEvent(event.getActionMasked() == MotionEvent.ACTION_UP ? "TOUCH_UP" : "TOUCH_CANCEL", null, 0, 0, true);
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(preDrawListener);
    }
    @Override
    protected void onDetachedFromWindow() {
        stopScrolling();
        getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
        super.onDetachedFromWindow();
    }
}
