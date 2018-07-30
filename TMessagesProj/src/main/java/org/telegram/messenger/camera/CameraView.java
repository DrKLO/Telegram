/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger.camera;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

@SuppressLint("NewApi")
public class CameraView extends FrameLayout implements TextureView.SurfaceTextureListener {

    private Size previewSize;
    private boolean mirror;
    private TextureView textureView;
    private CameraSession cameraSession;
    private boolean initied;
    private CameraViewDelegate delegate;
    private int clipTop;
    private int clipLeft;
    private boolean isFrontface;
    private Matrix txform = new Matrix();
    private Matrix matrix = new Matrix();
    private int focusAreaSize;

    private long lastDrawTime;
    private float focusProgress = 1.0f;
    private float innerAlpha;
    private float outerAlpha;
    private boolean initialFrontface;
    private int cx;
    private int cy;
    private Paint outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private DecelerateInterpolator interpolator = new DecelerateInterpolator();

    public interface CameraViewDelegate {
        void onCameraCreated(Camera camera);
        void onCameraInit();
    }

    public CameraView(Context context, boolean frontface) {
        super(context, null);
        initialFrontface = isFrontface = frontface;
        textureView = new TextureView(context);
        textureView.setSurfaceTextureListener(this);
        addView(textureView);
        focusAreaSize = AndroidUtilities.dp(96);
        outerPaint.setColor(0xffffffff);
        outerPaint.setStyle(Paint.Style.STROKE);
        outerPaint.setStrokeWidth(AndroidUtilities.dp(2));
        innerPaint.setColor(0x7fffffff);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        checkPreviewMatrix();
    }

    public void setMirror(boolean value) {
        mirror = value;
    }

    public boolean isFrontface() {
        return isFrontface;
    }

    public boolean hasFrontFaceCamera() {
        ArrayList<CameraInfo> cameraInfos = CameraController.getInstance().getCameras();
        for (int a = 0; a < cameraInfos.size(); a++) {
            if (cameraInfos.get(a).frontCamera != 0) {
                return true;
            }
        }
        return false;
    }

    public void switchCamera() {
        if (cameraSession != null) {
            CameraController.getInstance().close(cameraSession, null, null);
            cameraSession = null;
        }
        initied = false;
        isFrontface = !isFrontface;
        initCamera();
    }

    private void initCamera() {
        CameraInfo info = null;
        ArrayList<CameraInfo> cameraInfos = CameraController.getInstance().getCameras();
        if (cameraInfos == null) {
            return;
        }
        for (int a = 0; a < cameraInfos.size(); a++) {
            CameraInfo cameraInfo = cameraInfos.get(a);
            if (isFrontface && cameraInfo.frontCamera != 0 || !isFrontface && cameraInfo.frontCamera == 0) {
                info = cameraInfo;
                break;
            }
        }
        if (info == null) {
            return;
        }
        float size4to3 = 4.0f / 3.0f;
        float size16to9 = 16.0f / 9.0f;
        float screenSize = (float) Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
        org.telegram.messenger.camera.Size aspectRatio;
        int wantedWidth;
        int wantedHeight;
        if (initialFrontface) {
            aspectRatio = new Size(16, 9);
            wantedWidth = 480;
            wantedHeight = 270;
        } else {
            if (Math.abs(screenSize - size4to3) < 0.1f) {
                aspectRatio = new Size(4, 3);
                wantedWidth = 1280;
                wantedHeight = 960;
            } else {
                aspectRatio = new Size(16, 9);
                wantedWidth = 1280;
                wantedHeight = 720;
            }
        }
        if (textureView.getWidth() > 0 && textureView.getHeight() > 0) {
            int width = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
            int height = width * aspectRatio.getHeight() / aspectRatio.getWidth();
            previewSize = CameraController.chooseOptimalSize(info.getPreviewSizes(), width, height, aspectRatio);
        }
        org.telegram.messenger.camera.Size pictureSize = CameraController.chooseOptimalSize(info.getPictureSizes(), wantedWidth, wantedHeight, aspectRatio);
        if (pictureSize.getWidth() >= 1280 && pictureSize.getHeight() >= 1280) {
            if (Math.abs(screenSize - size4to3) < 0.1f) {
                aspectRatio = new Size(3, 4);
            } else {
                aspectRatio = new Size(9, 16);
            }
            org.telegram.messenger.camera.Size pictureSize2 = CameraController.chooseOptimalSize(info.getPictureSizes(), wantedHeight, wantedWidth, aspectRatio);
            if (pictureSize2.getWidth() < 1280 || pictureSize2.getHeight() < 1280) {
                pictureSize = pictureSize2;
            }
        }
        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        if (previewSize != null && surfaceTexture != null) {
            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            cameraSession = new CameraSession(info, previewSize, pictureSize, ImageFormat.JPEG);
            CameraController.getInstance().open(cameraSession, surfaceTexture, new Runnable() {
                @Override
                public void run() {
                    if (cameraSession != null) {
                        cameraSession.setInitied();
                    }
                    checkPreviewMatrix();
                }
            }, new Runnable() {
                @Override
                public void run() {
                    if (delegate != null) {
                        delegate.onCameraCreated(cameraSession.cameraInfo.camera);
                    }
                }
            });
        }
    }

