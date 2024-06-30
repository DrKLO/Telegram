/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;

public class LocationCell extends FrameLayout {

    private AnimatedTextView nameTextView;
    private AnimatedTextView addressTextView;
    private BackupImageView imageView;
    private ShapeDrawable circleDrawable;
    private boolean needDivider;
    private boolean wrapContent;
    private final Theme.ResourcesProvider resourcesProvider;

    public LocationCell(Context context, boolean wrap, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        wrapContent = wrap;

        imageView = new BackupImageView(context);
        imageView.setBackground(circleDrawable = Theme.createCircleDrawable(AndroidUtilities.dp(42), 0xffffffff));
        imageView.setSize(AndroidUtilities.dp(30), AndroidUtilities.dp(30));
        addView(imageView, LayoutHelper.createFrame(42, 42, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 15, 11, LocaleController.isRTL ? 15 : 0, 0));

        nameTextView = new AnimatedTextView(context, true, true, true);
        nameTextView.setAnimationProperties(0.4f, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        nameTextView.setScaleProperty(.6f);
        nameTextView.setTextSize(AndroidUtilities.dp(16));
        nameTextView.setEllipsizeByGradient(true);
        nameTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTypeface(AndroidUtilities.bold());
        nameTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        nameTextView.getDrawable().setOverrideFullWidth(AndroidUtilities.displaySize.x);
        NotificationCenter.listenEmojiLoading(nameTextView);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 22, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), (LocaleController.isRTL ? 16 : 73), 10, (LocaleController.isRTL ? 73 : 16), 0));

        addressTextView = new AnimatedTextView(context, true, true, true);
        addressTextView.setScaleProperty(.6f);
        addressTextView.setAnimationProperties(0.4f, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        addressTextView.setTextSize(AndroidUtilities.dp(14));
        addressTextView.setEllipsizeByGradient(true);
        addressTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText3));
        addressTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(addressTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), (LocaleController.isRTL ? 16 : 73), 35, (LocaleController.isRTL ? 73 : 16), 0));

        imageView.setAlpha(enterAlpha);
        nameTextView.setAlpha(enterAlpha);
        addressTextView.setAlpha(enterAlpha);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (wrapContent) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
        } else {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
        }
    }

    public BackupImageView getImageView() {
        return imageView;
    }

    public void setLocation(TLRPC.TL_messageMediaVenue location, int pos, boolean divider) {
        setLocation(location, null, pos, divider, false);
    }

    private boolean allowTextAnimation;
    public void setAllowTextAnimation(boolean allow) {
        allowTextAnimation = allow;
    }

    public static int getColorForIndex(int index) {
        switch (index % 7) {
            case 0:
                return 0xffeb6060;
            case 1:
                return 0xfff2c04b;
            case 2:
                return 0xff459df5;
            case 3:
                return 0xff36c766;
            case 4:
                return 0xff8771fd;
            case 5:
                return 0xff43b9d7;
            case 6:
            default:
                return 0xffec638b;
        }
    }

    private CharSequence lastCompleteTitle;
    private String lastEmoji, lastTitle;
    private CharSequence getTitle(TLRPC.TL_messageMediaVenue location) {
        if (location == null) {
            return "";
        }
        if (TextUtils.equals(lastEmoji, location.emoji) && TextUtils.equals(lastTitle, location.title)) {
            return lastCompleteTitle;
        }
        CharSequence title = location.title;
        if (!TextUtils.isEmpty(location.emoji)) {
            title = location.emoji + " " + title;
            title = Emoji.replaceEmoji(title, nameTextView.getPaint().getFontMetricsInt(), false);
        }
        lastEmoji = location.emoji;
        lastTitle = location.title;
        return lastCompleteTitle = title;
    }

    private float enterAlpha = 0f;
    private ValueAnimator enterAnimator;
    public void setLocation(TLRPC.TL_messageMediaVenue location, String label, int pos, boolean divider, boolean animated) {
        needDivider = divider;
        if (location != null) {
            nameTextView.setText(getTitle(location), allowTextAnimation && !LocaleController.isRTL && animated);
        }
        if (label != null) {
            addressTextView.setText(label, allowTextAnimation && !LocaleController.isRTL);
        } else if (location != null) {
            addressTextView.setText(location.address, allowTextAnimation && !LocaleController.isRTL && animated);
        }
        int color = getColorForIndex(pos);
        if (location != null && location.icon != null) {
            if ("pin".equals(location.icon) || location.icon.startsWith("emoji")) {
                Drawable drawable = getResources().getDrawable(R.drawable.pin).mutate();
                drawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_location_sendLocationIcon), PorterDuff.Mode.MULTIPLY));
                CombinedDrawable combinedDrawable = new CombinedDrawable(Theme.createCircleDrawable(AndroidUtilities.dp(42), 0), drawable);
                combinedDrawable.setCustomSize(AndroidUtilities.dp(42), AndroidUtilities.dp(42));
                combinedDrawable.setIconSize(AndroidUtilities.dp(24), AndroidUtilities.dp(24));
                imageView.setImageDrawable(combinedDrawable);
            } else {
                imageView.setImage(location.icon, null, null);
            }
        }
        circleDrawable.getPaint().setColor(color);
        setWillNotDraw(false);
        setClickable(location == null);

        if (enterAnimator != null)
            enterAnimator.cancel();

        boolean loading = location == null;
        float fromEnterAlpha = enterAlpha,
                toEnterAlpha = loading ? 0f : 1f;
        long duration = (long) (Math.abs(fromEnterAlpha - toEnterAlpha) * 150);
        enterAnimator = ValueAnimator.ofFloat(fromEnterAlpha, toEnterAlpha);
        final long start = SystemClock.elapsedRealtime();
        enterAnimator.addUpdateListener(a -> {
            float t = Math.min(Math.max((float) (SystemClock.elapsedRealtime() - start) / duration, 0), 1);
            if (duration <= 0)
                t = 1f;
            enterAlpha = AndroidUtilities.lerp(fromEnterAlpha, toEnterAlpha, t);
            imageView.setAlpha(enterAlpha);
            nameTextView.setAlpha(enterAlpha);
            addressTextView.setAlpha(enterAlpha);
            invalidate();
        });
        enterAnimator.setDuration(loading ? Long.MAX_VALUE : duration);
        enterAnimator.start();

        imageView.setAlpha(fromEnterAlpha);
        nameTextView.setAlpha(fromEnterAlpha);
        addressTextView.setAlpha(fromEnterAlpha);
        invalidate();
    }

    private static FlickerLoadingView globalGradientView;

    @Override
    protected void onDraw(Canvas canvas) {
        if (globalGradientView == null) {
            globalGradientView = new FlickerLoadingView(getContext(), resourcesProvider);
            globalGradientView.setIsSingleCell(true);
        }

        int index = getParent() instanceof ViewGroup ? ((ViewGroup) getParent()).indexOfChild(this) : 0;
        globalGradientView.setParentSize(getMeasuredWidth(), getMeasuredHeight(), -index * AndroidUtilities.dp(56));
        globalGradientView.setViewType(FlickerLoadingView.AUDIO_TYPE);
        globalGradientView.updateColors();
        globalGradientView.updateGradient();

        canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) ((1f - enterAlpha) * 255), Canvas.ALL_SAVE_FLAG);
        canvas.translate(AndroidUtilities.dp(2), (getMeasuredHeight() - AndroidUtilities.dp(56)) / 2);
        globalGradientView.draw(canvas);
        canvas.restore();
        super.onDraw(canvas);

        if (needDivider) {
            Paint dividerPaint = resourcesProvider == null ? null : resourcesProvider.getPaint(Theme.key_paint_divider);
            if (dividerPaint == null) {
                dividerPaint = Theme.dividerPaint;
            }
            canvas.drawLine(
                LocaleController.isRTL ? 0 : AndroidUtilities.dp(72),
                getHeight() - 1,
                LocaleController.isRTL ? getWidth() - AndroidUtilities.dp(72) : getWidth(),
                getHeight() - 1,
                dividerPaint
            );
        }
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
