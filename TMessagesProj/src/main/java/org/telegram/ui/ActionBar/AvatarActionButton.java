package org.telegram.ui.ActionBar;
import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;

import org.webrtc.EglBase14;

public class AvatarActionButton extends View {
    private Drawable iconDrawable;
    private String text = "";

    private final TextPaint textPaint;
    private final RectF bounds = new RectF();

    private int iconSize;
    private int spacing;
    private int padding;
    private float textWidth;
    private float textHeight;

    public AvatarActionButton(Context context) {
        this(context, null);
    }

    public AvatarActionButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AvatarActionButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = context.getResources().getDisplayMetrics().density;
        iconSize = dp(24);
        spacing = dp(4);
        padding = dp(12);

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14, context.getResources().getDisplayMetrics()));
        textPaint.setTextAlign(Paint.Align.CENTER);

        TypedValue outValue = new TypedValue();
        int attr;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            attr = android.R.attr.selectableItemBackgroundBorderless;
        } else {
            attr = android.R.attr.selectableItemBackground;
        }

        getContext().getTheme().resolveAttribute(attr, outValue, true);
        Drawable backgroundDrawable = ContextCompat.getDrawable(context, outValue.resourceId);
        setBackground(backgroundDrawable);

        setClickable(true);
    }

    public void setIcon(@DrawableRes int resId) {
        iconDrawable = ContextCompat.getDrawable(getContext(), resId);
        if (iconDrawable != null) {
            iconDrawable.setBounds(0, 0, iconSize, iconSize);
        }
        invalidate();
    }

    public void setText(String txt) {
        text = txt != null ? txt : "";
        measureText();
        requestLayout();
        invalidate();
    }

    private void measureText() {
        textWidth = textPaint.measureText(text);
        Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
        textHeight = fontMetrics.bottom - fontMetrics.top;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        measureText();
        int desiredWidth = (int) (padding * 2 + Math.max(iconSize, textWidth) + spacing);
        int desiredHeight = (int) (padding * 2 + iconSize + textHeight + spacing);
        int measuredWidth = resolveSize(desiredWidth, widthMeasureSpec);
        int measuredHeight = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (iconDrawable != null) {
            float cx = (getWidth() - iconSize) * 0.5f;
            canvas.save();
            canvas.translate(cx, padding);
            iconDrawable.draw(canvas);
            canvas.restore();
        }

        float x = getWidth() * 0.5f;
        float y = padding + iconSize + spacing - textPaint.getFontMetrics().ascent;
        canvas.drawText(text, x, y, textPaint);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }


}
