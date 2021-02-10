package org.telegram.messenger.camera;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.display.DisplayManager;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import androidx.camera.view.PreviewView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;

import java.io.File;

@TargetApi(21)
public class CameraXView extends CameraView {
    private boolean isStreaming = false;
    private final PreviewView previewView;
    private final ImageView placeholderView;
    private final int focusAreaSize;
    private final Paint outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private CameraXController.CameraLifecycle lifecycle;
    private Camera1View.CameraViewDelegate delegate;
    private int clipTop;
    private int clipBottom;
    private float focusProgress = 1.0f;
    private float innerAlpha;
    private float outerAlpha;
    private final DecelerateInterpolator interpolator = new DecelerateInterpolator();
    private long lastDrawTime;
    private int cx;
    private int cy;
    private final CameraXController controller;

    private int displayOrientation = 0;
    private int worldOrientation = 0;
    private final DisplayManager.DisplayListener displayOrientationListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
        }

        @Override
        public void onDisplayRemoved(int displayId) {
        }

        @Override
        public void onDisplayChanged(int displayId) {
            if (getRootView().getDisplay().getDisplayId() == displayId) {
                displayOrientation = getRootView().getDisplay().getRotation();
                if (controller != null) {
                    controller.setTargetOrientation(displayOrientation);
                }
            }
        }
    };

    private final OrientationEventListener worldOrientationListener = new OrientationEventListener(getContext()) {
        @Override
        public void onOrientationChanged(int orientation) {
            if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
                return;
            }
            int rotation = 0;
            if (orientation >= 45 && orientation < 135) {
                rotation = Surface.ROTATION_270;
            } else if (orientation >= 135 && orientation < 225) {
                rotation = Surface.ROTATION_180;
            } else if (orientation >= 225 && orientation < 315) {
                rotation = Surface.ROTATION_90;
            } else {
                rotation = Surface.ROTATION_0;
            }
            worldOrientation = rotation;

            if (controller != null) {
                controller.setWorldCaptureOrientation(rotation);
            }
        }
    };

    public interface VideoSavedCallback {
        void onFinishVideoRecording(String thumbPath, long duration);
    }


    public CameraXView(Context context, boolean frontface) {
        super(context, null);
        previewView = new PreviewView(context);
        placeholderView = new ImageView(context);
        placeholderView.setVisibility(View.GONE);
        placeholderView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        previewView.setFocusableInTouchMode(false);
        addView(previewView);
        addView(placeholderView);
        focusAreaSize = AndroidUtilities.dp(96);
        outerPaint.setColor(0xffffffff);
        outerPaint.setStyle(Paint.Style.STROKE);
        outerPaint.setStrokeWidth(AndroidUtilities.dp(2));
        innerPaint.setColor(0x7fffffff);
        lifecycle = new CameraXController.CameraLifecycle();
        controller = new CameraXController(lifecycle, previewView.getMeteringPointFactory(), previewView.getSurfaceProvider());
        controller.setFrontFace(frontface);


        ((DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE)).registerDisplayListener(displayOrientationListener, null);
        worldOrientationListener.enable();
    }

    @Override
    public boolean isInitied() {
        return isStreaming;
    }

    @Override
    public boolean isFrontface() {
        return controller.isFrontface();
    }

    @Override
    public void setClipTop(int cameraViewOffsetY) {
        clipTop = cameraViewOffsetY;
    }

    @Override
    public void setClipBottom(int cameraViewOffsetBottomY) {
        clipBottom = cameraViewOffsetBottomY;
    }


    @Override
    public void initCamera() {
        controller.initCamera(getContext(), controller.isFrontface(), this::observeStream);
    }

    //ugly behaviour after permission check
    public void rebind(){
        if(isStreaming){
            Bitmap previewBitmap = previewView.getBitmap();
            if(previewBitmap != null){
                placeholderView.setImageBitmap(previewBitmap);
                placeholderView.setVisibility(View.VISIBLE);
            }
        }
        controller.bindUseCases();
    }

    public void closeCamera() {
        controller.closeCamera();
    }

    private void observeStream() {
        previewView.getPreviewStreamState().observe(lifecycle, streamState -> {
            if (streamState == PreviewView.StreamState.STREAMING /*&& !isInited*/) {
                delegate.onCameraInit();
                isStreaming = true;
                placeholderView.setImageBitmap(null);
                placeholderView.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void switchCamera() {
        if(isStreaming){
            Bitmap previewBitmap = previewView.getBitmap();
            if(previewBitmap != null){
                //Bitmap lastBitmap = Bitmap.createScaledBitmap(previewBitmap, (int) (previewBitmap.getWidth() / (previewBitmap.getHeight() / 80.0f)),80, true);
                //Utilities.blurBitmap(lastBitmap, 7, 1, lastBitmap.getWidth(), lastBitmap.getHeight(), lastBitmap.getRowBytes());
                placeholderView.setImageBitmap(previewBitmap);
                placeholderView.setVisibility(View.VISIBLE);
            }
        }
        controller.switchCamera();
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean hasFrontFaceCamera() {
        return controller.hasFrontFaceCamera();
    }

    @SuppressLint("RestrictedApi")
    public static boolean hasGoodCamera(Context context) {
        return CameraXController.hasGoodCamera(context);
    }

    @Override
    public TextureView getTextureView() {
        return (TextureView) (previewView.getChildAt(0));
    }

    public Bitmap getBitmap() {
        return previewView.getBitmap();
    }


    public String setNextFlashMode() {
        return controller.setNextFlashMode();
    }

    public String getCurrentFlashMode() {
        return controller.getCurrentFlashMode();
    }

    public boolean isFlashAvailable() {
        return controller.isFlashAvailable();
    }

    @Override
    public void setUseMaxPreview(boolean value) {

    }

    @Override
    public void setMirror(boolean value) {

    }

    @Override
    public void setOptimizeForBarcode(boolean value) {

    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        lifecycle.stop();
        ((DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE)).unregisterDisplayListener(displayOrientationListener);
        worldOrientationListener.disable();
    }

    public void setDelegate(Camera1View.CameraViewDelegate cameraViewDelegate) {
        delegate = cameraViewDelegate;
    }

    @Override
    public void setZoom(float value) {
        controller.setZoom(value);
    }

    public boolean isExposureCompensationSupported(){
        return controller.isExposureCompensationSupported();
    }
    public void setExposureCompensation(float value) {
        controller.setExposureCompensation(value);
    }

    @Override
    public void focusToPoint(int x, int y) {
        controller.focusToPoint(x, y);
        focusProgress = 0.0f;
        innerAlpha = 1.0f;
        outerAlpha = 1.0f;
        cx = x;
        cy = y;
        lastDrawTime = System.currentTimeMillis();
        invalidate();
    }

    @Override
    public Size getPreviewSize() {
        return controller.getPreviewSize();
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (focusProgress != 1.0f || innerAlpha != 0.0f || outerAlpha != 0.0f) {
            int baseRad = AndroidUtilities.dp(30);
            long newTime = System.currentTimeMillis();
            long dt = newTime - lastDrawTime;
            if (dt < 0 || dt > 17) {
                dt = 17;
            }
            lastDrawTime = newTime;
            outerPaint.setAlpha((int) (interpolator.getInterpolation(outerAlpha) * 255));
            innerPaint.setAlpha((int) (interpolator.getInterpolation(innerAlpha) * 127));
            float interpolated = interpolator.getInterpolation(focusProgress);
            canvas.drawCircle(cx, cy, baseRad + baseRad * (1.0f - interpolated), outerPaint);
            canvas.drawCircle(cx, cy, baseRad * interpolated, innerPaint);

            if (focusProgress < 1) {
                focusProgress += dt / 200.0f;
                if (focusProgress > 1) {
                    focusProgress = 1;
                }
                invalidate();
            } else if (innerAlpha != 0) {
                innerAlpha -= dt / 150.0f;
                if (innerAlpha < 0) {
                    innerAlpha = 0;
                }
                invalidate();
            } else if (outerAlpha != 0) {
                outerAlpha -= dt / 150.0f;
                if (outerAlpha < 0) {
                    outerAlpha = 0;
                }
                invalidate();
            }
        }
        return result;
    }

    @SuppressLint("RestrictedApi")
    public void recordVideo(final File path, boolean mirrorThumb, VideoSavedCallback onStop) {
        controller.recordVideo(path, mirrorThumb, onStop);
    }


    @SuppressLint("RestrictedApi")
    public void stopVideoRecording(final boolean abandon) {
        controller.stopVideoRecording(abandon);
    }


    public void takePicture(final File file, Runnable onTake) {
        controller.takePicture(file, onTake);
    }

    public boolean isSameTakePictureOrientation(){
        return displayOrientation == worldOrientation;
    }
}
