package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.concurrent.atomic.AtomicBoolean;

public class ToggleButton2 extends View implements FlashViews.Invertable {

    private final Path clipPath = new Path();
    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    public ToggleButton2(Context context) {
        super(context);
        activePaint.setColor(0xFFFFFFFF);
        activeBitmapPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }

    private boolean selected;
    private AnimatedFloat animatedSelected = new AnimatedFloat(this, 0, 380, CubicBezierInterpolator.EASE_OUT_QUINT);

    private Drawable drawable;
    private Bitmap activeBitmap;

    private int currentIcon;
    private float scale = 1f;
    private ValueAnimator animator;

    public void setIcon(int iconRes, boolean animated) {
        if (currentIcon == iconRes) {
            return;
        }

        if (animator != null) {
            animator.cancel();
            animator = null;
        }

        if (animated) {
            animator = ValueAnimator.ofFloat(0, 1).setDuration(150);
            AtomicBoolean changed = new AtomicBoolean();
            animator.addUpdateListener(animation -> {
                float val = (float) animation.getAnimatedValue();
                this.scale = 0.5f + Math.abs(val - 0.5f);
                if (val >= 0.5f && !changed.get()) {
                    changed.set(true);
                    setDrawable(iconRes);
                }
            });
            animator.start();
        } else {
            scale = 1f;
            setDrawable(iconRes);
        }
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        invalidate();
    }

    @Override
    public void setInvert(float invert) {
        if (drawable != null) {
            drawable.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(0xffffffff, 0xff000000, invert), PorterDuff.Mode.MULTIPLY));
        }
        activePaint.setColor(ColorUtils.blendARGB(0xffffffff, 0xff000000, invert));
        invalidate();
    }

    private void setDrawable(int iconRes) {
        drawable = getContext().getResources().getDrawable(iconRes).mutate();
        if (activeBitmap != null) {
            activeBitmap.recycle();
            activeBitmap = null;
        }
        if (activeBitmap == null && iconRes != 0) {
            activeBitmap = BitmapFactory.decodeResource(getResources(), iconRes);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (drawable == null) {
            return;
        }

        float t = animatedSelected.set(selected);

        final int w = drawable.getIntrinsicWidth(), h = drawable.getIntrinsicHeight();

        AndroidUtilities.rectTmp2.set((getWidth() - w) / 2, (getHeight() - h) / 2, (getWidth() + w) / 2, (getHeight() + h) / 2);
        if (t <= 0) {
            drawable.setBounds(AndroidUtilities.rectTmp2);
            drawable.draw(canvas);
        } else if (t < 1) {
            canvas.save();
            clipPath.rewind();
            clipPath.addCircle(getWidth() / 2f, getHeight() / 2f, dp(16) * t, Path.Direction.CW);
            canvas.clipPath(clipPath, Region.Op.DIFFERENCE);
            drawable.setBounds(AndroidUtilities.rectTmp2);
            drawable.draw(canvas);
            canvas.restore();
        }

        if (t > 0) {
            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, dp(16) * t, activePaint);
            canvas.save();
            if (activeBitmap != null) {
                canvas.drawBitmap(activeBitmap, null, AndroidUtilities.rectTmp2, activeBitmapPaint);
            }
            canvas.restore();
            canvas.restore();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (activeBitmap == null && currentIcon != 0) {
            activeBitmap = BitmapFactory.decodeResource(getResources(), currentIcon);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (activeBitmap != null) {
            activeBitmap.recycle();
            activeBitmap = null;
        }
    }

}
