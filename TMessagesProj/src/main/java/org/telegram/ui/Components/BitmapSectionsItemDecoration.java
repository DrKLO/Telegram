/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;

public class BitmapSectionsItemDecoration extends RecyclerView.ItemDecoration implements IBlur3Capture {

    private static final float OPAQUE_BACKGROUND_OVERLAP = 1.0f;
    private static final float ANIMATED_SECTION_CONNECT_TOLERANCE = 1.5f;
    private static final int VERTEX_FLOATS_PER_RECT = 8;
    private static final int VERTEX_COLORS_PER_RECT = 4;
    private static final int VERTEX_INDICES_PER_RECT = 6;

    private final RecyclerListView parent;
    private final Utilities.CallbackReturn<View, Boolean> isSectionView;
    private final Utilities.CallbackReturn<Integer, Boolean> isViewTypeSection;
    private final Utilities.Callback5<Canvas, RectF, Float, Float, Float> drawSectionBackground;
    private final int padding;
    private final float radius;
    private final boolean enableTopPadding;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint cornerVerticesPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint backgroundPaint = new Paint();
    private final Paint sectionPaint = new Paint();
    private final Paint verticesPaint = new Paint();
    private final RectF backgroundRect = new RectF();
    private final Rect cornerSrc = new Rect();
    private final RectF cornerDst = new RectF();
    private Bitmap cornerBitmap;
    private BitmapShader cornerBitmapShader;
    private float[] opaqueIntervals = new float[32];
    private int opaqueIntervalsCount;
    private int bitmapSize;
    private float bitmapRadius = -1.0f;
    private boolean drawBackground;
    private RecyclerListView.SectionsDrawingMode drawingMode;
    private float[] vertices = new float[VERTEX_FLOATS_PER_RECT * 16];
    private int[] vertexColors = new int[VERTEX_COLORS_PER_RECT * 16];
    private short[] vertexIndices = new short[VERTEX_INDICES_PER_RECT * 16];
    private int verticesCount;
    private int vertexColorsCount;
    private int vertexIndicesCount;
    private boolean collectingVertices;
    private boolean sectionColorBackgroundDrawn;
    private boolean capturingSections;
    private float[] cornerVertices = new float[VERTEX_FLOATS_PER_RECT * 16];
    private float[] cornerTexs = new float[VERTEX_FLOATS_PER_RECT * 16];
    private short[] cornerIndices = new short[VERTEX_INDICES_PER_RECT * 16];
    private int cornerVerticesCount;
    private int cornerIndicesCount;
    private boolean collectingCornerVertices;
    private float[] animatedSections = new float[48];
    private int animatedSectionsCount;
    private float[] animatedGroups = new float[32];
    private int animatedGroupsCount;

    public BitmapSectionsItemDecoration(
            RecyclerListView parent,
            Utilities.CallbackReturn<View, Boolean> isSectionView,
            Utilities.CallbackReturn<Integer, Boolean> isViewTypeSection,
            int padding,
            float radius,
            Utilities.Callback5<Canvas, RectF, Float, Float, Float> drawSectionBackground,
            boolean enableTopPadding,
            boolean drawBackground,
            RecyclerListView.SectionsDrawingMode drawingMode
    ) {
        this.parent = parent;
        this.isSectionView = isSectionView;
        this.isViewTypeSection = isViewTypeSection;
        this.padding = padding;
        this.radius = radius;
        this.drawSectionBackground = drawSectionBackground;
        this.enableTopPadding = enableTopPadding;
        this.drawBackground = drawBackground;
        this.drawingMode = drawingMode;
        verticesPaint.setColor(Color.WHITE);
    }

    public boolean isDrawBackground() {
        return drawBackground;
    }

    public void setDrawBackground(boolean drawBackground) {
        if (this.drawBackground == drawBackground) return;
        this.drawBackground = drawBackground;
        parent.invalidateItemDecorations();
        parent.invalidate();
    }

    public void setDrawingMode(RecyclerListView.SectionsDrawingMode drawingMode) {
        if (this.drawingMode == drawingMode) return;
        this.drawingMode = drawingMode;
        parent.invalidateItemDecorations();
        parent.invalidate();
    }

    private void updateBackgroundColor() {
        final int color = parent.getSectionsBackgroundColorForDecoration();
        if (backgroundPaint.getColor() == color) {
            return;
        }
        backgroundPaint.setColor(color);
        cornerBitmap = null;
        cornerBitmapShader = null;
        bitmapRadius = -1.0f;
    }

