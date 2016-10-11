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

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodec.CodecException;
import android.media.MediaCodec.CryptoException;
import android.media.MediaCrypto;
import android.os.Handler;
import android.os.SystemClock;
import org.telegram.messenger.exoplayer.MediaCodecUtil.DecoderQueryException;
import org.telegram.messenger.exoplayer.drm.DrmInitData;
import org.telegram.messenger.exoplayer.drm.DrmSessionManager;
import org.telegram.messenger.exoplayer.drm.FrameworkMediaCrypto;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.NalUnitUtil;
import org.telegram.messenger.exoplayer.util.TraceUtil;
import org.telegram.messenger.exoplayer.util.Util;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * An abstract {@link TrackRenderer} that uses {@link MediaCodec} to decode samples for rendering.
 */
@TargetApi(16)
public abstract class MediaCodecTrackRenderer extends SampleSourceTrackRenderer {

  /**
   * Interface definition for a callback to be notified of {@link MediaCodecTrackRenderer} events.
   */
  public interface EventListener {

    /**
     * Invoked when a decoder fails to initialize.
     *
     * @param e The corresponding exception.
     */
    void onDecoderInitializationError(DecoderInitializationException e);

    /**
     * Invoked when a decoder operation raises a {@link CryptoException}.
     *
     * @param e The corresponding exception.
     */
    void onCryptoError(CryptoException e);

    /**
     * Invoked when a decoder is successfully created.
     *
     * @param decoderName The decoder that was configured and created.
     * @param elapsedRealtimeMs {@code elapsedRealtime} timestamp of when the initialization
     *    finished.
     * @param initializationDurationMs Amount of time taken to initialize the decoder.
     */
    void onDecoderInitialized(String decoderName, long elapsedRealtimeMs,
        long initializationDurationMs);

  }

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

    public DecoderInitializationException(MediaFormat mediaFormat, Throwable cause,
        boolean secureDecoderRequired, int errorCode) {
      super("Decoder init failed: [" + errorCode + "], " + mediaFormat, cause);
      this.mimeType = mediaFormat.mimeType;
      this.secureDecoderRequired = secureDecoderRequired;
      this.decoderName = null;
      this.diagnosticInfo = buildCustomDiagnosticInfo(errorCode);
    }

