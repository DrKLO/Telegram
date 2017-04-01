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

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.media.PlaybackParams;
import android.media.audiofx.Virtualizer;
import android.os.Handler;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.ExoPlaybackException;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.audio.AudioRendererEventListener.EventDispatcher;
import org.telegram.messenger.exoplayer2.drm.DrmSessionManager;
import org.telegram.messenger.exoplayer2.drm.FrameworkMediaCrypto;
import org.telegram.messenger.exoplayer2.mediacodec.MediaCodecInfo;
import org.telegram.messenger.exoplayer2.mediacodec.MediaCodecRenderer;
import org.telegram.messenger.exoplayer2.mediacodec.MediaCodecSelector;
import org.telegram.messenger.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import org.telegram.messenger.exoplayer2.util.MediaClock;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import org.telegram.messenger.exoplayer2.util.Util;
import java.nio.ByteBuffer;

/**
 * Decodes and renders audio using {@link MediaCodec} and {@link AudioTrack}.
 */
@TargetApi(16)
public class MediaCodecAudioRenderer extends MediaCodecRenderer implements MediaClock,
    AudioTrack.Listener {

  private final EventDispatcher eventDispatcher;
  private final AudioTrack audioTrack;

  private boolean passthroughEnabled;
  private android.media.MediaFormat passthroughMediaFormat;
  private int pcmEncoding;
  private int audioSessionId;
  private long currentPositionUs;
  private boolean allowPositionDiscontinuity;

  /**
   * @param mediaCodecSelector A decoder selector.
   */
  public MediaCodecAudioRenderer(MediaCodecSelector mediaCodecSelector) {
    this(mediaCodecSelector, null, true);
  }

  /**
   * @param mediaCodecSelector A decoder selector.
   * @param drmSessionManager For use with encrypted content. May be null if support for encrypted
   *     content is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisition. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   */
  public MediaCodecAudioRenderer(MediaCodecSelector mediaCodecSelector,
      DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys) {
    this(mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, null, null);
  }

  /**
   * @param mediaCodecSelector A decoder selector.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public MediaCodecAudioRenderer(MediaCodecSelector mediaCodecSelector, Handler eventHandler,
      AudioRendererEventListener eventListener) {
    this(mediaCodecSelector, null, true, eventHandler, eventListener);
  }

  /**
   * @param mediaCodecSelector A decoder selector.
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
   */
  public MediaCodecAudioRenderer(MediaCodecSelector mediaCodecSelector,
      DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys, Handler eventHandler,
      AudioRendererEventListener eventListener) {
    this(mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, eventHandler,
        eventListener, null);
  }

  /**
   * @param mediaCodecSelector A decoder selector.
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
   * @param audioCapabilities The audio capabilities for playback on this device. May be null if the
   *     default capabilities (no encoded audio passthrough support) should be assumed.
   */
  public MediaCodecAudioRenderer(MediaCodecSelector mediaCodecSelector,
      DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys, Handler eventHandler,
      AudioRendererEventListener eventListener, AudioCapabilities audioCapabilities) {
    super(C.TRACK_TYPE_AUDIO, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys);
    audioSessionId = AudioTrack.SESSION_ID_NOT_SET;
    audioTrack = new AudioTrack(audioCapabilities, this);
    eventDispatcher = new EventDispatcher(eventHandler, eventListener);
  }

  @Override
  protected int supportsFormat(MediaCodecSelector mediaCodecSelector, Format format)
      throws DecoderQueryException {
    String mimeType = format.sampleMimeType;
    if (!MimeTypes.isAudio(mimeType)) {
      return FORMAT_UNSUPPORTED_TYPE;
    }
    if (allowPassthrough(mimeType) && mediaCodecSelector.getPassthroughDecoderInfo() != null) {
      return ADAPTIVE_NOT_SEAMLESS | FORMAT_HANDLED;
    }
    MediaCodecInfo decoderInfo = mediaCodecSelector.getDecoderInfo(mimeType, false);
    if (decoderInfo == null) {
      return FORMAT_UNSUPPORTED_SUBTYPE;
    }
    // Note: We assume support for unknown sampleRate and channelCount.
    boolean decoderCapable = Util.SDK_INT < 21
        || ((format.sampleRate == Format.NO_VALUE
        || decoderInfo.isAudioSampleRateSupportedV21(format.sampleRate))
        && (format.channelCount == Format.NO_VALUE
        ||  decoderInfo.isAudioChannelCountSupportedV21(format.channelCount)));
    int formatSupport = decoderCapable ? FORMAT_HANDLED : FORMAT_EXCEEDS_CAPABILITIES;
    return ADAPTIVE_NOT_SEAMLESS | formatSupport;
  }

  @Override
  protected MediaCodecInfo getDecoderInfo(MediaCodecSelector mediaCodecSelector,
      Format format, boolean requiresSecureDecoder) throws DecoderQueryException {
    if (allowPassthrough(format.sampleMimeType)) {
      MediaCodecInfo passthroughDecoderInfo = mediaCodecSelector.getPassthroughDecoderInfo();
      if (passthroughDecoderInfo != null) {
        passthroughEnabled = true;
        return passthroughDecoderInfo;
      }
    }
    passthroughEnabled = false;
    return super.getDecoderInfo(mediaCodecSelector, format, requiresSecureDecoder);
  }

  /**
   * Returns whether encoded audio passthrough should be used for playing back the input format.
   * This implementation returns true if the {@link AudioTrack}'s audio capabilities indicate that
   * passthrough is supported.
   *
   * @param mimeType The type of input media.
   * @return Whether passthrough playback should be used.
   */
  protected boolean allowPassthrough(String mimeType) {
    return audioTrack.isPassthroughSupported(mimeType);
  }

  @Override
  protected void configureCodec(MediaCodec codec, Format format, MediaCrypto crypto) {
    if (passthroughEnabled) {
      // Override the MIME type used to configure the codec if we are using a passthrough decoder.
      passthroughMediaFormat = format.getFrameworkMediaFormatV16();
      passthroughMediaFormat.setString(MediaFormat.KEY_MIME, MimeTypes.AUDIO_RAW);
      codec.configure(passthroughMediaFormat, null, crypto, 0);
      passthroughMediaFormat.setString(MediaFormat.KEY_MIME, format.sampleMimeType);
    } else {
      codec.configure(format.getFrameworkMediaFormatV16(), null, crypto, 0);
      passthroughMediaFormat = null;
    }
  }

  @Override
  public MediaClock getMediaClock() {
    return this;
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
    // If the input format is anything other than PCM then we assume that the audio decoder will
    // output 16-bit PCM.
    pcmEncoding = MimeTypes.AUDIO_RAW.equals(newFormat.sampleMimeType) ? newFormat.pcmEncoding
        : C.ENCODING_PCM_16BIT;
  }

  @Override
  protected void onOutputFormatChanged(MediaCodec codec, MediaFormat outputFormat) {
    boolean passthrough = passthroughMediaFormat != null;
    String mimeType = passthrough ? passthroughMediaFormat.getString(MediaFormat.KEY_MIME)
        : MimeTypes.AUDIO_RAW;
    MediaFormat format = passthrough ? passthroughMediaFormat : outputFormat;
    int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
    int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
    audioTrack.configure(mimeType, channelCount, sampleRate, pcmEncoding, 0);
  }

  /**
   * Called when the audio session id becomes known. Once the id is known it will not change (and
   * hence this method will not be called again) unless the renderer is disabled and then
   * subsequently re-enabled.
   * <p>
   * The default implementation is a no-op. One reason for overriding this method would be to
   * instantiate and enable a {@link Virtualizer} in order to spatialize the audio channels. For
   * this use case, any {@link Virtualizer} instances should be released in {@link #onDisabled()}
   * (if not before).
   *
   * @param audioSessionId The audio session id.
   */
  protected void onAudioSessionId(int audioSessionId) {
    // Do nothing.
  }

  @Override
  protected void onEnabled(boolean joining) throws ExoPlaybackException {
    super.onEnabled(joining);
    eventDispatcher.enabled(decoderCounters);
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    super.onPositionReset(positionUs, joining);
    audioTrack.reset();
    currentPositionUs = positionUs;
    allowPositionDiscontinuity = true;
  }

  @Override
  protected void onStarted() {
    super.onStarted();
    audioTrack.play();
  }

  @Override
  protected void onStopped() {
    audioTrack.pause();
    super.onStopped();
  }

  @Override
  protected void onDisabled() {
    audioSessionId = AudioTrack.SESSION_ID_NOT_SET;
    try {
      audioTrack.release();
    } finally {
      try {
        super.onDisabled();
      } finally {
        decoderCounters.ensureUpdated();
        eventDispatcher.disabled(decoderCounters);
      }
    }
  }

  @Override
  public boolean isEnded() {
    return super.isEnded() && !audioTrack.hasPendingData();
  }

  @Override
  public boolean isReady() {
    return audioTrack.hasPendingData() || super.isReady();
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
  protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs, MediaCodec codec,
      ByteBuffer buffer, int bufferIndex, int bufferFlags, long bufferPresentationTimeUs,
      boolean shouldSkip) throws ExoPlaybackException {
    if (passthroughEnabled && (bufferFlags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
      // Discard output buffers from the passthrough (raw) decoder containing codec specific data.
      codec.releaseOutputBuffer(bufferIndex, false);
      return true;
    }

    if (shouldSkip) {
      codec.releaseOutputBuffer(bufferIndex, false);
      decoderCounters.skippedOutputBufferCount++;
      audioTrack.handleDiscontinuity();
      return true;
    }

    if (!audioTrack.isInitialized()) {
      // Initialize the AudioTrack now.
      try {
        if (audioSessionId == AudioTrack.SESSION_ID_NOT_SET) {
          audioSessionId = audioTrack.initialize(AudioTrack.SESSION_ID_NOT_SET);
          eventDispatcher.audioSessionId(audioSessionId);
          onAudioSessionId(audioSessionId);
        } else {
          audioTrack.initialize(audioSessionId);
        }
      } catch (AudioTrack.InitializationException e) {
        throw ExoPlaybackException.createForRenderer(e, getIndex());
      }
      if (getState() == STATE_STARTED) {
        audioTrack.play();
      }
    }

    int handleBufferResult;
    try {
      handleBufferResult = audioTrack.handleBuffer(buffer, bufferPresentationTimeUs);
    } catch (AudioTrack.WriteException e) {
      throw ExoPlaybackException.createForRenderer(e, getIndex());
    }

    // If we are out of sync, allow currentPositionUs to jump backwards.
    if ((handleBufferResult & AudioTrack.RESULT_POSITION_DISCONTINUITY) != 0) {
      handleAudioTrackDiscontinuity();
      allowPositionDiscontinuity = true;
    }

    // Release the buffer if it was consumed.
    if ((handleBufferResult & AudioTrack.RESULT_BUFFER_CONSUMED) != 0) {
      codec.releaseOutputBuffer(bufferIndex, false);
      decoderCounters.renderedOutputBufferCount++;
      return true;
    }

    return false;
  }

  @Override
  protected void onOutputStreamEnded() {
    audioTrack.handleEndOfStream();
  }

  protected void handleAudioTrackDiscontinuity() {
    // Do nothing
  }

  @Override
  public void handleMessage(int messageType, Object message) throws ExoPlaybackException {
    switch (messageType) {
      case C.MSG_SET_VOLUME:
        audioTrack.setVolume((Float) message);
        break;
      case C.MSG_SET_PLAYBACK_PARAMS:
        audioTrack.setPlaybackParams((PlaybackParams) message);
        break;
      case C.MSG_SET_STREAM_TYPE:
        @C.StreamType int streamType = (Integer) message;
        if (audioTrack.setStreamType(streamType)) {
          audioSessionId = AudioTrack.SESSION_ID_NOT_SET;
        }
        break;
      default:
        super.handleMessage(messageType, message);
        break;
    }
  }

  // AudioTrack.Listener implementation.

  @Override
  public void onUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
    eventDispatcher.audioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
  }

}
