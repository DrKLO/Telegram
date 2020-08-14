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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import androidx.annotation.Nullable;
import android.view.Surface;

/**
 * An implementation of VideoCapturer to capture the screen content as a video stream.
 * Capturing is done by {@code MediaProjection} on a {@code SurfaceTexture}. We interact with this
 * {@code SurfaceTexture} using a {@code SurfaceTextureHelper}.
 * The {@code SurfaceTextureHelper} is created by the native code and passed to this capturer in
 * {@code VideoCapturer.initialize()}. On receiving a new frame, this capturer passes it
 * as a texture to the native code via {@code CapturerObserver.onFrameCaptured()}. This takes
 * place on the HandlerThread of the given {@code SurfaceTextureHelper}. When done with each frame,
 * the native code returns the buffer to the  {@code SurfaceTextureHelper} to be used for new
 * frames. At any time, at most one frame is being processed.
 *
 * @note This class is only supported on Android Lollipop and above.
 */
@TargetApi(21)
public class ScreenCapturerAndroid implements VideoCapturer, VideoSink {
  private static final int DISPLAY_FLAGS =
      DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
  // DPI for VirtualDisplay, does not seem to matter for us.
  private static final int VIRTUAL_DISPLAY_DPI = 400;

  private final Intent mediaProjectionPermissionResultData;
  private final MediaProjection.Callback mediaProjectionCallback;

  private int width;
  private int height;
  @Nullable private VirtualDisplay virtualDisplay;
  @Nullable private SurfaceTextureHelper surfaceTextureHelper;
  @Nullable private CapturerObserver capturerObserver;
  private long numCapturedFrames;
  @Nullable private MediaProjection mediaProjection;
  private boolean isDisposed;
  @Nullable private MediaProjectionManager mediaProjectionManager;

  /**
   * Constructs a new Screen Capturer.
   *
   * @param mediaProjectionPermissionResultData the result data of MediaProjection permission
   *     activity; the calling app must validate that result code is Activity.RESULT_OK before
   *     calling this method.
   * @param mediaProjectionCallback MediaProjection callback to implement application specific
   *     logic in events such as when the user revokes a previously granted capture permission.
  **/
  public ScreenCapturerAndroid(Intent mediaProjectionPermissionResultData,
      MediaProjection.Callback mediaProjectionCallback) {
    this.mediaProjectionPermissionResultData = mediaProjectionPermissionResultData;
    this.mediaProjectionCallback = mediaProjectionCallback;
  }

  private void checkNotDisposed() {
    if (isDisposed) {
      throw new RuntimeException("capturer is disposed.");
    }
  }

  @Override
  // TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
  @SuppressWarnings("NoSynchronizedMethodCheck")
  public synchronized void initialize(final SurfaceTextureHelper surfaceTextureHelper,
      final Context applicationContext, final CapturerObserver capturerObserver) {
    checkNotDisposed();

    if (capturerObserver == null) {
      throw new RuntimeException("capturerObserver not set.");
    }
    this.capturerObserver = capturerObserver;

    if (surfaceTextureHelper == null) {
      throw new RuntimeException("surfaceTextureHelper not set.");
    }
    this.surfaceTextureHelper = surfaceTextureHelper;

    mediaProjectionManager = (MediaProjectionManager) applicationContext.getSystemService(
        Context.MEDIA_PROJECTION_SERVICE);
  }

  @Override
  // TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
  @SuppressWarnings("NoSynchronizedMethodCheck")
  public synchronized void startCapture(
      final int width, final int height, final int ignoredFramerate) {
    checkNotDisposed();

    this.width = width;
    this.height = height;

    mediaProjection = mediaProjectionManager.getMediaProjection(
        Activity.RESULT_OK, mediaProjectionPermissionResultData);

    // Let MediaProjection callback use the SurfaceTextureHelper thread.
    mediaProjection.registerCallback(mediaProjectionCallback, surfaceTextureHelper.getHandler());

    createVirtualDisplay();
    capturerObserver.onCapturerStarted(true);
    surfaceTextureHelper.startListening(ScreenCapturerAndroid.this);
  }

  @Override
  // TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
  @SuppressWarnings("NoSynchronizedMethodCheck")
  public synchronized void stopCapture() {
    checkNotDisposed();
    ThreadUtils.invokeAtFrontUninterruptibly(surfaceTextureHelper.getHandler(), new Runnable() {
      @Override
      public void run() {
        surfaceTextureHelper.stopListening();
        capturerObserver.onCapturerStopped();

        if (virtualDisplay != null) {
          virtualDisplay.release();
          virtualDisplay = null;
        }

        if (mediaProjection != null) {
          // Unregister the callback before stopping, otherwise the callback recursively
          // calls this method.
          mediaProjection.unregisterCallback(mediaProjectionCallback);
          mediaProjection.stop();
          mediaProjection = null;
        }
      }
    });
  }

  @Override
  // TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
  @SuppressWarnings("NoSynchronizedMethodCheck")
  public synchronized void dispose() {
    isDisposed = true;
  }

  /**
   * Changes output video format. This method can be used to scale the output
   * video, or to change orientation when the captured screen is rotated for example.
   *
   * @param width new output video width
   * @param height new output video height
   * @param ignoredFramerate ignored
   */
  @Override
  // TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
  @SuppressWarnings("NoSynchronizedMethodCheck")
  public synchronized void changeCaptureFormat(
      final int width, final int height, final int ignoredFramerate) {
    checkNotDisposed();

    this.width = width;
    this.height = height;

    if (virtualDisplay == null) {
      // Capturer is stopped, the virtual display will be created in startCaptuer().
      return;
    }

    // Create a new virtual display on the surfaceTextureHelper thread to avoid interference
    // with frame processing, which happens on the same thread (we serialize events by running
    // them on the same thread).
    ThreadUtils.invokeAtFrontUninterruptibly(surfaceTextureHelper.getHandler(), new Runnable() {
      @Override
      public void run() {
        virtualDisplay.release();
        createVirtualDisplay();
      }
    });
  }

  private void createVirtualDisplay() {
    surfaceTextureHelper.setTextureSize(width, height);
    virtualDisplay = mediaProjection.createVirtualDisplay("WebRTC_ScreenCapture", width, height,
        VIRTUAL_DISPLAY_DPI, DISPLAY_FLAGS, new Surface(surfaceTextureHelper.getSurfaceTexture()),
        null /* callback */, null /* callback handler */);
  }

  // This is called on the internal looper thread of {@Code SurfaceTextureHelper}.
  @Override
  public void onFrame(VideoFrame frame) {
    numCapturedFrames++;
    capturerObserver.onFrameCaptured(frame);
  }

  @Override
  public boolean isScreencast() {
    return true;
  }

  public long getNumCapturedFrames() {
    return numCapturedFrames;
  }
}
