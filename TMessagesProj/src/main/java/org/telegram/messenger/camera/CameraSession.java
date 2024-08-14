/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger.camera;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.WindowManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.List;

public class CameraSession {

    public CameraInfo cameraInfo;
    private String currentFlashMode;
    private OrientationEventListener orientationEventListener;
    private int lastOrientation = -1;
    private int lastDisplayOrientation = -1;
    private boolean isVideo;
    private final Size pictureSize;
    private final Size previewSize;
    private final int pictureFormat;
    private boolean initied;
    private int maxZoom;
    private boolean meteringAreaSupported;
    private int currentOrientation;
    private int diffOrientation;
    private int jpegOrientation;
    private boolean sameTakePictureOrientation;
    private boolean flipFront = true;
    private float currentZoom;
    private boolean optimizeForBarcode;
    private boolean useTorch;
    private boolean isRound;
    private boolean destroyed;

    public ArrayList<String> availableFlashModes = new ArrayList<>();

    private int infoCameraId = -1;
    Camera.CameraInfo info = new Camera.CameraInfo();

    public static final int ORIENTATION_HYSTERESIS = 5;

    private Camera.AutoFocusCallback autoFocusCallback = (success, camera) -> {
        if (success) {

        } else {

        }
    };
    private int displayOrientation;

