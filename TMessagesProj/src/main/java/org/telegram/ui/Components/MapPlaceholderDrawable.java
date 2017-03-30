/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;

public class MapPlaceholderDrawable extends Drawable {

    private Paint paint;
    private Paint linePaint;

    public MapPlaceholderDrawable() {
        super();
        paint = new Paint();
        paint.setColor(0xffded7d6);
        linePaint = new Paint();
        linePaint.setColor(0xffc6bfbe);
        linePaint.setStrokeWidth(AndroidUtilities.dp(1));
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawRect(getBounds(), paint);
        int gap = AndroidUtilities.dp(9);
        int xcount = getBounds().width() / gap;
        int ycount = getBounds().height() / gap;
        int x = getBounds().left;
        int y = getBounds().top;
        for (int a = 0; a < xcount; a++) {
            canvas.drawLine(x + gap * (a + 1), y, x + gap * (a + 1), y + getBounds().height(), linePaint);
        }
        for (int a = 0; a < ycount; a++) {
            canvas.drawLine(x, y + gap * (a + 1), x + getBounds().width(), y + gap * (a + 1), linePaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(ColorFilter cf) {

    }

    @Override
    public int getOpacity() {
        return 0;
    }

    @Override
    public int getIntrinsicWidth() {
        return 0;
    }

    @Override
    public int getIntrinsicHeight() {
        return 0;
    }
}
