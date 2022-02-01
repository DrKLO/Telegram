/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ShapeDrawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;

public class LocationCell extends FrameLayout {

    private TextView nameTextView;
    private TextView addressTextView;
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

        nameTextView = new TextView(context);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameTextView.setMaxLines(1);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setSingleLine(true);
        nameTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), (LocaleController.isRTL ? 16 : 73), 10, (LocaleController.isRTL ? 73 : 16), 0));

        addressTextView = new TextView(context);
        addressTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        addressTextView.setMaxLines(1);
        addressTextView.setEllipsize(TextUtils.TruncateAt.END);
        addressTextView.setSingleLine(true);
        addressTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText3));
        addressTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(addressTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), (LocaleController.isRTL ? 16 : 73), 35, (LocaleController.isRTL ? 73 : 16), 0));

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

    public void setLocation(TLRPC.TL_messageMediaVenue location, String icon, int pos, boolean divider) {
        setLocation(location, icon, null, pos, divider);
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

    private float enterAlpha = 0f;
    private ValueAnimator enterAnimator;
    public void setLocation(TLRPC.TL_messageMediaVenue location, String icon, String label, int pos, boolean divider) {
        needDivider = divider;
        circleDrawable.getPaint().setColor(getColorForIndex(pos));
        if (location != null)
            nameTextView.setText(location.title);
        if (label != null) {
            addressTextView.setText(label);
        } else if (location != null) {
            addressTextView.setText(location.address);
        }
        if (icon != null)
            imageView.setImage(icon, null, null);
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
            globalGradientView = new FlickerLoadingView(getContext());
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
            canvas.drawLine(
                LocaleController.isRTL ? 0 : AndroidUtilities.dp(72),
                getHeight() - 1,
                LocaleController.isRTL ? getWidth() - AndroidUtilities.dp(72) : getWidth(),
                getHeight() - 1,
                Theme.dividerPaint
            );
        }
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }
}
