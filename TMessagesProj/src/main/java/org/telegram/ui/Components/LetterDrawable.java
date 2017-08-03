/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

public class LetterDrawable extends Drawable {

    public static Paint paint = new Paint();
    private static TextPaint namePaint;

    private StaticLayout textLayout;
    private float textWidth;
    private float textHeight;
    private float textLeft;
    private StringBuilder stringBuilder = new StringBuilder(5);

    public LetterDrawable() {
        super();

        if (namePaint == null) {
            paint.setColor(Theme.getColor(Theme.key_sharedMedia_linkPlaceholder));
            namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            namePaint.setColor(Theme.getColor(Theme.key_sharedMedia_linkPlaceholderText));
        }
        namePaint.setTextSize(AndroidUtilities.dp(28));
    }

    public void setBackgroundColor(int value) {
        paint.setColor(value);
    }

    public void setColor(int value) {
        namePaint.setColor(value);
    }

    public void setTitle(String title) {
        stringBuilder.setLength(0);
        if (title != null && title.length() > 0) {
            stringBuilder.append(title.substring(0, 1));
        }

        if (stringBuilder.length() > 0) {
            String text = stringBuilder.toString().toUpperCase();
            try {
                textLayout = new StaticLayout(text, namePaint, AndroidUtilities.dp(100), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                if (textLayout.getLineCount() > 0) {
                    textLeft = textLayout.getLineLeft(0);
                    textWidth = textLayout.getLineWidth(0);
                    textHeight = textLayout.getLineBottom(0);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else {
            textLayout = null;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds == null) {
            return;
        }
        int size = bounds.width();
        canvas.save();
        canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.bottom, paint);
        if (textLayout != null) {
            canvas.translate(bounds.left + (size - textWidth) / 2 - textLeft, bounds.top + (size - textHeight) / 2);
            textLayout.draw(canvas);
        }
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(ColorFilter cf) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return 0;
    }

    @Override
    public int getIntrinsicHeight() {
        return 0;
    }
}
