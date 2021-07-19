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
import android.text.StaticLayout;

import org.telegram.messenger.AndroidUtilities;

public class LinkPath extends Path {

    private StaticLayout currentLayout;
    private int currentLine;
    private float lastTop = -1;
    private float heightOffset;
    private boolean useRoundRect;
    private RectF rect;
    private boolean allowReset = true;
    private int baselineShift;
    private int lineHeight;

    public LinkPath() {
        super();
    }

    public LinkPath(boolean roundRect) {
        super();
        useRoundRect = roundRect;
    }

    public void setCurrentLayout(StaticLayout layout, int start, float yOffset) {
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
            if (rect == null) {
                rect = new RectF();
            }
            rect.set(left - AndroidUtilities.dp(4), y, right + AndroidUtilities.dp(4), y2);
            super.addRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), dir);
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
    }
}
