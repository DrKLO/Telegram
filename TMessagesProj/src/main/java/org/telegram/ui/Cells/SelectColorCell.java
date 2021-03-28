package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.DynamicLayout;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Animations.SelectColorBottomSheet;

// TODO agolokoz: add ripple
public class SelectColorCell extends View {

    private static final int leftRightSpace = AndroidUtilities.dp(21);
    private static final int leftRightPadding = AndroidUtilities.dp(6);
    private static final int topBottomPadding = AndroidUtilities.dp(6);
    private static final int backgroundRadius = AndroidUtilities.dp(7);
    private static final int topBottomBackPadding = AndroidUtilities.dp(2);
    private static final int backRadius = AndroidUtilities.dp(4);
    private static final char[] chars = new char[1];
    private static final char[] maxWidthChars = new char[] { '#', 'A', 'A', 'A', 'A', 'A', 'A' };

    private final TextPaint titleTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint valueTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valueBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valueBackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF valueBackgroundRect = new RectF();
    private final RectF valueBackRect = new RectF();
    private final StringBuilder valueBuilder = new StringBuilder("#AAAAAA");
    private final float[] hsv = new float[3];
    private final DynamicLayout valueLayout;
    private final int backgroundWidth;
    private final float sharpCharWidth;

    private SelectColorBottomSheet.ColorListener colorListener;
    private SelectColorBottomSheet colorBottomSheet;
    private StaticLayout titleLayout;
    private float valueTextWidth;

    private String title;
    private int color;
    private boolean isValueSelected;

    public SelectColorCell(@NonNull Context context) {
        super(context);

        titleTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextPaint.setTextSize(AndroidUtilities.dp(15));
        valueTextPaint.setTextSize(AndroidUtilities.dp(15));

        float valueTextWidth = getMaxValueWidth();
        valueLayout = new DynamicLayout(valueBuilder, valueTextPaint, (int) valueTextWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 1.0f, false);
        backgroundWidth = (int)(valueTextWidth + leftRightPadding * 2);
        sharpCharWidth = valueTextPaint.measureText(maxWidthChars, 0, 1);
    }

    public void setColorListener(SelectColorBottomSheet.ColorListener colorListener) {
        this.colorListener = colorListener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(45));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w == oldw && h == oldh) {
            return;
        }
        if (titleLayout == null) {
            titleLayout = createTitleLayout(title);
        }
        int top = (h - valueLayout.getHeight()) / 2;
        int right = w - leftRightSpace;
        valueBackgroundRect.set(right - backgroundWidth, top - topBottomPadding, w - leftRightSpace, top + valueLayout.getHeight() + topBottomPadding);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (titleLayout != null) {
            canvas.save();
            canvas.translate(leftRightSpace, (getHeight() - titleLayout.getHeight()) * 0.5f);
            titleLayout.draw(canvas);
            canvas.restore();
        }

        float xTextTrans = valueBackgroundRect.left + (valueBackgroundRect.width() - valueLayout.getWidth()) / 2;
        float yTextTrans = valueBackgroundRect.top + topBottomPadding;
        float xTextRight = xTextTrans + valueLayout.getWidth() - (valueLayout.getWidth() - valueTextWidth) * 0.5f;

        canvas.drawRoundRect(valueBackgroundRect, backgroundRadius, backgroundRadius, valueBackgroundPaint);

        if (isValueSelected) {
            valueBackRect.set(
                    xTextRight - valueTextWidth + sharpCharWidth,
                    yTextTrans - topBottomBackPadding,
                    xTextRight,
                    yTextTrans + valueLayout.getHeight() + topBottomBackPadding
            );
            canvas.drawRoundRect(valueBackRect, backRadius, backRadius, valueBackPaint);
        }

        canvas.save();
        canvas.translate(xTextTrans, yTextTrans);
        valueLayout.draw(canvas);
        canvas.restore();
    }

    public void onClick() {
        setValueSelected(true);
        if (colorBottomSheet == null) {
            colorBottomSheet = new SelectColorBottomSheet(getContext(), true);
        }
        colorBottomSheet.setSelectedColor(color);
        colorBottomSheet.setColorListener(new SelectColorBottomSheet.ColorListener() {
            @Override
            public void onColorChanged(int color, @Nullable Object tag) {
                setColor(color);
                if (colorListener != null) {
                    colorListener.onColorChanged(color, getTag());
                }
            }
            @Override
            public void onColorApplied(int color, @Nullable Object tag) {
                if (colorListener != null) {
                    colorListener.onColorApplied(color, getTag());
                }
            }
            @Override
            public void onColorCancelled(@Nullable Object tag) {
                if (colorListener != null) {
                    colorListener.onColorCancelled(getTag());
                }
            }
        });
        colorBottomSheet.setOnDismissListener(dialog -> setValueSelected(false));
        colorBottomSheet.show();
    }

    public void setTitle(String title) {
        this.title = title;
        titleLayout = createTitleLayout(title);
    }

    public void setColor(@ColorInt int color) {
        this.color = color;
        boolean isLightColor = AndroidUtilities.isLightColor(color);

        valueBackgroundPaint.setColor(color);
        valueTextPaint.setColor(isLightColor ? Color.BLACK : Color.WHITE);
        valueBuilder.setLength(0);

        String valueText = AndroidUtilities.getRGBColorString(color).toUpperCase();
        valueTextWidth = valueTextPaint.measureText(valueText);
        valueBuilder.append(valueText);

        Color.RGBToHSV(Color.red(color), Color.green(color), Color.blue(color), hsv);
        hsv[2] -= 0.25f;
        if (hsv[2] < 0f) {
            hsv[1] = 0.0f;
            hsv[2] += 0.5f;
        }
        int backgroundSpanColor = Color.HSVToColor(hsv);
        valueBackPaint.setColor(backgroundSpanColor);

        invalidate();
    }

    public void setValueSelected(boolean valueSelected) {
        isValueSelected = valueSelected;
        invalidate();
    }

    @Nullable
    private StaticLayout createTitleLayout(String text) {
        if (getWidth() == 0 || TextUtils.isEmpty(text)) {
            return null;
        }
        int width = getWidth();
        CharSequence ellipsizedText = TextUtils.ellipsize(text, titleTextPaint, width, TextUtils.TruncateAt.END);
        return new StaticLayout(ellipsizedText, titleTextPaint, getWidth() / 2, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
    }

    private float getMaxValueWidth() {
        float maxWidth = 0;
        char maxWidthChar = 'A';
        for (char c = 'B'; c != 'G'; ++c) {
            chars[0] = c;
            float width = valueTextPaint.measureText(chars, 0, chars.length);
            if (width > maxWidth) {
                maxWidthChar = c;
                maxWidth = width;
            }
        }
        for (int i = 1; i != maxWidthChars.length; ++i) {
            maxWidthChars[i] = maxWidthChar;
        }
        return valueTextPaint.measureText(maxWidthChars, 0, maxWidthChars.length);
    }
}
