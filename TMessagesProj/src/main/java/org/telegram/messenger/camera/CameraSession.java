/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.messenger.camera;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.WindowManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;

public class CameraSession {

    protected CameraInfo cameraInfo;
    private String currentFlashMode = Camera.Parameters.FLASH_MODE_OFF;
    private OrientationEventListener orientationEventListener;
    private int lastOrientation = -1;
    private boolean isVideo;
    private final Size pictureSize;
    private final Size previewSize;
    private final int pictureFormat;
    private boolean initied;

    public CameraSession(CameraInfo info, Size preview, Size picture, int format) {
        previewSize = preview;
        pictureSize = picture;
        pictureFormat = format;
        cameraInfo = info;

        SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("camera", Activity.MODE_PRIVATE);
        currentFlashMode = sharedPreferences.getString(cameraInfo.frontCamera != 0 ? "flashMode_front" : "flashMode", Camera.Parameters.FLASH_MODE_OFF);

        orientationEventListener = new OrientationEventListener(ApplicationLoader.applicationContext) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientationEventListener == null || !initied) {
                    return;
                }
                WindowManager mgr = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
                int rotation = mgr.getDefaultDisplay().getRotation();
                if (lastOrientation != rotation) {
                    if (!isVideo) {
                        configurePhotoCamera();
                    }
                    lastOrientation = rotation;
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


    public void checkFlashMode(String mode) {
        ArrayList<String> modes = CameraController.getInstance().availableFlashModes;
        if (modes.contains(currentFlashMode)) {
            return;
        }
        currentFlashMode = mode;
        configurePhotoCamera();
        SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("camera", Activity.MODE_PRIVATE);
        sharedPreferences.edit().putString(cameraInfo.frontCamera != 0 ? "flashMode_front" : "flashMode", mode).commit();
    }

    public void setCurrentFlashMode(String mode) {
        currentFlashMode = mode;
        configurePhotoCamera();
        SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("camera", Activity.MODE_PRIVATE);
        sharedPreferences.edit().putString(cameraInfo.frontCamera != 0 ? "flashMode_front" : "flashMode", mode).commit();
    }

    public String getCurrentFlashMode() {
        return currentFlashMode;
    }

    public String getNextFlashMode() {
        ArrayList<String> modes = CameraController.getInstance().availableFlashModes;
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

    protected void setInitied() {
        initied = true;
    }

    protected boolean isInitied() {
        return initied;
    }

    protected void configurePhotoCamera() {
        try {
            Camera camera = cameraInfo.camera;
            if (camera != null) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.Parameters params = null;
                try {
                    params = camera.getParameters();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }

                Camera.getCameraInfo(cameraInfo.getCameraId(), info);

                int displayOrientation = getDisplayOrientation(info, true);
                int cameraDisplayOrientation;

                if ("samsung".equals(Build.MANUFACTURER) && "sf2wifixx".equals(Build.PRODUCT)) {
                    cameraDisplayOrientation = 0;
                } else {
                    int degrees = 0;
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
                camera.setDisplayOrientation(cameraDisplayOrientation);

                if (params != null) {
                    params.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
                    params.setPictureSize(pictureSize.getWidth(), pictureSize.getHeight());
                    params.setPictureFormat(pictureFormat);

                    String desiredMode;
                /*if (focusMode == FocusMode.OFF) {
                    desiredMode = Camera.Parameters.FOCUS_MODE_FIXED;
                } else if (focusMode == FocusMode.EDOF) {
                    desiredMode = Camera.Parameters.FOCUS_MODE_EDOF;
                } else if (isVideo) {
                    desiredMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
                } else {
                    desiredMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
                }*/
                    desiredMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
                    if (params.getSupportedFocusModes().contains(desiredMode)) {
                        params.setFocusMode(desiredMode);
                    }

                    int outputOrientation;
                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        outputOrientation = (360 - displayOrientation) % 360;
                    } else {
                        outputOrientation = displayOrientation;
                    }
                    try {
                        params.setRotation(outputOrientation);
                    } catch (Exception e) {
                        //
                    }
                    params.setFlashMode(currentFlashMode);
                    try {
                        camera.setParameters(params);
                    } catch (Exception e) {
                        //
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e("tmessages", e);
        }
    }

    protected void configureRecorder(int quality, MediaRecorder recorder) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraInfo.cameraId, info);
        int displayOrientation = getDisplayOrientation(info, false);
        recorder.setOrientationHint(displayOrientation);

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

    protected void stopVideoRecording() {
        isVideo = false;
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

    public void destroy() {
        initied = false;
        if (orientationEventListener != null) {
            orientationEventListener.disable();
            orientationEventListener = null;
        }
    }
}
