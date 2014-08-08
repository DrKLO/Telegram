/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Views;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import org.telegram.android.AndroidUtilities;

public class RoundProgressView {
    private Paint paint;

    public float currentProgress = 0;
    public RectF rect = new RectF();

    public RoundProgressView() {
        paint = new Paint();
        paint.setColor(0xffffffff);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1));
        paint.setAntiAlias(true);
    }

    public void setProgress(float progress) {
        currentProgress = progress;
        if (currentProgress < 0) {
            currentProgress = 0;
        } else if (currentProgress > 1) {
            currentProgress = 1;
        }
    }

    public void draw(Canvas canvas) {
        canvas.drawArc(rect, -90, 360 * currentProgress, false, paint);
    }
}
