/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.telegram.messenger.exoplayer2.video;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Point;
import android.media.MediaCodec;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.ExoPlaybackException;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.decoder.DecoderInputBuffer;
import org.telegram.messenger.exoplayer2.drm.DrmInitData;
import org.telegram.messenger.exoplayer2.drm.DrmSessionManager;
import org.telegram.messenger.exoplayer2.drm.FrameworkMediaCrypto;
import org.telegram.messenger.exoplayer2.mediacodec.MediaCodecInfo;
import org.telegram.messenger.exoplayer2.mediacodec.MediaCodecRenderer;
import org.telegram.messenger.exoplayer2.mediacodec.MediaCodecSelector;
import org.telegram.messenger.exoplayer2.mediacodec.MediaCodecUtil;
import org.telegram.messenger.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import org.telegram.messenger.exoplayer2.util.TraceUtil;
import org.telegram.messenger.exoplayer2.util.Util;
import org.telegram.messenger.exoplayer2.video.VideoRendererEventListener.EventDispatcher;
import java.nio.ByteBuffer;

/**
 * Decodes and renders video using {@link MediaCodec}.
 */
@TargetApi(16)
public class MediaCodecVideoRenderer extends MediaCodecRenderer {

  private static final String TAG = "MediaCodecVideoRenderer";
  private static final String KEY_CROP_LEFT = "crop-left";
  private static final String KEY_CROP_RIGHT = "crop-right";
  private static final String KEY_CROP_BOTTOM = "crop-bottom";
  private static final String KEY_CROP_TOP = "crop-top";

  // Long edge length in pixels for standard video formats, in decreasing in order.
  private static final int[] STANDARD_LONG_EDGE_VIDEO_PX = new int[] {
      1920, 1600, 1440, 1280, 960, 854, 640, 540, 480};

  private final VideoFrameReleaseTimeHelper frameReleaseTimeHelper;
  private final EventDispatcher eventDispatcher;
  private final long allowedJoiningTimeMs;
  private final int maxDroppedFramesToNotify;
  private final boolean deviceNeedsAutoFrcWorkaround;

  private Format[] streamFormats;
  private CodecMaxValues codecMaxValues;

  private Surface surface;
  @C.VideoScalingMode
  private int scalingMode;
  private boolean renderedFirstFrame;
  private long joiningDeadlineMs;
  private long droppedFrameAccumulationStartTimeMs;
  private int droppedFrames;
  private int consecutiveDroppedFrameCount;

  private int pendingRotationDegrees;
  private float pendingPixelWidthHeightRatio;
  private int currentWidth;
  private int currentHeight;
  private int currentUnappliedRotationDegrees;
  private float currentPixelWidthHeightRatio;
  private int reportedWidth;
  private int reportedHeight;
  private int reportedUnappliedRotationDegrees;
  private float reportedPixelWidthHeightRatio;