    public DecoderInitializationException(MediaFormat mediaFormat, Throwable cause,
        boolean secureDecoderRequired, String decoderName) {
      super("Decoder init failed: " + decoderName + ", " + mediaFormat, cause);
      this.mimeType = mediaFormat.mimeType;
      this.secureDecoderRequired = secureDecoderRequired;
      this.decoderName = decoderName;
      this.diagnosticInfo = Util.SDK_INT >= 21 ? getDiagnosticInfoV21(cause) : null;
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
      return "org.telegram.messenger.exoplayer.MediaCodecTrackRenderer_" + sign + Math.abs(errorCode);
    }

  }

  /**
   * Value returned by {@link #getSourceState()} when the source is not ready.
   */
  protected static final int SOURCE_STATE_NOT_READY = 0;
  /**
   * Value returned by {@link #getSourceState()} when the source is ready and we're able to read
   * from it.
   */
  protected static final int SOURCE_STATE_READY = 1;
  /**
   * Value returned by {@link #getSourceState()} when the source is ready but we might not be able
   * to read from it. We transition to this state when an attempt to read a sample fails despite the
   * source reporting that samples are available. This can occur when the next sample to be provided
   * by the source is for another renderer.
   */
  protected static final int SOURCE_STATE_READY_READ_MAY_FAIL = 2;

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

  /**
   * H.264/AVC buffer to queue when using the adaptation workaround (see
   * {@link #codecNeedsAdaptationWorkaround(String)}. Consists of three NAL units with start codes:
   * Baseline sequence/picture parameter sets and a 32 * 32 pixel IDR slice. This stream can be
   * queued to force a resolution change when adapting to a new format.
   */
  private static final byte[] ADAPTATION_WORKAROUND_BUFFER = Util.getBytesFromHexString(
      "0000016742C00BDA259000000168CE0F13200000016588840DCE7118A0002FBF1C31C3275D78");
  private static final int ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT = 32;

  public final CodecCounters codecCounters;

  private final MediaCodecSelector mediaCodecSelector;
  private final DrmSessionManager<FrameworkMediaCrypto> drmSessionManager;
  private final boolean playClearSamplesWithoutKeys;
  private final SampleHolder sampleHolder;
  private final MediaFormatHolder formatHolder;
  private final List<Long> decodeOnlyPresentationTimestamps;
  private final MediaCodec.BufferInfo outputBufferInfo;
  private final EventListener eventListener;
  private final boolean deviceNeedsAutoFrcWorkaround;
  protected final Handler eventHandler;

  private MediaFormat format;
  private DrmInitData drmInitData;
  private MediaCodec codec;
  private boolean codecIsAdaptive;
  private boolean codecNeedsDiscardToSpsWorkaround;
  private boolean codecNeedsFlushWorkaround;
  private boolean codecNeedsAdaptationWorkaround;
  private boolean codecNeedsEosPropagationWorkaround;
  private boolean codecNeedsEosFlushWorkaround;
  private boolean codecNeedsMonoChannelCountWorkaround;
  private boolean codecNeedsAdaptationWorkaroundBuffer;
  private boolean shouldSkipAdaptationWorkaroundOutputBuffer;
  private ByteBuffer[] inputBuffers;
  private ByteBuffer[] outputBuffers;
  private long codecHotswapTimeMs;
  private int inputIndex;
  private int outputIndex;
  private boolean openedDrmSession;
  private boolean codecReconfigured;
  private int codecReconfigurationState;
  private int codecReinitializationState;
  private boolean codecReceivedBuffers;
  private boolean codecReceivedEos;

  private int sourceState;
  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  private boolean waitingForKeys;
  private boolean waitingForFirstSyncFrame;

  /**
   * @param source The upstream source from which the renderer obtains samples.
   * @param mediaCodecSelector A decoder selector.
   * @param drmSessionManager For use with encrypted media. May be null if support for encrypted
   *     media is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisition. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public MediaCodecTrackRenderer(SampleSource source, MediaCodecSelector mediaCodecSelector,
      DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys, Handler eventHandler, EventListener eventListener) {
    this (new SampleSource[] {source}, mediaCodecSelector, drmSessionManager,
        playClearSamplesWithoutKeys, eventHandler, eventListener);
  }

  /**
   * @param sources The upstream sources from which the renderer obtains samples.
   * @param mediaCodecSelector A decoder selector.
   * @param drmSessionManager For use with encrypted media. May be null if support for encrypted
   *     media is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisition. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public MediaCodecTrackRenderer(SampleSource[] sources, MediaCodecSelector mediaCodecSelector,
      DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys, Handler eventHandler, EventListener eventListener) {
    super(sources);
    Assertions.checkState(Util.SDK_INT >= 16);
    this.mediaCodecSelector = Assertions.checkNotNull(mediaCodecSelector);
    this.drmSessionManager = drmSessionManager;
    this.playClearSamplesWithoutKeys = playClearSamplesWithoutKeys;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    deviceNeedsAutoFrcWorkaround = deviceNeedsAutoFrcWorkaround();
    codecCounters = new CodecCounters();
    sampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_DISABLED);
    formatHolder = new MediaFormatHolder();
    decodeOnlyPresentationTimestamps = new ArrayList<>();
    outputBufferInfo = new MediaCodec.BufferInfo();
    codecReconfigurationState = RECONFIGURATION_STATE_NONE;
    codecReinitializationState = REINITIALIZATION_STATE_NONE;
  }

  @Override
  protected final boolean handlesTrack(MediaFormat mediaFormat) throws DecoderQueryException {
    return handlesTrack(mediaCodecSelector, mediaFormat);
  }

  /**
   * Returns whether this renderer is capable of handling the provided track.
   *
   * @param mediaCodecSelector The decoder selector.
   * @param mediaFormat The format of the track.
   * @return True if the renderer can handle the track, false otherwise.
   * @throws DecoderQueryException Thrown if there was an error querying decoders.
   */
  protected abstract boolean handlesTrack(MediaCodecSelector mediaCodecSelector,
      MediaFormat mediaFormat) throws DecoderQueryException;

  /**
   * Returns a {@link DecoderInfo} for a given format.
   *
   * @param mediaCodecSelector The decoder selector.
   * @param mimeType The mime type for which a decoder is required.
   * @param requiresSecureDecoder Whether a secure decoder is required.
   * @return A {@link DecoderInfo} describing the decoder to instantiate, or null if no suitable
   *     decoder exists.
   * @throws DecoderQueryException Thrown if there was an error querying decoders.
   */
  protected DecoderInfo getDecoderInfo(MediaCodecSelector mediaCodecSelector, String mimeType,
      boolean requiresSecureDecoder) throws DecoderQueryException {
    return mediaCodecSelector.getDecoderInfo(mimeType, requiresSecureDecoder);
  }

  /**
   * Configures a newly created {@link MediaCodec}.
   *
   * @param codec The {@link MediaCodec} to configure.
   * @param codecIsAdaptive Whether the codec is adaptive.
   * @param format The format for which the codec is being configured.
   * @param crypto For drm protected playbacks, a {@link MediaCrypto} to use for decryption.
   */
  protected abstract void configureCodec(MediaCodec codec, boolean codecIsAdaptive,
      android.media.MediaFormat format, MediaCrypto crypto);

  @SuppressWarnings("deprecation")
  protected final void maybeInitCodec() throws ExoPlaybackException {
    if (!shouldInitCodec()) {
      return;
    }

    String mimeType = format.mimeType;
    MediaCrypto mediaCrypto = null;
    boolean requiresSecureDecoder = false;
    if (drmInitData != null) {
      if (drmSessionManager == null) {
        throw new ExoPlaybackException("Media requires a DrmSessionManager");
      }
      if (!openedDrmSession) {
        drmSessionManager.open(drmInitData);
        openedDrmSession = true;
      }
      int drmSessionState = drmSessionManager.getState();
      if (drmSessionState == DrmSessionManager.STATE_ERROR) {
        throw new ExoPlaybackException(drmSessionManager.getError());
      } else if (drmSessionState == DrmSessionManager.STATE_OPENED
          || drmSessionState == DrmSessionManager.STATE_OPENED_WITH_KEYS) {
        mediaCrypto = drmSessionManager.getMediaCrypto().getWrappedMediaCrypto();
        requiresSecureDecoder = drmSessionManager.requiresSecureDecoderComponent(mimeType);
      } else {
        // The drm session isn't open yet.
        return;
      }
    }

    DecoderInfo decoderInfo = null;
    try {
      decoderInfo = getDecoderInfo(mediaCodecSelector, mimeType, requiresSecureDecoder);
    } catch (DecoderQueryException e) {
      notifyAndThrowDecoderInitError(new DecoderInitializationException(format, e,
          requiresSecureDecoder, DecoderInitializationException.DECODER_QUERY_ERROR));
    }

    if (decoderInfo == null) {
      notifyAndThrowDecoderInitError(new DecoderInitializationException(format, null,
          requiresSecureDecoder, DecoderInitializationException.NO_SUITABLE_DECODER_ERROR));
    }

    String codecName = decoderInfo.name;
    codecIsAdaptive = decoderInfo.adaptive;
    codecNeedsDiscardToSpsWorkaround = codecNeedsDiscardToSpsWorkaround(codecName, format);
    codecNeedsFlushWorkaround = codecNeedsFlushWorkaround(codecName);
    codecNeedsAdaptationWorkaround = codecNeedsAdaptationWorkaround(codecName);
    codecNeedsEosPropagationWorkaround = codecNeedsEosPropagationWorkaround(codecName);
    codecNeedsEosFlushWorkaround = codecNeedsEosFlushWorkaround(codecName);
    codecNeedsMonoChannelCountWorkaround = codecNeedsMonoChannelCountWorkaround(codecName, format);
    try {
      long codecInitializingTimestamp = SystemClock.elapsedRealtime();
      TraceUtil.beginSection("createByCodecName(" + codecName + ")");
      codec = MediaCodec.createByCodecName(codecName);
      TraceUtil.endSection();
      TraceUtil.beginSection("configureCodec");
      configureCodec(codec, decoderInfo.adaptive, getFrameworkMediaFormat(format), mediaCrypto);
      TraceUtil.endSection();
      TraceUtil.beginSection("codec.start()");
      codec.start();
      TraceUtil.endSection();
      long codecInitializedTimestamp = SystemClock.elapsedRealtime();
      notifyDecoderInitialized(codecName, codecInitializedTimestamp,
          codecInitializedTimestamp - codecInitializingTimestamp);
      inputBuffers = codec.getInputBuffers();
      outputBuffers = codec.getOutputBuffers();
    } catch (Exception e) {
      notifyAndThrowDecoderInitError(new DecoderInitializationException(format, e,
          requiresSecureDecoder, codecName));
    }
    codecHotswapTimeMs = getState() == TrackRenderer.STATE_STARTED ?
        SystemClock.elapsedRealtime() : -1;
    inputIndex = -1;
    outputIndex = -1;
    waitingForFirstSyncFrame = true;
    codecCounters.codecInitCount++;
  }

  private void notifyAndThrowDecoderInitError(DecoderInitializationException e)
      throws ExoPlaybackException {
    notifyDecoderInitializationError(e);
    throw new ExoPlaybackException(e);
  }

  protected boolean shouldInitCodec() {
    return codec == null && format != null;
  }

  protected final boolean codecInitialized() {
    return codec != null;
  }

  protected final boolean haveFormat() {
    return format != null;
  }

  @Override
  protected void onDisabled() throws ExoPlaybackException {
    format = null;
    drmInitData = null;
    try {
      releaseCodec();
    } finally {
      try {
        if (openedDrmSession) {
          drmSessionManager.close();
          openedDrmSession = false;
        }
      } finally {
        super.onDisabled();
      }
    }
  }

  protected void releaseCodec() {
    if (codec != null) {
      codecHotswapTimeMs = -1;
      inputIndex = -1;
      outputIndex = -1;
      waitingForKeys = false;
      decodeOnlyPresentationTimestamps.clear();
      inputBuffers = null;
      outputBuffers = null;
      codecReconfigured = false;
      codecReceivedBuffers = false;
      codecIsAdaptive = false;
      codecNeedsDiscardToSpsWorkaround = false;
      codecNeedsFlushWorkaround = false;
      codecNeedsAdaptationWorkaround = false;
      codecNeedsEosPropagationWorkaround = false;
      codecNeedsEosFlushWorkaround = false;
      codecNeedsMonoChannelCountWorkaround = false;
      codecNeedsAdaptationWorkaroundBuffer = false;
      shouldSkipAdaptationWorkaroundOutputBuffer = false;
      codecReceivedEos = false;
      codecReconfigurationState = RECONFIGURATION_STATE_NONE;
      codecReinitializationState = REINITIALIZATION_STATE_NONE;
      codecCounters.codecReleaseCount++;
      try {
        codec.stop();
      } finally {
        try {
          codec.release();
        } finally {
          codec = null;
        }
      }
    }
  }

  @Override
  protected void onDiscontinuity(long positionUs) throws ExoPlaybackException {
    sourceState = SOURCE_STATE_NOT_READY;
    inputStreamEnded = false;
    outputStreamEnded = false;
    if (codec != null) {
      flushCodec();
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
  protected void doSomeWork(long positionUs, long elapsedRealtimeUs, boolean sourceIsReady)
      throws ExoPlaybackException {
    sourceState = sourceIsReady
        ? (sourceState == SOURCE_STATE_NOT_READY ? SOURCE_STATE_READY : sourceState)
        : SOURCE_STATE_NOT_READY;
    if (format == null) {
      readFormat(positionUs);
    }
    maybeInitCodec();
    if (codec != null) {
      TraceUtil.beginSection("drainAndFeed");
      while (drainOutputBuffer(positionUs, elapsedRealtimeUs)) {}
      if (feedInputBuffer(positionUs, true)) {
        while (feedInputBuffer(positionUs, false)) {}
      }
      TraceUtil.endSection();
    }
    codecCounters.ensureUpdated();
  }

  private void readFormat(long positionUs) throws ExoPlaybackException {
    int result = readSource(positionUs, formatHolder, null);
    if (result == SampleSource.FORMAT_READ) {
      onInputFormatChanged(formatHolder);
    }
  }

  protected void flushCodec() throws ExoPlaybackException {
    codecHotswapTimeMs = -1;
    inputIndex = -1;
    outputIndex = -1;
    waitingForFirstSyncFrame = true;
    waitingForKeys = false;
    decodeOnlyPresentationTimestamps.clear();
    codecNeedsAdaptationWorkaroundBuffer = false;
    shouldSkipAdaptationWorkaroundOutputBuffer = false;
    if (codecNeedsFlushWorkaround || (codecNeedsEosFlushWorkaround && codecReceivedEos)) {
      // Workaround framework bugs. See [Internal: b/8347958, b/8578467, b/8543366, b/23361053].
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

  /**
   * @param positionUs The current media time in microseconds, measured at the start of the
   *     current iteration of the rendering loop.
   * @param firstFeed True if this is the first call to this method from the current invocation of
   *     {@link #doSomeWork(long, long)}. False otherwise.
   * @return True if it may be possible to feed more input data. False otherwise.
   * @throws ExoPlaybackException If an error occurs feeding the input buffer.
   */
  private boolean feedInputBuffer(long positionUs, boolean firstFeed) throws ExoPlaybackException {
    if (inputStreamEnded
        || codecReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM) {
      // The input stream has ended, or we need to re-initialize the codec but are still waiting
      // for the existing codec to output any final output buffers.
      return false;
    }

    if (inputIndex < 0) {
      inputIndex = codec.dequeueInputBuffer(0);
      if (inputIndex < 0) {
        return false;
      }
      sampleHolder.data = inputBuffers[inputIndex];
      sampleHolder.clearData();
    }

    if (codecReinitializationState == REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM) {
      // We need to re-initialize the codec. Send an end of stream signal to the existing codec so
      // that it outputs any remaining buffers before we release it.
      if (codecNeedsEosPropagationWorkaround) {
        // Do nothing.
      } else {
        codecReceivedEos = true;
        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        inputIndex = -1;
      }
      codecReinitializationState = REINITIALIZATION_STATE_WAIT_END_OF_STREAM;
      return false;
    }

    if (codecNeedsAdaptationWorkaroundBuffer) {
      codecNeedsAdaptationWorkaroundBuffer = false;
      sampleHolder.data.put(ADAPTATION_WORKAROUND_BUFFER);
      codec.queueInputBuffer(inputIndex, 0, ADAPTATION_WORKAROUND_BUFFER.length, 0, 0);
      inputIndex = -1;
      codecReceivedBuffers = true;
      return true;
    }

    int result;
    if (waitingForKeys) {
      // We've already read an encrypted sample into sampleHolder, and are waiting for keys.
      result = SampleSource.SAMPLE_READ;
    } else {
      // For adaptive reconfiguration OMX decoders expect all reconfiguration data to be supplied
      // at the start of the buffer that also contains the first frame in the new format.
      if (codecReconfigurationState == RECONFIGURATION_STATE_WRITE_PENDING) {
        for (int i = 0; i < format.initializationData.size(); i++) {
          byte[] data = format.initializationData.get(i);
          sampleHolder.data.put(data);
        }
        codecReconfigurationState = RECONFIGURATION_STATE_QUEUE_PENDING;
      }
      result = readSource(positionUs, formatHolder, sampleHolder);
      if (firstFeed && sourceState == SOURCE_STATE_READY && result == SampleSource.NOTHING_READ) {
        sourceState = SOURCE_STATE_READY_READ_MAY_FAIL;
      }
    }

    if (result == SampleSource.NOTHING_READ) {
      return false;
    }
    if (result == SampleSource.FORMAT_READ) {
      if (codecReconfigurationState == RECONFIGURATION_STATE_QUEUE_PENDING) {
        // We received two formats in a row. Clear the current buffer of any reconfiguration data
        // associated with the first format.
        sampleHolder.clearData();
        codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
      }
      onInputFormatChanged(formatHolder);
      return true;
    }
    if (result == SampleSource.END_OF_STREAM) {
      if (codecReconfigurationState == RECONFIGURATION_STATE_QUEUE_PENDING) {
        // We received a new format immediately before the end of the stream. We need to clear
        // the corresponding reconfiguration data from the current buffer, but re-write it into
        // a subsequent buffer if there are any (e.g. if the user seeks backwards).
        sampleHolder.clearData();
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
          inputIndex = -1;
        }
      } catch (CryptoException e) {
        notifyCryptoError(e);
        throw new ExoPlaybackException(e);
      }
      return false;
    }
    if (waitingForFirstSyncFrame) {
      // TODO: Find out if it's possible to supply samples prior to the first sync
      // frame for HE-AAC.
      if (!sampleHolder.isSyncFrame()) {
        sampleHolder.clearData();
        if (codecReconfigurationState == RECONFIGURATION_STATE_QUEUE_PENDING) {
          // The buffer we just cleared contained reconfiguration data. We need to re-write this
          // data into a subsequent buffer (if there is one).
          codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
        }
        return true;
      }
      waitingForFirstSyncFrame = false;
    }
    boolean sampleEncrypted = sampleHolder.isEncrypted();
    waitingForKeys = shouldWaitForKeys(sampleEncrypted);
    if (waitingForKeys) {
      return false;
    }
    if (codecNeedsDiscardToSpsWorkaround && !sampleEncrypted) {
      NalUnitUtil.discardToSps(sampleHolder.data);
      if (sampleHolder.data.position() == 0) {
        return true;
      }
      codecNeedsDiscardToSpsWorkaround = false;
    }
    try {
      int bufferSize = sampleHolder.data.position();
      int adaptiveReconfigurationBytes = bufferSize - sampleHolder.size;
      long presentationTimeUs = sampleHolder.timeUs;
      if (sampleHolder.isDecodeOnly()) {
        decodeOnlyPresentationTimestamps.add(presentationTimeUs);
      }

      onQueuedInputBuffer(presentationTimeUs, sampleHolder.data, bufferSize, sampleEncrypted);

      if (sampleEncrypted) {
        MediaCodec.CryptoInfo cryptoInfo = getFrameworkCryptoInfo(sampleHolder,
            adaptiveReconfigurationBytes);
        codec.queueSecureInputBuffer(inputIndex, 0, cryptoInfo, presentationTimeUs, 0);
      } else {
        codec.queueInputBuffer(inputIndex, 0, bufferSize, presentationTimeUs, 0);
      }
      inputIndex = -1;
      codecReceivedBuffers = true;
      codecReconfigurationState = RECONFIGURATION_STATE_NONE;
      codecCounters.inputBufferCount++;
    } catch (CryptoException e) {
      notifyCryptoError(e);
      throw new ExoPlaybackException(e);
    }
    return true;
  }

  private static MediaCodec.CryptoInfo getFrameworkCryptoInfo(SampleHolder sampleHolder,
      int adaptiveReconfigurationBytes) {
    MediaCodec.CryptoInfo cryptoInfo = sampleHolder.cryptoInfo.getFrameworkCryptoInfoV16();
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

  private android.media.MediaFormat getFrameworkMediaFormat(MediaFormat format) {
    android.media.MediaFormat mediaFormat = format.getFrameworkMediaFormatV16();
    if (deviceNeedsAutoFrcWorkaround) {
      mediaFormat.setInteger("auto-frc", 0);
    }
    return mediaFormat;
  }

  private boolean shouldWaitForKeys(boolean sampleEncrypted) throws ExoPlaybackException {
    if (!openedDrmSession) {
      return false;
    }
    int drmManagerState = drmSessionManager.getState();
    if (drmManagerState == DrmSessionManager.STATE_ERROR) {
      throw new ExoPlaybackException(drmSessionManager.getError());
    }
    if (drmManagerState != DrmSessionManager.STATE_OPENED_WITH_KEYS &&
        (sampleEncrypted || !playClearSamplesWithoutKeys)) {
      return true;
    }
    return false;
  }

  /**
   * Invoked when a new format is read from the upstream {@link SampleSource}.
   *
   * @param formatHolder Holds the new format.
   * @throws ExoPlaybackException If an error occurs reinitializing the {@link MediaCodec}.
   */
  protected void onInputFormatChanged(MediaFormatHolder formatHolder) throws ExoPlaybackException {
    MediaFormat oldFormat = format;
    format = formatHolder.format;
    drmInitData = formatHolder.drmInitData;
    if (codec != null && canReconfigureCodec(codec, codecIsAdaptive, oldFormat, format)) {
      codecReconfigured = true;
      codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
      codecNeedsAdaptationWorkaroundBuffer = codecNeedsAdaptationWorkaround
          && format.width == oldFormat.width && format.height == oldFormat.height;
    } else {
      if (codecReceivedBuffers) {
        // Signal end of stream and wait for any final output buffers before re-initialization.
        codecReinitializationState = REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM;
      } else {
        // There aren't any final output buffers, so perform re-initialization immediately.
        releaseCodec();
        maybeInitCodec();
      }
    }
  }

  /**
   * Invoked when the output format of the {@link MediaCodec} changes.
   * <p>
   * The default implementation is a no-op.
   *
   * @param codec The {@link MediaCodec} instance.
   * @param outputFormat The new output format.
   * @throws ExoPlaybackException If an error occurs on output format change.
   */
  protected void onOutputFormatChanged(MediaCodec codec, android.media.MediaFormat outputFormat)
      throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Invoked when the output stream ends, meaning that the last output buffer has been processed
   * and the {@link MediaCodec#BUFFER_FLAG_END_OF_STREAM} flag has been propagated through the
   * decoder.
   * <p>
   * The default implementation is a no-op.
   */
  protected void onOutputStreamEnded() {
    // Do nothing.
  }

  /**
   * Invoked immediately before an input buffer is queued into the codec.
   * <p>
   * The default implementation is a no-op.
   *
   * @param presentationTimeUs The timestamp associated with the input buffer.
   * @param buffer The buffer to be queued.
   * @param bufferSize the size of the sample data stored in the buffer.
   * @param sampleEncrypted Whether the sample data is encrypted.
   */
  protected void onQueuedInputBuffer(
      long presentationTimeUs, ByteBuffer buffer, int bufferSize, boolean sampleEncrypted) {
    // Do nothing.
  }

  /**
   * Invoked when an output buffer is successfully processed.
   * <p>
   * The default implementation is a no-op.
   *
   * @param presentationTimeUs The timestamp associated with the output buffer.
   */
  protected void onProcessedOutputBuffer(long presentationTimeUs) {
    // Do nothing.
  }

  /**
   * Determines whether the existing {@link MediaCodec} should be reconfigured for a new format by
   * sending codec specific initialization data at the start of the next input buffer. If true is
   * returned then the {@link MediaCodec} instance will be reconfigured in this way. If false is
   * returned then the instance will be released, and a new instance will be created for the new
   * format.
   * <p>
   * The default implementation returns false.
   *
   * @param codec The existing {@link MediaCodec} instance.
   * @param codecIsAdaptive Whether the codec is adaptive.
   * @param oldFormat The format for which the existing instance is configured.
   * @param newFormat The new format.
   * @return True if the existing instance can be reconfigured. False otherwise.
   */
  protected boolean canReconfigureCodec(MediaCodec codec, boolean codecIsAdaptive,
      MediaFormat oldFormat, MediaFormat newFormat) {
    return false;
  }

  @Override
  protected boolean isEnded() {
    return outputStreamEnded;
  }

  @Override
  protected boolean isReady() {
    return format != null && !waitingForKeys
        && (sourceState != SOURCE_STATE_NOT_READY || outputIndex >= 0 || isWithinHotswapPeriod());
  }

  /**
   * Gets the source state.
   *
   * @return One of {@link #SOURCE_STATE_NOT_READY}, {@link #SOURCE_STATE_READY} and
   *     {@link #SOURCE_STATE_READY_READ_MAY_FAIL}.
   */
  protected final int getSourceState() {
    return sourceState;
  }

  private boolean isWithinHotswapPeriod() {
    return SystemClock.elapsedRealtime() < codecHotswapTimeMs + MAX_CODEC_HOTSWAP_TIME_MS;
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
   * @return True if it may be possible to drain more output data. False otherwise.
   * @throws ExoPlaybackException If an error occurs draining the output buffer.
   */
  @SuppressWarnings("deprecation")
  private boolean drainOutputBuffer(long positionUs, long elapsedRealtimeUs)
      throws ExoPlaybackException {
    if (outputStreamEnded) {
      return false;
    }

    if (outputIndex < 0) {
      outputIndex = codec.dequeueOutputBuffer(outputBufferInfo, getDequeueOutputBufferTimeoutUs());
    }

    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
      processOutputFormat();
      return true;
    } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
      outputBuffers = codec.getOutputBuffers();
      codecCounters.outputBuffersChangedCount++;
      return true;
    } else if (outputIndex < 0) {
      if (codecNeedsEosPropagationWorkaround && (inputStreamEnded
          || codecReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM)) {
        processEndOfStream();
        return true;
      }
      return false;
    }

    if (shouldSkipAdaptationWorkaroundOutputBuffer) {
      shouldSkipAdaptationWorkaroundOutputBuffer = false;
      codec.releaseOutputBuffer(outputIndex, false);
      outputIndex = -1;
      return true;
    }

    if ((outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
      processEndOfStream();
      return false;
    }

    int decodeOnlyIndex = getDecodeOnlyIndex(outputBufferInfo.presentationTimeUs);
    if (processOutputBuffer(positionUs, elapsedRealtimeUs, codec, outputBuffers[outputIndex],
        outputBufferInfo, outputIndex, decodeOnlyIndex != -1)) {
      onProcessedOutputBuffer(outputBufferInfo.presentationTimeUs);
      if (decodeOnlyIndex != -1) {
        decodeOnlyPresentationTimestamps.remove(decodeOnlyIndex);
      }
      outputIndex = -1;
      return true;
    }

    return false;
  }

  /**
   * Processes a new output format.
   *
   * @throws ExoPlaybackException If an error occurs processing the output format.
   */
  private void processOutputFormat() throws ExoPlaybackException {
    android.media.MediaFormat format = codec.getOutputFormat();
    if (codecNeedsAdaptationWorkaround
        && format.getInteger(android.media.MediaFormat.KEY_WIDTH)
            == ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT
        && format.getInteger(android.media.MediaFormat.KEY_HEIGHT)
            == ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT) {
      // We assume this format changed event was caused by the adaptation workaround.
      shouldSkipAdaptationWorkaroundOutputBuffer = true;
      return;
    }
    if (codecNeedsMonoChannelCountWorkaround) {
      format.setInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT, 1);
    }
    onOutputFormatChanged(codec, format);
    codecCounters.outputFormatChangedCount++;
  }

  /**
   * Processes the provided output buffer.
   *
   * @return True if the output buffer was processed (e.g. rendered or discarded) and hence is no
   *     longer required. False otherwise.
   * @throws ExoPlaybackException If an error occurs processing the output buffer.
   */
  protected abstract boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs,
      MediaCodec codec, ByteBuffer buffer, MediaCodec.BufferInfo bufferInfo, int bufferIndex,
      boolean shouldSkip) throws ExoPlaybackException;

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
      onOutputStreamEnded();
    }
  }

  private void notifyDecoderInitializationError(final DecoderInitializationException e) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onDecoderInitializationError(e);
        }
      });
    }
  }

  private void notifyCryptoError(final CryptoException e) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onCryptoError(e);
        }
      });
    }
  }

  private void notifyDecoderInitialized(final String decoderName,
      final long initializedTimestamp, final long initializationDuration) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable() {
        @Override
        public void run() {
          eventListener.onDecoderInitialized(decoderName, initializedTimestamp,
              initializationDuration);
        }
      });
    }
  }

  private int getDecodeOnlyIndex(long presentationTimeUs) {
    final int size = decodeOnlyPresentationTimestamps.size();
    for (int i = 0; i < size; i++) {
      if (decodeOnlyPresentationTimestamps.get(i).longValue() == presentationTimeUs) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns whether the decoder is known to fail when flushed.
   * <p>
   * If true is returned, the renderer will work around the issue by releasing the decoder and
   * instantiating a new one rather than flushing the current instance.
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
   * Returns whether the decoder is known to get stuck during some adaptations where the resolution
   * does not change.
   * <p>
   * If true is returned, the renderer will work around the issue by queueing and discarding a blank
   * frame at a different resolution, which resets the codec's internal state.
   * <p>
   * See [Internal: b/27807182].
   *
   * @param name The name of the decoder.
   * @return True if the decoder is known to get stuck during some adaptations.
   */
  private static boolean codecNeedsAdaptationWorkaround(String name) {
    return Util.SDK_INT < 24
        && ("OMX.Nvidia.h264.decode".equals(name) || "OMX.Nvidia.h264.decode.secure".equals(name))
        && (Util.DEVICE.equals("flounder") || Util.DEVICE.equals("flounder_lte")
            || Util.DEVICE.equals("grouper") || Util.DEVICE.equals("tilapia"));
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
  private static boolean codecNeedsDiscardToSpsWorkaround(String name, MediaFormat format) {
    return Util.SDK_INT < 21 && format.initializationData.isEmpty()
        && "OMX.MTK.VIDEO.DECODER.AVC".equals(name);
  }

  /**
   * Returns whether the decoder is known to handle the propagation of the
   * {@link MediaCodec#BUFFER_FLAG_END_OF_STREAM} flag incorrectly on the host device.
   * <p>
   * If true is returned, the renderer will work around the issue by approximating end of stream
   * behavior without relying on the flag being propagated through to an output buffer by the
   * underlying decoder.
   *
   * @param name The name of the decoder.
   * @return True if the decoder is known to handle {@link MediaCodec#BUFFER_FLAG_END_OF_STREAM}
   *     propagation incorrectly on the host device. False otherwise.
   */
  private static boolean codecNeedsEosPropagationWorkaround(String name) {
    return Util.SDK_INT <= 17 && ("OMX.rk.video_decoder.avc".equals(name)
        || "OMX.allwinner.video.decoder.avc".equals(name));
  }

  /**
   * Returns whether the decoder is known to behave incorrectly if flushed after receiving an input
   * buffer with {@link MediaCodec#BUFFER_FLAG_END_OF_STREAM} set.
   * <p>
   * If true is returned, the renderer will work around the issue by instantiating a new decoder
   * when this case occurs.
   *
   * @param name The name of the decoder.
   * @return True if the decoder is known to behave incorrectly if flushed after receiving an input
   *     buffer with {@link MediaCodec#BUFFER_FLAG_END_OF_STREAM} set. False otherwise.
   */
  private static boolean codecNeedsEosFlushWorkaround(String name) {
    return Util.SDK_INT <= 23 && "OMX.google.vorbis.decoder".equals(name);
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
   * @return True if the device is known to set the number of audio channels in the output format
   *     to 2 for the given input format, whilst only actually outputting a single channel. False
   *     otherwise.
   */
  private static boolean codecNeedsMonoChannelCountWorkaround(String name, MediaFormat format) {
    return Util.SDK_INT <= 18 && format.channelCount == 1
        && "OMX.MTK.AUDIO.DECODER.MP3".equals(name);
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

}
