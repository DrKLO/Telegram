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

import static com.google.android.exoplayer2.audio.AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Util.constrainValue;
import static com.google.common.base.MoreObjects.firstNonNull;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.annotation.SuppressLint;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.PlaybackParams;
import android.media.metrics.LogSessionId;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Pair;
import androidx.annotation.DoNotInline;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer.AudioOffloadListener;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.InlineMe;
import com.google.errorprone.annotations.InlineMeValidationDisabled;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * Plays audio data. The implementation delegates to an {@link AudioTrack} and handles playback
 * position smoothing, non-blocking writes and reconfiguration.
 *
 * <p>If tunneling mode is enabled, care must be taken that audio processors do not output buffers
 * with a different duration than their input, and buffer processors must produce output
 * corresponding to their last input immediately after that input is queued. This means that, for
 * example, speed adjustment is not possible while using tunneling.
 */
public final class DefaultAudioSink implements AudioSink {

  /**
   * If an attempt to instantiate an AudioTrack with a buffer size larger than this value fails, a
   * second attempt is made using this buffer size.
   */
  private static final int AUDIO_TRACK_SMALLER_BUFFER_RETRY_SIZE = 1_000_000;

  /**
   * Thrown when the audio track has provided a spurious timestamp, if {@link
   * #failOnSpuriousAudioTimestamp} is set.
   */
  public static final class InvalidAudioTrackTimestampException extends RuntimeException {

    /**
     * Creates a new invalid timestamp exception with the specified message.
     *
     * @param message The detail message for this exception.
     */
    private InvalidAudioTrackTimestampException(String message) {
      super(message);
    }
  }

  /**
   * @deprecated Use {@link com.google.android.exoplayer2.audio.AudioProcessorChain}.
   */
  @Deprecated
  public interface AudioProcessorChain
      extends com.google.android.exoplayer2.audio.AudioProcessorChain {}

  /**
   * The default audio processor chain, which applies a (possibly empty) chain of user-defined audio
   * processors followed by {@link SilenceSkippingAudioProcessor} and {@link SonicAudioProcessor}.
   */
  @SuppressWarnings("deprecation")
  public static class DefaultAudioProcessorChain implements AudioProcessorChain {

    private final AudioProcessor[] audioProcessors;
    private final SilenceSkippingAudioProcessor silenceSkippingAudioProcessor;
    private final SonicAudioProcessor sonicAudioProcessor;

    /**
     * Creates a new default chain of audio processors, with the user-defined {@code
     * audioProcessors} applied before silence skipping and speed adjustment processors.
     */
    public DefaultAudioProcessorChain(AudioProcessor... audioProcessors) {
      this(audioProcessors, new SilenceSkippingAudioProcessor(), new SonicAudioProcessor());
    }

    /**
     * Creates a new default chain of audio processors, with the user-defined {@code
     * audioProcessors} applied before silence skipping and speed adjustment processors.
     */
    public DefaultAudioProcessorChain(
        AudioProcessor[] audioProcessors,
        SilenceSkippingAudioProcessor silenceSkippingAudioProcessor,
        SonicAudioProcessor sonicAudioProcessor) {
      // The passed-in type may be more specialized than AudioProcessor[], so allocate a new array
      // rather than using Arrays.copyOf.
      this.audioProcessors = new AudioProcessor[audioProcessors.length + 2];
      System.arraycopy(
          /* src= */ audioProcessors,
          /* srcPos= */ 0,
          /* dest= */ this.audioProcessors,
          /* destPos= */ 0,
          /* length= */ audioProcessors.length);
      this.silenceSkippingAudioProcessor = silenceSkippingAudioProcessor;
      this.sonicAudioProcessor = sonicAudioProcessor;
      this.audioProcessors[audioProcessors.length] = silenceSkippingAudioProcessor;
      this.audioProcessors[audioProcessors.length + 1] = sonicAudioProcessor;
    }

    @Override
    public AudioProcessor[] getAudioProcessors() {
      return audioProcessors;
    }

    @Override
    public PlaybackParameters applyPlaybackParameters(PlaybackParameters playbackParameters) {
      sonicAudioProcessor.setSpeed(playbackParameters.speed);
      sonicAudioProcessor.setPitch(playbackParameters.pitch);
      return playbackParameters;
    }

    @Override
    public boolean applySkipSilenceEnabled(boolean skipSilenceEnabled) {
      silenceSkippingAudioProcessor.setEnabled(skipSilenceEnabled);
      return skipSilenceEnabled;
    }

    @Override
    public long getMediaDuration(long playoutDuration) {
      return sonicAudioProcessor.getMediaDuration(playoutDuration);
    }

    @Override
    public long getSkippedOutputFrameCount() {
      return silenceSkippingAudioProcessor.getSkippedFrames();
    }
  }

  /** Provides the buffer size to use when creating an {@link AudioTrack}. */
  public interface AudioTrackBufferSizeProvider {
    /** Default instance. */
    AudioTrackBufferSizeProvider DEFAULT =
        new DefaultAudioTrackBufferSizeProvider.Builder().build();
    /**
     * Returns the buffer size to use when creating an {@link AudioTrack} for a specific format and
     * output mode.
     *
     * @param minBufferSizeInBytes The minimum buffer size in bytes required to play this format.
     *     See {@link AudioTrack#getMinBufferSize}.
     * @param encoding The {@link C.Encoding} of the format.
     * @param outputMode How the audio will be played. One of the {@link OutputMode output modes}.
     * @param pcmFrameSize The size of the PCM frames if the {@code encoding} is PCM, 1 otherwise,
     *     in bytes.
     * @param sampleRate The sample rate of the format, in Hz.
     * @param bitrate The bitrate of the audio stream if the stream is compressed, or {@link
     *     Format#NO_VALUE} if {@code encoding} is PCM or the bitrate is not known.
     * @param maxAudioTrackPlaybackSpeed The maximum speed the content will be played using {@link
     *     AudioTrack#setPlaybackParams}. 0.5 is 2x slow motion, 1 is real time, 2 is 2x fast
     *     forward, etc. This will be {@code 1} unless {@link
     *     Builder#setEnableAudioTrackPlaybackParams} is enabled.
     * @return The computed buffer size in bytes. It should always be {@code >=
     *     minBufferSizeInBytes}. The computed buffer size must contain an integer number of frames:
     *     {@code bufferSizeInBytes % pcmFrameSize == 0}.
     */
    int getBufferSizeInBytes(
        int minBufferSizeInBytes,
        @C.Encoding int encoding,
        @OutputMode int outputMode,
        int pcmFrameSize,
        int sampleRate,
        int bitrate,
        double maxAudioTrackPlaybackSpeed);
  }

  /** A builder to create {@link DefaultAudioSink} instances. */
  public static final class Builder {

    private AudioCapabilities audioCapabilities;
    @Nullable private com.google.android.exoplayer2.audio.AudioProcessorChain audioProcessorChain;
    private boolean enableFloatOutput;
    private boolean enableAudioTrackPlaybackParams;
    private int offloadMode;
    AudioTrackBufferSizeProvider audioTrackBufferSizeProvider;
    @Nullable AudioOffloadListener audioOffloadListener;

    /** Creates a new builder. */
    public Builder() {
      audioCapabilities = DEFAULT_AUDIO_CAPABILITIES;
      offloadMode = OFFLOAD_MODE_DISABLED;
      audioTrackBufferSizeProvider = AudioTrackBufferSizeProvider.DEFAULT;
    }

    /**
     * Sets audio capabilities for playback on this device. May be {@code null} if the default
     * capabilities (no encoded audio passthrough support) should be assumed.
     *
     * <p>Default is {@link AudioCapabilities#DEFAULT_AUDIO_CAPABILITIES}.
     */
    @CanIgnoreReturnValue
    public Builder setAudioCapabilities(AudioCapabilities audioCapabilities) {
      checkNotNull(audioCapabilities);
      this.audioCapabilities = audioCapabilities;
      return this;
    }

    /**
     * Sets an array of {@link AudioProcessor AudioProcessors}s that will process PCM audio before
     * output. May be empty. Equivalent of {@code setAudioProcessorChain(new
     * DefaultAudioProcessorChain(audioProcessors)}.
     *
     * <p>The default value is an empty array.
     */
    @CanIgnoreReturnValue
    public Builder setAudioProcessors(AudioProcessor[] audioProcessors) {
      checkNotNull(audioProcessors);
      return setAudioProcessorChain(new DefaultAudioProcessorChain(audioProcessors));
    }

    /**
     * Sets the {@link com.google.android.exoplayer2.audio.AudioProcessorChain} to process audio
     * before playback. The instance passed in must not be reused in other sinks. Processing chains
     * are only supported for PCM playback (not passthrough or offload).
     *
     * <p>By default, no processing will be applied.
     */
    @CanIgnoreReturnValue
    public Builder setAudioProcessorChain(
        com.google.android.exoplayer2.audio.AudioProcessorChain audioProcessorChain) {
      checkNotNull(audioProcessorChain);
      this.audioProcessorChain = audioProcessorChain;
      return this;
    }

    /**
     * Sets whether to enable 32-bit float output or integer output. Where possible, 32-bit float
     * output will be used if the input is 32-bit float, and also if the input is high resolution
     * (24-bit or 32-bit) integer PCM. Float output is supported from API level 21. Audio processing
     * (for example, speed adjustment) will not be available when float output is in use.
     *
     * <p>The default value is {@code false}.
     */
    @CanIgnoreReturnValue
    public Builder setEnableFloatOutput(boolean enableFloatOutput) {
      this.enableFloatOutput = enableFloatOutput;
      return this;
    }

    /**
     * Sets whether to control the playback speed using the platform implementation (see {@link
     * AudioTrack#setPlaybackParams(PlaybackParams)}), if supported. If set to {@code false}, speed
     * up/down of the audio will be done by ExoPlayer (see {@link SonicAudioProcessor}). Platform
     * speed adjustment is lower latency, but less reliable.
     *
     * <p>The default value is {@code false}.
     */
    @CanIgnoreReturnValue
    public Builder setEnableAudioTrackPlaybackParams(boolean enableAudioTrackPlaybackParams) {
      this.enableAudioTrackPlaybackParams = enableAudioTrackPlaybackParams;
      return this;
    }

    /**
     * Sets the offload mode. If an audio format can be both played with offload and encoded audio
     * passthrough, it will be played in offload. Audio offload is supported from API level 29. Most
     * Android devices can only support one offload {@link AudioTrack} at a time and can invalidate
     * it at any time. Thus an app can never be guaranteed that it will be able to play in offload.
     * Audio processing (for example, speed adjustment) will not be available when offload is in
     * use.
     *
     * <p>The default value is {@link #OFFLOAD_MODE_DISABLED}.
     */
    @CanIgnoreReturnValue
    public Builder setOffloadMode(@OffloadMode int offloadMode) {
      this.offloadMode = offloadMode;
      return this;
    }

