package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class SettingsSearchCell extends FrameLayout {

    private TextView textView;
    private TextView valueTextView;
    private ImageView imageView;
    private boolean needDivider;
    private int left;

    public class VerticalImageSpan extends ImageSpan {

        public VerticalImageSpan(Drawable drawable) {
            super(drawable);
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fontMetricsInt) {
            Drawable drawable = getDrawable();
            Rect rect = drawable.getBounds();
            if (fontMetricsInt != null) {
                Paint.FontMetricsInt fmPaint = paint.getFontMetricsInt();
                int fontHeight = fmPaint.descent - fmPaint.ascent;
                int drHeight = rect.bottom - rect.top;
                int centerY = fmPaint.ascent + fontHeight / 2;

                fontMetricsInt.ascent = centerY - drHeight / 2;
                fontMetricsInt.top = fontMetricsInt.ascent;
                fontMetricsInt.bottom = centerY + drHeight / 2;
                fontMetricsInt.descent = fontMetricsInt.bottom;
            }
            return rect.right;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            Drawable drawable = getDrawable();
            canvas.save();
            Paint.FontMetricsInt fmPaint = paint.getFontMetricsInt();
            int fontHeight = fmPaint.descent - fmPaint.ascent;
            int centerY = y + fmPaint.descent - fontHeight / 2;
            int transY = centerY - (drawable.getBounds().bottom - drawable.getBounds().top) / 2;
            canvas.translate(x, transY);
            if (LocaleController.isRTL) {
                canvas.scale(-1, 1, drawable.getIntrinsicWidth() / 2, drawable.getIntrinsicHeight() / 2);
            }
            drawable.draw(canvas);
            canvas.restore();
        }
    }

    public SettingsSearchCell(Context context) {
        super(context);

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, LocaleController.isRTL ? 16 : 71, 10, LocaleController.isRTL ? 71 : 16, 0));

        valueTextView = new TextView(context);
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, LocaleController.isRTL ? 16 : 71, 33, LocaleController.isRTL ? 71 : 16, 0));

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
        addView(imageView, LayoutHelper.createFrame(48, 48, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 10, 8, 10, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    public void setTextAndValueAndIcon(CharSequence text, String[] value, int icon, boolean divider) {
        textView.setText(text);
        LayoutParams layoutParams = (LayoutParams) textView.getLayoutParams();
        layoutParams.leftMargin = AndroidUtilities.dp(LocaleController.isRTL ? 16 : 71);
        layoutParams.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? 71 : 16);

        if (value != null) {
            SpannableStringBuilder builder = new SpannableStringBuilder();
            for (int a = 0; a < value.length; a++) {
                if (a != 0) {
                    builder.append(" > ");
                    Drawable drawable = getContext().getResources().getDrawable(R.drawable.settings_arrow).mutate();
                    drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                    drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), PorterDuff.Mode.MULTIPLY));
                    builder.setSpan(new VerticalImageSpan(drawable), builder.length() - 2, builder.length() - 1, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                builder.append(value[a]);
            }
            valueTextView.setText(builder);
            valueTextView.setVisibility(VISIBLE);
            layoutParams.topMargin = AndroidUtilities.dp(10);

            layoutParams = (LayoutParams) valueTextView.getLayoutParams();
            layoutParams.leftMargin = AndroidUtilities.dp(LocaleController.isRTL ? 16 : 71);
            layoutParams.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? 71 : 16);
        } else {
            layoutParams.topMargin = AndroidUtilities.dp(21);
            valueTextView.setVisibility(GONE);
        }
        if (icon != 0) {
            imageView.setImageResource(icon);
            imageView.setVisibility(VISIBLE);
        } else {
            imageView.setVisibility(GONE);
        }
        left = 69;
        needDivider = divider;
        setWillNotDraw(!needDivider);
    }

    public void setTextAndValue(CharSequence text, String[] value, boolean faq, boolean divider) {
        LayoutParams layoutParams = (LayoutParams) textView.getLayoutParams();
        if (faq) {
            valueTextView.setText(text);
            SpannableStringBuilder builder = new SpannableStringBuilder();
            for (int a = 0; a < value.length; a++) {
                if (a != 0) {
                    builder.append(" > ");
                    Drawable drawable = getContext().getResources().getDrawable(R.drawable.settings_arrow).mutate();
                    drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                    drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
                    builder.setSpan(new VerticalImageSpan(drawable), builder.length() - 2, builder.length() - 1, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                builder.append(value[a]);
            }
            textView.setText(builder);
            valueTextView.setVisibility(VISIBLE);
            layoutParams.topMargin = AndroidUtilities.dp(10);
        } else {
            textView.setText(text);
            if (value != null) {
                SpannableStringBuilder builder = new SpannableStringBuilder();
                for (int a = 0; a < value.length; a++) {
                    if (a != 0) {
                        builder.append(" > ");
                        Drawable drawable = getContext().getResources().getDrawable(R.drawable.settings_arrow).mutate();
                        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), PorterDuff.Mode.MULTIPLY));
                        builder.setSpan(new VerticalImageSpan(drawable), builder.length() - 2, builder.length() - 1, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    builder.append(value[a]);
                }
                valueTextView.setText(builder);
                valueTextView.setVisibility(VISIBLE);
                layoutParams.topMargin = AndroidUtilities.dp(10);
            } else {
                layoutParams.topMargin = AndroidUtilities.dp(21);
                valueTextView.setVisibility(GONE);
            }
        }

        layoutParams.leftMargin = layoutParams.rightMargin = AndroidUtilities.dp(16);

        layoutParams = (LayoutParams) valueTextView.getLayoutParams();
        layoutParams.leftMargin = layoutParams.rightMargin = AndroidUtilities.dp(16);

        imageView.setVisibility(GONE);
        needDivider = divider;
        setWillNotDraw(!needDivider);
        left = 16;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(left), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(left) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }
}
