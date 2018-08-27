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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.media.audiofx.Virtualizer;
import android.os.Handler;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.PlayerMessage.Target;
import com.google.android.exoplayer2.audio.AudioRendererEventListener.EventDispatcher;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.mediacodec.MediaFormatUtil;
import com.google.android.exoplayer2.util.MediaClock;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

/**
 * Decodes and renders audio using {@link MediaCodec} and an {@link AudioSink}.
 *
 * <p>This renderer accepts the following messages sent via {@link ExoPlayer#createMessage(Target)}
 * on the playback thread:
 *
 * <ul>
 *   <li>Message with type {@link C#MSG_SET_VOLUME} to set the volume. The message payload should be
 *       a {@link Float} with 0 being silence and 1 being unity gain.
 *   <li>Message with type {@link C#MSG_SET_AUDIO_ATTRIBUTES} to set the audio attributes. The
 *       message payload should be an {@link com.google.android.exoplayer2.audio.AudioAttributes}
 *       instance that will configure the underlying audio track.
 * </ul>
 */
@TargetApi(16)
public class MediaCodecAudioRenderer extends MediaCodecRenderer implements MediaClock {

  private final Context context;
  private final EventDispatcher eventDispatcher;
  private final AudioSink audioSink;

  private int codecMaxInputSize;
  private boolean passthroughEnabled;
  private boolean codecNeedsDiscardChannelsWorkaround;
  private android.media.MediaFormat passthroughMediaFormat;
  @C.Encoding
  private int pcmEncoding;
  private int channelCount;
  private int encoderDelay;
  private int encoderPadding;
  private long currentPositionUs;
  private boolean allowFirstBufferPositionDiscontinuity;
  private boolean allowPositionDiscontinuity;

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   */
  public MediaCodecAudioRenderer(Context context, MediaCodecSelector mediaCodecSelector) {
    this(
        context,
        mediaCodecSelector,
        /* drmSessionManager= */ null,
        /* playClearSamplesWithoutKeys= */ false);
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   * @param drmSessionManager For use with encrypted content. May be null if support for encrypted
   *     content is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisition. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   */
  public MediaCodecAudioRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys) {
    this(
        context,
        mediaCodecSelector,
        drmSessionManager,
        playClearSamplesWithoutKeys,
        /* eventHandler= */ null,
        /* eventListener= */ null);
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public MediaCodecAudioRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener) {
    this(
        context,
        mediaCodecSelector,
        /* drmSessionManager= */ null,
        /* playClearSamplesWithoutKeys= */ false,
        eventHandler,
        eventListener);
  }