    /**
     * Sets an {@link AudioTrackBufferSizeProvider} to compute the buffer size when {@link
     * #configure} is called with {@code specifiedBufferSize == 0}.
     *
     * <p>The default value is {@link AudioTrackBufferSizeProvider#DEFAULT}.
     */
    @CanIgnoreReturnValue
    public Builder setAudioTrackBufferSizeProvider(
        AudioTrackBufferSizeProvider audioTrackBufferSizeProvider) {
      this.audioTrackBufferSizeProvider = audioTrackBufferSizeProvider;
      return this;
    }

    /**
     * Sets an optional {@link AudioOffloadListener} to receive events relevant to offloaded
     * playback.
     *
     * <p>The default value is null.
     */
    @CanIgnoreReturnValue
    public Builder setExperimentalAudioOffloadListener(
        @Nullable AudioOffloadListener audioOffloadListener) {
      this.audioOffloadListener = audioOffloadListener;
      return this;
    }

    /** Builds the {@link DefaultAudioSink}. Must only be called once per Builder instance. */
    public DefaultAudioSink build() {
      if (audioProcessorChain == null) {
        audioProcessorChain = new DefaultAudioProcessorChain();
      }
      return new DefaultAudioSink(this);
    }
  }

  /** The default playback speed. */
  public static final float DEFAULT_PLAYBACK_SPEED = 1f;
  /** The minimum allowed playback speed. Lower values will be constrained to fall in range. */
  public static final float MIN_PLAYBACK_SPEED = 0.1f;
  /** The maximum allowed playback speed. Higher values will be constrained to fall in range. */
  public static final float MAX_PLAYBACK_SPEED = 8f;
  /** The minimum allowed pitch factor. Lower values will be constrained to fall in range. */
  public static final float MIN_PITCH = 0.1f;
  /** The maximum allowed pitch factor. Higher values will be constrained to fall in range. */
  public static final float MAX_PITCH = 8f;

  /** The default skip silence flag. */
  private static final boolean DEFAULT_SKIP_SILENCE = false;

  /** Audio offload mode configuration. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    OFFLOAD_MODE_DISABLED,
    OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED,
    OFFLOAD_MODE_ENABLED_GAPLESS_NOT_REQUIRED,
    OFFLOAD_MODE_ENABLED_GAPLESS_DISABLED
  })
  public @interface OffloadMode {}

  /** The audio sink will never play in offload mode. */
  public static final int OFFLOAD_MODE_DISABLED = 0;
  /**
   * The audio sink will prefer offload playback except if the track is gapless and the device does
   * not advertise support for gapless playback in offload.
   *
   * <p>Use this option to prioritize seamless transitions between tracks of the same album to power
   * savings.
   */
  public static final int OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED = 1;
  /**
   * The audio sink will prefer offload playback even if this might result in silence gaps between
   * tracks.
   *
   * <p>Use this option to prioritize battery saving at the cost of a possible non seamless
   * transitions between tracks of the same album.
   */
  public static final int OFFLOAD_MODE_ENABLED_GAPLESS_NOT_REQUIRED = 2;
  /**
   * The audio sink will prefer offload playback, disabling gapless offload support.
   *
   * <p>Use this option if gapless has undesirable side effects. For example if it introduces
   * hardware issues.
   */
  public static final int OFFLOAD_MODE_ENABLED_GAPLESS_DISABLED = 3;

  /** Output mode of the audio sink. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({OUTPUT_MODE_PCM, OUTPUT_MODE_OFFLOAD, OUTPUT_MODE_PASSTHROUGH})
  public @interface OutputMode {}

  /** The audio sink plays PCM audio. */
  public static final int OUTPUT_MODE_PCM = 0;
  /** The audio sink plays encoded audio in offload. */
  public static final int OUTPUT_MODE_OFFLOAD = 1;
  /** The audio sink plays encoded audio in passthrough. */
  public static final int OUTPUT_MODE_PASSTHROUGH = 2;

  /**
   * Native error code equivalent of {@link AudioTrack#ERROR_DEAD_OBJECT} to workaround missing
   * error code translation on some devices.
   *
   * <p>On some devices, AudioTrack native error codes are not always converted to their SDK
   * equivalent.
   *
   * <p>For example: {@link AudioTrack#write(byte[], int, int)} can return -32 instead of {@link
   * AudioTrack#ERROR_DEAD_OBJECT}.
   */
  private static final int ERROR_NATIVE_DEAD_OBJECT = -32;

  /**
   * The duration for which failed attempts to initialize or write to the audio track may be retried
   * before throwing an exception, in milliseconds.
   */
  private static final int AUDIO_TRACK_RETRY_DURATION_MS = 100;

  private static final String TAG = "DefaultAudioSink";

  /**
   * Whether to throw an {@link InvalidAudioTrackTimestampException} when a spurious timestamp is
   * reported from {@link AudioTrack#getTimestamp}.
   *
   * <p>The flag must be set before creating a player. Should be set to {@code true} for testing and
   * debugging purposes only.
   */
  public static boolean failOnSpuriousAudioTimestamp = false;

  private static final Object releaseExecutorLock = new Object();

  @GuardedBy("releaseExecutorLock")
  @Nullable
  private static ExecutorService releaseExecutor;

  @GuardedBy("releaseExecutorLock")
  private static int pendingReleaseCount;

  private final AudioCapabilities audioCapabilities;
  private final com.google.android.exoplayer2.audio.AudioProcessorChain audioProcessorChain;
  private final boolean enableFloatOutput;
  private final ChannelMappingAudioProcessor channelMappingAudioProcessor;
  private final TrimmingAudioProcessor trimmingAudioProcessor;
  private final AudioProcessor[] toIntPcmAvailableAudioProcessors;
  private final AudioProcessor[] toFloatPcmAvailableAudioProcessors;
  private final ConditionVariable releasingConditionVariable;
  private final AudioTrackPositionTracker audioTrackPositionTracker;
  private final ArrayDeque<MediaPositionParameters> mediaPositionParametersCheckpoints;
  private final boolean enableAudioTrackPlaybackParams;
  private final @OffloadMode int offloadMode;
  private @MonotonicNonNull StreamEventCallbackV29 offloadStreamEventCallbackV29;
  private final PendingExceptionHolder<InitializationException>
      initializationExceptionPendingExceptionHolder;
  private final PendingExceptionHolder<WriteException> writeExceptionPendingExceptionHolder;
  private final AudioTrackBufferSizeProvider audioTrackBufferSizeProvider;
  @Nullable private final AudioOffloadListener audioOffloadListener;

  @Nullable private PlayerId playerId;
  @Nullable private Listener listener;
  @Nullable private Configuration pendingConfiguration;
  private @MonotonicNonNull Configuration configuration;
  @Nullable private AudioTrack audioTrack;

  private AudioAttributes audioAttributes;
  @Nullable private MediaPositionParameters afterDrainParameters;
  private MediaPositionParameters mediaPositionParameters;
  private PlaybackParameters audioTrackPlaybackParameters;

  @Nullable private ByteBuffer avSyncHeader;
  private int bytesUntilNextAvSync;

  private long submittedPcmBytes;
  private long submittedEncodedFrames;
  private long writtenPcmBytes;
  private long writtenEncodedFrames;
  private int framesPerEncodedSample;
  private boolean startMediaTimeUsNeedsSync;
  private boolean startMediaTimeUsNeedsInit;
  private long startMediaTimeUs;
  private float volume;

  private AudioProcessor[] activeAudioProcessors;
  private ByteBuffer[] outputBuffers;
  @Nullable private ByteBuffer inputBuffer;
  private int inputBufferAccessUnitCount;
  @Nullable private ByteBuffer outputBuffer;
  private @MonotonicNonNull byte[] preV21OutputBuffer;
  private int preV21OutputBufferOffset;
  private int drainingAudioProcessorIndex;
  private boolean handledEndOfStream;
  private boolean stoppedAudioTrack;

  private boolean playing;
  private boolean externalAudioSessionIdProvided;
  private int audioSessionId;
  private AuxEffectInfo auxEffectInfo;
  @Nullable private AudioDeviceInfoApi23 preferredDevice;
  private boolean tunneling;
  private long lastFeedElapsedRealtimeMs;
  private boolean offloadDisabledUntilNextConfiguration;
  private boolean isWaitingForOffloadEndOfStreamHandled;

  /**
   * @deprecated Use {@link Builder}.
   */
  @Deprecated
  @InlineMeValidationDisabled("Migrate constructor to Builder")
  @InlineMe(
      replacement =
          "new DefaultAudioSink.Builder()"
              + ".setAudioCapabilities(audioCapabilities)"
              + ".setAudioProcessors(audioProcessors)"
              + ".build()",
      imports = "com.google.android.exoplayer2.audio.DefaultAudioSink")
  public DefaultAudioSink(
      @Nullable AudioCapabilities audioCapabilities, AudioProcessor[] audioProcessors) {
    this(
        new Builder()
            .setAudioCapabilities(firstNonNull(audioCapabilities, DEFAULT_AUDIO_CAPABILITIES))
            .setAudioProcessors(audioProcessors));
  }

  /**
   * @deprecated Use {@link Builder}.
   */
  @Deprecated
  @InlineMeValidationDisabled("Migrate constructor to Builder")
  @InlineMe(
      replacement =
          "new DefaultAudioSink.Builder()"
              + ".setAudioCapabilities(audioCapabilities)"
              + ".setAudioProcessors(audioProcessors)"
              + ".setEnableFloatOutput(enableFloatOutput)"
              + ".build()",
      imports = "com.google.android.exoplayer2.audio.DefaultAudioSink")
  public DefaultAudioSink(
      @Nullable AudioCapabilities audioCapabilities,
      AudioProcessor[] audioProcessors,
      boolean enableFloatOutput) {
    this(
        new Builder()
            .setAudioCapabilities(firstNonNull(audioCapabilities, DEFAULT_AUDIO_CAPABILITIES))
            .setAudioProcessors(audioProcessors)
            .setEnableFloatOutput(enableFloatOutput));
  }

  /**
   * @deprecated Use {@link Builder}.
   */
  @Deprecated
  @InlineMeValidationDisabled("Migrate constructor to Builder")
  @InlineMe(
      replacement =
          "new DefaultAudioSink.Builder()"
              + ".setAudioCapabilities(audioCapabilities)"
              + ".setAudioProcessorChain(audioProcessorChain)"
              + ".setEnableFloatOutput(enableFloatOutput)"
              + ".setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)"
              + ".setOffloadMode(offloadMode)"
              + ".build()",
      imports = "com.google.android.exoplayer2.audio.DefaultAudioSink")
  public DefaultAudioSink(
      @Nullable AudioCapabilities audioCapabilities,
      AudioProcessorChain audioProcessorChain,
      boolean enableFloatOutput,
      boolean enableAudioTrackPlaybackParams,
      @OffloadMode int offloadMode) {
    this(
        new Builder()
            .setAudioCapabilities(firstNonNull(audioCapabilities, DEFAULT_AUDIO_CAPABILITIES))
            .setAudioProcessorChain(audioProcessorChain)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setOffloadMode(offloadMode));
  }

