package org.telegram.ui.Components;

import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;

/**
 * Workaround when using [android.graphics.CornerPathEffect] with a path based on rectangles.
 * [closeRects] must be called when all rects are added.
 *
 * In Android 14, [android.graphics.CornerPathEffect] does not smooth out the lines between intersecting rectangles.
 * https://issuetracker.google.com/issues/318612129
 */
public class CornerPath extends Path {

    private static ArrayList<RectF> recycled;
    private final ArrayList<RectF> rects;
    private boolean isPathCreated = false;
    protected boolean useCornerPathImplementation = true;
    private float rectsUnionDiffDelta = 0f;

    public CornerPath() {
        rects = new ArrayList<>(1);
    }

    public CornerPath(int initialRectsCapacity) {
        rects = new ArrayList<>(initialRectsCapacity);
    }

    @Override
    public void addRect(@NonNull RectF rect, @NonNull Direction dir) {
        if (Build.VERSION.SDK_INT < 34 || !useCornerPathImplementation) {
            super.addRect(rect.left, rect.top, rect.right, rect.bottom, dir);
        } else {
            if (rects.size() > 0 && rects.get(rects.size() - 1).contains(rect)) {
                return;
            }
            if (rects.size() > 0
                    && (Math.abs(rect.top - rects.get(rects.size() - 1).top) <= rectsUnionDiffDelta)
                    && (Math.abs(rect.bottom - rects.get(rects.size() - 1).bottom) <= rectsUnionDiffDelta)) {
                rects.get(rects.size() - 1).union(rect);
            } else {
                RectF rectF;
                if (recycled != null && recycled.size() > 0) {
                    rectF = recycled.remove(0);
                } else {
                    rectF = new RectF();
                }
                rectF.set(rect);
                rects.add(rectF);
            }
            isPathCreated = false;
        }
    }

    @Override
    public void addRect(float left, float top, float right, float bottom, @NonNull Direction dir) {
        if (Build.VERSION.SDK_INT < 34 || !useCornerPathImplementation) {
            super.addRect(left, top, right, bottom, dir);
        } else {
            if (rects.size() > 0 && rects.get(rects.size() - 1).contains(left, top, right, bottom)) {
                return;
            }
            if (rects.size() > 0
                    && (Math.abs(top - rects.get(rects.size() - 1).top) <= rectsUnionDiffDelta)
                    && (Math.abs(bottom - rects.get(rects.size() - 1).bottom) <= rectsUnionDiffDelta)) {
                rects.get(rects.size() - 1).union(left, top, right, bottom);
            } else {
                RectF rectF;
                if (recycled != null && recycled.size() > 0) {
                    rectF = recycled.remove(0);
                } else {
                    rectF = new RectF();
                }
                rectF.set(left, top, right, bottom);
                rects.add(rectF);
            }
            isPathCreated = false;
        }
    }

    @Override
    public void reset() {
        super.reset();
        if (Build.VERSION.SDK_INT >= 34 && useCornerPathImplementation) {
            resetRects();
        }
    }

    @Override
    public void rewind() {
        super.rewind();
        if (Build.VERSION.SDK_INT >= 34 && useCornerPathImplementation) {
            resetRects();
        }
    }

    private void resetRects() {
        if (recycled == null) {
            recycled = new ArrayList<>(rects.size());
        }
        recycled.addAll(rects);
        rects.clear();
        isPathCreated = false;
    }

    public void closeRects() {
        if (Build.VERSION.SDK_INT >= 34 && useCornerPathImplementation && !isPathCreated) {
            createClosedPathsFromRects(rects);
            isPathCreated = true;
        }
    }

    public void setUseCornerPathImplementation(boolean use) {
        useCornerPathImplementation = use;
    }

    public void setRectsUnionDiffDelta(float delta) {
        rectsUnionDiffDelta = delta;
    }

    @RequiresApi(34)
    private void createClosedPathsFromRects(List<RectF> rects) {
        if (rects.isEmpty()) {
            return;
        }
        if (rects.size() == 1) {
            super.addRect(rects.get(0).left, rects.get(0).top, rects.get(0).right, rects.get(0).bottom, Path.Direction.CW);
            return;
        }
        RectF prev = rects.get(0);
        RectF current;
        int lastContourIndex = rects.size() - 1;
        super.moveTo(prev.left, prev.top);
        boolean hasGap = false;
        for (int i = 1; i < rects.size(); i++) {
            current = rects.get(i);
            if (current.width() == 0) {
                continue;
            }
            if (prev.bottom < current.top || prev.left > current.right || prev.right < current.left) {
                // end of the current contour
                hasGap = true;
                lastContourIndex = i;
                break;
            }
            if (prev.left != current.left) {
                super.lineTo(prev.left, current.top);
                super.lineTo(current.left, current.top);
            }
            prev = current;
        }
        super.lineTo(prev.left, prev.bottom);
        super.lineTo(prev.right, prev.bottom);
        for (int i = lastContourIndex - 1; i >= 0; i--) {
            current = rects.get(i);
            if (current.width() == 0) {
                continue;
            }
            if (prev.right != current.right) {
                super.lineTo(prev.right, prev.top);
                super.lineTo(current.right, prev.top);
            }
            prev = current;
        }
        super.lineTo(prev.right, prev.top);
        super.close();
        if (hasGap) {
            createClosedPathsFromRects(rects.subList(lastContourIndex, rects.size()));
        }
    }
}
