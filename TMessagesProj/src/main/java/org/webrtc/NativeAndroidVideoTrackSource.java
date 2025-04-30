/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import androidx.annotation.Nullable;
import org.webrtc.VideoFrame;
import org.webrtc.VideoProcessor;

/**
 * This class is meant to be a simple layer that only handles the JNI wrapping of a C++
 * AndroidVideoTrackSource, that can easily be mocked out in Java unit tests. Refrain from adding
 * any unnecessary logic to this class.
 * This class is thred safe and methods can be called from any thread, but if frames A, B, ..., are
 * sent to adaptFrame(), the adapted frames adaptedA, adaptedB, ..., needs to be passed in the same
 * order to onFrameCaptured().
 */
class NativeAndroidVideoTrackSource {
  // Pointer to webrtc::jni::AndroidVideoTrackSource.
  private final long nativeAndroidVideoTrackSource;

  public NativeAndroidVideoTrackSource(long nativeAndroidVideoTrackSource) {
    this.nativeAndroidVideoTrackSource = nativeAndroidVideoTrackSource;
  }

  /**
   * Set the state for the native MediaSourceInterface. Maps boolean to either
   * SourceState::kLive or SourceState::kEnded.
   */
  public void setState(boolean isLive) {
    nativeSetState(nativeAndroidVideoTrackSource, isLive);
  }

  /**
   * This function should be called before delivering any frame to determine if the frame should be
   * dropped or what the cropping and scaling parameters should be. If the return value is null, the
   * frame should be dropped, otherwise the frame should be adapted in accordance to the frame
   * adaptation parameters before calling onFrameCaptured().
   */
  @Nullable
  public VideoProcessor.FrameAdaptationParameters adaptFrame(VideoFrame frame) {
    return nativeAdaptFrame(nativeAndroidVideoTrackSource, frame.getBuffer().getWidth(),
        frame.getBuffer().getHeight(), frame.getRotation(), frame.getTimestampNs());
  }

  /**
   * Pass an adapted frame to the native AndroidVideoTrackSource. Note that adaptFrame() is
   * expected to be called first and that the passed frame conforms to those parameters.
   */
  public void onFrameCaptured(VideoFrame frame) {
    nativeOnFrameCaptured(nativeAndroidVideoTrackSource, frame.getRotation(),
        frame.getTimestampNs(), frame.getBuffer());
  }

  /**
   * Calling this function will cause frames to be scaled down to the requested resolution. Also,
   * frames will be cropped to match the requested aspect ratio, and frames will be dropped to match
   * the requested fps.
   */
  public void adaptOutputFormat(VideoSource.AspectRatio targetLandscapeAspectRatio,
      @Nullable Integer maxLandscapePixelCount, VideoSource.AspectRatio targetPortraitAspectRatio,
      @Nullable Integer maxPortraitPixelCount, @Nullable Integer maxFps) {
    nativeAdaptOutputFormat(nativeAndroidVideoTrackSource, targetLandscapeAspectRatio.width,
        targetLandscapeAspectRatio.height, maxLandscapePixelCount, targetPortraitAspectRatio.width,
        targetPortraitAspectRatio.height, maxPortraitPixelCount, maxFps);
  }

  public void setIsScreencast(boolean isScreencast) {
    nativeSetIsScreencast(nativeAndroidVideoTrackSource, isScreencast);
  }

  @CalledByNative
  static VideoProcessor.FrameAdaptationParameters createFrameAdaptationParameters(int cropX,
      int cropY, int cropWidth, int cropHeight, int scaleWidth, int scaleHeight, long timestampNs,
      boolean drop) {
    return new VideoProcessor.FrameAdaptationParameters(
        cropX, cropY, cropWidth, cropHeight, scaleWidth, scaleHeight, timestampNs, drop);
  }

  private static native void nativeSetIsScreencast(
      long nativeAndroidVideoTrackSource, boolean isScreencast);
  private static native void nativeSetState(long nativeAndroidVideoTrackSource, boolean isLive);
  private static native void nativeAdaptOutputFormat(long nativeAndroidVideoTrackSource,
      int landscapeWidth, int landscapeHeight, @Nullable Integer maxLandscapePixelCount,
      int portraitWidth, int portraitHeight, @Nullable Integer maxPortraitPixelCount,
      @Nullable Integer maxFps);
  @Nullable
  private static native VideoProcessor.FrameAdaptationParameters nativeAdaptFrame(
      long nativeAndroidVideoTrackSource, int width, int height, int rotation, long timestampNs);
  private static native void nativeOnFrameCaptured(
      long nativeAndroidVideoTrackSource, int rotation, long timestampNs, VideoFrame.Buffer buffer);
}
