package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ShapeDrawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Animations.SelectColorBottomSheet;
import org.telegram.ui.Components.TextRoundedBackgroundColorSpan;

public class SelectColorCell extends ViewGroup implements SelectColorBottomSheet.ColorListener {

    private static final int leftRightSpace = AndroidUtilities.dp(21);
    private static final char[] chars = new char[1];
    private static final char[] maxWidthChars = new char[] { '#', 'A', 'A', 'A', 'A', 'A', 'A' };

    private final TextRoundedBackgroundColorSpan colorSpan = new TextRoundedBackgroundColorSpan(Color.TRANSPARENT, AndroidUtilities.dp(2));
    private final TextView titleText = new TextView(getContext());
    private final TextView colorText = new TextView(getContext());
    private final float[] hsv = new float[3];

    private SelectColorBottomSheet colorBottomSheet;
    private int color = Color.WHITE;

    public SelectColorCell(@NonNull Context context) {
        super(context);

        titleText.setEllipsize(TextUtils.TruncateAt.END);
        titleText.setSingleLine(true);
        titleText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        addView(titleText);

        colorText.setGravity(Gravity.CENTER);
        colorText.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(7), Color.TRANSPARENT));
        colorText.setIncludeFontPadding(false);
        int lrPadding = AndroidUtilities.dp(4);
        int tbPadding = AndroidUtilities.dp(6);
        colorText.setPadding(lrPadding, tbPadding, lrPadding, tbPadding);
        colorText.setSingleLine(true);
        colorText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        colorText.setOnClickListener(v -> {
            if (colorBottomSheet == null) {
                colorBottomSheet = new SelectColorBottomSheet(getContext(), true);
            }
            colorBottomSheet.setSelectedColor(color);
            colorBottomSheet.setColorListener(this);
            colorBottomSheet.show();
        });

        addView(colorText);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(45));

        float maxWidth = 0;
        char maxWidthChar = 'A';
        for (char c = 'B'; c != 'G'; ++c) {
            chars[0] = c;
            float width = colorText.getPaint().measureText(chars, 0, chars.length);
            if (width > maxWidth) {
                maxWidthChar = c;
                maxWidth = width;
            }
        }
        for (int i = 1; i != maxWidthChars.length; ++i) {
            maxWidthChars[i] = maxWidthChar;
        }

        CharSequence oldText = colorText.getText();
        colorText.setText(maxWidthChars, 0, maxWidthChars.length);
        colorText.measure(
                MeasureSpec.makeMeasureSpec(getMeasuredWidth() / 2, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST)
        );
        colorText.setText(oldText);

        titleText.measure(
                MeasureSpec.makeMeasureSpec(getMeasuredWidth() - colorText.getMeasuredWidth() - leftRightSpace * 3, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST)
        );
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (!changed) {
            return;
        }
        int t = (bottom - top - colorText.getMeasuredHeight()) / 2;
        int r = right - leftRightSpace;
        colorText.layout(r - colorText.getMeasuredWidth(), t, r, t + colorText.getMeasuredHeight());
        t = (bottom - top - titleText.getMeasuredHeight()) / 2;
        titleText.layout(leftRightSpace, t, leftRightSpace + titleText.getMeasuredWidth(), t + titleText.getMeasuredHeight());
    }

    @Override
    public void onColorChanged(int color) {
        setColor(color);
    }

    public void setTitle(String title) {
        titleText.setText(title);
    }

    public void setColor(@ColorInt int color) {
        this.color = color;
        boolean isLightColor = AndroidUtilities.isLightColor(color);

        ((ShapeDrawable) colorText.getBackground()).getPaint().setColor(color);
        colorText.setTextColor(isLightColor ? Color.BLACK : Color.WHITE);

        Color.RGBToHSV(Color.red(color), Color.green(color), Color.blue(color), hsv);
        hsv[2] -= 0.25f;
        if (hsv[2] < 0f) {
            hsv[2] += 0.5f;
        }
        int backgroundSpanColor = Color.HSVToColor(hsv);

        String colorTextString = AndroidUtilities.getRGBColorString(color).toUpperCase();
        SpannableString spannableString = new SpannableString(colorTextString);
        spannableString.setSpan(new BackgroundColorSpan(backgroundSpanColor), 1, colorTextString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        colorText.setText(spannableString);

        invalidate();
    }
}