  @RequiresNonNull("#1.audioProcessorChain")
  private DefaultAudioSink(Builder builder) {
    audioCapabilities = builder.audioCapabilities;
    audioProcessorChain = builder.audioProcessorChain;
    enableFloatOutput = Util.SDK_INT >= 21 && builder.enableFloatOutput;
    enableAudioTrackPlaybackParams = Util.SDK_INT >= 23 && builder.enableAudioTrackPlaybackParams;
    offloadMode = Util.SDK_INT >= 29 ? builder.offloadMode : OFFLOAD_MODE_DISABLED;
    audioTrackBufferSizeProvider = builder.audioTrackBufferSizeProvider;
    releasingConditionVariable = new ConditionVariable(Clock.DEFAULT);
    releasingConditionVariable.open();
    audioTrackPositionTracker = new AudioTrackPositionTracker(new PositionTrackerListener());
    channelMappingAudioProcessor = new ChannelMappingAudioProcessor();
    trimmingAudioProcessor = new TrimmingAudioProcessor();
    ArrayList<AudioProcessor> toIntPcmAudioProcessors = new ArrayList<>();
    Collections.addAll(
        toIntPcmAudioProcessors,
        new ResamplingAudioProcessor(),
        channelMappingAudioProcessor,
        trimmingAudioProcessor);
    Collections.addAll(toIntPcmAudioProcessors, audioProcessorChain.getAudioProcessors());
    toIntPcmAvailableAudioProcessors = toIntPcmAudioProcessors.toArray(new AudioProcessor[0]);
    toFloatPcmAvailableAudioProcessors = new AudioProcessor[] {new FloatResamplingAudioProcessor()};
    volume = 1f;
    audioAttributes = AudioAttributes.DEFAULT;
    audioSessionId = C.AUDIO_SESSION_ID_UNSET;
    auxEffectInfo = new AuxEffectInfo(AuxEffectInfo.NO_AUX_EFFECT_ID, 0f);
    mediaPositionParameters =
        new MediaPositionParameters(
            PlaybackParameters.DEFAULT,
            DEFAULT_SKIP_SILENCE,
            /* mediaTimeUs= */ 0,
            /* audioTrackPositionUs= */ 0);
    audioTrackPlaybackParameters = PlaybackParameters.DEFAULT;
    drainingAudioProcessorIndex = C.INDEX_UNSET;
    activeAudioProcessors = new AudioProcessor[0];
    outputBuffers = new ByteBuffer[0];
    mediaPositionParametersCheckpoints = new ArrayDeque<>();
    initializationExceptionPendingExceptionHolder =
        new PendingExceptionHolder<>(AUDIO_TRACK_RETRY_DURATION_MS);
    writeExceptionPendingExceptionHolder =
        new PendingExceptionHolder<>(AUDIO_TRACK_RETRY_DURATION_MS);
    audioOffloadListener = builder.audioOffloadListener;
  }

  // AudioSink implementation.

  @Override
  public void setListener(Listener listener) {
    this.listener = listener;
  }

  @Override
  public void setPlayerId(@Nullable PlayerId playerId) {
    this.playerId = playerId;
  }

  @Override
  public boolean supportsFormat(Format format) {
    return getFormatSupport(format) != SINK_FORMAT_UNSUPPORTED;
  }

  @Override
  public @SinkFormatSupport int getFormatSupport(Format format) {
    if (MimeTypes.AUDIO_RAW.equals(format.sampleMimeType)) {
      if (!Util.isEncodingLinearPcm(format.pcmEncoding)) {
        Log.w(TAG, "Invalid PCM encoding: " + format.pcmEncoding);
        return SINK_FORMAT_UNSUPPORTED;
      }
      if (format.pcmEncoding == C.ENCODING_PCM_16BIT
          || (enableFloatOutput && format.pcmEncoding == C.ENCODING_PCM_FLOAT)) {
        return SINK_FORMAT_SUPPORTED_DIRECTLY;
      }
      // We can resample all linear PCM encodings to 16-bit integer PCM, which AudioTrack is
      // guaranteed to support.
      return SINK_FORMAT_SUPPORTED_WITH_TRANSCODING;
    }
    if (!offloadDisabledUntilNextConfiguration && useOffloadedPlayback(format, audioAttributes)) {
      return SINK_FORMAT_SUPPORTED_DIRECTLY;
    }
    if (audioCapabilities.isPassthroughPlaybackSupported(format)) {
      return SINK_FORMAT_SUPPORTED_DIRECTLY;
    }
    return SINK_FORMAT_UNSUPPORTED;
  }

  @Override
  public long getCurrentPositionUs(boolean sourceEnded) {
    if (!isAudioTrackInitialized() || startMediaTimeUsNeedsInit) {
      return CURRENT_POSITION_NOT_SET;
    }
    long positionUs = audioTrackPositionTracker.getCurrentPositionUs(sourceEnded);
    positionUs = min(positionUs, configuration.framesToDurationUs(getWrittenFrames()));
    return applySkipping(applyMediaPositionParameters(positionUs));
  }

  @Override
  public void configure(Format inputFormat, int specifiedBufferSize, @Nullable int[] outputChannels)
      throws ConfigurationException {
    int inputPcmFrameSize;
    @Nullable AudioProcessor[] availableAudioProcessors;

    @OutputMode int outputMode;
    @C.Encoding int outputEncoding;
    int outputSampleRate;
    int outputChannelConfig;
    int outputPcmFrameSize;

    if (MimeTypes.AUDIO_RAW.equals(inputFormat.sampleMimeType)) {
      Assertions.checkArgument(Util.isEncodingLinearPcm(inputFormat.pcmEncoding));

      inputPcmFrameSize = Util.getPcmFrameSize(inputFormat.pcmEncoding, inputFormat.channelCount);
      availableAudioProcessors =
          shouldUseFloatOutput(inputFormat.pcmEncoding)
              ? toFloatPcmAvailableAudioProcessors
              : toIntPcmAvailableAudioProcessors;

      trimmingAudioProcessor.setTrimFrameCount(
          inputFormat.encoderDelay, inputFormat.encoderPadding);

      if (Util.SDK_INT < 21 && inputFormat.channelCount == 8 && outputChannels == null) {
        // AudioTrack doesn't support 8 channel output before Android L. Discard the last two (side)
        // channels to give a 6 channel stream that is supported.
        outputChannels = new int[6];
        for (int i = 0; i < outputChannels.length; i++) {
          outputChannels[i] = i;
        }
      }
      channelMappingAudioProcessor.setChannelMap(outputChannels);

      AudioProcessor.AudioFormat outputFormat =
          new AudioProcessor.AudioFormat(
              inputFormat.sampleRate, inputFormat.channelCount, inputFormat.pcmEncoding);
      for (AudioProcessor audioProcessor : availableAudioProcessors) {
        try {
          AudioProcessor.AudioFormat nextFormat = audioProcessor.configure(outputFormat);
          if (audioProcessor.isActive()) {
            outputFormat = nextFormat;
          }
        } catch (UnhandledAudioFormatException e) {
          throw new ConfigurationException(e, inputFormat);
        }
      }

      outputMode = OUTPUT_MODE_PCM;
      outputEncoding = outputFormat.encoding;
      outputSampleRate = outputFormat.sampleRate;
      outputChannelConfig = Util.getAudioTrackChannelConfig(outputFormat.channelCount);
      outputPcmFrameSize = Util.getPcmFrameSize(outputEncoding, outputFormat.channelCount);
    } else {
      inputPcmFrameSize = C.LENGTH_UNSET;
      availableAudioProcessors = new AudioProcessor[0];
      outputSampleRate = inputFormat.sampleRate;
      outputPcmFrameSize = C.LENGTH_UNSET;
      if (useOffloadedPlayback(inputFormat, audioAttributes)) {
        outputMode = OUTPUT_MODE_OFFLOAD;
        outputEncoding =
            MimeTypes.getEncoding(checkNotNull(inputFormat.sampleMimeType), inputFormat.codecs);
        outputChannelConfig = Util.getAudioTrackChannelConfig(inputFormat.channelCount);
      } else {
        outputMode = OUTPUT_MODE_PASSTHROUGH;
        @Nullable
        Pair<Integer, Integer> encodingAndChannelConfig =
            audioCapabilities.getEncodingAndChannelConfigForPassthrough(inputFormat);
        if (encodingAndChannelConfig == null) {
          throw new ConfigurationException(
              "Unable to configure passthrough for: " + inputFormat, inputFormat);
        }
        outputEncoding = encodingAndChannelConfig.first;
        outputChannelConfig = encodingAndChannelConfig.second;
      }
    }

    if (outputEncoding == C.ENCODING_INVALID) {
      throw new ConfigurationException(
          "Invalid output encoding (mode=" + outputMode + ") for: " + inputFormat, inputFormat);
    }
    if (outputChannelConfig == AudioFormat.CHANNEL_INVALID) {
      throw new ConfigurationException(
          "Invalid output channel config (mode=" + outputMode + ") for: " + inputFormat,
          inputFormat);
    }
    int bufferSize =
        specifiedBufferSize != 0
            ? specifiedBufferSize
            : audioTrackBufferSizeProvider.getBufferSizeInBytes(
                getAudioTrackMinBufferSize(outputSampleRate, outputChannelConfig, outputEncoding),
                outputEncoding,
                outputMode,
                outputPcmFrameSize != C.LENGTH_UNSET ? outputPcmFrameSize : 1,
                outputSampleRate,
                inputFormat.bitrate,
                enableAudioTrackPlaybackParams ? MAX_PLAYBACK_SPEED : DEFAULT_PLAYBACK_SPEED);

    offloadDisabledUntilNextConfiguration = false;
    Configuration pendingConfiguration =
        new Configuration(
            inputFormat,
            inputPcmFrameSize,
            outputMode,
            outputPcmFrameSize,
            outputSampleRate,
            outputChannelConfig,
            outputEncoding,
            bufferSize,
            availableAudioProcessors);
    if (isAudioTrackInitialized()) {
      this.pendingConfiguration = pendingConfiguration;
    } else {
      configuration = pendingConfiguration;
    }
  }

  private void setupAudioProcessors() {
    AudioProcessor[] audioProcessors = configuration.availableAudioProcessors;
    ArrayList<AudioProcessor> newAudioProcessors = new ArrayList<>();
    for (AudioProcessor audioProcessor : audioProcessors) {
      if (audioProcessor.isActive()) {
        newAudioProcessors.add(audioProcessor);
      } else {
        audioProcessor.flush();
      }
    }
    int count = newAudioProcessors.size();
    activeAudioProcessors = newAudioProcessors.toArray(new AudioProcessor[count]);
    outputBuffers = new ByteBuffer[count];
    flushAudioProcessors();
  }

  private void flushAudioProcessors() {
    for (int i = 0; i < activeAudioProcessors.length; i++) {
      AudioProcessor audioProcessor = activeAudioProcessors[i];
      audioProcessor.flush();
      outputBuffers[i] = audioProcessor.getOutput();
    }
  }

