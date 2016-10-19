/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class SendingFileExDrawable extends Drawable {

    private boolean isChat = false;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long lastUpdateTime = 0;
    private boolean started = false;
    private float progress;

    public SendingFileExDrawable() {
        super();
        paint.setColor(Theme.ACTION_BAR_SUBTITLE_COLOR);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(2));
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setIsChat(boolean value) {
        isChat = value;
    }

    private void update() {
        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        lastUpdateTime = newTime;
        if (dt > 50) {
            dt = 50;
        }
        progress += dt / 500.0f;
        while (progress > 1.0f) {
            progress -= 1.0f;
        }
        invalidateSelf();
    }

    public void start() {
        lastUpdateTime = System.currentTimeMillis();
        started = true;
        invalidateSelf();
    }

    public void stop() {
        started = false;
    }

    @Override
    public void draw(Canvas canvas) {

        for (int a = 0; a < 3; a++) {
            if (a == 0) {
                paint.setAlpha((int) (255 * progress));
            } else if (a == 2) {
                paint.setAlpha((int) (255 * (1.0f - progress)));
            } else {
                paint.setAlpha(255);
            }
            float side = AndroidUtilities.dp(5) * a + AndroidUtilities.dp(5) * progress;
            canvas.drawLine(side, AndroidUtilities.dp(isChat ? 3 : 4), side + AndroidUtilities.dp(4), AndroidUtilities.dp(isChat ? 7 : 8), paint);
            canvas.drawLine(side, AndroidUtilities.dp(isChat ? 11 : 12), side + AndroidUtilities.dp(4), AndroidUtilities.dp(isChat ? 7 : 8), paint);
        }

        if (started) {
            update();
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
        return AndroidUtilities.dp(18);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(14);
    }
}
