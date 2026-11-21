package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Picture;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.telegram.ui.ActionBar.Theme;

import me.vkryl.android.animator.FactorAnimator;

/**
 * {@link LinkSpanDrawable.ClickableSmallTextView} whose text can change animated: the old text moves
 * up and fades out while the new one comes from below.
 */
public class AnimatedClickableSmallTextView extends LinkSpanDrawable.ClickableSmallTextView implements FactorAnimator.Target {
    private static final long DURATION = 320;

    private final FactorAnimator changeAnimator = new FactorAnimator(0, this, CubicBezierInterpolator.EASE_OUT_QUINT, DURATION, 1f);
    private Picture outPicture;
    /** Where the old text was: a parent may move the view to the new text (e.g. to centre it), the old one stays. */
    private float outTranslationX;
    /** The layer the text view asks for (the gradient ellipsis); off while cross-fading, a layer would clip the old text. */
    private int requestedLayerType = LAYER_TYPE_NONE;
    private Paint requestedLayerPaint;

    public AnimatedClickableSmallTextView(Context context) {
        super(context);
    }

    public AnimatedClickableSmallTextView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
    }

    /** Sets the text; when {@code animated} and the view is on screen with another text, cross-fades to it. */
    public boolean setTextAnimated(CharSequence text, boolean animated) {
        final CharSequence current = getText();
        if (TextUtils.equals(current, text)) {
            return setText(text);
        }
        if (animated && isAttachedToWindow() && isLaidOut() && getVisibility() == VISIBLE && getAlpha() > 0 && getWidth() > 0 && getHeight() > 0 && !TextUtils.isEmpty(current)) {
            final Picture picture = new Picture();
            final Canvas canvas = picture.beginRecording(getWidth(), getHeight());
            drawContent(canvas, 1f, 0);
            picture.endRecording();
            changeAnimator.forceFactor(0f);
            outPicture = picture;
            outTranslationX = getTranslationX();
            super.setLayerType(LAYER_TYPE_NONE, null);
            changeAnimator.animateTo(1f);
        } else {
            changeAnimator.forceFactor(1f);
            finishChange();
        }
        invalidate();
        return setText(text);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (outPicture == null) {
            super.onDraw(canvas);
            return;
        }
        final float factor = changeAnimator.getFactor();
        final int offset = dp(6);
        canvas.save();
        canvas.translate(outTranslationX - getTranslationX(), -offset * factor);
        canvas.saveLayerAlpha(0, 0, outPicture.getWidth(), outPicture.getHeight(), (int) (0xFF * (1f - factor)));
        canvas.drawPicture(outPicture);
        canvas.restore();
        canvas.restore();
        drawContent(canvas, factor, offset * (1f - factor));
    }

    private void drawContent(Canvas canvas, float alpha, float translationY) {
        if (alpha >= 1f && translationY == 0) {
            super.onDraw(canvas);
            return;
        }
        canvas.save();
        canvas.translate(0, translationY);
        canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * alpha));
        super.onDraw(canvas);
        canvas.restore();
        canvas.restore();
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        invalidate();
    }

    @Override
    public void onFactorChangeFinished(int id, float finalFactor, FactorAnimator callee) {
        if (finalFactor == 1f) {
            finishChange();
        }
    }

    private void finishChange() {
        if (outPicture != null) {
            outPicture = null;
            super.setLayerType(requestedLayerType, requestedLayerPaint);
        }
        invalidate();
    }

    @Override
    public void setLayerType(int layerType, @Nullable Paint paint) {
        requestedLayerType = layerType;
        requestedLayerPaint = paint;
        if (outPicture == null) {
            super.setLayerType(layerType, paint);
        }
    }

    @Override
    public int getLayerType() {
        return requestedLayerType;
    }
}
