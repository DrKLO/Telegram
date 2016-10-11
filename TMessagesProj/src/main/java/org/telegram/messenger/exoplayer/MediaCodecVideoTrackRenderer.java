/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.telegram.messenger.exoplayer;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;
import android.view.TextureView;
import org.telegram.messenger.exoplayer.MediaCodecUtil.DecoderQueryException;
import org.telegram.messenger.exoplayer.drm.DrmSessionManager;
import org.telegram.messenger.exoplayer.drm.FrameworkMediaCrypto;
import org.telegram.messenger.exoplayer.util.MimeTypes;
import org.telegram.messenger.exoplayer.util.TraceUtil;
import org.telegram.messenger.exoplayer.util.Util;
import java.nio.ByteBuffer;

/**
 * Decodes and renders video using {@link MediaCodec}.
 */
@TargetApi(16)
public class MediaCodecVideoTrackRenderer extends MediaCodecTrackRenderer {

  /**
   * Interface definition for a callback to be notified of {@link MediaCodecVideoTrackRenderer}
   * events.
   */
  public interface EventListener extends MediaCodecTrackRenderer.EventListener {

    /**
     * Invoked to report the number of frames dropped by the renderer. Dropped frames are reported
     * whenever the renderer is stopped having dropped frames, and optionally, whenever the count
     * reaches a specified threshold whilst the renderer is started.
     *
     * @param count The number of dropped frames.
     * @param elapsed The duration in milliseconds over which the frames were dropped. This
     *     duration is timed from when the renderer was started or from when dropped frames were
     *     last reported (whichever was more recent), and not from when the first of the reported
     *     drops occurred.
     */
    void onDroppedFrames(int count, long elapsed);

    /**
     * Invoked each time there's a change in the size of the video being rendered.
     *
     * @param width The video width in pixels.
     * @param height The video height in pixels.
     * @param unappliedRotationDegrees For videos that require a rotation, this is the clockwise
     *     rotation in degrees that the application should apply for the video for it to be rendered
     *     in the correct orientation. This value will always be zero on API levels 21 and above,
     *     since the renderer will apply all necessary rotations internally. On earlier API levels
     *     this is not possible. Applications that use {@link TextureView} can apply the rotation by
     *     calling {@link TextureView#setTransform}. Applications that do not expect to encounter
     *     rotated videos can safely ignore this parameter.
     * @param pixelWidthHeightRatio The width to height ratio of each pixel. For the normal case
     *     of square pixels this will be equal to 1.0. Different values are indicative of anamorphic
     *     content.
     */
    void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
        float pixelWidthHeightRatio);

