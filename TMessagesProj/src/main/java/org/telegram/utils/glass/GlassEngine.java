package org.telegram.utils.glass;

import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Trace;
import android.view.View;
import android.view.ViewParent;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.LiteMode;
import org.telegram.messenger.postdrawcompat.OnPostDrawListener;
import org.telegram.messenger.postdrawcompat.ViewOnPostDrawListener;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SectionsScrollView;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.utils.glass.source.GlassSourceColor;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.utils.glass.positions.GlassPositionsArray;
import org.telegram.utils.glass.positions.GlassPositionsMerger;

import java.util.ArrayList;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.reference.ReferenceList;

public class GlassEngine {
    public static final int FLAG_INVALIDATED_SCROLL = 1;
    public static final int FLAG_INVALIDATED_SCROLL_EDGES = 1 << 1;
    public static final int FLAG_INVALIDATED_POSITIONS = 1 << 2;
    public static final int FLAG_INVALIDATED_OTHER = 1 << 3;
    public static final int FLAG_INVALIDATED_THEMED = 1 << 4;
    public static final int FLAG_INVALIDATED_LITE_MODE = 1 << 5;



    private GlassInvalidationListener glassInvalidationListener;

    public interface GlassInvalidationListener {
        void onGlassInvalidated(int flags);
    }

    public void setGlassInvalidationListener(GlassInvalidationListener glassInvalidationListener) {
        this.glassInvalidationListener = glassInvalidationListener;
    }



    private final OnPostDrawListener onPostDrawListener = new OnPostDrawListener() {
        @Override
        public void onDraw() {
            GlassEngine.this.onDraw();
        }

        @Override
        public void onPostDraw() {
            GlassEngine.this.onPostDraw();
        }
    };

    private final ArrayList<ViewPositionState> viewPositionStates = new ArrayList<>();

    private GlassPositionsMerger positionsMerger = GlassPositionsMerger.DEFAULT;

    public void setPositionsMerger(GlassPositionsMerger positionsMerger) {
        this.positionsMerger = positionsMerger;
    }

    public void registerDrawable(@NonNull View view, @NonNull GlassDrawable drawable) {
        viewPositionStates.add(new ViewPositionState(view, drawable));
    }

    private final RectF externalCapturePosition = new RectF();
    private boolean externalCapturePositionInvalidated;

    /** The area of a glass that is not in the root's views, e.g. the tabs over a page: in the root's coordinates, null for none. */
    public void setExternalCapturePosition(@Nullable RectF position) {
        if (position == null) {
            if (!externalCapturePosition.isEmpty()) {
                externalCapturePosition.setEmpty();
                externalCapturePositionInvalidated = true;
            }
        } else if (!externalCapturePosition.equals(position)) {
            externalCapturePosition.set(position);
            externalCapturePositionInvalidated = true;
        }
    }



    private long scrollGenerationId;
    private long scrollEdgesGenerationId;
    private long otherGenerationId;
    private boolean themedInvalidated;

    private int scrollEdgesCount;
    private int scrollOffsetX;
    private int scrollOffsetY;


    private View rootView;

    public void setRoot(View root) {
        if (rootView != root) {
            if (rootView != null) {
                ViewOnPostDrawListener.removeListener(rootView, onPostDrawListener);
            }
            if (root != null) {
                ViewOnPostDrawListener.addListener(root, onPostDrawListener);
            }
            rootView = root;
        }
    }

