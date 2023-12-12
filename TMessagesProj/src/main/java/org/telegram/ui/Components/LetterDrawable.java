/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
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
    private static TextPaint namePaintTopic;
    private static TextPaint namePaintSmallTopic;
    private RectF rect = new RectF();

    private StaticLayout textLayout;
    private float textWidth;
    private float textHeight;
    private float textLeft;
    private StringBuilder stringBuilder = new StringBuilder(5);

    public static final int STYLE_DEFAULT = 0;
    public static final int STYLE_TOPIC_DRAWABLE = 1;
    public static final int STYLE_SMALL_TOPIC_DRAWABLE = 2;
    int style;
    final TextPaint textPaint;
    public float scale = 1f;

    public LetterDrawable() {
        this(null, 0);
    }

    public LetterDrawable(Theme.ResourcesProvider resourcesProvider, int style) {
        super();
        this.style = style;
        if (style == STYLE_DEFAULT) {
            if (namePaint == null) {
                namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            }
            namePaint.setTextSize(AndroidUtilities.dp(28));
            paint.setColor(Theme.getColor(Theme.key_sharedMedia_linkPlaceholder, resourcesProvider));
            namePaint.setColor(Theme.getColor(Theme.key_sharedMedia_linkPlaceholderText, resourcesProvider));
            textPaint = namePaint;
        } else if (style == STYLE_TOPIC_DRAWABLE) {
            if (namePaintTopic == null) {
                namePaintTopic = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            }
            namePaintTopic.setColor(Color.WHITE);
            namePaintTopic.setTextSize(AndroidUtilities.dp(13));
            namePaintTopic.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint = namePaintTopic;
        } else {
            if (namePaintSmallTopic == null) {
                namePaintSmallTopic = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            }
            namePaintSmallTopic.setColor(Color.WHITE);
            namePaintSmallTopic.setTextSize(Theme.chat_topicTextPaint.getTextSize() * .75f);
            namePaintSmallTopic.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint = namePaintSmallTopic;
        }
    }

    public void setBackgroundColor(int value) {
        paint.setColor(value);
    }

    public void setColor(int value) {
        textPaint.setColor(value);
    }

    public void setTitle(String title) {
        stringBuilder.setLength(0);
        if (title != null && title.length() > 0) {
            stringBuilder.append(title.substring(0, 1));
        }

        if (stringBuilder.length() > 0) {
            String text = stringBuilder.toString().toUpperCase();
            try {
                textLayout = new StaticLayout(text, textPaint, AndroidUtilities.dp(100), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
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
        if (style == STYLE_DEFAULT) {
            rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
        }
        canvas.save();
        if (scale != 1) {
            canvas.scale(scale, scale, bounds.centerX(), bounds.centerY());
        }
        if (textLayout != null) {
            int size = bounds.width();
            canvas.translate(bounds.left + (size - textWidth) / 2 - textLeft, bounds.top + (size - textHeight) / 2);
            textLayout.draw(canvas);
        }
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        textPaint.setAlpha(alpha);
        paint.setAlpha(alpha);
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
