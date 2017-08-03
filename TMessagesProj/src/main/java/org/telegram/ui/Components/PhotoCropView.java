/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Build;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.Crop.CropRotationWheel;
import org.telegram.ui.Components.Crop.CropView;

public class PhotoCropView extends FrameLayout {

    public interface PhotoCropViewDelegate {
        void needMoveImageTo(float x, float y, float s, boolean animated);
        Bitmap getBitmap();

        void onChange(boolean reset);
    }

    private boolean freeformCrop = true;
    private float rectSizeX = 600;
    private float rectSizeY = 600;
    private int draggingState = 0;
    private int orientation;
    private float oldX = 0, oldY = 0;
    private int bitmapWidth = 1, bitmapHeight = 1, bitmapX, bitmapY;
    private float rectX = -1, rectY = -1;
    private float bitmapGlobalScale = 1;
    private float bitmapGlobalX = 0;
    private float bitmapGlobalY = 0;
    private PhotoCropViewDelegate delegate;
    private Bitmap bitmapToEdit;
    private boolean showOnSetBitmap;

    private RectF animationStartValues;
    private RectF animationEndValues;
    private Runnable animationRunnable;

    private CropView cropView;
    private CropRotationWheel wheelView;

    public PhotoCropView(Context context) {
        super(context);
    }

    public void setBitmap(Bitmap bitmap, int rotation, boolean freeform) {
        bitmapToEdit = bitmap;
        rectSizeX = 600;
        rectSizeY = 600;
        draggingState = 0;
        oldX = 0;
        oldY = 0;
        bitmapWidth = 1;
        bitmapHeight = 1;
        rectX = -1;
        rectY = -1;
        freeformCrop = freeform;
        orientation = rotation;
        requestLayout();

        if (cropView == null) {
            cropView = new CropView(getContext());
            cropView.setListener(new CropView.CropViewListener() {
                @Override
                public void onChange(boolean reset) {
                    if (delegate != null) {
                        delegate.onChange(reset);
                    }
                }

                @Override
                public void onAspectLock(boolean enabled) {
                    wheelView.setAspectLock(enabled);
                }
            });
            cropView.setBottomPadding(AndroidUtilities.dp(64));
            addView(cropView);

            wheelView = new CropRotationWheel(getContext());
            wheelView.setListener(new CropRotationWheel.RotationWheelListener() {
                @Override
                public void onStart() {
                    cropView.onRotationBegan();
                }

                @Override
                public void onChange(float angle) {
                    cropView.setRotation(angle);
                    if (delegate != null) {
                        delegate.onChange(false);
                    }
                }

                @Override
                public void onEnd(float angle) {
                    cropView.onRotationEnded();
                }

                @Override
                public void aspectRatioPressed() {
                    cropView.showAspectRatioDialog();
                }

                @Override
                public void rotate90Pressed() {
                    wheelView.reset();
                    cropView.rotate90Degrees();
                }
            });
            addView(wheelView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER | Gravity.BOTTOM, 0, 0, 0, 0));
        }

        cropView.setVisibility(VISIBLE);
        cropView.setBitmap(bitmap, rotation, freeform);

        if (showOnSetBitmap) {
            showOnSetBitmap = false;
            cropView.show();
        }

