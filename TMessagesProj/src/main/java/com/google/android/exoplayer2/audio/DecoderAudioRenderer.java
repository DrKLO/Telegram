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
package com.google.android.exoplayer2.audio;

import static com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.DISCARD_REASON_DRM_SESSION_CHANGED;
import static com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.DISCARD_REASON_REUSE_NOT_IMPLEMENTED;
import static com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.REUSE_RESULT_NO;
import static com.google.android.exoplayer2.source.SampleStream.FLAG_REQUIRE_FORMAT;
import static com.google.common.base.MoreObjects.firstNonNull;
import static java.lang.Math.max;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.media.AudioDeviceInfo;
import android.os.Handler;
import android.os.SystemClock;
import androidx.annotation.CallSuper;
import androidx.annotation.DoNotInline;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.PlayerMessage.Target;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.audio.AudioRendererEventListener.EventDispatcher;
import com.google.android.exoplayer2.audio.AudioSink.SinkFormatSupport;
import com.google.android.exoplayer2.decoder.CryptoConfig;
import com.google.android.exoplayer2.decoder.Decoder;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.decoder.DecoderException;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation;
import com.google.android.exoplayer2.decoder.SimpleDecoderOutputBuffer;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.DrmSession.DrmSessionException;
import com.google.android.exoplayer2.source.SampleStream.ReadDataResult;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MediaClock;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.errorprone.annotations.ForOverride;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Decodes and renders audio using a {@link Decoder}.
 *
 * <p>This renderer accepts the following messages sent via {@link ExoPlayer#createMessage(Target)}
 * on the playback thread:
 *
 * <ul>
 *   <li>Message with type {@link #MSG_SET_VOLUME} to set the volume. The message payload should be
 *       a {@link Float} with 0 being silence and 1 being unity gain.
 *   <li>Message with type {@link #MSG_SET_AUDIO_ATTRIBUTES} to set the audio attributes. The
 *       message payload should be an {@link AudioAttributes} instance that will configure the
 *       underlying audio track.
 *   <li>Message with type {@link #MSG_SET_AUX_EFFECT_INFO} to set the auxiliary effect. The message
 *       payload should be an {@link AuxEffectInfo} instance that will configure the underlying
 *       audio track.
 *   <li>Message with type {@link #MSG_SET_SKIP_SILENCE_ENABLED} to enable or disable skipping
 *       silences. The message payload should be a {@link Boolean}.
 *   <li>Message with type {@link #MSG_SET_AUDIO_SESSION_ID} to set the audio session ID. The
 *       message payload should be a session ID {@link Integer} that will be attached to the
 *       underlying audio track.
 * </ul>
 */
public abstract class DecoderAudioRenderer<
        T extends
            Decoder<
                    DecoderInputBuffer,
                    ? extends SimpleDecoderOutputBuffer,
                    ? extends DecoderException>>
    extends BaseRenderer implements MediaClock {

  private static final String TAG = "DecoderAudioRenderer";

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @java.lang.annotation.Target(TYPE_USE)
  @IntDef({
    REINITIALIZATION_STATE_NONE,
    REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM,
    REINITIALIZATION_STATE_WAIT_END_OF_STREAM
  })
  private @interface ReinitializationState {}
  /** The decoder does not need to be re-initialized. */
  private static final int REINITIALIZATION_STATE_NONE = 0;
  /**
   * The input format has changed in a way that requires the decoder to be re-initialized, but we
   * haven't yet signaled an end of stream to the existing decoder. We need to do so in order to
   * ensure that it outputs any remaining buffers before we release it.
   */
  private static final int REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM = 1;
  /**
   * The input format has changed in a way that requires the decoder to be re-initialized, and we've
   * signaled an end of stream to the existing decoder. We're waiting for the decoder to output an
   * end of stream signal to indicate that it has output any remaining buffers before we release it.
   */
  private static final int REINITIALIZATION_STATE_WAIT_END_OF_STREAM = 2;
  /**
   * Generally there is zero or one pending output stream offset. We track more offsets to allow for
   * pending output streams that have fewer frames than the codec latency.
   */
  private static final int MAX_PENDING_OUTPUT_STREAM_OFFSET_COUNT = 10;

  private final EventDispatcher eventDispatcher;
  private final AudioSink audioSink;
  private final DecoderInputBuffer flagsOnlyBuffer;

  private DecoderCounters decoderCounters;
  private Format inputFormat;
  private int encoderDelay;
  private int encoderPadding;

  private boolean experimentalKeepAudioTrackOnSeek;
  private boolean firstStreamSampleRead;

  @Nullable private T decoder;

  @Nullable private DecoderInputBuffer inputBuffer;
  @Nullable private SimpleDecoderOutputBuffer outputBuffer;
  @Nullable private DrmSession decoderDrmSession;
  @Nullable private DrmSession sourceDrmSession;

  private @ReinitializationState int decoderReinitializationState;
  private boolean decoderReceivedBuffers;
  private boolean audioTrackNeedsConfigure;

  private long currentPositionUs;
  private boolean allowFirstBufferPositionDiscontinuity;
  private boolean allowPositionDiscontinuity;
  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  private long outputStreamOffsetUs;
  private final long[] pendingOutputStreamOffsetsUs;
  private int pendingOutputStreamOffsetCount;

  public DecoderAudioRenderer() {
    this(/* eventHandler= */ null, /* eventListener= */ null);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioProcessors Optional {@link AudioProcessor}s that will process audio before output.
   */
  public DecoderAudioRenderer(
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioProcessor... audioProcessors) {
    this(eventHandler, eventListener, /* audioCapabilities= */ null, audioProcessors);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioCapabilities The audio capabilities for playback on this device. Use {@link
   *     AudioCapabilities#DEFAULT_AUDIO_CAPABILITIES} if default capabilities (no encoded audio
   *     passthrough support) should be assumed.
   * @param audioProcessors Optional {@link AudioProcessor}s that will process audio before output.
   */
  public DecoderAudioRenderer(
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioCapabilities audioCapabilities,
      AudioProcessor... audioProcessors) {
    this(
        eventHandler,
        eventListener,
        new DefaultAudioSink.Builder()
            .setAudioCapabilities( // For backward compatibility, null == default.
                firstNonNull(audioCapabilities, AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES))
            .setAudioProcessors(audioProcessors)
            .build());
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioSink The sink to which audio will be output.
   */
  public DecoderAudioRenderer(
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioSink audioSink) {
    super(C.TRACK_TYPE_AUDIO);
    eventDispatcher = new EventDispatcher(eventHandler, eventListener);
    this.audioSink = audioSink;
    audioSink.setListener(new AudioSinkListener());
    flagsOnlyBuffer = DecoderInputBuffer.newNoDataInstance();
    decoderReinitializationState = REINITIALIZATION_STATE_NONE;
    audioTrackNeedsConfigure = true;
    setOutputStreamOffsetUs(C.TIME_UNSET);
    pendingOutputStreamOffsetsUs = new long[MAX_PENDING_OUTPUT_STREAM_OFFSET_COUNT];
  }

  /**
   * Sets whether to enable the experimental feature that keeps and flushes the {@link
   * android.media.AudioTrack} when a seek occurs, as opposed to releasing and reinitialising. Off
   * by default.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release.
   *
   * @param enableKeepAudioTrackOnSeek Whether to keep the {@link android.media.AudioTrack} on seek.
   */
  public void experimentalSetEnableKeepAudioTrackOnSeek(boolean enableKeepAudioTrackOnSeek) {
    this.experimentalKeepAudioTrackOnSeek = enableKeepAudioTrackOnSeek;
  }

  @Override
  @Nullable
  public MediaClock getMediaClock() {
    return this;
  }

  @Override
  public final @Capabilities int supportsFormat(Format format) {
    if (!MimeTypes.isAudio(format.sampleMimeType)) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
    }
    @C.FormatSupport int formatSupport = supportsFormatInternal(format);
    if (formatSupport <= C.FORMAT_UNSUPPORTED_DRM) {
      return RendererCapabilities.create(formatSupport);
    }
    @TunnelingSupport
    int tunnelingSupport = Util.SDK_INT >= 21 ? TUNNELING_SUPPORTED : TUNNELING_NOT_SUPPORTED;
    return RendererCapabilities.create(formatSupport, ADAPTIVE_NOT_SEAMLESS, tunnelingSupport);
  }

  /**
   * Returns the {@link C.FormatSupport} for the given {@link Format}.
   *
   * @param format The format, which has an audio {@link Format#sampleMimeType}.
   * @return The {@link C.FormatSupport} for this {@link Format}.
   */
  @ForOverride
  protected abstract @C.FormatSupport int supportsFormatInternal(Format format);

  /**
   * Returns whether the renderer's {@link AudioSink} supports a given {@link Format}.
   *
   * @see AudioSink#supportsFormat(Format)
   */
  protected final boolean sinkSupportsFormat(Format format) {
    return audioSink.supportsFormat(format);
  }

  /**
   * Returns the level of support that the renderer's {@link AudioSink} provides for a given {@link
   * Format}.
   *
   * @see AudioSink#getFormatSupport(Format) (Format)
   */
  protected final @SinkFormatSupport int getSinkFormatSupport(Format format) {
    return audioSink.getFormatSupport(format);
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (outputStreamEnded) {
      try {
        audioSink.playToEndOfStream();
      } catch (AudioSink.WriteException e) {
        throw createRendererException(
            e, e.format, e.isRecoverable, PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED);
      }
      return;
    }

    // Try and read a format if we don't have one already.
    if (inputFormat == null) {
      // We don't have a format yet, so try and read one.
      FormatHolder formatHolder = getFormatHolder();
      flagsOnlyBuffer.clear();
      @ReadDataResult int result = readSource(formatHolder, flagsOnlyBuffer, FLAG_REQUIRE_FORMAT);
      if (result == C.RESULT_FORMAT_READ) {
        onInputFormatChanged(formatHolder);
      } else if (result == C.RESULT_BUFFER_READ) {
        // End of stream read having not read a format.
        Assertions.checkState(flagsOnlyBuffer.isEndOfStream());
        inputStreamEnded = true;
        try {
          processEndOfStream();
        } catch (AudioSink.WriteException e) {
          throw createRendererException(
              e, /* format= */ null, PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED);
        }
        return;
      } else {
        // We still don't have a format and can't make progress without one.
        return;
      }
    }

    // If we don't have a decoder yet, we need to instantiate one.
    maybeInitDecoder();

    if (decoder != null) {
      try {
        // Rendering loop.
        TraceUtil.beginSection("drainAndFeed");
        while (drainOutputBuffer()) {}
        while (feedInputBuffer()) {}
        TraceUtil.endSection();
      } catch (DecoderException e) {
        // Can happen with dequeueOutputBuffer, dequeueInputBuffer, queueInputBuffer
        Log.e(TAG, "Audio codec error", e);
        eventDispatcher.audioCodecError(e);
        throw createRendererException(e, inputFormat, PlaybackException.ERROR_CODE_DECODING_FAILED);
      } catch (AudioSink.ConfigurationException e) {
        throw createRendererException(
            e, e.format, PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED);
      } catch (AudioSink.InitializationException e) {
        throw createRendererException(
            e, e.format, e.isRecoverable, PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED);
      } catch (AudioSink.WriteException e) {
        throw createRendererException(
            e, e.format, e.isRecoverable, PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED);
      }
      decoderCounters.ensureUpdated();
    }
  }

  /** See {@link AudioSink.Listener#onPositionDiscontinuity()}. */
  @CallSuper
  @ForOverride
  protected void onPositionDiscontinuity() {
    // We are out of sync so allow currentPositionUs to jump backwards.
    allowPositionDiscontinuity = true;
  }

  /**
   * Creates a decoder for the given format.
   *
   * @param format The format for which a decoder is required.
   * @param cryptoConfig The {@link CryptoConfig} object required for decoding encrypted content.
   *     May be null and can be ignored if decoder does not handle encrypted content.
   * @return The decoder.
   * @throws DecoderException If an error occurred creating a suitable decoder.
   */
  @ForOverride
  protected abstract T createDecoder(Format format, @Nullable CryptoConfig cryptoConfig)
      throws DecoderException;

  /**
   * Returns the format of audio buffers output by the decoder. Will not be called until the first
   * output buffer has been dequeued, so the decoder may use input data to determine the format.
   *
   * @param decoder The decoder.
   */
  @ForOverride
  protected abstract Format getOutputFormat(T decoder);

  /**
   * Evaluates whether the existing decoder can be reused for a new {@link Format}.
   *
   * <p>The default implementation does not allow decoder reuse.
   *
   * @param decoderName The name of the decoder.
   * @param oldFormat The previous format.
   * @param newFormat The new format.
   * @return The result of the evaluation.
   */
  @ForOverride
  protected DecoderReuseEvaluation canReuseDecoder(
      String decoderName, Format oldFormat, Format newFormat) {
    return new DecoderReuseEvaluation(
        decoderName, oldFormat, newFormat, REUSE_RESULT_NO, DISCARD_REASON_REUSE_NOT_IMPLEMENTED);
  }

  private boolean drainOutputBuffer()
      throws ExoPlaybackException, DecoderException, AudioSink.ConfigurationException,
          AudioSink.InitializationException, AudioSink.WriteException {
    if (outputBuffer == null) {
      outputBuffer = decoder.dequeueOutputBuffer();
      if (outputBuffer == null) {
        return false;
      }
      if (outputBuffer.skippedOutputBufferCount > 0) {
        decoderCounters.skippedOutputBufferCount += outputBuffer.skippedOutputBufferCount;
        audioSink.handleDiscontinuity();
      }
      if (outputBuffer.isFirstSample()) {
        processFirstSampleOfStream();
      }
    }

    if (outputBuffer.isEndOfStream()) {
      if (decoderReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM) {
        // We're waiting to re-initialize the decoder, and have now processed all final buffers.
        releaseDecoder();
        maybeInitDecoder();
        // The audio track may need to be recreated once the new output format is known.
        audioTrackNeedsConfigure = true;
      } else {
        outputBuffer.release();
        outputBuffer = null;
        try {
          processEndOfStream();
        } catch (AudioSink.WriteException e) {
          throw createRendererException(
              e, e.format, e.isRecoverable, PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED);
        }
      }
      return false;
    }

    if (audioTrackNeedsConfigure) {
      Format outputFormat =
          getOutputFormat(decoder)
              .buildUpon()
              .setEncoderDelay(encoderDelay)
              .setEncoderPadding(encoderPadding)
              .build();
      audioSink.configure(outputFormat, /* specifiedBufferSize= */ 0, /* outputChannels= */ null);
      audioTrackNeedsConfigure = false;
    }

    if (audioSink.handleBuffer(
        outputBuffer.data, outputBuffer.timeUs, /* encodedAccessUnitCount= */ 1)) {
      decoderCounters.renderedOutputBufferCount++;
      outputBuffer.release();
      outputBuffer = null;
      return true;
    }

    return false;
  }

  private void processFirstSampleOfStream() {
    audioSink.handleDiscontinuity();
    if (pendingOutputStreamOffsetCount != 0) {
      setOutputStreamOffsetUs(pendingOutputStreamOffsetsUs[0]);
      pendingOutputStreamOffsetCount--;
      System.arraycopy(
          pendingOutputStreamOffsetsUs,
          /* srcPos= */ 1,
          pendingOutputStreamOffsetsUs,
          /* destPos= */ 0,
          pendingOutputStreamOffsetCount);
    }
  }

  private void setOutputStreamOffsetUs(long outputStreamOffsetUs) {
    this.outputStreamOffsetUs = outputStreamOffsetUs;
    if (outputStreamOffsetUs != C.TIME_UNSET) {
      audioSink.setOutputStreamOffsetUs(outputStreamOffsetUs);
    }
  }

  private boolean feedInputBuffer() throws DecoderException, ExoPlaybackException {
    if (decoder == null
        || decoderReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM
        || inputStreamEnded) {
      // We need to reinitialize the decoder or the input stream has ended.
      return false;
    }

    if (inputBuffer == null) {
      inputBuffer = decoder.dequeueInputBuffer();
      if (inputBuffer == null) {
        return false;
      }
    }

    if (decoderReinitializationState == REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM) {
      inputBuffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
      decoder.queueInputBuffer(inputBuffer);
      inputBuffer = null;
      decoderReinitializationState = REINITIALIZATION_STATE_WAIT_END_OF_STREAM;
      return false;
    }

    FormatHolder formatHolder = getFormatHolder();
    switch (readSource(formatHolder, inputBuffer, /* readFlags= */ 0)) {
      case C.RESULT_NOTHING_READ:
        return false;
      case C.RESULT_FORMAT_READ:
        onInputFormatChanged(formatHolder);
        return true;
      case C.RESULT_BUFFER_READ:
        if (inputBuffer.isEndOfStream()) {
          inputStreamEnded = true;
          decoder.queueInputBuffer(inputBuffer);
          inputBuffer = null;
          return false;
        }
        if (!firstStreamSampleRead) {
          firstStreamSampleRead = true;
          inputBuffer.addFlag(C.BUFFER_FLAG_FIRST_SAMPLE);
        }
        inputBuffer.flip();
        inputBuffer.format = inputFormat;
        onQueueInputBuffer(inputBuffer);
        decoder.queueInputBuffer(inputBuffer);
        decoderReceivedBuffers = true;
        decoderCounters.queuedInputBufferCount++;
        inputBuffer = null;
        return true;
      default:
        throw new IllegalStateException();
    }
  }

  private void processEndOfStream() throws AudioSink.WriteException {
    outputStreamEnded = true;
    audioSink.playToEndOfStream();
  }

  private void flushDecoder() throws ExoPlaybackException {
    if (decoderReinitializationState != REINITIALIZATION_STATE_NONE) {
      releaseDecoder();
      maybeInitDecoder();
    } else {
      inputBuffer = null;
      if (outputBuffer != null) {
        outputBuffer.release();
        outputBuffer = null;
      }
      decoder.flush();
      decoderReceivedBuffers = false;
    }
  }

  @Override
  public boolean isEnded() {
    return outputStreamEnded && audioSink.isEnded();
  }

  @Override
  public boolean isReady() {
    return audioSink.hasPendingData()
        || (inputFormat != null && (isSourceReady() || outputBuffer != null));
  }

  @Override
  public long getPositionUs() {
    if (getState() == STATE_STARTED) {
      updateCurrentPosition();
    }
    return currentPositionUs;
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    audioSink.setPlaybackParameters(playbackParameters);
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return audioSink.getPlaybackParameters();
  }

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream)
      throws ExoPlaybackException {
    decoderCounters = new DecoderCounters();
    eventDispatcher.enabled(decoderCounters);
    if (getConfiguration().tunneling) {
      audioSink.enableTunnelingV21();
    } else {
      audioSink.disableTunneling();
    }
    audioSink.setPlayerId(getPlayerId());
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    if (experimentalKeepAudioTrackOnSeek) {
      audioSink.experimentalFlushWithoutAudioTrackRelease();
    } else {
      audioSink.flush();
    }

    currentPositionUs = positionUs;
    allowFirstBufferPositionDiscontinuity = true;
    allowPositionDiscontinuity = true;
    inputStreamEnded = false;
    outputStreamEnded = false;
    if (decoder != null) {
      flushDecoder();
    }
  }

  @Override
  protected void onStarted() {
    audioSink.play();
  }

  @Override
  protected void onStopped() {
    updateCurrentPosition();
    audioSink.pause();
  }

  @Override
  protected void onDisabled() {
    inputFormat = null;
    audioTrackNeedsConfigure = true;
    setOutputStreamOffsetUs(C.TIME_UNSET);
    try {
      setSourceDrmSession(null);
      releaseDecoder();
      audioSink.reset();
    } finally {
      eventDispatcher.disabled(decoderCounters);
    }
  }

  @Override
  protected void onStreamChanged(Format[] formats, long startPositionUs, long offsetUs)
      throws ExoPlaybackException {
    super.onStreamChanged(formats, startPositionUs, offsetUs);
    firstStreamSampleRead = false;
    if (outputStreamOffsetUs == C.TIME_UNSET) {
      setOutputStreamOffsetUs(offsetUs);
    } else {
      if (pendingOutputStreamOffsetCount == pendingOutputStreamOffsetsUs.length) {
        Log.w(
            TAG,
            "Too many stream changes, so dropping offset: "
                + pendingOutputStreamOffsetsUs[pendingOutputStreamOffsetCount - 1]);
      } else {
        pendingOutputStreamOffsetCount++;
      }
      pendingOutputStreamOffsetsUs[pendingOutputStreamOffsetCount - 1] = offsetUs;
    }
  }

  @Override
  public void handleMessage(@MessageType int messageType, @Nullable Object message)
      throws ExoPlaybackException {
    switch (messageType) {
      case MSG_SET_VOLUME:
        audioSink.setVolume((Float) message);
        break;
      case MSG_SET_AUDIO_ATTRIBUTES:
        AudioAttributes audioAttributes = (AudioAttributes) message;
        audioSink.setAudioAttributes(audioAttributes);
        break;
      case MSG_SET_AUX_EFFECT_INFO:
        AuxEffectInfo auxEffectInfo = (AuxEffectInfo) message;
        audioSink.setAuxEffectInfo(auxEffectInfo);
        break;
      case MSG_SET_SKIP_SILENCE_ENABLED:
        audioSink.setSkipSilenceEnabled((Boolean) message);
        break;
      case MSG_SET_AUDIO_SESSION_ID:
        audioSink.setAudioSessionId((Integer) message);
        break;
      case MSG_SET_PREFERRED_AUDIO_DEVICE:
        if (Util.SDK_INT >= 23) {
          Api23.setAudioSinkPreferredDevice(audioSink, message);
        }
        break;
      case MSG_SET_CAMERA_MOTION_LISTENER:
      case MSG_SET_CHANGE_FRAME_RATE_STRATEGY:
      case MSG_SET_SCALING_MODE:
      case MSG_SET_VIDEO_FRAME_METADATA_LISTENER:
      case MSG_SET_VIDEO_OUTPUT:
      case MSG_SET_WAKEUP_LISTENER:
      default:
        super.handleMessage(messageType, message);
        break;
    }
  }

  private void maybeInitDecoder() throws ExoPlaybackException {
    if (decoder != null) {
      return;
    }

    setDecoderDrmSession(sourceDrmSession);

    CryptoConfig cryptoConfig = null;
    if (decoderDrmSession != null) {
      cryptoConfig = decoderDrmSession.getCryptoConfig();
      if (cryptoConfig == null) {
        DrmSessionException drmError = decoderDrmSession.getError();
        if (drmError != null) {
          // Continue for now. We may be able to avoid failure if a new input format causes the
          // session to be replaced without it having been used.
        } else {
          // The drm session isn't open yet.
          return;
        }
      }
    }

    try {
      long codecInitializingTimestamp = SystemClock.elapsedRealtime();
      TraceUtil.beginSection("createAudioDecoder");
      decoder = createDecoder(inputFormat, cryptoConfig);
      TraceUtil.endSection();
      long codecInitializedTimestamp = SystemClock.elapsedRealtime();
      eventDispatcher.decoderInitialized(
          decoder.getName(),
          codecInitializedTimestamp,
          codecInitializedTimestamp - codecInitializingTimestamp);
      decoderCounters.decoderInitCount++;
    } catch (DecoderException e) {
      Log.e(TAG, "Audio codec error", e);
      eventDispatcher.audioCodecError(e);
      throw createRendererException(
          e, inputFormat, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
    } catch (OutOfMemoryError e) {
      throw createRendererException(
          e, inputFormat, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
    }
  }

  private void releaseDecoder() {
    inputBuffer = null;
    outputBuffer = null;
    decoderReinitializationState = REINITIALIZATION_STATE_NONE;
    decoderReceivedBuffers = false;
    if (decoder != null) {
      decoderCounters.decoderReleaseCount++;
      decoder.release();
      eventDispatcher.decoderReleased(decoder.getName());
      decoder = null;
    }
    setDecoderDrmSession(null);
  }

  private void setSourceDrmSession(@Nullable DrmSession session) {
    DrmSession.replaceSession(sourceDrmSession, session);
    sourceDrmSession = session;
  }

  private void setDecoderDrmSession(@Nullable DrmSession session) {
    DrmSession.replaceSession(decoderDrmSession, session);
    decoderDrmSession = session;
  }

  private void onInputFormatChanged(FormatHolder formatHolder) throws ExoPlaybackException {
    Format newFormat = Assertions.checkNotNull(formatHolder.format);
    setSourceDrmSession(formatHolder.drmSession);
    Format oldFormat = inputFormat;
    inputFormat = newFormat;
    encoderDelay = newFormat.encoderDelay;
    encoderPadding = newFormat.encoderPadding;

    if (decoder == null) {
      maybeInitDecoder();
      eventDispatcher.inputFormatChanged(inputFormat, /* decoderReuseEvaluation= */ null);
      return;
    }

    DecoderReuseEvaluation evaluation;
    if (sourceDrmSession != decoderDrmSession) {
      evaluation =
          new DecoderReuseEvaluation(
              decoder.getName(),
              oldFormat,
              newFormat,
              REUSE_RESULT_NO,
              DISCARD_REASON_DRM_SESSION_CHANGED);
    } else {
      evaluation = canReuseDecoder(decoder.getName(), oldFormat, newFormat);
    }

    if (evaluation.result == REUSE_RESULT_NO) {
      if (decoderReceivedBuffers) {
        // Signal end of stream and wait for any final output buffers before re-initialization.
        decoderReinitializationState = REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM;
      } else {
        // There aren't any final output buffers, so release the decoder immediately.
        releaseDecoder();
        maybeInitDecoder();
        audioTrackNeedsConfigure = true;
      }
    }
    eventDispatcher.inputFormatChanged(inputFormat, evaluation);
  }

  protected void onQueueInputBuffer(DecoderInputBuffer buffer) {
    if (allowFirstBufferPositionDiscontinuity && !buffer.isDecodeOnly()) {
      // TODO: Remove this hack once we have a proper fix for [Internal: b/71876314].
      // Allow the position to jump if the first presentable input buffer has a timestamp that
      // differs significantly from what was expected.
      if (Math.abs(buffer.timeUs - currentPositionUs) > 500000) {
        currentPositionUs = buffer.timeUs;
      }
      allowFirstBufferPositionDiscontinuity = false;
    }
  }

  private void updateCurrentPosition() {
    long newCurrentPositionUs = audioSink.getCurrentPositionUs(isEnded());
    if (newCurrentPositionUs != AudioSink.CURRENT_POSITION_NOT_SET) {
      currentPositionUs =
          allowPositionDiscontinuity
              ? newCurrentPositionUs
              : max(currentPositionUs, newCurrentPositionUs);
      allowPositionDiscontinuity = false;
    }
  }

  private final class AudioSinkListener implements AudioSink.Listener {

    @Override
    public void onPositionDiscontinuity() {
      DecoderAudioRenderer.this.onPositionDiscontinuity();
    }

    @Override
    public void onPositionAdvancing(long playoutStartSystemTimeMs) {
      eventDispatcher.positionAdvancing(playoutStartSystemTimeMs);
    }

    @Override
    public void onUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
      eventDispatcher.underrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
    }

    @Override
    public void onSkipSilenceEnabledChanged(boolean skipSilenceEnabled) {
      eventDispatcher.skipSilenceEnabledChanged(skipSilenceEnabled);
    }

    @Override
    public void onAudioSinkError(Exception audioSinkError) {
      Log.e(TAG, "Audio sink error", audioSinkError);
      eventDispatcher.audioSinkError(audioSinkError);
    }
  }

  @RequiresApi(23)
  private static final class Api23 {
    private Api23() {}

    @DoNotInline
    public static void setAudioSinkPreferredDevice(
        AudioSink audioSink, @Nullable Object messagePayload) {
      @Nullable AudioDeviceInfo audioDeviceInfo = (AudioDeviceInfo) messagePayload;
      audioSink.setPreferredDevice(audioDeviceInfo);
    }
  }
}
