package org.telegram.ui.Components.voip;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.widget.ImageView;

import androidx.core.graphics.ColorUtils;
import androidx.palette.graphics.Palette;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.Utilities;

public class VoIPOverlayBackground extends ImageView {

    int blackoutColor = ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.4f));
    boolean imageSet;
    boolean showBlackout;
    float blackoutProgress;
    ValueAnimator animator;

    public VoIPOverlayBackground(Context context) {
        super(context);
        setScaleType(ScaleType.CENTER_CROP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (blackoutProgress == 1f) {
            canvas.drawColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.4f)));
        } else if (blackoutProgress == 0f) {
            setImageAlpha(255);
            super.onDraw(canvas);
        } else {
            canvas.drawColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.4f * blackoutProgress)));
            setImageAlpha((int) (255 * (1f - blackoutProgress)));
            super.onDraw(canvas);
        }
    }

    public void setBackground(final ImageReceiver.BitmapHolder src) {
        new Thread(() -> {
            try {
                Bitmap blur1 = Bitmap.createBitmap(150, 150, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(blur1);
                canvas.drawBitmap(src.bitmap, null, new Rect(0, 0, 150, 150), new Paint(Paint.FILTER_BITMAP_FLAG));
                Utilities.blurBitmap(blur1, 3, 0, blur1.getWidth(), blur1.getHeight(), blur1.getRowBytes());
                final Palette palette = Palette.from(src.bitmap).generate();
                Paint paint = new Paint();
                paint.setColor((palette.getDarkMutedColor(0xFF547499) & 0x00FFFFFF) | 0x44000000);
                canvas.drawColor(0x26000000);
                canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), paint);
                AndroidUtilities.runOnUIThread(() -> {
                    setImageBitmap(blur1);
                    imageSet = true;
                    src.release();
                });
            } catch (Throwable ignore) {

            }
        }).start();
    }

    public void setShowBlackout(boolean showBlackout, boolean animated) {
        if (this.showBlackout == showBlackout) {
            return;
        }
        this.showBlackout = showBlackout;
        if (!animated) {
            blackoutProgress = showBlackout ? 1f : 0f;
        } else {
            ValueAnimator animator = ValueAnimator.ofFloat(blackoutProgress, showBlackout ? 1f : 0f);
            animator.addUpdateListener(valueAnimator -> {
                blackoutProgress = (float) valueAnimator.getAnimatedValue();
                invalidate();
            });
            animator.setDuration(150).start();
        }
    }
}
