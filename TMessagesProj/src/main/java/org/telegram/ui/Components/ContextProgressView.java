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
import android.graphics.RectF;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class ContextProgressView extends View {

    private Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF cicleRect = new RectF();
    private int radOffset = 0;
    private long lastUpdateTime;
    private int currentColorType;
    private String innerKey;
    private String outerKey;
    private int innerColor;
    private int outerColor;

    public ContextProgressView(Context context, int colorType) {
        super(context);
        innerPaint.setStyle(Paint.Style.STROKE);
        innerPaint.setStrokeWidth(AndroidUtilities.dp(2));
        outerPaint.setStyle(Paint.Style.STROKE);
        outerPaint.setStrokeWidth(AndroidUtilities.dp(2));
        outerPaint.setStrokeCap(Paint.Cap.ROUND);
        if (colorType == 0) {
            innerKey = Theme.key_contextProgressInner1;
            outerKey = Theme.key_contextProgressOuter1;
        } else if (colorType == 1) {
            innerKey = Theme.key_contextProgressInner2;
            outerKey = Theme.key_contextProgressOuter2;
        } else if (colorType == 2) {
            innerKey = Theme.key_contextProgressInner3;
            outerKey = Theme.key_contextProgressOuter3;
        } else if (colorType == 3) {
            innerKey = Theme.key_contextProgressInner4;
            outerKey = Theme.key_contextProgressOuter4;
        }
        updateColors();
    }

    public void setColors(int innerColor, int outerColor) {
        innerKey = null;
        outerKey = null;
        this.innerColor = innerColor;
        this.outerColor = outerColor;
        updateColors();
    }

    public void updateColors() {
        if (innerKey != null) {
            innerPaint.setColor(Theme.getColor(innerKey));
        } else {
            innerPaint.setColor(innerColor);
        }
        if (outerKey != null) {
            outerPaint.setColor(Theme.getColor(outerKey));
        } else {
            outerPaint.setColor(outerColor);
        }
        invalidate();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        lastUpdateTime = System.currentTimeMillis();
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastUpdateTime = System.currentTimeMillis();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getVisibility() != VISIBLE) {
            return;
        }
        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        lastUpdateTime = newTime;
        radOffset += 360 * dt / 1000.0f;

        int x = getMeasuredWidth() / 2 - AndroidUtilities.dp(9);
        int y = getMeasuredHeight() / 2 - AndroidUtilities.dp(9);
        cicleRect.set(x, y, x + AndroidUtilities.dp(18), y + AndroidUtilities.dp(18));
        canvas.drawCircle(getMeasuredWidth() / 2, getMeasuredHeight() / 2, AndroidUtilities.dp(9), innerPaint);
        canvas.drawArc(cicleRect, -90 + radOffset, 90, false, outerPaint);
        invalidate();
    }
}