  private boolean initializeAudioTrack() throws InitializationException {
    // If we're asynchronously releasing a previous audio track then we wait until it has been
    // released. This guarantees that we cannot end up in a state where we have multiple audio
    // track instances. Without this guarantee it would be possible, in extreme cases, to exhaust
    // the shared memory that's available for audio track buffers. This would in turn cause the
    // initialization of the audio track to fail.
    if (!releasingConditionVariable.isOpen()) {
      return false;
    }

    audioTrack = buildAudioTrackWithRetry();
    if (isOffloadedPlayback(audioTrack)) {
      registerStreamEventCallbackV29(audioTrack);
      if (offloadMode != OFFLOAD_MODE_ENABLED_GAPLESS_DISABLED) {
        audioTrack.setOffloadDelayPadding(
            configuration.inputFormat.encoderDelay, configuration.inputFormat.encoderPadding);
      }
    }
    if (Util.SDK_INT >= 31 && playerId != null) {
      Api31.setLogSessionIdOnAudioTrack(audioTrack, playerId);
    }
    audioSessionId = audioTrack.getAudioSessionId();
    audioTrackPositionTracker.setAudioTrack(
        audioTrack,
        /* isPassthrough= */ configuration.outputMode == OUTPUT_MODE_PASSTHROUGH,
        configuration.outputEncoding,
        configuration.outputPcmFrameSize,
        configuration.bufferSize);
    setVolumeInternal();

    if (auxEffectInfo.effectId != AuxEffectInfo.NO_AUX_EFFECT_ID) {
      audioTrack.attachAuxEffect(auxEffectInfo.effectId);
      audioTrack.setAuxEffectSendLevel(auxEffectInfo.sendLevel);
    }
    if (preferredDevice != null && Util.SDK_INT >= 23) {
      Api23.setPreferredDeviceOnAudioTrack(audioTrack, preferredDevice);
    }

    startMediaTimeUsNeedsInit = true;
    return true;
  }

  @Override
  public void play() {
    playing = true;
    if (isAudioTrackInitialized()) {
      audioTrackPositionTracker.start();
      audioTrack.play();
    }
  }

  @Override
  public void handleDiscontinuity() {
    startMediaTimeUsNeedsSync = true;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean handleBuffer(
      ByteBuffer buffer, long presentationTimeUs, int encodedAccessUnitCount)
      throws InitializationException, WriteException {
    Assertions.checkArgument(inputBuffer == null || buffer == inputBuffer);

    if (pendingConfiguration != null) {
      if (!drainToEndOfStream()) {
        // There's still pending data in audio processors to write to the track.
        return false;
      } else if (!pendingConfiguration.canReuseAudioTrack(configuration)) {
        playPendingData();
        if (hasPendingData()) {
          // We're waiting for playout on the current audio track to finish.
          return false;
        }
        flush();
      } else {
        // The current audio track can be reused for the new configuration.
        configuration = pendingConfiguration;
        pendingConfiguration = null;
        if (isOffloadedPlayback(audioTrack)
            && offloadMode != OFFLOAD_MODE_ENABLED_GAPLESS_DISABLED) {
          // If the first track is very short (typically <1s), the offload AudioTrack might
          // not have started yet. Do not call setOffloadEndOfStream as it would throw.
          if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.setOffloadEndOfStream();
          }
          audioTrack.setOffloadDelayPadding(
              configuration.inputFormat.encoderDelay, configuration.inputFormat.encoderPadding);
          isWaitingForOffloadEndOfStreamHandled = true;
        }
      }
      // Re-apply playback parameters.
      applyAudioProcessorPlaybackParametersAndSkipSilence(presentationTimeUs);
    }

    if (!isAudioTrackInitialized()) {
      try {
        if (!initializeAudioTrack()) {
          // Not yet ready for initialization of a new AudioTrack.
          return false;
        }
      } catch (InitializationException e) {
        if (e.isRecoverable) {
          throw e; // Do not delay the exception if it can be recovered at higher level.
        }
        initializationExceptionPendingExceptionHolder.throwExceptionIfDeadlineIsReached(e);
        return false;
      }
    }
    initializationExceptionPendingExceptionHolder.clear();

    if (startMediaTimeUsNeedsInit) {
      startMediaTimeUs = max(0, presentationTimeUs);
      startMediaTimeUsNeedsSync = false;
      startMediaTimeUsNeedsInit = false;

      if (enableAudioTrackPlaybackParams && Util.SDK_INT >= 23) {
        setAudioTrackPlaybackParametersV23(audioTrackPlaybackParameters);
      }
      applyAudioProcessorPlaybackParametersAndSkipSilence(presentationTimeUs);

      if (playing) {
        play();
      }
    }

    if (!audioTrackPositionTracker.mayHandleBuffer(getWrittenFrames())) {
      return false;
    }

    if (inputBuffer == null) {
      // We are seeing this buffer for the first time.
      Assertions.checkArgument(buffer.order() == ByteOrder.LITTLE_ENDIAN);
      if (!buffer.hasRemaining()) {
        // The buffer is empty.
        return true;
      }

      if (configuration.outputMode != OUTPUT_MODE_PCM && framesPerEncodedSample == 0) {
        // If this is the first encoded sample, calculate the sample size in frames.
        framesPerEncodedSample = getFramesPerEncodedSample(configuration.outputEncoding, buffer);
        if (framesPerEncodedSample == 0) {
          // We still don't know the number of frames per sample, so drop the buffer.
          // For TrueHD this can occur after some seek operations, as not every sample starts with
          // a syncframe header. If we chunked samples together so the extracted samples always
          // started with a syncframe header, the chunks would be too large.
          return true;
        }
      }

      if (afterDrainParameters != null) {
        if (!drainToEndOfStream()) {
          // Don't process any more input until draining completes.
          return false;
        }
        applyAudioProcessorPlaybackParametersAndSkipSilence(presentationTimeUs);
        afterDrainParameters = null;
      }

      // Check that presentationTimeUs is consistent with the expected value.
      long expectedPresentationTimeUs =
          startMediaTimeUs
              + configuration.inputFramesToDurationUs(
                  getSubmittedFrames() - trimmingAudioProcessor.getTrimmedFrameCount());
      if (!startMediaTimeUsNeedsSync
          && Math.abs(expectedPresentationTimeUs - presentationTimeUs) > 200000) {
        if (listener != null) {
          listener.onAudioSinkError(
              new AudioSink.UnexpectedDiscontinuityException(
                  presentationTimeUs, expectedPresentationTimeUs));
        }
        startMediaTimeUsNeedsSync = true;
      }
      if (startMediaTimeUsNeedsSync) {
        if (!drainToEndOfStream()) {
          // Don't update timing until pending AudioProcessor buffers are completely drained.
          return false;
        }
        // Adjust startMediaTimeUs to be consistent with the current buffer's start time and the
        // number of bytes submitted.
        long adjustmentUs = presentationTimeUs - expectedPresentationTimeUs;
        startMediaTimeUs += adjustmentUs;
        startMediaTimeUsNeedsSync = false;
        // Re-apply playback parameters because the startMediaTimeUs changed.
        applyAudioProcessorPlaybackParametersAndSkipSilence(presentationTimeUs);
        if (listener != null && adjustmentUs != 0) {
          listener.onPositionDiscontinuity();
        }
      }

      if (configuration.outputMode == OUTPUT_MODE_PCM) {
        submittedPcmBytes += buffer.remaining();
      } else {
        submittedEncodedFrames += (long) framesPerEncodedSample * encodedAccessUnitCount;
      }

      inputBuffer = buffer;
      inputBufferAccessUnitCount = encodedAccessUnitCount;
    }

    processBuffers(presentationTimeUs);

    if (!inputBuffer.hasRemaining()) {
      inputBuffer = null;
      inputBufferAccessUnitCount = 0;
      return true;
    }

    if (audioTrackPositionTracker.isStalled(getWrittenFrames())) {
      Log.w(TAG, "Resetting stalled audio track");
      flush();
      return true;
    }

    return false;
  }

  private AudioTrack buildAudioTrackWithRetry() throws InitializationException {
    try {
      return buildAudioTrack(checkNotNull(configuration));
    } catch (InitializationException initialFailure) {
      // Retry with a smaller buffer size.
      if (configuration.bufferSize > AUDIO_TRACK_SMALLER_BUFFER_RETRY_SIZE) {
        Configuration retryConfiguration =
            configuration.copyWithBufferSize(AUDIO_TRACK_SMALLER_BUFFER_RETRY_SIZE);
        try {
          AudioTrack audioTrack = buildAudioTrack(retryConfiguration);
          configuration = retryConfiguration;
          return audioTrack;
        } catch (InitializationException retryFailure) {
          initialFailure.addSuppressed(retryFailure);
        }
      }
      maybeDisableOffload();
      throw initialFailure;
    }
  }

  private AudioTrack buildAudioTrack(Configuration configuration) throws InitializationException {
    try {
      AudioTrack audioTrack =
          configuration.buildAudioTrack(tunneling, audioAttributes, audioSessionId);
      if (audioOffloadListener != null) {
        audioOffloadListener.onExperimentalOffloadedPlayback(isOffloadedPlayback(audioTrack));
      }
      return audioTrack;
    } catch (InitializationException e) {
      if (listener != null) {
        listener.onAudioSinkError(e);
      }
      throw e;
    }
  }

  @RequiresApi(29)
  private void registerStreamEventCallbackV29(AudioTrack audioTrack) {
    if (offloadStreamEventCallbackV29 == null) {
      // Must be lazily initialized to receive stream event callbacks on the current (playback)
      // thread as the constructor is not called in the playback thread.
      offloadStreamEventCallbackV29 = new StreamEventCallbackV29();
    }
    offloadStreamEventCallbackV29.register(audioTrack);
  }

