package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.tgnet.TLObject;

public class AvatarsImageView extends View {

    public final AvatarsDarawable avatarsDarawable;

    public AvatarsImageView(@NonNull Context context, boolean inCall) {
        super(context);
        avatarsDarawable = new AvatarsDarawable(this, inCall);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        avatarsDarawable.width = getMeasuredWidth();
        avatarsDarawable.height = getMeasuredHeight();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatarsDarawable.onAttachedToWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        avatarsDarawable.onDraw(canvas);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        avatarsDarawable.onDetachedFromWindow();
    }


    public void setStyle(int style) {
        avatarsDarawable.setStyle(style);
    }

    public void setDelegate(Runnable delegate) {
        avatarsDarawable.setDelegate(delegate);
    }

    public void setObject(int a, int currentAccount, TLObject object) {
        avatarsDarawable.setObject(a, currentAccount, object);
    }

    public void reset() {
        avatarsDarawable.reset();
    }

    public void setCount(int usersCount) {
        avatarsDarawable.setCount(usersCount);
    }

    public void commitTransition(boolean animated) {
        avatarsDarawable.commitTransition(animated);
    }

    public void updateAfterTransitionEnd() {
        avatarsDarawable.updateAfterTransitionEnd();
    }

    public void setCentered(boolean centered) {
        avatarsDarawable.setCentered(centered);
    }
}