  private boolean tunneling;
  private int tunnelingAudioSessionId;
  /* package */ OnFrameRenderedListenerV23 tunnelingOnFrameRenderedListener;

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   */
  public MediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector) {
    this(context, mediaCodecSelector, 0);
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   */
  public MediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector,
      long allowedJoiningTimeMs) {
    this(context, mediaCodecSelector, allowedJoiningTimeMs, null, null, -1);
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFrameCountToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link VideoRendererEventListener#onDroppedFrames(int, long)}.
   */
  public MediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector,
      long allowedJoiningTimeMs, Handler eventHandler, VideoRendererEventListener eventListener,
      int maxDroppedFrameCountToNotify) {
    this(context, mediaCodecSelector, allowedJoiningTimeMs, null, false, eventHandler,
        eventListener, maxDroppedFrameCountToNotify);
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param drmSessionManager For use with encrypted content. May be null if support for encrypted
   *     content is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisition. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFramesToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link VideoRendererEventListener#onDroppedFrames(int, long)}.
   */
  public MediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector,
      long allowedJoiningTimeMs, DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys, Handler eventHandler,
      VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
    super(C.TRACK_TYPE_VIDEO, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys);
    this.allowedJoiningTimeMs = allowedJoiningTimeMs;
    this.maxDroppedFramesToNotify = maxDroppedFramesToNotify;
    frameReleaseTimeHelper = new VideoFrameReleaseTimeHelper(context);
    eventDispatcher = new EventDispatcher(eventHandler, eventListener);
    deviceNeedsAutoFrcWorkaround = deviceNeedsAutoFrcWorkaround();
    joiningDeadlineMs = C.TIME_UNSET;
    currentWidth = Format.NO_VALUE;
    currentHeight = Format.NO_VALUE;
    currentPixelWidthHeightRatio = Format.NO_VALUE;
    pendingPixelWidthHeightRatio = Format.NO_VALUE;
    scalingMode = C.VIDEO_SCALING_MODE_DEFAULT;
    clearReportedVideoSize();
  }

  @Override
  protected int supportsFormat(MediaCodecSelector mediaCodecSelector, Format format)
      throws DecoderQueryException {
    String mimeType = format.sampleMimeType;
    if (!MimeTypes.isVideo(mimeType)) {
      return FORMAT_UNSUPPORTED_TYPE;
    }
    boolean requiresSecureDecryption = false;
    DrmInitData drmInitData = format.drmInitData;
    if (drmInitData != null) {
      for (int i = 0; i < drmInitData.schemeDataCount; i++) {
        requiresSecureDecryption |= drmInitData.get(i).requiresSecureDecryption;
      }
    }
    MediaCodecInfo decoderInfo = mediaCodecSelector.getDecoderInfo(mimeType,
        requiresSecureDecryption);
    if (decoderInfo == null) {
      return FORMAT_UNSUPPORTED_SUBTYPE;
    }

    boolean decoderCapable = decoderInfo.isCodecSupported(format.codecs);
    if (decoderCapable && format.width > 0 && format.height > 0) {
      if (Util.SDK_INT >= 21) {
        decoderCapable = decoderInfo.isVideoSizeAndRateSupportedV21(format.width, format.height,
            format.frameRate);
      } else {
        decoderCapable = format.width * format.height <= MediaCodecUtil.maxH264DecodableFrameSize();
        if (!decoderCapable) {
          Log.d(TAG, "FalseCheck [legacyFrameSize, " + format.width + "x" + format.height + "] ["
              + Util.DEVICE_DEBUG_INFO + "]");
        }
      }
    }

    int adaptiveSupport = decoderInfo.adaptive ? ADAPTIVE_SEAMLESS : ADAPTIVE_NOT_SEAMLESS;
    int tunnelingSupport = decoderInfo.tunneling ? TUNNELING_SUPPORTED : TUNNELING_NOT_SUPPORTED;
    int formatSupport = decoderCapable ? FORMAT_HANDLED : FORMAT_EXCEEDS_CAPABILITIES;
    return adaptiveSupport | tunnelingSupport | formatSupport;
  }

  @Override
  protected void onEnabled(boolean joining) throws ExoPlaybackException {
    super.onEnabled(joining);
    tunnelingAudioSessionId = getConfiguration().tunnelingAudioSessionId;
    tunneling = tunnelingAudioSessionId != C.AUDIO_SESSION_ID_UNSET;
    eventDispatcher.enabled(decoderCounters);
    frameReleaseTimeHelper.enable();
  }

  @Override
  protected void onStreamChanged(Format[] formats) throws ExoPlaybackException {
    streamFormats = formats;
    super.onStreamChanged(formats);
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    super.onPositionReset(positionUs, joining);
    clearRenderedFirstFrame();
    consecutiveDroppedFrameCount = 0;
    if (joining) {
      setJoiningDeadlineMs();
    } else {
      joiningDeadlineMs = C.TIME_UNSET;
    }
  }

  @Override
  public boolean isReady() {
    if ((renderedFirstFrame || super.shouldInitCodec()) && super.isReady()) {
      // Ready. If we were joining then we've now joined, so clear the joining deadline.
      joiningDeadlineMs = C.TIME_UNSET;
      return true;
    } else if (joiningDeadlineMs == C.TIME_UNSET) {
      // Not joining.
      return false;
    } else if (SystemClock.elapsedRealtime() < joiningDeadlineMs) {
      // Joining and still within the joining deadline.
      return true;
    } else {
      // The joining deadline has been exceeded. Give up and clear the deadline.
      joiningDeadlineMs = C.TIME_UNSET;
      return false;
    }
  }

  @Override
  protected void onStarted() {
    super.onStarted();
    droppedFrames = 0;
    droppedFrameAccumulationStartTimeMs = SystemClock.elapsedRealtime();
    joiningDeadlineMs = C.TIME_UNSET;
  }

  @Override
  protected void onStopped() {
    maybeNotifyDroppedFrames();
    super.onStopped();
  }

  @Override
  protected void onDisabled() {
    currentWidth = Format.NO_VALUE;
    currentHeight = Format.NO_VALUE;
    currentPixelWidthHeightRatio = Format.NO_VALUE;
    pendingPixelWidthHeightRatio = Format.NO_VALUE;
    clearReportedVideoSize();
    clearRenderedFirstFrame();
    frameReleaseTimeHelper.disable();
    tunnelingOnFrameRenderedListener = null;
    try {
      super.onDisabled();
    } finally {
      decoderCounters.ensureUpdated();
      eventDispatcher.disabled(decoderCounters);
    }
  }

  @Override
  public void handleMessage(int messageType, Object message) throws ExoPlaybackException {
    if (messageType == C.MSG_SET_SURFACE) {
      setSurface((Surface) message);
    } else if (messageType == C.MSG_SET_SCALING_MODE) {
      scalingMode = (Integer) message;
      MediaCodec codec = getCodec();
      if (codec != null) {
        setVideoScalingMode(codec, scalingMode);
      }
    } else {
      super.handleMessage(messageType, message);
    }
  }

  private void setSurface(Surface surface) throws ExoPlaybackException {
    // We only need to update the codec if the surface has changed.
    if (this.surface != surface) {
      this.surface = surface;
      int state = getState();
      if (state == STATE_ENABLED || state == STATE_STARTED) {
        MediaCodec codec = getCodec();
        if (Util.SDK_INT >= 23 && codec != null && surface != null) {
          setOutputSurfaceV23(codec, surface);
        } else {
          releaseCodec();
          maybeInitCodec();
        }
      }
      if (surface != null) {
        // If we know the video size, report it again immediately.
        maybeRenotifyVideoSizeChanged();
        // We haven't rendered to the new surface yet.
        clearRenderedFirstFrame();
        if (state == STATE_STARTED) {
          setJoiningDeadlineMs();
        }
      } else {
        // The surface has been removed.
        clearReportedVideoSize();
        clearRenderedFirstFrame();
      }
    } else if (surface != null) {
      // The surface is unchanged and non-null. If we know the video size and/or have already
      // rendered to the surface, report these again immediately.
      maybeRenotifyVideoSizeChanged();
      maybeRenotifyRenderedFirstFrame();
    }
  }

  @Override
  protected boolean shouldInitCodec() {
    return super.shouldInitCodec() && surface != null && surface.isValid();
  }

  @Override
  protected void configureCodec(MediaCodecInfo codecInfo, MediaCodec codec, Format format,
      MediaCrypto crypto) throws DecoderQueryException {
    codecMaxValues = getCodecMaxValues(codecInfo, format, streamFormats);
    MediaFormat mediaFormat = getMediaFormat(format, codecMaxValues, deviceNeedsAutoFrcWorkaround,
        tunnelingAudioSessionId);
    codec.configure(mediaFormat, surface, crypto, 0);
    if (Util.SDK_INT >= 23 && tunneling) {
      tunnelingOnFrameRenderedListener = new OnFrameRenderedListenerV23(codec);
    }
  }

  @Override
  protected void onCodecInitialized(String name, long initializedTimestampMs,
      long initializationDurationMs) {
    eventDispatcher.decoderInitialized(name, initializedTimestampMs, initializationDurationMs);
  }

  @Override
  protected void onInputFormatChanged(Format newFormat) throws ExoPlaybackException {
    super.onInputFormatChanged(newFormat);
    eventDispatcher.inputFormatChanged(newFormat);
    pendingPixelWidthHeightRatio = getPixelWidthHeightRatio(newFormat);
    pendingRotationDegrees = getRotationDegrees(newFormat);
  }

  @Override
  protected void onQueueInputBuffer(DecoderInputBuffer buffer) {
    if (Util.SDK_INT < 23 && tunneling) {
      maybeNotifyRenderedFirstFrame();
    }
  }

  @Override
  protected void onOutputFormatChanged(MediaCodec codec, android.media.MediaFormat outputFormat) {
    boolean hasCrop = outputFormat.containsKey(KEY_CROP_RIGHT)
        && outputFormat.containsKey(KEY_CROP_LEFT) && outputFormat.containsKey(KEY_CROP_BOTTOM)
        && outputFormat.containsKey(KEY_CROP_TOP);
    currentWidth = hasCrop
        ? outputFormat.getInteger(KEY_CROP_RIGHT) - outputFormat.getInteger(KEY_CROP_LEFT) + 1
        : outputFormat.getInteger(MediaFormat.KEY_WIDTH);
    currentHeight = hasCrop
        ? outputFormat.getInteger(KEY_CROP_BOTTOM) - outputFormat.getInteger(KEY_CROP_TOP) + 1
        : outputFormat.getInteger(MediaFormat.KEY_HEIGHT);
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
    setVideoScalingMode(codec, scalingMode);
  }

  @Override
  protected boolean canReconfigureCodec(MediaCodec codec, boolean codecIsAdaptive,
      Format oldFormat, Format newFormat) {
    return areAdaptationCompatible(oldFormat, newFormat)
        && newFormat.width <= codecMaxValues.width && newFormat.height <= codecMaxValues.height
        && newFormat.maxInputSize <= codecMaxValues.inputSize
        && (codecIsAdaptive
        || (oldFormat.width == newFormat.width && oldFormat.height == newFormat.height));
  }

  @Override
  protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs, MediaCodec codec,
      ByteBuffer buffer, int bufferIndex, int bufferFlags, long bufferPresentationTimeUs,
      boolean shouldSkip) {
    if (shouldSkip) {
      skipOutputBuffer(codec, bufferIndex);
      return true;
    }

    if (!renderedFirstFrame) {
      if (Util.SDK_INT >= 21) {
        renderOutputBufferV21(codec, bufferIndex, System.nanoTime());
      } else {
        renderOutputBuffer(codec, bufferIndex);
      }
      return true;
    }

    if (getState() != STATE_STARTED) {
      return false;
    }

    // Compute how many microseconds it is until the buffer's presentation time.
    long elapsedSinceStartOfLoopUs = (SystemClock.elapsedRealtime() * 1000) - elapsedRealtimeUs;
    long earlyUs = bufferPresentationTimeUs - positionUs - elapsedSinceStartOfLoopUs;

    // Compute the buffer's desired release time in nanoseconds.
    long systemTimeNs = System.nanoTime();
    long unadjustedFrameReleaseTimeNs = systemTimeNs + (earlyUs * 1000);

    // Apply a timestamp adjustment, if there is one.
    long adjustedReleaseTimeNs = frameReleaseTimeHelper.adjustReleaseTime(
        bufferPresentationTimeUs, unadjustedFrameReleaseTimeNs);
    earlyUs = (adjustedReleaseTimeNs - systemTimeNs) / 1000;

    if (shouldDropOutputBuffer(earlyUs, elapsedRealtimeUs)) {
      // We're more than 30ms late rendering the frame.
      dropOutputBuffer(codec, bufferIndex);
      return true;
    }

    if (Util.SDK_INT >= 21) {
      // Let the underlying framework time the release.
      if (earlyUs < 50000) {
        renderOutputBufferV21(codec, bufferIndex, adjustedReleaseTimeNs);
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
        return true;
      }
    }

    // We're either not playing, or it's not time to render the frame yet.
    return false;
  }

  /**
   * Returns whether the buffer being processed should be dropped.
   *
   * @param earlyUs The time until the buffer should be presented in microseconds. A negative value
   *     indicates that the buffer is late.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   */
  protected boolean shouldDropOutputBuffer(long earlyUs, long elapsedRealtimeUs) {
    // Drop the frame if we're more than 30ms late rendering the frame.
    return earlyUs < -30000;
  }

  private void skipOutputBuffer(MediaCodec codec, int bufferIndex) {
    TraceUtil.beginSection("skipVideoBuffer");
    codec.releaseOutputBuffer(bufferIndex, false);
    TraceUtil.endSection();
    decoderCounters.skippedOutputBufferCount++;
  }

  private void dropOutputBuffer(MediaCodec codec, int bufferIndex) {
    TraceUtil.beginSection("dropVideoBuffer");
    codec.releaseOutputBuffer(bufferIndex, false);
    TraceUtil.endSection();
    decoderCounters.droppedOutputBufferCount++;
    droppedFrames++;
    consecutiveDroppedFrameCount++;
    decoderCounters.maxConsecutiveDroppedOutputBufferCount = Math.max(consecutiveDroppedFrameCount,
        decoderCounters.maxConsecutiveDroppedOutputBufferCount);
    if (droppedFrames == maxDroppedFramesToNotify) {
      maybeNotifyDroppedFrames();
    }
  }

  private void renderOutputBuffer(MediaCodec codec, int bufferIndex) {
    maybeNotifyVideoSizeChanged();
    TraceUtil.beginSection("releaseOutputBuffer");
    codec.releaseOutputBuffer(bufferIndex, true);
    TraceUtil.endSection();
    decoderCounters.renderedOutputBufferCount++;
    consecutiveDroppedFrameCount = 0;
    maybeNotifyRenderedFirstFrame();
  }

  @TargetApi(21)
  private void renderOutputBufferV21(MediaCodec codec, int bufferIndex, long releaseTimeNs) {
    maybeNotifyVideoSizeChanged();
    TraceUtil.beginSection("releaseOutputBuffer");
    codec.releaseOutputBuffer(bufferIndex, releaseTimeNs);
    TraceUtil.endSection();
    decoderCounters.renderedOutputBufferCount++;
    consecutiveDroppedFrameCount = 0;
    maybeNotifyRenderedFirstFrame();
  }

  private void setJoiningDeadlineMs() {
    joiningDeadlineMs = allowedJoiningTimeMs > 0
        ? (SystemClock.elapsedRealtime() + allowedJoiningTimeMs) : C.TIME_UNSET;
  }

  private void clearRenderedFirstFrame() {
    renderedFirstFrame = false;
    // The first frame notification is triggered by renderOutputBuffer or renderOutputBufferV21 for
    // non-tunneled playback, onQueueInputBuffer for tunneled playback prior to API level 23, and
    // OnFrameRenderedListenerV23.onFrameRenderedListener for tunneled playback on API level 23 and
    // above.
    if (Util.SDK_INT >= 23 && tunneling) {
      MediaCodec codec = getCodec();
      // If codec is null then the listener will be instantiated in configureCodec.
      if (codec != null) {
        tunnelingOnFrameRenderedListener = new OnFrameRenderedListenerV23(codec);
      }
    }
  }

  /* package */ void maybeNotifyRenderedFirstFrame() {
    if (!renderedFirstFrame) {
      renderedFirstFrame = true;
      eventDispatcher.renderedFirstFrame(surface);
    }
  }

  private void maybeRenotifyRenderedFirstFrame() {
    if (renderedFirstFrame) {
      eventDispatcher.renderedFirstFrame(surface);
    }
  }

  private void clearReportedVideoSize() {
    reportedWidth = Format.NO_VALUE;
    reportedHeight = Format.NO_VALUE;
    reportedPixelWidthHeightRatio = Format.NO_VALUE;
    reportedUnappliedRotationDegrees = Format.NO_VALUE;
  }

  private void maybeNotifyVideoSizeChanged() {
    if (reportedWidth != currentWidth || reportedHeight != currentHeight
        || reportedUnappliedRotationDegrees != currentUnappliedRotationDegrees
        || reportedPixelWidthHeightRatio != currentPixelWidthHeightRatio) {
      eventDispatcher.videoSizeChanged(currentWidth, currentHeight, currentUnappliedRotationDegrees,
          currentPixelWidthHeightRatio);
      reportedWidth = currentWidth;
      reportedHeight = currentHeight;
      reportedUnappliedRotationDegrees = currentUnappliedRotationDegrees;
      reportedPixelWidthHeightRatio = currentPixelWidthHeightRatio;
    }
  }

  private void maybeRenotifyVideoSizeChanged() {
    if (reportedWidth != Format.NO_VALUE || reportedHeight != Format.NO_VALUE) {
      eventDispatcher.videoSizeChanged(currentWidth, currentHeight, currentUnappliedRotationDegrees,
          currentPixelWidthHeightRatio);
    }
  }

  private void maybeNotifyDroppedFrames() {
    if (droppedFrames > 0) {
      long now = SystemClock.elapsedRealtime();
      long elapsedMs = now - droppedFrameAccumulationStartTimeMs;
      eventDispatcher.droppedFrames(droppedFrames, elapsedMs);
      droppedFrames = 0;
      droppedFrameAccumulationStartTimeMs = now;
    }
  }

  @SuppressLint("InlinedApi")
  private static MediaFormat getMediaFormat(Format format, CodecMaxValues codecMaxValues,
      boolean deviceNeedsAutoFrcWorkaround, int tunnelingAudioSessionId) {
    MediaFormat frameworkMediaFormat = format.getFrameworkMediaFormatV16();
    // Set the maximum adaptive video dimensions.
    frameworkMediaFormat.setInteger(MediaFormat.KEY_MAX_WIDTH, codecMaxValues.width);
    frameworkMediaFormat.setInteger(MediaFormat.KEY_MAX_HEIGHT, codecMaxValues.height);
    // Set the maximum input size.
    if (codecMaxValues.inputSize != Format.NO_VALUE) {
      frameworkMediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, codecMaxValues.inputSize);
    }
    // Set FRC workaround.
    if (deviceNeedsAutoFrcWorkaround) {
      frameworkMediaFormat.setInteger("auto-frc", 0);
    }
    // Configure tunneling if enabled.
    if (tunnelingAudioSessionId != C.AUDIO_SESSION_ID_UNSET) {
      configureTunnelingV21(frameworkMediaFormat, tunnelingAudioSessionId);
    }
    return frameworkMediaFormat;
  }

  @TargetApi(23)
  private static void setOutputSurfaceV23(MediaCodec codec, Surface surface) {
    codec.setOutputSurface(surface);
  }

  @TargetApi(21)
  private static void configureTunnelingV21(MediaFormat mediaFormat, int tunnelingAudioSessionId) {
    mediaFormat.setFeatureEnabled(CodecCapabilities.FEATURE_TunneledPlayback, true);
    mediaFormat.setInteger(MediaFormat.KEY_AUDIO_SESSION_ID, tunnelingAudioSessionId);
  }

  /**
   * Returns {@link CodecMaxValues} suitable for configuring a codec for {@code format} in a way
   * that will allow possible adaptation to other compatible formats in {@code streamFormats}.
   *
   * @param codecInfo Information about the {@link MediaCodec} being configured.
   * @param format The format for which the codec is being configured.
   * @param streamFormats The possible stream formats.
   * @return Suitable {@link CodecMaxValues}.
   * @throws DecoderQueryException If an error occurs querying {@code codecInfo}.
   */
  private static CodecMaxValues getCodecMaxValues(MediaCodecInfo codecInfo, Format format,
      Format[] streamFormats) throws DecoderQueryException {
    int maxWidth = format.width;
    int maxHeight = format.height;
    int maxInputSize = getMaxInputSize(format);
    if (streamFormats.length == 1) {
      // The single entry in streamFormats must correspond to the format for which the codec is
      // being configured.
      return new CodecMaxValues(maxWidth, maxHeight, maxInputSize);
    }
    boolean haveUnknownDimensions = false;
    for (Format streamFormat : streamFormats) {
      if (areAdaptationCompatible(format, streamFormat)) {
        haveUnknownDimensions |= (streamFormat.width == Format.NO_VALUE
            || streamFormat.height == Format.NO_VALUE);
        maxWidth = Math.max(maxWidth, streamFormat.width);
        maxHeight = Math.max(maxHeight, streamFormat.height);
        maxInputSize = Math.max(maxInputSize, getMaxInputSize(streamFormat));
      }
    }
    if (haveUnknownDimensions) {
      Log.w(TAG, "Resolutions unknown. Codec max resolution: " + maxWidth + "x" + maxHeight);
      Point codecMaxSize = getCodecMaxSize(codecInfo, format);
      if (codecMaxSize != null) {
        maxWidth = Math.max(maxWidth, codecMaxSize.x);
        maxHeight = Math.max(maxHeight, codecMaxSize.y);
        maxInputSize = Math.max(maxInputSize,
            getMaxInputSize(format.sampleMimeType, maxWidth, maxHeight));
        Log.w(TAG, "Codec max resolution adjusted to: " + maxWidth + "x" + maxHeight);
      }
    }
    return new CodecMaxValues(maxWidth, maxHeight, maxInputSize);
  }

  /**
   * Returns a maximum video size to use when configuring a codec for {@code format} in a way
   * that will allow possible adaptation to other compatible formats that are expected to have the
   * same aspect ratio, but whose sizes are unknown.
   *
   * @param codecInfo Information about the {@link MediaCodec} being configured.
   * @param format The format for which the codec is being configured.
   * @return The maximum video size to use, or null if the size of {@code format} should be used.
   * @throws DecoderQueryException If an error occurs querying {@code codecInfo}.
   */
  private static Point getCodecMaxSize(MediaCodecInfo codecInfo, Format format)
      throws DecoderQueryException {
    boolean isVerticalVideo = format.height > format.width;
    int formatLongEdgePx = isVerticalVideo ? format.height : format.width;
    int formatShortEdgePx = isVerticalVideo ? format.width : format.height;
    float aspectRatio = (float) formatShortEdgePx / formatLongEdgePx;
    for (int longEdgePx : STANDARD_LONG_EDGE_VIDEO_PX) {
      int shortEdgePx = (int) (longEdgePx * aspectRatio);
      if (longEdgePx <= formatLongEdgePx || shortEdgePx <= formatShortEdgePx) {
        // Don't return a size not larger than the format for which the codec is being configured.
        return null;
      } else if (Util.SDK_INT >= 21) {
        Point alignedSize = codecInfo.alignVideoSizeV21(isVerticalVideo ? shortEdgePx : longEdgePx,
            isVerticalVideo ? longEdgePx : shortEdgePx);
        float frameRate = format.frameRate;
        if (codecInfo.isVideoSizeAndRateSupportedV21(alignedSize.x, alignedSize.y, frameRate)) {
          return alignedSize;
        }
      } else {
        // Conservatively assume the codec requires 16px width and height alignment.
        longEdgePx = Util.ceilDivide(longEdgePx, 16) * 16;
        shortEdgePx = Util.ceilDivide(shortEdgePx, 16) * 16;
        if (longEdgePx * shortEdgePx <= MediaCodecUtil.maxH264DecodableFrameSize()) {
          return new Point(isVerticalVideo ? shortEdgePx : longEdgePx,
              isVerticalVideo ? longEdgePx : shortEdgePx);
        }
      }
    }
    return null;
  }

  /**
   * Returns a maximum input size for a given format.
   *
   * @param format The format.
   * @return A maximum input size in bytes, or {@link Format#NO_VALUE} if a maximum could not be
   *     determined.
   */
  private static int getMaxInputSize(Format format) {
    if (format.maxInputSize != Format.NO_VALUE) {
      // The format defines an explicit maximum input size.
      return format.maxInputSize;
    }
    return getMaxInputSize(format.sampleMimeType, format.width, format.height);
  }

  /**
   * Returns a maximum input size for a given mime type, width and height.
   *
   * @param sampleMimeType The format mime type.
   * @param width The width in pixels.
   * @param height The height in pixels.
   * @return A maximum input size in bytes, or {@link Format#NO_VALUE} if a maximum could not be
   *     determined.
   */
  private static int getMaxInputSize(String sampleMimeType, int width, int height) {
    if (width == Format.NO_VALUE || height == Format.NO_VALUE) {
      // We can't infer a maximum input size without video dimensions.
      return Format.NO_VALUE;
    }

    // Attempt to infer a maximum input size from the format.
    int maxPixels;
    int minCompressionRatio;
    switch (sampleMimeType) {
      case MimeTypes.VIDEO_H263:
      case MimeTypes.VIDEO_MP4V:
        maxPixels = width * height;
        minCompressionRatio = 2;
        break;
      case MimeTypes.VIDEO_H264:
        if ("BRAVIA 4K 2015".equals(Util.MODEL)) {
          // The Sony BRAVIA 4k TV has input buffers that are too small for the calculated 4k video
          // maximum input size, so use the default value.
          return Format.NO_VALUE;
        }
        // Round up width/height to an integer number of macroblocks.
        maxPixels = Util.ceilDivide(width, 16) * Util.ceilDivide(height, 16) * 16 * 16;
        minCompressionRatio = 2;
        break;
      case MimeTypes.VIDEO_VP8:
        // VPX does not specify a ratio so use the values from the platform's SoftVPX.cpp.
        maxPixels = width * height;
        minCompressionRatio = 2;
        break;
      case MimeTypes.VIDEO_H265:
      case MimeTypes.VIDEO_VP9:
        maxPixels = width * height;
        minCompressionRatio = 4;
        break;
      default:
        // Leave the default max input size.
        return Format.NO_VALUE;
    }
    // Estimate the maximum input size assuming three channel 4:2:0 subsampled input frames.
    return (maxPixels * 3) / (2 * minCompressionRatio);
  }

  private static void setVideoScalingMode(MediaCodec codec, int scalingMode) {
    codec.setVideoScalingMode(scalingMode);
  }

  /**
   * Returns whether the device is known to enable frame-rate conversion logic that negatively
   * impacts ExoPlayer.
   * <p>
   * If true is returned then we explicitly disable the feature.
   *
   * @return True if the device is known to enable frame-rate conversion logic that negatively
   *     impacts ExoPlayer. False otherwise.
   */
  private static boolean deviceNeedsAutoFrcWorkaround() {
    // nVidia Shield prior to M tries to adjust the playback rate to better map the frame-rate of
    // content to the refresh rate of the display. For example playback of 23.976fps content is
    // adjusted to play at 1.001x speed when the output display is 60Hz. Unfortunately the
    // implementation causes ExoPlayer's reported playback position to drift out of sync. Captions
    // also lose sync [Internal: b/26453592].
    return Util.SDK_INT <= 22 && "foster".equals(Util.DEVICE) && "NVIDIA".equals(Util.MANUFACTURER);
  }

  /**
   * Returns whether an adaptive codec with suitable {@link CodecMaxValues} will support adaptation
   * between two {@link Format}s.
   *
   * @param first The first format.
   * @param second The second format.
   * @return Whether an adaptive codec with suitable {@link CodecMaxValues} will support adaptation
   *     between two {@link Format}s.
   */
  private static boolean areAdaptationCompatible(Format first, Format second) {
    return first.sampleMimeType.equals(second.sampleMimeType)
        && getRotationDegrees(first) == getRotationDegrees(second);
  }

  private static float getPixelWidthHeightRatio(Format format) {
    return format.pixelWidthHeightRatio == Format.NO_VALUE ? 1 : format.pixelWidthHeightRatio;
  }

  private static int getRotationDegrees(Format format) {
    return format.rotationDegrees == Format.NO_VALUE ? 0 : format.rotationDegrees;
  }

  private static final class CodecMaxValues {

    public final int width;
    public final int height;
    public final int inputSize;

    public CodecMaxValues(int width, int height, int inputSize) {
      this.width = width;
      this.height = height;
      this.inputSize = inputSize;
    }

  }

  @TargetApi(23)
  private final class OnFrameRenderedListenerV23 implements MediaCodec.OnFrameRenderedListener {

    private OnFrameRenderedListenerV23(MediaCodec codec) {
      codec.setOnFrameRenderedListener(this, new Handler());
    }

    @Override
    public void onFrameRendered(@NonNull MediaCodec codec, long presentationTimeUs, long nanoTime) {
      if (this != tunnelingOnFrameRenderedListener) {
        // Stale event.
        return;
      }
      maybeNotifyRenderedFirstFrame();
    }

  }

}
