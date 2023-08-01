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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class TextDetailSettingsCell extends FrameLayout {

    private TextView textView;
    private TextView valueTextView;
    private ImageView imageView;
    private boolean needDivider;
    private boolean multiline;

    public TextDetailSettingsCell(Context context) {
        super(context);

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 10, 21, 0));

        valueTextView = new TextView(context);
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setPadding(0, 0, 0, 0);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 35, 21, 0));

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
        imageView.setVisibility(GONE);
        addView(imageView, LayoutHelper.createFrame(52, 52, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 8, 6, 8, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!multiline) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
        } else {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        }
    }

    public TextView getTextView() {
        return textView;
    }

    public TextView getValueTextView() {
        return valueTextView;
    }

    public void setMultilineDetail(boolean value) {
        multiline = value;
        if (value) {
            valueTextView.setLines(0);
            valueTextView.setMaxLines(0);
            valueTextView.setSingleLine(false);
            valueTextView.setPadding(0, 0, 0, AndroidUtilities.dp(12));
        } else {
            valueTextView.setLines(1);
            valueTextView.setMaxLines(1);
            valueTextView.setSingleLine(true);
            valueTextView.setPadding(0, 0, 0, 0);
        }
    }

    public void setTextAndValue(CharSequence text, CharSequence value, boolean divider) {
        textView.setText(text);
        valueTextView.setText(value);
        needDivider = divider;
        imageView.setVisibility(GONE);
        setWillNotDraw(!divider);
    }

    public void setTextAndValueAndIcon(String text, CharSequence value, int resId, boolean divider) {
        textView.setText(text);
        valueTextView.setText(value);
        imageView.setImageResource(resId);
        imageView.setVisibility(VISIBLE);
        textView.setPadding(LocaleController.isRTL ? 0 : AndroidUtilities.dp(50), 0, LocaleController.isRTL ? AndroidUtilities.dp(50) : 0, 0);
        valueTextView.setPadding(LocaleController.isRTL ? 0 : AndroidUtilities.dp(50), 0, LocaleController.isRTL ? AndroidUtilities.dp(50) : 0, multiline ? AndroidUtilities.dp(12) : 0);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setValue(CharSequence value) {
        valueTextView.setText(value);
    }

    public void setTextWithEmojiAnd21Value(String text, CharSequence value, boolean divider) {
        textView.setText(Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false));
        valueTextView.setText(value);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        textView.invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider && Theme.dividerPaint != null) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? 71 : 20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? 71 : 20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }
}
