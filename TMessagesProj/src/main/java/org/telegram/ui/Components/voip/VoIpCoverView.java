package org.telegram.ui.Components.voip;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.CubicBezierInterpolator;

@SuppressLint("ViewConstructor")
public class VoIpCoverView extends View {

    private VoipCoverEmoji[] voipCoverEmojiLeft;
    private VoipCoverEmoji[] voipCoverEmojiRight;
    private ValueAnimator positionAnimator;
    private int diffX1;
    private int diffX2;
    private int diffX3;
    private int diffX4;
    private int diffX5;
    private int diffY1;
    private int diffY2;
    private int diffY3;
    private int diffY4;
    private int diffY5;
    private boolean isConnected;
    private boolean isEmojiExpanded;
    private int connectedDiffX;
    private boolean isPaused;
    private final VoIPBackgroundProvider backgroundProvider;
    private final Paint saveLayerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect bgRect = new Rect();
    private final boolean allowAnimations;

    public VoIpCoverView(Context context, TLRPC.User callingUser, VoIPBackgroundProvider backgroundProvider) {
        super(context);
        this.allowAnimations = LiteMode.isEnabled(LiteMode.FLAG_CALLS_ANIMATIONS);
        this.backgroundProvider = backgroundProvider;
        if (!allowAnimations) {
            return;
        }
        voipCoverEmojiLeft = new VoipCoverEmoji[]{
                new VoipCoverEmoji(callingUser, this, dp(32)),
                new VoipCoverEmoji(callingUser, this, dp(28)),
                new VoipCoverEmoji(callingUser, this, dp(35)),
                new VoipCoverEmoji(callingUser, this, dp(28)),
                new VoipCoverEmoji(callingUser, this, dp(26))
        };
        voipCoverEmojiRight = new VoipCoverEmoji[]{
                new VoipCoverEmoji(callingUser, this, dp(32)),
                new VoipCoverEmoji(callingUser, this, dp(28)),
                new VoipCoverEmoji(callingUser, this, dp(35)),
                new VoipCoverEmoji(callingUser, this, dp(28)),
                new VoipCoverEmoji(callingUser, this, dp(26))
        };
        backgroundProvider.attach(this);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        saveLayerPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
    }

    public void onConnected() {
        if (!allowAnimations) {
            return;
        }
        if (isConnected) {
            return;
        }
        isConnected = true;
        connectedDiffX = dp(12);
        positionAnimator = ValueAnimator.ofInt(0, connectedDiffX);
        positionAnimator.addUpdateListener(a -> {
            diffX1 = (int) a.getAnimatedValue();
            diffX2 = diffX1;
            diffX3 = diffX1;
            diffX4 = diffX1;
            diffX5 = diffX1;
            invalidate();
        });
        positionAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        positionAnimator.setDuration(200);
        positionAnimator.start();
    }

