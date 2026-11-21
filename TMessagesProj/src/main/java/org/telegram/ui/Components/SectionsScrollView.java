package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.FiltersSetupActivity;

import java.util.ArrayList;
import java.util.Objects;

public class SectionsScrollView extends ScrollView implements IBlur3Capture {

    private static final float OPAQUE_BACKGROUND_OVERLAP = 1.0f;
    private static final int SECTION_VALUES_COUNT = 5;
    private static final long OVERSCROLL_CAPTURE_TIMEOUT_MS = 500L;

    private Theme.ResourcesProvider resourcesProvider;
    private LinearLayout contentView;

    private final float sectionRadius = dp(16);
    private final Paint backgroundPaint = new Paint();
    private final Paint sectionPaint = new Paint();
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect cornerSrc = new Rect();
    private final RectF cornerDst = new RectF();
    private final RectF captureRect = new RectF();
    private final Matrix inverseMatrix = new Matrix();
    private Bitmap cornerBitmap;
    private int bitmapSize;
    private int bitmapBackgroundColor;
    private boolean drawBackground;
    private float[] sections = new float[SECTION_VALUES_COUNT * 16];
    private int sectionsCount;
    private float[] opaqueIntervals = new float[32];
    private int opaqueIntervalsCount;
    private boolean overscrolling;
    private ArrayList<OnOverscrollChangedListener> overscrollListeners;
    private final Runnable finishOverscroll = () -> setOverscrolling(false);

    public interface OnOverscrollChangedListener {
        void onOverscrollChanged(boolean active);
    }

    public static boolean isSectionView(View view) {
        return !Objects.equals(view.getTag(), RecyclerListView.TAG_NOT_SECTION) && !(
                view instanceof TextInfoPrivacyCell ||
                        view instanceof ShadowSectionCell ||
                        view instanceof FiltersSetupActivity.HintInnerCell
        );
    }

    public SectionsScrollView(Context context, LinearLayout content, Theme.ResourcesProvider resourcesProvider) {
        this(context, content, resourcesProvider, true);
    }
    public SectionsScrollView(
            Context context,
            LinearLayout content,
            Theme.ResourcesProvider resourcesProvider,
            boolean enableTopPadding
    ) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.contentView = content;
        setWillNotDraw(false);

