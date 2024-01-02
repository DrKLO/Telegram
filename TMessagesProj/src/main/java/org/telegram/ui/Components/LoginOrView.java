package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public class LoginOrView extends View {
    private final static int LINE_SIZE_DP = 64;

    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String string;
    private Rect textBounds = new Rect();

    private View measureAfter;

    public LoginOrView(Context context) {
        super(context);

        string = LocaleController.getString(R.string.LoginOrSingInWithGoogle);
        textPaint.setTextSize(AndroidUtilities.dp(14));
        updateColors();
    }

    public void setMeasureAfter(View measureAfter) {
        this.measureAfter = measureAfter;
    }

    public void updateColors() {
        textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        linePaint.setColor(Theme.getColor(Theme.key_sheet_scrollUp));
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (measureAfter != null) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(measureAfter.getMeasuredWidth()), MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        textPaint.getTextBounds(string, 0, string.length(), textBounds);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float lineSize = measureAfter != null ? (getWidth() - textBounds.width() - AndroidUtilities.dp(8) - measureAfter.getPaddingLeft() - measureAfter.getPaddingRight()) / 2f : AndroidUtilities.dp(LINE_SIZE_DP);
        canvas.drawLine((getWidth() - textBounds.width()) / 2f - AndroidUtilities.dp(8) - lineSize, getHeight() / 2f, (getWidth() - textBounds.width()) / 2f - AndroidUtilities.dp(8), getHeight() / 2f, linePaint);
        canvas.drawLine((getWidth() + textBounds.width()) / 2f + AndroidUtilities.dp(8), getHeight() / 2f, (getWidth() + textBounds.width()) / 2f + AndroidUtilities.dp(8) + lineSize, getHeight() / 2f, linePaint);
        canvas.drawText(string, (getWidth() - textBounds.width()) / 2f, (getHeight() + textBounds.height()) / 2f, textPaint);
    }
}
