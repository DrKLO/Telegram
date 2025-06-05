package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;

public class ImageReceiverSpan extends ReplacementSpan {

    private final Paint shadowPaint;
    public final ImageReceiver imageReceiver;
    private float sz, radius;
    private final int currentAccount;

    private View parent;

    public ImageReceiverSpan(View parent, int currentAccount) {
        this(parent, currentAccount, 18);
    }

    public ImageReceiverSpan(View parent, int currentAccount, float sz) {
        this.currentAccount = currentAccount;
        this.imageReceiver = new ImageReceiver(parent);
        imageReceiver.setCurrentAccount(currentAccount);
        setSize(sz);

        this.shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setShadowLayer(dp(1), 0, dp(.66f), 0x33000000);

        setParent(parent);
    }

    public void setSize(float sz) {
        this.sz = sz;
    }

    public void setRoundRadius(float radiusDp) {
        imageReceiver.setRoundRadius((int) (radius = dp(radiusDp)));
    }

    public void setParent(View parent) {
        if (this.parent == parent) return;
        if (this.parent != null) {
            this.parent.removeOnAttachStateChangeListener(parentAttachListener);
            if (this.parent.isAttachedToWindow() && !parent.isAttachedToWindow()) {
                imageReceiver.onDetachedFromWindow();
            }
        }
        if ((this.parent == null || !this.parent.isAttachedToWindow()) && parent != null && parent.isAttachedToWindow()) {
            imageReceiver.onAttachedToWindow();
        }
        this.parent = parent;
        imageReceiver.setParentView(parent);
        if (parent != null) {
            parent.addOnAttachStateChangeListener(parentAttachListener);
        }
    }

    private final View.OnAttachStateChangeListener parentAttachListener = new View.OnAttachStateChangeListener() {
        @Override
        public void onViewAttachedToWindow(@NonNull View v) {
            imageReceiver.onAttachedToWindow();
        }
        @Override
        public void onViewDetachedFromWindow(@NonNull View v) {
            imageReceiver.onDetachedFromWindow();
        }
    };

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
        return dp(sz);
    }

    private boolean shadowEnabled = true;
    private float translateX, translateY;
    private int shadowPaintAlpha = 0xFF;

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        if (shadowEnabled && shadowPaintAlpha != paint.getAlpha()) {
            shadowPaint.setAlpha(shadowPaintAlpha = paint.getAlpha());
            shadowPaint.setShadowLayer(dp(1), 0, dp(.66f), Theme.multAlpha(0x33000000, shadowPaintAlpha / 255f));
        }
        final float l = translateX + x;
        final float t = translateY + (top + bottom) / 2f - dp(sz) / 2f;
        if (shadowEnabled) {
            AndroidUtilities.rectTmp.set(l, t, l + dp(sz), t + dp(sz));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, radius, radius, shadowPaint);
        }
        imageReceiver.setImageCoords(l, t, dp(sz), dp(sz));
        imageReceiver.setAlpha(paint.getAlpha() / 255f);
        imageReceiver.draw(canvas);
    }

    public void enableShadow(boolean enable) {
        shadowEnabled = enable;
    }

    public void translate(float x, float y) {
        this.translateX = x;
        this.translateY = y;
    }

}