    /**
     * Invoked when a frame is rendered to a surface for the first time following that surface
     * having been set as the target for the renderer.
     *
     * @param surface The surface to which a first frame has been rendered.
     */
    void onDrawnToSurface(Surface surface);

  }

  // TODO: Use MediaFormat constants if these get exposed through the API. See
  // [Internal: b/14127601].
  private static final String KEY_CROP_LEFT = "crop-left";
  private static final String KEY_CROP_RIGHT = "crop-right";
  private static final String KEY_CROP_BOTTOM = "crop-bottom";
  private static final String KEY_CROP_TOP = "crop-top";

  /**
   * The type of a message that can be passed to an instance of this class via
   * {@link ExoPlayer#sendMessage} or {@link ExoPlayer#blockingSendMessage}. The message object
   * should be the target {@link Surface}, or null.
   */
  public static final int MSG_SET_SURFACE = 1;

  private final VideoFrameReleaseTimeHelper frameReleaseTimeHelper;
  private final EventListener eventListener;
  private final long allowedJoiningTimeUs;
  private final int videoScalingMode;
  private final int maxDroppedFrameCountToNotify;

  private Surface surface;
  private boolean reportedDrawnToSurface;
  private boolean renderedFirstFrame;
  private long joiningDeadlineUs;
  private long droppedFrameAccumulationStartTimeMs;
  private int droppedFrameCount;
  private int consecutiveDroppedFrameCount;

  private int pendingRotationDegrees;
  private float pendingPixelWidthHeightRatio;
  private int currentWidth;
  private int currentHeight;
  private int currentUnappliedRotationDegrees;
  private float currentPixelWidthHeightRatio;
  private int lastReportedWidth;
  private int lastReportedHeight;
  private int lastReportedUnappliedRotationDegrees;
  private float lastReportedPixelWidthHeightRatio;

  /**
   * @param context A context.
   * @param source The upstream source from which the renderer obtains samples.
   * @param mediaCodecSelector A decoder selector.
   * @param videoScalingMode The scaling mode to pass to
   *     {@link MediaCodec#setVideoScalingMode(int)}.
   */
  public MediaCodecVideoTrackRenderer(Context context, SampleSource source,
      MediaCodecSelector mediaCodecSelector, int videoScalingMode) {
    this(context, source, mediaCodecSelector, videoScalingMode, 0);
  }

  /**
   * @param context A context.
   * @param source The upstream source from which the renderer obtains samples.
   * @param mediaCodecSelector A decoder selector.
   * @param videoScalingMode The scaling mode to pass to
   *     {@link MediaCodec#setVideoScalingMode(int)}.
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   */
  public MediaCodecVideoTrackRenderer(Context context, SampleSource source,
      MediaCodecSelector mediaCodecSelector, int videoScalingMode, long allowedJoiningTimeMs) {
    this(context, source, mediaCodecSelector, videoScalingMode, allowedJoiningTimeMs, null, null,
        -1);
  }

  /**
   * @param context A context.
   * @param source The upstream source from which the renderer obtains samples.
   * @param mediaCodecSelector A decoder selector.
   * @param videoScalingMode The scaling mode to pass to
   *     {@link MediaCodec#setVideoScalingMode(int)}.
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFrameCountToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link EventListener#onDroppedFrames(int, long)}.
   */
  public MediaCodecVideoTrackRenderer(Context context, SampleSource source,
      MediaCodecSelector mediaCodecSelector, int videoScalingMode, long allowedJoiningTimeMs,
      Handler eventHandler, EventListener eventListener, int maxDroppedFrameCountToNotify) {
    this(context, source, mediaCodecSelector, videoScalingMode, allowedJoiningTimeMs, null, false,
        eventHandler, eventListener, maxDroppedFrameCountToNotify);
  }

  /**
   * @param context A context.
   * @param source The upstream source from which the renderer obtains samples.
   * @param mediaCodecSelector A decoder selector.
   * @param videoScalingMode The scaling mode to pass to
   *     {@link MediaCodec#setVideoScalingMode(int)}.
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param drmSessionManager For use with encrypted content. May be null if support for encrypted
   *     content is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisision. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFrameCountToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link EventListener#onDroppedFrames(int, long)}.
   */
  public MediaCodecVideoTrackRenderer(Context context, SampleSource source,
      MediaCodecSelector mediaCodecSelector, int videoScalingMode, long allowedJoiningTimeMs,
      DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys, Handler eventHandler, EventListener eventListener,
      int maxDroppedFrameCountToNotify) {
    super(source, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, eventHandler,
        eventListener);
    this.frameReleaseTimeHelper = new VideoFrameReleaseTimeHelper(context);
    this.videoScalingMode = videoScalingMode;
    this.allowedJoiningTimeUs = allowedJoiningTimeMs * 1000;
    this.eventListener = eventListener;
    this.maxDroppedFrameCountToNotify = maxDroppedFrameCountToNotify;
    joiningDeadlineUs = -1;
    currentWidth = -1;
    currentHeight = -1;
    currentPixelWidthHeightRatio = -1;
    pendingPixelWidthHeightRatio = -1;
    lastReportedWidth = -1;
    lastReportedHeight = -1;
    lastReportedPixelWidthHeightRatio = -1;
  }

  @Override
  protected boolean handlesTrack(MediaCodecSelector mediaCodecSelector, MediaFormat mediaFormat)
      throws DecoderQueryException {
    String mimeType = mediaFormat.mimeType;
    return MimeTypes.isVideo(mimeType) && (MimeTypes.VIDEO_UNKNOWN.equals(mimeType)
        || mediaCodecSelector.getDecoderInfo(mimeType, false) != null);
  }

  @Override
  protected void onEnabled(int track, long positionUs, boolean joining)
      throws ExoPlaybackException {
    super.onEnabled(track, positionUs, joining);
    if (joining && allowedJoiningTimeUs > 0) {
      joiningDeadlineUs = SystemClock.elapsedRealtime() * 1000L + allowedJoiningTimeUs;
    }
    frameReleaseTimeHelper.enable();
  }

  @Override
  protected void onDiscontinuity(long positionUs) throws ExoPlaybackException {
    super.onDiscontinuity(positionUs);
    renderedFirstFrame = false;
    consecutiveDroppedFrameCount = 0;
    joiningDeadlineUs = -1;
  }

  @Override
  protected boolean isReady() {
    if (super.isReady() && (renderedFirstFrame || !codecInitialized()
        || getSourceState() == SOURCE_STATE_READY_READ_MAY_FAIL)) {
      // Ready. If we were joining then we've now joined, so clear the joining deadline.
      joiningDeadlineUs = -1;
      return true;
    } else if (joiningDeadlineUs == -1) {
      // Not joining.
      return false;
    } else if (SystemClock.elapsedRealtime() * 1000 < joiningDeadlineUs) {
      // Joining and still within the joining deadline.
      return true;
    } else {
      // The joining deadline has been exceeded. Give up and clear the deadline.
      joiningDeadlineUs = -1;
      return false;
    }
  }

  @Override
  protected void onStarted() {
    super.onStarted();
    droppedFrameCount = 0;
    droppedFrameAccumulationStartTimeMs = SystemClock.elapsedRealtime();
  }

  @Override
  protected void onStopped() {
    joiningDeadlineUs = -1;
    maybeNotifyDroppedFrameCount();
    super.onStopped();
  }

  @Override
  protected void onDisabled() throws ExoPlaybackException {
    currentWidth = -1;
    currentHeight = -1;
    currentPixelWidthHeightRatio = -1;
    pendingPixelWidthHeightRatio = -1;
    lastReportedWidth = -1;
    lastReportedHeight = -1;
    lastReportedPixelWidthHeightRatio = -1;
    frameReleaseTimeHelper.disable();
    super.onDisabled();
  }

  @Override
  public void handleMessage(int messageType, Object message) throws ExoPlaybackException {
    if (messageType == MSG_SET_SURFACE) {
      setSurface((Surface) message);
    } else {
      super.handleMessage(messageType, message);
    }
  }

  /**
   * @param surface The surface to set.
   * @throws ExoPlaybackException
   */
  private void setSurface(Surface surface) throws ExoPlaybackException {
    if (this.surface == surface) {
      return;
    }
    this.surface = surface;
    this.reportedDrawnToSurface = false;
    int state = getState();
    if (state == TrackRenderer.STATE_ENABLED || state == TrackRenderer.STATE_STARTED) {
      releaseCodec();
      maybeInitCodec();
    }
  }

  @Override
  protected boolean shouldInitCodec() {
    return super.shouldInitCodec() && surface != null && surface.isValid();
  }

  // Override configureCodec to provide the surface.
  @Override
  protected void configureCodec(MediaCodec codec, boolean codecIsAdaptive,
      android.media.MediaFormat format, MediaCrypto crypto) {
    maybeSetMaxInputSize(format, codecIsAdaptive);
    codec.configure(format, surface, crypto, 0);
  }

  @Override
  protected void onInputFormatChanged(MediaFormatHolder holder) throws ExoPlaybackException {
    super.onInputFormatChanged(holder);
    pendingPixelWidthHeightRatio = holder.format.pixelWidthHeightRatio == MediaFormat.NO_VALUE ? 1
        : holder.format.pixelWidthHeightRatio;
    pendingRotationDegrees = holder.format.rotationDegrees == MediaFormat.NO_VALUE ? 0
        : holder.format.rotationDegrees;
  }

  /**
   * @return True if the first frame has been rendered (playback has not necessarily begun).
   */
  protected final boolean haveRenderedFirstFrame() {
    return renderedFirstFrame;
  }

  @Override
  protected void onOutputFormatChanged(MediaCodec codec, android.media.MediaFormat outputFormat) {
    boolean hasCrop = outputFormat.containsKey(KEY_CROP_RIGHT)
        && outputFormat.containsKey(KEY_CROP_LEFT) && outputFormat.containsKey(KEY_CROP_BOTTOM)
        && outputFormat.containsKey(KEY_CROP_TOP);
    currentWidth = hasCrop
        ? outputFormat.getInteger(KEY_CROP_RIGHT) - outputFormat.getInteger(KEY_CROP_LEFT) + 1
        : outputFormat.getInteger(android.media.MediaFormat.KEY_WIDTH);
    currentHeight = hasCrop
        ? outputFormat.getInteger(KEY_CROP_BOTTOM) - outputFormat.getInteger(KEY_CROP_TOP) + 1
        : outputFormat.getInteger(android.media.MediaFormat.KEY_HEIGHT);
    currentPixelWidthHeightRatio = pendingPixelWidthHeightRatio;
    if (Util.SDK_INT >= 21) {
      // On API level 21 and above the decoder applies the rotation when rendering to the surface.
      // Hence currentUnappliedRotation should always be 0. For 90 and 270 degree rotations, we need
      // to flip the width, height and pixel aspect ratio to reflect the rotation that was applied.
      if (pendingRotationDegrees == 90 || pendingRotationDegrees == 270) {
        int rotatedHeight = currentWidth;
        currentWidth = currentHeight;
        currentHeight = rotatedHeight;
        currentPixelWidthHeightRatio = 1 / currentPixelWidthHeightRatio;
      }
    } else {
      // On API level 20 and below the decoder does not apply the rotation.
      currentUnappliedRotationDegrees = pendingRotationDegrees;
    }
    // Must be applied each time the output format changes.
    codec.setVideoScalingMode(videoScalingMode);
  }

  @Override
  protected boolean canReconfigureCodec(MediaCodec codec, boolean codecIsAdaptive,
      MediaFormat oldFormat, MediaFormat newFormat) {
    return newFormat.mimeType.equals(oldFormat.mimeType)
        && (codecIsAdaptive
            || (oldFormat.width == newFormat.width && oldFormat.height == newFormat.height));
  }

  @Override
  protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs, MediaCodec codec,
      ByteBuffer buffer, MediaCodec.BufferInfo bufferInfo, int bufferIndex, boolean shouldSkip) {
    if (shouldSkip) {
      skipOutputBuffer(codec, bufferIndex);
      consecutiveDroppedFrameCount = 0;
      return true;
    }

    if (!renderedFirstFrame) {
      if (Util.SDK_INT >= 21) {
        renderOutputBufferV21(codec, bufferIndex, System.nanoTime());
      } else {
        renderOutputBuffer(codec, bufferIndex);
      }
      consecutiveDroppedFrameCount = 0;
      return true;
    }

    if (getState() != TrackRenderer.STATE_STARTED) {
      return false;
    }

    // Compute how many microseconds it is until the buffer's presentation time.
    long elapsedSinceStartOfLoopUs = (SystemClock.elapsedRealtime() * 1000) - elapsedRealtimeUs;
    long earlyUs = bufferInfo.presentationTimeUs - positionUs - elapsedSinceStartOfLoopUs;

    // Compute the buffer's desired release time in nanoseconds.
    long systemTimeNs = System.nanoTime();
    long unadjustedFrameReleaseTimeNs = systemTimeNs + (earlyUs * 1000);

    // Apply a timestamp adjustment, if there is one.
    long adjustedReleaseTimeNs = frameReleaseTimeHelper.adjustReleaseTime(
        bufferInfo.presentationTimeUs, unadjustedFrameReleaseTimeNs);
    earlyUs = (adjustedReleaseTimeNs - systemTimeNs) / 1000;

    if (earlyUs < -30000) {
      // We're more than 30ms late rendering the frame.
      dropOutputBuffer(codec, bufferIndex);
      return true;
    }

    if (Util.SDK_INT >= 21) {
      // Let the underlying framework time the release.
      if (earlyUs < 50000) {
        renderOutputBufferV21(codec, bufferIndex, adjustedReleaseTimeNs);
        consecutiveDroppedFrameCount = 0;
        return true;
      }
    } else {
      // We need to time the release ourselves.
      if (earlyUs < 30000) {
        if (earlyUs > 11000) {
          // We're a little too early to render the frame. Sleep until the frame can be rendered.
          // Note: The 11ms threshold was chosen fairly arbitrarily.
          try {
            // Subtracting 10000 rather than 11000 ensures the sleep time will be at least 1ms.
            Thread.sleep((earlyUs - 10000) / 1000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
        renderOutputBuffer(codec, bufferIndex);
        consecutiveDroppedFrameCount = 0;
        return true;
      }
    }

    // We're either not playing, or it's not time to render the frame yet.
    return false;
  }

  protected void skipOutputBuffer(MediaCodec codec, int bufferIndex) {
    TraceUtil.beginSection("skipVideoBuffer");
    codec.releaseOutputBuffer(bufferIndex, false);
    TraceUtil.endSection();
    codecCounters.skippedOutputBufferCount++;
  }

  protected void dropOutputBuffer(MediaCodec codec, int bufferIndex) {
    TraceUtil.beginSection("dropVideoBuffer");
    codec.releaseOutputBuffer(bufferIndex, false);
    TraceUtil.endSection();
    codecCounters.droppedOutputBufferCount++;
    droppedFrameCount++;
    consecutiveDroppedFrameCount++;
    codecCounters.maxConsecutiveDroppedOutputBufferCount = Math.max(consecutiveDroppedFrameCount,
        codecCounters.maxConsecutiveDroppedOutputBufferCount);
    if (droppedFrameCount == maxDroppedFrameCountToNotify) {
      maybeNotifyDroppedFrameCount();
    }
  }

  protected void renderOutputBuffer(MediaCodec codec, int bufferIndex) {
    maybeNotifyVideoSizeChanged();
    TraceUtil.beginSection("releaseOutputBuffer");
    codec.releaseOutputBuffer(bufferIndex, true);
    TraceUtil.endSection();
    codecCounters.renderedOutputBufferCount++;
    renderedFirstFrame = true;
    maybeNotifyDrawnToSurface();
  }

  @TargetApi(21)
  protected void renderOutputBufferV21(MediaCodec codec, int bufferIndex, long releaseTimeNs) {
    maybeNotifyVideoSizeChanged();
    TraceUtil.beginSection("releaseOutputBuffer");
    codec.releaseOutputBuffer(bufferIndex, releaseTimeNs);
    TraceUtil.endSection();
    codecCounters.renderedOutputBufferCount++;
    renderedFirstFrame = true;
    maybeNotifyDrawnToSurface();
  }

  @SuppressLint("InlinedApi")
  private void maybeSetMaxInputSize(android.media.MediaFormat format, boolean codecIsAdaptive) {
    if (format.containsKey(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)) {
      // Already set. The source of the format may know better, so do nothing.
      return;
    }
    int maxHeight = format.getInteger(android.media.MediaFormat.KEY_HEIGHT);
    if (codecIsAdaptive && format.containsKey(android.media.MediaFormat.KEY_MAX_HEIGHT)) {
      maxHeight = Math.max(maxHeight, format.getInteger(android.media.MediaFormat.KEY_MAX_HEIGHT));
    }
    int maxWidth = format.getInteger(android.media.MediaFormat.KEY_WIDTH);
    if (codecIsAdaptive && format.containsKey(android.media.MediaFormat.KEY_MAX_WIDTH)) {
      maxWidth = Math.max(maxHeight, format.getInteger(android.media.MediaFormat.KEY_MAX_WIDTH));
    }
    int maxPixels;
    int minCompressionRatio;
    switch (format.getString(android.media.MediaFormat.KEY_MIME)) {
      case MimeTypes.VIDEO_H263:
      case MimeTypes.VIDEO_MP4V:
        maxPixels = maxWidth * maxHeight;
        minCompressionRatio = 2;
        break;
      case MimeTypes.VIDEO_H264:
        if ("BRAVIA 4K 2015".equals(Util.MODEL)) {
          // The Sony BRAVIA 4k TV has input buffers that are too small for the calculated 4k video
          // maximum input size, so use the default value.
          return;
        }
        // Round up width/height to an integer number of macroblocks.
        maxPixels = ((maxWidth + 15) / 16) * ((maxHeight + 15) / 16) * 16 * 16;
        minCompressionRatio = 2;
        break;
      case MimeTypes.VIDEO_VP8:
        // VPX does not specify a ratio so use the values from the platform's SoftVPX.cpp.
        maxPixels = maxWidth * maxHeight;
        minCompressionRatio = 2;
        break;
      case MimeTypes.VIDEO_H265:
      case MimeTypes.VIDEO_VP9:
        maxPixels = maxWidth * maxHeight;
        minCompressionRatio = 4;
        break;
      default:
        // Leave the default max input size.
        return;
    }
    // Estimate the maximum input size assuming three channel 4:2:0 subsampled input frames.
    int maxInputSize = (maxPixels * 3) / (2 * minCompressionRatio);
    format.setInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize);
  }

  private void maybeNotifyVideoSizeChanged() {
    if (eventHandler == null || eventListener == null
        || (lastReportedWidth == currentWidth && lastReportedHeight == currentHeight
        && lastReportedUnappliedRotationDegrees == currentUnappliedRotationDegrees
        && lastReportedPixelWidthHeightRatio == currentPixelWidthHeightRatio)) {
      return;
    }
    // Make final copies to ensure the runnable reports the correct values.
    final int currentWidth = this.currentWidth;
    final int currentHeight = this.currentHeight;
    final int currentUnappliedRotationDegrees = this.currentUnappliedRotationDegrees;
    final float currentPixelWidthHeightRatio = this.currentPixelWidthHeightRatio;
    eventHandler.post(new Runnable()  {
      @Override
      public void run() {
        eventListener.onVideoSizeChanged(currentWidth, currentHeight,
            currentUnappliedRotationDegrees, currentPixelWidthHeightRatio);
      }
    });
    // Update the last reported values.
    lastReportedWidth = currentWidth;
    lastReportedHeight = currentHeight;
    lastReportedUnappliedRotationDegrees = currentUnappliedRotationDegrees;
    lastReportedPixelWidthHeightRatio = currentPixelWidthHeightRatio;
  }

  private void maybeNotifyDrawnToSurface() {
    if (eventHandler == null || eventListener == null || reportedDrawnToSurface) {
      return;
    }
    // Make a final copy to ensure the runnable reports the correct surface.
    final Surface surface = this.surface;
    eventHandler.post(new Runnable()  {
      @Override
      public void run() {
        eventListener.onDrawnToSurface(surface);
      }
    });
    // Record that we have reported that the surface has been drawn to.
    reportedDrawnToSurface = true;
  }

  private void maybeNotifyDroppedFrameCount() {
    if (eventHandler == null || eventListener == null || droppedFrameCount == 0) {
      return;
    }
    long now = SystemClock.elapsedRealtime();
    // Make final copies to ensure the runnable reports the correct values.
    final int countToNotify = droppedFrameCount;
    final long elapsedToNotify = now - droppedFrameAccumulationStartTimeMs;
    eventHandler.post(new Runnable()  {
      @Override
      public void run() {
        eventListener.onDroppedFrames(countToNotify, elapsedToNotify);
      }
    });
    // Reset the dropped frame tracking.
    droppedFrameCount = 0;
    droppedFrameAccumulationStartTimeMs = now;
  }

}
