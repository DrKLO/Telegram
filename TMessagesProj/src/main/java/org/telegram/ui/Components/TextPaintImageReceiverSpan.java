/*
 * This is the source code of Telegram for Android v. 4.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.tgnet.TLRPC;

import java.util.Locale;

public class TextPaintImageReceiverSpan extends ReplacementSpan {

    public static final int ALIGN_BOTTOM = 0;
    public static final int ALIGN_BASELINE = 1;

    protected final int mVerticalAlignment;

    private ImageReceiver imageReceiver;
    private int width;
    private int height;

    public TextPaintImageReceiverSpan(View parentView, TLRPC.Document document, int w, int h) {
        mVerticalAlignment = ALIGN_BASELINE;
        String filter = String.format(Locale.US, "%d_%d", w, h);
        width = AndroidUtilities.dp(w);
        height = AndroidUtilities.dp(h);
        imageReceiver = new ImageReceiver(parentView);
        imageReceiver.setImage(document, filter, document.thumb != null ? document.thumb.location : null, filter, -1, null, 1);
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        if (fm != null) {
            fm.ascent = -height;
            fm.descent = 0;

            fm.top = fm.ascent;
            fm.bottom = 0;
        }
        return width;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        canvas.save();
        int transY = bottom - height;
        if (mVerticalAlignment == ALIGN_BASELINE) {
            transY -= paint.getFontMetricsInt().descent;
        }
        imageReceiver.setImageCoords((int) x, transY, width, height);
        imageReceiver.draw(canvas);
        canvas.restore();
    }
}