  /**
   * @param context A context.
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
  public MediaCodecAudioRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys,
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener) {
    this(
        context,
        mediaCodecSelector,
        drmSessionManager,
        playClearSamplesWithoutKeys,
        eventHandler,
        eventListener,
        (AudioCapabilities) null);
  }

  /**
   * @param context A context.
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
   * @param audioProcessors Optional {@link AudioProcessor}s that will process PCM audio before
   *     output.
   */
  public MediaCodecAudioRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys,
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      @Nullable AudioCapabilities audioCapabilities,
      AudioProcessor... audioProcessors) {
    this(
        context,
        mediaCodecSelector,
        drmSessionManager,
        playClearSamplesWithoutKeys,
        eventHandler,
        eventListener,
        new DefaultAudioSink(audioCapabilities, audioProcessors));
  }

  /**
   * @param context A context.
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
   * @param audioSink The sink to which audio will be output.
   */
  public MediaCodecAudioRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys,
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioSink audioSink) {
    super(
        C.TRACK_TYPE_AUDIO,
        mediaCodecSelector,
        drmSessionManager,
        playClearSamplesWithoutKeys,
        /* assumedMinimumCodecOperatingRate= */ 44100);
    this.context = context.getApplicationContext();
    this.audioSink = audioSink;
    eventDispatcher = new EventDispatcher(eventHandler, eventListener);
    audioSink.setListener(new AudioSinkListener());
  }

  @Override
  protected int supportsFormat(MediaCodecSelector mediaCodecSelector,
      DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, Format format)
      throws DecoderQueryException {
    String mimeType = format.sampleMimeType;
    if (!MimeTypes.isAudio(mimeType)) {
      return FORMAT_UNSUPPORTED_TYPE;
    }
    int tunnelingSupport = Util.SDK_INT >= 21 ? TUNNELING_SUPPORTED : TUNNELING_NOT_SUPPORTED;
    boolean supportsFormatDrm = supportsFormatDrm(drmSessionManager, format.drmInitData);
    if (supportsFormatDrm && allowPassthrough(mimeType)
        && mediaCodecSelector.getPassthroughDecoderInfo() != null) {
      return ADAPTIVE_NOT_SEAMLESS | tunnelingSupport | FORMAT_HANDLED;
    }
    if ((MimeTypes.AUDIO_RAW.equals(mimeType) && !audioSink.isEncodingSupported(format.pcmEncoding))
        || !audioSink.isEncodingSupported(C.ENCODING_PCM_16BIT)) {
      // Assume the decoder outputs 16-bit PCM, unless the input is raw.
      return FORMAT_UNSUPPORTED_SUBTYPE;
    }
    boolean requiresSecureDecryption = false;
    DrmInitData drmInitData = format.drmInitData;
    if (drmInitData != null) {
      for (int i = 0; i < drmInitData.schemeDataCount; i++) {
        requiresSecureDecryption |= drmInitData.get(i).requiresSecureDecryption;
      }
    }
    List<MediaCodecInfo> decoderInfos =
        mediaCodecSelector.getDecoderInfos(format, requiresSecureDecryption);
    if (decoderInfos.isEmpty()) {
      return requiresSecureDecryption
              && !mediaCodecSelector
                  .getDecoderInfos(format, /* requiresSecureDecoder= */ false)
                  .isEmpty()
          ? FORMAT_UNSUPPORTED_DRM
          : FORMAT_UNSUPPORTED_SUBTYPE;
    }
    if (!supportsFormatDrm) {
      return FORMAT_UNSUPPORTED_DRM;
    }
    // Check capabilities for the first decoder in the list, which takes priority.
    MediaCodecInfo decoderInfo = decoderInfos.get(0);
    // Note: We assume support for unknown sampleRate and channelCount.
    boolean decoderCapable = Util.SDK_INT < 21
        || ((format.sampleRate == Format.NO_VALUE
        || decoderInfo.isAudioSampleRateSupportedV21(format.sampleRate))
        && (format.channelCount == Format.NO_VALUE
        ||  decoderInfo.isAudioChannelCountSupportedV21(format.channelCount)));
    int formatSupport = decoderCapable ? FORMAT_HANDLED : FORMAT_EXCEEDS_CAPABILITIES;
    return ADAPTIVE_NOT_SEAMLESS | tunnelingSupport | formatSupport;
  }

  @Override
  protected List<MediaCodecInfo> getDecoderInfos(
      MediaCodecSelector mediaCodecSelector, Format format, boolean requiresSecureDecoder)
      throws DecoderQueryException {
    if (allowPassthrough(format.sampleMimeType)) {
      MediaCodecInfo passthroughDecoderInfo = mediaCodecSelector.getPassthroughDecoderInfo();
      if (passthroughDecoderInfo != null) {
        return Collections.singletonList(passthroughDecoderInfo);
      }
    }
    return super.getDecoderInfos(mediaCodecSelector, format, requiresSecureDecoder);
  }

  /**
   * Returns whether encoded audio passthrough should be used for playing back the input format.
   * This implementation returns true if the {@link AudioSink} indicates that encoded audio output
   * is supported.
   *
   * @param mimeType The type of input media.
   * @return Whether passthrough playback is supported.
   */
  protected boolean allowPassthrough(String mimeType) {
    @C.Encoding int encoding = MimeTypes.getEncoding(mimeType);
    return encoding != C.ENCODING_INVALID && audioSink.isEncodingSupported(encoding);
  }

  @Override
  protected void configureCodec(
      MediaCodecInfo codecInfo,
      MediaCodec codec,
      Format format,
      MediaCrypto crypto,
      float codecOperatingRate) {
    codecMaxInputSize = getCodecMaxInputSize(codecInfo, format, getStreamFormats());
    codecNeedsDiscardChannelsWorkaround = codecNeedsDiscardChannelsWorkaround(codecInfo.name);
    passthroughEnabled = codecInfo.passthrough;
    String codecMimeType = codecInfo.mimeType == null ? MimeTypes.AUDIO_RAW : codecInfo.mimeType;
    MediaFormat mediaFormat =
        getMediaFormat(format, codecMimeType, codecMaxInputSize, codecOperatingRate);
    codec.configure(mediaFormat, /* surface= */ null, crypto, /* flags= */ 0);
    if (passthroughEnabled) {
      // Store the input MIME type if we're using the passthrough codec.
      passthroughMediaFormat = mediaFormat;
      passthroughMediaFormat.setString(MediaFormat.KEY_MIME, format.sampleMimeType);
    } else {
      passthroughMediaFormat = null;
    }
  }

  @Override
  protected @KeepCodecResult int canKeepCodec(
      MediaCodec codec, MediaCodecInfo codecInfo, Format oldFormat, Format newFormat) {
    return KEEP_CODEC_RESULT_NO;
    // TODO: Determine when codecs can be safely kept. When doing so, also uncomment the commented
    // out code in getCodecMaxInputSize.
    // return getCodecMaxInputSize(codecInfo, newFormat) <= codecMaxInputSize
    //         && areAdaptationCompatible(oldFormat, newFormat)
    //     ? KEEP_CODEC_RESULT_YES_WITHOUT_RECONFIGURATION
    //     : KEEP_CODEC_RESULT_NO;
  }

  @Override
  public MediaClock getMediaClock() {
    return this;
  }

  @Override
  protected float getCodecOperatingRate(
      float operatingRate, Format format, Format[] streamFormats) {
    return format.sampleRate == Format.NO_VALUE
        ? CODEC_OPERATING_RATE_UNSET
        : (format.sampleRate * operatingRate);
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
    channelCount = newFormat.channelCount;
    encoderDelay = newFormat.encoderDelay;
    encoderPadding = newFormat.encoderPadding;
  }

  @Override
  protected void onOutputFormatChanged(MediaCodec codec, MediaFormat outputFormat)
      throws ExoPlaybackException {
    @C.Encoding int encoding;
    MediaFormat format;
    if (passthroughMediaFormat != null) {
      encoding = MimeTypes.getEncoding(passthroughMediaFormat.getString(MediaFormat.KEY_MIME));
      format = passthroughMediaFormat;
    } else {
      encoding = pcmEncoding;
      format = outputFormat;
    }
    int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
    int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
    int[] channelMap;
    if (codecNeedsDiscardChannelsWorkaround && channelCount == 6 && this.channelCount < 6) {
      channelMap = new int[this.channelCount];
      for (int i = 0; i < this.channelCount; i++) {
        channelMap[i] = i;
      }
    } else {
      channelMap = null;
    }

    try {
      audioSink.configure(encoding, channelCount, sampleRate, 0, channelMap, encoderDelay,
          encoderPadding);
    } catch (AudioSink.ConfigurationException e) {
      throw ExoPlaybackException.createForRenderer(e, getIndex());
    }
  }

  /**
   * Called when the audio session id becomes known. The default implementation is a no-op. One
   * reason for overriding this method would be to instantiate and enable a {@link Virtualizer} in
   * order to spatialize the audio channels. For this use case, any {@link Virtualizer} instances
   * should be released in {@link #onDisabled()} (if not before).
   *
   * @see AudioSink.Listener#onAudioSessionId(int)
   */
  protected void onAudioSessionId(int audioSessionId) {
    // Do nothing.
  }

  /**
   * @see AudioSink.Listener#onPositionDiscontinuity()
   */
  protected void onAudioTrackPositionDiscontinuity() {
    // Do nothing.
  }

  /**
   * @see AudioSink.Listener#onUnderrun(int, long, long)
   */
  protected void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs,
      long elapsedSinceLastFeedMs) {
    // Do nothing.
  }

  @Override
  protected void onEnabled(boolean joining) throws ExoPlaybackException {
    super.onEnabled(joining);
    eventDispatcher.enabled(decoderCounters);
    int tunnelingAudioSessionId = getConfiguration().tunnelingAudioSessionId;
    if (tunnelingAudioSessionId != C.AUDIO_SESSION_ID_UNSET) {
      audioSink.enableTunnelingV21(tunnelingAudioSessionId);
    } else {
      audioSink.disableTunneling();
    }
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    super.onPositionReset(positionUs, joining);
    audioSink.reset();
    currentPositionUs = positionUs;
    allowFirstBufferPositionDiscontinuity = true;
    allowPositionDiscontinuity = true;
  }

  @Override
  protected void onStarted() {
    super.onStarted();
    audioSink.play();
  }

  @Override
  protected void onStopped() {
    updateCurrentPosition();
    audioSink.pause();
    super.onStopped();
  }

  @Override
  protected void onDisabled() {
    try {
      audioSink.release();
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
    return super.isEnded() && audioSink.isEnded();
  }

  @Override
  public boolean isReady() {
    return audioSink.hasPendingData() || super.isReady();
  }

  @Override
  public long getPositionUs() {
    if (getState() == STATE_STARTED) {
      updateCurrentPosition();
    }
    return currentPositionUs;
  }

  @Override
  public PlaybackParameters setPlaybackParameters(PlaybackParameters playbackParameters) {
    return audioSink.setPlaybackParameters(playbackParameters);
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return audioSink.getPlaybackParameters();
  }

  @Override
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
      audioSink.handleDiscontinuity();
      return true;
    }

    try {
      if (audioSink.handleBuffer(buffer, bufferPresentationTimeUs)) {
        codec.releaseOutputBuffer(bufferIndex, false);
        decoderCounters.renderedOutputBufferCount++;
        return true;
      }
    } catch (AudioSink.InitializationException | AudioSink.WriteException e) {
      throw ExoPlaybackException.createForRenderer(e, getIndex());
    }
    return false;
  }

  @Override
  protected void renderToEndOfStream() throws ExoPlaybackException {
    try {
      audioSink.playToEndOfStream();
    } catch (AudioSink.WriteException e) {
      throw ExoPlaybackException.createForRenderer(e, getIndex());
    }
  }

  @Override
  public void handleMessage(int messageType, Object message) throws ExoPlaybackException {
    switch (messageType) {
      case C.MSG_SET_VOLUME:
        audioSink.setVolume((Float) message);
        break;
      case C.MSG_SET_AUDIO_ATTRIBUTES:
        AudioAttributes audioAttributes = (AudioAttributes) message;
        audioSink.setAudioAttributes(audioAttributes);
        break;
      default:
        super.handleMessage(messageType, message);
        break;
    }
  }

  /**
   * Returns a maximum input size suitable for configuring a codec for {@code format} in a way that
   * will allow possible adaptation to other compatible formats in {@code streamFormats}.
   *
   * @param codecInfo A {@link MediaCodecInfo} describing the decoder.
   * @param format The format for which the codec is being configured.
   * @param streamFormats The possible stream formats.
   * @return A suitable maximum input size.
   */
  protected int getCodecMaxInputSize(
      MediaCodecInfo codecInfo, Format format, Format[] streamFormats) {
    int maxInputSize = getCodecMaxInputSize(codecInfo, format);
    // if (streamFormats.length == 1) {
    //   // The single entry in streamFormats must correspond to the format for which the codec is
    //   // being configured.
    //   return maxInputSize;
    // }
    // for (Format streamFormat : streamFormats) {
    //   if (areAdaptationCompatible(format, streamFormat)) {
    //     maxInputSize = Math.max(maxInputSize, getCodecMaxInputSize(codecInfo, streamFormat));
    //   }
    // }
    return maxInputSize;
  }

  /**
   * Returns a maximum input buffer size for a given format.
   *
   * @param codecInfo A {@link MediaCodecInfo} describing the decoder.
   * @param format The format.
   * @return A maximum input buffer size in bytes, or {@link Format#NO_VALUE} if a maximum could not
   *     be determined.
   */
  private int getCodecMaxInputSize(MediaCodecInfo codecInfo, Format format) {
    if (Util.SDK_INT < 24 && "OMX.google.raw.decoder".equals(codecInfo.name)) {
      // OMX.google.raw.decoder didn't resize its output buffers correctly prior to N, so there's no
      // point requesting a non-default input size. Doing so may cause a native crash, where-as not
      // doing so will cause a more controlled failure when attempting to fill an input buffer. See:
      // https://github.com/google/ExoPlayer/issues/4057.
      boolean needsRawDecoderWorkaround = true;
      if (Util.SDK_INT == 23) {
        PackageManager packageManager = context.getPackageManager();
        if (packageManager != null
            && packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
          // The workaround is not required for AndroidTV devices running M.
          needsRawDecoderWorkaround = false;
        }
      }
      if (needsRawDecoderWorkaround) {
        return Format.NO_VALUE;
      }
    }
    return format.maxInputSize;
  }

  /**
   * Returns the framework {@link MediaFormat} that can be used to configure a {@link MediaCodec}
   * for decoding the given {@link Format} for playback.
   *
   * @param format The format of the media.
   * @param codecMimeType The MIME type handled by the codec.
   * @param codecMaxInputSize The maximum input size supported by the codec.
   * @param codecOperatingRate The codec operating rate, or {@link #CODEC_OPERATING_RATE_UNSET} if
   *     no codec operating rate should be set.
   * @return The framework media format.
   */
  @SuppressLint("InlinedApi")
  protected MediaFormat getMediaFormat(
      Format format, String codecMimeType, int codecMaxInputSize, float codecOperatingRate) {
    MediaFormat mediaFormat = new MediaFormat();
    // Set format parameters that should always be set.
    mediaFormat.setString(MediaFormat.KEY_MIME, codecMimeType);
    mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, format.channelCount);
    mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, format.sampleRate);
    MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData);
    // Set codec max values.
    MediaFormatUtil.maybeSetInteger(mediaFormat, MediaFormat.KEY_MAX_INPUT_SIZE, codecMaxInputSize);
    // Set codec configuration values.
    if (Util.SDK_INT >= 23) {
      mediaFormat.setInteger(MediaFormat.KEY_PRIORITY, 0 /* realtime priority */);
      if (codecOperatingRate != CODEC_OPERATING_RATE_UNSET) {
        mediaFormat.setFloat(MediaFormat.KEY_OPERATING_RATE, codecOperatingRate);
      }
    }
    return mediaFormat;
  }

  private void updateCurrentPosition() {
    long newCurrentPositionUs = audioSink.getCurrentPositionUs(isEnded());
    if (newCurrentPositionUs != AudioSink.CURRENT_POSITION_NOT_SET) {
      currentPositionUs =
          allowPositionDiscontinuity
              ? newCurrentPositionUs
              : Math.max(currentPositionUs, newCurrentPositionUs);
      allowPositionDiscontinuity = false;
    }
  }

  /**
   * Returns whether a codec with suitable maximum input size will support adaptation between two
   * {@link Format}s.
   *
   * @param first The first format.
   * @param second The second format.
   * @return Whether the codec will support adaptation between the two {@link Format}s.
   */
  private static boolean areAdaptationCompatible(Format first, Format second) {
    return first.sampleMimeType.equals(second.sampleMimeType)
        && first.channelCount == second.channelCount
        && first.sampleRate == second.sampleRate
        && first.encoderDelay == 0
        && first.encoderPadding == 0
        && second.encoderDelay == 0
        && second.encoderPadding == 0
        && first.initializationDataEquals(second);
  }

  /**
   * Returns whether the decoder is known to output six audio channels when provided with input with
   * fewer than six channels.
   * <p>
   * See [Internal: b/35655036].
   */
  private static boolean codecNeedsDiscardChannelsWorkaround(String codecName) {
    // The workaround applies to Samsung Galaxy S6 and Samsung Galaxy S7.
    return Util.SDK_INT < 24 && "OMX.SEC.aac.dec".equals(codecName)
        && "samsung".equals(Util.MANUFACTURER)
        && (Util.DEVICE.startsWith("zeroflte") || Util.DEVICE.startsWith("herolte")
        || Util.DEVICE.startsWith("heroqlte"));
  }

  private final class AudioSinkListener implements AudioSink.Listener {

    @Override
    public void onAudioSessionId(int audioSessionId) {
      eventDispatcher.audioSessionId(audioSessionId);
      MediaCodecAudioRenderer.this.onAudioSessionId(audioSessionId);
    }

    @Override
    public void onPositionDiscontinuity() {
      onAudioTrackPositionDiscontinuity();
      // We are out of sync so allow currentPositionUs to jump backwards.
      MediaCodecAudioRenderer.this.allowPositionDiscontinuity = true;
    }

    @Override
    public void onUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
      eventDispatcher.audioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
      onAudioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
    }

  }

}