    public void onEmojiExpanded(boolean expanded) {
        if (!allowAnimations) {
            return;
        }
        if (expanded == isEmojiExpanded) {
            return;
        }
        isEmojiExpanded = expanded;
        positionAnimator = expanded ? ValueAnimator.ofFloat(0, 1f) : ValueAnimator.ofFloat(1f, 0f);
        positionAnimator.addUpdateListener(a -> {
            float percent = (float) a.getAnimatedValue();
            diffX1 = AndroidUtilities.lerp(connectedDiffX, dp(56), percent);
            diffX2 = AndroidUtilities.lerp(connectedDiffX, dp(36), percent);
            diffX3 = AndroidUtilities.lerp(connectedDiffX, dp(60), percent);
            diffX4 = AndroidUtilities.lerp(connectedDiffX, dp(36), percent);
            diffX5 = AndroidUtilities.lerp(connectedDiffX, dp(64), percent);

            diffY1 = AndroidUtilities.lerp(0, dp(50), percent);
            diffY2 = AndroidUtilities.lerp(0, dp(20), percent);
            diffY3 = AndroidUtilities.lerp(0, 0, percent);
            diffY4 = AndroidUtilities.lerp(0, dp(-20), percent);
            diffY5 = AndroidUtilities.lerp(0, dp(-40), percent);
            invalidate();
        });
        positionAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        positionAnimator.setDuration(200);
        positionAnimator.start();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!allowAnimations) {
            return;
        }
        for (VoipCoverEmoji voipCoverEmoji : voipCoverEmojiLeft) {
            voipCoverEmoji.onLayout(getMeasuredWidth(), getMeasuredHeight());
        }
        for (VoipCoverEmoji voipCoverEmoji : voipCoverEmojiRight) {
            voipCoverEmoji.onLayout(getMeasuredWidth(), getMeasuredHeight());
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!allowAnimations) {
            return;
        }
        if (isPaused) {
            return;
        }
        bgRect.set(0, 0, getWidth(), getHeight());
        backgroundProvider.setDarkTranslation(getX(), getY());
        int centerX = getMeasuredWidth() / 2;
        voipCoverEmojiLeft[0].setPosition(centerX - dp(120) - diffX1, dp(120) - diffY1);
        voipCoverEmojiLeft[1].setPosition(centerX - dp(180) - diffX2, dp(150) - diffY2);
        voipCoverEmojiLeft[2].setPosition(centerX - dp(150) - diffX3, dp(185) - diffY3);
        voipCoverEmojiLeft[3].setPosition(centerX - dp(176) - diffX4, dp(240) - diffY4);
        voipCoverEmojiLeft[4].setPosition(centerX - dp(130) - diffX5, dp(265) - diffY5);
        for (VoipCoverEmoji voipCoverEmoji : voipCoverEmojiLeft) {
            voipCoverEmoji.onDraw(canvas);
        }
        voipCoverEmojiRight[0].setPosition(centerX + dp(50) + diffX1, dp(120) - diffY1);
        voipCoverEmojiRight[1].setPosition(centerX + dp(110) + diffX2, dp(150) - diffY2);
        voipCoverEmojiRight[2].setPosition(centerX + dp(80) + diffX3, dp(185) - diffY3);
        voipCoverEmojiRight[3].setPosition(centerX + dp(106) + diffX4, dp(240) - diffY4);
        voipCoverEmojiRight[4].setPosition(centerX + dp(60) + diffX5, dp(265) - diffY5);
        for (VoipCoverEmoji voipCoverEmoji : voipCoverEmojiRight) {
            voipCoverEmoji.onDraw(canvas);
        }

        int oldDarkAlpha = backgroundProvider.getDarkPaint().getAlpha();
        saveLayerPaint.setAlpha(255);
        canvas.saveLayer(0, 0, getMeasuredWidth(), getMeasuredHeight(), saveLayerPaint, Canvas.ALL_SAVE_FLAG);
        backgroundProvider.getDarkPaint().setAlpha(255);
        canvas.drawRect(bgRect, backgroundProvider.getDarkPaint());
        backgroundProvider.getDarkPaint().setAlpha(oldDarkAlpha);

        if (backgroundProvider.isReveal()) {
            int oldRevealDarkAlpha = backgroundProvider.getRevealDarkPaint().getAlpha();
            backgroundProvider.getRevealDarkPaint().setAlpha(255);
            canvas.drawRect(bgRect, backgroundProvider.getRevealDarkPaint());
            backgroundProvider.getRevealDarkPaint().setAlpha(oldRevealDarkAlpha);
        }
        canvas.restore();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!allowAnimations) {
            return;
        }
        for (VoipCoverEmoji voipCoverEmoji : voipCoverEmojiLeft) {
            voipCoverEmoji.onAttachedToWindow();
        }
        for (VoipCoverEmoji voipCoverEmoji : voipCoverEmojiRight) {
            voipCoverEmoji.onAttachedToWindow();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!allowAnimations) {
            return;
        }
        for (VoipCoverEmoji voipCoverEmoji : voipCoverEmojiLeft) {
            voipCoverEmoji.onDetachedFromWindow();
        }
        for (VoipCoverEmoji voipCoverEmoji : voipCoverEmojiRight) {
            voipCoverEmoji.onDetachedFromWindow();
        }
        if (positionAnimator != null) {
            positionAnimator.cancel();
        }
    }

    public void setState(boolean isPaused) {
        this.isPaused = isPaused;
        invalidate();
    }
}
