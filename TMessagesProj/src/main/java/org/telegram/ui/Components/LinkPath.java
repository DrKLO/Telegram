/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;

public class LinkPath extends Path {

    private Layout currentLayout;
    private int currentLine;
    private float lastTop = -1;
    private float heightOffset;
    private boolean useRoundRect;
    private boolean allowReset = true;
    private int baselineShift;
    private int lineHeight;
    private ArrayList<RectF> rects = new ArrayList<>();

    private final int radius = AndroidUtilities.dp(4);
    private final int halfRadius = radius >> 1;

    public LinkPath() {
        super();
    }

    public LinkPath(boolean roundRect) {
        super();
        useRoundRect = roundRect;
    }

    public void setCurrentLayout(Layout layout, int start, float yOffset) {
        currentLayout = layout;
        currentLine = layout.getLineForOffset(start);
        lastTop = -1;
        heightOffset = yOffset;
        if (Build.VERSION.SDK_INT >= 28) {
            int lineCount = layout.getLineCount();
            if (lineCount > 0) {
                lineHeight = layout.getLineBottom(lineCount - 1) - layout.getLineTop(lineCount - 1);
            }
        }
    }

    public void setAllowReset(boolean value) {
        allowReset = value;
    }

    public void setUseRoundRect(boolean value) {
        useRoundRect = value;
    }

    public boolean isUsingRoundRect() {
        return useRoundRect;
    }

    public void setBaselineShift(int value) {
        baselineShift = value;
    }

    @Override
    public void addRect(float left, float top, float right, float bottom, Direction dir) {
        top += heightOffset;
        bottom += heightOffset;
        if (lastTop == -1) {
            lastTop = top;
        } else if (lastTop != top) {
            lastTop = top;
            currentLine++;
        }
        float lineRight = currentLayout.getLineRight(currentLine);
        float lineLeft = currentLayout.getLineLeft(currentLine);
        if (left >= lineRight || left <= lineLeft && right <= lineLeft) {
            return;
        }
        if (right > lineRight) {
            right = lineRight;
        }
        if (left < lineLeft) {
            left = lineLeft;
        }
        float y = top;
        float y2;
        if (Build.VERSION.SDK_INT >= 28) {
            y2 = bottom;
            if (bottom - top > lineHeight) {
                y2 = heightOffset + (bottom != currentLayout.getHeight() ? (currentLayout.getLineBottom(currentLine) - currentLayout.getSpacingAdd()) : 0);
            }
        } else {
            y2 = bottom - (bottom != currentLayout.getHeight() ? currentLayout.getSpacingAdd() : 0);
        }
        if (baselineShift < 0) {
            y2 += baselineShift;
        } else if (baselineShift > 0) {
            y += baselineShift;
        }
        if (useRoundRect) {
            RectF rect = new RectF();
            rect.set(left - halfRadius, y, right + halfRadius, y2);
            rects.add(rect);
        } else {
            super.addRect(left, y, right, y2, dir);
        }
    }

    @Override
    public void reset() {
        if (!allowReset) {
            return;
        }
        super.reset();
        rects.clear();
    }

    private boolean containsPoint(float x, float y) {
        for (RectF rect : rects) {
            if (rect.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    public void onPathEnd() {
        if (useRoundRect) {
            super.reset();
            final int count = rects.size();
            for (int i = 0; i < count; ++i) {
                float[] radii = new float[8];

                RectF rect = rects.get(i);

                radii[0] = radii[1] = containsPoint(rect.left, rect.top - radius) ? 0 : radius; // top left
                radii[2] = radii[3] = containsPoint(rect.right, rect.top - radius) ? 0 : radius; // top right
                radii[4] = radii[5] = containsPoint(rect.right, rect.bottom + radius) ? 0 : radius; // bottom right
                radii[6] = radii[7] = containsPoint(rect.left, rect.bottom + radius) ? 0 : radius; // bottom left

                super.addRoundRect(rect, radii, Direction.CW);
            }
        }
    }
}
