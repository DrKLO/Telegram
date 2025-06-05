package org.telegram.ui.Components.voip;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;

import org.telegram.messenger.LiteMode;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class VoipCoverEmoji {

    private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emoji;
    private ValueAnimator positionAnimator;
    private final boolean allowAnimations;
    private int toRandomX;
    private int toRandomY;
    private int fromRandomX;
    private int fromRandomY;
    private int randomX;
    private int randomY;
    private int posX;
    private int posY;
    private final View parent;
    private final int size;
    private int alpha = 0;
    private float scale = 0f;
    private int width;
    private int height;
    private int diffX;
    private boolean isShown;
    private ValueAnimator diffXAnimator;

    public VoipCoverEmoji(TLRPC.User user, View parent, int size) {
        this.parent = parent;
        this.size = size;
        allowAnimations = LiteMode.isEnabled(LiteMode.FLAG_CALLS_ANIMATIONS);
        long emojiId = UserObject.getProfileEmojiId(user);
        if (allowAnimations && emojiId != 0) {
            emoji = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(parent, false, size, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC);
            emoji.set(emojiId, false);
            emoji.setColor(Color.BLACK);
            emoji.setAlpha(alpha);

            positionAnimator = ValueAnimator.ofFloat(0f, 1f);
            positionAnimator.addUpdateListener(a -> {
                randomX = (int) (fromRandomX + (toRandomX - fromRandomX) * (float) a.getAnimatedValue());
                randomY = (int) (fromRandomY + (toRandomY - fromRandomY) * (float) a.getAnimatedValue());
                parent.invalidate();
            });
            fromRandomX = toRandomX + dp(12);
            fromRandomY = toRandomY + dp(12);
            toRandomX = Utilities.random.nextInt(dp(16)) + dp(12);
            toRandomY = Utilities.random.nextInt(dp(16)) + dp(12);
            positionAnimator.setInterpolator(new LinearInterpolator());
            positionAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(@NonNull Animator animation, boolean isReverse) {
                    fromRandomX = toRandomX;
                    fromRandomY = toRandomY;
                    toRandomX = Utilities.random.nextInt(dp(16)) + dp(12);
                    toRandomY = Utilities.random.nextInt(dp(16)) + dp(12);
                    if (positionAnimator != null) {
                        positionAnimator.start();
                    }
                }
            });
            positionAnimator.setDuration(2000);
        }
    }

    private void show() {
        if (isShown) return;
        if (emoji.getDrawable() instanceof AnimatedEmojiDrawable) {
            AnimatedEmojiDrawable emojiDrawable = ((AnimatedEmojiDrawable) emoji.getDrawable());
            if (emojiDrawable.getImageReceiver() == null || !emojiDrawable.getImageReceiver().hasImageLoaded()) {
                return;
            }
        }
        isShown = true;
        int delay = 180;
        int duration = 350;
        int maxX = 12;
        diffX = posX > getCenterX() ? dp(maxX) : -dp(maxX);
        ValueAnimator scaleAnimator = ValueAnimator.ofFloat(0, 1f);
        scaleAnimator.setInterpolator(new CubicBezierInterpolator(.34, 1.36, .64, 1));
        scaleAnimator.addUpdateListener(a -> {
            scale = (float) a.getAnimatedValue();
            parent.invalidate();
            if (scale > 1.0f && diffXAnimator == null) {
                diffXAnimator = ValueAnimator.ofInt(dp(maxX), 0);
                diffXAnimator.addUpdateListener(a2 -> {
                    int val = (int) a2.getAnimatedValue();
                    diffX = posX > getCenterX() ? val : -val;
                    parent.invalidate();
                });
                diffXAnimator.setDuration(duration - a.getCurrentPlayTime());
                diffXAnimator.start();
            }
        });
        scaleAnimator.setDuration(duration);
        scaleAnimator.setStartDelay(delay);
        scaleAnimator.start();
        ValueAnimator alphaAnimator = ValueAnimator.ofInt(0, 255, 255);
        alphaAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        alphaAnimator.addUpdateListener(a -> {
            alpha = (int) a.getAnimatedValue();
            parent.invalidate();
        });
        alphaAnimator.setStartDelay(delay);
        alphaAnimator.setDuration(duration);
        alphaAnimator.start();
    }

    private int getCenterX() {
        return width / 2;
    }

    public void onLayout(int width, int height) {
        this.width = width;
        this.height = height;
        parent.invalidate();
    }

    public void setPosition(int x, int y) {
        if (emoji == null) return;
        posX = x;
        posY = y;
        parent.invalidate();
        show();
    }

    public void onDraw(Canvas canvas) {
        if (emoji == null) return;
        canvas.save();
        canvas.scale(scale, scale, width / 2f, dp(300));
        canvas.translate(posX - diffX, posY);
        emoji.setBounds(randomX, randomY, randomX + size, randomY + size);
        emoji.setAlpha(alpha);
        emoji.draw(canvas);
        canvas.restore();
    }

    public void onAttachedToWindow() {
        if (emoji == null) return;
        emoji.attach();
        if (positionAnimator != null) {
            positionAnimator.start();
        }
    }

    public void onDetachedFromWindow() {
        if (emoji == null) return;
        if (positionAnimator != null) {
            positionAnimator.cancel();
            positionAnimator = null;
        }
        emoji.detach();
    }
}
