/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger.camera;

import android.hardware.Camera;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;

import java.util.ArrayList;

public class CameraInfo {


    protected int cameraId;
    protected Camera camera;
    protected ArrayList<Size> pictureSizes = new ArrayList<>();
    protected ArrayList<Size> previewSizes = new ArrayList<>();
    protected final int frontCamera;

    protected CameraDevice cameraDevice;
    CameraCharacteristics cameraCharacteristics;
    CaptureRequest.Builder captureRequestBuilder;
    public CameraCaptureSession cameraCaptureSession;


    public CameraInfo(int id, int frontFace) {
        cameraId = id;
        frontCamera = frontFace;
    }

    public int getCameraId() {
        return cameraId;
    }

    private Camera getCamera() {
        return camera;
    }

    public ArrayList<Size> getPreviewSizes() {
        return previewSizes;
    }

    public ArrayList<Size> getPictureSizes() {
        return pictureSizes;
    }

    public boolean isFrontface() {
        return frontCamera != 0;
    }
}
