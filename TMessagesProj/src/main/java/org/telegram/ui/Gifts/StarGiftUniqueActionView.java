package org.telegram.ui.Gifts;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Stars.StarGiftUniqueActionLayout;

@SuppressLint("ViewConstructor")
public class StarGiftUniqueActionView extends View {
    private final StarGiftUniqueActionLayout layout;
    private final Theme.ResourcesProvider resourcesProvider;
    private float layoutX, layoutY;

    public StarGiftUniqueActionView(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        layout = new StarGiftUniqueActionLayout(currentAccount, this, resourcesProvider);
        // layout.getMessageDrawable().setUseAvatarAnimator(true);
        layout.getMessageDrawable().setCallback(this);

        NotificationCenter.listenEmojiLoading(this);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) || who == layout.getMessageDrawable();
    }

    public void set(TL_stars.TL_starGiftUnique gift, long fromId,
                    TLRPC.TL_textWithEntities message, String button, boolean animated) {
        layout.set(gift, fromId, message, button, animated);
        requestLayout();
        invalidate();
    }

    public StarGiftUniqueActionLayout getLayout() {
        return layout;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int layoutW = (int) layout.getWidth();
        final int layoutH = (int) layout.getHeight();
        layoutX = (width - layoutW) / 2f;
        layoutY = getPaddingTop();
        setMeasuredDimension(width, (int) layoutY + layoutH + getPaddingBottom());
    }

    Drawable background;

    public void setLayoutBackground(Drawable background) {
        this.background = background;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int backgroundHeight = 0;
        if (getParent() instanceof View) {
            backgroundHeight = ((View) getParent()).getHeight();
        }

        if (resourcesProvider != null) {
            resourcesProvider.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, 0, getY());
        } else {
            Theme.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, 0, getY());
        }

        final int layoutW = (int) layout.getWidth();
        layoutX = (getWidth() - layoutW) / 2f;

        float w = layout.getWidth() + dp(4 + 4);
        float x = (getWidth() - w) / 2f;
        float y = layoutY - dp(4);
        AndroidUtilities.rectTmp.set(x, y, x + w, y + layout.getHeight() + dp(4 + 4));
        AndroidUtilities.rectTmp.round(AndroidUtilities.rectTmp2);
        background.setBounds(AndroidUtilities.rectTmp2);
        background.draw(canvas);

        canvas.save();
        canvas.translate(layoutX, layoutY);
        layout.draw(canvas);
        layout.drawOutbounds(canvas);
        canvas.restore();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        layout.attach();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return layout.onTouchEvent(layoutX, layoutY, event);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        layout.detach();
    }

    public boolean hasGradientService() {
        return resourcesProvider != null ? resourcesProvider.hasGradientService() : Theme.hasGradientService();
    }

    protected Paint getThemedPaint(String paintKey) {
        Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(paintKey) : null;
        return paint != null ? paint : Theme.getThemePaint(paintKey);
    }
}