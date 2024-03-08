package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.util.StateSet;
import android.view.Gravity;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

public class ClickableAnimatedTextView extends AnimatedTextView {

    private final Rect bounds = new Rect();
    public ClickableAnimatedTextView(Context context) {
        super(context);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        bounds.set(getDrawable().getBounds());
        int w = (int) Math.ceil(getDrawable().getCurrentWidth());
        if (getDrawable().getGravity() == Gravity.LEFT) {
            bounds.right = bounds.left + w;
        } else if (getDrawable().getGravity() == Gravity.RIGHT) {
            bounds.left = bounds.right - w;
        } else if (getDrawable().getGravity() == Gravity.CENTER) {
            int cx = (bounds.left + bounds.right) / 2;
            bounds.left = cx - w / 2;
            bounds.right = cx + w / 2;
        }
        bounds.left -= getPaddingLeft();
        bounds.top -= getPaddingTop();
        bounds.right += getPaddingRight();
        bounds.bottom += getPaddingBottom();
        backgroundDrawable.setBounds(bounds);
        backgroundDrawable.draw(canvas);
        super.onDraw(canvas);
    }

    public Rect getClickBounds() {
        return bounds;
    }

    private Drawable backgroundDrawable;

    @Override
    public void setBackground(Drawable background) {
        if (backgroundDrawable != null) {
            backgroundDrawable.setCallback(null);
        }
        backgroundDrawable = background;
        if (backgroundDrawable != null) {
            backgroundDrawable.setCallback(this);
        }
        invalidate();
    }

    @Override
    public void setBackgroundDrawable(Drawable background) {
        if (backgroundDrawable != null) {
            backgroundDrawable.setCallback(null);
        }
        backgroundDrawable = background;
        if (backgroundDrawable != null) {
            backgroundDrawable.setCallback(this);
        }
        invalidate();
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == backgroundDrawable || super.verifyDrawable(who);
    }

    private boolean pressed;
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final boolean hit = getClickBounds().contains((int) event.getX(), (int) event.getY());
        if (event.getAction() == MotionEvent.ACTION_DOWN && hit) {
            pressed = true;
            if (backgroundDrawable != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    backgroundDrawable.setHotspot(event.getX(), event.getY());
                }
                backgroundDrawable.setState(new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled});
            }
            invalidate();
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (pressed && hit) {
                callOnClick();
            }
            pressed = false;
            if (backgroundDrawable != null) {
                backgroundDrawable.setState(StateSet.NOTHING);
            }
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            pressed = false;
            if (backgroundDrawable != null) {
                backgroundDrawable.setState(StateSet.NOTHING);
            }
        }
        return hit;
    }
}
