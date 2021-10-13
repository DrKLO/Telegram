/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class RecordStatusDrawable extends StatusDrawable {

    private boolean isChat = false;
    private long lastUpdateTime = 0;
    private boolean started = false;
    private RectF rect = new RectF();
    private float progress;
    int alpha = 255;

    Paint currentPaint;

    public RecordStatusDrawable(boolean createPaint) {
        if (createPaint) {
            currentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            currentPaint.setStyle(Paint.Style.STROKE);
            currentPaint.setStrokeCap(Paint.Cap.ROUND);
            currentPaint.setStrokeWidth(AndroidUtilities.dp(2));
        }
    }

    public void setIsChat(boolean value) {
        isChat = value;
    }

    @Override
    public void setColor(int color) {
        if (currentPaint != null) {
            currentPaint.setColor(color);
        }
    }

    private void update() {
        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        lastUpdateTime = newTime;
        if (dt > 50) {
            dt = 50;
        }
        progress += dt / 800.0f;
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
        Paint paint = currentPaint == null ? Theme.chat_statusRecordPaint : currentPaint;
        if (paint.getStrokeWidth() != AndroidUtilities.dp(2)) {
            paint.setStrokeWidth(AndroidUtilities.dp(2));
        }
        canvas.save();
        canvas.translate(0, getIntrinsicHeight() / 2 + AndroidUtilities.dp(isChat ? 1 : 2));
        for (int a = 0; a < 4; a++) {
            if (a == 0) {
                paint.setAlpha((int) (alpha * progress));
            } else if (a == 3) {
                paint.setAlpha((int) (alpha * (1.0f - progress)));
            } else {
                paint.setAlpha(alpha);
            }
            float side = AndroidUtilities.dp(4) * a + AndroidUtilities.dp(4) * progress;
            rect.set(-side, -side, side, side);
            canvas.drawArc(rect, -15, 30, false, paint);
        }
        canvas.restore();
        if (started) {
            update();
        }
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
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
