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
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.CheckResult;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.util.Log;
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
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * An abstract renderer that uses {@link MediaCodec} to decode samples for rendering.
 */
@TargetApi(16)
public abstract class MediaCodecRenderer extends BaseRenderer {

  /**
   * Thrown when a failure occurs instantiating a decoder.
   */
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
     * The name of the decoder that failed to initialize. Null if no suitable decoder was found.
     */
    public final String decoderName;

    /**
     * An optional developer-readable diagnostic information string. May be null.
     */
    public final String diagnosticInfo;

    /**
     * If the decoder failed to initialize and another decoder being used as a fallback also failed
     * to initialize, the {@link DecoderInitializationException} for the fallback decoder. Null if
     * there was no fallback decoder or no suitable decoders were found.
     */
    public final @Nullable DecoderInitializationException fallbackDecoderInitializationException;

    public DecoderInitializationException(Format format, Throwable cause,
        boolean secureDecoderRequired, int errorCode) {
      this(
          "Decoder init failed: [" + errorCode + "], " + format,
          cause,
          format.sampleMimeType,
          secureDecoderRequired,
          /* decoderName= */ null,
          buildCustomDiagnosticInfo(errorCode),
          /* fallbackDecoderInitializationException= */ null);
    }

    public DecoderInitializationException(Format format, Throwable cause,
        boolean secureDecoderRequired, String decoderName) {
      this(
          "Decoder init failed: " + decoderName + ", " + format,
          cause,
          format.sampleMimeType,
          secureDecoderRequired,
          decoderName,
          Util.SDK_INT >= 21 ? getDiagnosticInfoV21(cause) : null,
          /* fallbackDecoderInitializationException= */ null);
    }

