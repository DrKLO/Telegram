/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

public class ShareLocationDrawable extends Drawable {

    private long lastUpdateTime = 0;
    private float[] progress = new float[]{0.0f, -0.5f};
    private Drawable drawable;
    private Drawable drawableLeft;
    private Drawable drawableRight;
    private int currentType;

    public static final int TYPE_ADD = 4;
    public static final int TYPE_DISABLE = 5;

    public ShareLocationDrawable(Context context, int type) {
        currentType = type;
        if (type == TYPE_ADD) {
            drawable = context.getResources().getDrawable(R.drawable.filled_extend_location).mutate();
            drawableLeft = context.getResources().getDrawable(R.drawable.smallanimationpinleft).mutate();
            drawableRight = context.getResources().getDrawable(R.drawable.smallanimationpinright).mutate();
        } else if (type == TYPE_DISABLE) {
            drawable = context.getResources().getDrawable(R.drawable.filled_stop_location).mutate();
            drawableLeft = context.getResources().getDrawable(R.drawable.smallanimationpinleft).mutate();
            drawableRight = context.getResources().getDrawable(R.drawable.smallanimationpinright).mutate();
        } else if (type == 3) {
            drawable = context.getResources().getDrawable(R.drawable.nearby_l).mutate();
            drawableLeft = context.getResources().getDrawable(R.drawable.animationpinleft).mutate();
            drawableRight = context.getResources().getDrawable(R.drawable.animationpinright).mutate();
        } else if (type == 2) {
            drawable = context.getResources().getDrawable(R.drawable.nearby_m).mutate();
            drawableLeft = context.getResources().getDrawable(R.drawable.animationpinleft).mutate();
            drawableRight = context.getResources().getDrawable(R.drawable.animationpinright).mutate();
        } else if (type == 1) {
            drawable = context.getResources().getDrawable(R.drawable.smallanimationpin).mutate();
            drawableLeft = context.getResources().getDrawable(R.drawable.smallanimationpinleft).mutate();
            drawableRight = context.getResources().getDrawable(R.drawable.smallanimationpinright).mutate();
        } else {
            drawable = context.getResources().getDrawable(R.drawable.animationpin).mutate();
            drawableLeft = context.getResources().getDrawable(R.drawable.animationpinleft).mutate();
            drawableRight = context.getResources().getDrawable(R.drawable.animationpinright).mutate();
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
        int size;
        int drawableW = drawable.getIntrinsicWidth();
        int drawableH = drawable.getIntrinsicHeight();
        
        if (currentType == TYPE_ADD || currentType == TYPE_DISABLE) {
            size = AndroidUtilities.dp(24);
        } else if (currentType == 3) {
            size = AndroidUtilities.dp(44);
        } else if (currentType == 2) {
            size = AndroidUtilities.dp(32);
        } else if (currentType == 1) {
            size = AndroidUtilities.dp(30);
        } else {
            size = AndroidUtilities.dp(120);
        }
        int y = getBounds().top + (getIntrinsicHeight() - size) / 2;
        int x = getBounds().left + (getIntrinsicWidth() - size) / 2;

        drawable.setBounds(x, y, x + drawableW, y + drawableH);
        drawable.draw(canvas);

        for (int a = 0; a < 2; a++) {
            if (progress[a] < 0) {
                continue;
            }
            float scale = 0.5f + 0.5f * progress[a];
            int w;
            int h;
            int tx;
            int cx;
            int cx2;
            int cy;
            if (currentType == TYPE_ADD || currentType == TYPE_DISABLE) {
                w = AndroidUtilities.dp((2.5f) * scale);
                h = AndroidUtilities.dp((6.5f) * scale);
                tx = AndroidUtilities.dp((6.0f) * progress[a]);

                cx = x + AndroidUtilities.dp(3) - tx;
                cy = y + drawableH / 2 - AndroidUtilities.dp(2);
                cx2 = x + drawableW - AndroidUtilities.dp(3) + tx;
            } else if (currentType == 3) {
                w = AndroidUtilities.dp((5) * scale);
                h = AndroidUtilities.dp((18) * scale);
                tx = AndroidUtilities.dp((15) * progress[a]);

                cx = x + AndroidUtilities.dp(2) - tx;
                cy = y + drawableH / 2 - AndroidUtilities.dp(7);
                cx2 = x + drawableW - AndroidUtilities.dp(2) + tx;
            } else if (currentType == 2) {
                w = AndroidUtilities.dp((5) * scale);
                h = AndroidUtilities.dp((18) * scale);
                tx = AndroidUtilities.dp((15) * progress[a]);

                cx = x + AndroidUtilities.dp(2) - tx;
                cy = y + drawableH / 2;
                cx2 = x + drawableW - AndroidUtilities.dp(2) + tx;
            } else if (currentType == 1) {
                w = AndroidUtilities.dp((2.5f) * scale);
                h = AndroidUtilities.dp((6.5f) * scale);
                tx = AndroidUtilities.dp((6.0f) * progress[a]);

                cx = x + AndroidUtilities.dp(7) - tx;
                cy = y + drawableH / 2;
                cx2 = x + drawableW - AndroidUtilities.dp(7) + tx;
            } else {
                w = AndroidUtilities.dp((5) * scale);
                h = AndroidUtilities.dp((18) * scale);
                tx = AndroidUtilities.dp((15) * progress[a]);

                cx = x + AndroidUtilities.dp(42) - tx;
                cy = y + drawableH / 2 - AndroidUtilities.dp(7);
                cx2 = x + drawableW - AndroidUtilities.dp(42) + tx;
            }
            float alpha;
            if (progress[a] < 0.5f) {
                alpha = progress[a] / 0.5f;
            } else {
                alpha = 1.0f - (progress[a] - 0.5f) / 0.5f;
            }

            drawableLeft.setAlpha((int) (alpha * 255));
            drawableLeft.setBounds(cx - w, cy - h, cx + w, cy + h);
            drawableLeft.draw(canvas);

            drawableRight.setAlpha((int) (alpha * 255));
            drawableRight.setBounds(cx2 - w, cy - h, cx2 + w, cy + h);
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
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        if (currentType == TYPE_ADD || currentType == TYPE_DISABLE) {
            return AndroidUtilities.dp(42);
        } else if (currentType == 3) {
            return AndroidUtilities.dp(100);
        } else if (currentType == 2) {
            return AndroidUtilities.dp(74);
        } else if (currentType == 1) {
            return AndroidUtilities.dp(40);
        }
        return AndroidUtilities.dp(120);
    }

    @Override
    public int getIntrinsicHeight() {
        if (currentType == TYPE_ADD || currentType == TYPE_DISABLE) {
            return AndroidUtilities.dp(42);
        } else if (currentType == 3) {
            return AndroidUtilities.dp(100);
        } else if (currentType == 2) {
            return AndroidUtilities.dp(74);
        } else if (currentType == 1) {
            return AndroidUtilities.dp(40);
        }
        return AndroidUtilities.dp(180);
    }
}
