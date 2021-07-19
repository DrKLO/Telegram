/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import android.os.SystemClock;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import org.webrtc.CameraEnumerationAndroid.CaptureFormat;

@SuppressWarnings("deprecation")
public class Camera1Enumerator implements CameraEnumerator {
  private final static String TAG = "Camera1Enumerator";
  // Each entry contains the supported formats for corresponding camera index. The formats for all
  // cameras are enumerated on the first call to getSupportedFormats(), and cached for future
  // reference.
  private static List<List<CaptureFormat>> cachedSupportedFormats;

  private final boolean captureToTexture;

  public Camera1Enumerator() {
    this(true /* captureToTexture */);
  }

  public Camera1Enumerator(boolean captureToTexture) {
    this.captureToTexture = captureToTexture;
  }

  // Returns device names that can be used to create a new VideoCapturerAndroid.
  @Override
  public String[] getDeviceNames() {
    ArrayList<String> namesList = new ArrayList<>();
    for (int i = 0; i < android.hardware.Camera.getNumberOfCameras(); ++i) {
      String name = getDeviceName(i);
      if (name != null) {
        namesList.add(name);
        Logging.d(TAG, "Index: " + i + ". " + name);
      } else {
        Logging.e(TAG, "Index: " + i + ". Failed to query camera name.");
      }
    }
    String[] namesArray = new String[namesList.size()];
    return namesList.toArray(namesArray);
  }

  @Override
  public boolean isFrontFacing(String deviceName) {
    android.hardware.Camera.CameraInfo info = getCameraInfo(getCameraIndex(deviceName));
    return info != null && info.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT;
  }

  @Override
  public boolean isBackFacing(String deviceName) {
    android.hardware.Camera.CameraInfo info = getCameraInfo(getCameraIndex(deviceName));
    return info != null && info.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK;
  }

  @Override
  public List<CaptureFormat> getSupportedFormats(String deviceName) {
    return getSupportedFormats(getCameraIndex(deviceName));
  }

  @Override
  public CameraVideoCapturer createCapturer(
      String deviceName, CameraVideoCapturer.CameraEventsHandler eventsHandler) {
    return new Camera1Capturer(deviceName, eventsHandler, captureToTexture);
  }

  private static @Nullable android.hardware.Camera.CameraInfo getCameraInfo(int index) {
    android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
    try {
      android.hardware.Camera.getCameraInfo(index, info);
    } catch (Exception e) {
      Logging.e(TAG, "getCameraInfo failed on index " + index, e);
      return null;
    }
    return info;
  }

  static synchronized List<CaptureFormat> getSupportedFormats(int cameraId) {
    if (cachedSupportedFormats == null) {
      cachedSupportedFormats = new ArrayList<List<CaptureFormat>>();
      for (int i = 0; i < android.hardware.Camera.getNumberOfCameras(); ++i) {
        cachedSupportedFormats.add(enumerateFormats(i));
      }
    }
    return cachedSupportedFormats.get(cameraId);
  }

  private static List<CaptureFormat> enumerateFormats(int cameraId) {
    Logging.d(TAG, "Get supported formats for camera index " + cameraId + ".");
    final long startTimeMs = SystemClock.elapsedRealtime();
    final android.hardware.Camera.Parameters parameters;
    android.hardware.Camera camera = null;
    try {
      Logging.d(TAG, "Opening camera with index " + cameraId);
      camera = android.hardware.Camera.open(cameraId);
      parameters = camera.getParameters();
    } catch (RuntimeException e) {
      Logging.e(TAG, "Open camera failed on camera index " + cameraId, e);
      return new ArrayList<CaptureFormat>();
    } finally {
      if (camera != null) {
        camera.release();
      }
    }

    final List<CaptureFormat> formatList = new ArrayList<CaptureFormat>();
    try {
      int minFps = 0;
      int maxFps = 0;
      final List<int[]> listFpsRange = parameters.getSupportedPreviewFpsRange();
      if (listFpsRange != null) {
        // getSupportedPreviewFpsRange() returns a sorted list. Take the fps range
        // corresponding to the highest fps.
        final int[] range = listFpsRange.get(listFpsRange.size() - 1);
        minFps = range[android.hardware.Camera.Parameters.PREVIEW_FPS_MIN_INDEX];
        maxFps = range[android.hardware.Camera.Parameters.PREVIEW_FPS_MAX_INDEX];
      }
      for (android.hardware.Camera.Size size : parameters.getSupportedPreviewSizes()) {
        formatList.add(new CaptureFormat(size.width, size.height, minFps, maxFps));
      }
    } catch (Exception e) {
      Logging.e(TAG, "getSupportedFormats() failed on camera index " + cameraId, e);
    }

    final long endTimeMs = SystemClock.elapsedRealtime();
    Logging.d(TAG, "Get supported formats for camera index " + cameraId + " done."
            + " Time spent: " + (endTimeMs - startTimeMs) + " ms.");
    return formatList;
  }

  // Convert from android.hardware.Camera.Size to Size.
  static List<Size> convertSizes(List<android.hardware.Camera.Size> cameraSizes) {
    final List<Size> sizes = new ArrayList<Size>();
    for (android.hardware.Camera.Size size : cameraSizes) {
      sizes.add(new Size(size.width, size.height));
    }
    return sizes;
  }

  // Convert from int[2] to CaptureFormat.FramerateRange.
  static List<CaptureFormat.FramerateRange> convertFramerates(List<int[]> arrayRanges) {
    final List<CaptureFormat.FramerateRange> ranges = new ArrayList<CaptureFormat.FramerateRange>();
    for (int[] range : arrayRanges) {
      ranges.add(new CaptureFormat.FramerateRange(
          range[android.hardware.Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
          range[android.hardware.Camera.Parameters.PREVIEW_FPS_MAX_INDEX]));
    }
    return ranges;
  }

  // Returns the camera index for camera with name |deviceName|, or throws IllegalArgumentException
  // if no such camera can be found.
  static int getCameraIndex(String deviceName) {
    Logging.d(TAG, "getCameraIndex: " + deviceName);
    for (int i = 0; i < android.hardware.Camera.getNumberOfCameras(); ++i) {
      if (deviceName.equals(getDeviceName(i))) {
        return i;
      }
    }
    throw new IllegalArgumentException("No such camera: " + deviceName);
  }

  // Returns the name of the camera with camera index. Returns null if the
  // camera can not be used.
  static @Nullable String getDeviceName(int index) {
    android.hardware.Camera.CameraInfo info = getCameraInfo(index);
    if (info == null) {
      return null;
    }

    String facing =
        (info.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT) ? "front" : "back";
    return "Camera " + index + ", Facing " + facing + ", Orientation " + info.orientation;
  }
}
