/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.Switch;

public class TextCell extends FrameLayout {

    public final SimpleTextView textView;
    private final SimpleTextView subtitleView;
    public final AnimatedTextView valueTextView;
    public final SimpleTextView valueSpoilersTextView;
    public final RLottieImageView imageView;
    private Switch checkBox;
    private ImageView valueImageView;
    private int leftPadding;
    private boolean needDivider;
    public int offsetFromImage = 71;
    public int heightDp = 50;
    public int imageLeft = 21;
    private boolean inDialogs;
    private boolean prioritizeTitleOverValue;
    private Theme.ResourcesProvider resourcesProvider;
    private boolean attached;
    private int loadingSize;
    private boolean drawLoading;
    private boolean measureDelay;
    private float loadingProgress;
    private float drawLoadingProgress;

    private int lastWidth;

    public TextCell(Context context) {
        this(context, 23, false, false, null);
    }

    public TextCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, 23, false, false, resourcesProvider);
    }

    public TextCell(Context context, int left, boolean dialog) {
        this(context, left, dialog, false, null);
    }

    public TextCell(Context context, int left, boolean dialog, boolean needCheck, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.resourcesProvider = resourcesProvider;
        leftPadding = left;

        textView = new SimpleTextView(context);
        textView.setTextColor(Theme.getColor(dialog ? Theme.key_dialogTextBlack : Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setTextSize(16);
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        subtitleView = new SimpleTextView(context);
        subtitleView.setTextColor(Theme.getColor(dialog ? Theme.key_dialogTextGray : Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        subtitleView.setTextSize(13);
        subtitleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        subtitleView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        valueTextView = new AnimatedTextView(context, false, false, true);
        valueTextView.setTextColor(Theme.getColor(dialog ? Theme.key_dialogTextBlue2 : Theme.key_windowBackgroundWhiteValueText, resourcesProvider));
        valueTextView.setPadding(0, AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18));
        valueTextView.setTextSize(AndroidUtilities.dp(16));
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        valueTextView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        valueTextView.setTranslationY(AndroidUtilities.dp(-2));
        addView(valueTextView);

        valueSpoilersTextView = new SimpleTextView(context);
        valueSpoilersTextView.setEllipsizeByGradient(18, false);
        valueSpoilersTextView.setTextColor(Theme.getColor(dialog ? Theme.key_dialogTextBlue2 : Theme.key_windowBackgroundWhiteValueText, resourcesProvider));
        valueSpoilersTextView.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        valueSpoilersTextView.setTextSize(16);
        valueSpoilersTextView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        valueSpoilersTextView.setVisibility(GONE);
        addView(valueSpoilersTextView);

        imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(dialog ? Theme.key_dialogIcon : Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        addView(imageView);

        valueImageView = new ImageView(context);
        valueImageView.setScaleType(ImageView.ScaleType.CENTER);
        addView(valueImageView);

        if (needCheck) {
            checkBox = new Switch(context, resourcesProvider);
            checkBox.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
            addView(checkBox, LayoutHelper.createFrame(37, 20, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 22, 0, 22, 0));
        }

        setFocusable(true);
    }

    public boolean isChecked() {
        return checkBox != null && checkBox.isChecked();
    }

    public Switch getCheckBox() {
        return checkBox;
    }

    public void setIsInDialogs() {
        inDialogs = true;
    }

    public SimpleTextView getTextView() {
        return textView;
    }

    public RLottieImageView getImageView() {
        return imageView;
    }

    public AnimatedTextView getValueTextView() {
        return valueTextView;
    }

    public ImageView getValueImageView() {
        return valueImageView;
    }

    public void setPrioritizeTitleOverValue(boolean prioritizeTitleOverValue) {
        if (this.prioritizeTitleOverValue != prioritizeTitleOverValue) {
            this.prioritizeTitleOverValue = prioritizeTitleOverValue;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = AndroidUtilities.dp(heightDp);

        if (lastWidth != 0 && lastWidth != width && valueText != null) {
            valueTextView.setText(TextUtils.ellipsize(valueText, valueTextView.getPaint(), AndroidUtilities.displaySize.x / 2.5f, TextUtils.TruncateAt.END), false);
        }
        lastWidth = width;

        int valueWidth;
        if (prioritizeTitleOverValue) {
            textView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(71 + leftPadding), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
            subtitleView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(71 + leftPadding), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
            valueTextView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(103 + leftPadding) - textView.getTextWidth(), LocaleController.isRTL ? MeasureSpec.AT_MOST : MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
            valueSpoilersTextView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(103 + leftPadding) - textView.getTextWidth(), LocaleController.isRTL ? MeasureSpec.AT_MOST : MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
        } else {
            valueTextView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(leftPadding), LocaleController.isRTL ? MeasureSpec.AT_MOST : MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
            valueSpoilersTextView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(leftPadding), LocaleController.isRTL ? MeasureSpec.AT_MOST : MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
            valueWidth = Math.max(valueTextView.width(), valueSpoilersTextView.getTextWidth());
            textView.measure(MeasureSpec.makeMeasureSpec(Math.max(0, width - AndroidUtilities.dp(71 + leftPadding) - valueWidth), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
            subtitleView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(71 + leftPadding) - valueWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
        }
        if (imageView.getVisibility() == VISIBLE) {
            imageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        }
        if (valueImageView.getVisibility() == VISIBLE) {
            valueImageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        }
        if (checkBox != null) {
            checkBox.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(37), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(width, height + (needDivider ? 1 : 0));
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (checkBox != null) {
            checkBox.setEnabled(enabled);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int height = bottom - top;
        int width = right - left;

        int viewTop = (height - Math.max(valueSpoilersTextView.getTextHeight(), valueTextView.getTextHeight())) / 2;
        int viewLeft = LocaleController.isRTL ? AndroidUtilities.dp(leftPadding) : width - valueTextView.getMeasuredWidth() - AndroidUtilities.dp(leftPadding);
        if (prioritizeTitleOverValue && !LocaleController.isRTL) {
            viewLeft = width - valueTextView.getMeasuredWidth() - AndroidUtilities.dp(leftPadding);
        }
        valueTextView.layout(viewLeft, viewTop, viewLeft + valueTextView.getMeasuredWidth(), viewTop + valueTextView.getMeasuredHeight());
        viewLeft = LocaleController.isRTL ? AndroidUtilities.dp(leftPadding) : width - valueSpoilersTextView.getMeasuredWidth() - AndroidUtilities.dp(leftPadding);
        valueSpoilersTextView.layout(viewLeft, viewTop, viewLeft + valueSpoilersTextView.getMeasuredWidth(), viewTop + valueSpoilersTextView.getMeasuredHeight());

        if (LocaleController.isRTL) {
            viewLeft = getMeasuredWidth() - textView.getMeasuredWidth() - AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? offsetFromImage : leftPadding);
        } else {
            viewLeft = AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? offsetFromImage : leftPadding);
        }
        if (subtitleView.getVisibility() == View.VISIBLE) {
            int margin = heightDp > 50 ? 4 : 2;
            viewTop = (height - textView.getTextHeight() - subtitleView.getTextHeight() - AndroidUtilities.dp(margin)) / 2;
            textView.layout(viewLeft, viewTop, viewLeft + textView.getMeasuredWidth(), viewTop + textView.getMeasuredHeight());
            viewTop = viewTop + textView.getTextHeight() + AndroidUtilities.dp(margin);
            subtitleView.layout(viewLeft, viewTop, viewLeft + subtitleView.getMeasuredWidth(), viewTop + subtitleView.getMeasuredHeight());
        } else {
            viewTop = (height - textView.getTextHeight()) / 2;
            textView.layout(viewLeft, viewTop, viewLeft + textView.getMeasuredWidth(), viewTop + textView.getMeasuredHeight());
        }
        if (imageView.getVisibility() == VISIBLE) {
            viewTop = AndroidUtilities.dp(heightDp > 50 ? 0 : 2) + (height - imageView.getMeasuredHeight()) / 2 - imageView.getPaddingTop();
            viewLeft = !LocaleController.isRTL ? AndroidUtilities.dp(imageLeft) : width - imageView.getMeasuredWidth() - AndroidUtilities.dp(imageLeft);
            imageView.layout(viewLeft, viewTop, viewLeft + imageView.getMeasuredWidth(), viewTop + imageView.getMeasuredHeight());
        }

        if (valueImageView.getVisibility() == VISIBLE) {
            viewTop = (height - valueImageView.getMeasuredHeight()) / 2;
            viewLeft = LocaleController.isRTL ? AndroidUtilities.dp(23) : width - valueImageView.getMeasuredWidth() - AndroidUtilities.dp(23);
            valueImageView.layout(viewLeft, viewTop, viewLeft + valueImageView.getMeasuredWidth(), viewTop + valueImageView.getMeasuredHeight());
        }
        if (checkBox != null && checkBox.getVisibility() == VISIBLE) {
            viewTop = (height - checkBox.getMeasuredHeight()) / 2;
            viewLeft = LocaleController.isRTL ? AndroidUtilities.dp(22) : width - checkBox.getMeasuredWidth() - AndroidUtilities.dp(22);
            checkBox.layout(viewLeft, viewTop, viewLeft + checkBox.getMeasuredWidth(), viewTop + checkBox.getMeasuredHeight());
        }
    }

    public void setTextColor(int color) {
        textView.setTextColor(color);
    }

    public void setColors(int icon, int text) {
        textView.setTextColor(Theme.getColor(text, resourcesProvider));
        textView.setTag(text);
        if (icon >= 0) {
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(icon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            imageView.setTag(icon);
        }
    }

    private CharSequence valueText;

    public void setText(String text, boolean divider) {
        imageLeft = 21;
        textView.setText(text);
        valueTextView.setText(valueText = null, false);
        imageView.setVisibility(GONE);
        valueTextView.setVisibility(GONE);
        valueSpoilersTextView.setVisibility(GONE);
        valueImageView.setVisibility(GONE);
        needDivider = divider;
        setWillNotDraw(!needDivider);
    }

    public void setTextAndIcon(String text, int resId, boolean divider) {
        imageLeft = 21;
        offsetFromImage = getOffsetFromImage(false);
        textView.setText(text);
        valueTextView.setText(valueText = null, false);
        imageView.setImageResource(resId);
        imageView.setVisibility(VISIBLE);
        valueTextView.setVisibility(GONE);
        valueSpoilersTextView.setVisibility(GONE);
        valueImageView.setVisibility(GONE);
        imageView.setPadding(0, AndroidUtilities.dp(7), 0, 0);
        needDivider = divider;
        setWillNotDraw(!needDivider);
    }

    public void setTextAndColorfulIcon(String text, int resId, int color, boolean divider) {
        imageLeft = 21;
        offsetFromImage = 71;
        textView.setText(text);
        valueTextView.setText(valueText = null, false);
        setColorfulIcon(color, resId);
        valueTextView.setVisibility(GONE);
        valueImageView.setVisibility(GONE);
        needDivider = divider;
        setWillNotDraw(!needDivider);
    }

    public void setTextAndIcon(String text, Drawable drawable, boolean divider) {
        offsetFromImage = 68;
        imageLeft = 18;
        textView.setText(text);
        valueTextView.setText(valueText = null, false);
        imageView.setColorFilter(null);
        if (drawable instanceof RLottieDrawable) {
            imageView.setAnimation((RLottieDrawable) drawable);
        } else {
            imageView.setImageDrawable(drawable);
        }
        imageView.setVisibility(VISIBLE);
        valueTextView.setVisibility(GONE);
        valueImageView.setVisibility(GONE);
        imageView.setPadding(0, AndroidUtilities.dp(6), 0, 0);
        needDivider = divider;
        setWillNotDraw(!needDivider);
    }

    public void setOffsetFromImage(int value) {
        offsetFromImage = value;
    }

    public void setImageLeft(int imageLeft) {
        this.imageLeft = imageLeft;
    }

    public void setTextAndValue(String text, String value, boolean divider) {
        setTextAndValue(text, value, false, divider);
    }

    public void setTextAndValue(String text, String value, boolean animated, boolean divider) {
        imageLeft = 21;
        offsetFromImage = getOffsetFromImage(false);
        textView.setText(text);
        valueTextView.setText(TextUtils.ellipsize(valueText = value, valueTextView.getPaint(), AndroidUtilities.displaySize.x / 2.5f, TextUtils.TruncateAt.END), animated);
        valueTextView.setVisibility(VISIBLE);
        valueSpoilersTextView.setVisibility(GONE);
        imageView.setVisibility(GONE);
        valueImageView.setVisibility(GONE);
        needDivider = divider;
        setWillNotDraw(!needDivider);
        if (checkBox != null) {
            checkBox.setVisibility(GONE);
        }
    }

    public void setTextAndValueAndColorfulIcon(String text, CharSequence value, boolean animated, int resId, int color, boolean divider) {
        imageLeft = 21;
        offsetFromImage = getOffsetFromImage(false);
        textView.setText(text);
        valueTextView.setText(TextUtils.ellipsize(valueText = value, valueTextView.getPaint(), AndroidUtilities.displaySize.x / 2.5f, TextUtils.TruncateAt.END), animated);
        valueTextView.setVisibility(VISIBLE);
        valueSpoilersTextView.setVisibility(GONE);
        setColorfulIcon(color, resId);
        valueImageView.setVisibility(GONE);
        needDivider = divider;
        setWillNotDraw(!needDivider);
        if (checkBox != null) {
            checkBox.setVisibility(GONE);
        }
    }

    public void setTextAndSpoilersValueAndIcon(String text, CharSequence value, int resId, boolean divider) {
        imageLeft = 21;
        offsetFromImage = getOffsetFromImage(false);
        textView.setText(text);
        valueSpoilersTextView.setVisibility(VISIBLE);
        valueSpoilersTextView.setText(value);
        valueTextView.setVisibility(GONE);
        valueImageView.setVisibility(GONE);
        imageView.setVisibility(VISIBLE);
        imageView.setTranslationX(0);
        imageView.setTranslationY(0);
        imageView.setPadding(0, AndroidUtilities.dp(7), 0, 0);
        imageView.setImageResource(resId);
        needDivider = divider;
        setWillNotDraw(!needDivider);
        if (checkBox != null) {
            checkBox.setVisibility(GONE);
        }
    }

    public void setTextAndSpoilersValueAndColorfulIcon(String text, CharSequence value, int resId, int color, boolean divider) {
        imageLeft = 21;
        offsetFromImage = getOffsetFromImage(false);
        textView.setText(text);
        valueSpoilersTextView.setVisibility(VISIBLE);
        valueSpoilersTextView.setText(value);
        valueTextView.setVisibility(GONE);
        setColorfulIcon(color, resId);
        valueImageView.setVisibility(GONE);
        needDivider = divider;
        setWillNotDraw(!needDivider);
        if (checkBox != null) {
            checkBox.setVisibility(GONE);
        }
    }

    public void setTextAndValueAndIcon(String text, String value, int resId, boolean divider) {
        setTextAndValueAndIcon(text, value, false, resId, divider);
    }

    public void setTextAndValueAndIcon(String text, String value, boolean animated, int resId, boolean divider) {
        imageLeft = 21;
        offsetFromImage = getOffsetFromImage(false);
        textView.setText(text);
        valueTextView.setText(TextUtils.ellipsize(valueText = value, valueTextView.getPaint(), AndroidUtilities.displaySize.x / 2.5f, TextUtils.TruncateAt.END), animated);
        valueTextView.setVisibility(VISIBLE);
        valueSpoilersTextView.setVisibility(GONE);
        valueImageView.setVisibility(GONE);
        imageView.setVisibility(VISIBLE);
        imageView.setTranslationX(0);
        imageView.setTranslationY(0);
        imageView.setPadding(0, AndroidUtilities.dp(7), 0, 0);
        imageView.setImageResource(resId);
        needDivider = divider;
        setWillNotDraw(!needDivider);
        if (checkBox != null) {
            checkBox.setVisibility(GONE);
        }
    }

    public void setColorfulIcon(int color, int resId) {
        offsetFromImage = getOffsetFromImage(true);
        imageView.setVisibility(VISIBLE);
        imageView.setPadding(AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(2));
        imageView.setTranslationX(AndroidUtilities.dp(LocaleController.isRTL ? 0 : -3));
        imageView.setImageResource(resId);
        imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        imageView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(9), color));
    }

    public void setTextAndCheck(CharSequence text, boolean checked, boolean divider) {
        imageLeft = 21;
        offsetFromImage = getOffsetFromImage(false);
        textView.setText(text);
        imageView.setVisibility(GONE);
        valueImageView.setVisibility(GONE);
        needDivider = divider;
        if (checkBox != null) {
            checkBox.setVisibility(VISIBLE);
            checkBox.setChecked(checked, false);
        }
        setWillNotDraw(!needDivider);
    }

    public void setTextAndCheckAndIcon(CharSequence text, boolean checked, int resId, boolean divider) {
        imageLeft = 21;
        offsetFromImage = getOffsetFromImage(false);
        textView.setText(text);
        valueTextView.setVisibility(GONE);
        valueSpoilersTextView.setVisibility(GONE);
        valueImageView.setVisibility(GONE);
        if (checkBox != null) {
            checkBox.setVisibility(VISIBLE);
            checkBox.setChecked(checked, false);
        }
        imageView.setVisibility(VISIBLE);
        imageView.setPadding(0, AndroidUtilities.dp(7), 0, 0);
        imageView.setImageResource(resId);
        needDivider = divider;
        setWillNotDraw(!needDivider);
    }

    public void setTextAndCheckAndIcon(String text, boolean checked, Drawable resDrawable, boolean divider) {
        imageLeft = 21;
        offsetFromImage = getOffsetFromImage(false);
        textView.setText(text);
        valueTextView.setVisibility(GONE);
        valueSpoilersTextView.setVisibility(GONE);
        valueImageView.setVisibility(GONE);
        if (checkBox != null) {
            checkBox.setVisibility(VISIBLE);
            checkBox.setChecked(checked, false);
        }
        imageView.setVisibility(VISIBLE);
        imageView.setPadding(0, AndroidUtilities.dp(7), 0, 0);
        imageView.setImageDrawable(resDrawable);
        needDivider = divider;
        setWillNotDraw(!needDivider);
    }

    public void setTextAndValueDrawable(String text, Drawable drawable, boolean divider) {
        imageLeft = 21;
        offsetFromImage = getOffsetFromImage(false);
        textView.setText(text);
        valueTextView.setText(valueText = null, false);
        valueImageView.setVisibility(VISIBLE);
        valueImageView.setImageDrawable(drawable);
        valueTextView.setVisibility(GONE);
        valueSpoilersTextView.setVisibility(GONE);
        imageView.setVisibility(GONE);
        imageView.setPadding(0, AndroidUtilities.dp(7), 0, 0);
        needDivider = divider;
        setWillNotDraw(!needDivider);
        if (checkBox != null) {
            checkBox.setVisibility(GONE);
        }
    }

    protected int getOffsetFromImage(boolean colourful) {
        return colourful ? 65 : 71;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(Theme.key_paint_divider) : null;
            if (paint == null) {
                paint = Theme.dividerPaint;
            }
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? (inDialogs ? 72 : 68) : 20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? (inDialogs ? 72 : 68) : 20) : 0), getMeasuredHeight() - 1, paint);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        CharSequence text = textView.getText();
        if (!TextUtils.isEmpty(text)) {
            final CharSequence valueText = valueTextView.getText();
            if (!TextUtils.isEmpty(valueText)) {
                text = TextUtils.concat(text, ": ", valueText);
            }
        }
        if (checkBox != null) {
            info.setClassName("android.widget.Switch");
            info.setCheckable(true);
            info.setChecked(checkBox.isChecked());
            StringBuilder sb = new StringBuilder();
            sb.append(textView.getText());
            if (!TextUtils.isEmpty(valueTextView.getText())) {
                sb.append('\n');
                sb.append(valueTextView.getText());
            }
            info.setContentDescription(sb);
        } else {
            if (!TextUtils.isEmpty(text)) {
                info.setText(text);
            }
        }
        info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    public void setNeedDivider(boolean needDivider) {
        if (this.needDivider != needDivider) {
            this.needDivider = needDivider;
            setWillNotDraw(!needDivider);
            invalidate();
        }
    }

    public void setChecked(boolean checked) {
        checkBox.setChecked(checked, true);
    }

    public void showEnabledAlpha(boolean show) {
        float alpha = show ? 0.5f : 1f;
        if (attached) {
            if (imageView != null) {
                imageView.animate().alpha(alpha).start();
            }
            if (textView != null) {
                textView.animate().alpha(alpha).start();
            }
            if (valueTextView != null) {
                valueTextView.animate().alpha(alpha).start();
            }
            if (valueSpoilersTextView != null) {
                valueSpoilersTextView.animate().alpha(alpha).start();
            }
            if (valueImageView != null) {
                valueImageView.animate().alpha(alpha).start();
            }
        } else {
            if (imageView != null) {
                imageView.setAlpha(alpha);
            }
            if (textView != null) {
                textView.setAlpha(alpha);
            }
            if (valueTextView != null) {
                valueTextView.setAlpha(alpha);
            }
            if (valueSpoilersTextView != null) {
                valueSpoilersTextView.setAlpha(alpha);
            }
            if (valueImageView != null) {
                valueImageView.setAlpha(alpha);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
    }

    public void setDrawLoading(boolean drawLoading, int size, boolean animated) {
        this.drawLoading = drawLoading;
        this.loadingSize = size;

        if (!animated) {
            drawLoadingProgress = drawLoading ? 1f : 0f;
        } else {
            measureDelay = true;
        }
        invalidate();
    }

    Paint paint;
    private boolean incrementLoadingProgress;
    private int changeProgressStartDelay;

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (drawLoading || drawLoadingProgress != 0) {
            if (paint == null) {
                paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setColor(Theme.getColor(Theme.key_dialogSearchBackground, resourcesProvider));
            }
            //LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT;
            if (incrementLoadingProgress) {
                loadingProgress += 16 / 1000f;
                if (loadingProgress > 1f) {
                    loadingProgress = 1f;
                    incrementLoadingProgress = false;
                }
            } else {
                loadingProgress -= 16 / 1000f;
                if (loadingProgress < 0) {
                    loadingProgress = 0;
                    incrementLoadingProgress = true;
                }
            }

            if (changeProgressStartDelay > 0) {
                changeProgressStartDelay -= 15;
            } else if (drawLoading && drawLoadingProgress != 1f) {
                drawLoadingProgress += 16 / 150f;
                if (drawLoadingProgress > 1f) {
                    drawLoadingProgress = 1f;
                }
            } else if (!drawLoading && drawLoadingProgress != 0) {
                drawLoadingProgress -= 16 / 150f;
                if (drawLoadingProgress < 0) {
                    drawLoadingProgress = 0;
                }
            }

            float alpha = (0.6f + 0.4f * loadingProgress) * drawLoadingProgress;
            paint.setAlpha((int) (255 * alpha));
            int cy = getMeasuredHeight() >> 1;
            AndroidUtilities.rectTmp.set(
                getMeasuredWidth() - AndroidUtilities.dp(21) - AndroidUtilities.dp(loadingSize),
                cy - AndroidUtilities.dp(3),
                getMeasuredWidth() - AndroidUtilities.dp(21),
                cy + AndroidUtilities.dp(3)
            );
            if (LocaleController.isRTL) {
                AndroidUtilities.rectTmp.left = getMeasuredWidth() - AndroidUtilities.rectTmp.left;
                AndroidUtilities.rectTmp.right = getMeasuredWidth() - AndroidUtilities.rectTmp.right;
            }
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(3), AndroidUtilities.dp(3), paint);
            invalidate();
        }
        valueTextView.setAlpha(1f - drawLoadingProgress);
        valueSpoilersTextView.setAlpha(1f - drawLoadingProgress);
        super.dispatchDraw(canvas);
    }

    public void setSubtitle(CharSequence charSequence) {
        if (!TextUtils.isEmpty(charSequence)) {
            subtitleView.setVisibility(View.VISIBLE);
            subtitleView.setText(charSequence);
        } else {
            subtitleView.setVisibility(View.GONE);
        }
    }
}
