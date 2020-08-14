/*
 *  Copyright 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import androidx.annotation.Nullable;

/**
 * Java wrapper of native AndroidVideoTrackSource.
 */
public class VideoSource extends MediaSource {
  /** Simple aspect ratio clas for use in constraining output format. */
  public static class AspectRatio {
    public static final AspectRatio UNDEFINED = new AspectRatio(/* width= */ 0, /* height= */ 0);

    public final int width;
    public final int height;

    public AspectRatio(int width, int height) {
      this.width = width;
      this.height = height;
    }
  }

  private final NativeAndroidVideoTrackSource nativeAndroidVideoTrackSource;
  private final Object videoProcessorLock = new Object();
  @Nullable private VideoProcessor videoProcessor;
  private boolean isCapturerRunning;

  private final CapturerObserver capturerObserver = new CapturerObserver() {
    @Override
    public void onCapturerStarted(boolean success) {
      nativeAndroidVideoTrackSource.setState(success);
      synchronized (videoProcessorLock) {
        isCapturerRunning = success;
        if (videoProcessor != null) {
          videoProcessor.onCapturerStarted(success);
        }
      }
    }

    @Override
    public void onCapturerStopped() {
      nativeAndroidVideoTrackSource.setState(/* isLive= */ false);
      synchronized (videoProcessorLock) {
        isCapturerRunning = false;
        if (videoProcessor != null) {
          videoProcessor.onCapturerStopped();
        }
      }
    }

    @Override
    public void onFrameCaptured(VideoFrame frame) {
      final VideoProcessor.FrameAdaptationParameters parameters =
          nativeAndroidVideoTrackSource.adaptFrame(frame);
      synchronized (videoProcessorLock) {
        if (videoProcessor != null) {
          videoProcessor.onFrameCaptured(frame, parameters);
          return;
        }
      }

      VideoFrame adaptedFrame = VideoProcessor.applyFrameAdaptationParameters(frame, parameters);
      if (adaptedFrame != null) {
        nativeAndroidVideoTrackSource.onFrameCaptured(adaptedFrame);
        adaptedFrame.release();
      }
    }
  };

  public VideoSource(long nativeSource) {
    super(nativeSource);
    this.nativeAndroidVideoTrackSource = new NativeAndroidVideoTrackSource(nativeSource);
  }

  /**
   * Calling this function will cause frames to be scaled down to the requested resolution. Also,
   * frames will be cropped to match the requested aspect ratio, and frames will be dropped to match
   * the requested fps. The requested aspect ratio is orientation agnostic and will be adjusted to
   * maintain the input orientation, so it doesn't matter if e.g. 1280x720 or 720x1280 is requested.
   */
  public void adaptOutputFormat(int width, int height, int fps) {
    final int maxSide = Math.max(width, height);
    final int minSide = Math.min(width, height);
    adaptOutputFormat(maxSide, minSide, minSide, maxSide, fps);
  }

  /**
   * Same as above, but allows setting two different target resolutions depending on incoming
   * frame orientation. This gives more fine-grained control and can e.g. be used to force landscape
   * video to be cropped to portrait video.
   */
  public void adaptOutputFormat(
      int landscapeWidth, int landscapeHeight, int portraitWidth, int portraitHeight, int fps) {
    adaptOutputFormat(new AspectRatio(landscapeWidth, landscapeHeight),
        /* maxLandscapePixelCount= */ landscapeWidth * landscapeHeight,
        new AspectRatio(portraitWidth, portraitHeight),
        /* maxPortraitPixelCount= */ portraitWidth * portraitHeight, fps);
  }

  /** Same as above, with even more control as each constraint is optional. */
  public void adaptOutputFormat(AspectRatio targetLandscapeAspectRatio,
      @Nullable Integer maxLandscapePixelCount, AspectRatio targetPortraitAspectRatio,
      @Nullable Integer maxPortraitPixelCount, @Nullable Integer maxFps) {
    nativeAndroidVideoTrackSource.adaptOutputFormat(targetLandscapeAspectRatio,
        maxLandscapePixelCount, targetPortraitAspectRatio, maxPortraitPixelCount, maxFps);
  }

  public void setIsScreencast(boolean isScreencast) {
    nativeAndroidVideoTrackSource.setIsScreencast(isScreencast);
  }

  /**
   * Hook for injecting a custom video processor before frames are passed onto WebRTC. The frames
   * will be cropped and scaled depending on CPU and network conditions before they are passed to
   * the video processor. Frames will be delivered to the video processor on the same thread they
   * are passed to this object. The video processor is allowed to deliver the processed frames
   * back on any thread.
   */
  public void setVideoProcessor(@Nullable VideoProcessor newVideoProcessor) {
    synchronized (videoProcessorLock) {
      if (videoProcessor != null) {
        videoProcessor.setSink(/* sink= */ null);
        if (isCapturerRunning) {
          videoProcessor.onCapturerStopped();
        }
      }
      videoProcessor = newVideoProcessor;
      if (newVideoProcessor != null) {
        newVideoProcessor.setSink(
            (frame)
                -> runWithReference(() -> nativeAndroidVideoTrackSource.onFrameCaptured(frame)));
        if (isCapturerRunning) {
          newVideoProcessor.onCapturerStarted(/* success= */ true);
        }
      }
    }
  }

  public CapturerObserver getCapturerObserver() {
    return capturerObserver;
  }

  /** Returns a pointer to webrtc::VideoTrackSourceInterface. */
  long getNativeVideoTrackSource() {
    return getNativeMediaSource();
  }

  @Override
  public void dispose() {
    setVideoProcessor(/* newVideoProcessor= */ null);
    super.dispose();
  }
}
