package org.telegram.ui.Gifts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.R;
import org.telegram.messenger.utils.DrawableUtils;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.blur3.utils.NinePatchBuilder;

import me.vkryl.android.animator.BoolAnimator;

public class GiftMessageDrawable extends Drawable {

    private NinePatchDrawable bubble;
    private NinePatchDrawable bubbleBorder;

    private final TextPaint textPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final ImageReceiver avatarReceiver = new ImageReceiver();
    private final AvatarDrawable avatarDrawable = new AvatarDrawable();

    private final int avatarRadius = dp(10.66f);
    private final int avatarSize = avatarRadius * 2;
    private final int avatarLeftPadding = dp(4);
    private final float firstBaselineTop = dpf2(15.33f);
    private final float lastBaselineBottom = dpf2(7.33f);
    private final int textPaddingH = dp(8);
    private final int minHeight = (int) dpf2(22.66f);

    private boolean hasAvatar;
    private CharSequence message;
    private StaticLayout textLayout;
    private float textDrawX;
    private float textDrawY;
    private AnimatedEmojiSpan.EmojiGroupedSpans emojiGroupedSpans;
    private View parentView;

    private int lastMeasuredWidth;
    private int measuredWidth;
    private int measuredHeight;

    public GiftMessageDrawable() {
        textPaint.setTextSize(dp(12));
        textPaint.setColor(0xFFFFFFFF);
        avatarReceiver.setRoundRadius(avatarRadius);
    }

    private void ensureNinePatches() {
        if (bubble == null) {
            bubble = createBubbleNinePatch(R.drawable.gift_message_bubble_24);
        }
        if (bubbleBorder == null) {
            bubbleBorder = createBubbleBorderNinePatch(R.drawable.gift_message_bubble_border_24);
        }
    }

    public TextPaint getTextPaint() {
        return textPaint;
    }

    public void setParentView(View view) {
        this.parentView = view;
        avatarReceiver.setParentView(view);
    }

    public void setMessage(CharSequence text) {
        this.message = text;
        lastMeasuredWidth = -1;
    }

    public void setUser(TLObject user) {
        hasAvatar = user != null;
        if (hasAvatar) {
            avatarDrawable.setInfo(user);
            if (user instanceof TLRPC.User) {
                avatarReceiver.setImage(
                        ImageLocation.getForUser((TLRPC.User) user, ImageLocation.TYPE_SMALL),
                        "48_48", avatarDrawable, null, null, 0);
            } else if (user instanceof TLRPC.Chat) {
                avatarReceiver.setImage(
                        ImageLocation.getForChat((TLRPC.Chat) user, ImageLocation.TYPE_SMALL),
                        "48_48", avatarDrawable, null, null, 0);
            } else {
                avatarReceiver.setImageBitmap(avatarDrawable);
            }
        }
        lastMeasuredWidth = -1;
    }

    public void attach() {
        avatarReceiver.onAttachedToWindow();
    }

    public void detach() {
        avatarReceiver.onDetachedFromWindow();
        AnimatedEmojiSpan.release(null, emojiGroupedSpans);
        emojiGroupedSpans = null;
    }

    private int getTextLeftPadding() {
        return (hasAvatar || alwaysUseAvatarAnimator ? avatarLeftPadding + avatarSize : 0) + textPaddingH;
    }

    public int getLineCount() {
        return textLayout != null ? textLayout.getLineCount() : 0;
    }

    public int measure(int maxWidth) {
        ensureNinePatches();

        if (maxWidth == lastMeasuredWidth && textLayout != null) {
            return measuredHeight;
        }
        lastMeasuredWidth = maxWidth;

        final int textLeftPadding = getTextLeftPadding();
        final int textMaxWidth = maxWidth - textLeftPadding - textPaddingH;
        if (textMaxWidth <= 0 || TextUtils.isEmpty(message)) {
            textLayout = null;
            measuredWidth = minHeight;
            measuredHeight = minHeight;
            return measuredHeight;
        }

        final StaticLayout firstLayout = new StaticLayout(
                message, textPaint, textMaxWidth,
                Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);

        final int lineCount = firstLayout.getLineCount();

        float maxLineWidth = 0;
        for (int i = 0; i < lineCount; i++) {
            maxLineWidth = Math.max(maxLineWidth, firstLayout.getLineWidth(i));
        }

        StaticLayout balanced = firstLayout;

        if (lineCount > 1) {
            final int balancedTextWidth = (int) Math.ceil(maxLineWidth);
            if (balancedTextWidth < textMaxWidth) {
                final StaticLayout secondLayout = new StaticLayout(
                        message, textPaint, balancedTextWidth,
                        Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);

                if (secondLayout.getLineCount() == lineCount) {
                    balanced = secondLayout;
                    maxLineWidth = 0;
                    for (int i = 0; i < balanced.getLineCount(); i++) {
                        maxLineWidth = Math.max(maxLineWidth, balanced.getLineWidth(i));
                    }
                }
            }
        }

        textLayout = balanced;
        textDrawX = textLeftPadding;
        measuredWidth = (int) Math.ceil(maxLineWidth) + textLeftPadding + textPaddingH;

        final int lastLine = textLayout.getLineCount() - 1;
        final float firstBaseline = textLayout.getLineBaseline(0);
        final float lastBaseline = textLayout.getLineBaseline(lastLine);
        measuredHeight = Math.max(minHeight,
                (int) Math.ceil(firstBaselineTop
                        + (lastBaseline - firstBaseline)
                        + lastBaselineBottom));

        textDrawY = firstBaselineTop - firstBaseline;

        return measuredHeight;
    }

