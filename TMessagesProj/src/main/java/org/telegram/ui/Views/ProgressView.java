/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Views;

import android.graphics.Canvas;
import android.graphics.Paint;

import org.telegram.messenger.Utilities;

public class ProgressView {
    private static Paint innerPaint1;
    private static Paint outerPaint1;
    private static Paint innerPaint2;
    private static Paint outerPaint2;

    public int type;
    public int thumbX = 0;
    public int width;
    public int height;

    public ProgressView() {
        if (innerPaint1 == null) {
            innerPaint1 = new Paint();
            outerPaint1 = new Paint();
            innerPaint2 = new Paint();
            outerPaint2 = new Paint();

            innerPaint1.setColor(0xffb4e396);
            outerPaint1.setColor(0xff6ac453);
            innerPaint2.setColor(0xffd9e2eb);
            outerPaint2.setColor(0xff86c5f8);
        }
    }

    public void setProgress(float progress) {
        thumbX = (int)Math.ceil(width * progress);
        if (thumbX < 0) {
            thumbX = 0;
        } else if (thumbX > width) {
            thumbX = width;
        }
    }

    public void draw(Canvas canvas) {
        Paint inner = null;
        Paint outer = null;
        if (type == 0) {
            inner = innerPaint1;
            outer = outerPaint1;
        } else if (type == 1) {
            inner = innerPaint2;
            outer = outerPaint2;
        }
        canvas.drawRect(0, height / 2 - Utilities.dp(1), width, height / 2 + Utilities.dp(1), inner);
        canvas.drawRect(0, height / 2 - Utilities.dp(1), thumbX, height / 2 + Utilities.dp(1), outer);
    }
}