        wheelView.setFreeform(freeform);
        wheelView.reset();
    }

    public void setOrientation(int rotation) {
        orientation = rotation;
        rectX = -1;
        rectY = -1;
        rectSizeX = 600;
        rectSizeY = 600;
        delegate.needMoveImageTo(0, 0, 1, false);
        requestLayout();
    }

    public boolean isReady() {
        return cropView.isReady();
    }

    public void reset() {
        wheelView.reset();
        cropView.reset();
    }

    public void onAppear() {
        if (cropView != null) {
            cropView.willShow();
        }
    }

    public void onAppeared() {
        if (cropView != null) {
            cropView.show();
        } else {
            showOnSetBitmap = true;
        }
    }

    public void onDisappear() {
        cropView.hide();
    }

    public float getRectX() {
        return cropView.getCropLeft() - AndroidUtilities.dp(14);
    }

    public float getRectY() {
        return cropView.getCropTop() - AndroidUtilities.dp(14) - (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
    }

    public float getRectSizeX() {
        return cropView.getCropWidth();
    }

    public float getRectSizeY() {
        return cropView.getCropHeight();
    }

    public float getBitmapX() {
        return bitmapX - AndroidUtilities.dp(14);
    }

    public float getBitmapY() {
        float additionalY = (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
        return bitmapY - AndroidUtilities.dp(14) - additionalY;
    }

    public float getLimitX() {
        return rectX - Math.max(0, (float) Math.ceil((getWidth() - bitmapWidth * bitmapGlobalScale) / 2));
    }

    public float getLimitY() {
        float additionalY = (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
        return rectY - Math.max(0, (float) Math.ceil((getHeight() - bitmapHeight * bitmapGlobalScale + additionalY) / 2));
    }

    public float getLimitWidth() {
        return getWidth() - AndroidUtilities.dp(14) - rectX - (int) Math.max(0, Math.ceil((getWidth() - AndroidUtilities.dp(28) - bitmapWidth * bitmapGlobalScale) / 2)) - rectSizeX;
    }

    public float getLimitHeight() {
        float additionalY = (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
        return getHeight() - AndroidUtilities.dp(14) - additionalY - rectY - (int) Math.max(0, Math.ceil((getHeight() - AndroidUtilities.dp(28) - bitmapHeight * bitmapGlobalScale - additionalY) / 2)) - rectSizeY;
    }

    public Bitmap getBitmap() {
        if (cropView != null) {
            return cropView.getResult();
        }
        return null;
    }

    public void setBitmapParams(float scale, float x, float y) {
        bitmapGlobalScale = scale;
        bitmapGlobalX = x;
        bitmapGlobalY = y;
    }

    public void startAnimationRunnable() {
        if (animationRunnable != null) {
            return;
        }
        animationRunnable = new Runnable() {
            @Override
            public void run() {
                if (animationRunnable == this) {
                    animationRunnable = null;
                    moveToFill(true);
                }
            }
        };
        AndroidUtilities.runOnUIThread(animationRunnable, 1500);
    }

    public void cancelAnimationRunnable() {
        if (animationRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(animationRunnable);
            animationRunnable = null;
            animationStartValues = null;
            animationEndValues = null;
        }
    }

    public void setAnimationProgress(float animationProgress) {
        if (animationStartValues != null) {
            if (animationProgress == 1) {
                rectX = animationEndValues.left;
                rectY = animationEndValues.top;
                rectSizeX = animationEndValues.right;
                rectSizeY = animationEndValues.bottom;
                animationStartValues = null;
                animationEndValues = null;
            } else {
                rectX = animationStartValues.left + (animationEndValues.left - animationStartValues.left) * animationProgress;
                rectY = animationStartValues.top + (animationEndValues.top - animationStartValues.top) * animationProgress;
                rectSizeX = animationStartValues.right + (animationEndValues.right - animationStartValues.right) * animationProgress;
                rectSizeY = animationStartValues.bottom + (animationEndValues.bottom - animationStartValues.bottom) * animationProgress;
            }
            invalidate();
        }
    }

    public void moveToFill(boolean animated) {
        float scaleToX = bitmapWidth / rectSizeX;
        float scaleToY = bitmapHeight / rectSizeY;
        float scaleTo = scaleToX > scaleToY ? scaleToY : scaleToX;
        if (scaleTo > 1 && scaleTo * bitmapGlobalScale > 3) {
            scaleTo = 3 / bitmapGlobalScale;
        } else if (scaleTo < 1 && scaleTo * bitmapGlobalScale < 1) {
            scaleTo = 1 / bitmapGlobalScale;
        }
        float newSizeX = rectSizeX * scaleTo;
        float newSizeY = rectSizeY * scaleTo;
        float additionalY = (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
        float newX = (getWidth() - newSizeX) / 2;
        float newY = (getHeight() - newSizeY + additionalY) / 2;
        animationStartValues = new RectF(rectX, rectY, rectSizeX, rectSizeY);
        animationEndValues = new RectF(newX, newY, newSizeX, newSizeY);

        float newBitmapGlobalX = newX + getWidth() / 2 * (scaleTo - 1) + (bitmapGlobalX - rectX) * scaleTo;
        float newBitmapGlobalY = newY + (getHeight() + additionalY) / 2 * (scaleTo - 1) + (bitmapGlobalY - rectY) * scaleTo;

        delegate.needMoveImageTo(newBitmapGlobalX, newBitmapGlobalY, bitmapGlobalScale * scaleTo, animated);
    }

    public void setDelegate(PhotoCropViewDelegate delegate) {
        this.delegate = delegate;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        Bitmap newBitmap = delegate.getBitmap();
        if (newBitmap != null) {
            bitmapToEdit = newBitmap;
        }

        if (cropView != null) {
            cropView.updateLayout();
        }
    }
}
