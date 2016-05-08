/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.graphics.Path;
import android.text.StaticLayout;

public class LinkPath extends Path {

    private StaticLayout currentLayout;
    private int currentLine;
    private float lastTop = -1;
    private float heightOffset;

    public void setCurrentLayout(StaticLayout layout, int start, float yOffset) {
        currentLayout = layout;
        currentLine = layout.getLineForOffset(start);
        lastTop = -1;
        heightOffset = yOffset;
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
        if (left >= lineRight) {
            return;
        }
        if (right > lineRight) {
            right = lineRight;
        }
        if (left < lineLeft) {
            left = lineLeft;
        }
        super.addRect(left, top, right, bottom, dir);
    }
}
