package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class ReplaceableIconDrawable extends Drawable implements Animator.AnimatorListener {

    private Context context;
    private ColorFilter colorFilter;
    private int currentResId = 0;

    private Drawable currentDrawable;
    private Drawable outDrawable;

    private ValueAnimator animation;
    private float progress = 1f;
    ArrayList<View> parentViews = new ArrayList<>();
    public boolean exactlyBounds;

    public ReplaceableIconDrawable(Context context) {
        this.context = context;
    }


    public void setIcon(@DrawableRes int resId, boolean animated) {
        if (currentResId == resId) {
            return;
        }
        setIcon(ContextCompat.getDrawable(context, resId).mutate(), animated);
        currentResId = resId;
    }

    public Drawable getIcon() {
        return currentDrawable;
    }

    public void setIcon(Drawable drawable, boolean animated) {
        if (drawable == null) {
            currentDrawable = null;
            outDrawable = null;
            invalidateSelf();
            return;
        }

        if (getBounds() == null || getBounds().isEmpty()) {
            animated = false;
        }

        if (drawable == currentDrawable) {
            currentDrawable.setColorFilter(colorFilter);
            return;
        }

        currentResId = 0;
        outDrawable = currentDrawable;
        currentDrawable = drawable;
        currentDrawable.setColorFilter(colorFilter);

        updateBounds(currentDrawable, getBounds());
        updateBounds(outDrawable, getBounds());

        if (animation != null) {
            animation.removeAllListeners();
            animation.cancel();
        }

        if (!animated) {
            progress = 1f;
            outDrawable = null;
            return;
        }

        animation = ValueAnimator.ofFloat(0, 1f);
        animation.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidateSelf();
        });
        animation.addListener(this);
        animation.setDuration(150);
        animation.start();
    }


    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        updateBounds(currentDrawable, bounds);
        updateBounds(outDrawable, bounds);
    }

    private void updateBounds(Drawable d, Rect bounds) {
        if (d == null) {
            return;
        }
        if (exactlyBounds) {
            d.setBounds(bounds);
            return;
        }
        int left;
        int right;
        int bottom;
        int top;

        if (d.getIntrinsicHeight() < 0) {
            top = bounds.top;
            bottom = bounds.bottom;
        } else {
            int offset = (bounds.height() - d.getIntrinsicHeight()) / 2;
            top = bounds.top + offset;
            bottom = bounds.top + offset + d.getIntrinsicHeight();
        }


        if (d.getIntrinsicWidth() < 0) {
            left = bounds.left;
            right = bounds.right;
        } else {
            int offset = (bounds.width() - d.getIntrinsicWidth()) / 2;
            left = bounds.left + offset;
            right = bounds.left + offset + d.getIntrinsicWidth();
        }
        d.setBounds(left, top, right, bottom);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int cX = getBounds().centerX();
        int cY = getBounds().centerY();

        if (progress != 1f && currentDrawable != null) {
            canvas.save();
            canvas.scale(progress, progress, cX, cY);
            currentDrawable.setAlpha((int) (255 * progress));
            currentDrawable.draw(canvas);
            canvas.restore();
        } else if (currentDrawable != null) {
            currentDrawable.setAlpha(255);
            currentDrawable.draw(canvas);
        }

        if (progress != 1f && outDrawable != null) {
            float progressRev = 1f - progress;
            canvas.save();
            canvas.scale(progressRev, progressRev, cX, cY);
            outDrawable.setAlpha((int) (255 * progressRev));
            outDrawable.draw(canvas);
            canvas.restore();
        } else if (outDrawable != null) {
            outDrawable.setAlpha(255);
            outDrawable.draw(canvas);
        }
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        this.colorFilter = colorFilter;
        if (currentDrawable != null) {
            currentDrawable.setColorFilter(colorFilter);
        }
        if (outDrawable != null) {
            outDrawable.setColorFilter(colorFilter);
        }
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        outDrawable = null;
        invalidateSelf();
    }

    @Override
    public void onAnimationStart(Animator animation) {

    }


    @Override
    public void onAnimationCancel(Animator animation) {

    }

    @Override
    public void onAnimationRepeat(Animator animation) {

    }

    public void addView(View view) {
        if (!parentViews.contains(view)) {
            parentViews.add(view);
        }
    }

    @Override
    public void invalidateSelf() {
        super.invalidateSelf();
        if (parentViews != null) {
            for (int i = 0; i < parentViews.size(); i++) {
                parentViews.get(i).invalidate();
            }
        }
    }

    public void removeView(View view) {
        parentViews.remove(view);
    }
}