/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.FrameLayoutFixed;
import org.telegram.ui.Components.LayoutHelper;

public class StickerCell extends FrameLayoutFixed {

    private BackupImageView imageView;
    private TLRPC.Document sticker;
    private long lastUpdateTime;
    private boolean scaled;
    private float scale;
    private long time = 0;
    private static AccelerateInterpolator interpolator = new AccelerateInterpolator(0.5f);

    public StickerCell(Context context) {
        super(context);

        imageView = new BackupImageView(context);
        imageView.setAspectFit(true);
        addView(imageView, LayoutHelper.createFrame(66, 66, Gravity.CENTER_HORIZONTAL, 0, 5, 0, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(76) + getPaddingLeft() + getPaddingRight(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(78), MeasureSpec.EXACTLY));
    }

    @Override
    public void setPressed(boolean pressed) {
        if (imageView.getImageReceiver().getPressed() != pressed) {
            imageView.getImageReceiver().setPressed(pressed);
            imageView.invalidate();
        }
        super.setPressed(pressed);
    }

    public void setSticker(TLRPC.Document document, int side) {
        if (document != null && document.thumb != null) {
            imageView.setImage(document.thumb.location, null, "webp", null);
        }
        sticker = document;
        if (side == -1) {
            setBackgroundResource(R.drawable.stickers_back_left);
            setPadding(AndroidUtilities.dp(7), 0, 0, 0);
        } else if (side == 0) {
            setBackgroundResource(R.drawable.stickers_back_center);
            setPadding(0, 0, 0, 0);
        } else if (side == 1) {
            setBackgroundResource(R.drawable.stickers_back_right);
            setPadding(0, 0, AndroidUtilities.dp(7), 0);
        } else if (side == 2) {
            setBackgroundResource(R.drawable.stickers_back_all);
            setPadding(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(3), 0);
        }
        if (getBackground() != null) {
            getBackground().setAlpha(230);
        }
    }

    public TLRPC.Document getSticker() {
        return sticker;
    }

    public void setScaled(boolean value) {
        scaled = value;
        lastUpdateTime = System.currentTimeMillis();
        invalidate();
    }

    public boolean showingBitmap() {
        return imageView.getImageReceiver().getBitmap() != null;
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (child == imageView && (scaled && scale != 0.8f || !scaled && scale != 1.0f)) {
            long newTime = System.currentTimeMillis();
            long dt = (newTime - lastUpdateTime);
            lastUpdateTime = newTime;
            if (scaled && scale != 0.8f) {
                scale -= dt / 400.0f;
                if (scale < 0.8f) {
                    scale = 0.8f;
                }
            } else {
                scale += dt / 400.0f;
                if (scale > 1.0f) {
                    scale = 1.0f;
                }
            }
            if (Build.VERSION.SDK_INT >= 11) {
                imageView.setScaleX(scale);
                imageView.setScaleY(scale);
            }
            imageView.invalidate();
            invalidate();
        }
        return result;
    }
}
