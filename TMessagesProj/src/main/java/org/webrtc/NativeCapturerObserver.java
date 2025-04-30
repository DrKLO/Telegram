/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import org.webrtc.VideoFrame;

/**
 * Used from native api and implements a simple VideoCapturer.CapturerObserver that feeds frames to
 * a webrtc::jni::AndroidVideoTrackSource.
 */
class NativeCapturerObserver implements CapturerObserver {
  private final NativeAndroidVideoTrackSource nativeAndroidVideoTrackSource;

  @CalledByNative
  public NativeCapturerObserver(long nativeSource) {
    this.nativeAndroidVideoTrackSource = new NativeAndroidVideoTrackSource(nativeSource);
  }

  @Override
  public void onCapturerStarted(boolean success) {
    nativeAndroidVideoTrackSource.setState(success);
  }

  @Override
  public void onCapturerStopped() {
    nativeAndroidVideoTrackSource.setState(/* isLive= */ false);
  }

  @Override
  public void onFrameCaptured(VideoFrame frame) {
    final VideoProcessor.FrameAdaptationParameters parameters =
        nativeAndroidVideoTrackSource.adaptFrame(frame);
    if (parameters == null) {
      // Drop frame.
      return;
    }

    final VideoFrame.Buffer adaptedBuffer =
        frame.getBuffer().cropAndScale(parameters.cropX, parameters.cropY, parameters.cropWidth,
            parameters.cropHeight, parameters.scaleWidth, parameters.scaleHeight);
    nativeAndroidVideoTrackSource.onFrameCaptured(
        new VideoFrame(adaptedBuffer, frame.getRotation(), parameters.timestampNs));
    adaptedBuffer.release();
  }
}
