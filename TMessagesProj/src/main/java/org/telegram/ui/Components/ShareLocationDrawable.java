/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

public class ShareLocationDrawable extends Drawable {

    private long lastUpdateTime = 0;
    private float progress[] = new float[] {0.0f, -0.5f};
    private Drawable drawable;
    private Drawable drawableLeft;
    private Drawable drawableRight;
    private boolean isSmall;

    public ShareLocationDrawable(Context context, boolean small) {
        isSmall = small;
        if (small) {
            drawable = context.getResources().getDrawable(R.drawable.smallanimationpin);
            drawableLeft = context.getResources().getDrawable(R.drawable.smallanimationpinleft);
            drawableRight = context.getResources().getDrawable(R.drawable.smallanimationpinright);
        } else {
            drawable = context.getResources().getDrawable(R.drawable.animationpin);
            drawableLeft = context.getResources().getDrawable(R.drawable.animationpinleft);
            drawableRight = context.getResources().getDrawable(R.drawable.animationpinright);
        }
    }

    private void update() {
        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        lastUpdateTime = newTime;
        if (dt > 16) {
            dt = 16;
        }
        for (int a = 0; a < 2; a++) {
            if (progress[a] >= 1.0f) {
                progress[a] = 0.0f;
            }
            progress[a] += dt / 1300.0f;
            if (progress[a] > 1.0f) {
                progress[a] = 1.0f;
            }
        }
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        int size = AndroidUtilities.dp(isSmall ? 30 : 120);
        int y = getBounds().top + (getIntrinsicHeight() - size) / 2;
        int x = getBounds().left + (getIntrinsicWidth() - size) / 2;

        drawable.setBounds(x, y, x + drawable.getIntrinsicWidth(), y + drawable.getIntrinsicHeight());
        drawable.draw(canvas);

        for (int a = 0; a < 2; a++) {
            if (progress[a] < 0) {
                continue;
            }
            float scale = 0.5f + 0.5f * progress[a];
            int w = AndroidUtilities.dp((isSmall ? 2.5f : 5) * scale);
            int h = AndroidUtilities.dp((isSmall ? 6.5f : 18) * scale);
            int tx = AndroidUtilities.dp((isSmall ? 6.0f : 15) * progress[a]);
            float alpha;
            if (progress[a] < 0.5f) {
                alpha = progress[a] / 0.5f;
            } else {
                alpha = 1.0f - (progress[a] - 0.5f) / 0.5f;
            }

            int cx = x + AndroidUtilities.dp(isSmall ? 7 : 42) - tx;
            int cy = y + drawable.getIntrinsicHeight() / 2 - (isSmall ? 0 : AndroidUtilities.dp(7));

            drawableLeft.setAlpha((int) (alpha * 255));
            drawableLeft.setBounds(cx - w, cy - h, cx + w, cy + h);
            drawableLeft.draw(canvas);

            cx = x + drawable.getIntrinsicWidth() - AndroidUtilities.dp(isSmall ? 7 : 42) + tx;

            drawableRight.setAlpha((int) (alpha * 255));
            drawableRight.setBounds(cx - w, cy - h, cx + w, cy + h);
            drawableRight.draw(canvas);
        }

        update();
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        drawable.setColorFilter(cf);
        drawableLeft.setColorFilter(cf);
        drawableRight.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return 0;
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(isSmall ? 40 : 120);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(isSmall ? 40 : 180);
    }
}