    public Size getPreviewSize() {
        return previewSize;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        initCamera();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        checkPreviewMatrix();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        if (cameraSession != null) {
            CameraController.getInstance().close(cameraSession, null, null);
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        if (!initied && cameraSession != null && cameraSession.isInitied()) {
            if (delegate != null) {
                delegate.onCameraInit();
            }
            initied = true;
        }
    }

    public void setClipTop(int value) {
        clipTop = value;
    }

    public void setClipLeft(int value) {
        clipLeft = value;
    }

    private void checkPreviewMatrix() {
        if (previewSize == null) {
            return;
        }
        adjustAspectRatio(previewSize.getWidth(), previewSize.getHeight(), ((Activity) getContext()).getWindowManager().getDefaultDisplay().getRotation());
    }

    private void adjustAspectRatio(int previewWidth, int previewHeight, int rotation) {
        txform.reset();

        int viewWidth = getWidth();
        int viewHeight = getHeight();
        float viewCenterX = viewWidth / 2;
        float viewCenterY = viewHeight / 2;

        float scale;
        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            scale = Math.max((float) (viewHeight + clipTop) / previewWidth, (float) (viewWidth + clipLeft) / previewHeight);
        } else {
            scale = Math.max((float) (viewHeight + clipTop) / previewHeight, (float) (viewWidth + clipLeft) / previewWidth);
        }

        float previewWidthScaled = previewWidth * scale;
        float previewHeightScaled = previewHeight * scale;

        float scaleX = previewHeightScaled / (viewWidth);
        float scaleY = previewWidthScaled / (viewHeight);

        txform.postScale(scaleX, scaleY, viewCenterX, viewCenterY);

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            txform.postRotate(90 * (rotation - 2), viewCenterX, viewCenterY);
        } else {
            if (Surface.ROTATION_180 == rotation) {
                txform.postRotate(180, viewCenterX, viewCenterY);
            }
        }

        if (mirror) {
            txform.postScale(-1, 1, viewCenterX, viewCenterY);
        }
        if (clipTop != 0 || clipLeft != 0) {
            txform.postTranslate(-clipLeft / 2, -clipTop / 2);
        }

        textureView.setTransform(txform);

        Matrix matrix = new Matrix();
        matrix.postRotate(cameraSession.getDisplayOrientation());
        matrix.postScale(viewWidth / 2000f, viewHeight / 2000f);
        matrix.postTranslate(viewWidth / 2f, viewHeight / 2f);
        matrix.invert(this.matrix);
    }

    private Rect calculateTapArea(float x, float y, float coefficient) {
        int areaSize = Float.valueOf(focusAreaSize * coefficient).intValue();

        int left = clamp((int) x - areaSize / 2, 0, getWidth() - areaSize);
        int top = clamp((int) y - areaSize / 2, 0, getHeight() - areaSize);

        RectF rectF = new RectF(left, top, left + areaSize, top + areaSize);
        matrix.mapRect(rectF);

        return new Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right), Math.round(rectF.bottom));
    }

    private int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }

    public void focusToPoint(int x, int y) {
        Rect focusRect = calculateTapArea(x, y, 1f);
        Rect meteringRect = calculateTapArea(x, y, 1.5f);

        if (cameraSession != null) {
            cameraSession.focusToRect(focusRect, meteringRect);
        }

        focusProgress = 0.0f;
        innerAlpha = 1.0f;
        outerAlpha = 1.0f;
        cx = x;
        cy = y;
        lastDrawTime = System.currentTimeMillis();
        invalidate();
    }

    public void setDelegate(CameraViewDelegate cameraViewDelegate) {
        delegate = cameraViewDelegate;
    }

    public boolean isInitied() {
        return initied;
    }

    public CameraSession getCameraSession() {
        return cameraSession;
    }

    public void destroy(boolean async, final Runnable beforeDestroyRunnable) {
        if (cameraSession != null) {
            cameraSession.destroy();
            CameraController.getInstance().close(cameraSession, !async ? new CountDownLatch(1) : null, beforeDestroyRunnable);
        }
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
}