        contentView.setPadding(dp(12), dp(enableTopPadding ? 12 : 4), dp(12), dp(12));

    }

    public boolean isDrawBackground() {
        return drawBackground;
    }

    public void setDrawBackground(boolean drawBackground) {
        if (this.drawBackground == drawBackground) return;
        this.drawBackground = drawBackground;
        invalidate();
    }

    private ArrayList<Runnable> onScroll = new ArrayList<>();
    public void onScroll(Runnable listener) {
        onScroll.add(listener);
    }

    public boolean hasActiveOverscroll() {
        return overscrolling;
    }

    public void addOnOverscrollChangedListener(OnOverscrollChangedListener listener) {
        if (listener == null) return;
        if (overscrollListeners == null) {
            overscrollListeners = new ArrayList<>();
        }
        if (!overscrollListeners.contains(listener)) {
            overscrollListeners.add(listener);
        }
    }

    public void removeOnOverscrollChangedListener(OnOverscrollChangedListener listener) {
        if (overscrollListeners != null) {
            overscrollListeners.remove(listener);
        }
    }

    private void setOverscrolling(boolean overscrolling) {
        if (this.overscrolling == overscrolling) return;
        this.overscrolling = overscrolling;
        invalidate();
        if (overscrollListeners == null) return;
        for (int i = overscrollListeners.size() - 1; i >= 0; i--) {
            overscrollListeners.get(i).onOverscrollChanged(overscrolling);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(finishOverscroll);
        setOverscrolling(false);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);

        for (Runnable listener : onScroll)
            listener.run();

        invalidate();
        contentView.invalidate();
    }

    @Override
    protected boolean overScrollBy(
            int deltaX, int deltaY,
            int scrollX, int scrollY,
            int scrollRangeX, int scrollRangeY,
            int maxOverScrollX, int maxOverScrollY,
            boolean isTouchEvent
    ) {
        final long targetScrollY = (long) scrollY + deltaY;
        if (getOverScrollMode() != OVER_SCROLL_NEVER &&
                (targetScrollY < 0L || targetScrollY > scrollRangeY)) {
            setOverscrolling(true);
            removeCallbacks(finishOverscroll);
            postDelayed(finishOverscroll, OVERSCROLL_CAPTURE_TIMEOUT_MS);
        }
        return super.overScrollBy(
                deltaX, deltaY,
                scrollX, scrollY,
                scrollRangeX, scrollRangeY,
                maxOverScrollX, maxOverScrollY,
                isTouchEvent
        );
    }

    private final ArrayList<View> children = new ArrayList<>();
    private void gatherChildren(ViewGroup layout, float x, float y) {
        for (int i = 0; i < layout.getChildCount(); ++i) {
            final View child = layout.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            if (child instanceof LinearLayout && ((LinearLayout) child).getOrientation() == LinearLayout.VERTICAL && x + child.getX() <= contentView.getPaddingLeft() && x + child.getX() + child.getWidth() >= contentView.getWidth() - contentView.getPaddingRight()) {
                gatherChildren((LinearLayout) child, x + child.getX(), y + child.getY());
            } else {
                children.add(child);
            }
        }
    }
    private float getChildX(View child) {
        if (child == contentView) return 0.0f;
        if (!(child.getParent() instanceof View)) return child.getX();
        return getChildX((View) child.getParent()) + child.getX();
    }
    private float getChildY(View child) {
        if (child == contentView) return 0.0f;
        if (!(child.getParent() instanceof View)) return child.getY();
        return getChildY((View) child.getParent()) + child.getY();
    }

    private void collectSections() {
        sectionsCount = 0;
        children.clear();
        gatherChildren(contentView, 0, 0);

        View start = null, prev = null;
        for (View child : children) {
            if (!isSectionView(child)) {
                addSection(start, prev);
                start = prev = null;
                continue;
            }
            if (start != null && Math.abs(prev.getAlpha() - child.getAlpha()) > 0.1f) {
                addSection(start, prev);
                start = null;
            }
            if (start == null) {
                start = child;
            }
            prev = child;
        }
        addSection(start, prev);
    }

    private void addSection(View from, View to) {
        if (from == null || to == null) return;

        float fromTopMargin = 0.0f;
        float toBottomMargin = 0.0f;
        final ViewGroup.LayoutParams fromLp = from.getLayoutParams();
        final ViewGroup.LayoutParams toLp = to.getLayoutParams();
        if (from.getParent() != contentView && fromLp instanceof MarginLayoutParams) {
            fromTopMargin = ((MarginLayoutParams) fromLp).topMargin;
        }
        if (to.getParent() != contentView && toLp instanceof MarginLayoutParams) {
            toBottomMargin = ((MarginLayoutParams) toLp).bottomMargin;
        }
        toBottomMargin += getTrailingPaddingBottom(to);

        final float left = contentView.getX() + getChildX(from);
        final float top = contentView.getY() + getChildY(from) - fromTopMargin;
        final float right = left + from.getWidth();
        final float bottom = contentView.getY() + getChildY(to) + to.getHeight() + toBottomMargin;
        if (right <= left || bottom <= top) return;

        ensureSectionsCapacity();
        sections[sectionsCount++] = left;
        sections[sectionsCount++] = top;
        sections[sectionsCount++] = right;
        sections[sectionsCount++] = bottom;
        sections[sectionsCount++] = from.getAlpha();
    }

    // bottom paddings of the gathered parents that end with this child (e.g. under LinkActionView buttons)
    private float getTrailingPaddingBottom(View child) {
        float padding = 0.0f;
        while (child.getParent() instanceof ViewGroup && child.getParent() != contentView) {
            final ViewGroup parent = (ViewGroup) child.getParent();
            for (int i = parent.getChildCount() - 1; i >= 0; --i) {
                final View last = parent.getChildAt(i);
                if (last.getVisibility() == View.GONE) continue;
                if (last != child) return padding;
                break;
            }
            padding += parent.getPaddingBottom();
            child = parent;
        }
        return padding;
    }

    private void ensureSectionsCapacity() {
        if (sectionsCount + SECTION_VALUES_COUNT <= sections.length) return;
        final float[] newSections = new float[sections.length * 2];
        System.arraycopy(sections, 0, newSections, 0, sectionsCount);
        sections = newSections;
    }

    private void updateColors() {
        final int backgroundColor = ColorUtils.setAlphaComponent(
                Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider),
                0xFF
        );
        backgroundPaint.setColor(backgroundColor);
        if (bitmapBackgroundColor != backgroundColor) {
            bitmapBackgroundColor = backgroundColor;
            cornerBitmap = null;
        }
    }

    private int getSectionColor(float alpha) {
        final int sectionColor = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
        final int sectionAlpha = Math.round(Color.alpha(sectionColor) * Math.max(0.0f, Math.min(1.0f, alpha)));
        if (!drawBackground) {
            return ColorUtils.setAlphaComponent(sectionColor, sectionAlpha);
        }
        return ColorUtils.compositeColors(
                ColorUtils.setAlphaComponent(sectionColor, sectionAlpha),
                backgroundPaint.getColor()
        );
    }

    private void drawSectionsBackgrounds(Canvas canvas) {
        captureRect.set(
                getScrollX(), getScrollY(),
                getScrollX() + getWidth(), getScrollY() + getHeight()
        );
        for (int i = 0; i < sectionsCount; i += SECTION_VALUES_COUNT) {
            if (!captureRect.intersects(
                    sections[i], sections[i + 1], sections[i + 2], sections[i + 3])) continue;
            sectionPaint.setColor(getSectionColor(sections[i + 4]));
            canvas.drawRect(sections[i], sections[i + 1], sections[i + 2], sections[i + 3], sectionPaint);
        }
    }

    private void drawOptimizedBackground(Canvas canvas) {
        final float viewLeft = getScrollX();
        final float viewTop = getScrollY();
        final float viewRight = viewLeft + getWidth();
        final float viewBottom = viewTop + getHeight();
        final float sectionLeft = Math.max(viewLeft, Math.min(viewRight, contentView.getX() + contentView.getPaddingLeft()));
        final float sectionRight = Math.max(sectionLeft, Math.min(viewRight, contentView.getX() + contentView.getWidth() - contentView.getPaddingRight()));

        collectOpaqueIntervals(viewTop, viewBottom);
        if (opaqueIntervalsCount == 0 || sectionLeft >= sectionRight) {
            canvas.drawRect(viewLeft, viewTop, viewRight, viewBottom, backgroundPaint);
        } else {
            final float firstTop = opaqueIntervals[0];
            final float lastBottom = opaqueIntervals[opaqueIntervalsCount - 1];
            if (firstTop > viewTop) {
                canvas.drawRect(viewLeft, viewTop, viewRight, Math.min(viewBottom, firstTop + OPAQUE_BACKGROUND_OVERLAP), backgroundPaint);
            }
            if (lastBottom < viewBottom) {
                canvas.drawRect(viewLeft, Math.max(viewTop, lastBottom - OPAQUE_BACKGROUND_OVERLAP), viewRight, viewBottom, backgroundPaint);
            }

            final float sidesTop = Math.max(viewTop, firstTop);
            final float sidesBottom = Math.min(viewBottom, lastBottom);
            if (sidesBottom > sidesTop) {
                canvas.drawRect(viewLeft, sidesTop, Math.min(viewRight, sectionLeft + OPAQUE_BACKGROUND_OVERLAP), sidesBottom, backgroundPaint);
                canvas.drawRect(Math.max(viewLeft, sectionRight - OPAQUE_BACKGROUND_OVERLAP), sidesTop, viewRight, sidesBottom, backgroundPaint);
            }

            for (int i = 2; i < opaqueIntervalsCount; i += 2) {
                final float from = opaqueIntervals[i - 1];
                final float to = opaqueIntervals[i];
                if (to > from) {
                    canvas.drawRect(
                            sectionLeft,
                            Math.max(viewTop, from - OPAQUE_BACKGROUND_OVERLAP),
                            sectionRight,
                            Math.min(viewBottom, to + OPAQUE_BACKGROUND_OVERLAP),
                            backgroundPaint
                    );
                }
            }
        }

        drawSectionsBackgrounds(canvas);
    }

    private void collectOpaqueIntervals(float viewTop, float viewBottom) {
        opaqueIntervalsCount = 0;
        for (int i = 0; i < sectionsCount; i += SECTION_VALUES_COUNT) {
            final float from = Math.max(viewTop, sections[i + 1]);
            final float to = Math.min(viewBottom, sections[i + 3]);
            if (to <= from) continue;
            ensureOpaqueIntervalsCapacity();
            opaqueIntervals[opaqueIntervalsCount++] = from;
            opaqueIntervals[opaqueIntervalsCount++] = to;
        }
        sortOpaqueIntervals();
        mergeOpaqueIntervals();
    }

    private void ensureOpaqueIntervalsCapacity() {
        if (opaqueIntervalsCount + 2 <= opaqueIntervals.length) return;
        final float[] newIntervals = new float[opaqueIntervals.length * 2];
        System.arraycopy(opaqueIntervals, 0, newIntervals, 0, opaqueIntervalsCount);
        opaqueIntervals = newIntervals;
    }

    private void sortOpaqueIntervals() {
        for (int i = 2; i < opaqueIntervalsCount; i += 2) {
            final float from = opaqueIntervals[i];
            final float to = opaqueIntervals[i + 1];
            int j = i - 2;
            while (j >= 0 && opaqueIntervals[j] > from) {
                opaqueIntervals[j + 2] = opaqueIntervals[j];
                opaqueIntervals[j + 3] = opaqueIntervals[j + 1];
                j -= 2;
            }
            opaqueIntervals[j + 2] = from;
            opaqueIntervals[j + 3] = to;
        }
    }

    private void mergeOpaqueIntervals() {
        int mergedCount = 0;
        for (int i = 0; i < opaqueIntervalsCount; i += 2) {
            final float from = opaqueIntervals[i];
            final float to = opaqueIntervals[i + 1];
            if (mergedCount > 0 && from <= opaqueIntervals[mergedCount - 1] + OPAQUE_BACKGROUND_OVERLAP) {
                opaqueIntervals[mergedCount - 1] = Math.max(opaqueIntervals[mergedCount - 1], to);
            } else {
                opaqueIntervals[mergedCount++] = from;
                opaqueIntervals[mergedCount++] = to;
            }
        }
        opaqueIntervalsCount = mergedCount;
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        updateColors();
        collectSections();
        if (drawBackground) {
            drawOptimizedBackground(canvas);
        } else {
            drawSectionsBackgrounds(canvas);
        }
        super.dispatchDraw(canvas);
        drawSectionCorners(canvas);
    }

    @Override
    public void capture(Canvas canvas, RectF position) {
        final long drawingTime = SystemClock.uptimeMillis();
        if (hasActiveOverscroll() && getOverScrollMode() != OVER_SCROLL_NEVER) {
            canvas.save();
            try {
                canvas.clipRect(position);
                final Matrix matrix = getMatrix();
                if (matrix.invert(inverseMatrix)) {
                    canvas.concat(inverseMatrix);
                }
                canvas.translate(-getX(), -getY());
                try {
                    super.drawChild(canvas, this, drawingTime);
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            } finally {
                canvas.restore();
            }
            return;
        }

        updateColors();
        final float scrollX = getScrollX();
        final float scrollY = getScrollY();
        captureRect.set(position);
        captureRect.offset(scrollX, scrollY);
        collectSections();
        final int sectionColor = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
        canvas.save();
        try {
            canvas.clipRect(position);
            canvas.translate(-scrollX, -scrollY);
            for (int i = 0; i < sectionsCount; i += SECTION_VALUES_COUNT) {
                if (!captureRect.intersects(
                        sections[i], sections[i + 1], sections[i + 2], sections[i + 3])) {
                    continue;
                }
                final int sectionAlpha = Math.round(Color.alpha(sectionColor) * Math.max(0.0f, Math.min(1.0f, sections[i + 4])));
                sectionPaint.setColor(ColorUtils.setAlphaComponent(sectionColor, sectionAlpha));
                cornerDst.set(sections[i], sections[i + 1], sections[i + 2], sections[i + 3]);
                canvas.drawRoundRect(cornerDst, sectionRadius, sectionRadius, sectionPaint);
            }

            canvas.save();
            try {
                canvas.translate(contentView.getX(), contentView.getY());
                final float contentX = contentView.getX();
                final float contentY = contentView.getY();
                if (contentView instanceof SectionsLinearLayout) {
                    final SectionsLinearLayout sectionsLayout = (SectionsLinearLayout) contentView;
                    for (int i = 0; i < sectionsLayout.getChildCount(); i++) {
                        final View child = sectionsLayout.getChildAt(i);
                        if (child.getVisibility() != View.VISIBLE) continue;
                        final float childLeft = contentX + child.getX();
                        final float childTop = contentY + child.getY();
                        if (!captureRect.intersects(
                                childLeft, childTop,
                                childLeft + child.getWidth(), childTop + child.getHeight())) continue;
                        sectionsLayout.drawChild(canvas, child, drawingTime);
                    }
                } else {
                    for (int i = 0; i < contentView.getChildCount(); i++) {
                        final View child = contentView.getChildAt(i);
                        if (child.getVisibility() != View.VISIBLE) continue;
                        final float childLeft = contentX + child.getX();
                        final float childTop = contentY + child.getY();
                        if (!captureRect.intersects(
                                childLeft, childTop,
                                childLeft + child.getWidth(), childTop + child.getHeight())) continue;
                        canvas.save();
                        canvas.translate(child.getX(), child.getY());
                        child.draw(canvas);
                        canvas.restore();
                    }
                }
            } finally {
                canvas.restore();
            }

            drawSectionCorners(canvas, captureRect);
        } finally {
            canvas.restore();
        }
    }

    private void drawSectionCorners(Canvas canvas) {
        captureRect.set(
                getScrollX(), getScrollY(),
                getScrollX() + getWidth(), getScrollY() + getHeight()
        );
        drawSectionCorners(canvas, captureRect);
    }

    private void drawSectionCorners(Canvas canvas, RectF clipRect) {
        if (sectionsCount == 0 || sectionRadius <= 0.0f) return;
        ensureCornerBitmap();
        for (int i = 0; i < sectionsCount; i += SECTION_VALUES_COUNT) {
            final float left = sections[i];
            final float top = sections[i + 1];
            final float right = sections[i + 2];
            final float bottom = sections[i + 3];
            drawCorner(canvas, 0, left, top, left + bitmapSize, top + bitmapSize, clipRect);
            drawCorner(canvas, 1, right - bitmapSize, top, right, top + bitmapSize, clipRect);
            drawCorner(canvas, 2, left, bottom - bitmapSize, left + bitmapSize, bottom, clipRect);
            drawCorner(canvas, 3, right - bitmapSize, bottom - bitmapSize, right, bottom, clipRect);
        }
    }

    private void ensureCornerBitmap() {
        final int size = Math.max(1, (int) Math.ceil(sectionRadius));
        if (cornerBitmap != null && bitmapSize == size) return;
        bitmapSize = size;
        cornerBitmap = Bitmap.createBitmap(size * 2, size * 2, Bitmap.Config.ARGB_8888);
        final Canvas bitmapCanvas = new Canvas(cornerBitmap);
        bitmapCanvas.drawColor(backgroundPaint.getColor());
        final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        bitmapCanvas.drawCircle(size, size, sectionRadius, clearPaint);
    }

    private void drawCorner(
            Canvas canvas, int corner,
            float left, float top, float right, float bottom,
            RectF clipRect
    ) {
        if (!clipRect.intersects(left, top, right, bottom)) return;
        final int srcLeft = (corner & 1) == 0 ? 0 : bitmapSize;
        final int srcTop = corner < 2 ? 0 : bitmapSize;
        cornerSrc.set(srcLeft, srcTop, srcLeft + bitmapSize, srcTop + bitmapSize);
        cornerDst.set(left, top, right, bottom);
        canvas.drawBitmap(cornerBitmap, cornerSrc, cornerDst, bitmapPaint);
    }

    public static class SectionsLinearLayout extends LinearLayout {
        public SectionsLinearLayout(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        public boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
            return super.drawChild(canvas, child, drawingTime);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            if (getParent() instanceof SectionsScrollView) {
                ((SectionsScrollView) getParent()).invalidate();
            }
        }
    }
}
