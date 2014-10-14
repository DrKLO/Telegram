/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.MessageObject;
import org.telegram.android.MessagesController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

public class ChatActionCell extends BaseCell {

    private static Drawable backgroundBlack;
    private static Drawable backgroundBlue;
    private static TextPaint textPaint;

    private StaticLayout textLayout;
    private int textWidth = 0;
    private int textHeight = 0;
    private int textX = 0;
    private int textXLeft = 0;
    private int textY = 0;
    private boolean useBlackBackground = false;
    private boolean wasLayout = false;

    private MessageObject currentMessageObject;

    public ChatActionCell(Context context) {
        super(context);
        if (backgroundBlack == null) {
            backgroundBlack = getResources().getDrawable(R.drawable.system_black);
            backgroundBlue = getResources().getDrawable(R.drawable.system_blue);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(0xffffffff);
        }
        textPaint.setTextSize(AndroidUtilities.dp(MessagesController.getInstance().fontSize));
    }

    public void setMessageObject(MessageObject messageObject) {
        if (currentMessageObject == messageObject) {
            return;
        }
        currentMessageObject = messageObject;
        int size;
        if (AndroidUtilities.isTablet()) {
            size = AndroidUtilities.getMinTabletSide();
        } else {
            size = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
        }
        textLayout = new StaticLayout(currentMessageObject.messageText, textPaint, size - AndroidUtilities.dp(30), Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
        textHeight = 0;
        textWidth = 0;
        try {
            int linesCount = textLayout.getLineCount();
            boolean hasNonRTL = false;
            for (int a = 0; a < linesCount; a++) {
                float lineWidth = 0;
                float lineLeft = 0;
                try {
                    lineWidth = textLayout.getLineWidth(a);
                    lineLeft = textLayout.getLineLeft(a);
                    textHeight = (int)Math.max(textHeight, Math.ceil(textLayout.getLineBottom(a)));
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                    return;
                }

                if (lineLeft == 0) {
                    hasNonRTL = true;
                }
                textWidth = (int)Math.max(textWidth, Math.ceil(lineWidth));
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        textY = AndroidUtilities.dp(7);
        wasLayout = false;
        requestLayout();
    }

    public void setUseBlackBackground(boolean value) {
        useBlackBackground = value;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), textHeight + AndroidUtilities.dp(14));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (currentMessageObject == null) {
            super.onLayout(changed, left, top, right, bottom);
            return;
        }
        if (!wasLayout || changed) {
            textX = (right - left - textWidth) / 2;
            textXLeft = (right - left - textLayout.getWidth()) / 2;

            wasLayout = true;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (currentMessageObject == null) {
            return;
        }
        if (!wasLayout) {
            requestLayout();
            return;
        }

        Drawable backgroundDrawable = null;
        if (useBlackBackground) {
            backgroundDrawable = backgroundBlack;
        } else {
            backgroundDrawable = backgroundBlue;
        }
        backgroundDrawable.setBounds(textX - AndroidUtilities.dp(5), AndroidUtilities.dp(5), textX + textWidth + AndroidUtilities.dp(5), getMeasuredHeight() - AndroidUtilities.dp(5));
        backgroundDrawable.draw(canvas);

        canvas.save();
        canvas.translate(textXLeft, textY);
        textLayout.draw(canvas);
        canvas.restore();
    }
}
