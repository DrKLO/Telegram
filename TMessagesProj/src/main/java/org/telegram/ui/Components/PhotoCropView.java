/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Property;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BubbleActivity;
import org.telegram.ui.Components.Crop.CropRotationWheel;
import org.telegram.ui.Components.Crop.CropTransform;
import org.telegram.ui.Components.Crop.CropView;

public class PhotoCropView extends FrameLayout {

    public void setSubtitle(String subtitle) {
        cropView.setSubtitle(subtitle);
    }

    public interface PhotoCropViewDelegate {
        void onChange(boolean reset);
        void onUpdate();
        void onTapUp();
        int getVideoThumbX();
        void onVideoThumbClick();
        boolean rotate();
        boolean mirror();
    }

    private PhotoCropViewDelegate delegate;

    public boolean isReset = true;
    public CropView cropView;
    public CropRotationWheel wheelView;

    private boolean inBubbleMode;

    private ImageReceiver thumbImageView;
    private boolean thumbImageVisible;
    private boolean thumbImageVisibleOverride = true;
    private float thumbImageVisibleProgress;
    private float thumbAnimationProgress = 1.0f;
    private AnimatorSet thumbAnimation;
    private AnimatorSet thumbOverrideAnimation;
    private float flashAlpha = 0.0f;

    private Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Theme.ResourcesProvider resourcesProvider;

    public final Property<PhotoCropView, Float> ANIMATION_VALUE = new AnimationProperties.FloatProperty<PhotoCropView>("thumbAnimationProgress") {
        @Override
        public void setValue(PhotoCropView object, float value) {
            thumbAnimationProgress = value;
            object.invalidate();
        }

        @Override
        public Float get(PhotoCropView object) {
            return thumbAnimationProgress;
        }
    };

    public final Property<PhotoCropView, Float> PROGRESS_VALUE = new AnimationProperties.FloatProperty<PhotoCropView>("thumbImageVisibleProgress") {
        @Override
        public void setValue(PhotoCropView object, float value) {
            thumbImageVisibleProgress = value;
            object.invalidate();
        }

        @Override
        public Float get(PhotoCropView object) {
            return thumbImageVisibleProgress;
        }
    };

    public PhotoCropView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        inBubbleMode = context instanceof BubbleActivity;

        cropView = new CropView(context);
        cropView.setListener(new CropView.CropViewListener() {
            @Override
            public void onChange(boolean reset) {
                isReset = reset;
                if (delegate != null) {
                    delegate.onChange(reset);
                }
            }

            @Override
            public void onUpdate() {
                if (delegate != null) {
                    delegate.onUpdate();
                }
            }

            @Override
            public void onAspectLock(boolean enabled) {
                wheelView.setAspectLock(enabled);
            }

            @Override
            public void onTapUp() {
                if (delegate != null) {
                    delegate.onTapUp();
                }
            }
        });
        cropView.setBottomPadding(AndroidUtilities.dp(64));
        addView(cropView);

        thumbImageView = new ImageReceiver(this);

