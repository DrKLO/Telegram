package org.telegram.utils.pip;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;

/**
 * Full-window layer on top of the activity decor view. While the activity is entering
 * system PiP the render target sits exactly at the source rect hint; once the window
 * has shrunk to PiP size it fills the whole window.
 */
@SuppressLint("ViewConstructor")
final class SystemPipLayout extends ViewGroup {
    private final Activity activity;
    private final Rect sourceRect = new Rect();
    private @Nullable View target;

    private int originalWidth, originalHeight;
    private boolean isViewInPip;

    SystemPipLayout(Activity activity) {
        super(activity);
        this.activity = activity;
        setWillNotDraw(false);
    }

    void setTarget(@Nullable View target, @Nullable Rect sourceRect) {
        if (this.target != null && this.target != target) {
            removeView(this.target);
        }
        this.target = target;
        if (sourceRect != null) {
            this.sourceRect.set(sourceRect);
        } else {
            this.sourceRect.setEmpty();
        }
        if (target != null && target.getParent() != this) {
            AndroidUtilities.removeFromParent(target);
            addView(target);
        }
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        final boolean isActivityInPip = AndroidUtilities.isInPictureInPictureMode(activity);
        if (!isActivityInPip) {
            originalWidth = width;
            originalHeight = height;
        }
        isViewInPip = isActivityInPip && (width < originalWidth || height < originalHeight);
        setMeasuredDimension(width, height);

        if (target != null) {
            final boolean fill = isViewInPip || sourceRect.isEmpty();
            target.measure(
                MeasureSpec.makeMeasureSpec(fill ? width : sourceRect.width(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(fill ? height : sourceRect.height(), MeasureSpec.EXACTLY)
            );
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (target == null) {
            return;
        }
        if (isViewInPip || sourceRect.isEmpty()) {
            target.layout(0, 0, r - l, b - t);
        } else {
            target.layout(sourceRect.left, sourceRect.top, sourceRect.right, sourceRect.bottom);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (target != null && isViewInPip) {
            canvas.drawColor(0xFF000000);
        }
    }
}
