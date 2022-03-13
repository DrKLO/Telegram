/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextPaint;
import android.util.TypedValue;

import org.telegram.ui.ActionBar.Theme;

public class HintEditText extends EditTextBoldCursor {
    protected TextPaint hintPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private String hintText;
    private Rect rect = new Rect();

    public HintEditText(Context context) {
        super(context);
        hintPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
    }

    @Override
    public void setTextSize(int unit, float size) {
        super.setTextSize(unit, size);

        hintPaint.setTextSize(TypedValue.applyDimension(unit, size, getResources().getDisplayMetrics()));
    }

    public String getHintText() {
        return hintText;
    }

    public void setHintText(String value) {
        hintText = value;
        onTextChange();
        setText(getText());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        onTextChange();
    }

    public void onTextChange() {
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (hintText != null && length() < hintText.length()) {
            float offsetX = 0;
            for (int a = 0; a < hintText.length(); a++) {
                float newOffset;
                if (a < length()) {
                    newOffset = getPaint().measureText(getText(), a, a + 1);
                } else {
                    newOffset = hintPaint.measureText(hintText, a, a + 1);
                }
                if (!shouldDrawBehindText(a) && a < length()) {
                    offsetX += newOffset;
                    continue;
                }

                int color = hintPaint.getColor();
                canvas.save();
                hintPaint.getTextBounds(hintText, 0, hintText.length(), rect);
                float offsetY = (getHeight() + rect.height()) / 2f;
                onPreDrawHintCharacter(a, canvas, offsetX, offsetY);
                canvas.drawText(hintText, a, a + 1, offsetX, offsetY, hintPaint);
                offsetX += newOffset;
                canvas.restore();
                hintPaint.setColor(color);
            }
        }
        super.onDraw(canvas);
    }

    protected boolean shouldDrawBehindText(int index) {
        return false;
    }

    protected void onPreDrawHintCharacter(int index, Canvas canvas, float pivotX, float pivotY) {}
}
