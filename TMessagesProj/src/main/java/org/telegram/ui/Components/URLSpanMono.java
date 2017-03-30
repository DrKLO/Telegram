/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.ui.ActionBar.Theme;

public class URLSpanMono extends MetricAffectingSpan {

    private CharSequence currentMessage;
    private int currentStart;
    private int currentEnd;
    private boolean isOut;

    public URLSpanMono(CharSequence message, int start, int end, boolean out) {
        currentMessage = message;
        currentStart = start;
        currentEnd = end;
    }

    public void copyToClipboard() {
        AndroidUtilities.addToClipboard(currentMessage.subSequence(currentStart, currentEnd).toString());
    }

    @Override
    public void updateMeasureState(TextPaint p) {
        p.setTypeface(Typeface.MONOSPACE);
        p.setTextSize(AndroidUtilities.dp(MessagesController.getInstance().fontSize - 1));
        p.setFlags(p.getFlags() | Paint.SUBPIXEL_TEXT_FLAG);
    }

    @Override
    public void updateDrawState(TextPaint ds) {
        ds.setTextSize(AndroidUtilities.dp(MessagesController.getInstance().fontSize - 1));
        ds.setTypeface(Typeface.MONOSPACE);
        ds.setUnderlineText(false);
        if (isOut) {
            ds.setColor(Theme.getColor(Theme.key_chat_messageTextOut));
        } else {
            ds.setColor(Theme.getColor(Theme.key_chat_messageTextIn));
        }
    }
}
