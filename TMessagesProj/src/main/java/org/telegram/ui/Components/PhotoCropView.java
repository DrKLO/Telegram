/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.Crop.CropRotationWheel;
import org.telegram.ui.Components.Crop.CropView;

public class PhotoCropView extends FrameLayout {

    public interface PhotoCropViewDelegate {
        void onChange(boolean reset);
    }

    private PhotoCropViewDelegate delegate;
    private boolean showOnSetBitmap;

    private CropView cropView;
    private CropRotationWheel wheelView;

    public PhotoCropView(Context context) {
        super(context);

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
                rotate();
            }
        });
        addView(wheelView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER | Gravity.BOTTOM, 0, 0, 0, 0));
    }

    public void rotate() {
        if (wheelView != null) {
            wheelView.reset();
        }
        cropView.rotate90Degrees();
    }

    public void setBitmap(Bitmap bitmap, int rotation, boolean freeform, boolean update) {
        requestLayout();

        cropView.setBitmap(bitmap, rotation, freeform, update);

        if (showOnSetBitmap) {
            showOnSetBitmap = false;
            cropView.show();
        }

        wheelView.setFreeform(freeform);
        wheelView.reset();
        wheelView.setVisibility(freeform ? VISIBLE : INVISIBLE);
    }

    public boolean isReady() {
        return cropView.isReady();
    }

    public void reset() {
        wheelView.reset();
        cropView.reset();
    }

    public void onAppear() {
        cropView.willShow();
    }

    public void setAspectRatio(float ratio) {
        cropView.setAspectRatio(ratio);
    }

    public void hideBackView() {
        cropView.hideBackView();
    }

    public void showBackView() {
        cropView.showBackView();
    }

    public void setFreeform(boolean freeform) {
        cropView.setFreeform(freeform);
    }

    public void onAppeared() {
        if (cropView != null) {
            cropView.show();
        } else {
            showOnSetBitmap = true;
        }
    }

    public void onDisappear() {
        if (cropView != null) {
            cropView.hide();
        }
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

    public Bitmap getBitmap() {
        if (cropView != null) {
            return cropView.getResult();
        }
        return null;
    }

    public void setDelegate(PhotoCropViewDelegate delegate) {
        this.delegate = delegate;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (cropView != null) {
            cropView.updateLayout();
        }
    }
}