  private void processBuffers(long avSyncPresentationTimeUs) throws WriteException {
    int count = activeAudioProcessors.length;
    int index = count;
    while (index >= 0) {
      ByteBuffer input =
          index > 0
              ? outputBuffers[index - 1]
              : (inputBuffer != null ? inputBuffer : AudioProcessor.EMPTY_BUFFER);
      if (index == count) {
        writeBuffer(input, avSyncPresentationTimeUs);
      } else {
        AudioProcessor audioProcessor = activeAudioProcessors[index];
        if (index > drainingAudioProcessorIndex) {
          audioProcessor.queueInput(input);
        }
        ByteBuffer output = audioProcessor.getOutput();
        outputBuffers[index] = output;
        if (output.hasRemaining()) {
          // Handle the output as input to the next audio processor or the AudioTrack.
          index++;
          continue;
        }
      }

      if (input.hasRemaining()) {
        // The input wasn't consumed and no output was produced, so give up for now.
        return;
      }

      // Get more input from upstream.
      index--;
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void writeBuffer(ByteBuffer buffer, long avSyncPresentationTimeUs) throws WriteException {
    if (!buffer.hasRemaining()) {
      return;
    }
    if (outputBuffer != null) {
      Assertions.checkArgument(outputBuffer == buffer);
    } else {
      outputBuffer = buffer;
      if (Util.SDK_INT < 21) {
        int bytesRemaining = buffer.remaining();
        if (preV21OutputBuffer == null || preV21OutputBuffer.length < bytesRemaining) {
          preV21OutputBuffer = new byte[bytesRemaining];
        }
        int originalPosition = buffer.position();
        buffer.get(preV21OutputBuffer, 0, bytesRemaining);
        buffer.position(originalPosition);
        preV21OutputBufferOffset = 0;
      }
    }
    int bytesRemaining = buffer.remaining();
    int bytesWrittenOrError = 0; // Error if negative
    if (Util.SDK_INT < 21) { // outputMode == OUTPUT_MODE_PCM.
      // Work out how many bytes we can write without the risk of blocking.
      int bytesToWrite = audioTrackPositionTracker.getAvailableBufferSize(writtenPcmBytes);
      if (bytesToWrite > 0) {
        bytesToWrite = min(bytesRemaining, bytesToWrite);
        bytesWrittenOrError =
            audioTrack.write(preV21OutputBuffer, preV21OutputBufferOffset, bytesToWrite);
        if (bytesWrittenOrError > 0) { // No error
          preV21OutputBufferOffset += bytesWrittenOrError;
          buffer.position(buffer.position() + bytesWrittenOrError);
        }
      }
    } else if (tunneling) {
      Assertions.checkState(avSyncPresentationTimeUs != C.TIME_UNSET);
      bytesWrittenOrError =
          writeNonBlockingWithAvSyncV21(
              audioTrack, buffer, bytesRemaining, avSyncPresentationTimeUs);
    } else {
      bytesWrittenOrError = writeNonBlockingV21(audioTrack, buffer, bytesRemaining);
    }

    lastFeedElapsedRealtimeMs = SystemClock.elapsedRealtime();

    if (bytesWrittenOrError < 0) {
      int error = bytesWrittenOrError;

      // Treat a write error on a previously successful offload channel as recoverable
      // without disabling offload. Offload will be disabled when a new AudioTrack is created,
      // if no longer supported.
      boolean isRecoverable = isAudioTrackDeadObject(error) && writtenEncodedFrames > 0;

      WriteException e = new WriteException(error, configuration.inputFormat, isRecoverable);
      if (listener != null) {
        listener.onAudioSinkError(e);
      }
      if (e.isRecoverable) {
        throw e; // Do not delay the exception if it can be recovered at higher level.
      }
      writeExceptionPendingExceptionHolder.throwExceptionIfDeadlineIsReached(e);
      return;
    }
    writeExceptionPendingExceptionHolder.clear();

    int bytesWritten = bytesWrittenOrError;

    if (isOffloadedPlayback(audioTrack)) {
      // After calling AudioTrack.setOffloadEndOfStream, the AudioTrack internally stops and
      // restarts during which AudioTrack.write will return 0. This situation must be detected to
      // prevent reporting the buffer as full even though it is not which could lead ExoPlayer to
      // sleep forever waiting for a onDataRequest that will never come.
      if (writtenEncodedFrames > 0) {
        isWaitingForOffloadEndOfStreamHandled = false;
      }

      // Consider the offload buffer as full if the AudioTrack is playing and AudioTrack.write could
      // not write all the data provided to it. This relies on the assumption that AudioTrack.write
      // always writes as much as possible.
      if (playing
          && listener != null
          && bytesWritten < bytesRemaining
          && !isWaitingForOffloadEndOfStreamHandled) {
        listener.onOffloadBufferFull();
      }
    }

    if (configuration.outputMode == OUTPUT_MODE_PCM) {
      writtenPcmBytes += bytesWritten;
    }
    if (bytesWritten == bytesRemaining) {
      if (configuration.outputMode != OUTPUT_MODE_PCM) {
        // When playing non-PCM, the inputBuffer is never processed, thus the last inputBuffer
        // must be the current input buffer.
        Assertions.checkState(buffer == inputBuffer);
        writtenEncodedFrames += (long) framesPerEncodedSample * inputBufferAccessUnitCount;
      }
      outputBuffer = null;
    }
  }

  @Override
  public void playToEndOfStream() throws WriteException {
    if (!handledEndOfStream && isAudioTrackInitialized() && drainToEndOfStream()) {
      playPendingData();
      handledEndOfStream = true;
    }
  }

  private void maybeDisableOffload() {
    if (!configuration.outputModeIsOffload()) {
      return;
    }
    // Offload was requested, but may not be available. There are cases when this can occur even if
    // AudioManager.isOffloadedPlaybackSupported returned true. For example, due to use of an
    // AudioPlaybackCaptureConfiguration. Disable offload until the sink is next configured.
    offloadDisabledUntilNextConfiguration = true;
  }

  private static boolean isAudioTrackDeadObject(int status) {
    return (Util.SDK_INT >= 24 && status == AudioTrack.ERROR_DEAD_OBJECT)
        || status == ERROR_NATIVE_DEAD_OBJECT;
  }

  private boolean drainToEndOfStream() throws WriteException {
    boolean audioProcessorNeedsEndOfStream = false;
    if (drainingAudioProcessorIndex == C.INDEX_UNSET) {
      drainingAudioProcessorIndex = 0;
      audioProcessorNeedsEndOfStream = true;
    }
    while (drainingAudioProcessorIndex < activeAudioProcessors.length) {
      AudioProcessor audioProcessor = activeAudioProcessors[drainingAudioProcessorIndex];
      if (audioProcessorNeedsEndOfStream) {
        audioProcessor.queueEndOfStream();
      }
      processBuffers(C.TIME_UNSET);
      if (!audioProcessor.isEnded()) {
        return false;
      }
      audioProcessorNeedsEndOfStream = true;
      drainingAudioProcessorIndex++;
    }

    // Finish writing any remaining output to the track.
    if (outputBuffer != null) {
      writeBuffer(outputBuffer, C.TIME_UNSET);
      if (outputBuffer != null) {
        return false;
      }
    }
    drainingAudioProcessorIndex = C.INDEX_UNSET;
    return true;
  }

  @Override
  public boolean isEnded() {
    return !isAudioTrackInitialized() || (handledEndOfStream && !hasPendingData());
  }

  @Override
  public boolean hasPendingData() {
    return isAudioTrackInitialized()
        && audioTrackPositionTracker.hasPendingData(getWrittenFrames());
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    playbackParameters =
        new PlaybackParameters(
            constrainValue(playbackParameters.speed, MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED),
            constrainValue(playbackParameters.pitch, MIN_PITCH, MAX_PITCH));
    if (enableAudioTrackPlaybackParams && Util.SDK_INT >= 23) {
      setAudioTrackPlaybackParametersV23(playbackParameters);
    } else {
      setAudioProcessorPlaybackParametersAndSkipSilence(
          playbackParameters, getSkipSilenceEnabled());
    }
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return enableAudioTrackPlaybackParams
        ? audioTrackPlaybackParameters
        : getAudioProcessorPlaybackParameters();
  }

  @Override
  public void setSkipSilenceEnabled(boolean skipSilenceEnabled) {
    setAudioProcessorPlaybackParametersAndSkipSilence(
        getAudioProcessorPlaybackParameters(), skipSilenceEnabled);
  }

  @Override
  public boolean getSkipSilenceEnabled() {
    return getMediaPositionParameters().skipSilence;
  }

  @Override
  public void setAudioAttributes(AudioAttributes audioAttributes) {
    if (this.audioAttributes.equals(audioAttributes)) {
      return;
    }
    this.audioAttributes = audioAttributes;
    if (tunneling) {
      // The audio attributes are ignored in tunneling mode, so no need to reset.
      return;
    }
    flush();
  }

  @Override
  public AudioAttributes getAudioAttributes() {
    return audioAttributes;
  }

  @Override
  public void setAudioSessionId(int audioSessionId) {
    if (this.audioSessionId != audioSessionId) {
      this.audioSessionId = audioSessionId;
      externalAudioSessionIdProvided = audioSessionId != C.AUDIO_SESSION_ID_UNSET;
      flush();
    }
  }

  @Override
  public void setAuxEffectInfo(AuxEffectInfo auxEffectInfo) {
    if (this.auxEffectInfo.equals(auxEffectInfo)) {
      return;
    }
    int effectId = auxEffectInfo.effectId;
    float sendLevel = auxEffectInfo.sendLevel;
    if (audioTrack != null) {
      if (this.auxEffectInfo.effectId != effectId) {
        audioTrack.attachAuxEffect(effectId);
      }
      if (effectId != AuxEffectInfo.NO_AUX_EFFECT_ID) {
        audioTrack.setAuxEffectSendLevel(sendLevel);
      }
    }
    this.auxEffectInfo = auxEffectInfo;
  }

  @RequiresApi(23)
  @Override
  public void setPreferredDevice(@Nullable AudioDeviceInfo audioDeviceInfo) {
    this.preferredDevice =
        audioDeviceInfo == null ? null : new AudioDeviceInfoApi23(audioDeviceInfo);
    if (audioTrack != null) {
      Api23.setPreferredDeviceOnAudioTrack(audioTrack, this.preferredDevice);
    }
  }

  @Override
  public void enableTunnelingV21() {
    Assertions.checkState(Util.SDK_INT >= 21);
    Assertions.checkState(externalAudioSessionIdProvided);
    if (!tunneling) {
      tunneling = true;
      flush();
    }
  }

  @Override
  public void disableTunneling() {
    if (tunneling) {
      tunneling = false;
      flush();
    }
  }

  @Override
  public void setVolume(float volume) {
    if (this.volume != volume) {
      this.volume = volume;
      setVolumeInternal();
    }
  }

  private void setVolumeInternal() {
    if (!isAudioTrackInitialized()) {
      // Do nothing.
    } else if (Util.SDK_INT >= 21) {
      setVolumeInternalV21(audioTrack, volume);
    } else {
      setVolumeInternalV3(audioTrack, volume);
    }
  }

  @Override
  public void pause() {
    playing = false;
    if (isAudioTrackInitialized() && audioTrackPositionTracker.pause()) {
      audioTrack.pause();
    }
  }

  @Override
  public void flush() {
    if (isAudioTrackInitialized()) {
      resetSinkStateForFlush();

      if (audioTrackPositionTracker.isPlaying()) {
        audioTrack.pause();
      }
      if (isOffloadedPlayback(audioTrack)) {
        checkNotNull(offloadStreamEventCallbackV29).unregister(audioTrack);
      }
      if (Util.SDK_INT < 21 && !externalAudioSessionIdProvided) {
        // Prior to API level 21, audio sessions are not kept alive once there are no components
        // associated with them. If we generated the session ID internally, the only component
        // associated with the session is the audio track that's being released, and therefore
        // the session will not be kept alive. As a result, we need to generate a new session when
        // we next create an audio track.
        audioSessionId = C.AUDIO_SESSION_ID_UNSET;
      }
      if (pendingConfiguration != null) {
        configuration = pendingConfiguration;
        pendingConfiguration = null;
      }
      audioTrackPositionTracker.reset();
      releaseAudioTrackAsync(audioTrack, releasingConditionVariable);
      audioTrack = null;
    }
    writeExceptionPendingExceptionHolder.clear();
    initializationExceptionPendingExceptionHolder.clear();
  }

  @Override
  public void experimentalFlushWithoutAudioTrackRelease() {
    // Prior to SDK 25, AudioTrack flush does not work as intended, and therefore it must be
    // released and reinitialized. (Internal reference: b/143500232)
    if (Util.SDK_INT < 25) {
      flush();
      return;
    }

    writeExceptionPendingExceptionHolder.clear();
    initializationExceptionPendingExceptionHolder.clear();

    if (!isAudioTrackInitialized()) {
      return;
    }

    resetSinkStateForFlush();
    if (audioTrackPositionTracker.isPlaying()) {
      audioTrack.pause();
    }
    audioTrack.flush();

    audioTrackPositionTracker.reset();
    audioTrackPositionTracker.setAudioTrack(
        audioTrack,
        /* isPassthrough= */ configuration.outputMode == OUTPUT_MODE_PASSTHROUGH,
        configuration.outputEncoding,
        configuration.outputPcmFrameSize,
        configuration.bufferSize);

    startMediaTimeUsNeedsInit = true;
  }

  @Override
  public void reset() {
    flush();
    for (AudioProcessor audioProcessor : toIntPcmAvailableAudioProcessors) {
      audioProcessor.reset();
    }
    for (AudioProcessor audioProcessor : toFloatPcmAvailableAudioProcessors) {
      audioProcessor.reset();
    }
    playing = false;
    offloadDisabledUntilNextConfiguration = false;
  }

  // Internal methods.

  private void resetSinkStateForFlush() {
    submittedPcmBytes = 0;
    submittedEncodedFrames = 0;
    writtenPcmBytes = 0;
    writtenEncodedFrames = 0;
    isWaitingForOffloadEndOfStreamHandled = false;
    framesPerEncodedSample = 0;
    mediaPositionParameters =
        new MediaPositionParameters(
            getAudioProcessorPlaybackParameters(),
            getSkipSilenceEnabled(),
            /* mediaTimeUs= */ 0,
            /* audioTrackPositionUs= */ 0);
    startMediaTimeUs = 0;
    afterDrainParameters = null;
    mediaPositionParametersCheckpoints.clear();
    inputBuffer = null;
    inputBufferAccessUnitCount = 0;
    outputBuffer = null;
    stoppedAudioTrack = false;
    handledEndOfStream = false;
    drainingAudioProcessorIndex = C.INDEX_UNSET;
    avSyncHeader = null;
    bytesUntilNextAvSync = 0;
    trimmingAudioProcessor.resetTrimmedFrameCount();
    flushAudioProcessors();
  }

  @RequiresApi(23)
  private void setAudioTrackPlaybackParametersV23(PlaybackParameters audioTrackPlaybackParameters) {
    if (isAudioTrackInitialized()) {
      PlaybackParams playbackParams =
          new PlaybackParams()
              .allowDefaults()
              .setSpeed(audioTrackPlaybackParameters.speed)
              .setPitch(audioTrackPlaybackParameters.pitch)
              .setAudioFallbackMode(PlaybackParams.AUDIO_FALLBACK_MODE_FAIL);
      try {
        audioTrack.setPlaybackParams(playbackParams);
      } catch (IllegalArgumentException e) {
        Log.w(TAG, "Failed to set playback params", e);
      }
      // Update the speed using the actual effective speed from the audio track.
      audioTrackPlaybackParameters =
          new PlaybackParameters(
              audioTrack.getPlaybackParams().getSpeed(), audioTrack.getPlaybackParams().getPitch());
      audioTrackPositionTracker.setAudioTrackPlaybackSpeed(audioTrackPlaybackParameters.speed);
    }
    this.audioTrackPlaybackParameters = audioTrackPlaybackParameters;
  }

  private void setAudioProcessorPlaybackParametersAndSkipSilence(
      PlaybackParameters playbackParameters, boolean skipSilence) {
    MediaPositionParameters currentMediaPositionParameters = getMediaPositionParameters();
    if (!playbackParameters.equals(currentMediaPositionParameters.playbackParameters)
        || skipSilence != currentMediaPositionParameters.skipSilence) {
      MediaPositionParameters mediaPositionParameters =
          new MediaPositionParameters(
              playbackParameters,
              skipSilence,
              /* mediaTimeUs= */ C.TIME_UNSET,
              /* audioTrackPositionUs= */ C.TIME_UNSET);
      if (isAudioTrackInitialized()) {
        // Drain the audio processors so we can determine the frame position at which the new
        // parameters apply.
        this.afterDrainParameters = mediaPositionParameters;
      } else {
        // Update the audio processor chain parameters now. They will be applied to the audio
        // processors during initialization.
        this.mediaPositionParameters = mediaPositionParameters;
      }
    }
  }

  private PlaybackParameters getAudioProcessorPlaybackParameters() {
    return getMediaPositionParameters().playbackParameters;
  }

  private MediaPositionParameters getMediaPositionParameters() {
    // Mask the already set parameters.
    return afterDrainParameters != null
        ? afterDrainParameters
        : !mediaPositionParametersCheckpoints.isEmpty()
            ? mediaPositionParametersCheckpoints.getLast()
            : mediaPositionParameters;
  }

  private void applyAudioProcessorPlaybackParametersAndSkipSilence(long presentationTimeUs) {
    PlaybackParameters playbackParameters =
        shouldApplyAudioProcessorPlaybackParameters()
            ? audioProcessorChain.applyPlaybackParameters(getAudioProcessorPlaybackParameters())
            : PlaybackParameters.DEFAULT;
    boolean skipSilenceEnabled =
        shouldApplyAudioProcessorPlaybackParameters()
            ? audioProcessorChain.applySkipSilenceEnabled(getSkipSilenceEnabled())
            : DEFAULT_SKIP_SILENCE;
    mediaPositionParametersCheckpoints.add(
        new MediaPositionParameters(
            playbackParameters,
            skipSilenceEnabled,
            /* mediaTimeUs= */ max(0, presentationTimeUs),
            /* audioTrackPositionUs= */ configuration.framesToDurationUs(getWrittenFrames())));
    setupAudioProcessors();
    if (listener != null) {
      listener.onSkipSilenceEnabledChanged(skipSilenceEnabled);
    }
  }

  /**
   * Returns whether audio processor playback parameters should be applied in the current
   * configuration.
   */
  private boolean shouldApplyAudioProcessorPlaybackParameters() {
    // We don't apply speed/pitch adjustment using an audio processor in the following cases:
    // - in tunneling mode, because audio processing can change the duration of audio yet the video
    //   frame presentation times are currently not modified (see also
    //   https://github.com/google/ExoPlayer/issues/4803);
    // - when playing encoded audio via passthrough/offload, because modifying the audio stream
    //   would require decoding/re-encoding; and
    // - when outputting float PCM audio, because SonicAudioProcessor outputs 16-bit integer PCM.
    return !tunneling
        && MimeTypes.AUDIO_RAW.equals(configuration.inputFormat.sampleMimeType)
        && !shouldUseFloatOutput(configuration.inputFormat.pcmEncoding);
  }

  /**
   * Returns whether audio in the specified PCM encoding should be written to the audio track as
   * float PCM.
   */
  private boolean shouldUseFloatOutput(@C.PcmEncoding int pcmEncoding) {
    return enableFloatOutput && Util.isEncodingHighResolutionPcm(pcmEncoding);
  }

  /**
   * Applies and updates media position parameters.
   *
   * @param positionUs The current audio track position, in microseconds.
   * @return The current media time, in microseconds.
   */
  private long applyMediaPositionParameters(long positionUs) {
    while (!mediaPositionParametersCheckpoints.isEmpty()
        && positionUs >= mediaPositionParametersCheckpoints.getFirst().audioTrackPositionUs) {
      // We are playing (or about to play) media with the new parameters, so update them.
      mediaPositionParameters = mediaPositionParametersCheckpoints.remove();
    }

    long playoutDurationSinceLastCheckpointUs =
        positionUs - mediaPositionParameters.audioTrackPositionUs;
    if (mediaPositionParameters.playbackParameters.equals(PlaybackParameters.DEFAULT)) {
      return mediaPositionParameters.mediaTimeUs + playoutDurationSinceLastCheckpointUs;
    } else if (mediaPositionParametersCheckpoints.isEmpty()) {
      long mediaDurationSinceLastCheckpointUs =
          audioProcessorChain.getMediaDuration(playoutDurationSinceLastCheckpointUs);
      return mediaPositionParameters.mediaTimeUs + mediaDurationSinceLastCheckpointUs;
    } else {
      // The processor chain has been configured with new parameters, but we're still playing audio
      // that was processed using previous parameters. We can't scale the playout duration using the
      // processor chain in this case, so we fall back to scaling using the previous parameters'
      // target speed instead. Since the processor chain may not have achieved the target speed
      // precisely, we scale the duration to the next checkpoint (which will always be small) rather
      // than the duration from the previous checkpoint (which may be arbitrarily large). This
      // limits the amount of error that can be introduced due to a difference between the target
      // and actual speeds.
      MediaPositionParameters nextMediaPositionParameters =
          mediaPositionParametersCheckpoints.getFirst();
      long playoutDurationUntilNextCheckpointUs =
          nextMediaPositionParameters.audioTrackPositionUs - positionUs;
      long mediaDurationUntilNextCheckpointUs =
          Util.getMediaDurationForPlayoutDuration(
              playoutDurationUntilNextCheckpointUs,
              mediaPositionParameters.playbackParameters.speed);
      return nextMediaPositionParameters.mediaTimeUs - mediaDurationUntilNextCheckpointUs;
    }
  }

  private long applySkipping(long positionUs) {
    return positionUs
        + configuration.framesToDurationUs(audioProcessorChain.getSkippedOutputFrameCount());
  }

  private boolean isAudioTrackInitialized() {
    return audioTrack != null;
  }

  private long getSubmittedFrames() {
    return configuration.outputMode == OUTPUT_MODE_PCM
        ? (submittedPcmBytes / configuration.inputPcmFrameSize)
        : submittedEncodedFrames;
  }

  private long getWrittenFrames() {
    return configuration.outputMode == OUTPUT_MODE_PCM
        ? (writtenPcmBytes / configuration.outputPcmFrameSize)
        : writtenEncodedFrames;
  }

  private boolean useOffloadedPlayback(Format format, AudioAttributes audioAttributes) {
    if (Util.SDK_INT < 29 || offloadMode == OFFLOAD_MODE_DISABLED) {
      return false;
    }
    @C.Encoding
    int encoding = MimeTypes.getEncoding(checkNotNull(format.sampleMimeType), format.codecs);
    if (encoding == C.ENCODING_INVALID) {
      return false;
    }
    int channelConfig = Util.getAudioTrackChannelConfig(format.channelCount);
    if (channelConfig == AudioFormat.CHANNEL_INVALID) {
      return false;
    }
    AudioFormat audioFormat = getAudioFormat(format.sampleRate, channelConfig, encoding);

    switch (getOffloadedPlaybackSupport(
        audioFormat, audioAttributes.getAudioAttributesV21().audioAttributes)) {
      case AudioManager.PLAYBACK_OFFLOAD_NOT_SUPPORTED:
        return false;
      case AudioManager.PLAYBACK_OFFLOAD_SUPPORTED:
        boolean isGapless = format.encoderDelay != 0 || format.encoderPadding != 0;
        boolean gaplessSupportRequired = offloadMode == OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED;
        return !isGapless || !gaplessSupportRequired;
      case AudioManager.PLAYBACK_OFFLOAD_GAPLESS_SUPPORTED:
        return true;
      default:
        throw new IllegalStateException();
    }
  }

  @RequiresApi(29)
  @SuppressLint("InlinedApi")
  private int getOffloadedPlaybackSupport(
      AudioFormat audioFormat, android.media.AudioAttributes audioAttributes) {
    if (Util.SDK_INT >= 31) {
      return AudioManager.getPlaybackOffloadSupport(audioFormat, audioAttributes);
    }
    if (!AudioManager.isOffloadedPlaybackSupported(audioFormat, audioAttributes)) {
      return AudioManager.PLAYBACK_OFFLOAD_NOT_SUPPORTED;
    }
    // Manual testing has shown that Pixels on Android 11 support gapless offload.
    if (Util.SDK_INT == 30 && Util.MODEL.startsWith("Pixel")) {
      return AudioManager.PLAYBACK_OFFLOAD_GAPLESS_SUPPORTED;
    }
    return AudioManager.PLAYBACK_OFFLOAD_SUPPORTED;
  }

  private static boolean isOffloadedPlayback(AudioTrack audioTrack) {
    return Util.SDK_INT >= 29 && audioTrack.isOffloadedPlayback();
  }

  private static int getFramesPerEncodedSample(@C.Encoding int encoding, ByteBuffer buffer) {
    switch (encoding) {
      case C.ENCODING_MP3:
        int headerDataInBigEndian = Util.getBigEndianInt(buffer, buffer.position());
        int frameCount = MpegAudioUtil.parseMpegAudioFrameSampleCount(headerDataInBigEndian);
        if (frameCount == C.LENGTH_UNSET) {
          throw new IllegalArgumentException();
        }
        return frameCount;
      case C.ENCODING_AAC_LC:
        return AacUtil.AAC_LC_AUDIO_SAMPLE_COUNT;
      case C.ENCODING_AAC_HE_V1:
      case C.ENCODING_AAC_HE_V2:
        return AacUtil.AAC_HE_AUDIO_SAMPLE_COUNT;
      case C.ENCODING_AAC_XHE:
        return AacUtil.AAC_XHE_AUDIO_SAMPLE_COUNT;
      case C.ENCODING_AAC_ELD:
        return AacUtil.AAC_LD_AUDIO_SAMPLE_COUNT;
      case C.ENCODING_DTS:
      case C.ENCODING_DTS_HD:
        return DtsUtil.parseDtsAudioSampleCount(buffer);
      case C.ENCODING_AC3:
      case C.ENCODING_E_AC3:
      case C.ENCODING_E_AC3_JOC:
        return Ac3Util.parseAc3SyncframeAudioSampleCount(buffer);
      case C.ENCODING_AC4:
        return Ac4Util.parseAc4SyncframeAudioSampleCount(buffer);
      case C.ENCODING_DOLBY_TRUEHD:
        int syncframeOffset = Ac3Util.findTrueHdSyncframeOffset(buffer);
        return syncframeOffset == C.INDEX_UNSET
            ? 0
            : (Ac3Util.parseTrueHdSyncframeAudioSampleCount(buffer, syncframeOffset)
                * Ac3Util.TRUEHD_RECHUNK_SAMPLE_COUNT);
      case C.ENCODING_OPUS:
        return OpusUtil.parsePacketAudioSampleCount(buffer);
      case C.ENCODING_PCM_16BIT:
      case C.ENCODING_PCM_16BIT_BIG_ENDIAN:
      case C.ENCODING_PCM_24BIT:
      case C.ENCODING_PCM_32BIT:
      case C.ENCODING_PCM_8BIT:
      case C.ENCODING_PCM_FLOAT:
      case C.ENCODING_AAC_ER_BSAC:
      case C.ENCODING_INVALID:
      case Format.NO_VALUE:
      default:
        throw new IllegalStateException("Unexpected audio encoding: " + encoding);
    }
  }

  @RequiresApi(21)
  private static int writeNonBlockingV21(AudioTrack audioTrack, ByteBuffer buffer, int size) {
    return audioTrack.write(buffer, size, AudioTrack.WRITE_NON_BLOCKING);
  }

  @RequiresApi(21)
  private int writeNonBlockingWithAvSyncV21(
      AudioTrack audioTrack, ByteBuffer buffer, int size, long presentationTimeUs) {
    if (Util.SDK_INT >= 26) {
      // The underlying platform AudioTrack writes AV sync headers directly.
      return audioTrack.write(
          buffer, size, AudioTrack.WRITE_NON_BLOCKING, presentationTimeUs * 1000);
    }
    if (avSyncHeader == null) {
      avSyncHeader = ByteBuffer.allocate(16);
      avSyncHeader.order(ByteOrder.BIG_ENDIAN);
      avSyncHeader.putInt(0x55550001);
    }
    if (bytesUntilNextAvSync == 0) {
      avSyncHeader.putInt(4, size);
      avSyncHeader.putLong(8, presentationTimeUs * 1000);
      avSyncHeader.position(0);
      bytesUntilNextAvSync = size;
    }
    int avSyncHeaderBytesRemaining = avSyncHeader.remaining();
    if (avSyncHeaderBytesRemaining > 0) {
      int result =
          audioTrack.write(avSyncHeader, avSyncHeaderBytesRemaining, AudioTrack.WRITE_NON_BLOCKING);
      if (result < 0) {
        bytesUntilNextAvSync = 0;
        return result;
      }
      if (result < avSyncHeaderBytesRemaining) {
        return 0;
      }
    }
    int result = writeNonBlockingV21(audioTrack, buffer, size);
    if (result < 0) {
      bytesUntilNextAvSync = 0;
      return result;
    }
    bytesUntilNextAvSync -= result;
    return result;
  }

  @RequiresApi(21)
  private static void setVolumeInternalV21(AudioTrack audioTrack, float volume) {
    audioTrack.setVolume(volume);
  }

  private static void setVolumeInternalV3(AudioTrack audioTrack, float volume) {
    audioTrack.setStereoVolume(volume, volume);
  }

  private void playPendingData() {
    if (!stoppedAudioTrack) {
      stoppedAudioTrack = true;
      audioTrackPositionTracker.handleEndOfStream(getWrittenFrames());
      audioTrack.stop();
      bytesUntilNextAvSync = 0;
    }
  }

  private static void releaseAudioTrackAsync(
      AudioTrack audioTrack, ConditionVariable releasedConditionVariable) {
    // AudioTrack.release can take some time, so we call it on a background thread. The background
    // thread is shared statically to avoid creating many threads when multiple players are released
    // at the same time.
    releasedConditionVariable.close();
    synchronized (releaseExecutorLock) {
      if (releaseExecutor == null) {
        releaseExecutor = Util.newSingleThreadExecutor("ExoPlayer:AudioTrackReleaseThread");
      }
      pendingReleaseCount++;
      releaseExecutor.execute(
          () -> {
            try {
              audioTrack.flush();
              audioTrack.release();
            } finally {
              releasedConditionVariable.open();
              synchronized (releaseExecutorLock) {
                pendingReleaseCount--;
                if (pendingReleaseCount == 0) {
                  releaseExecutor.shutdown();
                  releaseExecutor = null;
                }
              }
            }
          });
    }
  }

  @RequiresApi(29)
  private final class StreamEventCallbackV29 {
    private final Handler handler;
    private final AudioTrack.StreamEventCallback callback;

    public StreamEventCallbackV29() {
      handler = new Handler(Looper.myLooper());
      // Avoid StreamEventCallbackV29 inheriting directly from AudioTrack.StreamEventCallback as it
      // would cause a NoClassDefFoundError warning on load of DefaultAudioSink for SDK < 29.
      // See: https://github.com/google/ExoPlayer/issues/8058
      callback =
          new AudioTrack.StreamEventCallback() {
            @Override
            public void onDataRequest(AudioTrack track, int size) {
              if (!track.equals(audioTrack)) {
                // Stale event.
                return;
              }
              if (listener != null && playing) {
                // Do not signal that the buffer is emptying if not playing as it is a transient
                // state.
                listener.onOffloadBufferEmptying();
              }
            }

            @Override
            public void onTearDown(AudioTrack track) {
              if (!track.equals(audioTrack)) {
                // Stale event.
                return;
              }
              if (listener != null && playing) {
                // The audio track was destroyed while in use. Thus a new AudioTrack needs to be
                // created and its buffer filled, which will be done on the next handleBuffer call.
                // Request this call explicitly in case ExoPlayer is sleeping waiting for a data
                // request.
                listener.onOffloadBufferEmptying();
              }
            }
          };
    }

    public void register(AudioTrack audioTrack) {
      audioTrack.registerStreamEventCallback(handler::post, callback);
    }

    public void unregister(AudioTrack audioTrack) {
      audioTrack.unregisterStreamEventCallback(callback);
      handler.removeCallbacksAndMessages(/* token= */ null);
    }
  }

  /** Stores parameters used to calculate the current media position. */
  private static final class MediaPositionParameters {

    /** The playback parameters. */
    public final PlaybackParameters playbackParameters;
    /** Whether to skip silences. */
    public final boolean skipSilence;
    /** The media time from which the playback parameters apply, in microseconds. */
    public final long mediaTimeUs;
    /** The audio track position from which the playback parameters apply, in microseconds. */
    public final long audioTrackPositionUs;

    private MediaPositionParameters(
        PlaybackParameters playbackParameters,
        boolean skipSilence,
        long mediaTimeUs,
        long audioTrackPositionUs) {
      this.playbackParameters = playbackParameters;
      this.skipSilence = skipSilence;
      this.mediaTimeUs = mediaTimeUs;
      this.audioTrackPositionUs = audioTrackPositionUs;
    }
  }

  @RequiresApi(21)
  private static AudioFormat getAudioFormat(int sampleRate, int channelConfig, int encoding) {
    return new AudioFormat.Builder()
        .setSampleRate(sampleRate)
        .setChannelMask(channelConfig)
        .setEncoding(encoding)
        .build();
  }

  private static int getAudioTrackMinBufferSize(
      int sampleRateInHz, int channelConfig, int encoding) {
    int minBufferSize = AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, encoding);
    Assertions.checkState(minBufferSize != AudioTrack.ERROR_BAD_VALUE);
    return minBufferSize;
  }

  private final class PositionTrackerListener implements AudioTrackPositionTracker.Listener {

    @Override
    public void onPositionFramesMismatch(
        long audioTimestampPositionFrames,
        long audioTimestampSystemTimeUs,
        long systemTimeUs,
        long playbackPositionUs) {
      String message =
          "Spurious audio timestamp (frame position mismatch): "
              + audioTimestampPositionFrames
              + ", "
              + audioTimestampSystemTimeUs
              + ", "
              + systemTimeUs
              + ", "
              + playbackPositionUs
              + ", "
              + getSubmittedFrames()
              + ", "
              + getWrittenFrames();
      if (failOnSpuriousAudioTimestamp) {
        throw new InvalidAudioTrackTimestampException(message);
      }
      Log.w(TAG, message);
    }

    @Override
    public void onSystemTimeUsMismatch(
        long audioTimestampPositionFrames,
        long audioTimestampSystemTimeUs,
        long systemTimeUs,
        long playbackPositionUs) {
      String message =
          "Spurious audio timestamp (system clock mismatch): "
              + audioTimestampPositionFrames
              + ", "
              + audioTimestampSystemTimeUs
              + ", "
              + systemTimeUs
              + ", "
              + playbackPositionUs
              + ", "
              + getSubmittedFrames()
              + ", "
              + getWrittenFrames();
      if (failOnSpuriousAudioTimestamp) {
        throw new InvalidAudioTrackTimestampException(message);
      }
      Log.w(TAG, message);
    }

    @Override
    public void onInvalidLatency(long latencyUs) {
      Log.w(TAG, "Ignoring impossibly large audio latency: " + latencyUs);
    }

    @Override
    public void onPositionAdvancing(long playoutStartSystemTimeMs) {
      if (listener != null) {
        listener.onPositionAdvancing(playoutStartSystemTimeMs);
      }
    }

    @Override
    public void onUnderrun(int bufferSize, long bufferSizeMs) {
      if (listener != null) {
        long elapsedSinceLastFeedMs = SystemClock.elapsedRealtime() - lastFeedElapsedRealtimeMs;
        listener.onUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
      }
    }
  }

  /** Stores configuration relating to the audio format. */
  private static final class Configuration {

    public final Format inputFormat;
    public final int inputPcmFrameSize;
    public final @OutputMode int outputMode;
    public final int outputPcmFrameSize;
    public final int outputSampleRate;
    public final int outputChannelConfig;
    public final @C.Encoding int outputEncoding;
    public final int bufferSize;
    public final AudioProcessor[] availableAudioProcessors;

    public Configuration(
        Format inputFormat,
        int inputPcmFrameSize,
        @OutputMode int outputMode,
        int outputPcmFrameSize,
        int outputSampleRate,
        int outputChannelConfig,
        int outputEncoding,
        int bufferSize,
        AudioProcessor[] availableAudioProcessors) {
      this.inputFormat = inputFormat;
      this.inputPcmFrameSize = inputPcmFrameSize;
      this.outputMode = outputMode;
      this.outputPcmFrameSize = outputPcmFrameSize;
      this.outputSampleRate = outputSampleRate;
      this.outputChannelConfig = outputChannelConfig;
      this.outputEncoding = outputEncoding;
      this.bufferSize = bufferSize;
      this.availableAudioProcessors = availableAudioProcessors;
    }

    public Configuration copyWithBufferSize(int bufferSize) {
      return new Configuration(
          inputFormat,
          inputPcmFrameSize,
          outputMode,
          outputPcmFrameSize,
          outputSampleRate,
          outputChannelConfig,
          outputEncoding,
          bufferSize,
          availableAudioProcessors);
    }

    /** Returns if the configurations are sufficiently compatible to reuse the audio track. */
    public boolean canReuseAudioTrack(Configuration audioTrackConfiguration) {
      return audioTrackConfiguration.outputMode == outputMode
          && audioTrackConfiguration.outputEncoding == outputEncoding
          && audioTrackConfiguration.outputSampleRate == outputSampleRate
          && audioTrackConfiguration.outputChannelConfig == outputChannelConfig
          && audioTrackConfiguration.outputPcmFrameSize == outputPcmFrameSize;
    }

    public long inputFramesToDurationUs(long frameCount) {
      return (frameCount * C.MICROS_PER_SECOND) / inputFormat.sampleRate;
    }

    public long framesToDurationUs(long frameCount) {
      return (frameCount * C.MICROS_PER_SECOND) / outputSampleRate;
    }

    public AudioTrack buildAudioTrack(
        boolean tunneling, AudioAttributes audioAttributes, int audioSessionId)
        throws InitializationException {
      AudioTrack audioTrack;
      try {
        audioTrack = createAudioTrack(tunneling, audioAttributes, audioSessionId);
      } catch (UnsupportedOperationException | IllegalArgumentException e) {
        throw new InitializationException(
            AudioTrack.STATE_UNINITIALIZED,
            outputSampleRate,
            outputChannelConfig,
            bufferSize,
            inputFormat,
            /* isRecoverable= */ outputModeIsOffload(),
            e);
      }

      int state = audioTrack.getState();
      if (state != AudioTrack.STATE_INITIALIZED) {
        try {
          audioTrack.release();
        } catch (Exception e) {
          // The track has already failed to initialize, so it wouldn't be that surprising if
          // release were to fail too. Swallow the exception.
        }
        throw new InitializationException(
            state,
            outputSampleRate,
            outputChannelConfig,
            bufferSize,
            inputFormat,
            /* isRecoverable= */ outputModeIsOffload(),
            /* audioTrackException= */ null);
      }
      return audioTrack;
    }

    private AudioTrack createAudioTrack(
        boolean tunneling, AudioAttributes audioAttributes, int audioSessionId) {
      if (Util.SDK_INT >= 29) {
        return createAudioTrackV29(tunneling, audioAttributes, audioSessionId);
      } else if (Util.SDK_INT >= 21) {
        return createAudioTrackV21(tunneling, audioAttributes, audioSessionId);
      } else {
        return createAudioTrackV9(audioAttributes, audioSessionId);
      }
    }

    @RequiresApi(29)
    private AudioTrack createAudioTrackV29(
        boolean tunneling, AudioAttributes audioAttributes, int audioSessionId) {
      AudioFormat audioFormat =
          getAudioFormat(outputSampleRate, outputChannelConfig, outputEncoding);
      android.media.AudioAttributes audioTrackAttributes =
          getAudioTrackAttributesV21(audioAttributes, tunneling);
      return new AudioTrack.Builder()
          .setAudioAttributes(audioTrackAttributes)
          .setAudioFormat(audioFormat)
          .setTransferMode(AudioTrack.MODE_STREAM)
          .setBufferSizeInBytes(bufferSize)
          .setSessionId(audioSessionId)
          .setOffloadedPlayback(outputMode == OUTPUT_MODE_OFFLOAD)
          .build();
    }

    @RequiresApi(21)
    private AudioTrack createAudioTrackV21(
        boolean tunneling, AudioAttributes audioAttributes, int audioSessionId) {
      return new AudioTrack(
          getAudioTrackAttributesV21(audioAttributes, tunneling),
          getAudioFormat(outputSampleRate, outputChannelConfig, outputEncoding),
          bufferSize,
          AudioTrack.MODE_STREAM,
          audioSessionId);
    }

    @SuppressWarnings("deprecation") // Using deprecated AudioTrack constructor.
    private AudioTrack createAudioTrackV9(AudioAttributes audioAttributes, int audioSessionId) {
      int streamType = Util.getStreamTypeForAudioUsage(audioAttributes.usage);
      if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) {
        return new AudioTrack(
            streamType,
            outputSampleRate,
            outputChannelConfig,
            outputEncoding,
            bufferSize,
            AudioTrack.MODE_STREAM);
      } else {
        // Re-attach to the same audio session.
        return new AudioTrack(
            streamType,
            outputSampleRate,
            outputChannelConfig,
            outputEncoding,
            bufferSize,
            AudioTrack.MODE_STREAM,
            audioSessionId);
      }
    }

    @RequiresApi(21)
    private static android.media.AudioAttributes getAudioTrackAttributesV21(
        AudioAttributes audioAttributes, boolean tunneling) {
      if (tunneling) {
        return getAudioTrackTunnelingAttributesV21();
      } else {
        return audioAttributes.getAudioAttributesV21().audioAttributes;
      }
    }

    @RequiresApi(21)
    private static android.media.AudioAttributes getAudioTrackTunnelingAttributesV21() {
      return new android.media.AudioAttributes.Builder()
          .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
          .setFlags(android.media.AudioAttributes.FLAG_HW_AV_SYNC)
          .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
          .build();
    }

    public boolean outputModeIsOffload() {
      return outputMode == OUTPUT_MODE_OFFLOAD;
    }
  }

