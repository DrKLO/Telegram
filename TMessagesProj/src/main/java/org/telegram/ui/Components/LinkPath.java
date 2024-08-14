/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.CornerPathEffect;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.text.Layout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;

public class LinkPath extends CornerPath {

    private Layout currentLayout;
    private int currentLine;
    private float lastTop = -1;
    private float xOffset, yOffset;
    private boolean useRoundRect;
    private boolean allowReset = true;
    private int baselineShift;
    private int lineHeight;

    public float centerX, centerY;

    public static int getRadius() {
        return AndroidUtilities.dp(5);
    }

    private static CornerPathEffect roundedEffect;
    private static int roundedEffectRadius;
    public static CornerPathEffect getRoundedEffect() {
        if (roundedEffect == null || roundedEffectRadius != getRadius()) {
            roundedEffect = new CornerPathEffect(roundedEffectRadius = getRadius());
        }
        return roundedEffect;
    }

    public LinkPath() {
        super();
        useCornerPathImplementation = false;
    }

    public LinkPath(boolean roundRect) {
        super();
        useRoundRect = roundRect;
        useCornerPathImplementation = false;
    }

    public void setCurrentLayout(Layout layout, int start, float yOffset) {
        setCurrentLayout(layout, start, 0, yOffset);
    }

    public void setCurrentLayout(Layout layout, int start, float xOffset, float yOffset) {
        if (layout == null) {
            currentLayout = null;
            currentLine = 0;
            lastTop = -1;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
            return;
        }
        currentLayout = layout;
        currentLine = layout.getLineForOffset(start);
        lastTop = -1;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
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

    private float insetVert, insetHoriz;
    public void setInset(float insetVert, float insetHoriz) {
        this.insetVert = insetVert;
        this.insetHoriz = insetHoriz;
    }

    private float minX = Float.MAX_VALUE, maxX, minY = Float.MAX_VALUE, maxY;

    @Override
    public void addRect(float left, float top, float right, float bottom, @NonNull Direction dir) {
        if (currentLayout == null) {
            superAddRect(left, top, right, bottom, dir);
            return;
        }
        try {
            top += yOffset;
            bottom += yOffset;
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
            left += xOffset;
            right += xOffset;
            float y = top;
            float y2;
            if (Build.VERSION.SDK_INT >= 28) {
                y2 = bottom;
                if (bottom - top > lineHeight) {
                    y2 = yOffset + (bottom != currentLayout.getHeight() ? (currentLayout.getLineBottom(currentLine) - currentLayout.getSpacingAdd()) : 0);
                }
            } else {
                y2 = bottom - (bottom != currentLayout.getHeight() ? currentLayout.getSpacingAdd() : 0);
            }
            if (baselineShift < 0) {
                y2 += baselineShift;
            } else if (baselineShift > 0) {
                y += baselineShift;
            }
            centerX = (right + left) / 2;
            centerY = (y2 + y) / 2;
            if (useRoundRect) {
//            final CharSequence text = currentLayout.getText();
//            int startOffset = currentLayout.getOffsetForHorizontal(currentLine, left), endOffset = currentLayout.getOffsetForHorizontal(currentLine, right) + 1;
                boolean startsWithWhitespace = false; // startOffset >= 0 && startOffset < text.length() && text.charAt(startOffset) == ' ';
                boolean endsWithWhitespace = false; // endOffset >= 0 && endOffset < text.length() && text.charAt(endOffset) == ' ';
                superAddRect(left - (startsWithWhitespace ? 0 : getRadius() / 2f), y, right + (endsWithWhitespace ? 0 : getRadius() / 2f), y2, dir);
            } else {
                superAddRect(left, y, right, y2, dir);
            }
        } catch (Exception e) {

        }
    }

    private void superAddRect(float l, float t, float r, float b, Path.Direction d) {
        l -= insetHoriz;
        t -= insetVert;
        r += insetHoriz;
        b += insetVert;
        minX = Math.min(minX, Math.min(l, r));
        minY = Math.min(minY, Math.min(t, b));
        maxX = Math.max(maxX, Math.max(l, r));
        maxY = Math.max(maxY, Math.max(t, b));
        super.addRect(l, t, r, b, d);
    }

    public void getBounds(RectF bounds) {
        bounds.set(minX, minY, maxX, maxY);
    }

    @Override
    public void reset() {
        if (!allowReset) {
            return;
        }
        super.reset();
    }
}
