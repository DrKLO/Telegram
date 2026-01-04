package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.drawable.Drawable;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotInlineKeyboard;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.Text;

class BotButton {
    public final Runnable invalidateRunnable;

    public boolean isSeparator;
    public float x;
    public int y;
    public float width;
    public int height;
    public int positionFlags;
    public Text title;
    @Nullable
    public TLRPC.KeyboardButton button;
    @Nullable
    public BotInlineKeyboard.ButtonCustom buttonCustom;
    public TLRPC.TL_reactionCount reaction;
    public int angle;
    public float progressAlpha;
    public long lastUpdateTime;
    public boolean isInviteButton;
    public boolean isLocked;

    public LoadingDrawable loadingDrawable;
    public Drawable selectorDrawable;
    public Drawable iconDrawable;

    public boolean pressed;
    public float pressT;
    public ValueAnimator pressAnimator;

    public BotButton(Runnable invalidateRunnable) {
        this.invalidateRunnable = invalidateRunnable;
    }

    public void setPressed(boolean pressed) {
        if (this.pressed != pressed) {
            this.pressed = pressed;
            invalidateRunnable.run();
            if (pressed) {
                if (pressAnimator != null) {
                    pressAnimator.removeAllListeners();
                    pressAnimator.cancel();
                }
            }
            if (!pressed && pressT != 0) {
                pressAnimator = ValueAnimator.ofFloat(pressT, 0);
                pressAnimator.addUpdateListener(animation -> {
                    pressT = (float) animation.getAnimatedValue();
                    invalidateRunnable.run();
                });
                pressAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        pressAnimator = null;
                    }
                });
                pressAnimator.setInterpolator(new OvershootInterpolator(2.0f));
                pressAnimator.setDuration(350);
                pressAnimator.start();
            }
        }
    }

    public boolean hasPositionFlag(int flag) {
        return (positionFlags & flag) == flag;
    }

    public float getPressScale() {
        if (pressed && pressT != 1f) {
            pressT += (float) Math.min(40, 1000f / AndroidUtilities.screenRefreshRate) / 100f;
            pressT = Utilities.clamp(pressT, 1f, 0);
            invalidateRunnable.run();
        }
        return 0.96f + 0.04f * (1f - pressT);
    }
}
