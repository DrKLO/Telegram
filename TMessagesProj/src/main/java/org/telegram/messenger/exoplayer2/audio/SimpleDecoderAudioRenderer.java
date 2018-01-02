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
package org.telegram.messenger.exoplayer2.audio;

import android.media.audiofx.Virtualizer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.IntDef;
import org.telegram.messenger.exoplayer2.BaseRenderer;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.ExoPlaybackException;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.FormatHolder;
import org.telegram.messenger.exoplayer2.PlaybackParameters;
import org.telegram.messenger.exoplayer2.audio.AudioRendererEventListener.EventDispatcher;
import org.telegram.messenger.exoplayer2.decoder.DecoderCounters;
import org.telegram.messenger.exoplayer2.decoder.DecoderInputBuffer;
import org.telegram.messenger.exoplayer2.decoder.SimpleDecoder;
import org.telegram.messenger.exoplayer2.decoder.SimpleOutputBuffer;
import org.telegram.messenger.exoplayer2.drm.DrmSession;
import org.telegram.messenger.exoplayer2.drm.DrmSession.DrmSessionException;
import org.telegram.messenger.exoplayer2.drm.DrmSessionManager;
import org.telegram.messenger.exoplayer2.drm.ExoMediaCrypto;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.MediaClock;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import org.telegram.messenger.exoplayer2.util.TraceUtil;
import org.telegram.messenger.exoplayer2.util.Util;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Decodes and renders audio using a {@link SimpleDecoder}.
 */
public abstract class SimpleDecoderAudioRenderer extends BaseRenderer implements MediaClock {

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({REINITIALIZATION_STATE_NONE, REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM,
      REINITIALIZATION_STATE_WAIT_END_OF_STREAM})
  private @interface ReinitializationState {}
  /**
   * The decoder does not need to be re-initialized.
   */
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

  private final DrmSessionManager<ExoMediaCrypto> drmSessionManager;
  private final boolean playClearSamplesWithoutKeys;
  private final EventDispatcher eventDispatcher;
  private final AudioTrack audioTrack;
  private final FormatHolder formatHolder;
  private final DecoderInputBuffer flagsOnlyBuffer;

  private DecoderCounters decoderCounters;
  private Format inputFormat;
  private SimpleDecoder<DecoderInputBuffer, ? extends SimpleOutputBuffer,
        ? extends AudioDecoderException> decoder;
  private DecoderInputBuffer inputBuffer;
  private SimpleOutputBuffer outputBuffer;
  private DrmSession<ExoMediaCrypto> drmSession;
  private DrmSession<ExoMediaCrypto> pendingDrmSession;

  @ReinitializationState private int decoderReinitializationState;
  private boolean decoderReceivedBuffers;
  private boolean audioTrackNeedsConfigure;

  private long currentPositionUs;
  private boolean allowPositionDiscontinuity;
  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  private boolean waitingForKeys;

