package org.telegram.ui.Components;

import android.graphics.Path;

import org.telegram.messenger.AndroidUtilities;

public class PhotoBubbleClip extends Path {

    private int lastCx, lastCy, lastR;

    public void setBounds(int cx, int cy, int r) {
        if (lastCx == cx && lastCy == cy && lastR == r) return;

        rewind();
        AndroidUtilities.rectTmp.set(cx - r, cy - r, cx + r, cy + r);
        arcTo(AndroidUtilities.rectTmp, -180, 270, false);
        final float b = cy + r;
        final float x = r / 81.0f;
        cubicTo(cx - 13*x, b, cx - 25*x, b - 3*x, cx - 36f*x, b - 8.42f*x);
        cubicTo(cx - 52*x, b - x, cx - 56.5f*x, b - x, cx - 78.02f*x, b - x);
        cubicTo(cx - 80*x, b - x, cx - 81*x, b - 3*x, cx - 79.52f*x, b - 4.5f*x);
        cubicTo(cx - 78*x, b - 6*x, cx - 63.73f*x, b - 15*x, cx - 63.73f*x, b - 31*x);
        cubicTo(cx - 74.5f*x, b - 44.75f*x, cx - r, cy+18.87f*x, cx - r, cy);
        close();

        lastCx = cx;
        lastCy = cy;
        lastR = r;
    }

}