        wheelView = new CropRotationWheel(context);
        wheelView.setListener(new CropRotationWheel.RotationWheelListener() {
            @Override
            public void onStart() {
                cropView.onRotationBegan();
            }

            @Override
            public void onChange(float angle) {
                cropView.setRotation(angle);
                isReset = false;
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
            public boolean rotate90Pressed() {
                if (delegate != null) {
                    return delegate.rotate();
                }
                return false;
            }

            @Override
            public boolean mirror() {
                if (delegate != null) {
                    return delegate.mirror();
                }
                return false;
            }
        });
        addView(wheelView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER | Gravity.BOTTOM, 0, 0, 0, 0));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (thumbImageVisibleOverride && thumbImageVisible && thumbImageView.isInsideImage(event.getX(), event.getY())) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                delegate.onVideoThumbClick();
            }
            return true;
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (thumbImageVisibleOverride && thumbImageVisible && thumbImageView.isInsideImage(event.getX(), event.getY())) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                delegate.onVideoThumbClick();
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (thumbImageVisible && child == cropView) {
            RectF rect = cropView.getActualRect();
            int targetSize = AndroidUtilities.dp(32);
            int targetX = delegate.getVideoThumbX() - targetSize / 2 + AndroidUtilities.dp(2);
            int targetY = getMeasuredHeight() - AndroidUtilities.dp(156);
            float x = rect.left + (targetX - rect.left) * thumbAnimationProgress;
            float y = rect.top + (targetY - rect.top) * thumbAnimationProgress;
            float size = rect.width() + (targetSize - rect.width()) * thumbAnimationProgress;
            thumbImageView.setRoundRadius((int) (size / 2));
            thumbImageView.setImageCoords(x, y, size, size);
            thumbImageView.setAlpha(thumbImageVisibleProgress);
            thumbImageView.draw(canvas);

            if (flashAlpha > 0.0f) {
                circlePaint.setColor(0xffffffff);
                circlePaint.setAlpha((int) (flashAlpha * 255));
                canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() / 2, circlePaint);
            }

            circlePaint.setColor(getThemedColor(Theme.key_chat_editMediaButton));
            circlePaint.setAlpha(Math.min(255, (int) (255 * thumbAnimationProgress * thumbImageVisibleProgress)));
            canvas.drawCircle(targetX + targetSize / 2, targetY + targetSize + AndroidUtilities.dp(8), AndroidUtilities.dp(3), circlePaint);
        }
        return result;
    }

    public boolean rotate(float diff) {
        if (wheelView != null) {
            wheelView.reset(false);
        }
        return cropView.rotate(diff);
    }

    public boolean mirror() {
        return cropView.mirror();
    }

    public void setBitmap(Bitmap bitmap, int rotation, boolean freeform, boolean update, PaintingOverlay paintingOverlay, CropTransform cropTransform, VideoEditTextureView videoView, MediaController.CropState state) {
        requestLayout();

        thumbImageVisible = false;
        thumbImageView.setImageBitmap((Drawable) null);
        cropView.setBitmap(bitmap, rotation, freeform, update, paintingOverlay, cropTransform, videoView, state);
        wheelView.setFreeform(freeform);
        wheelView.reset(true);
        if (state != null) {
            wheelView.setRotation(state.cropRotate, false);
            wheelView.setRotated(state.transformRotation != 0);
            wheelView.setMirrored(state.mirrored);
        } else {
            wheelView.setRotated(false);
            wheelView.setMirrored(false);
        }
        wheelView.setVisibility(freeform ? VISIBLE : INVISIBLE);
    }

    public void setVideoThumbFlashAlpha(float alpha) {
        flashAlpha = alpha;
        invalidate();
    }

    public Bitmap getVideoThumb() {
        return thumbImageVisible && thumbImageVisibleOverride ? thumbImageView.getBitmap() : null;
    }

    public void setVideoThumb(Bitmap bitmap, int orientation) {
        thumbImageVisible = bitmap != null;
        thumbImageView.setImageBitmap(bitmap);
        thumbImageView.setOrientation(orientation, false);
        if (thumbAnimation != null) {
            thumbAnimation.cancel();
        }
        if (thumbOverrideAnimation != null) {
            thumbOverrideAnimation.cancel();
        }
        thumbImageVisibleOverride = true;
        thumbImageVisibleProgress = 1.0f;
        thumbAnimation = new AnimatorSet();
        thumbAnimation.playTogether(ObjectAnimator.ofFloat(this, ANIMATION_VALUE, 0.0f, 1.0f));
        thumbAnimation.setDuration(250);
        thumbAnimation.setInterpolator(new OvershootInterpolator(1.01f));
        thumbAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                thumbAnimation = null;
            }
        });
        thumbAnimation.start();
    }

    public void cancelThumbAnimation() {
        if (thumbAnimation != null) {
            thumbAnimation.cancel();
            thumbAnimation = null;
            thumbImageVisible = false;
        }
    }

    public void setVideoThumbVisible(boolean visible) {
        if (thumbImageVisibleOverride == visible) {
            return;
        }
        thumbImageVisibleOverride = visible;
        if (thumbOverrideAnimation != null) {
            thumbOverrideAnimation.cancel();
        }
        thumbOverrideAnimation = new AnimatorSet();
        thumbOverrideAnimation.playTogether(ObjectAnimator.ofFloat(this, PROGRESS_VALUE, visible ? 1.0f : 0.0f));
        thumbOverrideAnimation.setDuration(180);
        thumbOverrideAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                thumbOverrideAnimation = null;
            }
        });
        thumbOverrideAnimation.start();
    }

    public boolean isReady() {
        return cropView.isReady();
    }

    public void reset() {
        reset(false);
    }

    public void reset(boolean force) {
        wheelView.reset(true);
        cropView.reset(force);
    }

    public void onAppear() {
        cropView.willShow();
    }

    public void setAspectRatio(float ratio) {
        cropView.setAspectRatio(ratio);
    }

    public void setFreeform(boolean freeform) {
        cropView.setFreeform(freeform);
    }

    public void onAppeared() {
        cropView.show();
    }

    public void onDisappear() {
        cropView.hide();
    }

    public void onShow() {
        cropView.onShow();
    }

    public void onHide() {
        cropView.onHide();
    }

    public float getRectX() {
        return cropView.getCropLeft() - AndroidUtilities.dp(14);
    }

    public float getRectY() {
        return cropView.getCropTop() - AndroidUtilities.dp(14) - (Build.VERSION.SDK_INT >= 21 && !inBubbleMode ? AndroidUtilities.statusBarHeight : 0);
    }

    public float getRectSizeX() {
        return cropView.getCropWidth();
    }

    public float getRectSizeY() {
        return cropView.getCropHeight();
    }

    public void makeCrop(MediaController.MediaEditState editState) {
        cropView.makeCrop(editState);
    }

    public void setDelegate(PhotoCropViewDelegate photoCropViewDelegate) {
        delegate = photoCropViewDelegate;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        cropView.updateLayout();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        cropView.invalidate();
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
