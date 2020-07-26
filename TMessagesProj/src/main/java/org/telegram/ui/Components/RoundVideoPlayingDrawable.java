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
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class RoundVideoPlayingDrawable extends Drawable {

    private long lastUpdateTime = 0;
    private boolean started = false;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress1 = 0.47f;
    private float progress2 = 0.0f;
    private float progress3 = 0.32f;
    private int progress1Direction = 1;
    private int progress2Direction = 1;
    private int progress3Direction = 1;
    private View parentView;

    public RoundVideoPlayingDrawable(View view) {
        super();
        parentView = view;
    }

    private void update() {
        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        lastUpdateTime = newTime;
        if (dt > 50) {
            dt = 50;
        }
        progress1 += dt / 300.0f * progress1Direction;
        if (progress1 > 1.0f) {
            progress1Direction = -1;
            progress1 = 1.0f;
        } else if (progress1 < 0.0f) {
            progress1Direction = 1;
            progress1 = 0.0f;
        }

        progress2 += dt / 310.0f * progress2Direction;
        if (progress2 > 1.0f) {
            progress2Direction = -1;
            progress2 = 1.0f;
        } else if (progress2 < 0.0f) {
            progress2Direction = 1;
            progress2 = 0.0f;
        }

        progress3 += dt / 320.0f * progress3Direction;
        if (progress3 > 1.0f) {
            progress3Direction = -1;
            progress3 = 1.0f;
        } else if (progress3 < 0.0f) {
            progress3Direction = 1;
            progress3 = 0.0f;
        }

        parentView.invalidate();
    }

    public void start() {
        if (started) {
            return;
        }
        lastUpdateTime = System.currentTimeMillis();
        started = true;
        parentView.invalidate();
    }

    public void stop() {
        if (!started) {
            return;
        }
        started = false;
    }

    @Override
    public void draw(Canvas canvas) {
        paint.setColor(Theme.getColor(Theme.key_chat_serviceText));
        int x = getBounds().left;
        int y = getBounds().top;
        for (int a = 0; a < 3; a++) {
            canvas.drawRect(x + AndroidUtilities.dp(2), y + AndroidUtilities.dp(2 + 7 * progress1), x + AndroidUtilities.dp(4), y + AndroidUtilities.dp(10), paint);
            canvas.drawRect(x + AndroidUtilities.dp(5), y + AndroidUtilities.dp(2 + 7 * progress2), x + AndroidUtilities.dp(7), y + AndroidUtilities.dp(10), paint);
            canvas.drawRect(x + AndroidUtilities.dp(8), y + AndroidUtilities.dp(2 + 7 * progress3), x + AndroidUtilities.dp(10), y + AndroidUtilities.dp(10), paint);
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
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(12);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(12);
    }
}
