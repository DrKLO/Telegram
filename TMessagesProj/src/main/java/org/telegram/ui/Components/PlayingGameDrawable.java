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
import android.graphics.RectF;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;

public class PlayingGameDrawable extends StatusDrawable {

    private boolean isChat = false;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int currentAccount = UserConfig.selectedAccount;

    private long lastUpdateTime = 0;
    private boolean started = false;
    private RectF rect = new RectF();
    private float progress;
    private final boolean isDialogScreen;
    Theme.ResourcesProvider resourcesProvider;

    public PlayingGameDrawable(boolean isDialogScreen, Theme.ResourcesProvider resourcesProvider) {
        this.isDialogScreen = isDialogScreen;
        this.resourcesProvider = resourcesProvider;
    }
    public void setIsChat(boolean value) {
        isChat = value;
    }

    @Override
    public void setColor(int color) {
    }

    private void update() {
        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        lastUpdateTime = newTime;
        if (dt > 16) {
            dt = 16;
        }
        if (progress >= 1.0f) {
            progress = 0.0f;
        }
        progress += dt / 300.0f;
        if (progress > 1.0f) {
            progress = 1.0f;
        }
        invalidateSelf();
    }

    public void start() {
        lastUpdateTime = System.currentTimeMillis();
        started = true;
        invalidateSelf();
    }

    public void stop() {
        progress = 0.0f;
        started = false;
    }

    @Override
    public void draw(Canvas canvas) {
        int size = AndroidUtilities.dp(10);
        int y = getBounds().top + (getIntrinsicHeight() - size) / 2;
        if (isChat) {
            //y = AndroidUtilities.dp(8.5f) + getBounds().top;
        } else {
            y += AndroidUtilities.dp(1);
            //y = AndroidUtilities.dp(9.3f) + getBounds().top;
        }

        paint.setColor(Theme.getColor(isDialogScreen ? Theme.key_chats_actionMessage : Theme.key_chat_status, resourcesProvider));
        rect.set(0, y, size, y + size);
        int rad;
        if (progress < 0.5f) {
            rad = (int) (35 * (1.0f - progress / 0.5f));
        } else {
            rad = (int) (35 * (progress - 0.5f) / 0.5f);
        }
        for (int a = 0; a < 3; a++) {
            float x = a * AndroidUtilities.dp(5) + AndroidUtilities.dp(9.2f) - AndroidUtilities.dp(5) * progress;
            if (a == 2) {
                paint.setAlpha(Math.min(255, (int) (255 * progress / 0.5f)));
            } else if (a == 0) {
                if (progress > 0.5f) {
                    paint.setAlpha((int) (255 * (1.0f - (progress - 0.5f) / 0.5f)));
                } else {
                    paint.setAlpha(255);
                }
            } else {
                paint.setAlpha(255);
            }
            canvas.drawCircle(x, y + size / 2, AndroidUtilities.dp(1.2f), paint);
        }
        paint.setAlpha(255);
        canvas.drawArc(rect, rad, 360 - rad * 2, true, paint);
        paint.setColor(Theme.getColor(isDialogScreen ? Theme.key_windowBackgroundWhite : Theme.key_actionBarDefault));
        canvas.drawCircle(AndroidUtilities.dp(4), y + size / 2 - AndroidUtilities.dp(2), AndroidUtilities.dp(1), paint);

        checkUpdate();
    }

    private void checkUpdate() {
        if (started) {
            if (!NotificationCenter.getInstance(currentAccount).isAnimationInProgress()) {
                update();
            } else {
                AndroidUtilities.runOnUIThread(this::checkUpdate, 100);
            }
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
        return AndroidUtilities.dp(20);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(18);
    }
}