    boolean isSectionItem(View view) {
        return isSectionView.run(view);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.State state) {
        if (!isSectionItem(view)) return;
        outRect.left = outRect.right = padding;
        final RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(view);
        final RecyclerView.Adapter adapter = recyclerView.getAdapter();
        if (holder == null || adapter == null) return;
        final int position = holder.getAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return;
        if (position == 0) outRect.top = enableTopPadding ? padding : AndroidUtilities.dp(4);
        if (position == adapter.getItemCount() - 1) outRect.bottom = padding;
    }

    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.State state) {
        if (recyclerView != parent) return;
        updateBackgroundColor();
        if (!drawBackground) {
            drawSectionsBackgrounds(canvas);
            return;
        }
        switch (drawingMode) {
            case FULL_BACKGROUND:
                canvas.drawRect(0.0f, 0.0f, parent.getWidth(), parent.getHeight(), backgroundPaint);
                drawSectionsBackgrounds(canvas);
                break;
            case SECTION_COLOR_BACKGROUND:
                sectionPaint.setColor(getSectionColor(1.0f));
                canvas.drawRect(0.0f, 0.0f, parent.getWidth(), parent.getHeight(), sectionPaint);
                drawOpaqueBackground(canvas, 0.0f);
                sectionColorBackgroundDrawn = true;
                drawSectionsBackgrounds(canvas);
                sectionColorBackgroundDrawn = false;
                break;
            case DRAW_VERTICES:
                beginVertices();
                collectingVertices = true;
                drawOpaqueBackground(canvas, 0.0f);
                drawSectionsBackgrounds(canvas);
                collectingVertices = false;
                flushVertices(canvas);
                break;
            case OPTIMIZED_BACKGROUND:
            default:
                drawOpaqueBackground(canvas, OPAQUE_BACKGROUND_OVERLAP);
                drawSectionsBackgrounds(canvas);
                break;
        }
    }

    @Override
    public void capture(Canvas canvas, RectF position) {
        canvas.save();
        try {
            canvas.clipRect(position);
            capturingSections = true;
            drawSectionsBackgrounds(canvas);
        } finally {
            capturingSections = false;
            canvas.restore();
        }
    }

    private void drawOpaqueBackground(Canvas canvas, float overlap) {
        final float width = parent.getWidth();
        final float height = parent.getHeight();
        final float sectionLeft = Math.max(0.0f, Math.min(width, parent.getPaddingLeft() + padding));
        final float sectionRight = Math.max(sectionLeft, Math.min(width, width - parent.getPaddingRight() - padding));
        if (sectionLeft >= sectionRight) {
            drawBackgroundRect(canvas, 0.0f, 0.0f, width, height);
            return;
        }
        collectOpaqueIntervals(height);
        sortOpaqueIntervals();
        mergeOpaqueIntervals(height, overlap);
        if (opaqueIntervalsCount == 0) {
            drawBackgroundRect(canvas, 0.0f, 0.0f, width, height);
            return;
        }

        final float firstSectionTop = opaqueIntervals[0];
        final float lastSectionBottom = opaqueIntervals[opaqueIntervalsCount - 1];
        if (firstSectionTop > 0.0f) {
            drawBackgroundRect(canvas, 0.0f, 0.0f, width, Math.min(height, firstSectionTop + overlap));
        }
        if (lastSectionBottom < height) {
            drawBackgroundRect(canvas, 0.0f, Math.max(0.0f, lastSectionBottom - overlap), width, height);
        }

        final float sidesTop = firstSectionTop > 0.0f ? firstSectionTop : 0.0f;
        final float sidesBottom = lastSectionBottom < height ? lastSectionBottom : height;
        if (sidesBottom > sidesTop) {
            drawBackgroundRect(canvas, 0.0f, sidesTop, Math.min(width, sectionLeft + overlap), sidesBottom);
            drawBackgroundRect(canvas, Math.max(0.0f, sectionRight - overlap), sidesTop, width, sidesBottom);
        }

        for (int i = 2; i < opaqueIntervalsCount; i += 2) {
            final float from = opaqueIntervals[i - 1];
            final float to = opaqueIntervals[i];
            if (to > from) {
                drawBackgroundRect(
                        canvas,
                        sectionLeft,
                        Math.max(0.0f, from - overlap),
                        sectionRight,
                        Math.min(height, to + overlap)
                );
            }
        }
    }

    private void mergeOpaqueIntervals(float height, float overlap) {
        int mergedCount = 0;
        for (int i = 0; i < opaqueIntervalsCount; i += 2) {
            final float from = Math.max(0.0f, opaqueIntervals[i]);
            final float to = Math.min(height, opaqueIntervals[i + 1]);
            if (to <= from) continue;
            if (mergedCount > 0 && from <= opaqueIntervals[mergedCount - 1] + overlap) {
                opaqueIntervals[mergedCount - 1] = Math.max(opaqueIntervals[mergedCount - 1], to);
            } else {
                opaqueIntervals[mergedCount++] = from;
                opaqueIntervals[mergedCount++] = to;
            }
        }
        opaqueIntervalsCount = mergedCount;
    }

    private void collectOpaqueIntervals(float height) {
        opaqueIntervalsCount = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            final View child = parent.getChildAt(i);
            final int position = parent.getChildAdapterPosition(child);
            final RecyclerView.ViewHolder holder = parent.getChildViewHolder(child);
            if (child == parent.getEmptyView() || child.getVisibility() != View.VISIBLE ||
                    child.getAlpha() <= 0.0f ||
                    (parent.isAnimating() && holder != null && holder.isRemoved() && child.getAlpha() < 1.0f) ||
                    !isSectionItem(child) || parent.isInsideForcedSection(position)) continue;
            float from = RecyclerListView.top(child);
            float to = RecyclerListView.bottom(child);
            if (child instanceof JoinToSendSettingsView) to -= ((JoinToSendSettingsView) child).getBottomInfoMargin();
            if (parent.applyPaddingToSections) {
                from = Math.max(parent.getPaddingTop(), from);
                to = Math.min(height - parent.getPaddingBottom(), to);
            }
            addOpaqueInterval(from, to);
        }
        if (parent.forcedSections != null) {
            for (int i = 0; i < parent.forcedSections.size(); i++) {
                final long section = parent.forcedSections.get(i);
                final int beginPosition = AndroidUtilities.unpackA(section);
                final int endPosition = AndroidUtilities.unpackB(section);
                float from = height;
                float to = 0.0f;
                for (int j = 0; j < parent.getChildCount(); j++) {
                    final View child = parent.getChildAt(j);
                    final int position = parent.getChildAdapterPosition(child);
                    if (position >= beginPosition && position <= endPosition) {
                        from = Math.min(from, RecyclerListView.top(child));
                        to = Math.max(to, RecyclerListView.bottom(child));
                    }
                }
                addOpaqueInterval(from, to);
            }
        }
    }

    private void addOpaqueInterval(float from, float to) {
        if (to <= from) return;
        if (opaqueIntervalsCount + 2 > opaqueIntervals.length) {
            final float[] newIntervals = new float[opaqueIntervals.length * 2];
            System.arraycopy(opaqueIntervals, 0, newIntervals, 0, opaqueIntervalsCount);
            opaqueIntervals = newIntervals;
        }
        opaqueIntervals[opaqueIntervalsCount++] = from;
        opaqueIntervals[opaqueIntervalsCount++] = to;
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

    private void drawSectionsBackgrounds(Canvas canvas) {
        if (parent.isAnimating()) drawAnimatingSectionsBackgrounds(canvas);
        else drawStableSectionsBackgrounds(canvas);
        drawForcedSectionsBackgrounds(canvas);
    }

    private void drawStableSectionsBackgrounds(Canvas canvas) {
        int startIndex = -1, previousIndex = -1;
        View start = null, previous = null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            final View child = parent.getChildAt(i);
            if (child == parent.getEmptyView() || child.getVisibility() != View.VISIBLE || child.getAlpha() <= 0.0f ||
                    !isSectionItem(child) || parent.isInsideForcedSection(parent.getChildAdapterPosition(child))) {
                drawSectionBackground(canvas, start, previous, hasAbove(start, startIndex), hasBelow(previous, previousIndex));
                startIndex = previousIndex = -1;
                start = previous = null;
                continue;
            }
            if (start != null && Math.abs(previous.getAlpha() - child.getAlpha()) > 0.1f) {
                drawSectionBackground(canvas, start, previous, hasAbove(start, startIndex), hasBelow(previous, previousIndex));
                startIndex = -1;
                start = null;
            }
            if (start == null) {
                startIndex = i;
                start = child;
            }
            previousIndex = i;
            previous = child;
        }
        drawSectionBackground(canvas, start, previous, hasAbove(start, startIndex), hasBelow(previous, previousIndex));
    }

    private void drawAnimatingSectionsBackgrounds(Canvas canvas) {
        animatedSectionsCount = 0;
        animatedGroupsCount = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            final View child = parent.getChildAt(i);
            if (child == parent.getEmptyView() || child.getVisibility() != View.VISIBLE || child.getAlpha() <= 0.0f || !isSectionItem(child)) continue;
            float from = RecyclerListView.top(child);
            float to = RecyclerListView.bottom(child);
            final RecyclerView.ViewHolder holder = parent.getChildViewHolder(child);
            if (holder != null && holder.isRemoved() && child.getAlpha() < 1.0f) {
                if (holder.mOldCompoundPosition >= 0) {
                    final int nextPosition = (int) Math.ceil(holder.mOldCompoundPosition / 1000.0) + 1;
                    final View next = parent.findViewByPosition(nextPosition);
                    if (next != null && to > next.getY() && isSectionItem(next)) {
                        final RecyclerView.ViewHolder nextHolder = parent.getChildViewHolder(next);
                        if (nextHolder != null && !nextHolder.isRemoved()) {
                            from--;
                            to = next.getY();
                            if (to < from) continue;
                        }
                    }
                }
            } else if (parent.isInsideForcedSection(parent.getChildAdapterPosition(child))) {
                continue;
            }
            addAnimatedSection(from, to, child.getAlpha());
        }
        sortAnimatedSections();

        int groupStart = 0;
        while (groupStart < animatedSectionsCount) {
            float groupTo = animatedSections[groupStart + 1];
            int groupEnd = groupStart + 3;
            while (groupEnd < animatedSectionsCount && animatedSections[groupEnd] <= groupTo + ANIMATED_SECTION_CONNECT_TOLERANCE) {
                groupTo = Math.max(groupTo, animatedSections[groupEnd + 1]);
                groupEnd += 3;
            }
            drawAnimatedSectionGroup(canvas, groupStart, groupEnd);
            groupStart = groupEnd;
        }
    }

    private void addAnimatedSection(float from, float to, float alpha) {
        if (animatedSectionsCount + 3 > animatedSections.length) {
            final float[] newSections = new float[animatedSections.length * 2];
            System.arraycopy(animatedSections, 0, newSections, 0, animatedSectionsCount);
            animatedSections = newSections;
        }
        animatedSections[animatedSectionsCount++] = from;
        animatedSections[animatedSectionsCount++] = to;
        animatedSections[animatedSectionsCount++] = alpha;
    }

    private void sortAnimatedSections() {
        for (int i = 3; i < animatedSectionsCount; i += 3) {
            final float from = animatedSections[i];
            final float to = animatedSections[i + 1];
            final float alpha = animatedSections[i + 2];
            int j = i - 3;
            while (j >= 0 && animatedSections[j] > from) {
                animatedSections[j + 3] = animatedSections[j];
                animatedSections[j + 4] = animatedSections[j + 1];
                animatedSections[j + 5] = animatedSections[j + 2];
                j -= 3;
            }
            animatedSections[j + 3] = from;
            animatedSections[j + 4] = to;
            animatedSections[j + 5] = alpha;
        }
    }

    private void drawAnimatedSectionGroup(Canvas canvas, int start, int end) {
        float stableFrom = Float.MAX_VALUE;
        float stableTo = -Float.MAX_VALUE;
        float minFrom = Float.MAX_VALUE;
        float maxTo = -Float.MAX_VALUE;
        float maxAlpha = 0.0f;

        for (int i = start; i < end; i += 3) {
            final float from = animatedSections[i];
            final float to = animatedSections[i + 1];
            final float alpha = animatedSections[i + 2];
            minFrom = Math.min(minFrom, from);
            maxTo = Math.max(maxTo, to);
            maxAlpha = Math.max(maxAlpha, alpha);
            if (alpha >= 0.99f) {
                stableFrom = Math.min(stableFrom, from);
                stableTo = Math.max(stableTo, to);
            }
        }
        if (maxAlpha < 0.001f) return;

        float drawFrom;
        float drawTo;
        float drawAlpha;
        if (stableFrom == Float.MAX_VALUE) {
            drawFrom = minFrom;
            drawTo = maxTo;
            drawAlpha = maxAlpha;
        } else {
            drawFrom = stableFrom;
            drawTo = stableTo;
            drawAlpha = 1.0f;
            float topExtension = 0.0f;
            float bottomExtension = 0.0f;
            for (int i = start; i < end; i += 3) {
                final float from = animatedSections[i];
                final float to = animatedSections[i + 1];
                final float alpha = animatedSections[i + 2];
                if (alpha >= 0.99f) continue;
                if (from < stableFrom) topExtension = Math.max(topExtension, (stableFrom - from) * alpha);
                if (to > stableTo) bottomExtension = Math.max(bottomExtension, (to - stableTo) * alpha);
            }
            drawFrom -= topExtension;
            drawTo += bottomExtension;
        }
        if (drawTo <= drawFrom) return;

        backgroundRect.set(
                parent.getPaddingLeft() + padding,
                drawFrom,
                parent.getWidth() - parent.getPaddingRight() - padding,
                drawTo
        );
        drawSectionRect(canvas, backgroundRect, drawAlpha);
        addAnimatedGroup(drawFrom, drawTo, drawAlpha, drawAlpha);
    }

    private void addAnimatedGroup(float from, float to, float topRadiusProgress, float bottomRadiusProgress) {
        if (animatedGroupsCount + 4 > animatedGroups.length) {
            final float[] newGroups = new float[animatedGroups.length * 2];
            System.arraycopy(animatedGroups, 0, newGroups, 0, animatedGroupsCount);
            animatedGroups = newGroups;
        }
        animatedGroups[animatedGroupsCount++] = from;
        animatedGroups[animatedGroupsCount++] = to;
        animatedGroups[animatedGroupsCount++] = topRadiusProgress;
        animatedGroups[animatedGroupsCount++] = bottomRadiusProgress;
    }

    private void drawForcedSectionsBackgrounds(Canvas canvas) {
        if (parent.forcedSections == null) return;
        for (int i = 0; i < parent.forcedSections.size(); i++) {
            final long section = parent.forcedSections.get(i);
            final int beginPosition = AndroidUtilities.unpackA(section);
            final int endPosition = AndroidUtilities.unpackB(section);
            float from = parent.getHeight() + radius;
            float to = -radius;
            for (int j = 0; j < parent.getChildCount(); j++) {
                final View child = parent.getChildAt(j);
                final int position = parent.getChildAdapterPosition(child);
                if (position >= beginPosition && position <= endPosition) {
                    from = Math.min(from, RecyclerListView.top(child));
                    to = Math.max(to, RecyclerListView.bottom(child));
                }
            }
            if (from < to) {
                backgroundRect.set(parent.getPaddingLeft() + padding, from, parent.getWidth() - parent.getPaddingRight() - padding, to);
                drawSectionRect(canvas, backgroundRect, 1.0f);
            }
        }
    }

    private void drawSectionBackground(Canvas canvas, View from, View to, boolean hasAbove, boolean hasBelow) {
        if (from == null || to == null) return;
        float bottomMargin = 0.0f;
        if (to instanceof JoinToSendSettingsView) bottomMargin = ((JoinToSendSettingsView) to).getBottomInfoMargin();
        backgroundRect.set(
                from.getLeft(),
                Math.max(parent.applyPaddingToSections ? parent.getPaddingTop() : -radius - parent.getSectionsOverscanTop(), RecyclerListView.top(from) - (hasAbove ? radius : 0.0f)),
                from.getRight(),
                Math.min(parent.getHeight() - (parent.applyPaddingToSections ? parent.getPaddingBottom() : -radius), RecyclerListView.bottom(to) + (hasBelow ? radius : 0.0f) - bottomMargin)
        );
        if (backgroundRect.bottom >= backgroundRect.top) drawSectionRect(canvas, backgroundRect, from.getAlpha());
    }

    private void drawSectionRect(Canvas canvas, RectF rect, float alpha) {
        if (capturingSections) {
            final int sectionColor = parent.getSectionColorForDecoration();
            final int sectionAlpha = Math.round(Color.alpha(sectionColor) * Math.max(0.0f, Math.min(1.0f, alpha)));
            sectionPaint.setColor(ColorUtils.setAlphaComponent(sectionColor, sectionAlpha));
            canvas.drawRoundRect(rect, radius, radius, sectionPaint);
            return;
        }
        if (drawBackground) {
            sectionPaint.setColor(getSectionColor(alpha));
            if (sectionColorBackgroundDrawn && alpha >= 1.0f) {
                return;
            }
            if (collectingVertices) {
                addVerticesRect(rect.left, rect.top, rect.right, rect.bottom, sectionPaint.getColor());
            } else {
                canvas.drawRect(rect, sectionPaint);
            }
        } else {
            drawSectionBackground.run(canvas, rect, 0.0f, 0.0f, alpha);
        }
    }

    private int getSectionColor(float alpha) {
        final int sectionColor = parent.getSectionColorForDecoration();
        final int foregroundAlpha = Math.round(Color.alpha(sectionColor) * Math.max(0.0f, Math.min(1.0f, alpha)));
        final int foreground = ColorUtils.setAlphaComponent(sectionColor, foregroundAlpha);
        return ColorUtils.compositeColors(foreground, backgroundPaint.getColor());
    }

    private void drawBackgroundRect(Canvas canvas, float left, float top, float right, float bottom) {
        if (right <= left || bottom <= top) return;
        if (collectingVertices) {
            addVerticesRect(left, top, right, bottom, backgroundPaint.getColor());
        } else {
            canvas.drawRect(left, top, right, bottom, backgroundPaint);
        }
    }

    private void beginVertices() {
        verticesCount = 0;
        vertexColorsCount = 0;
        vertexIndicesCount = 0;
    }

    private void addVerticesRect(float left, float top, float right, float bottom, int color) {
        if (right <= left || bottom <= top) return;
        ensureVerticesCapacity();
        final int baseVertex = vertexColorsCount;

        vertices[verticesCount++] = left;
        vertices[verticesCount++] = top;
        vertices[verticesCount++] = right;
        vertices[verticesCount++] = top;
        vertices[verticesCount++] = left;
        vertices[verticesCount++] = bottom;
        vertices[verticesCount++] = right;
        vertices[verticesCount++] = bottom;

        vertexColors[vertexColorsCount++] = color;
        vertexColors[vertexColorsCount++] = color;
        vertexColors[vertexColorsCount++] = color;
        vertexColors[vertexColorsCount++] = color;

        vertexIndices[vertexIndicesCount++] = (short) baseVertex;
        vertexIndices[vertexIndicesCount++] = (short) (baseVertex + 1);
        vertexIndices[vertexIndicesCount++] = (short) (baseVertex + 2);
        vertexIndices[vertexIndicesCount++] = (short) (baseVertex + 2);
        vertexIndices[vertexIndicesCount++] = (short) (baseVertex + 1);
        vertexIndices[vertexIndicesCount++] = (short) (baseVertex + 3);
    }

    private void ensureVerticesCapacity() {
        if (verticesCount + VERTEX_FLOATS_PER_RECT > vertices.length) {
            final float[] newVertices = new float[vertices.length * 2];
            System.arraycopy(vertices, 0, newVertices, 0, verticesCount);
            vertices = newVertices;
        }
        if (vertexColorsCount + VERTEX_COLORS_PER_RECT > vertexColors.length) {
            final int[] newColors = new int[vertexColors.length * 2];
            System.arraycopy(vertexColors, 0, newColors, 0, vertexColorsCount);
            vertexColors = newColors;
        }
        if (vertexIndicesCount + VERTEX_INDICES_PER_RECT > vertexIndices.length) {
            final short[] newIndices = new short[vertexIndices.length * 2];
            System.arraycopy(vertexIndices, 0, newIndices, 0, vertexIndicesCount);
            vertexIndices = newIndices;
        }
    }

    private void flushVertices(Canvas canvas) {
        if (verticesCount == 0) return;
        canvas.drawVertices(
                Canvas.VertexMode.TRIANGLES,
                verticesCount,
                vertices,
                0,
                null,
                0,
                vertexColors,
                0,
                vertexIndices,
                0,
                vertexIndicesCount,
                verticesPaint
        );
    }

    private boolean hasAbove(View view, int index) {
        if (view == null || index > 0) return false;
        final RecyclerView.Adapter adapter = parent.getAdapter();
        final int position = parent.getChildAdapterPosition(view);
        return adapter != null && position > 0 && isViewTypeSection.run(adapter.getItemViewType(position - 1));
    }

    private boolean hasBelow(View view, int index) {
        if (view == null || index < parent.getChildCount() - 1) return false;
        final RecyclerView.Adapter adapter = parent.getAdapter();
        final int position = parent.getChildAdapterPosition(view);
        return adapter != null && position >= 0 && position < adapter.getItemCount() - 1 && isViewTypeSection.run(adapter.getItemViewType(position + 1));
    }

    @Override
    public void onDrawOver(@NonNull Canvas canvas, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.State state) {
        if (recyclerView != parent || radius <= 0.0f) return;
        updateBackgroundColor();
        ensureBitmaps();
        collectingCornerVertices = drawBackground && drawingMode == RecyclerListView.SectionsDrawingMode.DRAW_VERTICES;
        if (collectingCornerVertices) beginCornerVertices();
        if (parent.isAnimating()) {
            drawAnimatedCorners(canvas);
        } else {
            drawStableCorners(canvas);
        }
        drawForcedSectionsCorners(canvas);
        if (collectingCornerVertices) {
            collectingCornerVertices = false;
            flushCornerVertices(canvas);
        }
    }

    private void drawStableCorners(Canvas canvas) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            final View child = parent.getChildAt(i);
            final RecyclerView.ViewHolder holder = parent.getChildViewHolder(child);
            final int position = parent.getChildAdapterPosition(child);
            if (child == parent.getEmptyView() || child.getVisibility() != View.VISIBLE || child.getAlpha() <= 0.0f ||
                    position == RecyclerView.NO_POSITION || (holder != null && holder.isRemoved()) ||
                    !isSectionItem(child) || parent.isInsideForcedSection(position)) continue;
            final float left = child.getX();
            final float right = left + child.getWidth();
            if (!isSectionPosition(position - 1) && !RecyclerListView.hasSectionContentAboveTop(child)) drawTopCorners(canvas, left, right, RecyclerListView.top(child), 1.0f);
            if (!isSectionPosition(position + 1)) drawBottomCorners(canvas, left, right, RecyclerListView.bottom(child), 1.0f);
        }
    }

    private void drawAnimatedCorners(Canvas canvas) {
        final float left = parent.getPaddingLeft() + padding;
        final float right = parent.getWidth() - parent.getPaddingRight() - padding;
        final float radiusMergeDistance = radius * 0.2f;
        for (int i = 0; i < animatedGroupsCount; i += 4) {
            final float from = animatedGroups[i];
            final float to = animatedGroups[i + 1];
            float topRadiusProgress = animatedGroups[i + 2];
            float bottomRadiusProgress = animatedGroups[i + 3];
            if (radiusMergeDistance > 0.0f && i > 0) {
                final float gap = from - animatedGroups[i - 3];
                topRadiusProgress = Math.min(topRadiusProgress, Math.max(0.0f, Math.min(1.0f, gap / radiusMergeDistance)));
            }
            if (radiusMergeDistance > 0.0f && i + 4 < animatedGroupsCount) {
                final float gap = animatedGroups[i + 4] - to;
                bottomRadiusProgress = Math.min(bottomRadiusProgress, Math.max(0.0f, Math.min(1.0f, gap / radiusMergeDistance)));
            }
            if (!isTopWithContentAbove(from)) {
                drawTopCorners(canvas, left, right, from, topRadiusProgress);
            }
            drawBottomCorners(canvas, left, right, to, bottomRadiusProgress);
        }
    }

    /** Whether a section item with content above its top starts at {@code top}, see RecyclerListView.SectionContentAboveTop. */
    private boolean isTopWithContentAbove(float top) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            final View child = parent.getChildAt(i);
            if (RecyclerListView.hasSectionContentAboveTop(child) && Math.abs(RecyclerListView.top(child) - top) < 1.0f) {
                return true;
            }
        }
        return false;
    }

    private void drawForcedSectionsCorners(Canvas canvas) {
        if (parent.forcedSections != null) {
            for (int i = 0; i < parent.forcedSections.size(); i++) {
                final long section = parent.forcedSections.get(i);
                final View first = parent.findViewByPosition(AndroidUtilities.unpackA(section));
                final View last = parent.findViewByPosition(AndroidUtilities.unpackB(section));
                if (first != null && !RecyclerListView.hasSectionContentAboveTop(first)) drawTopCorners(canvas, first.getX(), first.getX() + first.getWidth(), RecyclerListView.top(first), 1.0f);
                if (last != null) drawBottomCorners(canvas, last.getX(), last.getX() + last.getWidth(), RecyclerListView.bottom(last), 1.0f);
            }
        }
    }

    private void ensureBitmaps() {
        final int size = Math.max(1, (int) Math.ceil(radius));
        if (cornerBitmap != null && bitmapSize == size && bitmapRadius == radius) return;
        bitmapSize = size;
        bitmapRadius = radius;
        cornerBitmap = Bitmap.createBitmap(size * 2, size * 2, Bitmap.Config.ARGB_8888);
        final Canvas bitmapCanvas = new Canvas(cornerBitmap);
        bitmapCanvas.drawColor(backgroundPaint.getColor());
        final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        bitmapCanvas.drawCircle(size, size, radius, clearPaint);
        cornerBitmapShader = new BitmapShader(cornerBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        cornerVerticesPaint.setShader(cornerBitmapShader);
    }

    private boolean isSectionPosition(int position) {
        final RecyclerView.Adapter adapter = parent.getAdapter();
        if (position < 0 || adapter == null || position >= adapter.getItemCount()) return false;
        final View child = parent.findViewByPosition(position);
        if (child != null) return isSectionItem(child) && !parent.isInsideForcedSection(position);
        return isViewTypeSection.run(adapter.getItemViewType(position)) && !parent.isInsideForcedSection(position);
    }

    private void drawTopCorners(Canvas canvas, float left, float right, float top, float radiusProgress) {
        if (radiusProgress <= 0.0f) return;
        final float scaledSize = bitmapSize * Math.min(1.0f, radiusProgress);
        drawCorner(canvas, 0, left, top, left + scaledSize, top + scaledSize);
        drawCorner(canvas, 1, right - scaledSize, top, right, top + scaledSize);
    }

    private void drawBottomCorners(Canvas canvas, float left, float right, float bottom, float radiusProgress) {
        if (radiusProgress <= 0.0f) return;
        final float scaledSize = bitmapSize * Math.min(1.0f, radiusProgress);
        drawCorner(canvas, 2, left, bottom - scaledSize, left + scaledSize, bottom);
        drawCorner(canvas, 3, right - scaledSize, bottom - scaledSize, right, bottom);
    }

    private void drawCorner(Canvas canvas, int corner, float left, float top, float right, float bottom) {
        final int srcLeft = (corner & 1) == 0 ? 0 : bitmapSize;
        final int srcTop = corner < 2 ? 0 : bitmapSize;
        if (collectingCornerVertices) {
            addCornerVertices(left, top, right, bottom, srcLeft, srcTop, srcLeft + bitmapSize, srcTop + bitmapSize);
            return;
        }
        cornerSrc.set(srcLeft, srcTop, srcLeft + bitmapSize, srcTop + bitmapSize);
        cornerDst.set(left, top, right, bottom);
        canvas.drawBitmap(cornerBitmap, cornerSrc, cornerDst, bitmapPaint);
    }

    private void beginCornerVertices() {
        cornerVerticesCount = 0;
        cornerIndicesCount = 0;
    }

    private void addCornerVertices(
            float left, float top, float right, float bottom,
            float texLeft, float texTop, float texRight, float texBottom
    ) {
        ensureCornerVerticesCapacity();
        final int baseVertex = cornerVerticesCount >> 1;

        cornerVertices[cornerVerticesCount] = left;
        cornerTexs[cornerVerticesCount++] = texLeft;
        cornerVertices[cornerVerticesCount] = top;
        cornerTexs[cornerVerticesCount++] = texTop;
        cornerVertices[cornerVerticesCount] = right;
        cornerTexs[cornerVerticesCount++] = texRight;
        cornerVertices[cornerVerticesCount] = top;
        cornerTexs[cornerVerticesCount++] = texTop;
        cornerVertices[cornerVerticesCount] = left;
        cornerTexs[cornerVerticesCount++] = texLeft;
        cornerVertices[cornerVerticesCount] = bottom;
        cornerTexs[cornerVerticesCount++] = texBottom;
        cornerVertices[cornerVerticesCount] = right;
        cornerTexs[cornerVerticesCount++] = texRight;
        cornerVertices[cornerVerticesCount] = bottom;
        cornerTexs[cornerVerticesCount++] = texBottom;

        cornerIndices[cornerIndicesCount++] = (short) baseVertex;
        cornerIndices[cornerIndicesCount++] = (short) (baseVertex + 1);
        cornerIndices[cornerIndicesCount++] = (short) (baseVertex + 2);
        cornerIndices[cornerIndicesCount++] = (short) (baseVertex + 2);
        cornerIndices[cornerIndicesCount++] = (short) (baseVertex + 1);
        cornerIndices[cornerIndicesCount++] = (short) (baseVertex + 3);
    }

    private void ensureCornerVerticesCapacity() {
        if (cornerVerticesCount + VERTEX_FLOATS_PER_RECT > cornerVertices.length) {
            final int newLength = cornerVertices.length * 2;
            final float[] newVertices = new float[newLength];
            final float[] newTexs = new float[newLength];
            System.arraycopy(cornerVertices, 0, newVertices, 0, cornerVerticesCount);
            System.arraycopy(cornerTexs, 0, newTexs, 0, cornerVerticesCount);
            cornerVertices = newVertices;
            cornerTexs = newTexs;
        }
        if (cornerIndicesCount + VERTEX_INDICES_PER_RECT > cornerIndices.length) {
            final short[] newIndices = new short[cornerIndices.length * 2];
            System.arraycopy(cornerIndices, 0, newIndices, 0, cornerIndicesCount);
            cornerIndices = newIndices;
        }
    }

    private void flushCornerVertices(Canvas canvas) {
        if (cornerVerticesCount == 0) return;
        canvas.drawVertices(
                Canvas.VertexMode.TRIANGLES,
                cornerVerticesCount,
                cornerVertices,
                0,
                cornerTexs,
                0,
                null,
                0,
                cornerIndices,
                0,
                cornerIndicesCount,
                cornerVerticesPaint
        );
    }
}