  private static final class PendingExceptionHolder<T extends Exception> {

    private final long throwDelayMs;

    @Nullable private T pendingException;
    private long throwDeadlineMs;

    public PendingExceptionHolder(long throwDelayMs) {
      this.throwDelayMs = throwDelayMs;
    }

    public void throwExceptionIfDeadlineIsReached(T exception) throws T {
      long nowMs = SystemClock.elapsedRealtime();
      if (pendingException == null) {
        pendingException = exception;
        throwDeadlineMs = nowMs + throwDelayMs;
      }
      if (nowMs >= throwDeadlineMs) {
        if (pendingException != exception) {
          // All retry exception are probably the same, thus only save the last one to save memory.
          pendingException.addSuppressed(exception);
        }
        T pendingException = this.pendingException;
        clear();
        throw pendingException;
      }
    }

    public void clear() {
      pendingException = null;
    }
  }

  @RequiresApi(23)
  private static final class AudioDeviceInfoApi23 {

    public final AudioDeviceInfo audioDeviceInfo;

    public AudioDeviceInfoApi23(AudioDeviceInfo audioDeviceInfo) {
      this.audioDeviceInfo = audioDeviceInfo;
    }
  }

  @RequiresApi(23)
  private static final class Api23 {
    private Api23() {}

    @DoNotInline
    public static void setPreferredDeviceOnAudioTrack(
        AudioTrack audioTrack, @Nullable AudioDeviceInfoApi23 audioDeviceInfo) {
      audioTrack.setPreferredDevice(
          audioDeviceInfo == null ? null : audioDeviceInfo.audioDeviceInfo);
    }
  }

  @RequiresApi(31)
  private static final class Api31 {
    private Api31() {}

    @DoNotInline
    public static void setLogSessionIdOnAudioTrack(AudioTrack audioTrack, PlayerId playerId) {
      LogSessionId logSessionId = playerId.getLogSessionId();
      if (!logSessionId.equals(LogSessionId.LOG_SESSION_ID_NONE)) {
        audioTrack.setLogSessionId(logSessionId);
      }
    }
  }
}