    private DecoderInitializationException(
        String message,
        Throwable cause,
        String mimeType,
        boolean secureDecoderRequired,
        @Nullable String decoderName,
        @Nullable String diagnosticInfo,
        @Nullable DecoderInitializationException fallbackDecoderInitializationException) {
      super(message, cause);
      this.mimeType = mimeType;
      this.secureDecoderRequired = secureDecoderRequired;
      this.decoderName = decoderName;
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
          decoderName,
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
      return "com.google.android.exoplayer.MediaCodecTrackRenderer_" + sign + Math.abs(errorCode);
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
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    KEEP_CODEC_RESULT_NO,
    KEEP_CODEC_RESULT_YES_WITHOUT_RECONFIGURATION,
    KEEP_CODEC_RESULT_YES_WITH_RECONFIGURATION
  })
  protected @interface KeepCodecResult {}
  /** The codec cannot be kept. */
  protected static final int KEEP_CODEC_RESULT_NO = 0;
  /** The codec can be kept. No reconfiguration is required. */
  protected static final int KEEP_CODEC_RESULT_YES_WITHOUT_RECONFIGURATION = 1;
  /**
   * The codec can be kept, but must be reconfigured by prefixing the next input buffer with the new
   * format's configuration data.
   */
  protected static final int KEEP_CODEC_RESULT_YES_WITH_RECONFIGURATION = 3;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({RECONFIGURATION_STATE_NONE, RECONFIGURATION_STATE_WRITE_PENDING,
      RECONFIGURATION_STATE_QUEUE_PENDING})
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

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({REINITIALIZATION_STATE_NONE, REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM,
      REINITIALIZATION_STATE_WAIT_END_OF_STREAM})
  private @interface ReinitializationState {}
  /**
   * The codec does not need to be re-initialized.
   */
  private static final int REINITIALIZATION_STATE_NONE = 0;
  /**
   * The input format has changed in a way that requires the codec to be re-initialized, but we
   * haven't yet signaled an end of stream to the existing codec. We need to do so in order to
   * ensure that it outputs any remaining buffers before we release it.
   */
  private static final int REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM = 1;
  /**
   * The input format has changed in a way that requires the codec to be re-initialized, and we've
   * signaled an end of stream to the existing codec. We're waiting for the codec to output an end
   * of stream signal to indicate that it has output any remaining buffers before we release it.
   */
  private static final int REINITIALIZATION_STATE_WAIT_END_OF_STREAM = 2;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({ADAPTATION_WORKAROUND_MODE_NEVER, ADAPTATION_WORKAROUND_MODE_SAME_RESOLUTION,
      ADAPTATION_WORKAROUND_MODE_ALWAYS})
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
   * H.264/AVC buffer to queue when using the adaptation workaround (see
   * {@link #codecAdaptationWorkaroundMode(String)}. Consists of three NAL units with start codes:
   * Baseline sequence/picture parameter sets and a 32 * 32 pixel IDR slice. This stream can be
   * queued to force a resolution change when adapting to a new format.
   */
  private static final byte[] ADAPTATION_WORKAROUND_BUFFER = Util.getBytesFromHexString(
      "0000016742C00BDA259000000168CE0F13200000016588840DCE7118A0002FBF1C31C3275D78");
  private static final int ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT = 32;

  private final MediaCodecSelector mediaCodecSelector;
  @Nullable
  private final DrmSessionManager<FrameworkMediaCrypto> drmSessionManager;
  private final boolean playClearSamplesWithoutKeys;
  private final float assumedMinimumCodecOperatingRate;
  private final DecoderInputBuffer buffer;
  private final DecoderInputBuffer flagsOnlyBuffer;
  private final FormatHolder formatHolder;
  private final List<Long> decodeOnlyPresentationTimestamps;
  private final MediaCodec.BufferInfo outputBufferInfo;

  private Format format;
  private DrmSession<FrameworkMediaCrypto> drmSession;
  private DrmSession<FrameworkMediaCrypto> pendingDrmSession;
  private MediaCodec codec;
  private float rendererOperatingRate;
  private float codecOperatingRate;
  private boolean codecConfiguredWithOperatingRate;
  private @Nullable ArrayDeque<MediaCodecInfo> availableCodecInfos;
  private @Nullable DecoderInitializationException preferredDecoderInitializationException;
  private @Nullable MediaCodecInfo codecInfo;
  private @AdaptationWorkaroundMode int codecAdaptationWorkaroundMode;
  private boolean codecNeedsDiscardToSpsWorkaround;
  private boolean codecNeedsFlushWorkaround;
  private boolean codecNeedsEosPropagationWorkaround;
  private boolean codecNeedsEosFlushWorkaround;
  private boolean codecNeedsEosOutputExceptionWorkaround;
  private boolean codecNeedsMonoChannelCountWorkaround;
  private boolean codecNeedsAdaptationWorkaroundBuffer;
  private boolean shouldSkipAdaptationWorkaroundOutputBuffer;
  private ByteBuffer[] inputBuffers;
  private ByteBuffer[] outputBuffers;
  private long codecHotswapDeadlineMs;
  private int inputIndex;
  private int outputIndex;
  private ByteBuffer outputBuffer;
  private boolean shouldSkipOutputBuffer;
  private boolean codecReconfigured;
  private @ReconfigurationState int codecReconfigurationState;
  private @ReinitializationState int codecReinitializationState;
  private boolean codecReceivedBuffers;
  private boolean codecReceivedEos;

  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  private boolean waitingForKeys;
  private boolean waitingForFirstSyncFrame;

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
   * @param assumedMinimumCodecOperatingRate A codec operating rate that all codecs instantiated by
   *     this renderer are assumed to meet implicitly (i.e. without the operating rate being set
   *     explicitly using {@link MediaFormat#KEY_OPERATING_RATE}).
   */
  public MediaCodecRenderer(
      int trackType,
      MediaCodecSelector mediaCodecSelector,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys,
      float assumedMinimumCodecOperatingRate) {
    super(trackType);
    Assertions.checkState(Util.SDK_INT >= 16);
    this.mediaCodecSelector = Assertions.checkNotNull(mediaCodecSelector);
    this.drmSessionManager = drmSessionManager;
    this.playClearSamplesWithoutKeys = playClearSamplesWithoutKeys;
    this.assumedMinimumCodecOperatingRate = assumedMinimumCodecOperatingRate;
    buffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    flagsOnlyBuffer = DecoderInputBuffer.newFlagsOnlyInstance();
    formatHolder = new FormatHolder();
    decodeOnlyPresentationTimestamps = new ArrayList<>();
    outputBufferInfo = new MediaCodec.BufferInfo();
    codecReconfigurationState = RECONFIGURATION_STATE_NONE;
    codecReinitializationState = REINITIALIZATION_STATE_NONE;
    codecOperatingRate = CODEC_OPERATING_RATE_UNSET;
    rendererOperatingRate = 1f;
  }

  @Override
  public final int supportsMixedMimeTypeAdaptation() {
    return ADAPTIVE_NOT_SEAMLESS;
  }

  @Override
  public final int supportsFormat(Format format) throws ExoPlaybackException {
    try {
      return supportsFormat(mediaCodecSelector, drmSessionManager, format);
    } catch (DecoderQueryException e) {
      throw ExoPlaybackException.createForRenderer(e, getIndex());
    }
  }

  /**
   * Returns the extent to which the renderer is capable of supporting a given format.
   *
   * @param mediaCodecSelector The decoder selector.
   * @param drmSessionManager The renderer's {@link DrmSessionManager}.
   * @param format The format.
   * @return The extent to which the renderer is capable of supporting the given format. See
   *     {@link #supportsFormat(Format)} for more detail.
   * @throws DecoderQueryException If there was an error querying decoders.
   */
  protected abstract int supportsFormat(MediaCodecSelector mediaCodecSelector,
      DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, Format format)
      throws DecoderQueryException;

  /**
   * Returns a list of decoders that can decode media in the specified format, in priority order.
   *
   * @param mediaCodecSelector The decoder selector.
   * @param format The format for which a decoder is required.
   * @param requiresSecureDecoder Whether a secure decoder is required.
   * @return A list of {@link MediaCodecInfo}s corresponding to decoders. May be empty.
   * @throws DecoderQueryException Thrown if there was an error querying decoders.
   */
  protected List<MediaCodecInfo> getDecoderInfos(
      MediaCodecSelector mediaCodecSelector, Format format, boolean requiresSecureDecoder)
      throws DecoderQueryException {
    return mediaCodecSelector.getDecoderInfos(format, requiresSecureDecoder);
  }

  /**
   * Configures a newly created {@link MediaCodec}.
   *
   * @param codecInfo Information about the {@link MediaCodec} being configured.
   * @param codec The {@link MediaCodec} to configure.
   * @param format The format for which the codec is being configured.
   * @param crypto For drm protected playbacks, a {@link MediaCrypto} to use for decryption.
   * @param codecOperatingRate The codec operating rate, or {@link #CODEC_OPERATING_RATE_UNSET} if
   *     no codec operating rate should be set.
   * @throws DecoderQueryException If an error occurs querying {@code codecInfo}.
   */
  protected abstract void configureCodec(
      MediaCodecInfo codecInfo,
      MediaCodec codec,
      Format format,
      MediaCrypto crypto,
      float codecOperatingRate)
      throws DecoderQueryException;

  protected final void maybeInitCodec() throws ExoPlaybackException {
    if (codec != null || format == null) {
      // We have a codec already, or we don't have a format with which to instantiate one.
      return;
    }

    drmSession = pendingDrmSession;
    String mimeType = format.sampleMimeType;
    MediaCrypto wrappedMediaCrypto = null;
    boolean drmSessionRequiresSecureDecoder = false;
    if (drmSession != null) {
      FrameworkMediaCrypto mediaCrypto = drmSession.getMediaCrypto();
      if (mediaCrypto == null) {
        DrmSessionException drmError = drmSession.getError();
        if (drmError != null) {
          // Continue for now. We may be able to avoid failure if the session recovers, or if a new
          // input format causes the session to be replaced before it's used.
        } else {
          // The drm session isn't open yet.
          return;
        }
      } else {
        wrappedMediaCrypto = mediaCrypto.getWrappedMediaCrypto();
        drmSessionRequiresSecureDecoder = mediaCrypto.requiresSecureDecoderComponent(mimeType);
      }
      if (deviceNeedsDrmKeysToConfigureCodecWorkaround()) {
        @DrmSession.State int drmSessionState = drmSession.getState();
        if (drmSessionState == DrmSession.STATE_ERROR) {
          throw ExoPlaybackException.createForRenderer(drmSession.getError(), getIndex());
        } else if (drmSessionState != DrmSession.STATE_OPENED_WITH_KEYS) {
          // Wait for keys.
          return;
        }
      }
    }

    try {
      if (!initCodecWithFallback(wrappedMediaCrypto, drmSessionRequiresSecureDecoder)) {
        // We can't initialize a codec yet.
        return;
      }
    } catch (DecoderInitializationException e) {
      throw ExoPlaybackException.createForRenderer(e, getIndex());
    }

    String codecName = codecInfo.name;
    codecAdaptationWorkaroundMode = codecAdaptationWorkaroundMode(codecName);
    codecNeedsDiscardToSpsWorkaround = codecNeedsDiscardToSpsWorkaround(codecName, format);
    codecNeedsFlushWorkaround = codecNeedsFlushWorkaround(codecName);
    codecNeedsEosPropagationWorkaround = codecNeedsEosPropagationWorkaround(codecInfo);
    codecNeedsEosFlushWorkaround = codecNeedsEosFlushWorkaround(codecName);
    codecNeedsEosOutputExceptionWorkaround = codecNeedsEosOutputExceptionWorkaround(codecName);
    codecNeedsMonoChannelCountWorkaround = codecNeedsMonoChannelCountWorkaround(codecName, format);
    codecHotswapDeadlineMs =
        getState() == STATE_STARTED
            ? (SystemClock.elapsedRealtime() + MAX_CODEC_HOTSWAP_TIME_MS)
            : C.TIME_UNSET;
    resetInputBuffer();
    resetOutputBuffer();
    waitingForFirstSyncFrame = true;
    decoderCounters.decoderInitCount++;
  }

  protected boolean shouldInitCodec(MediaCodecInfo codecInfo) {
    return true;
  }

  protected final MediaCodec getCodec() {
    return codec;
  }

  protected final MediaCodecInfo getCodecInfo() {
    return codecInfo;
  }

  @Override
  protected void onEnabled(boolean joining) throws ExoPlaybackException {
    decoderCounters = new DecoderCounters();
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    inputStreamEnded = false;
    outputStreamEnded = false;
    if (codec != null) {
      flushCodec();
    }
  }

  @Override
  public final void setOperatingRate(float operatingRate) throws ExoPlaybackException {
    rendererOperatingRate = operatingRate;
    updateCodecOperatingRate();
  }

  @Override
  protected void onDisabled() {
    format = null;
    availableCodecInfos = null;
    try {
      releaseCodec();
    } finally {
      try {
        if (drmSession != null) {
          drmSessionManager.releaseSession(drmSession);
        }
      } finally {
        try {
          if (pendingDrmSession != null && pendingDrmSession != drmSession) {
            drmSessionManager.releaseSession(pendingDrmSession);
          }
        } finally {
          drmSession = null;
          pendingDrmSession = null;
        }
      }
    }
  }

  protected void releaseCodec() {
    codecHotswapDeadlineMs = C.TIME_UNSET;
    resetInputBuffer();
    resetOutputBuffer();
    waitingForKeys = false;
    shouldSkipOutputBuffer = false;
    decodeOnlyPresentationTimestamps.clear();
    resetCodecBuffers();
    codecInfo = null;
    codecReconfigured = false;
    codecReceivedBuffers = false;
    codecNeedsDiscardToSpsWorkaround = false;
    codecNeedsFlushWorkaround = false;
    codecAdaptationWorkaroundMode = ADAPTATION_WORKAROUND_MODE_NEVER;
    codecNeedsEosPropagationWorkaround = false;
    codecNeedsEosFlushWorkaround = false;
    codecNeedsMonoChannelCountWorkaround = false;
    codecNeedsAdaptationWorkaroundBuffer = false;
    shouldSkipAdaptationWorkaroundOutputBuffer = false;
    codecReceivedEos = false;
    codecReconfigurationState = RECONFIGURATION_STATE_NONE;
    codecReinitializationState = REINITIALIZATION_STATE_NONE;
    codecConfiguredWithOperatingRate = false;
    if (codec != null) {
      decoderCounters.decoderReleaseCount++;
      try {
        codec.stop();
      } finally {
        try {
          codec.release();
        } finally {
          codec = null;
          if (drmSession != null && pendingDrmSession != drmSession) {
            try {
              drmSessionManager.releaseSession(drmSession);
            } finally {
              drmSession = null;
            }
          }
        }
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
    if (outputStreamEnded) {
      renderToEndOfStream();
      return;
    }
    if (format == null) {
      // We don't have a format yet, so try and read one.
      flagsOnlyBuffer.clear();
      int result = readSource(formatHolder, flagsOnlyBuffer, true);
      if (result == C.RESULT_FORMAT_READ) {
        onInputFormatChanged(formatHolder.format);
      } else if (result == C.RESULT_BUFFER_READ) {
        // End of stream read having not read a format.
        Assertions.checkState(flagsOnlyBuffer.isEndOfStream());
        inputStreamEnded = true;
        processEndOfStream();
        return;
      } else {
        // We still don't have a format and can't make progress without one.
        return;
      }
    }
    // We have a format.
    maybeInitCodec();
    if (codec != null) {
      TraceUtil.beginSection("drainAndFeed");
      while (drainOutputBuffer(positionUs, elapsedRealtimeUs)) {}
      while (feedInputBuffer()) {}
      TraceUtil.endSection();
    } else {
      decoderCounters.skippedInputBufferCount += skipSource(positionUs);
      // We need to read any format changes despite not having a codec so that drmSession can be
      // updated, and so that we have the most recent format should the codec be initialized. We may
      // also reach the end of the stream. Note that readSource will not read a sample into a
      // flags-only buffer.
      flagsOnlyBuffer.clear();
      int result = readSource(formatHolder, flagsOnlyBuffer, false);
      if (result == C.RESULT_FORMAT_READ) {
        onInputFormatChanged(formatHolder.format);
      } else if (result == C.RESULT_BUFFER_READ) {
        Assertions.checkState(flagsOnlyBuffer.isEndOfStream());
        inputStreamEnded = true;
        processEndOfStream();
      }
    }
    decoderCounters.ensureUpdated();
  }

  protected void flushCodec() throws ExoPlaybackException {
    codecHotswapDeadlineMs = C.TIME_UNSET;
    resetInputBuffer();
    resetOutputBuffer();
    waitingForFirstSyncFrame = true;
    waitingForKeys = false;
    shouldSkipOutputBuffer = false;
    decodeOnlyPresentationTimestamps.clear();
    codecNeedsAdaptationWorkaroundBuffer = false;
    shouldSkipAdaptationWorkaroundOutputBuffer = false;
    if (codecNeedsFlushWorkaround || (codecNeedsEosFlushWorkaround && codecReceivedEos)) {
      releaseCodec();
      maybeInitCodec();
    } else if (codecReinitializationState != REINITIALIZATION_STATE_NONE) {
      // We're already waiting to release and re-initialize the codec. Since we're now flushing,
      // there's no need to wait any longer.
      releaseCodec();
      maybeInitCodec();
    } else {
      // We can flush and re-use the existing decoder.
      codec.flush();
      codecReceivedBuffers = false;
    }
    if (codecReconfigured && format != null) {
      // Any reconfiguration data that we send shortly before the flush may be discarded. We
      // avoid this issue by sending reconfiguration data following every flush.
      codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
    }
  }

  private boolean initCodecWithFallback(MediaCrypto crypto, boolean drmSessionRequiresSecureDecoder)
      throws DecoderInitializationException {
    if (availableCodecInfos == null) {
      try {
        availableCodecInfos =
            new ArrayDeque<>(getAvailableCodecInfos(drmSessionRequiresSecureDecoder));
        preferredDecoderInitializationException = null;
      } catch (DecoderQueryException e) {
        throw new DecoderInitializationException(
            format,
            e,
            drmSessionRequiresSecureDecoder,
            DecoderInitializationException.DECODER_QUERY_ERROR);
      }
    }

    if (availableCodecInfos.isEmpty()) {
      throw new DecoderInitializationException(
          format,
          /* cause= */ null,
          drmSessionRequiresSecureDecoder,
          DecoderInitializationException.NO_SUITABLE_DECODER_ERROR);
    }

    while (true) {
      MediaCodecInfo codecInfo = availableCodecInfos.peekFirst();
      if (!shouldInitCodec(codecInfo)) {
        return false;
      }
      try {
        initCodec(codecInfo, crypto);
        return true;
      } catch (Exception e) {
        Log.w(TAG, "Failed to initialize decoder: " + codecInfo, e);
        // This codec failed to initialize, so fall back to the next codec in the list (if any). We
        // won't try to use this codec again unless there's a format change or the renderer is
        // disabled and re-enabled.
        availableCodecInfos.removeFirst();
        DecoderInitializationException exception =
            new DecoderInitializationException(
                format, e, drmSessionRequiresSecureDecoder, codecInfo.name);
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
  }

  private List<MediaCodecInfo> getAvailableCodecInfos(boolean drmSessionRequiresSecureDecoder)
      throws DecoderQueryException {
    List<MediaCodecInfo> codecInfos =
        getDecoderInfos(mediaCodecSelector, format, drmSessionRequiresSecureDecoder);
    if (codecInfos.isEmpty() && drmSessionRequiresSecureDecoder) {
      // The drm session indicates that a secure decoder is required, but the device does not
      // have one. Assuming that supportsFormat indicated support for the media being played, we
      // know that it does not require a secure output path. Most CDM implementations allow
      // playback to proceed with a non-secure decoder in this case, so we try our luck.
      codecInfos = getDecoderInfos(mediaCodecSelector, format, /* requiresSecureDecoder= */ false);
      if (!codecInfos.isEmpty()) {
        Log.w(
            TAG,
            "Drm session requires secure decoder for "
                + format.sampleMimeType
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
    String name = codecInfo.name;
    updateCodecOperatingRate();
    boolean configureWithOperatingRate = codecOperatingRate > assumedMinimumCodecOperatingRate;
    try {
      codecInitializingTimestamp = SystemClock.elapsedRealtime();
      TraceUtil.beginSection("createCodec:" + name);
      codec = MediaCodec.createByCodecName(name);
      TraceUtil.endSection();
      TraceUtil.beginSection("configureCodec");
      configureCodec(
          codecInfo,
          codec,
          format,
          crypto,
          configureWithOperatingRate ? codecOperatingRate : CODEC_OPERATING_RATE_UNSET);
      codecConfiguredWithOperatingRate = configureWithOperatingRate;
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
    long elapsed = codecInitializedTimestamp - codecInitializingTimestamp;
    onCodecInitialized(name, codecInitializedTimestamp, elapsed);
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

  /**
   * @return Whether it may be possible to feed more input data.
   * @throws ExoPlaybackException If an error occurs feeding the input buffer.
   */
  private boolean feedInputBuffer() throws ExoPlaybackException {
    if (codec == null || codecReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM
        || inputStreamEnded) {
      // We need to reinitialize the codec or the input stream has ended.
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

    if (codecReinitializationState == REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM) {
      // We need to re-initialize the codec. Send an end of stream signal to the existing codec so
      // that it outputs any remaining buffers before we release it.
      if (codecNeedsEosPropagationWorkaround) {
        // Do nothing.
      } else {
        codecReceivedEos = true;
        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        resetInputBuffer();
      }
      codecReinitializationState = REINITIALIZATION_STATE_WAIT_END_OF_STREAM;
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
    int adaptiveReconfigurationBytes = 0;
    if (waitingForKeys) {
      // We've already read an encrypted sample into buffer, and are waiting for keys.
      result = C.RESULT_BUFFER_READ;
    } else {
      // For adaptive reconfiguration OMX decoders expect all reconfiguration data to be supplied
      // at the start of the buffer that also contains the first frame in the new format.
      if (codecReconfigurationState == RECONFIGURATION_STATE_WRITE_PENDING) {
        for (int i = 0; i < format.initializationData.size(); i++) {
          byte[] data = format.initializationData.get(i);
          buffer.data.put(data);
        }
        codecReconfigurationState = RECONFIGURATION_STATE_QUEUE_PENDING;
      }
      adaptiveReconfigurationBytes = buffer.data.position();
      result = readSource(formatHolder, buffer, false);
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
      onInputFormatChanged(formatHolder.format);
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
        if (codecNeedsEosPropagationWorkaround) {
          // Do nothing.
        } else {
          codecReceivedEos = true;
          codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
          resetInputBuffer();
        }
      } catch (CryptoException e) {
        throw ExoPlaybackException.createForRenderer(e, getIndex());
      }
      return false;
    }
    if (waitingForFirstSyncFrame && !buffer.isKeyFrame()) {
      buffer.clear();
      if (codecReconfigurationState == RECONFIGURATION_STATE_QUEUE_PENDING) {
        // The buffer we just cleared contained reconfiguration data. We need to re-write this
        // data into a subsequent buffer (if there is one).
        codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
      }
      return true;
    }
    waitingForFirstSyncFrame = false;
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

      buffer.flip();
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
      throw ExoPlaybackException.createForRenderer(e, getIndex());
    }
    return true;
  }

  private boolean shouldWaitForKeys(boolean bufferEncrypted) throws ExoPlaybackException {
    if (drmSession == null || (!bufferEncrypted && playClearSamplesWithoutKeys)) {
      return false;
    }
    @DrmSession.State int drmSessionState = drmSession.getState();
    if (drmSessionState == DrmSession.STATE_ERROR) {
      throw ExoPlaybackException.createForRenderer(drmSession.getError(), getIndex());
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
   * Called when a new format is read from the upstream {@link MediaPeriod}.
   *
   * @param newFormat The new format.
   * @throws ExoPlaybackException If an error occurs reinitializing the {@link MediaCodec}.
   */
  protected void onInputFormatChanged(Format newFormat) throws ExoPlaybackException {
    Format oldFormat = format;
    format = newFormat;

    boolean drmInitDataChanged =
        !Util.areEqual(format.drmInitData, oldFormat == null ? null : oldFormat.drmInitData);
    if (drmInitDataChanged) {
      if (format.drmInitData != null) {
        if (drmSessionManager == null) {
          throw ExoPlaybackException.createForRenderer(
              new IllegalStateException("Media requires a DrmSessionManager"), getIndex());
        }
        pendingDrmSession = drmSessionManager.acquireSession(Looper.myLooper(), format.drmInitData);
        if (pendingDrmSession == drmSession) {
          drmSessionManager.releaseSession(pendingDrmSession);
        }
      } else {
        pendingDrmSession = null;
      }
    }

    boolean keepingCodec = false;
    if (pendingDrmSession == drmSession && codec != null) {
      switch (canKeepCodec(codec, codecInfo, oldFormat, format)) {
        case KEEP_CODEC_RESULT_NO:
          // Do nothing.
          break;
        case KEEP_CODEC_RESULT_YES_WITHOUT_RECONFIGURATION:
          keepingCodec = true;
          break;
        case KEEP_CODEC_RESULT_YES_WITH_RECONFIGURATION:
          keepingCodec = true;
          codecReconfigured = true;
          codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
          codecNeedsAdaptationWorkaroundBuffer =
              codecAdaptationWorkaroundMode == ADAPTATION_WORKAROUND_MODE_ALWAYS
                  || (codecAdaptationWorkaroundMode == ADAPTATION_WORKAROUND_MODE_SAME_RESOLUTION
                      && format.width == oldFormat.width
                      && format.height == oldFormat.height);
          break;
        default:
          throw new IllegalStateException(); // Never happens.
      }
    }

    if (!keepingCodec) {
      reinitializeCodec();
    } else {
      updateCodecOperatingRate();
    }
  }

  /**
   * Called when the output format of the {@link MediaCodec} changes.
   * <p>
   * The default implementation is a no-op.
   *
   * @param codec The {@link MediaCodec} instance.
   * @param outputFormat The new output format.
   * @throws ExoPlaybackException Thrown if an error occurs handling the new output format.
   */
  protected void onOutputFormatChanged(MediaCodec codec, MediaFormat outputFormat)
      throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Called immediately before an input buffer is queued into the codec.
   * <p>
   * The default implementation is a no-op.
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
   * Determines whether the existing {@link MediaCodec} can be kept for a new format, and if it can
   * whether it requires reconfiguration.
   *
   * <p>The default implementation returns {@link #KEEP_CODEC_RESULT_NO}.
   *
   * @param codec The existing {@link MediaCodec} instance.
   * @param codecInfo A {@link MediaCodecInfo} describing the decoder.
   * @param oldFormat The format for which the existing instance is configured.
   * @param newFormat The new format.
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
    return format != null
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
   * current format and set of possible stream formats.
   *
   * <p>The default implementation returns {@link #CODEC_OPERATING_RATE_UNSET}.
   *
   * @param operatingRate The renderer operating rate.
   * @param format The format for which the codec is being configured.
   * @param streamFormats The possible stream formats.
   * @return The codec operating rate, or {@link #CODEC_OPERATING_RATE_UNSET} if no codec operating
   *     rate should be set.
   */
  protected float getCodecOperatingRate(
      float operatingRate, Format format, Format[] streamFormats) {
    return CODEC_OPERATING_RATE_UNSET;
  }

  /**
   * Updates the codec operating rate, and the codec itself if necessary.
   *
   * @throws ExoPlaybackException If an error occurs releasing or initializing a codec.
   */
  private void updateCodecOperatingRate() throws ExoPlaybackException {
    if (format == null || Util.SDK_INT < 23) {
      return;
    }

    float codecOperatingRate =
        getCodecOperatingRate(rendererOperatingRate, format, getStreamFormats());
    if (this.codecOperatingRate == codecOperatingRate) {
      return;
    }

    this.codecOperatingRate = codecOperatingRate;
    if (codec == null || codecReinitializationState != REINITIALIZATION_STATE_NONE) {
      // Either no codec, or it's about to be reinitialized anyway.
    } else if (codecOperatingRate == CODEC_OPERATING_RATE_UNSET
        && codecConfiguredWithOperatingRate) {
      // We need to clear the operating rate. The only way to do so is to instantiate a new codec
      // instance. See [Internal ref: b/71987865].
      reinitializeCodec();
    } else if (codecOperatingRate != CODEC_OPERATING_RATE_UNSET
        && (codecConfiguredWithOperatingRate
            || codecOperatingRate > assumedMinimumCodecOperatingRate)) {
      // We need to set the operating rate, either because we've set it previously or because it's
      // above the assumed minimum rate.
      Bundle codecParameters = new Bundle();
      codecParameters.putFloat(MediaFormat.KEY_OPERATING_RATE, codecOperatingRate);
      codec.setParameters(codecParameters);
      codecConfiguredWithOperatingRate = true;
    }
  }

  /**
   * Starts the process of releasing the existing codec and initializing a new one. This may occur
   * immediately, or be deferred until any final output buffers have been dequeued.
   *
   * @throws ExoPlaybackException If an error occurs releasing or initializing a codec.
   */
  private void reinitializeCodec() throws ExoPlaybackException {
    availableCodecInfos = null;
    if (codecReceivedBuffers) {
      // Signal end of stream and wait for any final output buffers before re-initialization.
      codecReinitializationState = REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM;
    } else {
      // There aren't any final output buffers, so perform re-initialization immediately.
      releaseCodec();
      maybeInitCodec();
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

      if (outputIndex >= 0) {
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
        } else {
          this.outputIndex = outputIndex;
          outputBuffer = getOutputBuffer(outputIndex);
          // The dequeued buffer is a media buffer. Do some initial setup.
          // It will be processed by calling processOutputBuffer (possibly multiple times).
          if (outputBuffer != null) {
            outputBuffer.position(outputBufferInfo.offset);
            outputBuffer.limit(outputBufferInfo.offset + outputBufferInfo.size);
          }
          shouldSkipOutputBuffer = shouldSkipOutputBuffer(outputBufferInfo.presentationTimeUs);
        }
      } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED /* (-2) */) {
        processOutputFormat();
        return true;
      } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED /* (-3) */) {
        processOutputBuffersChanged();
        return true;
      } else /* MediaCodec.INFO_TRY_AGAIN_LATER (-1) or unknown negative return value */ {
        if (codecNeedsEosPropagationWorkaround
            && (inputStreamEnded
                || codecReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM)) {
          processEndOfStream();
        }
        return false;
      }
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
                shouldSkipOutputBuffer);
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
              shouldSkipOutputBuffer);
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

  /**
   * Processes a new output format.
   */
  private void processOutputFormat() throws ExoPlaybackException {
    MediaFormat format = codec.getOutputFormat();
    if (codecAdaptationWorkaroundMode != ADAPTATION_WORKAROUND_MODE_NEVER
        && format.getInteger(MediaFormat.KEY_WIDTH) == ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT
        && format.getInteger(MediaFormat.KEY_HEIGHT) == ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT) {
      // We assume this format changed event was caused by the adaptation workaround.
      shouldSkipAdaptationWorkaroundOutputBuffer = true;
      return;
    }
    if (codecNeedsMonoChannelCountWorkaround) {
      format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
    }
    onOutputFormatChanged(codec, format);
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
   * <p>
   * When a new {@link ByteBuffer} is passed to this method its position and limit delineate the
   * data to be processed. The return value indicates whether the buffer was processed in full. If
   * true is returned then the next call to this method will receive a new buffer to be processed.
   * If false is returned then the same buffer will be passed to the next call. An implementation of
   * this method is free to modify the buffer and can assume that the buffer will not be externally
   * modified between successive calls. Hence an implementation can, for example, modify the
   * buffer's position to keep track of how much of the data it has processed.
   * <p>
   * Note that the first call to this method following a call to
   * {@link #onPositionReset(long, boolean)} will always receive a new {@link ByteBuffer} to be
   * processed.
   *
   * @param positionUs The current media time in microseconds, measured at the start of the
   *     current iteration of the rendering loop.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   * @param codec The {@link MediaCodec} instance.
   * @param buffer The output buffer to process.
   * @param bufferIndex The index of the output buffer.
   * @param bufferFlags The flags attached to the output buffer.
   * @param bufferPresentationTimeUs The presentation time of the output buffer in microseconds.
   * @param shouldSkip Whether the buffer should be skipped (i.e. not rendered).
   *
   * @return Whether the output buffer was fully processed (e.g. rendered or skipped).
   * @throws ExoPlaybackException If an error occurs processing the output buffer.
   */
  protected abstract boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs,
      MediaCodec codec, ByteBuffer buffer, int bufferIndex, int bufferFlags,
      long bufferPresentationTimeUs, boolean shouldSkip) throws ExoPlaybackException;

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
    if (codecReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM) {
      // We're waiting to re-initialize the codec, and have now processed all final buffers.
      releaseCodec();
      maybeInitCodec();
    } else {
      outputStreamEnded = true;
      renderToEndOfStream();
    }
  }

  private boolean shouldSkipOutputBuffer(long presentationTimeUs) {
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

  private static MediaCodec.CryptoInfo getFrameworkCryptoInfo(
      DecoderInputBuffer buffer, int adaptiveReconfigurationBytes) {
    MediaCodec.CryptoInfo cryptoInfo = buffer.cryptoInfo.getFrameworkCryptoInfoV16();
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

  /**
   * Returns whether the device needs keys to have been loaded into the {@link DrmSession} before
   * codec configuration.
   */
  private boolean deviceNeedsDrmKeysToConfigureCodecWorkaround() {
    return "Amazon".equals(Util.MANUFACTURER)
        && ("AFTM".equals(Util.MODEL) // Fire TV Stick Gen 1
            || "AFTB".equals(Util.MODEL)); // Fire TV Gen 1
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
   * <p>
   * When enabled, the workaround queues and discards a blank frame with a resolution whose width
   * and height both equal {@link #ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT}, to reset the codec's
   * internal state when a format change occurs.
   * <p>
   * See [Internal: b/27807182].
   * See <a href="https://github.com/google/ExoPlayer/issues/3257">GitHub issue #3257</a>.
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
   * Returns whether the decoder is an H.264/AVC decoder known to fail if NAL units are queued
   * before the codec specific data.
   * <p>
   * If true is returned, the renderer will work around the issue by discarding data up to the SPS.
   *
   * @param name The name of the decoder.
   * @param format The format used to configure the decoder.
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
    return (Util.SDK_INT <= 17
            && ("OMX.rk.video_decoder.avc".equals(name)
                || "OMX.allwinner.video.decoder.avc".equals(name)))
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
        || (Util.SDK_INT <= 19 && "hb2000".equals(Util.DEVICE)
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
   * Returns whether the decoder is known to set the number of audio channels in the output format
   * to 2 for the given input format, whilst only actually outputting a single channel.
   * <p>
   * If true is returned then we explicitly override the number of channels in the output format,
   * setting it to 1.
   *
   * @param name The decoder name.
   * @param format The input format.
   * @return True if the decoder is known to set the number of audio channels in the output format
   *     to 2 for the given input format, whilst only actually outputting a single channel. False
   *     otherwise.
   */
  private static boolean codecNeedsMonoChannelCountWorkaround(String name, Format format) {
    return Util.SDK_INT <= 18 && format.channelCount == 1
        && "OMX.MTK.AUDIO.DECODER.MP3".equals(name);
  }

}
