/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views;

import android.view.View;
import android.widget.TextView;

import org.telegram.messenger.R;

public class MessageLayout extends FrameLayoutFixed {
    public TextView timeTextView;
    public View timeLayout;
    public TightTextView messageTextView;
    public float density = 1;
    public int maxWidth;

    public MessageLayout(android.content.Context context) {
        super(context);
    }

    public MessageLayout(android.content.Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public MessageLayout(android.content.Context context, android.util.AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int timeWidth = timeLayout != null ? timeLayout.getMeasuredWidth() : timeTextView.getMeasuredWidth();
        int totalWidth = getMeasuredWidth();


        int maxChildWidth = getChildAt(0).getMeasuredWidth();
        int count = getChildCount();
        for (int a = 1; a < count - 1; a++) {
            maxChildWidth = Math.max(maxChildWidth, getChildAt(a).getMeasuredWidth());
        }
        int timeMore = (int)(timeWidth + 6 * density);
        int fields = totalWidth - Math.max(maxChildWidth, timeWidth);
        int height = getMeasuredHeight();

        if (maxWidth - messageTextView.lastLineWidth < timeMore) {
            setMeasuredDimension(Math.max(maxChildWidth, messageTextView.lastLineWidth) + fields, (int)(height + 14 * density));
        } else {
            int diff = maxChildWidth - messageTextView.lastLineWidth;
            if (diff >= 0 && diff <= timeMore) {
                setMeasuredDimension(maxChildWidth + timeMore - diff + fields, height);
            } else {
                setMeasuredDimension(Math.max(maxChildWidth, messageTextView.lastLineWidth + timeMore) + fields, height);
            }
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        timeTextView = (TextView)findViewById(R.id.chat_time_text);
        messageTextView = (TightTextView)findViewById(R.id.chat_message_text);
        timeLayout = findViewById(R.id.chat_time_layout);
    }
}
