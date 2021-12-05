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
package com.google.android.exoplayer2.mediacodec;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodec.CodecException;
import android.media.MediaCodec.CryptoException;
import android.media.MediaCrypto;
import android.media.MediaCryptoException;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.annotation.CheckResult;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.DrmSession.DrmSessionException;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.TimedValueQueue;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * An abstract renderer that uses {@link MediaCodec} to decode samples for rendering.
 */
public abstract class MediaCodecRenderer extends BaseRenderer {

  /** Thrown when a failure occurs instantiating a decoder. */
  public static class DecoderInitializationException extends Exception {

    private static final int CUSTOM_ERROR_CODE_BASE = -50000;
    private static final int NO_SUITABLE_DECODER_ERROR = CUSTOM_ERROR_CODE_BASE + 1;
    private static final int DECODER_QUERY_ERROR = CUSTOM_ERROR_CODE_BASE + 2;

    /**
     * The mime type for which a decoder was being initialized.
     */
    public final String mimeType;

    /**
     * Whether it was required that the decoder support a secure output path.
     */
    public final boolean secureDecoderRequired;

    /**
     * The {@link MediaCodecInfo} of the decoder that failed to initialize. Null if no suitable
     * decoder was found.
     */
    @Nullable public final MediaCodecInfo codecInfo;

    /** An optional developer-readable diagnostic information string. May be null. */
    @Nullable public final String diagnosticInfo;

    /**
     * If the decoder failed to initialize and another decoder being used as a fallback also failed
     * to initialize, the {@link DecoderInitializationException} for the fallback decoder. Null if
     * there was no fallback decoder or no suitable decoders were found.
     */
    @Nullable public final DecoderInitializationException fallbackDecoderInitializationException;

    public DecoderInitializationException(Format format, Throwable cause,
        boolean secureDecoderRequired, int errorCode) {
      this(
          "Decoder init failed: [" + errorCode + "], " + format,
          cause,
          format.sampleMimeType,
          secureDecoderRequired,
          /* mediaCodecInfo= */ null,
          buildCustomDiagnosticInfo(errorCode),
          /* fallbackDecoderInitializationException= */ null);
    }

    public DecoderInitializationException(
        Format format,
        Throwable cause,
        boolean secureDecoderRequired,
        MediaCodecInfo mediaCodecInfo) {
      this(
          "Decoder init failed: " + mediaCodecInfo.name + ", " + format,
          cause,
          format.sampleMimeType,
          secureDecoderRequired,
          mediaCodecInfo,
          Util.SDK_INT >= 21 ? getDiagnosticInfoV21(cause) : null,
          /* fallbackDecoderInitializationException= */ null);
    }

    private DecoderInitializationException(
        String message,
        Throwable cause,
        String mimeType,
        boolean secureDecoderRequired,
        @Nullable MediaCodecInfo mediaCodecInfo,
        @Nullable String diagnosticInfo,
        @Nullable DecoderInitializationException fallbackDecoderInitializationException) {
      super(message, cause);
      this.mimeType = mimeType;
      this.secureDecoderRequired = secureDecoderRequired;
      this.codecInfo = mediaCodecInfo;
      this.diagnosticInfo = diagnosticInfo;
      this.fallbackDecoderInitializationException = fallbackDecoderInitializationException;
    }

    @CheckResult
    private DecoderInitializationException copyWithFallbackException(
        DecoderInitializationException fallbackException) {
      return new DecoderInitializationException(
          getMessage(),
          getCause(),
          mimeType,
          secureDecoderRequired,
          codecInfo,
          diagnosticInfo,
          fallbackException);
    }

    @TargetApi(21)
    private static String getDiagnosticInfoV21(Throwable cause) {
      if (cause instanceof CodecException) {
        return ((CodecException) cause).getDiagnosticInfo();
      }
      return null;
    }

    private static String buildCustomDiagnosticInfo(int errorCode) {
      String sign = errorCode < 0 ? "neg_" : "";
      return "com.google.android.exoplayer2.mediacodec.MediaCodecRenderer_"
          + sign
          + Math.abs(errorCode);
    }
  }

  /** Thrown when a failure occurs in the decoder. */
  public static class DecoderException extends Exception {

    /** The {@link MediaCodecInfo} of the decoder that failed. Null if unknown. */
    @Nullable public final MediaCodecInfo codecInfo;

    /** An optional developer-readable diagnostic information string. May be null. */
    @Nullable public final String diagnosticInfo;

    public DecoderException(Throwable cause, @Nullable MediaCodecInfo codecInfo) {
      super("Decoder failed: " + (codecInfo == null ? null : codecInfo.name), cause);
      this.codecInfo = codecInfo;
      diagnosticInfo = Util.SDK_INT >= 21 ? getDiagnosticInfoV21(cause) : null;
    }

    @TargetApi(21)
    private static String getDiagnosticInfoV21(Throwable cause) {
      if (cause instanceof CodecException) {
        return ((CodecException) cause).getDiagnosticInfo();
      }
      return null;
    }
  }

  /** Indicates no codec operating rate should be set. */
  protected static final float CODEC_OPERATING_RATE_UNSET = -1;

  private static final String TAG = "MediaCodecRenderer";

  /**
   * If the {@link MediaCodec} is hotswapped (i.e. replaced during playback), this is the period of
   * time during which {@link #isReady()} will report true regardless of whether the new codec has
   * output frames that are ready to be rendered.
   * <p>
   * This allows codec hotswapping to be performed seamlessly, without interrupting the playback of
   * other renderers, provided the new codec is able to decode some frames within this time period.
   */
  private static final long MAX_CODEC_HOTSWAP_TIME_MS = 1000;

