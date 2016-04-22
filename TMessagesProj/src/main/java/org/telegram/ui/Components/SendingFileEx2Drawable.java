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

public class SendingFileEx2Drawable extends Drawable {

    private boolean isChat = false;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long lastUpdateTime = 0;
    private boolean started = false;
    private float progress;

    public SendingFileEx2Drawable() {
        super();
        paint.setColor(Theme.ACTION_BAR_SUBTITLE_COLOR);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(3));
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
        progress += dt / 1000.0f;
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
        int start = (int) (progress <= 0.5f ? AndroidUtilities.dp(1) : AndroidUtilities.dp(11) * (progress - 0.5f) * 2);
        int end = (int) (progress >= 0.5f ? AndroidUtilities.dp(11) : AndroidUtilities.dp(11) * progress * 2);
        canvas.drawLine(start, AndroidUtilities.dp(isChat ? 11 : 12), end, AndroidUtilities.dp(isChat ? 11 : 12), paint);

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
