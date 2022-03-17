package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ChatActivity;

public class ChatBlurredFrameLayout extends FrameLayout {

    ChatActivity chatActivity;
    protected Paint backgroundPaint;
    public int backgroundColor = Color.TRANSPARENT;
    public int backgroundPaddingBottom;
    public int backgroundPaddingTop;
    public boolean isTopView = true;
    public boolean drawBlur = true;

    public ChatBlurredFrameLayout(@NonNull Context context, ChatActivity chatActivity) {
        super(context);
        this.chatActivity = chatActivity;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (SharedConfig.chatBlurEnabled() && chatActivity != null && drawBlur && backgroundColor != Color.TRANSPARENT) {
            if (backgroundPaint == null) {
                backgroundPaint = new Paint();
            }
            backgroundPaint.setColor(backgroundColor);
            AndroidUtilities.rectTmp2.set(0, backgroundPaddingTop, getMeasuredWidth(), getMeasuredHeight() - backgroundPaddingBottom);
            float y = 0;
            View view = this;
            while (view != chatActivity.contentView) {
                y += view.getY();
                view = (View) view.getParent();
            }
            chatActivity.contentView.drawBlur(canvas, y, AndroidUtilities.rectTmp2, backgroundPaint, isTopView);
        }
        super.dispatchDraw(canvas);
    }

    @Override
    public void setBackgroundColor(int color) {
        if (SharedConfig.chatBlurEnabled() && chatActivity != null) {
            backgroundColor = color;
        } else {
            super.setBackgroundColor(color);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        if (SharedConfig.chatBlurEnabled() && chatActivity != null) {
            chatActivity.contentView.blurBehindViews.add(this);
        }
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (chatActivity != null) {
            chatActivity.contentView.blurBehindViews.remove(this);
        }
        super.onDetachedFromWindow();
    }
}