    public CameraSession(CameraInfo info, Size preview, Size picture, int format, boolean round) {
        previewSize = preview;
        pictureSize = picture;
        pictureFormat = format;
        cameraInfo = info;
        isRound = round;

        SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("camera", Activity.MODE_PRIVATE);
        currentFlashMode = sharedPreferences.getString(cameraInfo.frontCamera != 0 ? "flashMode_front" : "flashMode", Camera.Parameters.FLASH_MODE_OFF);

        orientationEventListener = new OrientationEventListener(ApplicationLoader.applicationContext) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientationEventListener == null || !initied || orientation == ORIENTATION_UNKNOWN) {
                    return;
                }
                jpegOrientation = roundOrientation(orientation, jpegOrientation);
                WindowManager mgr = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
                int rotation = mgr.getDefaultDisplay().getRotation();
                if (lastOrientation != jpegOrientation || rotation != lastDisplayOrientation) {
                    if (!isVideo) {
                        configurePhotoCamera();
                    }
                    lastDisplayOrientation = rotation;
                    lastOrientation = jpegOrientation;
                }
            }
        };

        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable();
        } else {
            orientationEventListener.disable();
            orientationEventListener = null;
        }
    }

    private void updateCameraInfo() {
        if (infoCameraId != cameraInfo.getCameraId()) {
            Camera.getCameraInfo(infoCameraId = cameraInfo.getCameraId(), this.info);
        }
    }

    private int roundOrientation(int orientation, int orientationHistory) {
        boolean changeOrientation;
        if (orientationHistory == OrientationEventListener.ORIENTATION_UNKNOWN) {
            changeOrientation = true;
        } else {
            int dist = Math.abs(orientation - orientationHistory);
            dist = Math.min(dist, 360 - dist);
            changeOrientation = (dist >= 45 + ORIENTATION_HYSTERESIS);
        }
        if (changeOrientation) {
            return ((orientation + 45) / 90 * 90) % 360;
        }
        return orientationHistory;
    }

    public void setOptimizeForBarcode(boolean value) {
        optimizeForBarcode = value;
        configurePhotoCamera();
    }

    public void checkFlashMode(String mode) {
        ArrayList<String> modes = availableFlashModes;
        if (modes.contains(currentFlashMode)) {
            return;
        }
        currentFlashMode = mode;
        if (isRound) {
            configureRoundCamera(false);
        } else {
            configurePhotoCamera();
            SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("camera", Activity.MODE_PRIVATE);
            sharedPreferences.edit().putString(cameraInfo.frontCamera != 0 ? "flashMode_front" : "flashMode", mode).commit();
        }
    }

    public void setCurrentFlashMode(String mode) {
        currentFlashMode = mode;
        if (isRound) {
            configureRoundCamera(false);
        } else {
            configurePhotoCamera();
            SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("camera", Activity.MODE_PRIVATE);
            sharedPreferences.edit().putString(cameraInfo.frontCamera != 0 ? "flashMode_front" : "flashMode", mode).commit();
        }
    }

    public void setTorchEnabled(boolean enabled) {
        try {
            currentFlashMode = enabled ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF;
            if (isRound) {
                configureRoundCamera(false);
            } else {
                configurePhotoCamera();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public String getCurrentFlashMode() {
        return currentFlashMode;
    }

    public String getNextFlashMode() {
        ArrayList<String> modes = availableFlashModes;
        for (int a = 0; a < modes.size(); a++) {
            String mode = modes.get(a);
            if (mode.equals(currentFlashMode)) {
                if (a < modes.size() - 1) {
                    return modes.get(a + 1);
                } else {
                    return modes.get(0);
                }
            }
        }
        return currentFlashMode;
    }

    public void setInitied() {
        initied = true;
    }

    public boolean isInitied() {
        return initied;
    }

    public int getCurrentOrientation() {
        return currentOrientation;
    }

    public boolean isFlipFront() {
        return flipFront;
    }

    public void setFlipFront(boolean value) {
        flipFront = value;
    }

    public int getWorldAngle() {
        return diffOrientation;
    }

    public boolean isSameTakePictureOrientation() {
        return sameTakePictureOrientation;
    }

    protected boolean configureRoundCamera(boolean initial) {
        try {
            isVideo = true;
            Camera camera = cameraInfo.camera;
            if (camera != null) {
                Camera.Parameters params = null;
                try {
                    params = camera.getParameters();
                } catch (Exception e) {
                    FileLog.e(e);
                }

                updateCameraInfo();
                updateRotation();

                if (params != null) {
                    if (initial && BuildVars.LOGS_ENABLED) {
                        FileLog.d("set preview size = " + previewSize.getWidth() + " " + previewSize.getHeight());
                    }
                    params.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
                    if (initial && BuildVars.LOGS_ENABLED) {
                        FileLog.d("set picture size = " + pictureSize.getWidth() + " " + pictureSize.getHeight());
                    }
                    params.setPictureSize(pictureSize.getWidth(), pictureSize.getHeight());
                    params.setPictureFormat(pictureFormat);
                    params.setRecordingHint(true);
                    maxZoom = params.getMaxZoom();

                    String desiredMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
                    if (params.getSupportedFocusModes().contains(desiredMode)) {
                        params.setFocusMode(desiredMode);
                    } else {
                        desiredMode = Camera.Parameters.FOCUS_MODE_AUTO;
                        if (params.getSupportedFocusModes().contains(desiredMode)) {
                            params.setFocusMode(desiredMode);
                        }
                    }

                    int outputOrientation = 0;
                    if (jpegOrientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
                        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                            outputOrientation = (info.orientation - jpegOrientation + 360) % 360;
                        } else {
                            outputOrientation = (info.orientation + jpegOrientation) % 360;
                        }
                    }
                    try {
                        params.setRotation(outputOrientation);
                        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                            sameTakePictureOrientation = (360 - displayOrientation) % 360 == outputOrientation;
                        } else {
                            sameTakePictureOrientation = displayOrientation == outputOrientation;
                        }
                    } catch (Exception e) {
                        //
                    }
                    params.setFlashMode(currentFlashMode);
                    params.setZoom((int) (currentZoom * maxZoom));
                    try {
                        camera.setParameters(params);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                        //
                    }

                    if (params.getMaxNumMeteringAreas() > 0) {
                        meteringAreaSupported = true;
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
        return true;
    }

    public void updateRotation() {
        if (cameraInfo == null) {
            return;
        }

        try {
            updateCameraInfo();
        } catch (Throwable throwable) {
            FileLog.e(throwable);
            return;
        }
        Camera camera = destroyed ? null : cameraInfo.camera;

        displayOrientation = getDisplayOrientation(info, true);
        int cameraDisplayOrientation;

        int degrees = 0;
        if ("samsung".equals(Build.MANUFACTURER) && "sf2wifixx".equals(Build.PRODUCT)) {
            cameraDisplayOrientation = 0;
        } else {
            int temp = displayOrientation;
            switch (temp) {
                case Surface.ROTATION_0:
                    degrees = 0;
                    break;
                case Surface.ROTATION_90:
                    degrees = 90;
                    break;
                case Surface.ROTATION_180:
                    degrees = 180;
                    break;
                case Surface.ROTATION_270:
                    degrees = 270;
                    break;
            }
            if (info.orientation % 90 != 0) {
                info.orientation = 0;
            }
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                temp = (info.orientation + degrees) % 360;
                temp = (360 - temp) % 360;
            } else {
                temp = (info.orientation - degrees + 360) % 360;
            }
            cameraDisplayOrientation = temp;
        }
        currentOrientation = cameraDisplayOrientation;
        if (camera != null) {
            try {
                camera.setDisplayOrientation(currentOrientation);
            } catch (Throwable ignore) {}
        }
        diffOrientation = currentOrientation - displayOrientation;
        if (diffOrientation < 0) {
            diffOrientation += 360;
        }
    }

    protected void configurePhotoCamera() {
        try {
            Camera camera = cameraInfo.camera;
            if (camera != null) {
                Camera.Parameters params = null;
                try {
                    params = camera.getParameters();
                } catch (Exception e) {
                    FileLog.e(e);
                }

                updateCameraInfo();
                updateRotation();

                diffOrientation = currentOrientation - displayOrientation;
                if (diffOrientation < 0) {
                    diffOrientation += 360;
                }

                if (params != null) {
                    params.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
                    params.setPictureSize(pictureSize.getWidth(), pictureSize.getHeight());
                    params.setPictureFormat(pictureFormat);
                    params.setJpegQuality(100);
                    params.setJpegThumbnailQuality(100);
                    maxZoom = params.getMaxZoom();
                    params.setZoom((int) (currentZoom * maxZoom));

                    if (optimizeForBarcode) {
                        List<String> modes = params.getSupportedSceneModes();
                        if (modes != null && modes.contains(Camera.Parameters.SCENE_MODE_BARCODE)) {
                            params.setSceneMode(Camera.Parameters.SCENE_MODE_BARCODE);
                        }
                        String desiredMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
                        if (params.getSupportedFocusModes().contains(desiredMode)) {
                            params.setFocusMode(desiredMode);
                        }
                    } else {
                        String desiredMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
                        if (params.getSupportedFocusModes().contains(desiredMode)) {
                            params.setFocusMode(desiredMode);
                        }
                    }

                    int outputOrientation = 0;
                    if (jpegOrientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
                        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                            outputOrientation = (info.orientation - jpegOrientation + 360) % 360;
                        } else {
                            outputOrientation = (info.orientation + jpegOrientation) % 360;
                        }
                    }
                    try {
                        params.setRotation(outputOrientation);
                        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                            sameTakePictureOrientation = (360 - displayOrientation) % 360 == outputOrientation;
                        } else {
                            sameTakePictureOrientation = displayOrientation == outputOrientation;
                        }
                    } catch (Exception e) {
                        //
                    }
                    params.setFlashMode(useTorch ? Camera.Parameters.FLASH_MODE_TORCH : currentFlashMode);

                    try {
                        camera.setParameters(params);
                    } catch (Exception e) {
                        //
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public void focusToRect(Rect focusRect, Rect meteringRect) {
        try {
            Camera camera = cameraInfo.camera;
            if (camera != null) {
                camera.cancelAutoFocus();
                Camera.Parameters parameters = null;
                try {
                    parameters = camera.getParameters();
                } catch (Exception e) {
                    FileLog.e(e);
                }

                if (parameters != null) {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                    ArrayList<Camera.Area> meteringAreas = new ArrayList<>();
                    meteringAreas.add(new Camera.Area(focusRect, 1000));
                    parameters.setFocusAreas(meteringAreas);

                    if (meteringAreaSupported) {
                        meteringAreas = new ArrayList<>();
                        meteringAreas.add(new Camera.Area(meteringRect, 1000));
                        parameters.setMeteringAreas(meteringAreas);
                    }

                    try {
                        camera.setParameters(parameters);
                        camera.autoFocus(autoFocusCallback);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    protected int getMaxZoom() {
        return maxZoom;
    }

    public void onStartRecord() {
        isVideo = true;
    }

    public void setZoom(float value) {
        currentZoom = value;
        if (isVideo && Camera.Parameters.FLASH_MODE_ON.equals(currentFlashMode)) {
            useTorch = true;
        }
        if (isRound) {
            configureRoundCamera(false);
        } else {
            configurePhotoCamera();
        }
    }

    protected void configureRecorder(int quality, MediaRecorder recorder) {
        updateCameraInfo();

        int outputOrientation = 0;
        if (jpegOrientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                outputOrientation = (info.orientation - jpegOrientation + 360) % 360;
            } else {
                outputOrientation = (info.orientation + jpegOrientation) % 360;
            }
        }
        recorder.setOrientationHint(outputOrientation);

        int highProfile = getHigh();
        boolean canGoHigh = CamcorderProfile.hasProfile(cameraInfo.cameraId, highProfile);
        boolean canGoLow = CamcorderProfile.hasProfile(cameraInfo.cameraId, CamcorderProfile.QUALITY_LOW);
        if (canGoHigh && (quality == 1 || !canGoLow)) {
            recorder.setProfile(CamcorderProfile.get(cameraInfo.cameraId, highProfile));
        } else if (canGoLow) {
            recorder.setProfile(CamcorderProfile.get(cameraInfo.cameraId, CamcorderProfile.QUALITY_LOW));
        } else {
            throw new IllegalStateException("cannot find valid CamcorderProfile");
        }
        isVideo = true;
    }

    public void stopVideoRecording() {
        isVideo = false;
        useTorch = false;
        configurePhotoCamera();
    }

    private int getHigh() {
        if ("LGE".equals(Build.MANUFACTURER) && "g3_tmo_us".equals(Build.PRODUCT)) {
            return CamcorderProfile.QUALITY_480P;
        }
        return CamcorderProfile.QUALITY_HIGH;
    }

    private int getDisplayOrientation(Camera.CameraInfo info, boolean isStillCapture) {
        WindowManager mgr = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
        int rotation = mgr.getDefaultDisplay().getRotation();
        int degrees = 0;

        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int displayOrientation;

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            displayOrientation = (info.orientation + degrees) % 360;
            displayOrientation = (360 - displayOrientation) % 360;

            if (!isStillCapture && displayOrientation == 90) {
                displayOrientation = 270;
            }
            if (!isStillCapture && "Huawei".equals(Build.MANUFACTURER) && "angler".equals(Build.PRODUCT) && displayOrientation == 270) {
                displayOrientation = 90;
            }
        } else {
            displayOrientation = (info.orientation - degrees + 360) % 360;
        }

        return displayOrientation;
    }

    public int getDisplayOrientation() {
        try {
            updateCameraInfo();
            return getDisplayOrientation(info, true);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return 0;
    }

    public void setPreviewCallback(Camera.PreviewCallback callback){
        cameraInfo.camera.setPreviewCallback(callback);
    }

    public void setOneShotPreviewCallback(Camera.PreviewCallback callback) {
        if (cameraInfo != null && cameraInfo.camera != null) {
            try {
                cameraInfo.camera.setOneShotPreviewCallback(callback);
            } catch (Exception ignore) {

            }
        }
    }

    public void destroy() {
        initied = false;
        destroyed = true;
        if (orientationEventListener != null) {
            orientationEventListener.disable();
            orientationEventListener = null;
        }
    }

    public Camera.Size getCurrentPreviewSize() {
        return cameraInfo.camera.getParameters().getPreviewSize();
    }

    public Camera.Size getCurrentPictureSize() {
        return cameraInfo.camera.getParameters().getPictureSize();
    }
}
