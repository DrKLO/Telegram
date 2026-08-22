package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.RichMessageLayout;
import org.telegram.messenger.utils.DrawableUtils;
import org.telegram.ui.ActionBar.Theme;

import me.vkryl.android.util.ClickHelper;

public class UnsupportedBlockDrawable extends Drawable {

    private final Drawable planeDrawable;
    private final Drawable bubbleDrawable;
    private final TextPaint titlePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final TextPaint subtitlePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final TextPaint buttonTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final Paint buttonBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF buttonRect = new RectF();
    private final ButtonBounce buttonBounce = new ButtonBounce(null);
    private final ClickHelper clickHelper = new ClickHelper(new ClickHelper.Delegate() {
        @Override
        public boolean needClickAt(View view, float x, float y) {
            final int p = dp(9);
            buttonRect.inset(-p, -p);
            final boolean result = buttonRect.contains(x, y);
            buttonRect.inset(p, p);
            return result;
        }

        @Override
        public void onClickTouchDown(View view, float x, float y) {
            buttonBounce.setPressed(true);
        }

        @Override
        public void onClickTouchUp(View view, float x, float y) {
            buttonBounce.setPressed(false);
        }

        @Override
        public void onClickAt(View view, float x, float y) {
            if (onClickListener != null) {
                onClickListener.run();
            }
        }
    });

    private Theme.ResourcesProvider resourcesProvider;
    private Runnable onClickListener;

    public boolean onTouchEvent(View v, MotionEvent ev) {
        return clickHelper.onTouchEvent(v , ev);
    }

    private StaticLayout titleLayout;
    private StaticLayout subtitleLayout;
    private StaticLayout buttonLayout;

    private CharSequence title;
    private CharSequence subtitle;
    private CharSequence buttonText;

    private int measuredWidth;
    private int measuredHeight;

    private final int textLeft = dp(62.33f);
    private final int buttonPaddingH = dp(12);
    private final int buttonHeight = dp(30);
    private final int buttonRadius = dp(15);
    private final int paddingV = dp(7);
    private final int buttonGap = dp(12);
    private final int titleSubtitleGap = dp(2);

    public UnsupportedBlockDrawable(Theme.ResourcesProvider resourceProvider) {
        this.resourcesProvider = resourceProvider;

        planeDrawable = ApplicationLoader.applicationContext.getDrawable(R.drawable.send_plane_26).mutate();
        bubbleDrawable = ApplicationLoader.applicationContext.getDrawable(R.drawable.large_unsupported).mutate();

        buttonBounce.setAdditionalInvalidate(this::invalidateSelf);
        titlePaint.setTypeface(AndroidUtilities.bold());
        titlePaint.setTextSize(dp(14));
        subtitlePaint.setTextSize(dp(12));
        buttonTextPaint.setTypeface(AndroidUtilities.bold());
        buttonTextPaint.setTextSize(dp(14));

        updateColors();
    }

    public void updateColors() {
        final int white = Theme.getColor(Theme.key_chat_serviceText); // 0xFFFFFFFF;
        final int black = 0xFF000000;

        planeDrawable.setColorFilter(new PorterDuffColorFilter(white, PorterDuff.Mode.MULTIPLY));
        bubbleDrawable.setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(black, 0.11f), PorterDuff.Mode.MULTIPLY));
        titlePaint.setColor(white);
        subtitlePaint.setColor(ColorUtils.setAlphaComponent(white, 0xB3));
        buttonTextPaint.setColor(white);
        buttonBackgroundPaint.setColor(Theme.multAlpha(black, 0.11f));
    }

    public void setOnClickListener(Runnable onClickListener) {
        this.onClickListener = onClickListener;
    }

    public void setTitle(CharSequence title) {
        this.title = title;
    }

    public void setSubtitle(CharSequence subtitle) {
        this.subtitle = subtitle;
    }

    public void setButtonText(CharSequence text) {
        this.buttonText = text;
    }

    public int measure(int width) {
        measuredWidth = width;

        final float buttonTextWidth = buttonTextPaint.measureText(buttonText, 0, buttonText.length());
        final int buttonW = (int) (buttonTextWidth + buttonPaddingH * 2);
        buttonLayout = new StaticLayout(
                buttonText, buttonTextPaint, (int) Math.ceil(buttonTextWidth),
                Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);

        final int textWidth = width - textLeft - buttonW - buttonGap - dp(11);
        final CharSequence titleEllipsized = TextUtils.ellipsize(title, titlePaint, textWidth, TextUtils.TruncateAt.END);
        titleLayout = new StaticLayout(
                titleEllipsized, titlePaint, textWidth,
                Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);

        subtitleLayout = new StaticLayout(
                subtitle, subtitlePaint, textWidth,
                Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);

        final int textBlockHeight = titleLayout.getHeight() + titleSubtitleGap + subtitleLayout.getHeight();
        measuredHeight = Math.max(textBlockHeight, buttonHeight) + paddingV * 2;

        setBounds(0, 0, measuredWidth, measuredHeight);
        return measuredHeight;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (titleLayout == null || subtitleLayout == null || buttonLayout == null) return;

        final int left = getBounds().left;
        final int right = getBounds().right;
        final int cy = getBounds().centerY();

        final int textBlockHeight = titleLayout.getHeight() + titleSubtitleGap + subtitleLayout.getHeight();
        final int textTop = cy - textBlockHeight / 2;

        canvas.save();
        canvas.translate(left + textLeft, textTop);
        titleLayout.draw(canvas);
        canvas.translate(0, titleLayout.getHeight() + titleSubtitleGap);
        subtitleLayout.draw(canvas);
        canvas.restore();

        final float buttonTextWidth = buttonLayout.getWidth();
        final int buttonW = (int) (buttonTextWidth + buttonPaddingH * 2);
        final int buttonRight = right - dp(11);
        final int buttonLeft = buttonRight - buttonW;
        final int buttonTop = cy - buttonHeight / 2;

        buttonRect.set(buttonLeft, buttonTop, buttonRight, buttonTop + buttonHeight);

        final float s = buttonBounce.getScale(0.05f);
        canvas.save();
        canvas.scale(s, s, buttonRect.centerX(), buttonRect.centerY());
        final int bgColor = Theme.multAlpha(0xFFFFFFFF, 0.18f);
        canvas.drawRoundRect(buttonRect, buttonRadius, buttonRadius, buttonBackgroundPaint);
        canvas.save();
        canvas.translate(
                buttonLeft + buttonPaddingH,
                buttonTop + (buttonHeight - buttonLayout.getHeight()) / 2f
        );
        buttonLayout.draw(canvas);
        canvas.restore();
        canvas.restore();


        DrawableUtils.setBounds(bubbleDrawable, left + dp(29.66f), cy + 1, Gravity.CENTER);
        bubbleDrawable.draw(canvas);

        DrawableUtils.setBounds(planeDrawable, left + dp(29.66f), cy + 1, Gravity.CENTER);
        planeDrawable.draw(canvas);
    }

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {}

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}