  /**
   * The possible return values for {@link #canKeepCodec(MediaCodec, MediaCodecInfo, Format,
   * Format)}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    KEEP_CODEC_RESULT_NO,
    KEEP_CODEC_RESULT_YES_WITH_FLUSH,
    KEEP_CODEC_RESULT_YES_WITH_RECONFIGURATION,
    KEEP_CODEC_RESULT_YES_WITHOUT_RECONFIGURATION
  })
  protected @interface KeepCodecResult {}
  /** The codec cannot be kept. */
  protected static final int KEEP_CODEC_RESULT_NO = 0;
  /** The codec can be kept, but must be flushed. */
  protected static final int KEEP_CODEC_RESULT_YES_WITH_FLUSH = 1;
  /**
   * The codec can be kept. It does not need to be flushed, but must be reconfigured by prefixing
   * the next input buffer with the new format's configuration data.
   */
  protected static final int KEEP_CODEC_RESULT_YES_WITH_RECONFIGURATION = 2;
  /** The codec can be kept. It does not need to be flushed and no reconfiguration is required. */
  protected static final int KEEP_CODEC_RESULT_YES_WITHOUT_RECONFIGURATION = 3;

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    RECONFIGURATION_STATE_NONE,
    RECONFIGURATION_STATE_WRITE_PENDING,
    RECONFIGURATION_STATE_QUEUE_PENDING
  })
  private @interface ReconfigurationState {}
  /**
   * There is no pending adaptive reconfiguration work.
   */
  private static final int RECONFIGURATION_STATE_NONE = 0;
  /**
   * Codec configuration data needs to be written into the next buffer.
   */
  private static final int RECONFIGURATION_STATE_WRITE_PENDING = 1;
  /**
   * Codec configuration data has been written into the next buffer, but that buffer still needs to
   * be returned to the codec.
   */
  private static final int RECONFIGURATION_STATE_QUEUE_PENDING = 2;

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({DRAIN_STATE_NONE, DRAIN_STATE_SIGNAL_END_OF_STREAM, DRAIN_STATE_WAIT_END_OF_STREAM})
  private @interface DrainState {}
  /** The codec is not being drained. */
  private static final int DRAIN_STATE_NONE = 0;
  /** The codec needs to be drained, but we haven't signaled an end of stream to it yet. */
  private static final int DRAIN_STATE_SIGNAL_END_OF_STREAM = 1;
  /** The codec needs to be drained, and we're waiting for it to output an end of stream. */
  private static final int DRAIN_STATE_WAIT_END_OF_STREAM = 2;

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    DRAIN_ACTION_NONE,
    DRAIN_ACTION_FLUSH,
    DRAIN_ACTION_UPDATE_DRM_SESSION,
    DRAIN_ACTION_REINITIALIZE
  })
  private @interface DrainAction {}
  /** No special action should be taken. */
  private static final int DRAIN_ACTION_NONE = 0;
  /** The codec should be flushed. */
  private static final int DRAIN_ACTION_FLUSH = 1;
  /** The codec should be flushed and updated to use the pending DRM session. */
  private static final int DRAIN_ACTION_UPDATE_DRM_SESSION = 2;
  /** The codec should be reinitialized. */
  private static final int DRAIN_ACTION_REINITIALIZE = 3;

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    ADAPTATION_WORKAROUND_MODE_NEVER,
    ADAPTATION_WORKAROUND_MODE_SAME_RESOLUTION,
    ADAPTATION_WORKAROUND_MODE_ALWAYS
  })
  private @interface AdaptationWorkaroundMode {}
  /**
   * The adaptation workaround is never used.
   */
  private static final int ADAPTATION_WORKAROUND_MODE_NEVER = 0;
  /**
   * The adaptation workaround is used when adapting between formats of the same resolution only.
   */
  private static final int ADAPTATION_WORKAROUND_MODE_SAME_RESOLUTION = 1;
  /**
   * The adaptation workaround is always used when adapting between formats.
   */
  private static final int ADAPTATION_WORKAROUND_MODE_ALWAYS = 2;

  /**
   * H.264/AVC buffer to queue when using the adaptation workaround (see {@link
   * #codecAdaptationWorkaroundMode(String)}. Consists of three NAL units with start codes: Baseline
   * sequence/picture parameter sets and a 32 * 32 pixel IDR slice. This stream can be queued to
   * force a resolution change when adapting to a new format.
   */
  private static final byte[] ADAPTATION_WORKAROUND_BUFFER =
      new byte[] {
        0, 0, 1, 103, 66, -64, 11, -38, 37, -112, 0, 0, 1, 104, -50, 15, 19, 32, 0, 0, 1, 101, -120,
        -124, 13, -50, 113, 24, -96, 0, 47, -65, 28, 49, -61, 39, 93, 120
      };

  private static final int ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT = 32;

  private final MediaCodecSelector mediaCodecSelector;
  @Nullable private final DrmSessionManager<FrameworkMediaCrypto> drmSessionManager;
  private final boolean playClearSamplesWithoutKeys;
  private final boolean enableDecoderFallback;
  private final float assumedMinimumCodecOperatingRate;
  private final DecoderInputBuffer buffer;
  private final DecoderInputBuffer flagsOnlyBuffer;
  private final TimedValueQueue<Format> formatQueue;
  private final ArrayList<Long> decodeOnlyPresentationTimestamps;
  private final MediaCodec.BufferInfo outputBufferInfo;

  private boolean drmResourcesAcquired;
  @Nullable private Format inputFormat;
  private Format outputFormat;
  @Nullable private DrmSession<FrameworkMediaCrypto> codecDrmSession;
  @Nullable private DrmSession<FrameworkMediaCrypto> sourceDrmSession;
  @Nullable private MediaCrypto mediaCrypto;
  private boolean mediaCryptoRequiresSecureDecoder;
  private long renderTimeLimitMs;
  private float rendererOperatingRate;
  @Nullable private MediaCodec codec;
  @Nullable private Format codecFormat;
  private float codecOperatingRate;
  @Nullable private ArrayDeque<MediaCodecInfo> availableCodecInfos;
  @Nullable private DecoderInitializationException preferredDecoderInitializationException;
  @Nullable private MediaCodecInfo codecInfo;
  @AdaptationWorkaroundMode private int codecAdaptationWorkaroundMode;
  private boolean codecNeedsReconfigureWorkaround;
  private boolean codecNeedsDiscardToSpsWorkaround;
  private boolean codecNeedsFlushWorkaround;
  private boolean codecNeedsSosFlushWorkaround;
  private boolean codecNeedsEosFlushWorkaround;
  private boolean codecNeedsEosOutputExceptionWorkaround;
  private boolean codecNeedsMonoChannelCountWorkaround;
  private boolean codecNeedsAdaptationWorkaroundBuffer;
  private boolean shouldSkipAdaptationWorkaroundOutputBuffer;
  private boolean codecNeedsEosPropagation;
  private ByteBuffer[] inputBuffers;
  private ByteBuffer[] outputBuffers;
  private long codecHotswapDeadlineMs;
  private int inputIndex;
  private int outputIndex;
  private ByteBuffer outputBuffer;
  private boolean isDecodeOnlyOutputBuffer;
  private boolean isLastOutputBuffer;
  private boolean codecReconfigured;
  @ReconfigurationState private int codecReconfigurationState;
  @DrainState private int codecDrainState;
  @DrainAction private int codecDrainAction;
  private boolean codecReceivedBuffers;
  private boolean codecReceivedEos;
  private boolean codecHasOutputMediaFormat;
  private long largestQueuedPresentationTimeUs;
  private long lastBufferInStreamPresentationTimeUs;
  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  private boolean waitingForKeys;
  private boolean waitingForFirstSyncSample;
  private boolean waitingForFirstSampleInFormat;
  private boolean skipMediaCodecStopOnRelease;
  private boolean pendingOutputEndOfStream;

  protected DecoderCounters decoderCounters;

  /**
   * @param trackType The track type that the renderer handles. One of the {@code C.TRACK_TYPE_*}
   *     constants defined in {@link C}.
   * @param mediaCodecSelector A decoder selector.
   * @param drmSessionManager For use with encrypted media. May be null if support for encrypted
   *     media is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisition. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   * @param enableDecoderFallback Whether to enable fallback to lower-priority decoders if decoder
   *     initialization fails. This may result in using a decoder that is less efficient or slower
   *     than the primary decoder.
   * @param assumedMinimumCodecOperatingRate A codec operating rate that all codecs instantiated by
   *     this renderer are assumed to meet implicitly (i.e. without the operating rate being set
   *     explicitly using {@link MediaFormat#KEY_OPERATING_RATE}).
   */
  public MediaCodecRenderer(
      int trackType,
      MediaCodecSelector mediaCodecSelector,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys,
      boolean enableDecoderFallback,
      float assumedMinimumCodecOperatingRate) {
    super(trackType);
    this.mediaCodecSelector = Assertions.checkNotNull(mediaCodecSelector);
    this.drmSessionManager = drmSessionManager;
    this.playClearSamplesWithoutKeys = playClearSamplesWithoutKeys;
    this.enableDecoderFallback = enableDecoderFallback;
    this.assumedMinimumCodecOperatingRate = assumedMinimumCodecOperatingRate;
    buffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    flagsOnlyBuffer = DecoderInputBuffer.newFlagsOnlyInstance();
    formatQueue = new TimedValueQueue<>();
    decodeOnlyPresentationTimestamps = new ArrayList<>();
    outputBufferInfo = new MediaCodec.BufferInfo();
    codecReconfigurationState = RECONFIGURATION_STATE_NONE;
    codecDrainState = DRAIN_STATE_NONE;
    codecDrainAction = DRAIN_ACTION_NONE;
    codecOperatingRate = CODEC_OPERATING_RATE_UNSET;
    rendererOperatingRate = 1f;
    renderTimeLimitMs = C.TIME_UNSET;
  }

  /**
   * Set a limit on the time a single {@link #render(long, long)} call can spend draining and
   * filling the decoder.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release. It should
   * only be called before the renderer is used.
   *
   * @param renderTimeLimitMs The render time limit in milliseconds, or {@link C#TIME_UNSET} for no
   *     limit.
   */
  public void experimental_setRenderTimeLimitMs(long renderTimeLimitMs) {
    this.renderTimeLimitMs = renderTimeLimitMs;
  }

  /**
   * Skip calling {@link MediaCodec#stop()} when the underlying MediaCodec is going to be released.
   *
   * <p>By default, when the MediaCodecRenderer is releasing the underlying {@link MediaCodec}, it
   * first calls {@link MediaCodec#stop()} and then calls {@link MediaCodec#release()}. If this
   * feature is enabled, the MediaCodecRenderer will skip the call to {@link MediaCodec#stop()}.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release. It should
   * only be called before the renderer is used.
   *
   * @param enabled enable or disable the feature.
   */
  public void experimental_setSkipMediaCodecStopOnRelease(boolean enabled) {
    skipMediaCodecStopOnRelease = enabled;
  }

  @Override
  @AdaptiveSupport
  public final int supportsMixedMimeTypeAdaptation() {
    return ADAPTIVE_NOT_SEAMLESS;
  }

  @Override
  @Capabilities
  public final int supportsFormat(Format format) throws ExoPlaybackException {
    try {
      return supportsFormat(mediaCodecSelector, drmSessionManager, format);
    } catch (DecoderQueryException e) {
      throw createRendererException(e, format);
    }
  }

  /**
   * Returns the {@link Capabilities} for the given {@link Format}.
   *
   * @param mediaCodecSelector The decoder selector.
   * @param drmSessionManager The renderer's {@link DrmSessionManager}.
   * @param format The {@link Format}.
   * @return The {@link Capabilities} for this {@link Format}.
   * @throws DecoderQueryException If there was an error querying decoders.
   */
  @Capabilities
  protected abstract int supportsFormat(
      MediaCodecSelector mediaCodecSelector,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      Format format)
      throws DecoderQueryException;

  /**
   * Returns a list of decoders that can decode media in the specified format, in priority order.
   *
   * @param mediaCodecSelector The decoder selector.
   * @param format The {@link Format} for which a decoder is required.
   * @param requiresSecureDecoder Whether a secure decoder is required.
   * @return A list of {@link MediaCodecInfo}s corresponding to decoders. May be empty.
   * @throws DecoderQueryException Thrown if there was an error querying decoders.
   */
  protected abstract List<MediaCodecInfo> getDecoderInfos(
      MediaCodecSelector mediaCodecSelector, Format format, boolean requiresSecureDecoder)
      throws DecoderQueryException;

  /**
   * Configures a newly created {@link MediaCodec}.
   *
   * @param codecInfo Information about the {@link MediaCodec} being configured.
   * @param codec The {@link MediaCodec} to configure.
   * @param format The {@link Format} for which the codec is being configured.
   * @param crypto For drm protected playbacks, a {@link MediaCrypto} to use for decryption.
   * @param codecOperatingRate The codec operating rate, or {@link #CODEC_OPERATING_RATE_UNSET} if
   *     no codec operating rate should be set.
   */
  protected abstract void configureCodec(
      MediaCodecInfo codecInfo,
      MediaCodec codec,
      Format format,
      @Nullable MediaCrypto crypto,
      float codecOperatingRate);

  protected final void maybeInitCodec() throws ExoPlaybackException {
    if (codec != null || inputFormat == null) {
      // We have a codec already, or we don't have a format with which to instantiate one.
      return;
    }

    setCodecDrmSession(sourceDrmSession);

    String mimeType = inputFormat.sampleMimeType;
    if (codecDrmSession != null) {
      if (mediaCrypto == null) {
        FrameworkMediaCrypto sessionMediaCrypto = codecDrmSession.getMediaCrypto();
        if (sessionMediaCrypto == null) {
          DrmSessionException drmError = codecDrmSession.getError();
          if (drmError != null) {
            // Continue for now. We may be able to avoid failure if the session recovers, or if a
            // new input format causes the session to be replaced before it's used.
          } else {
            // The drm session isn't open yet.
            return;
          }
        } else {
          try {
            mediaCrypto = new MediaCrypto(sessionMediaCrypto.uuid, sessionMediaCrypto.sessionId);
          } catch (MediaCryptoException e) {
            throw createRendererException(e, inputFormat);
          }
          mediaCryptoRequiresSecureDecoder =
              !sessionMediaCrypto.forceAllowInsecureDecoderComponents
                  && mediaCrypto.requiresSecureDecoderComponent(mimeType);
        }
      }
      if (FrameworkMediaCrypto.WORKAROUND_DEVICE_NEEDS_KEYS_TO_CONFIGURE_CODEC) {
        @DrmSession.State int drmSessionState = codecDrmSession.getState();
        if (drmSessionState == DrmSession.STATE_ERROR) {
          throw createRendererException(codecDrmSession.getError(), inputFormat);
        } else if (drmSessionState != DrmSession.STATE_OPENED_WITH_KEYS) {
          // Wait for keys.
          return;
        }
      }
    }

    try {
      maybeInitCodecWithFallback(mediaCrypto, mediaCryptoRequiresSecureDecoder);
    } catch (DecoderInitializationException e) {
      throw createRendererException(e, inputFormat);
    }
  }

  protected boolean shouldInitCodec(MediaCodecInfo codecInfo) {
    return true;
  }

  /**
   * Returns whether the codec needs the renderer to propagate the end-of-stream signal directly,
   * rather than by using an end-of-stream buffer queued to the codec.
   */
  protected boolean getCodecNeedsEosPropagation() {
    return false;
  }

  /**
   * Polls the pending output format queue for a given buffer timestamp. If a format is present, it
   * is removed and returned. Otherwise returns {@code null}. Subclasses should only call this
   * method if they are taking over responsibility for output format propagation (e.g., when using
   * video tunneling).
   */
  protected final @Nullable Format updateOutputFormatForTime(long presentationTimeUs) {
    Format format = formatQueue.pollFloor(presentationTimeUs);
    if (format != null) {
      outputFormat = format;
    }
    return format;
  }

  protected final MediaCodec getCodec() {
    return codec;
  }

  protected final @Nullable MediaCodecInfo getCodecInfo() {
    return codecInfo;
  }

  @Override
  protected void onEnabled(boolean joining) throws ExoPlaybackException {
    if (drmSessionManager != null && !drmResourcesAcquired) {
      drmResourcesAcquired = true;
      drmSessionManager.prepare();
    }
    decoderCounters = new DecoderCounters();
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    inputStreamEnded = false;
    outputStreamEnded = false;
    pendingOutputEndOfStream = false;
    flushOrReinitializeCodec();
    formatQueue.clear();
  }

  @Override
  public final void setOperatingRate(float operatingRate) throws ExoPlaybackException {
    rendererOperatingRate = operatingRate;
    if (codec != null
        && codecDrainAction != DRAIN_ACTION_REINITIALIZE
        && getState() != STATE_DISABLED) {
      updateCodecOperatingRate();
    }
  }

  @Override
  protected void onDisabled() {
    inputFormat = null;
    if (sourceDrmSession != null || codecDrmSession != null) {
      // TODO: Do something better with this case.
      onReset();
    } else {
      flushOrReleaseCodec();
    }
  }

  @Override
  protected void onReset() {
    try {
      releaseCodec();
    } finally {
      setSourceDrmSession(null);
    }
    if (drmSessionManager != null && drmResourcesAcquired) {
      drmResourcesAcquired = false;
      drmSessionManager.release();
    }
  }

  protected void releaseCodec() {
    availableCodecInfos = null;
    codecInfo = null;
    codecFormat = null;
    codecHasOutputMediaFormat = false;
    resetInputBuffer();
    resetOutputBuffer();
    resetCodecBuffers();
    waitingForKeys = false;
    codecHotswapDeadlineMs = C.TIME_UNSET;
    decodeOnlyPresentationTimestamps.clear();
    largestQueuedPresentationTimeUs = C.TIME_UNSET;
    lastBufferInStreamPresentationTimeUs = C.TIME_UNSET;
    try {
      if (codec != null) {
        decoderCounters.decoderReleaseCount++;
        try {
          if (!skipMediaCodecStopOnRelease) {
            codec.stop();
          }
        } finally {
          codec.release();
        }
      }
    } finally {
      codec = null;
      try {
        if (mediaCrypto != null) {
          mediaCrypto.release();
        }
      } finally {
        mediaCrypto = null;
        mediaCryptoRequiresSecureDecoder = false;
        setCodecDrmSession(null);
      }
    }
  }

  @Override
  protected void onStarted() {
    // Do nothing. Overridden to remove throws clause.
  }

  @Override
  protected void onStopped() {
    // Do nothing. Overridden to remove throws clause.
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (pendingOutputEndOfStream) {
      pendingOutputEndOfStream = false;
      processEndOfStream();
    }
    try {
      if (outputStreamEnded) {
        renderToEndOfStream();
        return;
      }
      if (inputFormat == null && !readToFlagsOnlyBuffer(/* requireFormat= */ true)) {
        // We still don't have a format and can't make progress without one.
        return;
      }
      // We have a format.
      maybeInitCodec();
      if (codec != null) {
        long drainStartTimeMs = SystemClock.elapsedRealtime();
        TraceUtil.beginSection("drainAndFeed");
        while (drainOutputBuffer(positionUs, elapsedRealtimeUs)) {}
        while (feedInputBuffer() && shouldContinueFeeding(drainStartTimeMs)) {}
        TraceUtil.endSection();
      } else {
        decoderCounters.skippedInputBufferCount += skipSource(positionUs);
        // We need to read any format changes despite not having a codec so that drmSession can be
        // updated, and so that we have the most recent format should the codec be initialized. We
        // may also reach the end of the stream. Note that readSource will not read a sample into a
        // flags-only buffer.
        readToFlagsOnlyBuffer(/* requireFormat= */ false);
      }
      decoderCounters.ensureUpdated();
    } catch (IllegalStateException e) {
      if (isMediaCodecException(e)) {
        throw createRendererException(e, inputFormat);
      }
      throw e;
    }
  }

  /**
   * Flushes the codec. If flushing is not possible, the codec will be released and re-instantiated.
   * This method is a no-op if the codec is {@code null}.
   *
   * <p>The implementation of this method calls {@link #flushOrReleaseCodec()}, and {@link
   * #maybeInitCodec()} if the codec needs to be re-instantiated.
   *
   * @return Whether the codec was released and reinitialized, rather than being flushed.
   * @throws ExoPlaybackException If an error occurs re-instantiating the codec.
   */
  protected final boolean flushOrReinitializeCodec() throws ExoPlaybackException {
    boolean released = flushOrReleaseCodec();
    if (released) {
      maybeInitCodec();
    }
    return released;
  }

  /**
   * Flushes the codec. If flushing is not possible, the codec will be released. This method is a
   * no-op if the codec is {@code null}.
   *
   * @return Whether the codec was released.
   */
  protected boolean flushOrReleaseCodec() {
    if (codec == null) {
      return false;
    }
    if (codecDrainAction == DRAIN_ACTION_REINITIALIZE
        || codecNeedsFlushWorkaround
        || (codecNeedsSosFlushWorkaround && !codecHasOutputMediaFormat)
        || (codecNeedsEosFlushWorkaround && codecReceivedEos)) {
      releaseCodec();
      return true;
    }

    codec.flush();
    resetInputBuffer();
    resetOutputBuffer();
    codecHotswapDeadlineMs = C.TIME_UNSET;
    codecReceivedEos = false;
    codecReceivedBuffers = false;
    waitingForFirstSyncSample = true;
    codecNeedsAdaptationWorkaroundBuffer = false;
    shouldSkipAdaptationWorkaroundOutputBuffer = false;
    isDecodeOnlyOutputBuffer = false;
    isLastOutputBuffer = false;

    waitingForKeys = false;
    decodeOnlyPresentationTimestamps.clear();
    largestQueuedPresentationTimeUs = C.TIME_UNSET;
    lastBufferInStreamPresentationTimeUs = C.TIME_UNSET;
    codecDrainState = DRAIN_STATE_NONE;
    codecDrainAction = DRAIN_ACTION_NONE;
    // Reconfiguration data sent shortly before the flush may not have been processed by the
    // decoder. If the codec has been reconfigured we always send reconfiguration data again to
    // guarantee that it's processed.
    codecReconfigurationState =
        codecReconfigured ? RECONFIGURATION_STATE_WRITE_PENDING : RECONFIGURATION_STATE_NONE;
    return false;
  }

  protected DecoderException createDecoderException(
      Throwable cause, @Nullable MediaCodecInfo codecInfo) {
    return new DecoderException(cause, codecInfo);
  }

  /** Reads into {@link #flagsOnlyBuffer} and returns whether a {@link Format} was read. */
  private boolean readToFlagsOnlyBuffer(boolean requireFormat) throws ExoPlaybackException {
    FormatHolder formatHolder = getFormatHolder();
    flagsOnlyBuffer.clear();
    int result = readSource(formatHolder, flagsOnlyBuffer, requireFormat);
    if (result == C.RESULT_FORMAT_READ) {
      onInputFormatChanged(formatHolder);
      return true;
    } else if (result == C.RESULT_BUFFER_READ && flagsOnlyBuffer.isEndOfStream()) {
      inputStreamEnded = true;
      processEndOfStream();
    }
    return false;
  }

  private void maybeInitCodecWithFallback(
      MediaCrypto crypto, boolean mediaCryptoRequiresSecureDecoder)
      throws DecoderInitializationException {
    if (availableCodecInfos == null) {
      try {
        List<MediaCodecInfo> allAvailableCodecInfos =
            getAvailableCodecInfos(mediaCryptoRequiresSecureDecoder);
        availableCodecInfos = new ArrayDeque<>();
        if (enableDecoderFallback) {
          availableCodecInfos.addAll(allAvailableCodecInfos);
        } else if (!allAvailableCodecInfos.isEmpty()) {
          availableCodecInfos.add(allAvailableCodecInfos.get(0));
        }
        preferredDecoderInitializationException = null;
      } catch (DecoderQueryException e) {
        throw new DecoderInitializationException(
            inputFormat,
            e,
            mediaCryptoRequiresSecureDecoder,
            DecoderInitializationException.DECODER_QUERY_ERROR);
      }
    }

    if (availableCodecInfos.isEmpty()) {
      throw new DecoderInitializationException(
          inputFormat,
          /* cause= */ null,
          mediaCryptoRequiresSecureDecoder,
          DecoderInitializationException.NO_SUITABLE_DECODER_ERROR);
    }

    while (codec == null) {
      MediaCodecInfo codecInfo = availableCodecInfos.peekFirst();
      if (!shouldInitCodec(codecInfo)) {
        return;
      }
      try {
        initCodec(codecInfo, crypto);
      } catch (Exception e) {
        Log.w(TAG, "Failed to initialize decoder: " + codecInfo, e);
        // This codec failed to initialize, so fall back to the next codec in the list (if any). We
        // won't try to use this codec again unless there's a format change or the renderer is
        // disabled and re-enabled.
        availableCodecInfos.removeFirst();
        DecoderInitializationException exception =
            new DecoderInitializationException(
                inputFormat, e, mediaCryptoRequiresSecureDecoder, codecInfo);
        if (preferredDecoderInitializationException == null) {
          preferredDecoderInitializationException = exception;
        } else {
          preferredDecoderInitializationException =
              preferredDecoderInitializationException.copyWithFallbackException(exception);
        }
        if (availableCodecInfos.isEmpty()) {
          throw preferredDecoderInitializationException;
        }
      }
    }

    availableCodecInfos = null;
  }

  private List<MediaCodecInfo> getAvailableCodecInfos(boolean mediaCryptoRequiresSecureDecoder)
      throws DecoderQueryException {
    List<MediaCodecInfo> codecInfos =
        getDecoderInfos(mediaCodecSelector, inputFormat, mediaCryptoRequiresSecureDecoder);
    if (codecInfos.isEmpty() && mediaCryptoRequiresSecureDecoder) {
      // The drm session indicates that a secure decoder is required, but the device does not
      // have one. Assuming that supportsFormat indicated support for the media being played, we
      // know that it does not require a secure output path. Most CDM implementations allow
      // playback to proceed with a non-secure decoder in this case, so we try our luck.
      codecInfos =
          getDecoderInfos(mediaCodecSelector, inputFormat, /* requiresSecureDecoder= */ false);
      if (!codecInfos.isEmpty()) {
        Log.w(
            TAG,
            "Drm session requires secure decoder for "
                + inputFormat.sampleMimeType
                + ", but no secure decoder available. Trying to proceed with "
                + codecInfos
                + ".");
      }
    }
    return codecInfos;
  }

  private void initCodec(MediaCodecInfo codecInfo, MediaCrypto crypto) throws Exception {
    long codecInitializingTimestamp;
    long codecInitializedTimestamp;
    MediaCodec codec = null;
    String codecName = codecInfo.name;

    float codecOperatingRate =
        Util.SDK_INT < 23
            ? CODEC_OPERATING_RATE_UNSET
            : getCodecOperatingRateV23(rendererOperatingRate, inputFormat, getStreamFormats());
    if (codecOperatingRate <= assumedMinimumCodecOperatingRate) {
      codecOperatingRate = CODEC_OPERATING_RATE_UNSET;
    }
    try {
      codecInitializingTimestamp = SystemClock.elapsedRealtime();
      TraceUtil.beginSection("createCodec:" + codecName);
      codec = MediaCodec.createByCodecName(codecName);
      TraceUtil.endSection();
      TraceUtil.beginSection("configureCodec");
      configureCodec(codecInfo, codec, inputFormat, crypto, codecOperatingRate);
      TraceUtil.endSection();
      TraceUtil.beginSection("startCodec");
      codec.start();
      TraceUtil.endSection();
      codecInitializedTimestamp = SystemClock.elapsedRealtime();
      getCodecBuffers(codec);
    } catch (Exception e) {
      if (codec != null) {
        resetCodecBuffers();
        codec.release();
      }
      throw e;
    }

    this.codec = codec;
    this.codecInfo = codecInfo;
    this.codecOperatingRate = codecOperatingRate;
    codecFormat = inputFormat;
    codecAdaptationWorkaroundMode = codecAdaptationWorkaroundMode(codecName);
    codecNeedsReconfigureWorkaround = codecNeedsReconfigureWorkaround(codecName);
    codecNeedsDiscardToSpsWorkaround = codecNeedsDiscardToSpsWorkaround(codecName, codecFormat);
    codecNeedsFlushWorkaround = codecNeedsFlushWorkaround(codecName);
    codecNeedsSosFlushWorkaround = codecNeedsSosFlushWorkaround(codecName);
    codecNeedsEosFlushWorkaround = codecNeedsEosFlushWorkaround(codecName);
    codecNeedsEosOutputExceptionWorkaround = codecNeedsEosOutputExceptionWorkaround(codecName);
    codecNeedsMonoChannelCountWorkaround =
        codecNeedsMonoChannelCountWorkaround(codecName, codecFormat);
    codecNeedsEosPropagation =
        codecNeedsEosPropagationWorkaround(codecInfo) || getCodecNeedsEosPropagation();

    resetInputBuffer();
    resetOutputBuffer();
    codecHotswapDeadlineMs =
        getState() == STATE_STARTED
            ? (SystemClock.elapsedRealtime() + MAX_CODEC_HOTSWAP_TIME_MS)
            : C.TIME_UNSET;
    codecReconfigured = false;
    codecReconfigurationState = RECONFIGURATION_STATE_NONE;
    codecReceivedEos = false;
    codecReceivedBuffers = false;
    largestQueuedPresentationTimeUs = C.TIME_UNSET;
    lastBufferInStreamPresentationTimeUs = C.TIME_UNSET;
    codecDrainState = DRAIN_STATE_NONE;
    codecDrainAction = DRAIN_ACTION_NONE;
    codecNeedsAdaptationWorkaroundBuffer = false;
    shouldSkipAdaptationWorkaroundOutputBuffer = false;
    isDecodeOnlyOutputBuffer = false;
    isLastOutputBuffer = false;
    waitingForFirstSyncSample = true;

    decoderCounters.decoderInitCount++;
    long elapsed = codecInitializedTimestamp - codecInitializingTimestamp;
    onCodecInitialized(codecName, codecInitializedTimestamp, elapsed);
  }

  private boolean shouldContinueFeeding(long drainStartTimeMs) {
    return renderTimeLimitMs == C.TIME_UNSET
        || SystemClock.elapsedRealtime() - drainStartTimeMs < renderTimeLimitMs;
  }

  private void getCodecBuffers(MediaCodec codec) {
    if (Util.SDK_INT < 21) {
      inputBuffers = codec.getInputBuffers();
      outputBuffers = codec.getOutputBuffers();
    }
  }

  private void resetCodecBuffers() {
    if (Util.SDK_INT < 21) {
      inputBuffers = null;
      outputBuffers = null;
    }
  }

  private ByteBuffer getInputBuffer(int inputIndex) {
    if (Util.SDK_INT >= 21) {
      return codec.getInputBuffer(inputIndex);
    } else {
      return inputBuffers[inputIndex];
    }
  }

  private ByteBuffer getOutputBuffer(int outputIndex) {
    if (Util.SDK_INT >= 21) {
      return codec.getOutputBuffer(outputIndex);
    } else {
      return outputBuffers[outputIndex];
    }
  }

  private boolean hasOutputBuffer() {
    return outputIndex >= 0;
  }

  private void resetInputBuffer() {
    inputIndex = C.INDEX_UNSET;
    buffer.data = null;
  }

  private void resetOutputBuffer() {
    outputIndex = C.INDEX_UNSET;
    outputBuffer = null;
  }

  private void setSourceDrmSession(@Nullable DrmSession<FrameworkMediaCrypto> session) {
    DrmSession.replaceSession(sourceDrmSession, session);
    sourceDrmSession = session;
  }

  private void setCodecDrmSession(@Nullable DrmSession<FrameworkMediaCrypto> session) {
    DrmSession.replaceSession(codecDrmSession, session);
    codecDrmSession = session;
  }

  /**
   * @return Whether it may be possible to feed more input data.
   * @throws ExoPlaybackException If an error occurs feeding the input buffer.
   */
  private boolean feedInputBuffer() throws ExoPlaybackException {
    if (codec == null || codecDrainState == DRAIN_STATE_WAIT_END_OF_STREAM || inputStreamEnded) {
      return false;
    }

    if (inputIndex < 0) {
      inputIndex = codec.dequeueInputBuffer(0);
      if (inputIndex < 0) {
        return false;
      }
      buffer.data = getInputBuffer(inputIndex);
      buffer.clear();
    }

    if (codecDrainState == DRAIN_STATE_SIGNAL_END_OF_STREAM) {
      // We need to re-initialize the codec. Send an end of stream signal to the existing codec so
      // that it outputs any remaining buffers before we release it.
      if (codecNeedsEosPropagation) {
        // Do nothing.
      } else {
        codecReceivedEos = true;
        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        resetInputBuffer();
      }
      codecDrainState = DRAIN_STATE_WAIT_END_OF_STREAM;
      return false;
    }

    if (codecNeedsAdaptationWorkaroundBuffer) {
      codecNeedsAdaptationWorkaroundBuffer = false;
      buffer.data.put(ADAPTATION_WORKAROUND_BUFFER);
      codec.queueInputBuffer(inputIndex, 0, ADAPTATION_WORKAROUND_BUFFER.length, 0, 0);
      resetInputBuffer();
      codecReceivedBuffers = true;
      return true;
    }

    int result;
    FormatHolder formatHolder = getFormatHolder();
    int adaptiveReconfigurationBytes = 0;
    if (waitingForKeys) {
      // We've already read an encrypted sample into buffer, and are waiting for keys.
      result = C.RESULT_BUFFER_READ;
    } else {
      // For adaptive reconfiguration OMX decoders expect all reconfiguration data to be supplied
      // at the start of the buffer that also contains the first frame in the new format.
      if (codecReconfigurationState == RECONFIGURATION_STATE_WRITE_PENDING) {
        for (int i = 0; i < codecFormat.initializationData.size(); i++) {
          byte[] data = codecFormat.initializationData.get(i);
          buffer.data.put(data);
        }
        codecReconfigurationState = RECONFIGURATION_STATE_QUEUE_PENDING;
      }
      adaptiveReconfigurationBytes = buffer.data.position();
      result = readSource(formatHolder, buffer, false);
    }

    if (hasReadStreamToEnd()) {
      // Notify output queue of the last buffer's timestamp.
      lastBufferInStreamPresentationTimeUs = largestQueuedPresentationTimeUs;
    }

    if (result == C.RESULT_NOTHING_READ) {
      return false;
    }
    if (result == C.RESULT_FORMAT_READ) {
      if (codecReconfigurationState == RECONFIGURATION_STATE_QUEUE_PENDING) {
        // We received two formats in a row. Clear the current buffer of any reconfiguration data
        // associated with the first format.
        buffer.clear();
        codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
      }
      onInputFormatChanged(formatHolder);
      return true;
    }

    // We've read a buffer.
    if (buffer.isEndOfStream()) {
      if (codecReconfigurationState == RECONFIGURATION_STATE_QUEUE_PENDING) {
        // We received a new format immediately before the end of the stream. We need to clear
        // the corresponding reconfiguration data from the current buffer, but re-write it into
        // a subsequent buffer if there are any (e.g. if the user seeks backwards).
        buffer.clear();
        codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
      }
      inputStreamEnded = true;
      if (!codecReceivedBuffers) {
        processEndOfStream();
        return false;
      }
      try {
        if (codecNeedsEosPropagation) {
          // Do nothing.
        } else {
          codecReceivedEos = true;
          codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
          resetInputBuffer();
        }
      } catch (CryptoException e) {
        throw createRendererException(e, inputFormat);
      }
      return false;
    }
    if (waitingForFirstSyncSample && !buffer.isKeyFrame()) {
      buffer.clear();
      if (codecReconfigurationState == RECONFIGURATION_STATE_QUEUE_PENDING) {
        // The buffer we just cleared contained reconfiguration data. We need to re-write this
        // data into a subsequent buffer (if there is one).
        codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
      }
      return true;
    }
    waitingForFirstSyncSample = false;
    boolean bufferEncrypted = buffer.isEncrypted();
    waitingForKeys = shouldWaitForKeys(bufferEncrypted);
    if (waitingForKeys) {
      return false;
    }
    if (codecNeedsDiscardToSpsWorkaround && !bufferEncrypted) {
      NalUnitUtil.discardToSps(buffer.data);
      if (buffer.data.position() == 0) {
        return true;
      }
      codecNeedsDiscardToSpsWorkaround = false;
    }
    try {
      long presentationTimeUs = buffer.timeUs;
      if (buffer.isDecodeOnly()) {
        decodeOnlyPresentationTimestamps.add(presentationTimeUs);
      }
      if (waitingForFirstSampleInFormat) {
        formatQueue.add(presentationTimeUs, inputFormat);
        waitingForFirstSampleInFormat = false;
      }
      largestQueuedPresentationTimeUs =
          Math.max(largestQueuedPresentationTimeUs, presentationTimeUs);

      buffer.flip();
      if (buffer.hasSupplementalData()) {
        handleInputBufferSupplementalData(buffer);
      }
      onQueueInputBuffer(buffer);

      if (bufferEncrypted) {
        MediaCodec.CryptoInfo cryptoInfo = getFrameworkCryptoInfo(buffer,
            adaptiveReconfigurationBytes);
        codec.queueSecureInputBuffer(inputIndex, 0, cryptoInfo, presentationTimeUs, 0);
      } else {
        codec.queueInputBuffer(inputIndex, 0, buffer.data.limit(), presentationTimeUs, 0);
      }
      resetInputBuffer();
      codecReceivedBuffers = true;
      codecReconfigurationState = RECONFIGURATION_STATE_NONE;
      decoderCounters.inputBufferCount++;
    } catch (CryptoException e) {
      throw createRendererException(e, inputFormat);
    }
    return true;
  }

  private boolean shouldWaitForKeys(boolean bufferEncrypted) throws ExoPlaybackException {
    if (codecDrmSession == null
        || (!bufferEncrypted
            && (playClearSamplesWithoutKeys || codecDrmSession.playClearSamplesWithoutKeys()))) {
      return false;
    }
    @DrmSession.State int drmSessionState = codecDrmSession.getState();
    if (drmSessionState == DrmSession.STATE_ERROR) {
      throw createRendererException(codecDrmSession.getError(), inputFormat);
    }
    return drmSessionState != DrmSession.STATE_OPENED_WITH_KEYS;
  }

  /**
   * Called when a {@link MediaCodec} has been created and configured.
   * <p>
   * The default implementation is a no-op.
   *
   * @param name The name of the codec that was initialized.
   * @param initializedTimestampMs {@link SystemClock#elapsedRealtime()} when initialization
   *     finished.
   * @param initializationDurationMs The time taken to initialize the codec in milliseconds.
   */
  protected void onCodecInitialized(String name, long initializedTimestampMs,
      long initializationDurationMs) {
    // Do nothing.
  }

  /**
   * Called when a new {@link Format} is read from the upstream {@link MediaPeriod}.
   *
   * @param formatHolder A {@link FormatHolder} that holds the new {@link Format}.
   * @throws ExoPlaybackException If an error occurs re-initializing the {@link MediaCodec}.
   */
  @SuppressWarnings("unchecked")
  protected void onInputFormatChanged(FormatHolder formatHolder) throws ExoPlaybackException {
    waitingForFirstSampleInFormat = true;
    Format newFormat = Assertions.checkNotNull(formatHolder.format);
    if (formatHolder.includesDrmSession) {
      setSourceDrmSession((DrmSession<FrameworkMediaCrypto>) formatHolder.drmSession);
    } else {
      sourceDrmSession =
          getUpdatedSourceDrmSession(inputFormat, newFormat, drmSessionManager, sourceDrmSession);
    }
    inputFormat = newFormat;

    if (codec == null) {
      maybeInitCodec();
      return;
    }

    // We have an existing codec that we may need to reconfigure or re-initialize. If the existing
    // codec instance is being kept then its operating rate may need to be updated.

    if ((sourceDrmSession == null && codecDrmSession != null)
        || (sourceDrmSession != null && codecDrmSession == null)
        || (sourceDrmSession != codecDrmSession
            && !codecInfo.secure
            && maybeRequiresSecureDecoder(sourceDrmSession, newFormat))
        || (Util.SDK_INT < 23 && sourceDrmSession != codecDrmSession)) {
      // We might need to switch between the clear and protected output paths, or we're using DRM
      // prior to API level 23 where the codec needs to be re-initialized to switch to the new DRM
      // session.
      drainAndReinitializeCodec();
      return;
    }

    switch (canKeepCodec(codec, codecInfo, codecFormat, newFormat)) {
      case KEEP_CODEC_RESULT_NO:
        drainAndReinitializeCodec();
        break;
      case KEEP_CODEC_RESULT_YES_WITH_FLUSH:
        codecFormat = newFormat;
        updateCodecOperatingRate();
        if (sourceDrmSession != codecDrmSession) {
          drainAndUpdateCodecDrmSession();
        } else {
          drainAndFlushCodec();
        }
        break;
      case KEEP_CODEC_RESULT_YES_WITH_RECONFIGURATION:
        if (codecNeedsReconfigureWorkaround) {
          drainAndReinitializeCodec();
        } else {
          codecReconfigured = true;
          codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
          codecNeedsAdaptationWorkaroundBuffer =
              codecAdaptationWorkaroundMode == ADAPTATION_WORKAROUND_MODE_ALWAYS
                  || (codecAdaptationWorkaroundMode == ADAPTATION_WORKAROUND_MODE_SAME_RESOLUTION
                      && newFormat.width == codecFormat.width
                      && newFormat.height == codecFormat.height);
          codecFormat = newFormat;
          updateCodecOperatingRate();
          if (sourceDrmSession != codecDrmSession) {
            drainAndUpdateCodecDrmSession();
          }
        }
        break;
      case KEEP_CODEC_RESULT_YES_WITHOUT_RECONFIGURATION:
        codecFormat = newFormat;
        updateCodecOperatingRate();
        if (sourceDrmSession != codecDrmSession) {
          drainAndUpdateCodecDrmSession();
        }
        break;
      default:
        throw new IllegalStateException(); // Never happens.
    }
  }

  /**
   * Called when the output {@link MediaFormat} of the {@link MediaCodec} changes.
   *
   * <p>The default implementation is a no-op.
   *
   * @param codec The {@link MediaCodec} instance.
   * @param outputMediaFormat The new output {@link MediaFormat}.
   * @throws ExoPlaybackException Thrown if an error occurs handling the new output media format.
   */
  protected void onOutputFormatChanged(MediaCodec codec, MediaFormat outputMediaFormat)
      throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Handles supplemental data associated with an input buffer.
   *
   * <p>The default implementation is a no-op.
   *
   * @param buffer The input buffer that is about to be queued.
   * @throws ExoPlaybackException Thrown if an error occurs handling supplemental data.
   */
  protected void handleInputBufferSupplementalData(DecoderInputBuffer buffer)
      throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Called immediately before an input buffer is queued into the codec.
   *
   * <p>The default implementation is a no-op.
   *
   * @param buffer The buffer to be queued.
   */
  protected void onQueueInputBuffer(DecoderInputBuffer buffer) {
    // Do nothing.
  }

  /**
   * Called when an output buffer is successfully processed.
   * <p>
   * The default implementation is a no-op.
   *
   * @param presentationTimeUs The timestamp associated with the output buffer.
   */
  protected void onProcessedOutputBuffer(long presentationTimeUs) {
    // Do nothing.
  }

  /**
   * Determines whether the existing {@link MediaCodec} can be kept for a new {@link Format}, and if
   * it can whether it requires reconfiguration.
   *
   * <p>The default implementation returns {@link #KEEP_CODEC_RESULT_NO}.
   *
   * @param codec The existing {@link MediaCodec} instance.
   * @param codecInfo A {@link MediaCodecInfo} describing the decoder.
   * @param oldFormat The {@link Format} for which the existing instance is configured.
   * @param newFormat The new {@link Format}.
   * @return Whether the instance can be kept, and if it can whether it requires reconfiguration.
   */
  protected @KeepCodecResult int canKeepCodec(
      MediaCodec codec, MediaCodecInfo codecInfo, Format oldFormat, Format newFormat) {
    return KEEP_CODEC_RESULT_NO;
  }

  @Override
  public boolean isEnded() {
    return outputStreamEnded;
  }

  @Override
  public boolean isReady() {
    return inputFormat != null
        && !waitingForKeys
        && (isSourceReady()
            || hasOutputBuffer()
            || (codecHotswapDeadlineMs != C.TIME_UNSET
                && SystemClock.elapsedRealtime() < codecHotswapDeadlineMs));
  }

  /**
   * Returns the maximum time to block whilst waiting for a decoded output buffer.
   *
   * @return The maximum time to block, in microseconds.
   */
  protected long getDequeueOutputBufferTimeoutUs() {
    return 0;
  }

  /**
   * Returns the {@link MediaFormat#KEY_OPERATING_RATE} value for a given renderer operating rate,
   * current {@link Format} and set of possible stream formats.
   *
   * <p>The default implementation returns {@link #CODEC_OPERATING_RATE_UNSET}.
   *
   * @param operatingRate The renderer operating rate.
   * @param format The {@link Format} for which the codec is being configured.
   * @param streamFormats The possible stream formats.
   * @return The codec operating rate, or {@link #CODEC_OPERATING_RATE_UNSET} if no codec operating
   *     rate should be set.
   */
  protected float getCodecOperatingRateV23(
      float operatingRate, Format format, Format[] streamFormats) {
    return CODEC_OPERATING_RATE_UNSET;
  }

  /**
   * Updates the codec operating rate.
   *
   * @throws ExoPlaybackException If an error occurs releasing or initializing a codec.
   */
  private void updateCodecOperatingRate() throws ExoPlaybackException {
    if (Util.SDK_INT < 23) {
      return;
    }

    float newCodecOperatingRate =
        getCodecOperatingRateV23(rendererOperatingRate, codecFormat, getStreamFormats());
    if (codecOperatingRate == newCodecOperatingRate) {
      // No change.
    } else if (newCodecOperatingRate == CODEC_OPERATING_RATE_UNSET) {
      // The only way to clear the operating rate is to instantiate a new codec instance. See
      // [Internal ref: b/71987865].
      drainAndReinitializeCodec();
    } else if (codecOperatingRate != CODEC_OPERATING_RATE_UNSET
        || newCodecOperatingRate > assumedMinimumCodecOperatingRate) {
      // We need to set the operating rate, either because we've set it previously or because it's
      // above the assumed minimum rate.
      Bundle codecParameters = new Bundle();
      codecParameters.putFloat(MediaFormat.KEY_OPERATING_RATE, newCodecOperatingRate);
      codec.setParameters(codecParameters);
      codecOperatingRate = newCodecOperatingRate;
    }
  }

  /** Starts draining the codec for flush. */
  private void drainAndFlushCodec() {
    if (codecReceivedBuffers) {
      codecDrainState = DRAIN_STATE_SIGNAL_END_OF_STREAM;
      codecDrainAction = DRAIN_ACTION_FLUSH;
    }
  }

  /**
   * Starts draining the codec to update its DRM session. The update may occur immediately if no
   * buffers have been queued to the codec.
   *
   * @throws ExoPlaybackException If an error occurs updating the codec's DRM session.
   */
  private void drainAndUpdateCodecDrmSession() throws ExoPlaybackException {
    if (Util.SDK_INT < 23) {
      // The codec needs to be re-initialized to switch to the source DRM session.
      drainAndReinitializeCodec();
      return;
    }
    if (codecReceivedBuffers) {
      codecDrainState = DRAIN_STATE_SIGNAL_END_OF_STREAM;
      codecDrainAction = DRAIN_ACTION_UPDATE_DRM_SESSION;
    } else {
      // Nothing has been queued to the decoder, so we can do the update immediately.
      updateDrmSessionOrReinitializeCodecV23();
    }
  }

  /**
   * Starts draining the codec for re-initialization. Re-initialization may occur immediately if no
   * buffers have been queued to the codec.
   *
   * @throws ExoPlaybackException If an error occurs re-initializing a codec.
   */
  private void drainAndReinitializeCodec() throws ExoPlaybackException {
    if (codecReceivedBuffers) {
      codecDrainState = DRAIN_STATE_SIGNAL_END_OF_STREAM;
      codecDrainAction = DRAIN_ACTION_REINITIALIZE;
    } else {
      // Nothing has been queued to the decoder, so we can re-initialize immediately.
      reinitializeCodec();
    }
  }

  /**
   * @return Whether it may be possible to drain more output data.
   * @throws ExoPlaybackException If an error occurs draining the output buffer.
   */
  private boolean drainOutputBuffer(long positionUs, long elapsedRealtimeUs)
      throws ExoPlaybackException {
    if (!hasOutputBuffer()) {
      int outputIndex;
      if (codecNeedsEosOutputExceptionWorkaround && codecReceivedEos) {
        try {
          outputIndex =
              codec.dequeueOutputBuffer(outputBufferInfo, getDequeueOutputBufferTimeoutUs());
        } catch (IllegalStateException e) {
          processEndOfStream();
          if (outputStreamEnded) {
            // Release the codec, as it's in an error state.
            releaseCodec();
          }
          return false;
        }
      } else {
        outputIndex =
            codec.dequeueOutputBuffer(outputBufferInfo, getDequeueOutputBufferTimeoutUs());
      }

      if (outputIndex < 0) {
        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED /* (-2) */) {
          processOutputFormat();
          return true;
        } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED /* (-3) */) {
          processOutputBuffersChanged();
          return true;
        }
        /* MediaCodec.INFO_TRY_AGAIN_LATER (-1) or unknown negative return value */
        if (codecNeedsEosPropagation
            && (inputStreamEnded || codecDrainState == DRAIN_STATE_WAIT_END_OF_STREAM)) {
          processEndOfStream();
        }
        return false;
      }

      // We've dequeued a buffer.
      if (shouldSkipAdaptationWorkaroundOutputBuffer) {
        shouldSkipAdaptationWorkaroundOutputBuffer = false;
        codec.releaseOutputBuffer(outputIndex, false);
        return true;
      } else if (outputBufferInfo.size == 0
          && (outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
        // The dequeued buffer indicates the end of the stream. Process it immediately.
        processEndOfStream();
        return false;
      }

      this.outputIndex = outputIndex;
      outputBuffer = getOutputBuffer(outputIndex);
      // The dequeued buffer is a media buffer. Do some initial setup.
      // It will be processed by calling processOutputBuffer (possibly multiple times).
      if (outputBuffer != null) {
        outputBuffer.position(outputBufferInfo.offset);
        outputBuffer.limit(outputBufferInfo.offset + outputBufferInfo.size);
      }
      isDecodeOnlyOutputBuffer = isDecodeOnlyBuffer(outputBufferInfo.presentationTimeUs);
      isLastOutputBuffer =
          lastBufferInStreamPresentationTimeUs == outputBufferInfo.presentationTimeUs;
      updateOutputFormatForTime(outputBufferInfo.presentationTimeUs);
    }

    boolean processedOutputBuffer;
    if (codecNeedsEosOutputExceptionWorkaround && codecReceivedEos) {
      try {
        processedOutputBuffer =
            processOutputBuffer(
                positionUs,
                elapsedRealtimeUs,
                codec,
                outputBuffer,
                outputIndex,
                outputBufferInfo.flags,
                outputBufferInfo.presentationTimeUs,
                isDecodeOnlyOutputBuffer,
                isLastOutputBuffer,
                outputFormat);
      } catch (IllegalStateException e) {
        processEndOfStream();
        if (outputStreamEnded) {
          // Release the codec, as it's in an error state.
          releaseCodec();
        }
        return false;
      }
    } else {
      processedOutputBuffer =
          processOutputBuffer(
              positionUs,
              elapsedRealtimeUs,
              codec,
              outputBuffer,
              outputIndex,
              outputBufferInfo.flags,
              outputBufferInfo.presentationTimeUs,
              isDecodeOnlyOutputBuffer,
              isLastOutputBuffer,
              outputFormat);
    }

    if (processedOutputBuffer) {
      onProcessedOutputBuffer(outputBufferInfo.presentationTimeUs);
      boolean isEndOfStream = (outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
      resetOutputBuffer();
      if (!isEndOfStream) {
        return true;
      }
      processEndOfStream();
    }

    return false;
  }

  /** Processes a new output {@link MediaFormat}. */
  private void processOutputFormat() throws ExoPlaybackException {
    codecHasOutputMediaFormat = true;
    MediaFormat mediaFormat = codec.getOutputFormat();
    if (codecAdaptationWorkaroundMode != ADAPTATION_WORKAROUND_MODE_NEVER
        && mediaFormat.getInteger(MediaFormat.KEY_WIDTH) == ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT
        && mediaFormat.getInteger(MediaFormat.KEY_HEIGHT)
            == ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT) {
      // We assume this format changed event was caused by the adaptation workaround.
      shouldSkipAdaptationWorkaroundOutputBuffer = true;
      return;
    }
    if (codecNeedsMonoChannelCountWorkaround) {
      mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
    }
    onOutputFormatChanged(codec, mediaFormat);
  }

  /**
   * Processes a change in the output buffers.
   */
  private void processOutputBuffersChanged() {
    if (Util.SDK_INT < 21) {
      outputBuffers = codec.getOutputBuffers();
    }
  }

  /**
   * Processes an output media buffer.
   *
   * <p>When a new {@link ByteBuffer} is passed to this method its position and limit delineate the
   * data to be processed. The return value indicates whether the buffer was processed in full. If
   * true is returned then the next call to this method will receive a new buffer to be processed.
   * If false is returned then the same buffer will be passed to the next call. An implementation of
   * this method is free to modify the buffer and can assume that the buffer will not be externally
   * modified between successive calls. Hence an implementation can, for example, modify the
   * buffer's position to keep track of how much of the data it has processed.
   *
   * <p>Note that the first call to this method following a call to {@link #onPositionReset(long,
   * boolean)} will always receive a new {@link ByteBuffer} to be processed.
   *
   * @param positionUs The current media time in microseconds, measured at the start of the current
   *     iteration of the rendering loop.
   * @param elapsedRealtimeUs {@link SystemClock#elapsedRealtime()} in microseconds, measured at the
   *     start of the current iteration of the rendering loop.
   * @param codec The {@link MediaCodec} instance.
   * @param buffer The output buffer to process.
   * @param bufferIndex The index of the output buffer.
   * @param bufferFlags The flags attached to the output buffer.
   * @param bufferPresentationTimeUs The presentation time of the output buffer in microseconds.
   * @param isDecodeOnlyBuffer Whether the buffer was marked with {@link C#BUFFER_FLAG_DECODE_ONLY}
   *     by the source.
   * @param isLastBuffer Whether the buffer is the last sample of the current stream.
   * @param format The {@link Format} associated with the buffer.
   * @return Whether the output buffer was fully processed (e.g. rendered or skipped).
   * @throws ExoPlaybackException If an error occurs processing the output buffer.
   */
  protected abstract boolean processOutputBuffer(
      long positionUs,
      long elapsedRealtimeUs,
      MediaCodec codec,
      ByteBuffer buffer,
      int bufferIndex,
      int bufferFlags,
      long bufferPresentationTimeUs,
      boolean isDecodeOnlyBuffer,
      boolean isLastBuffer,
      Format format)
      throws ExoPlaybackException;

  /**
   * Incrementally renders any remaining output.
   * <p>
   * The default implementation is a no-op.
   *
   * @throws ExoPlaybackException Thrown if an error occurs rendering remaining output.
   */
  protected void renderToEndOfStream() throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Processes an end of stream signal.
   *
   * @throws ExoPlaybackException If an error occurs processing the signal.
   */
  private void processEndOfStream() throws ExoPlaybackException {
    switch (codecDrainAction) {
      case DRAIN_ACTION_REINITIALIZE:
        reinitializeCodec();
        break;
      case DRAIN_ACTION_UPDATE_DRM_SESSION:
        updateDrmSessionOrReinitializeCodecV23();
        break;
      case DRAIN_ACTION_FLUSH:
        flushOrReinitializeCodec();
        break;
      case DRAIN_ACTION_NONE:
      default:
        outputStreamEnded = true;
        renderToEndOfStream();
        break;
    }
  }

  /**
   * Notifies the renderer that output end of stream is pending and should be handled on the next
   * render.
   */
  protected final void setPendingOutputEndOfStream() {
    pendingOutputEndOfStream = true;
  }

  private void reinitializeCodec() throws ExoPlaybackException {
    releaseCodec();
    maybeInitCodec();
  }

  private boolean isDecodeOnlyBuffer(long presentationTimeUs) {
    // We avoid using decodeOnlyPresentationTimestamps.remove(presentationTimeUs) because it would
    // box presentationTimeUs, creating a Long object that would need to be garbage collected.
    int size = decodeOnlyPresentationTimestamps.size();
    for (int i = 0; i < size; i++) {
      if (decodeOnlyPresentationTimestamps.get(i) == presentationTimeUs) {
        decodeOnlyPresentationTimestamps.remove(i);
        return true;
      }
    }
    return false;
  }

  @TargetApi(23)
  private void updateDrmSessionOrReinitializeCodecV23() throws ExoPlaybackException {
    @Nullable FrameworkMediaCrypto sessionMediaCrypto = sourceDrmSession.getMediaCrypto();
    if (sessionMediaCrypto == null) {
      // We'd only expect this to happen if the CDM from which the pending session is obtained needs
      // provisioning. This is unlikely to happen (it probably requires a switch from one DRM scheme
      // to another, where the new CDM hasn't been used before and needs provisioning). It would be
      // possible to handle this case more efficiently (i.e. with a new renderer state that waits
      // for provisioning to finish and then calls mediaCrypto.setMediaDrmSession), but the extra
      // complexity is not warranted given how unlikely the case is to occur.
      reinitializeCodec();
      return;
    }
    if (C.PLAYREADY_UUID.equals(sessionMediaCrypto.uuid)) {
      // The PlayReady CDM does not implement setMediaDrmSession.
      // TODO: Add API check once [Internal ref: b/128835874] is fixed.
      reinitializeCodec();
      return;
    }

    if (flushOrReinitializeCodec()) {
      // The codec was reinitialized. The new codec will be using the new DRM session, so there's
      // nothing more to do.
      return;
    }

    try {
      mediaCrypto.setMediaDrmSession(sessionMediaCrypto.sessionId);
    } catch (MediaCryptoException e) {
      throw createRendererException(e, inputFormat);
    }
    setCodecDrmSession(sourceDrmSession);
    codecDrainState = DRAIN_STATE_NONE;
    codecDrainAction = DRAIN_ACTION_NONE;
  }

  /**
   * Returns whether a {@link DrmSession} may require a secure decoder for a given {@link Format}.
   *
   * @param drmSession The {@link DrmSession}.
   * @param format The {@link Format}.
   * @return Whether a secure decoder may be required.
   */
  private static boolean maybeRequiresSecureDecoder(
      DrmSession<FrameworkMediaCrypto> drmSession, Format format) {
    @Nullable FrameworkMediaCrypto sessionMediaCrypto = drmSession.getMediaCrypto();
    if (sessionMediaCrypto == null) {
      // We'd only expect this to happen if the CDM from which the pending session is obtained needs
      // provisioning. This is unlikely to happen (it probably requires a switch from one DRM scheme
      // to another, where the new CDM hasn't been used before and needs provisioning). Assume that
      // a secure decoder may be required.
      return true;
    }
    if (sessionMediaCrypto.forceAllowInsecureDecoderComponents) {
      return false;
    }
    MediaCrypto mediaCrypto;
    try {
      mediaCrypto = new MediaCrypto(sessionMediaCrypto.uuid, sessionMediaCrypto.sessionId);
    } catch (MediaCryptoException e) {
      // This shouldn't happen, but if it does then assume that a secure decoder may be required.
      return true;
    }
    try {
      return mediaCrypto.requiresSecureDecoderComponent(format.sampleMimeType);
    } finally {
      mediaCrypto.release();
    }
  }

  private static MediaCodec.CryptoInfo getFrameworkCryptoInfo(
      DecoderInputBuffer buffer, int adaptiveReconfigurationBytes) {
    MediaCodec.CryptoInfo cryptoInfo = buffer.cryptoInfo.getFrameworkCryptoInfo();
    if (adaptiveReconfigurationBytes == 0) {
      return cryptoInfo;
    }
    // There must be at least one sub-sample, although numBytesOfClearData is permitted to be
    // null if it contains no clear data. Instantiate it if needed, and add the reconfiguration
    // bytes to the clear byte count of the first sub-sample.
    if (cryptoInfo.numBytesOfClearData == null) {
      cryptoInfo.numBytesOfClearData = new int[1];
    }
    cryptoInfo.numBytesOfClearData[0] += adaptiveReconfigurationBytes;
    return cryptoInfo;
  }

  private static boolean isMediaCodecException(IllegalStateException error) {
    if (Util.SDK_INT >= 21 && isMediaCodecExceptionV21(error)) {
      return true;
    }
    StackTraceElement[] stackTrace = error.getStackTrace();
    return stackTrace.length > 0 && stackTrace[0].getClassName().equals("android.media.MediaCodec");
  }

  @TargetApi(21)
  private static boolean isMediaCodecExceptionV21(IllegalStateException error) {
    return error instanceof MediaCodec.CodecException;
  }

  /**
   * Returns whether the decoder is known to fail when flushed.
   * <p>
   * If true is returned, the renderer will work around the issue by releasing the decoder and
   * instantiating a new one rather than flushing the current instance.
   * <p>
   * See [Internal: b/8347958, b/8543366].
   *
   * @param name The name of the decoder.
   * @return True if the decoder is known to fail when flushed.
   */
  private static boolean codecNeedsFlushWorkaround(String name) {
    return Util.SDK_INT < 18
        || (Util.SDK_INT == 18
        && ("OMX.SEC.avc.dec".equals(name) || "OMX.SEC.avc.dec.secure".equals(name)))
        || (Util.SDK_INT == 19 && Util.MODEL.startsWith("SM-G800")
        && ("OMX.Exynos.avc.dec".equals(name) || "OMX.Exynos.avc.dec.secure".equals(name)));
  }

  /**
   * Returns a mode that specifies when the adaptation workaround should be enabled.
   *
   * <p>When enabled, the workaround queues and discards a blank frame with a resolution whose width
   * and height both equal {@link #ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT}, to reset the decoder's
   * internal state when a format change occurs.
   *
   * <p>See [Internal: b/27807182]. See <a
   * href="https://github.com/google/ExoPlayer/issues/3257">GitHub issue #3257</a>.
   *
   * @param name The name of the decoder.
   * @return The mode specifying when the adaptation workaround should be enabled.
   */
  private @AdaptationWorkaroundMode int codecAdaptationWorkaroundMode(String name) {
    if (Util.SDK_INT <= 25 && "OMX.Exynos.avc.dec.secure".equals(name)
        && (Util.MODEL.startsWith("SM-T585") || Util.MODEL.startsWith("SM-A510")
        || Util.MODEL.startsWith("SM-A520") || Util.MODEL.startsWith("SM-J700"))) {
      return ADAPTATION_WORKAROUND_MODE_ALWAYS;
    } else if (Util.SDK_INT < 24
        && ("OMX.Nvidia.h264.decode".equals(name) || "OMX.Nvidia.h264.decode.secure".equals(name))
        && ("flounder".equals(Util.DEVICE) || "flounder_lte".equals(Util.DEVICE)
        || "grouper".equals(Util.DEVICE) || "tilapia".equals(Util.DEVICE))) {
      return ADAPTATION_WORKAROUND_MODE_SAME_RESOLUTION;
    } else {
      return ADAPTATION_WORKAROUND_MODE_NEVER;
    }
  }

  /**
   * Returns whether the decoder is known to fail when an attempt is made to reconfigure it with a
   * new format's configuration data.
   *
   * <p>When enabled, the workaround will always release and recreate the decoder, rather than
   * attempting to reconfigure the existing instance.
   *
   * @param name The name of the decoder.
   * @return True if the decoder is known to fail when an attempt is made to reconfigure it with a
   *     new format's configuration data.
   */
  private static boolean codecNeedsReconfigureWorkaround(String name) {
    return Util.MODEL.startsWith("SM-T230") && "OMX.MARVELL.VIDEO.HW.CODA7542DECODER".equals(name);
  }

  /**
   * Returns whether the decoder is an H.264/AVC decoder known to fail if NAL units are queued
   * before the codec specific data.
   *
   * <p>If true is returned, the renderer will work around the issue by discarding data up to the
   * SPS.
   *
   * @param name The name of the decoder.
   * @param format The {@link Format} used to configure the decoder.
   * @return True if the decoder is known to fail if NAL units are queued before CSD.
   */
  private static boolean codecNeedsDiscardToSpsWorkaround(String name, Format format) {
    return Util.SDK_INT < 21 && format.initializationData.isEmpty()
        && "OMX.MTK.VIDEO.DECODER.AVC".equals(name);
  }

  /**
   * Returns whether the decoder is known to handle the propagation of the {@link
   * MediaCodec#BUFFER_FLAG_END_OF_STREAM} flag incorrectly on the host device.
   *
   * <p>If true is returned, the renderer will work around the issue by approximating end of stream
   * behavior without relying on the flag being propagated through to an output buffer by the
   * underlying decoder.
   *
   * @param codecInfo Information about the {@link MediaCodec}.
   * @return True if the decoder is known to handle {@link MediaCodec#BUFFER_FLAG_END_OF_STREAM}
   *     propagation incorrectly on the host device. False otherwise.
   */
  private static boolean codecNeedsEosPropagationWorkaround(MediaCodecInfo codecInfo) {
    String name = codecInfo.name;
    return (Util.SDK_INT <= 25 && "OMX.rk.video_decoder.avc".equals(name))
        || (Util.SDK_INT <= 17 && "OMX.allwinner.video.decoder.avc".equals(name))
        || ("Amazon".equals(Util.MANUFACTURER) && "AFTS".equals(Util.MODEL) && codecInfo.secure);
  }

  /**
   * Returns whether the decoder is known to behave incorrectly if flushed after receiving an input
   * buffer with {@link MediaCodec#BUFFER_FLAG_END_OF_STREAM} set.
   * <p>
   * If true is returned, the renderer will work around the issue by instantiating a new decoder
   * when this case occurs.
   * <p>
   * See [Internal: b/8578467, b/23361053].
   *
   * @param name The name of the decoder.
   * @return True if the decoder is known to behave incorrectly if flushed after receiving an input
   *     buffer with {@link MediaCodec#BUFFER_FLAG_END_OF_STREAM} set. False otherwise.
   */
  private static boolean codecNeedsEosFlushWorkaround(String name) {
    return (Util.SDK_INT <= 23 && "OMX.google.vorbis.decoder".equals(name))
        || (Util.SDK_INT <= 19
            && ("hb2000".equals(Util.DEVICE) || "stvm8".equals(Util.DEVICE))
            && ("OMX.amlogic.avc.decoder.awesome".equals(name)
                || "OMX.amlogic.avc.decoder.awesome.secure".equals(name)));
  }

  /**
   * Returns whether the decoder may throw an {@link IllegalStateException} from
   * {@link MediaCodec#dequeueOutputBuffer(MediaCodec.BufferInfo, long)} or
   * {@link MediaCodec#releaseOutputBuffer(int, boolean)} after receiving an input
   * buffer with {@link MediaCodec#BUFFER_FLAG_END_OF_STREAM} set.
   * <p>
   * See [Internal: b/17933838].
   *
   * @param name The name of the decoder.
   * @return True if the decoder may throw an exception after receiving an end-of-stream buffer.
   */
  private static boolean codecNeedsEosOutputExceptionWorkaround(String name) {
    return Util.SDK_INT == 21 && "OMX.google.aac.decoder".equals(name);
  }

  /**
   * Returns whether the decoder is known to set the number of audio channels in the output {@link
   * Format} to 2 for the given input {@link Format}, whilst only actually outputting a single
   * channel.
   *
   * <p>If true is returned then we explicitly override the number of channels in the output {@link
   * Format}, setting it to 1.
   *
   * @param name The decoder name.
   * @param format The input {@link Format}.
   * @return True if the decoder is known to set the number of audio channels in the output {@link
   *     Format} to 2 for the given input {@link Format}, whilst only actually outputting a single
   *     channel. False otherwise.
   */
  private static boolean codecNeedsMonoChannelCountWorkaround(String name, Format format) {
    return Util.SDK_INT <= 18 && format.channelCount == 1
        && "OMX.MTK.AUDIO.DECODER.MP3".equals(name);
  }

  /**
   * Returns whether the decoder is known to behave incorrectly if flushed prior to having output a
   * {@link MediaFormat}.
   *
   * <p>If true is returned, the renderer will work around the issue by instantiating a new decoder
   * when this case occurs.
   *
   * <p>See [Internal: b/141097367].
   *
   * @param name The name of the decoder.
   * @return True if the decoder is known to behave incorrectly if flushed prior to having output a
   *     {@link MediaFormat}. False otherwise.
   */
  private static boolean codecNeedsSosFlushWorkaround(String name) {
    return Util.SDK_INT == 29 && "c2.android.aac.decoder".equals(name);
  }
}
