/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.messenger.camera;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

@SuppressLint("NewApi")
public class CameraView extends FrameLayout implements TextureView.SurfaceTextureListener {

    private org.telegram.messenger.camera.Size previewSize;
    private boolean mirror;
    private TextureView textureView;
    private CameraSession cameraSession;
    private boolean initied;
    private CameraViewDelegate delegate;
    private int clipTop;
    private int clipLeft;
    private boolean isFrontface;
    private Matrix txform = new Matrix();

    public interface CameraViewDelegate {
        void onCameraInit();
    }

    public CameraView(Context context) {
        super(context, null);
        textureView = new TextureView(context);
        textureView.setSurfaceTextureListener(this);
        addView(textureView);
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
            CameraController.getInstance().close(cameraSession, null);
            cameraSession = null;
        }
        initied = false;
        isFrontface = !isFrontface;
        initCamera(isFrontface);
    }

    private void initCamera(boolean front) {
        CameraInfo info = null;
        ArrayList<CameraInfo> cameraInfos = CameraController.getInstance().getCameras();
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
        org.telegram.messenger.camera.Size pictureSize;
        if (Math.abs(screenSize - size4to3) < 0.1f) {
            pictureSize = new Size(4, 3);
        } else {
            pictureSize = new Size(16, 9);
        }
        if (textureView.getWidth() > 0 && textureView.getHeight() > 0) {
            int width = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
            int height = width * pictureSize.getHeight() / pictureSize.getWidth();
            previewSize = CameraController.chooseOptimalSize(info.getPreviewSizes(), width, height, pictureSize);
        }
        pictureSize = CameraController.chooseOptimalSize(info.getPictureSizes(), 1280, 1280, pictureSize);
        if (previewSize != null && textureView.getSurfaceTexture() != null) {
            textureView.getSurfaceTexture().setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            cameraSession = new CameraSession(info, previewSize, pictureSize, ImageFormat.JPEG);
            CameraController.getInstance().open(cameraSession, textureView.getSurfaceTexture(), new Runnable() {
                @Override
                public void run() {
                    if (cameraSession != null) {
                        cameraSession.setInitied();
                    }
                    checkPreviewMatrix();
                }
            });
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
        initCamera(isFrontface);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
        checkPreviewMatrix();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        if (cameraSession != null) {
            CameraController.getInstance().close(cameraSession, null);
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

    public void destroy(boolean async) {
        if (cameraSession != null) {
            cameraSession.destroy();
            CameraController.getInstance().close(cameraSession, !async ? new Semaphore(0) : null);
        }
    }
}
