package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Premium.PremiumGradient;

public class AvatarsImageView extends View {

    public final AvatarsDrawable avatarsDrawable;

    public AvatarsImageView(@NonNull Context context, boolean inCall) {
        super(context);
        avatarsDrawable = new AvatarsDrawable(this, inCall);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        avatarsDrawable.width = getMeasuredWidth();
        avatarsDrawable.height = getMeasuredHeight();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatarsDrawable.onAttachedToWindow();
    }

    private PremiumGradient.PremiumGradientTools premiumGradient;
    private Text plusText;
    private Paint plusBgPaint;

    public void setPlus(int n, int bgColor) {
        premiumGradient = new PremiumGradient.PremiumGradientTools(Theme.key_premiumGradient1, Theme.key_premiumGradient2, -1, -1, -1, null);
        plusText = new Text("+" + n, 12, AndroidUtilities.getTypeface("fonts/num.otf"));
        plusBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        plusBgPaint.setColor(bgColor);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        avatarsDrawable.onDraw(canvas);
        if (plusText != null) {
            AndroidUtilities.rectTmp.set(getWidth() - dp(22), getHeight() - dp(22), getWidth() - dp(0), getHeight() - dp(0));
            premiumGradient.gradientMatrix(AndroidUtilities.rectTmp);
            canvas.drawCircle(AndroidUtilities.rectTmp.centerX(), AndroidUtilities.rectTmp.centerY(), AndroidUtilities.rectTmp.width() / 2f + dp(1.33f), plusBgPaint);
            canvas.drawCircle(AndroidUtilities.rectTmp.centerX(), AndroidUtilities.rectTmp.centerY(), AndroidUtilities.rectTmp.width() / 2f, premiumGradient.paint);
            plusText.draw(canvas, AndroidUtilities.rectTmp.centerX() - plusText.getCurrentWidth() / 2f, AndroidUtilities.rectTmp.centerY(), Color.WHITE, 1f);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        avatarsDrawable.onDetachedFromWindow();
    }


    public void setStyle(int style) {
        avatarsDrawable.setStyle(style);
    }

    public void setDelegate(Runnable delegate) {
        avatarsDrawable.setDelegate(delegate);
    }

    public void setObject(int a, int currentAccount, TLObject object) {
        avatarsDrawable.setObject(a, currentAccount, object);
    }

    public void setAvatarsTextSize(int size) {
        avatarsDrawable.setAvatarsTextSize(size);
    }

    public void setSize(int size) {
        avatarsDrawable.setSize(size);
    }

    public void setStepFactor(float factor) {
        avatarsDrawable.setStepFactor(factor);
    }

    public void reset() {
        avatarsDrawable.reset();
    }

    public void setCount(int usersCount) {
        avatarsDrawable.setCount(usersCount);
    }

    public void commitTransition(boolean animated) {
        avatarsDrawable.commitTransition(animated);
    }

    public void updateAfterTransitionEnd() {
        avatarsDrawable.updateAfterTransitionEnd();
    }

    public void setCentered(boolean centered) {
        avatarsDrawable.setCentered(centered);
    }
}