    public void addScrolledView(ViewPager viewPager) {
        if (viewPager == null) {
            return;
        }

        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            private int lastScrollX = viewPager.getScrollX();

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                final int scrollX = viewPager.getScrollX();
                GlassEngine.this.onScrolled(scrollX - lastScrollX, 0);
                lastScrollX = scrollX;
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                scrollGenerationId++;
            }
        });
    }

    public void addScrolledView(ViewPagerFixed viewPagerFixed) {
        if (viewPagerFixed == null) {
            return;
        }
        viewPagerFixed.addOnScrollListener(dx ->
                GlassEngine.this.onScrolled(dx, 0));
    }

    /** Scroll of content the engine does not track itself, e.g. pages moved by translation. */
    public void onScrolled(int dx, int dy) {
        if (dx == 0 && dy == 0) {
            return;
        }
        scrollGenerationId++;
        scrollOffsetX += dx;
        scrollOffsetY += dy;
    }

    private final ReferenceList<RecyclerListView> recyclerListViews = new ReferenceList<>();

    public void addScrolledView(RecyclerListView recyclerListView) {
        if (recyclerListView == null) {
            return;
        }

        recyclerListViews.add(recyclerListView);

        recyclerListView.addEdgeEffectListener((direction, isVisible) -> {
            scrollEdgesGenerationId++;
            scrollEdgesCount += (isVisible ? 1 : -1);
        });
        // rows that appear without a scroll, e.g. a loaded page, change what is under the glass too
        recyclerListView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View view) {
                otherGenerationId++;
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
                otherGenerationId++;
            }
        });
        recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                scrollGenerationId++;
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                GlassEngine.this.onScrolled(dx, dy);
            }
        });
    }

    public void addScrolledView(SectionsScrollView scrollView) {
        if (scrollView == null) {
            return;
        }

        scrollView.onScroll(new Runnable() {
            int oldX, oldY;

            @Override
            public void run() {
                final int sX = scrollView.getScrollX();
                final int sY = scrollView.getScrollY();

                scrollOffsetX += (sX - oldX);
                scrollOffsetY += (sY - oldY);

                oldX = sX;
                oldY = sY;

                scrollGenerationId++;
            }
        });
        scrollView.addOnOverscrollChangedListener(new SectionsScrollView.OnOverscrollChangedListener() {
            boolean lastActive;

            @Override
            public void onOverscrollChanged(boolean active) {
                if (lastActive != active) {
                    lastActive = active;
                    scrollEdgesGenerationId++;
                    scrollEdgesCount += (active ? 1 : -1);
                }
            }
        });
    }

    public int getScrollOffsetX() {
        return scrollOffsetX;
    }

    public int getScrollOffsetY() {
        return scrollOffsetY;
    }

    public boolean hasScrollEdges() {
        return scrollEdgesCount > 0;
    }


    private final ArrayList<DownscaleScrollableNoiseSuppressor> downscaleScrollableNoiseSuppressors = new ArrayList<>();

    public void addNoiseSuppressor(DownscaleScrollableNoiseSuppressor downscaleScrollableNoiseSuppressor) {
        downscaleScrollableNoiseSuppressors.add(downscaleScrollableNoiseSuppressor);
    }

    public void invalidate() {
        otherGenerationId++;
    }

    public void invalidateScroll() {
        scrollGenerationId++;
    }

    private long lastScrollGenerationId;

    private long lastScrollEdgesGenerationId;

    private long lastOtherGenerationId;
    private long lastThemeGenerationId;
    private long lastGlassSettingsGenerationId;


    private int onDrawUpdateFlags;

    private void onDraw() {
        int flags = 0;
        if (lastGlassSettingsGenerationId != glassSettingsGenerationId) {
            lastGlassSettingsGenerationId = glassSettingsGenerationId;
            flags |= FLAG_INVALIDATED_LITE_MODE;
            // a glass may go over to a source without a capture and back
            externalCapturePositionInvalidated = true;
        }

        final long themeGenerationId = Theme.getThemeGenerationId();
        if (lastThemeGenerationId != themeGenerationId) {
            lastThemeGenerationId = themeGenerationId;
            flags |= FLAG_INVALIDATED_THEMED;
        }

        for (RecyclerListView recyclerListView : recyclerListViews) {
            if (recyclerListView.isAnimating()) {
                otherGenerationId++;
            }
        }

        if (flags == 0) {
            return;
        }

        if (BitwiseUtils.hasFlag(flags, FLAG_INVALIDATED_THEMED)) {
            for (int a = 0, N = themedSourceColorStates.size(); a < N; a++) {
                final ThemedSourceColorState state = themedSourceColorStates.get(a);
                final int color = state.colorDelegate.getThemedColor();
                state.sourceColor.setColor(color);
            }
        }

        for (int a = 0, N = viewPositionStates.size(); a < N; a++) {
            final ViewPositionState state = viewPositionStates.get(a);
            if (BitwiseUtils.hasFlag(flags, FLAG_INVALIDATED_LITE_MODE)) {
                state.drawable.setGlassSettings(glassSettings);
            }
            if (BitwiseUtils.hasFlag(flags, FLAG_INVALIDATED_THEMED)) {
                state.drawable.updateColors();
            }
            state.drawable.invalidateSelf();
            state.view.invalidate();
            state.invalidated = true;
        }

        onDrawUpdateFlags |= flags;
    }

    private void onPostDraw() {
        Trace.beginSection("G.CheckPositions");
        hasInvalidatedDrawables = false;
        final boolean hasInvalidatedPositionsOrVisibility = checkDrawablePositionsIfNeeded();
        Trace.endSection();

        int flags = onDrawUpdateFlags;
        onDrawUpdateFlags = 0;

        if (hasInvalidatedPositionsOrVisibility) {
            flags |= FLAG_INVALIDATED_POSITIONS;
        }



        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (int a = 0, N = downscaleScrollableNoiseSuppressors.size(); a < N; a++) {
                final DownscaleScrollableNoiseSuppressor s = downscaleScrollableNoiseSuppressors.get(a);
                if (s.hasLostDisplayLists) {
                    s.hasLostDisplayLists = false;
                    otherGenerationId++;
                }
            }
        }

        if (lastOtherGenerationId != otherGenerationId) {
            lastOtherGenerationId = otherGenerationId;
            flags |= FLAG_INVALIDATED_OTHER;
        }

        if (lastScrollEdgesGenerationId != scrollEdgesGenerationId) {
            lastScrollEdgesGenerationId = scrollEdgesGenerationId;
            flags |= FLAG_INVALIDATED_SCROLL_EDGES;
        }

        if (lastScrollGenerationId != scrollGenerationId) {
            lastScrollGenerationId = scrollGenerationId;
            flags |= FLAG_INVALIDATED_SCROLL;
        }

        if (flags != 0 || hasInvalidatedDrawables) {
            if (flags != 0) {
                Trace.beginSection("G.Listeners");
                if (glassInvalidationListener != null) {
                    glassInvalidationListener.onGlassInvalidated(flags);
                }
                Trace.endSection();
            }

            final boolean invalidateAll = BitwiseUtils.hasFlag(flags, FLAG_INVALIDATED_POSITIONS);
            final boolean invalidateScroll = BitwiseUtils.hasFlag(flags, FLAG_INVALIDATED_SCROLL)
                || BitwiseUtils.hasFlag(flags, FLAG_INVALIDATED_SCROLL_EDGES);

            Trace.beginSection("G.UpdateDisplayLists");
            for (int a = 0, N = viewPositionStates.size(); a < N; a++) {
                final ViewPositionState state = viewPositionStates.get(a);
                if (state.visible && (state.invalidated || invalidateAll || invalidateScroll)) {
                    state.invalidated = false;
                    if (state.drawable.shouldHaveDisplayList()) {
                        state.drawable.buildDisplayList();
                    } else {
                        state.drawable.invalidateSelf();
                        state.view.invalidate();
                    }
                }
            }
            Trace.endSection();
        }
    }



    /* * */

    private final RectF tmpPositionRect = new RectF();
    private final Rect tmpPositionRect2 = new Rect();
    private final Rect tmpReservedRect = new Rect();

    /** A glass changed inside its reserved bounds: recorded again, the capture stays. */
    private boolean hasInvalidatedDrawables;

    private int lastRootWidth, lastRootHeight;

    private final GlassPositionsArray allVisibleDrawablesWithDisplayListPositions = new GlassPositionsArray();

    private final GlassPositionsArray allVisibleDrawablesWithDisplayListPositionsMerged = new GlassPositionsArray();

    private final GlassPositionsArray output = new GlassPositionsArray();

    private boolean checkDrawablePositionsIfNeeded() {
        boolean hasInvalidatedPositionsOrVisibility = false;

        final int W = rootView.getWidth();
        final int H = rootView.getHeight();
        if (lastRootWidth != W || lastRootHeight != H) {
            lastRootWidth = W;
            lastRootHeight = H;
            hasInvalidatedPositionsOrVisibility = true;
        }
        if (externalCapturePositionInvalidated) {
            externalCapturePositionInvalidated = false;
            hasInvalidatedPositionsOrVisibility = true;
        }

        for (int a = 0, N = viewPositionStates.size(); a < N; a++) {
            final ViewPositionState state = viewPositionStates.get(a);
            if (!ViewPositionWatcher.computeRectInParent(state.view, rootView, tmpPositionRect)) {
                continue;
            }

            boolean invalidated = false;
            boolean captureInvalidated = false;

            if (!state.viewPositionRelativeRoot.equals(tmpPositionRect)) {
                state.viewPositionRelativeRoot.set(tmpPositionRect);
                state.invalidated = true;
                state.drawable.setSourceOffset(state.viewPositionRelativeRoot.left, state.viewPositionRelativeRoot.top);
                invalidated = true;
            }

            state.drawable.getBoundsWithPaddingInset(tmpPositionRect2);
            tmpPositionRect.set(tmpPositionRect2);
            if (!state.drawableBoundsRelativeView.equals(tmpPositionRect)) {
                state.drawableBoundsRelativeView.set(tmpPositionRect);
                state.invalidated = true;
                invalidated = true;
            }

            tmpPositionRect.offset(state.viewPositionRelativeRoot.left, state.viewPositionRelativeRoot.top);
            if (!state.drawableBoundsRelativeRoot.equals(tmpPositionRect)) {
                state.drawableBoundsRelativeRoot.set(tmpPositionRect);
                state.invalidated = true;
                invalidated = true;
            }

            tmpPositionRect.set(state.drawableBoundsRelativeRoot);
            tmpPositionRect.inset(-state.drawable.getOutsetX(), -state.drawable.getOutsetY());
            if (!state.drawableBoundsRelativeRootWithOutset.equals(tmpPositionRect)) {
                state.drawableBoundsRelativeRootWithOutset.set(tmpPositionRect);
                state.invalidated = true;
                invalidated = true;
            }

            // what is captured: the bounds with the area the glass may still grow to
            tmpPositionRect.set(state.drawableBoundsRelativeRoot);
            if (state.drawable.getReservedBounds(tmpReservedRect)) {
                tmpPositionRect.union(
                    tmpReservedRect.left + state.viewPositionRelativeRoot.left,
                    tmpReservedRect.top + state.viewPositionRelativeRoot.top,
                    tmpReservedRect.right + state.viewPositionRelativeRoot.left,
                    tmpReservedRect.bottom + state.viewPositionRelativeRoot.top);
            }
            tmpPositionRect.inset(-state.drawable.getOutsetX(), -state.drawable.getOutsetY());
            if (!state.captureBoundsRelativeRootWithOutset.equals(tmpPositionRect)) {
                state.captureBoundsRelativeRootWithOutset.set(tmpPositionRect);
                captureInvalidated = true;
            }

            final View view = state.view;
            final boolean isViewVisible = !state.drawableBoundsRelativeRoot.isEmpty()
                && view.isAttachedToWindow()
                && state.drawableBoundsRelativeRoot.intersects(0, 0, W, H)
                && state.drawable.getAlpha() > 0
                && isShownInRoot(view)
                && view.getScaleX() != 0
                && view.getScaleY() != 0;

            final boolean hasDisplayList = isViewVisible && state.drawable.hasDisplayList();
            final boolean visibilityInvalidated = state.visible != isViewVisible || isViewVisible && !hasDisplayList;
            if (visibilityInvalidated) {
                state.visible = isViewVisible;
                state.invalidated = true;
                invalidated = true;
            }

            if (visibilityInvalidated) {
                captureInvalidated = true;
            }
            if ((isViewVisible || visibilityInvalidated) && captureInvalidated) {
                hasInvalidatedPositionsOrVisibility = true;
            } else if (isViewVisible && invalidated) {
                hasInvalidatedDrawables = true;
            }
        }

        if (hasInvalidatedPositionsOrVisibility) {
            allVisibleDrawablesWithDisplayListPositions.clear();
            for (int a = 0, N = viewPositionStates.size(); a < N; a++) {
                final ViewPositionState state = viewPositionStates.get(a);
                if (state.visible && state.drawable.shouldHaveDisplayList()) {
                    allVisibleDrawablesWithDisplayListPositions.add(state.captureBoundsRelativeRootWithOutset);
                }
            }
            if (!externalCapturePosition.isEmpty()) {
                allVisibleDrawablesWithDisplayListPositions.add(externalCapturePosition);
            }
            positionsMerger.merge(
                allVisibleDrawablesWithDisplayListPositions,
                allVisibleDrawablesWithDisplayListPositionsMerged);
        }

        return hasInvalidatedPositionsOrVisibility;
    }

    /** With the parents: a glass in a hidden container, e.g. the button of another page, needs no capture. */
    private boolean isShownInRoot(View view) {
        View current = view;
        while (current != null && current != rootView) {
            if (current.getVisibility() != View.VISIBLE || current.getAlpha() <= 0) {
                return false;
            }
            final ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return true;
    }

    public GlassPositionsArray getAllVisibleDrawablesWithDisplayListPositionsMerged() {
        output.set(allVisibleDrawablesWithDisplayListPositionsMerged);
        return output;
    }



    /* * */

    private static class ViewPositionState {
        private final @NonNull View view;
        private final @NonNull GlassDrawable drawable;
        private final RectF viewPositionRelativeRoot = new RectF();
        private final RectF drawableBoundsRelativeView = new RectF();
        private final RectF drawableBoundsRelativeRoot = new RectF();
        private final RectF drawableBoundsRelativeRootWithOutset = new RectF();
        private final RectF captureBoundsRelativeRootWithOutset = new RectF();
        private boolean invalidated;
        private boolean visible;

        private ViewPositionState(@NonNull View view, @NonNull GlassDrawable drawable) {
            this.view = view;
            this.drawable = drawable;
        }
    }



    /* Colors */

    private final ArrayList<ThemedSourceColorState> themedSourceColorStates = new ArrayList<>();

    public void addColorSource(GlassSourceColor sourceColor, ThemedColorDelegate delegate) {
        themedSourceColorStates.add(new ThemedSourceColorState(sourceColor, delegate));
    }

    public GlassSourceColor createThemedColorSource(ThemedColorDelegate delegate) {
        final GlassSourceColor blurredBackgroundSourceColor = new GlassSourceColor();
        addColorSource(blurredBackgroundSourceColor, delegate);
        blurredBackgroundSourceColor.setColor(delegate.getThemedColor());
        return blurredBackgroundSourceColor;
    }

    public interface ThemedColorDelegate {
        int getThemedColor();
    }

    private static class ThemedSourceColorState {
        private final GlassSourceColor sourceColor;
        private final ThemedColorDelegate colorDelegate;

        private ThemedSourceColorState(GlassSourceColor sourceColor, ThemedColorDelegate colorDelegate) {
            this.sourceColor = sourceColor;
            this.colorDelegate = colorDelegate;
        }
    }





    private static GlassSettings glassSettings;
    private static int glassSettingsGenerationId;

    private static void checkGlassSettings() {
        final boolean isBlurEnabled = LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR);
        final boolean isLiquidGlassEnabled = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS);

        final boolean glassSettingsInvalidated = glassSettings == null
            || glassSettings.isBlurEnabled != isBlurEnabled
            || glassSettings.isLiquidGlassEnabled != isLiquidGlassEnabled;

        if (glassSettingsInvalidated) {
            glassSettings = new GlassSettings(isBlurEnabled, isLiquidGlassEnabled);
            glassSettingsGenerationId++;
        }
    }

    public static GlassSettings getCurrentGlassSettings() {
        checkGlassSettings();
        return glassSettings;
    }

    /** Changes with the glass settings (LiteMode reports its changes here): a cheap check for drawables drawn by hand. */
    public static int getGlassSettingsGenerationId() {
        return glassSettingsGenerationId;
    }
}
