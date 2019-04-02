/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.animation.DecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;

public class CloseProgressDrawable extends Drawable {

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long lastFrameTime;
    private int currentAnimationTime;
    private DecelerateInterpolator interpolator = new DecelerateInterpolator();
    private int currentSegment;

    public CloseProgressDrawable() {
        super();
        paint.setColor(0xff757575);
        paint.setStrokeWidth(AndroidUtilities.dp(2));
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    public void draw(Canvas canvas) {
        long newTime = System.currentTimeMillis();
        if (lastFrameTime != 0) {
            long dt = (newTime - lastFrameTime);
            currentAnimationTime += dt;
            if (currentAnimationTime > 200) {
                currentAnimationTime = 0;
                currentSegment++;
                if (currentSegment == 4) {
                    currentSegment -= 4;
                }
            }
        }

        canvas.save();
        canvas.translate(getIntrinsicWidth() / 2, getIntrinsicHeight() / 2);
        canvas.rotate(45);
        paint.setAlpha(255 - (currentSegment % 4) * 40);
        canvas.drawLine(-AndroidUtilities.dp(8), 0, 0, 0, paint);
        paint.setAlpha(255 - ((currentSegment + 1) % 4) * 40);
        canvas.drawLine(0, -AndroidUtilities.dp(8), 0, 0, paint);
        paint.setAlpha(255 - ((currentSegment + 2) % 4) * 40);
        canvas.drawLine(0, 0, AndroidUtilities.dp(8), 0, paint);
        paint.setAlpha(255 - ((currentSegment + 3) % 4) * 40);
        canvas.drawLine(0, 0, 0, AndroidUtilities.dp(8), paint);
        canvas.restore();

        lastFrameTime = newTime;
        invalidateSelf();
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(ColorFilter cf) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(24);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(24);
    }
}