    @Override
    public int getMinimumWidth() {
        return measuredWidth;
    }

    @Override
    public int getMinimumHeight() {
        return measuredHeight;
    }

    private final BoolAnimator animatorAvatarVisible = new BoolAnimator(0, (a, b, c, d) -> invalidateSelf(), CubicBezierInterpolator.EASE_OUT_QUINT, 320L, true);
    private boolean alwaysUseAvatarAnimator;

    public void setAvatarVisible(boolean visible, boolean animated) {
        animatorAvatarVisible.setValue(visible, animated);
    }


    public void setUseAvatarAnimator(boolean alwaysUseAvatarAnimator) {
        this.alwaysUseAvatarAnimator = alwaysUseAvatarAnimator;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        ensureNinePatches();

        final Rect bounds = getBounds();

        canvas.save();

        final float avatarFactor;
        if (alwaysUseAvatarAnimator) {
            avatarFactor = animatorAvatarVisible.getFloatValue();
            canvas.translate(-(avatarLeftPadding + avatarSize) / 2f * (1f - avatarFactor), 0);
        } else {
            avatarFactor = hasAvatar ? 1 : 0;
        }

        DrawableUtils.setBoundsIncreasePadding(bubble,
                bounds.left + (hasAvatar ? avatarLeftPadding + avatarSize : 0), bounds.top, bounds.right, bounds.bottom);
        bubble.draw(canvas);

        DrawableUtils.setBoundsIncreasePadding(bubbleBorder,
                bounds.left + (hasAvatar ? avatarLeftPadding + avatarSize : 0), bounds.top, bounds.right, bounds.bottom);
        bubbleBorder.draw(canvas);

        if (textLayout != null) {
            canvas.save();
            canvas.translate(bounds.left + textDrawX, bounds.top + textDrawY);
            textLayout.draw(canvas);

            if (parentView != null && message instanceof android.text.Spanned) {
                emojiGroupedSpans = AnimatedEmojiSpan.update(
                        AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, parentView, false,
                        emojiGroupedSpans, textLayout);
                AnimatedEmojiSpan.drawAnimatedEmojis(canvas, textLayout,
                        emojiGroupedSpans, 0, null, 0, 0, 0, 1f, null);
            }

            canvas.restore();
        }

        if (avatarFactor > 0) {
            final int avatarLeft = bounds.left;
            final int avatarTop = bounds.bottom - avatarSize;
            avatarReceiver.setImageCoords(avatarLeft, avatarTop, avatarSize, avatarSize);
            canvas.save();
            canvas.scale(avatarFactor, avatarFactor, avatarReceiver.getCenterX(), avatarReceiver.getCenterY());
            avatarReceiver.draw(canvas);
            canvas.restore();
        }

        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {}

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    private static NinePatchDrawable createBubbleNinePatch(@DrawableRes int drawableRes) {
        final Drawable drawable = ApplicationLoader.applicationContext
                .getResources().getDrawable(drawableRes);
        final int width = drawable.getIntrinsicWidth();
        final int height = drawable.getIntrinsicHeight();
        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        final Rect padding = new Rect(
                27 * width / 168, 4 * height / 144,
                5 * width / 168, 4 * height / 144);
        return NinePatchBuilder.createNinePatch(bitmap, padding,
                94 * width / 168, 71 * height / 144);
    }
    private static NinePatchDrawable createBubbleBorderNinePatch(@DrawableRes int drawableRes) {
        final Drawable drawable = ApplicationLoader.applicationContext
                .getResources().getDrawable(drawableRes);
        final int width = drawable.getIntrinsicWidth();
        final int height = drawable.getIntrinsicHeight();
        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);

        final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gradientPaint.setShader(new LinearGradient(
                width, 0,
                0, height,
                new int[]{ 0x40FFFFFF, 0xCFFFFFFF, 0x40FFFFFF },
                null,
                Shader.TileMode.CLAMP
        ));
        gradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        canvas.drawRect(0, 0, width, height, gradientPaint);

        final Rect padding = new Rect(
                27 * width / 168, 4 * height / 144,
                5 * width / 168, 4 * height / 144);
        return NinePatchBuilder.createNinePatch(bitmap, padding,
                94 * width / 168, 71 * height / 144);
    }
}