  public SimpleDecoderAudioRenderer() {
    this(null, null);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioProcessors Optional {@link AudioProcessor}s that will process audio before output.
   */
  public SimpleDecoderAudioRenderer(Handler eventHandler,
      AudioRendererEventListener eventListener, AudioProcessor... audioProcessors) {
    this(eventHandler, eventListener, null, null, false, audioProcessors);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioCapabilities The audio capabilities for playback on this device. May be null if the
   *     default capabilities (no encoded audio passthrough support) should be assumed.
   */
  public SimpleDecoderAudioRenderer(Handler eventHandler,
      AudioRendererEventListener eventListener, AudioCapabilities audioCapabilities) {
    this(eventHandler, eventListener, audioCapabilities, null, false);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioCapabilities The audio capabilities for playback on this device. May be null if the
   *     default capabilities (no encoded audio passthrough support) should be assumed.
   * @param drmSessionManager For use with encrypted media. May be null if support for encrypted
   *     media is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisition. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   * @param audioProcessors Optional {@link AudioProcessor}s that will process audio before output.
   */
  public SimpleDecoderAudioRenderer(Handler eventHandler,
      AudioRendererEventListener eventListener, AudioCapabilities audioCapabilities,
      DrmSessionManager<ExoMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys,
      AudioProcessor... audioProcessors) {
    super(C.TRACK_TYPE_AUDIO);
    this.drmSessionManager = drmSessionManager;
    this.playClearSamplesWithoutKeys = playClearSamplesWithoutKeys;
    eventDispatcher = new EventDispatcher(eventHandler, eventListener);
    audioTrack = new AudioTrack(audioCapabilities, audioProcessors, new AudioTrackListener());
    formatHolder = new FormatHolder();
    flagsOnlyBuffer = DecoderInputBuffer.newFlagsOnlyInstance();
    decoderReinitializationState = REINITIALIZATION_STATE_NONE;
    audioTrackNeedsConfigure = true;
  }

  @Override
  public MediaClock getMediaClock() {
    return this;
  }

  @Override
  public final int supportsFormat(Format format) {
    int formatSupport = supportsFormatInternal(format);
    if (formatSupport == FORMAT_UNSUPPORTED_TYPE || formatSupport == FORMAT_UNSUPPORTED_SUBTYPE) {
      return formatSupport;
    }
    int tunnelingSupport = Util.SDK_INT >= 21 ? TUNNELING_SUPPORTED : TUNNELING_NOT_SUPPORTED;
    return ADAPTIVE_NOT_SEAMLESS | tunnelingSupport | formatSupport;
  }

  /**
   * Returns the {@link #FORMAT_SUPPORT_MASK} component of the return value for
   * {@link #supportsFormat(Format)}.
   *
   * @param format The format.
   * @return The extent to which the renderer supports the format itself.
   */
  protected abstract int supportsFormatInternal(Format format);

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (outputStreamEnded) {
      try {
        audioTrack.playToEndOfStream();
      } catch (AudioTrack.WriteException e) {
        throw ExoPlaybackException.createForRenderer(e, getIndex());
      }
      return;
    }

    // Try and read a format if we don't have one already.
    if (inputFormat == null) {
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

    // If we don't have a decoder yet, we need to instantiate one.
    maybeInitDecoder();

    if (decoder != null) {
      try {
        // Rendering loop.
        TraceUtil.beginSection("drainAndFeed");
        while (drainOutputBuffer()) {}
        while (feedInputBuffer()) {}
        TraceUtil.endSection();
      } catch (AudioDecoderException | AudioTrack.ConfigurationException
          | AudioTrack.InitializationException | AudioTrack.WriteException e) {
        throw ExoPlaybackException.createForRenderer(e, getIndex());
      }
      decoderCounters.ensureUpdated();
    }
  }

  /**
   * Called when the audio session id becomes known. The default implementation is a no-op. One
   * reason for overriding this method would be to instantiate and enable a {@link Virtualizer} in
   * order to spatialize the audio channels. For this use case, any {@link Virtualizer} instances
   * should be released in {@link #onDisabled()} (if not before).
   *
   * @see AudioTrack.Listener#onAudioSessionId(int)
   */
  protected void onAudioSessionId(int audioSessionId) {
    // Do nothing.
  }

  /**
   * @see AudioTrack.Listener#onPositionDiscontinuity()
   */
  protected void onAudioTrackPositionDiscontinuity() {
    // Do nothing.
  }

  /**
   * @see AudioTrack.Listener#onUnderrun(int, long, long)
   */
  protected void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs,
      long elapsedSinceLastFeedMs) {
    // Do nothing.
  }

  /**
   * Creates a decoder for the given format.
   *
   * @param format The format for which a decoder is required.
   * @param mediaCrypto The {@link ExoMediaCrypto} object required for decoding encrypted content.
   *     Maybe null and can be ignored if decoder does not handle encrypted content.
   * @return The decoder.
   * @throws AudioDecoderException If an error occurred creating a suitable decoder.
   */
  protected abstract SimpleDecoder<DecoderInputBuffer, ? extends SimpleOutputBuffer,
      ? extends AudioDecoderException> createDecoder(Format format, ExoMediaCrypto mediaCrypto)
      throws AudioDecoderException;

  /**
   * Returns the format of audio buffers output by the decoder. Will not be called until the first
   * output buffer has been dequeued, so the decoder may use input data to determine the format.
   * <p>
   * The default implementation returns a 16-bit PCM format with the same channel count and sample
   * rate as the input.
   */
  protected Format getOutputFormat() {
    return Format.createAudioSampleFormat(null, MimeTypes.AUDIO_RAW, null, Format.NO_VALUE,
        Format.NO_VALUE, inputFormat.channelCount, inputFormat.sampleRate, C.ENCODING_PCM_16BIT,
        null, null, 0, null);
  }

  private boolean drainOutputBuffer() throws ExoPlaybackException, AudioDecoderException,
      AudioTrack.ConfigurationException, AudioTrack.InitializationException,
      AudioTrack.WriteException {
    if (outputBuffer == null) {
      outputBuffer = decoder.dequeueOutputBuffer();
      if (outputBuffer == null) {
        return false;
      }
      decoderCounters.skippedOutputBufferCount += outputBuffer.skippedOutputBufferCount;
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
        processEndOfStream();
      }
      return false;
    }

    if (audioTrackNeedsConfigure) {
      Format outputFormat = getOutputFormat();
      audioTrack.configure(outputFormat.sampleMimeType, outputFormat.channelCount,
          outputFormat.sampleRate, outputFormat.pcmEncoding, 0);
      audioTrackNeedsConfigure = false;
    }

    if (audioTrack.handleBuffer(outputBuffer.data, outputBuffer.timeUs)) {
      decoderCounters.renderedOutputBufferCount++;
      outputBuffer.release();
      outputBuffer = null;
      return true;
    }

    return false;
  }

  private boolean feedInputBuffer() throws AudioDecoderException, ExoPlaybackException {
    if (decoder == null || decoderReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM
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

    int result;
    if (waitingForKeys) {
      // We've already read an encrypted sample into buffer, and are waiting for keys.
      result = C.RESULT_BUFFER_READ;
    } else {
      result = readSource(formatHolder, inputBuffer, false);
    }

    if (result == C.RESULT_NOTHING_READ) {
      return false;
    }
    if (result == C.RESULT_FORMAT_READ) {
      onInputFormatChanged(formatHolder.format);
      return true;
    }
    if (inputBuffer.isEndOfStream()) {
      inputStreamEnded = true;
      decoder.queueInputBuffer(inputBuffer);
      inputBuffer = null;
      return false;
    }
    boolean bufferEncrypted = inputBuffer.isEncrypted();
    waitingForKeys = shouldWaitForKeys(bufferEncrypted);
    if (waitingForKeys) {
      return false;
    }
    inputBuffer.flip();
    decoder.queueInputBuffer(inputBuffer);
    decoderReceivedBuffers = true;
    decoderCounters.inputBufferCount++;
    inputBuffer = null;
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

  private void processEndOfStream() throws ExoPlaybackException {
    outputStreamEnded = true;
    try {
      audioTrack.playToEndOfStream();
    } catch (AudioTrack.WriteException e) {
      throw ExoPlaybackException.createForRenderer(drmSession.getError(), getIndex());
    }
  }

  private void flushDecoder() throws ExoPlaybackException {
    waitingForKeys = false;
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
    return outputStreamEnded && audioTrack.isEnded();
  }

  @Override
  public boolean isReady() {
    return audioTrack.hasPendingData()
        || (inputFormat != null && !waitingForKeys && (isSourceReady() || outputBuffer != null));
  }

  @Override
  public long getPositionUs() {
    long newCurrentPositionUs = audioTrack.getCurrentPositionUs(isEnded());
    if (newCurrentPositionUs != AudioTrack.CURRENT_POSITION_NOT_SET) {
      currentPositionUs = allowPositionDiscontinuity ? newCurrentPositionUs
          : Math.max(currentPositionUs, newCurrentPositionUs);
      allowPositionDiscontinuity = false;
    }
    return currentPositionUs;
  }

  @Override
  public PlaybackParameters setPlaybackParameters(PlaybackParameters playbackParameters) {
    return audioTrack.setPlaybackParameters(playbackParameters);
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return audioTrack.getPlaybackParameters();
  }

  @Override
  protected void onEnabled(boolean joining) throws ExoPlaybackException {
    decoderCounters = new DecoderCounters();
    eventDispatcher.enabled(decoderCounters);
    int tunnelingAudioSessionId = getConfiguration().tunnelingAudioSessionId;
    if (tunnelingAudioSessionId != C.AUDIO_SESSION_ID_UNSET) {
      audioTrack.enableTunnelingV21(tunnelingAudioSessionId);
    } else {
      audioTrack.disableTunneling();
    }
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    audioTrack.reset();
    currentPositionUs = positionUs;
    allowPositionDiscontinuity = true;
    inputStreamEnded = false;
    outputStreamEnded = false;
    if (decoder != null) {
      flushDecoder();
    }
  }

  @Override
  protected void onStarted() {
    audioTrack.play();
  }

  @Override
  protected void onStopped() {
    audioTrack.pause();
  }

  @Override
  protected void onDisabled() {
    inputFormat = null;
    audioTrackNeedsConfigure = true;
    waitingForKeys = false;
    try {
      releaseDecoder();
      audioTrack.release();
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
          decoderCounters.ensureUpdated();
          eventDispatcher.disabled(decoderCounters);
        }
      }
    }
  }

  private void maybeInitDecoder() throws ExoPlaybackException {
    if (decoder != null) {
      return;
    }

    drmSession = pendingDrmSession;
    ExoMediaCrypto mediaCrypto = null;
    if (drmSession != null) {
      mediaCrypto = drmSession.getMediaCrypto();
      if (mediaCrypto == null) {
        DrmSessionException drmError = drmSession.getError();
        if (drmError != null) {
          throw ExoPlaybackException.createForRenderer(drmError, getIndex());
        }
        // The drm session isn't open yet.
        return;
      }
    }

    try {
      long codecInitializingTimestamp = SystemClock.elapsedRealtime();
      TraceUtil.beginSection("createAudioDecoder");
      decoder = createDecoder(inputFormat, mediaCrypto);
      TraceUtil.endSection();
      long codecInitializedTimestamp = SystemClock.elapsedRealtime();
      eventDispatcher.decoderInitialized(decoder.getName(), codecInitializedTimestamp,
          codecInitializedTimestamp - codecInitializingTimestamp);
      decoderCounters.decoderInitCount++;
    } catch (AudioDecoderException e) {
      throw ExoPlaybackException.createForRenderer(e, getIndex());
    }
  }

  private void releaseDecoder() {
    if (decoder == null) {
      return;
    }

    inputBuffer = null;
    outputBuffer = null;
    decoder.release();
    decoder = null;
    decoderCounters.decoderReleaseCount++;
    decoderReinitializationState = REINITIALIZATION_STATE_NONE;
    decoderReceivedBuffers = false;
  }

  private void onInputFormatChanged(Format newFormat) throws ExoPlaybackException {
    Format oldFormat = inputFormat;
    inputFormat = newFormat;

    boolean drmInitDataChanged = !Util.areEqual(inputFormat.drmInitData, oldFormat == null ? null
        : oldFormat.drmInitData);
    if (drmInitDataChanged) {
      if (inputFormat.drmInitData != null) {
        if (drmSessionManager == null) {
          throw ExoPlaybackException.createForRenderer(
              new IllegalStateException("Media requires a DrmSessionManager"), getIndex());
        }
        pendingDrmSession = drmSessionManager.acquireSession(Looper.myLooper(),
            inputFormat.drmInitData);
        if (pendingDrmSession == drmSession) {
          drmSessionManager.releaseSession(pendingDrmSession);
        }
      } else {
        pendingDrmSession = null;
      }
    }

    if (decoderReceivedBuffers) {
      // Signal end of stream and wait for any final output buffers before re-initialization.
      decoderReinitializationState = REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM;
    } else {
      // There aren't any final output buffers, so release the decoder immediately.
      releaseDecoder();
      maybeInitDecoder();
      audioTrackNeedsConfigure = true;
    }

    eventDispatcher.inputFormatChanged(newFormat);
  }

  @Override
  public void handleMessage(int messageType, Object message) throws ExoPlaybackException {
    switch (messageType) {
      case C.MSG_SET_VOLUME:
        audioTrack.setVolume((Float) message);
        break;
      case C.MSG_SET_AUDIO_ATTRIBUTES:
        AudioAttributes audioAttributes = (AudioAttributes) message;
        audioTrack.setAudioAttributes(audioAttributes);
        break;
      default:
        super.handleMessage(messageType, message);
        break;
    }
  }

  private final class AudioTrackListener implements AudioTrack.Listener {

    @Override
    public void onAudioSessionId(int audioSessionId) {
      eventDispatcher.audioSessionId(audioSessionId);
      SimpleDecoderAudioRenderer.this.onAudioSessionId(audioSessionId);
    }

    @Override
    public void onPositionDiscontinuity() {
      onAudioTrackPositionDiscontinuity();
      // We are out of sync so allow currentPositionUs to jump backwards.
      SimpleDecoderAudioRenderer.this.allowPositionDiscontinuity = true;
    }

    @Override
    public void onUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
      eventDispatcher.audioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
      onAudioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
    }

  }

}
