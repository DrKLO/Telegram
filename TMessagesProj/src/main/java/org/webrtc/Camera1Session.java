/*
 *  Copyright 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import android.content.Context;
import android.hardware.Camera;
import android.os.Handler;
import android.os.SystemClock;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.webrtc.CameraEnumerationAndroid.CaptureFormat;

@SuppressWarnings("deprecation")
class Camera1Session implements CameraSession {
  private static final String TAG = "Camera1Session";
  private static final int NUMBER_OF_CAPTURE_BUFFERS = 3;

  private static final Histogram camera1StartTimeMsHistogram =
      Histogram.createCounts("WebRTC.Android.Camera1.StartTimeMs", 1, 10000, 50);
  private static final Histogram camera1StopTimeMsHistogram =
      Histogram.createCounts("WebRTC.Android.Camera1.StopTimeMs", 1, 10000, 50);
  private static final Histogram camera1ResolutionHistogram = Histogram.createEnumeration(
      "WebRTC.Android.Camera1.Resolution", CameraEnumerationAndroid.COMMON_RESOLUTIONS.size());

  private static enum SessionState { RUNNING, STOPPED }

  private final Handler cameraThreadHandler;
  private final Events events;
  private final boolean captureToTexture;
  private final Context applicationContext;
  private final SurfaceTextureHelper surfaceTextureHelper;
  private final int cameraId;
  private final Camera camera;
  private final Camera.CameraInfo info;
  private final CaptureFormat captureFormat;
  // Used only for stats. Only used on the camera thread.
  private final long constructionTimeNs; // Construction time of this class.

  private OrientationHelper orientationHelper;

  private SessionState state;
  private boolean firstFrameReported;

  // TODO(titovartem) make correct fix during webrtc:9175
  @SuppressWarnings("ByteBufferBackingArray")
  public static void create(final CreateSessionCallback callback, final Events events,
      final boolean captureToTexture, final Context applicationContext,
      final SurfaceTextureHelper surfaceTextureHelper, final String cameraName,
      final int width, final int height, final int framerate) {
    final long constructionTimeNs = System.nanoTime();
    Logging.d(TAG, "Open camera " + cameraName);
    events.onCameraOpening();

    final int cameraId;
    try {
      cameraId = Camera1Enumerator.getCameraIndex(cameraName);
    } catch (IllegalArgumentException e) {
      callback.onFailure(FailureType.ERROR, e.getMessage());
      return;
    }

    final Camera camera;
    try {
      camera = Camera.open(cameraId);
    } catch (RuntimeException e) {
      callback.onFailure(FailureType.ERROR, e.getMessage());
      return;
    }

    if (camera == null) {
      callback.onFailure(
          FailureType.ERROR, "Camera.open returned null for camera id = " + cameraId);
      return;
    }

    try {
      camera.setPreviewTexture(surfaceTextureHelper.getSurfaceTexture());
    } catch (IOException | RuntimeException e) {
      camera.release();
      callback.onFailure(FailureType.ERROR, e.getMessage());
      return;
    }

    final Camera.CameraInfo info = new Camera.CameraInfo();
    Camera.getCameraInfo(cameraId, info);

    final CaptureFormat captureFormat;
    try {
      final Camera.Parameters parameters = camera.getParameters();
      captureFormat = findClosestCaptureFormat(parameters, width, height, framerate);
      final Size pictureSize = findClosestPictureSize(parameters, width, height);
      updateCameraParameters(camera, parameters, captureFormat, pictureSize, captureToTexture);
    } catch (RuntimeException e) {
      camera.release();
      callback.onFailure(FailureType.ERROR, e.getMessage());
      return;
    }

    if (!captureToTexture) {
      final int frameSize = captureFormat.frameSize();
      for (int i = 0; i < NUMBER_OF_CAPTURE_BUFFERS; ++i) {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(frameSize);
        camera.addCallbackBuffer(buffer.array());
      }
    }

    // Calculate orientation manually and send it as CVO instead.
    try {
      camera.setDisplayOrientation(0 /* degrees */);
    } catch (RuntimeException e) {
      camera.release();
      callback.onFailure(FailureType.ERROR, e.getMessage());
      return;
    }

    callback.onDone(new Camera1Session(events, captureToTexture, applicationContext,
        surfaceTextureHelper, cameraId, camera, info, captureFormat, constructionTimeNs));
  }

  private static void updateCameraParameters(Camera camera, Camera.Parameters parameters,
      CaptureFormat captureFormat, Size pictureSize, boolean captureToTexture) {
    final List<String> focusModes = parameters.getSupportedFocusModes();

    parameters.setPreviewFpsRange(captureFormat.framerate.min, captureFormat.framerate.max);
    parameters.setPreviewSize(captureFormat.width, captureFormat.height);
    parameters.setPictureSize(pictureSize.width, pictureSize.height);
    if (!captureToTexture) {
      parameters.setPreviewFormat(captureFormat.imageFormat);
    }

    if (parameters.isVideoStabilizationSupported()) {
      parameters.setVideoStabilization(true);
    }
    if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
      parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
    }
    camera.setParameters(parameters);
  }

  private static CaptureFormat findClosestCaptureFormat(
      Camera.Parameters parameters, int width, int height, int framerate) {
    // Find closest supported format for `width` x `height` @ `framerate`.
    final List<CaptureFormat.FramerateRange> supportedFramerates =
        Camera1Enumerator.convertFramerates(parameters.getSupportedPreviewFpsRange());
    Logging.d(TAG, "Available fps ranges: " + supportedFramerates);

    final CaptureFormat.FramerateRange fpsRange =
        CameraEnumerationAndroid.getClosestSupportedFramerateRange(supportedFramerates, framerate);

    final Size previewSize = CameraEnumerationAndroid.getClosestSupportedSize(
        Camera1Enumerator.convertSizes(parameters.getSupportedPreviewSizes()), width, height);
    CameraEnumerationAndroid.reportCameraResolution(camera1ResolutionHistogram, previewSize);

    return new CaptureFormat(previewSize.width, previewSize.height, fpsRange);
  }

  private static Size findClosestPictureSize(Camera.Parameters parameters, int width, int height) {
    return CameraEnumerationAndroid.getClosestSupportedSize(
        Camera1Enumerator.convertSizes(parameters.getSupportedPictureSizes()), width, height);
  }

  private Camera1Session(Events events, boolean captureToTexture, Context applicationContext,
      SurfaceTextureHelper surfaceTextureHelper, int cameraId, Camera camera,
      Camera.CameraInfo info, CaptureFormat captureFormat, long constructionTimeNs) {
    Logging.d(TAG, "Create new camera1 session on camera " + cameraId);

    this.cameraThreadHandler = new Handler();
    this.events = events;
    this.captureToTexture = captureToTexture;
    this.applicationContext = applicationContext;
    this.surfaceTextureHelper = surfaceTextureHelper;
    this.cameraId = cameraId;
    this.camera = camera;
    this.info = info;
    this.captureFormat = captureFormat;
    this.constructionTimeNs = constructionTimeNs;
    this.orientationHelper = new OrientationHelper();

    surfaceTextureHelper.setTextureSize(captureFormat.width, captureFormat.height);

    startCapturing();
  }

  @Override
  public void stop() {
    Logging.d(TAG, "Stop camera1 session on camera " + cameraId);
    checkIsOnCameraThread();
    if (state != SessionState.STOPPED) {
      final long stopStartTime = System.nanoTime();
      stopInternal();
      final int stopTimeMs = (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStartTime);
      camera1StopTimeMsHistogram.addSample(stopTimeMs);
    }
  }

  private void startCapturing() {
    Logging.d(TAG, "Start capturing");
    checkIsOnCameraThread();

    state = SessionState.RUNNING;

    camera.setErrorCallback(new Camera.ErrorCallback() {
      @Override
      public void onError(int error, Camera camera) {
        String errorMessage;
        if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
          errorMessage = "Camera server died!";
        } else {
          errorMessage = "Camera error: " + error;
        }
        Logging.e(TAG, errorMessage);
        stopInternal();
        if (error == Camera.CAMERA_ERROR_EVICTED) {
          events.onCameraDisconnected(Camera1Session.this);
        } else {
          events.onCameraError(Camera1Session.this, errorMessage);
        }
      }
    });

    if (captureToTexture) {
      listenForTextureFrames();
    } else {
      listenForBytebufferFrames();
    }
    try {
      camera.startPreview();
    } catch (RuntimeException e) {
      stopInternal();
      events.onCameraError(this, e.getMessage());
    }
  }

  private void stopInternal() {
    Logging.d(TAG, "Stop internal");
    checkIsOnCameraThread();
    if (state == SessionState.STOPPED) {
      Logging.d(TAG, "Camera is already stopped");
      return;
    }

    state = SessionState.STOPPED;
    surfaceTextureHelper.stopListening();
    // Note: stopPreview or other driver code might deadlock. Deadlock in
    // Camera._stopPreview(Native Method) has been observed on
    // Nexus 5 (hammerhead), OS version LMY48I.
    camera.stopPreview();
    camera.release();
    events.onCameraClosed(this);
    if (orientationHelper != null) {
      orientationHelper.stop();
    }
    Logging.d(TAG, "Stop done");
  }

  private void listenForTextureFrames() {
    surfaceTextureHelper.startListening((VideoFrame frame) -> {
      checkIsOnCameraThread();

      if (state != SessionState.RUNNING) {
        Logging.d(TAG, "Texture frame captured but camera is no longer running.");
        return;
      }

      if (!firstFrameReported) {
        final int startTimeMs =
            (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - constructionTimeNs);
        camera1StartTimeMsHistogram.addSample(startTimeMs);
        firstFrameReported = true;
      }

      // Undo the mirror that the OS "helps" us with.
      // http://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
      final VideoFrame modifiedFrame =
          new VideoFrame(CameraSession.createTextureBufferWithModifiedTransformMatrix(
                             (TextureBufferImpl) frame.getBuffer(),
                             /* mirror= */ info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT,
                             /* rotation= */ 0),
              /* rotation= */ getFrameOrientation(), frame.getTimestampNs());
      events.onFrameCaptured(Camera1Session.this, modifiedFrame);
      modifiedFrame.release();
    });
  }

  private void listenForBytebufferFrames() {
    camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
      @Override
      public void onPreviewFrame(final byte[] data, Camera callbackCamera) {
        checkIsOnCameraThread();

        if (callbackCamera != camera) {
          Logging.e(TAG, "Callback from a different camera. This should never happen.");
          return;
        }

        if (state != SessionState.RUNNING) {
          Logging.d(TAG, "Bytebuffer frame captured but camera is no longer running.");
          return;
        }

        final long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());

        if (!firstFrameReported) {
          final int startTimeMs =
              (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - constructionTimeNs);
          camera1StartTimeMsHistogram.addSample(startTimeMs);
          firstFrameReported = true;
        }

        VideoFrame.Buffer frameBuffer = new NV21Buffer(
            data, captureFormat.width, captureFormat.height, () -> cameraThreadHandler.post(() -> {
              if (state == SessionState.RUNNING) {
                camera.addCallbackBuffer(data);
              }
            }));
        final VideoFrame frame = new VideoFrame(frameBuffer, getFrameOrientation(), captureTimeNs);
        events.onFrameCaptured(Camera1Session.this, frame);
        frame.release();
      }
    });
  }

  private int getFrameOrientation() {
    int rotation = orientationHelper.getOrientation();
    OrientationHelper.cameraOrientation = rotation;
    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      rotation = 360 - rotation;
    }
    OrientationHelper.cameraRotation = rotation;
    return (info.orientation + rotation) % 360;
  }

  private void checkIsOnCameraThread() {
    if (Thread.currentThread() != cameraThreadHandler.getLooper().getThread()) {
      throw new IllegalStateException("Wrong thread");
    }
  }
}
