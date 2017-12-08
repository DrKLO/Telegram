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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.os.ConditionVariable;
import android.os.SystemClock;
import android.util.Log;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.PlaybackParameters;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import org.telegram.messenger.exoplayer2.util.Util;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Plays audio data. The implementation delegates to an {@link android.media.AudioTrack} and handles
 * playback position smoothing, non-blocking writes and reconfiguration.
 * <p>
 * Before starting playback, specify the input format by calling
 * {@link #configure(String, int, int, int, int)}. Optionally call {@link #setAudioSessionId(int)},
 * {@link #setAudioAttributes(AudioAttributes)}, {@link #enableTunnelingV21(int)} and
 * {@link #disableTunneling()} to configure audio playback. These methods may be called after
 * writing data to the track, in which case it will be reinitialized as required.
 * <p>
 * Call {@link #handleBuffer(ByteBuffer, long)} to write data, and {@link #handleDiscontinuity()}
 * when the data being fed is discontinuous. Call {@link #play()} to start playing the written data.
 * <p>
 * Call {@link #configure(String, int, int, int, int)} whenever the input format changes. The track
 * will be reinitialized on the next call to {@link #handleBuffer(ByteBuffer, long)}.
 * <p>
 * Calling {@link #reset()} releases the underlying {@link android.media.AudioTrack} (and so does
 * calling {@link #configure(String, int, int, int, int)} unless the format is unchanged). It is
 * safe to call {@link #handleBuffer(ByteBuffer, long)} after {@link #reset()} without calling
 * {@link #configure(String, int, int, int, int)}.
 * <p>
 * Call {@link #playToEndOfStream()} repeatedly to play out all data when no more input buffers will
 * be provided via {@link #handleBuffer(ByteBuffer, long)} until the next {@link #reset}. Call
 * {@link #release()} when the instance is no longer required.
 */
public final class AudioTrack {

  /**
   * Listener for audio track events.
   */
  public interface Listener {

    /**
     * Called when the audio track has been initialized with a newly generated audio session id.
     *
     * @param audioSessionId The newly generated audio session id.
     */
    void onAudioSessionId(int audioSessionId);

    /**
     * Called when the audio track handles a buffer whose timestamp is discontinuous with the last
     * buffer handled since it was reset.
     */
    void onPositionDiscontinuity();

    /**
     * Called when the audio track underruns.
     *
     * @param bufferSize The size of the track's buffer, in bytes.
     * @param bufferSizeMs The size of the track's buffer, in milliseconds, if it is configured for
     *     PCM output. {@link C#TIME_UNSET} if it is configured for passthrough output, as the
     *     buffered media can have a variable bitrate so the duration may be unknown.
     * @param elapsedSinceLastFeedMs The time since the track was last fed data, in milliseconds.
     */
    void onUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs);

  }

  /**
   * Thrown when a failure occurs configuring the track.
   */
  public static final class ConfigurationException extends Exception {

    public ConfigurationException(Throwable cause) {
      super(cause);
    }

    public ConfigurationException(String message) {
      super(message);
    }

  }

  /**
   * Thrown when a failure occurs initializing an {@link android.media.AudioTrack}.
   */
  public static final class InitializationException extends Exception {

    /**
     * The state as reported by {@link android.media.AudioTrack#getState()}.
     */
    public final int audioTrackState;

    /**
     * @param audioTrackState The state as reported by {@link android.media.AudioTrack#getState()}.
     * @param sampleRate The requested sample rate in Hz.
     * @param channelConfig The requested channel configuration.
     * @param bufferSize The requested buffer size in bytes.
     */
    public InitializationException(int audioTrackState, int sampleRate, int channelConfig,
        int bufferSize) {
      super("AudioTrack init failed: " + audioTrackState + ", Config(" + sampleRate + ", "
          + channelConfig + ", " + bufferSize + ")");
      this.audioTrackState = audioTrackState;
    }

  }

  /**
   * Thrown when a failure occurs writing to an {@link android.media.AudioTrack}.
   */
  public static final class WriteException extends Exception {

    /**
     * The error value returned from {@link android.media.AudioTrack#write(byte[], int, int)} or
     *     {@link android.media.AudioTrack#write(ByteBuffer, int, int)}.
     */
    public final int errorCode;

    /**
     * @param errorCode The error value returned from
     *     {@link android.media.AudioTrack#write(byte[], int, int)} or
     *     {@link android.media.AudioTrack#write(ByteBuffer, int, int)}.
     */
    public WriteException(int errorCode) {
      super("AudioTrack write failed: " + errorCode);
      this.errorCode = errorCode;
    }

  }

  /**
   * Thrown when {@link android.media.AudioTrack#getTimestamp} returns a spurious timestamp, if
   * {@code AudioTrack#failOnSpuriousAudioTimestamp} is set.
   */
  public static final class InvalidAudioTrackTimestampException extends RuntimeException {

    /**
     * @param detailMessage The detail message for this exception.
     */
    public InvalidAudioTrackTimestampException(String detailMessage) {
      super(detailMessage);
    }

  }

  /**
   * Returned by {@link #getCurrentPositionUs(boolean)} when the position is not set.
   */
  public static final long CURRENT_POSITION_NOT_SET = Long.MIN_VALUE;

  /**
   * A minimum length for the {@link android.media.AudioTrack} buffer, in microseconds.
   */
  private static final long MIN_BUFFER_DURATION_US = 250000;
  /**
   * A maximum length for the {@link android.media.AudioTrack} buffer, in microseconds.
   */
  private static final long MAX_BUFFER_DURATION_US = 750000;
  /**
   * The length for passthrough {@link android.media.AudioTrack} buffers, in microseconds.
   */
  private static final long PASSTHROUGH_BUFFER_DURATION_US = 250000;
  /**
   * A multiplication factor to apply to the minimum buffer size requested by the underlying
   * {@link android.media.AudioTrack}.
   */
  private static final int BUFFER_MULTIPLICATION_FACTOR = 4;

  /**
   * @see android.media.AudioTrack#PLAYSTATE_STOPPED
   */
  private static final int PLAYSTATE_STOPPED = android.media.AudioTrack.PLAYSTATE_STOPPED;
  /**
   * @see android.media.AudioTrack#PLAYSTATE_PAUSED
   */
  private static final int PLAYSTATE_PAUSED = android.media.AudioTrack.PLAYSTATE_PAUSED;
  /**
   * @see android.media.AudioTrack#PLAYSTATE_PLAYING
   */
  private static final int PLAYSTATE_PLAYING = android.media.AudioTrack.PLAYSTATE_PLAYING;
  /**
   * @see android.media.AudioTrack#ERROR_BAD_VALUE
   */
  private static final int ERROR_BAD_VALUE = android.media.AudioTrack.ERROR_BAD_VALUE;
  /**
   * @see android.media.AudioTrack#MODE_STATIC
   */
  private static final int MODE_STATIC = android.media.AudioTrack.MODE_STATIC;
  /**
   * @see android.media.AudioTrack#MODE_STREAM
   */
  private static final int MODE_STREAM = android.media.AudioTrack.MODE_STREAM;
  /**
   * @see android.media.AudioTrack#STATE_INITIALIZED
   */
  private static final int STATE_INITIALIZED = android.media.AudioTrack.STATE_INITIALIZED;
  /**
   * @see android.media.AudioTrack#WRITE_NON_BLOCKING
   */
  @SuppressLint("InlinedApi")
  private static final int WRITE_NON_BLOCKING = android.media.AudioTrack.WRITE_NON_BLOCKING;

  private static final String TAG = "AudioTrack";

  /**
   * AudioTrack timestamps are deemed spurious if they are offset from the system clock by more
   * than this amount.
   * <p>
   * This is a fail safe that should not be required on correctly functioning devices.
   */
  private static final long MAX_AUDIO_TIMESTAMP_OFFSET_US = 5 * C.MICROS_PER_SECOND;

  /**
   * AudioTrack latencies are deemed impossibly large if they are greater than this amount.
   * <p>
   * This is a fail safe that should not be required on correctly functioning devices.
   */
  private static final long MAX_LATENCY_US = 5 * C.MICROS_PER_SECOND;

  private static final int START_NOT_SET = 0;
  private static final int START_IN_SYNC = 1;
  private static final int START_NEED_SYNC = 2;

  private static final int MAX_PLAYHEAD_OFFSET_COUNT = 10;
  private static final int MIN_PLAYHEAD_OFFSET_SAMPLE_INTERVAL_US = 30000;
  private static final int MIN_TIMESTAMP_SAMPLE_INTERVAL_US = 500000;

  /**
   * The minimum number of output bytes from {@link #sonicAudioProcessor} at which the speedup is
   * calculated using the input/output byte counts from the processor, rather than using the
   * current playback parameters speed.
   */
  private static final int SONIC_MIN_BYTES_FOR_SPEEDUP = 1024;

  /**
   * Whether to enable a workaround for an issue where an audio effect does not keep its session
   * active across releasing/initializing a new audio track, on platform builds where
   * {@link Util#SDK_INT} &lt; 21.
   * <p>
   * The flag must be set before creating a player.
   */
  public static boolean enablePreV21AudioSessionWorkaround = false;

  /**
   * Whether to throw an {@link InvalidAudioTrackTimestampException} when a spurious timestamp is
   * reported from {@link android.media.AudioTrack#getTimestamp}.
   * <p>
   * The flag must be set before creating a player. Should be set to {@code true} for testing and
   * debugging purposes only.
   */
  public static boolean failOnSpuriousAudioTimestamp = false;

  private final AudioCapabilities audioCapabilities;
  private final ChannelMappingAudioProcessor channelMappingAudioProcessor;
  private final SonicAudioProcessor sonicAudioProcessor;
  private final AudioProcessor[] availableAudioProcessors;
  private final Listener listener;
  private final ConditionVariable releasingConditionVariable;
  private final long[] playheadOffsets;
  private final AudioTrackUtil audioTrackUtil;
  private final LinkedList<PlaybackParametersCheckpoint> playbackParametersCheckpoints;

  /**
   * Used to keep the audio session active on pre-V21 builds (see {@link #initialize()}).
   */
  private android.media.AudioTrack keepSessionIdAudioTrack;

  private android.media.AudioTrack audioTrack;
  private int sampleRate;
  private int channelConfig;
  @C.Encoding
  private int encoding;
  @C.Encoding
  private int outputEncoding;
  private AudioAttributes audioAttributes;
  private boolean passthrough;
  private int bufferSize;
  private long bufferSizeUs;

  private PlaybackParameters drainingPlaybackParameters;
  private PlaybackParameters playbackParameters;
  private long playbackParametersOffsetUs;
  private long playbackParametersPositionUs;

  private ByteBuffer avSyncHeader;
  private int bytesUntilNextAvSync;

  private int nextPlayheadOffsetIndex;
  private int playheadOffsetCount;
  private long smoothedPlayheadOffsetUs;
  private long lastPlayheadSampleTimeUs;
  private boolean audioTimestampSet;
  private long lastTimestampSampleTimeUs;

  private Method getLatencyMethod;
  private int pcmFrameSize;
  private long submittedPcmBytes;
  private long submittedEncodedFrames;
  private int outputPcmFrameSize;
  private long writtenPcmBytes;
  private long writtenEncodedFrames;
  private int framesPerEncodedSample;
  private int startMediaTimeState;
  private long startMediaTimeUs;
  private long resumeSystemTimeUs;
  private long latencyUs;
  private float volume;

  private AudioProcessor[] audioProcessors;
  private ByteBuffer[] outputBuffers;
  private ByteBuffer inputBuffer;
  private ByteBuffer outputBuffer;
  private byte[] preV21OutputBuffer;
  private int preV21OutputBufferOffset;
  private int drainingAudioProcessorIndex;
  private boolean handledEndOfStream;

  private boolean playing;
  private int audioSessionId;
  private boolean tunneling;
  private boolean hasData;
  private long lastFeedElapsedRealtimeMs;

  /**
   * @param audioCapabilities The audio capabilities for playback on this device. May be null if the
   *     default capabilities (no encoded audio passthrough support) should be assumed.
   * @param audioProcessors An array of {@link AudioProcessor}s that will process PCM audio before
   *     output. May be empty.
   * @param listener Listener for audio track events.
   */
  public AudioTrack(AudioCapabilities audioCapabilities, AudioProcessor[] audioProcessors,
      Listener listener) {
    this.audioCapabilities = audioCapabilities;
    this.listener = listener;
    releasingConditionVariable = new ConditionVariable(true);
    if (Util.SDK_INT >= 18) {
      try {
        getLatencyMethod =
            android.media.AudioTrack.class.getMethod("getLatency", (Class<?>[]) null);
      } catch (NoSuchMethodException e) {
        // There's no guarantee this method exists. Do nothing.
      }
    }
    if (Util.SDK_INT >= 19) {
      audioTrackUtil = new AudioTrackUtilV19();
    } else {
      audioTrackUtil = new AudioTrackUtil();
    }
    channelMappingAudioProcessor = new ChannelMappingAudioProcessor();
    sonicAudioProcessor = new SonicAudioProcessor();
    availableAudioProcessors = new AudioProcessor[3 + audioProcessors.length];
    availableAudioProcessors[0] = new ResamplingAudioProcessor();
    availableAudioProcessors[1] = channelMappingAudioProcessor;
    System.arraycopy(audioProcessors, 0, availableAudioProcessors, 2, audioProcessors.length);
    availableAudioProcessors[2 + audioProcessors.length] = sonicAudioProcessor;
    playheadOffsets = new long[MAX_PLAYHEAD_OFFSET_COUNT];
    volume = 1.0f;
    startMediaTimeState = START_NOT_SET;
    audioAttributes = AudioAttributes.DEFAULT;
    audioSessionId = C.AUDIO_SESSION_ID_UNSET;
    playbackParameters = PlaybackParameters.DEFAULT;
    drainingAudioProcessorIndex = C.INDEX_UNSET;
    this.audioProcessors = new AudioProcessor[0];
    outputBuffers = new ByteBuffer[0];
    playbackParametersCheckpoints = new LinkedList<>();
  }

  /**
   * Returns whether it's possible to play audio in the specified format using encoded passthrough.
   *
   * @param mimeType The format mime type.
   * @return Whether it's possible to play audio in the format using encoded passthrough.
   */
  public boolean isPassthroughSupported(String mimeType) {
    return audioCapabilities != null
        && audioCapabilities.supportsEncoding(getEncodingForMimeType(mimeType));
  }

  /**
   * Returns the playback position in the stream starting at zero, in microseconds, or
   * {@link #CURRENT_POSITION_NOT_SET} if it is not yet available.
   *
   * <p>If the device supports it, the method uses the playback timestamp from
   * {@link android.media.AudioTrack#getTimestamp}. Otherwise, it derives a smoothed position by
   * sampling the {@link android.media.AudioTrack}'s frame position.
   *
   * @param sourceEnded Specify {@code true} if no more input buffers will be provided.
   * @return The playback position relative to the start of playback, in microseconds.
   */
  public long getCurrentPositionUs(boolean sourceEnded) {
    if (!hasCurrentPositionUs()) {
      return CURRENT_POSITION_NOT_SET;
    }

    if (audioTrack.getPlayState() == PLAYSTATE_PLAYING) {
      maybeSampleSyncParams();
    }

    long systemClockUs = System.nanoTime() / 1000;
    long positionUs;
    if (audioTimestampSet) {
      // Calculate the speed-adjusted position using the timestamp (which may be in the future).
      long elapsedSinceTimestampUs = systemClockUs - (audioTrackUtil.getTimestampNanoTime() / 1000);
      long elapsedSinceTimestampFrames = durationUsToFrames(elapsedSinceTimestampUs);
      long elapsedFrames = audioTrackUtil.getTimestampFramePosition() + elapsedSinceTimestampFrames;
      positionUs = framesToDurationUs(elapsedFrames);
    } else {
      if (playheadOffsetCount == 0) {
        // The AudioTrack has started, but we don't have any samples to compute a smoothed position.
        positionUs = audioTrackUtil.getPositionUs();
      } else {
        // getPlayheadPositionUs() only has a granularity of ~20 ms, so we base the position off the
        // system clock (and a smoothed offset between it and the playhead position) so as to
        // prevent jitter in the reported positions.
        positionUs = systemClockUs + smoothedPlayheadOffsetUs;
      }
      if (!sourceEnded) {
        positionUs -= latencyUs;
      }
    }

    return startMediaTimeUs + applySpeedup(positionUs);
  }

  /**
   * Configures (or reconfigures) the audio track.
   *
   * @param mimeType The mime type.
   * @param channelCount The number of channels.
   * @param sampleRate The sample rate in Hz.
   * @param pcmEncoding For PCM formats, the encoding used. One of {@link C#ENCODING_PCM_16BIT},
   *     {@link C#ENCODING_PCM_16BIT}, {@link C#ENCODING_PCM_24BIT} and
   *     {@link C#ENCODING_PCM_32BIT}.
   * @param specifiedBufferSize A specific size for the playback buffer in bytes, or 0 to infer a
   *     suitable buffer size automatically.
   * @throws ConfigurationException If an error occurs configuring the track.
   */
  public void configure(String mimeType, int channelCount, int sampleRate,
      @C.PcmEncoding int pcmEncoding, int specifiedBufferSize) throws ConfigurationException {
    configure(mimeType, channelCount, sampleRate, pcmEncoding, specifiedBufferSize, null);
  }

  /**
   * Configures (or reconfigures) the audio track.
   *
   * @param mimeType The mime type.
   * @param channelCount The number of channels.
   * @param sampleRate The sample rate in Hz.
   * @param pcmEncoding For PCM formats, the encoding used. One of {@link C#ENCODING_PCM_16BIT},
   *     {@link C#ENCODING_PCM_16BIT}, {@link C#ENCODING_PCM_24BIT} and
   *     {@link C#ENCODING_PCM_32BIT}.
   * @param specifiedBufferSize A specific size for the playback buffer in bytes, or 0 to infer a
   *     suitable buffer size automatically.
   * @param outputChannels A mapping from input to output channels that is applied to this track's
   *     input as a preprocessing step, if handling PCM input. Specify {@code null} to leave the
   *     input unchanged. Otherwise, the element at index {@code i} specifies index of the input
   *     channel to map to output channel {@code i} when preprocessing input buffers. After the
   *     map is applied the audio data will have {@code outputChannels.length} channels.
   * @throws ConfigurationException If an error occurs configuring the track.
   */
  public void configure(String mimeType, int channelCount, int sampleRate,
      @C.PcmEncoding int pcmEncoding, int specifiedBufferSize, int[] outputChannels)
      throws ConfigurationException {
    boolean passthrough = !MimeTypes.AUDIO_RAW.equals(mimeType);
    @C.Encoding int encoding = passthrough ? getEncodingForMimeType(mimeType) : pcmEncoding;
    boolean flush = false;
    if (!passthrough) {
      pcmFrameSize = Util.getPcmFrameSize(pcmEncoding, channelCount);
      channelMappingAudioProcessor.setChannelMap(outputChannels);
      for (AudioProcessor audioProcessor : availableAudioProcessors) {
        try {
          flush |= audioProcessor.configure(sampleRate, channelCount, encoding);
        } catch (AudioProcessor.UnhandledFormatException e) {
          throw new ConfigurationException(e);
        }
        if (audioProcessor.isActive()) {
          channelCount = audioProcessor.getOutputChannelCount();
          encoding = audioProcessor.getOutputEncoding();
        }
      }
      if (flush) {
        resetAudioProcessors();
      }
    }

    int channelConfig;
    switch (channelCount) {
      case 1:
        channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        break;
      case 2:
        channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
        break;
      case 3:
        channelConfig = AudioFormat.CHANNEL_OUT_STEREO | AudioFormat.CHANNEL_OUT_FRONT_CENTER;
        break;
      case 4:
        channelConfig = AudioFormat.CHANNEL_OUT_QUAD;
        break;
      case 5:
        channelConfig = AudioFormat.CHANNEL_OUT_QUAD | AudioFormat.CHANNEL_OUT_FRONT_CENTER;
        break;
      case 6:
        channelConfig = AudioFormat.CHANNEL_OUT_5POINT1;
        break;
      case 7:
        channelConfig = AudioFormat.CHANNEL_OUT_5POINT1 | AudioFormat.CHANNEL_OUT_BACK_CENTER;
        break;
      case 8:
        channelConfig = C.CHANNEL_OUT_7POINT1_SURROUND;
        break;
      default:
        throw new ConfigurationException("Unsupported channel count: " + channelCount);
    }

    // Workaround for overly strict channel configuration checks on nVidia Shield.
    if (Util.SDK_INT <= 23 && "foster".equals(Util.DEVICE) && "NVIDIA".equals(Util.MANUFACTURER)) {
      switch (channelCount) {
        case 7:
          channelConfig = C.CHANNEL_OUT_7POINT1_SURROUND;
          break;
        case 3:
        case 5:
          channelConfig = AudioFormat.CHANNEL_OUT_5POINT1;
          break;
        default:
          break;
      }
    }

    // Workaround for Nexus Player not reporting support for mono passthrough.
    // (See [Internal: b/34268671].)
    if (Util.SDK_INT <= 25 && "fugu".equals(Util.DEVICE) && passthrough && channelCount == 1) {
      channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
    }

    if (!flush && isInitialized() && this.encoding == encoding && this.sampleRate == sampleRate
        && this.channelConfig == channelConfig) {
      // We already have an audio track with the correct sample rate, channel config and encoding.
      return;
    }

    reset();

    this.encoding = encoding;
    this.passthrough = passthrough;
    this.sampleRate = sampleRate;
    this.channelConfig = channelConfig;
    outputEncoding = passthrough ? encoding : C.ENCODING_PCM_16BIT;
    outputPcmFrameSize = Util.getPcmFrameSize(C.ENCODING_PCM_16BIT, channelCount);

    if (specifiedBufferSize != 0) {
      bufferSize = specifiedBufferSize;
    } else if (passthrough) {
      // TODO: Set the minimum buffer size using getMinBufferSize when it takes the encoding into
      // account. [Internal: b/25181305]
      if (outputEncoding == C.ENCODING_AC3 || outputEncoding == C.ENCODING_E_AC3) {
        // AC-3 allows bitrates up to 640 kbit/s.
        bufferSize = (int) (PASSTHROUGH_BUFFER_DURATION_US * 80 * 1024 / C.MICROS_PER_SECOND);
      } else /* (outputEncoding == C.ENCODING_DTS || outputEncoding == C.ENCODING_DTS_HD */ {
        // DTS allows an 'open' bitrate, but we assume the maximum listed value: 1536 kbit/s.
        bufferSize = (int) (PASSTHROUGH_BUFFER_DURATION_US * 192 * 1024 / C.MICROS_PER_SECOND);
      }
    } else {
      int minBufferSize =
          android.media.AudioTrack.getMinBufferSize(sampleRate, channelConfig, outputEncoding);
      Assertions.checkState(minBufferSize != ERROR_BAD_VALUE);
      int multipliedBufferSize = minBufferSize * BUFFER_MULTIPLICATION_FACTOR;
      int minAppBufferSize = (int) durationUsToFrames(MIN_BUFFER_DURATION_US) * outputPcmFrameSize;
      int maxAppBufferSize = (int) Math.max(minBufferSize,
          durationUsToFrames(MAX_BUFFER_DURATION_US) * outputPcmFrameSize);
      bufferSize = multipliedBufferSize < minAppBufferSize ? minAppBufferSize
          : multipliedBufferSize > maxAppBufferSize ? maxAppBufferSize
          : multipliedBufferSize;
    }
    bufferSizeUs = passthrough ? C.TIME_UNSET : framesToDurationUs(bufferSize / outputPcmFrameSize);

    // The old playback parameters may no longer be applicable so try to reset them now.
    setPlaybackParameters(playbackParameters);
  }

  private void resetAudioProcessors() {
    ArrayList<AudioProcessor> newAudioProcessors = new ArrayList<>();
    for (AudioProcessor audioProcessor : availableAudioProcessors) {
      if (audioProcessor.isActive()) {
        newAudioProcessors.add(audioProcessor);
      } else {
        audioProcessor.flush();
      }
    }
    int count = newAudioProcessors.size();
    audioProcessors = newAudioProcessors.toArray(new AudioProcessor[count]);
    outputBuffers = new ByteBuffer[count];
    for (int i = 0; i < count; i++) {
      AudioProcessor audioProcessor = audioProcessors[i];
      audioProcessor.flush();
      outputBuffers[i] = audioProcessor.getOutput();
    }
  }

  private void initialize() throws InitializationException {
    // If we're asynchronously releasing a previous audio track then we block until it has been
    // released. This guarantees that we cannot end up in a state where we have multiple audio
    // track instances. Without this guarantee it would be possible, in extreme cases, to exhaust
    // the shared memory that's available for audio track buffers. This would in turn cause the
    // initialization of the audio track to fail.
    releasingConditionVariable.block();

    audioTrack = initializeAudioTrack();
    int audioSessionId = audioTrack.getAudioSessionId();
    if (enablePreV21AudioSessionWorkaround) {
      if (Util.SDK_INT < 21) {
        // The workaround creates an audio track with a two byte buffer on the same session, and
        // does not release it until this object is released, which keeps the session active.
        if (keepSessionIdAudioTrack != null
            && audioSessionId != keepSessionIdAudioTrack.getAudioSessionId()) {
          releaseKeepSessionIdAudioTrack();
        }
        if (keepSessionIdAudioTrack == null) {
          keepSessionIdAudioTrack = initializeKeepSessionIdAudioTrack(audioSessionId);
        }
      }
    }
    if (this.audioSessionId != audioSessionId) {
      this.audioSessionId = audioSessionId;
      listener.onAudioSessionId(audioSessionId);
    }

    audioTrackUtil.reconfigure(audioTrack, needsPassthroughWorkarounds());
    setVolumeInternal();
    hasData = false;
  }

  /**
   * Starts or resumes playing audio if the audio track has been initialized.
   */
  public void play() {
    playing = true;
    if (isInitialized()) {
      resumeSystemTimeUs = System.nanoTime() / 1000;
      audioTrack.play();
    }
  }

  /**
   * Signals to the audio track that the next buffer is discontinuous with the previous buffer.
   */
  public void handleDiscontinuity() {
    // Force resynchronization after a skipped buffer.
    if (startMediaTimeState == START_IN_SYNC) {
      startMediaTimeState = START_NEED_SYNC;
    }
  }

  /**
   * Attempts to process data from a {@link ByteBuffer}, starting from its current position and
   * ending at its limit (exclusive). The position of the {@link ByteBuffer} is advanced by the
   * number of bytes that were handled. {@link Listener#onPositionDiscontinuity()} will be called if
   * {@code presentationTimeUs} is discontinuous with the last buffer handled since the last reset.
   * <p>
   * Returns whether the data was handled in full. If the data was not handled in full then the same
   * {@link ByteBuffer} must be provided to subsequent calls until it has been fully consumed,
   * except in the case of an interleaving call to {@link #reset()} (or an interleaving call to
   * {@link #configure(String, int, int, int, int)} that caused the track to be reset).
   *
   * @param buffer The buffer containing audio data.
   * @param presentationTimeUs The presentation timestamp of the buffer in microseconds.
   * @return Whether the buffer was handled fully.
   * @throws InitializationException If an error occurs initializing the track.
   * @throws WriteException If an error occurs writing the audio data.
   */
  @SuppressWarnings("ReferenceEquality")
  public boolean handleBuffer(ByteBuffer buffer, long presentationTimeUs)
      throws InitializationException, WriteException {
    Assertions.checkArgument(inputBuffer == null || buffer == inputBuffer);
    if (!isInitialized()) {
      initialize();
      if (playing) {
        play();
      }
    }

    if (needsPassthroughWorkarounds()) {
      // An AC-3 audio track continues to play data written while it is paused. Stop writing so its
      // buffer empties. See [Internal: b/18899620].
      if (audioTrack.getPlayState() == PLAYSTATE_PAUSED) {
        // We force an underrun to pause the track, so don't notify the listener in this case.
        hasData = false;
        return false;
      }

      // A new AC-3 audio track's playback position continues to increase from the old track's
      // position for a short time after is has been released. Avoid writing data until the playback
      // head position actually returns to zero.
      if (audioTrack.getPlayState() == PLAYSTATE_STOPPED
          && audioTrackUtil.getPlaybackHeadPosition() != 0) {
        return false;
      }
    }

    boolean hadData = hasData;
    hasData = hasPendingData();
    if (hadData && !hasData && audioTrack.getPlayState() != PLAYSTATE_STOPPED) {
      long elapsedSinceLastFeedMs = SystemClock.elapsedRealtime() - lastFeedElapsedRealtimeMs;
      listener.onUnderrun(bufferSize, C.usToMs(bufferSizeUs), elapsedSinceLastFeedMs);
    }

    if (inputBuffer == null) {
      // We are seeing this buffer for the first time.
      if (!buffer.hasRemaining()) {
        // The buffer is empty.
        return true;
      }

      if (passthrough && framesPerEncodedSample == 0) {
        // If this is the first encoded sample, calculate the sample size in frames.
        framesPerEncodedSample = getFramesPerEncodedSample(outputEncoding, buffer);
      }

      if (drainingPlaybackParameters != null) {
        if (!drainAudioProcessorsToEndOfStream()) {
          // Don't process any more input until draining completes.
          return false;
        }
        // Store the position and corresponding media time from which the parameters will apply.
        playbackParametersCheckpoints.add(new PlaybackParametersCheckpoint(
            drainingPlaybackParameters, Math.max(0, presentationTimeUs),
            framesToDurationUs(getWrittenFrames())));
        drainingPlaybackParameters = null;
        // The audio processors have drained, so flush them. This will cause any active speed
        // adjustment audio processor to start producing audio with the new parameters.
        resetAudioProcessors();
      }

      if (startMediaTimeState == START_NOT_SET) {
        startMediaTimeUs = Math.max(0, presentationTimeUs);
        startMediaTimeState = START_IN_SYNC;
      } else {
        // Sanity check that presentationTimeUs is consistent with the expected value.
        long expectedPresentationTimeUs = startMediaTimeUs
            + framesToDurationUs(getSubmittedFrames());
        if (startMediaTimeState == START_IN_SYNC
            && Math.abs(expectedPresentationTimeUs - presentationTimeUs) > 200000) {
          Log.e(TAG, "Discontinuity detected [expected " + expectedPresentationTimeUs + ", got "
              + presentationTimeUs + "]");
          startMediaTimeState = START_NEED_SYNC;
        }
        if (startMediaTimeState == START_NEED_SYNC) {
          // Adjust startMediaTimeUs to be consistent with the current buffer's start time and the
          // number of bytes submitted.
          startMediaTimeUs += (presentationTimeUs - expectedPresentationTimeUs);
          startMediaTimeState = START_IN_SYNC;
          listener.onPositionDiscontinuity();
        }
      }

      if (passthrough) {
        submittedEncodedFrames += framesPerEncodedSample;
      } else {
        submittedPcmBytes += buffer.remaining();
      }

      inputBuffer = buffer;
    }

    if (passthrough) {
      // Passthrough buffers are not processed.
      writeBuffer(inputBuffer, presentationTimeUs);
    } else {
      processBuffers(presentationTimeUs);
    }

    if (!inputBuffer.hasRemaining()) {
      inputBuffer = null;
      return true;
    }
    return false;
  }

  private void processBuffers(long avSyncPresentationTimeUs) throws WriteException {
    int count = audioProcessors.length;
    int index = count;
    while (index >= 0) {
      ByteBuffer input = index > 0 ? outputBuffers[index - 1]
          : (inputBuffer != null ? inputBuffer : AudioProcessor.EMPTY_BUFFER);
      if (index == count) {
        writeBuffer(input, avSyncPresentationTimeUs);
      } else {
        AudioProcessor audioProcessor = audioProcessors[index];
        audioProcessor.queueInput(input);
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
  private boolean writeBuffer(ByteBuffer buffer, long avSyncPresentationTimeUs)
      throws WriteException {
    if (!buffer.hasRemaining()) {
      return true;
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
    int bytesWritten = 0;
    if (Util.SDK_INT < 21) { // passthrough == false
      // Work out how many bytes we can write without the risk of blocking.
      int bytesPending =
          (int) (writtenPcmBytes - (audioTrackUtil.getPlaybackHeadPosition() * outputPcmFrameSize));
      int bytesToWrite = bufferSize - bytesPending;
      if (bytesToWrite > 0) {
        bytesToWrite = Math.min(bytesRemaining, bytesToWrite);
        bytesWritten = audioTrack.write(preV21OutputBuffer, preV21OutputBufferOffset, bytesToWrite);
        if (bytesWritten > 0) {
          preV21OutputBufferOffset += bytesWritten;
          buffer.position(buffer.position() + bytesWritten);
        }
      }
    } else if (tunneling) {
      Assertions.checkState(avSyncPresentationTimeUs != C.TIME_UNSET);
      bytesWritten = writeNonBlockingWithAvSyncV21(audioTrack, buffer, bytesRemaining,
          avSyncPresentationTimeUs);
    } else {
      bytesWritten = writeNonBlockingV21(audioTrack, buffer, bytesRemaining);
    }

    lastFeedElapsedRealtimeMs = SystemClock.elapsedRealtime();

    if (bytesWritten < 0) {
      throw new WriteException(bytesWritten);
    }

    if (!passthrough) {
      writtenPcmBytes += bytesWritten;
    }
    if (bytesWritten == bytesRemaining) {
      if (passthrough) {
        writtenEncodedFrames += framesPerEncodedSample;
      }
      outputBuffer = null;
      return true;
    }
    return false;
  }

  /**
   * Plays out remaining audio. {@link #isEnded()} will return {@code true} when playback has ended.
   *
   * @throws WriteException If an error occurs draining data to the track.
   */
  public void playToEndOfStream() throws WriteException {
    if (handledEndOfStream || !isInitialized()) {
      return;
    }

    if (drainAudioProcessorsToEndOfStream()) {
      // The audio processors have drained, so drain the underlying audio track.
      audioTrackUtil.handleEndOfStream(getWrittenFrames());
      bytesUntilNextAvSync = 0;
      handledEndOfStream = true;
    }
  }

  private boolean drainAudioProcessorsToEndOfStream() throws WriteException {
    boolean audioProcessorNeedsEndOfStream = false;
    if (drainingAudioProcessorIndex == C.INDEX_UNSET) {
      drainingAudioProcessorIndex = passthrough ? audioProcessors.length : 0;
      audioProcessorNeedsEndOfStream = true;
    }
    while (drainingAudioProcessorIndex < audioProcessors.length) {
      AudioProcessor audioProcessor = audioProcessors[drainingAudioProcessorIndex];
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

  /**
   * Returns whether all buffers passed to {@link #handleBuffer(ByteBuffer, long)} have been
   * completely processed and played.
   */
  public boolean isEnded() {
    return !isInitialized() || (handledEndOfStream && !hasPendingData());
  }

  /**
   * Returns whether the audio track has more data pending that will be played back.
   */
  public boolean hasPendingData() {
    return isInitialized()
        && (getWrittenFrames() > audioTrackUtil.getPlaybackHeadPosition()
        || overrideHasPendingData());
  }

  /**
   * Attempts to set the playback parameters and returns the active playback parameters, which may
   * differ from those passed in.
   *
   * @param playbackParameters The new playback parameters to attempt to set.
   * @return The active playback parameters.
   */
  public PlaybackParameters setPlaybackParameters(PlaybackParameters playbackParameters) {
    if (passthrough) {
      // The playback parameters are always the default in passthrough mode.
      this.playbackParameters = PlaybackParameters.DEFAULT;
      return this.playbackParameters;
    }
    playbackParameters = new PlaybackParameters(
        sonicAudioProcessor.setSpeed(playbackParameters.speed),
        sonicAudioProcessor.setPitch(playbackParameters.pitch));
    PlaybackParameters lastSetPlaybackParameters =
        drainingPlaybackParameters != null ? drainingPlaybackParameters
            : !playbackParametersCheckpoints.isEmpty()
                ? playbackParametersCheckpoints.getLast().playbackParameters
                : this.playbackParameters;
    if (!playbackParameters.equals(lastSetPlaybackParameters)) {
      if (isInitialized()) {
        // Drain the audio processors so we can determine the frame position at which the new
        // parameters apply.
        drainingPlaybackParameters = playbackParameters;
      } else {
        this.playbackParameters = playbackParameters;
      }
    }
    return this.playbackParameters;
  }

  /**
   * Gets the {@link PlaybackParameters}.
   */
  public PlaybackParameters getPlaybackParameters() {
    return playbackParameters;
  }

  /**
   * Sets the attributes for audio playback. If the attributes have changed and if the audio track
   * is not configured for use with tunneling, then the audio track is reset and the audio session
   * id is cleared.
   * <p>
   * If the audio track is configured for use with tunneling then the audio attributes are ignored.
   * The audio track is not reset and the audio session id is not cleared. The passed attributes
   * will be used if the audio track is later re-configured into non-tunneled mode.
   *
   * @param audioAttributes The attributes for audio playback.
   */
  public void setAudioAttributes(AudioAttributes audioAttributes) {
    if (this.audioAttributes.equals(audioAttributes)) {
      return;
    }
    this.audioAttributes = audioAttributes;
    if (tunneling) {
      // The audio attributes are ignored in tunneling mode, so no need to reset.
      return;
    }
    reset();
    audioSessionId = C.AUDIO_SESSION_ID_UNSET;
  }

  /**
   * Sets the audio session id. The audio track is reset if the audio session id has changed.
   */
  public void setAudioSessionId(int audioSessionId) {
    if (this.audioSessionId != audioSessionId) {
      this.audioSessionId = audioSessionId;
      reset();
    }
  }

  /**
   * Enables tunneling. The audio track is reset if tunneling was previously disabled or if the
   * audio session id has changed. Enabling tunneling requires platform API version 21 onwards.
   * <p>
   * If this instance has {@link AudioProcessor}s and tunneling is enabled, care must be taken that
   * audio processors do not output buffers with a different duration than their input, and buffer
   * processors must produce output corresponding to their last input immediately after that input
   * is queued.
   *
   * @param tunnelingAudioSessionId The audio session id to use.
   * @throws IllegalStateException Thrown if enabling tunneling on platform API version &lt; 21.
   */
  public void enableTunnelingV21(int tunnelingAudioSessionId) {
    Assertions.checkState(Util.SDK_INT >= 21);
    if (!tunneling || audioSessionId != tunnelingAudioSessionId) {
      tunneling = true;
      audioSessionId = tunnelingAudioSessionId;
      reset();
    }
  }

  /**
   * Disables tunneling. If tunneling was previously enabled then the audio track is reset and the
   * audio session id is cleared.
   */
  public void disableTunneling() {
    if (tunneling) {
      tunneling = false;
      audioSessionId = C.AUDIO_SESSION_ID_UNSET;
      reset();
    }
  }

  /**
   * Sets the playback volume.
   *
   * @param volume A volume in the range [0.0, 1.0].
   */
  public void setVolume(float volume) {
    if (this.volume != volume) {
      this.volume = volume;
      setVolumeInternal();
    }
  }

  private void setVolumeInternal() {
    if (!isInitialized()) {
      // Do nothing.
    } else if (Util.SDK_INT >= 21) {
      setVolumeInternalV21(audioTrack, volume);
    } else {
      setVolumeInternalV3(audioTrack, volume);
    }
  }

  /**
   * Pauses playback.
   */
  public void pause() {
    playing = false;
    if (isInitialized()) {
      resetSyncParams();
      audioTrackUtil.pause();
    }
  }

  /**
   * Releases the underlying audio track asynchronously.
   * <p>
   * Calling {@link #handleBuffer(ByteBuffer, long)} will block until the audio track has been
   * released, so it is safe to use the audio track immediately after a reset. The audio session may
   * remain active until {@link #release()} is called.
   */
  public void reset() {
    if (isInitialized()) {
      submittedPcmBytes = 0;
      submittedEncodedFrames = 0;
      writtenPcmBytes = 0;
      writtenEncodedFrames = 0;
      framesPerEncodedSample = 0;
      if (drainingPlaybackParameters != null) {
        playbackParameters = drainingPlaybackParameters;
        drainingPlaybackParameters = null;
      } else if (!playbackParametersCheckpoints.isEmpty()) {
        playbackParameters = playbackParametersCheckpoints.getLast().playbackParameters;
      }
      playbackParametersCheckpoints.clear();
      playbackParametersOffsetUs = 0;
      playbackParametersPositionUs = 0;
      inputBuffer = null;
      outputBuffer = null;
      for (int i = 0; i < audioProcessors.length; i++) {
        AudioProcessor audioProcessor = audioProcessors[i];
        audioProcessor.flush();
        outputBuffers[i] = audioProcessor.getOutput();
      }
      handledEndOfStream = false;
      drainingAudioProcessorIndex = C.INDEX_UNSET;
      avSyncHeader = null;
      bytesUntilNextAvSync = 0;
      startMediaTimeState = START_NOT_SET;
      latencyUs = 0;
      resetSyncParams();
      int playState = audioTrack.getPlayState();
      if (playState == PLAYSTATE_PLAYING) {
        audioTrack.pause();
      }
      // AudioTrack.release can take some time, so we call it on a background thread.
      final android.media.AudioTrack toRelease = audioTrack;
      audioTrack = null;
      audioTrackUtil.reconfigure(null, false);
      releasingConditionVariable.close();
      new Thread() {
        @Override
        public void run() {
          try {
            toRelease.flush();
            toRelease.release();
          } finally {
            releasingConditionVariable.open();
          }
        }
      }.start();
    }
  }

  /**
   * Releases all resources associated with this instance.
   */
  public void release() {
    reset();
    releaseKeepSessionIdAudioTrack();
    for (AudioProcessor audioProcessor : availableAudioProcessors) {
      audioProcessor.reset();
    }
    audioSessionId = C.AUDIO_SESSION_ID_UNSET;
    playing = false;
  }

  /**
   * Releases {@link #keepSessionIdAudioTrack} asynchronously, if it is non-{@code null}.
   */
  private void releaseKeepSessionIdAudioTrack() {
    if (keepSessionIdAudioTrack == null) {
      return;
    }

    // AudioTrack.release can take some time, so we call it on a background thread.
    final android.media.AudioTrack toRelease = keepSessionIdAudioTrack;
    keepSessionIdAudioTrack = null;
    new Thread() {
      @Override
      public void run() {
        toRelease.release();
      }
    }.start();
  }

  /**
   * Returns whether {@link #getCurrentPositionUs} can return the current playback position.
   */
  private boolean hasCurrentPositionUs() {
    return isInitialized() && startMediaTimeState != START_NOT_SET;
  }

  /**
   * Returns the underlying audio track {@code positionUs} with any applicable speedup applied.
   */
  private long applySpeedup(long positionUs) {
    while (!playbackParametersCheckpoints.isEmpty()
        && positionUs >= playbackParametersCheckpoints.getFirst().positionUs) {
      // We are playing (or about to play) media with the new playback parameters, so update them.
      PlaybackParametersCheckpoint checkpoint = playbackParametersCheckpoints.remove();
      playbackParameters = checkpoint.playbackParameters;
      playbackParametersPositionUs = checkpoint.positionUs;
      playbackParametersOffsetUs = checkpoint.mediaTimeUs - startMediaTimeUs;
    }

    if (playbackParameters.speed == 1f) {
      return positionUs + playbackParametersOffsetUs - playbackParametersPositionUs;
    }

    if (playbackParametersCheckpoints.isEmpty()
        && sonicAudioProcessor.getOutputByteCount() >= SONIC_MIN_BYTES_FOR_SPEEDUP) {
      return playbackParametersOffsetUs
          + Util.scaleLargeTimestamp(positionUs - playbackParametersPositionUs,
          sonicAudioProcessor.getInputByteCount(), sonicAudioProcessor.getOutputByteCount());
    }

    // We are playing drained data at a previous playback speed, or don't have enough bytes to
    // calculate an accurate speedup, so fall back to multiplying by the speed.
    return playbackParametersOffsetUs
        + (long) ((double) playbackParameters.speed * (positionUs - playbackParametersPositionUs));
  }

  /**
   * Updates the audio track latency and playback position parameters.
   */
  private void maybeSampleSyncParams() {
    long playbackPositionUs = audioTrackUtil.getPositionUs();
    if (playbackPositionUs == 0) {
      // The AudioTrack hasn't output anything yet.
      return;
    }
    long systemClockUs = System.nanoTime() / 1000;
    if (systemClockUs - lastPlayheadSampleTimeUs >= MIN_PLAYHEAD_OFFSET_SAMPLE_INTERVAL_US) {
      // Take a new sample and update the smoothed offset between the system clock and the playhead.
      playheadOffsets[nextPlayheadOffsetIndex] = playbackPositionUs - systemClockUs;
      nextPlayheadOffsetIndex = (nextPlayheadOffsetIndex + 1) % MAX_PLAYHEAD_OFFSET_COUNT;
      if (playheadOffsetCount < MAX_PLAYHEAD_OFFSET_COUNT) {
        playheadOffsetCount++;
      }
      lastPlayheadSampleTimeUs = systemClockUs;
      smoothedPlayheadOffsetUs = 0;
      for (int i = 0; i < playheadOffsetCount; i++) {
        smoothedPlayheadOffsetUs += playheadOffsets[i] / playheadOffsetCount;
      }
    }

    if (needsPassthroughWorkarounds()) {
      // Don't sample the timestamp and latency if this is an AC-3 passthrough AudioTrack on
      // platform API versions 21/22, as incorrect values are returned. See [Internal: b/21145353].
      return;
    }

    if (systemClockUs - lastTimestampSampleTimeUs >= MIN_TIMESTAMP_SAMPLE_INTERVAL_US) {
      audioTimestampSet = audioTrackUtil.updateTimestamp();
      if (audioTimestampSet) {
        // Perform sanity checks on the timestamp.
        long audioTimestampUs = audioTrackUtil.getTimestampNanoTime() / 1000;
        long audioTimestampFramePosition = audioTrackUtil.getTimestampFramePosition();
        if (audioTimestampUs < resumeSystemTimeUs) {
          // The timestamp corresponds to a time before the track was most recently resumed.
          audioTimestampSet = false;
        } else if (Math.abs(audioTimestampUs - systemClockUs) > MAX_AUDIO_TIMESTAMP_OFFSET_US) {
          // The timestamp time base is probably wrong.
          String message = "Spurious audio timestamp (system clock mismatch): "
              + audioTimestampFramePosition + ", " + audioTimestampUs + ", " + systemClockUs + ", "
              + playbackPositionUs + ", " + getSubmittedFrames() + ", " + getWrittenFrames();
          if (failOnSpuriousAudioTimestamp) {
            throw new InvalidAudioTrackTimestampException(message);
          }
          Log.w(TAG, message);
          audioTimestampSet = false;
        } else if (Math.abs(framesToDurationUs(audioTimestampFramePosition) - playbackPositionUs)
            > MAX_AUDIO_TIMESTAMP_OFFSET_US) {
          // The timestamp frame position is probably wrong.
          String message = "Spurious audio timestamp (frame position mismatch): "
              + audioTimestampFramePosition + ", " + audioTimestampUs + ", " + systemClockUs + ", "
              + playbackPositionUs + ", " + getSubmittedFrames() + ", " + getWrittenFrames();
          if (failOnSpuriousAudioTimestamp) {
            throw new InvalidAudioTrackTimestampException(message);
          }
          Log.w(TAG, message);
          audioTimestampSet = false;
        }
      }
      if (getLatencyMethod != null && !passthrough) {
        try {
          // Compute the audio track latency, excluding the latency due to the buffer (leaving
          // latency due to the mixer and audio hardware driver).
          latencyUs = (Integer) getLatencyMethod.invoke(audioTrack, (Object[]) null) * 1000L
              - bufferSizeUs;
          // Sanity check that the latency is non-negative.
          latencyUs = Math.max(latencyUs, 0);
          // Sanity check that the latency isn't too large.
          if (latencyUs > MAX_LATENCY_US) {
            Log.w(TAG, "Ignoring impossibly large audio latency: " + latencyUs);
            latencyUs = 0;
          }
        } catch (Exception e) {
          // The method existed, but doesn't work. Don't try again.
          getLatencyMethod = null;
        }
      }
      lastTimestampSampleTimeUs = systemClockUs;
    }
  }

  private boolean isInitialized() {
    return audioTrack != null;
  }

  private long framesToDurationUs(long frameCount) {
    return (frameCount * C.MICROS_PER_SECOND) / sampleRate;
  }

  private long durationUsToFrames(long durationUs) {
    return (durationUs * sampleRate) / C.MICROS_PER_SECOND;
  }

  private long getSubmittedFrames() {
    return passthrough ? submittedEncodedFrames : (submittedPcmBytes / pcmFrameSize);
  }

  private long getWrittenFrames() {
    return passthrough ? writtenEncodedFrames : (writtenPcmBytes / outputPcmFrameSize);
  }

  private void resetSyncParams() {
    smoothedPlayheadOffsetUs = 0;
    playheadOffsetCount = 0;
    nextPlayheadOffsetIndex = 0;
    lastPlayheadSampleTimeUs = 0;
    audioTimestampSet = false;
    lastTimestampSampleTimeUs = 0;
  }

  /**
   * Returns whether to work around problems with passthrough audio tracks.
   * See [Internal: b/18899620, b/19187573, b/21145353].
   */
  private boolean needsPassthroughWorkarounds() {
    return Util.SDK_INT < 23
        && (outputEncoding == C.ENCODING_AC3 || outputEncoding == C.ENCODING_E_AC3);
  }

  /**
   * Returns whether the audio track should behave as though it has pending data. This is to work
   * around an issue on platform API versions 21/22 where AC-3 audio tracks can't be paused, so we
   * empty their buffers when paused. In this case, they should still behave as if they have
   * pending data, otherwise writing will never resume.
   */
  private boolean overrideHasPendingData() {
    return needsPassthroughWorkarounds()
        && audioTrack.getPlayState() == PLAYSTATE_PAUSED
        && audioTrack.getPlaybackHeadPosition() == 0;
  }

  private android.media.AudioTrack initializeAudioTrack() throws InitializationException {
    android.media.AudioTrack audioTrack;
    if (Util.SDK_INT >= 21) {
      audioTrack = createAudioTrackV21();
    } else {
      int streamType = Util.getStreamTypeForAudioUsage(audioAttributes.usage);
      if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) {
        audioTrack = new android.media.AudioTrack(streamType, sampleRate, channelConfig,
            outputEncoding, bufferSize, MODE_STREAM);
      } else {
        // Re-attach to the same audio session.
        audioTrack = new android.media.AudioTrack(streamType, sampleRate, channelConfig,
            outputEncoding, bufferSize, MODE_STREAM, audioSessionId);
      }
    }

    int state = audioTrack.getState();
    if (state != STATE_INITIALIZED) {
      try {
        audioTrack.release();
      } catch (Exception e) {
        // The track has already failed to initialize, so it wouldn't be that surprising if release
        // were to fail too. Swallow the exception.
      }
      throw new InitializationException(state, sampleRate, channelConfig, bufferSize);
    }
    return audioTrack;
  }

  @TargetApi(21)
  private android.media.AudioTrack createAudioTrackV21() {
    android.media.AudioAttributes attributes;
    if (tunneling) {
      attributes = new android.media.AudioAttributes.Builder()
          .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
          .setFlags(android.media.AudioAttributes.FLAG_HW_AV_SYNC)
          .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
          .build();
    } else {
      attributes = audioAttributes.getAudioAttributesV21();
    }
    AudioFormat format = new AudioFormat.Builder()
        .setChannelMask(channelConfig)
        .setEncoding(outputEncoding)
        .setSampleRate(sampleRate)
        .build();
    int audioSessionId = this.audioSessionId != C.AUDIO_SESSION_ID_UNSET ? this.audioSessionId
        : AudioManager.AUDIO_SESSION_ID_GENERATE;
    return new android.media.AudioTrack(attributes, format, bufferSize, MODE_STREAM,
        audioSessionId);
  }

  private android.media.AudioTrack initializeKeepSessionIdAudioTrack(int audioSessionId) {
    int sampleRate = 4000; // Equal to private android.media.AudioTrack.MIN_SAMPLE_RATE.
    int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
    @C.PcmEncoding int encoding = C.ENCODING_PCM_16BIT;
    int bufferSize = 2; // Use a two byte buffer, as it is not actually used for playback.
    return new android.media.AudioTrack(C.STREAM_TYPE_DEFAULT, sampleRate, channelConfig, encoding,
        bufferSize, MODE_STATIC, audioSessionId);
  }

  @C.Encoding
  private static int getEncodingForMimeType(String mimeType) {
    switch (mimeType) {
      case MimeTypes.AUDIO_AC3:
        return C.ENCODING_AC3;
      case MimeTypes.AUDIO_E_AC3:
        return C.ENCODING_E_AC3;
      case MimeTypes.AUDIO_DTS:
        return C.ENCODING_DTS;
      case MimeTypes.AUDIO_DTS_HD:
        return C.ENCODING_DTS_HD;
      default:
        return C.ENCODING_INVALID;
    }
  }

  private static int getFramesPerEncodedSample(@C.Encoding int encoding, ByteBuffer buffer) {
    if (encoding == C.ENCODING_DTS || encoding == C.ENCODING_DTS_HD) {
      return DtsUtil.parseDtsAudioSampleCount(buffer);
    } else if (encoding == C.ENCODING_AC3) {
      return Ac3Util.getAc3SyncframeAudioSampleCount();
    } else if (encoding == C.ENCODING_E_AC3) {
      return Ac3Util.parseEAc3SyncframeAudioSampleCount(buffer);
    } else {
      throw new IllegalStateException("Unexpected audio encoding: " + encoding);
    }
  }

  @TargetApi(21)
  private static int writeNonBlockingV21(android.media.AudioTrack audioTrack, ByteBuffer buffer,
      int size) {
    return audioTrack.write(buffer, size, WRITE_NON_BLOCKING);
  }

  @TargetApi(21)
  private int writeNonBlockingWithAvSyncV21(android.media.AudioTrack audioTrack,
      ByteBuffer buffer, int size, long presentationTimeUs) {
    // TODO: Uncomment this when [Internal ref: b/33627517] is clarified or fixed.
    // if (Util.SDK_INT >= 23) {
    //   // The underlying platform AudioTrack writes AV sync headers directly.
    //   return audioTrack.write(buffer, size, WRITE_NON_BLOCKING, presentationTimeUs * 1000);
    // }
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
      int result = audioTrack.write(avSyncHeader, avSyncHeaderBytesRemaining, WRITE_NON_BLOCKING);
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

  @TargetApi(21)
  private static void setVolumeInternalV21(android.media.AudioTrack audioTrack, float volume) {
    audioTrack.setVolume(volume);
  }

  @SuppressWarnings("deprecation")
  private static void setVolumeInternalV3(android.media.AudioTrack audioTrack, float volume) {
    audioTrack.setStereoVolume(volume, volume);
  }

  /**
   * Wraps an {@link android.media.AudioTrack} to expose useful utility methods.
   */
  private static class AudioTrackUtil {

    protected android.media.AudioTrack audioTrack;
    private boolean needsPassthroughWorkaround;
    private int sampleRate;
    private long lastRawPlaybackHeadPosition;
    private long rawPlaybackHeadWrapCount;
    private long passthroughWorkaroundPauseOffset;

    private long stopTimestampUs;
    private long stopPlaybackHeadPosition;
    private long endPlaybackHeadPosition;

    /**
     * Reconfigures the audio track utility helper to use the specified {@code audioTrack}.
     *
     * @param audioTrack The audio track to wrap.
     * @param needsPassthroughWorkaround Whether to workaround issues with pausing AC-3 passthrough
     *     audio tracks on platform API version 21/22.
     */
    public void reconfigure(android.media.AudioTrack audioTrack,
        boolean needsPassthroughWorkaround) {
      this.audioTrack = audioTrack;
      this.needsPassthroughWorkaround = needsPassthroughWorkaround;
      stopTimestampUs = C.TIME_UNSET;
      lastRawPlaybackHeadPosition = 0;
      rawPlaybackHeadWrapCount = 0;
      passthroughWorkaroundPauseOffset = 0;
      if (audioTrack != null) {
        sampleRate = audioTrack.getSampleRate();
      }
    }

    /**
     * Stops the audio track in a way that ensures media written to it is played out in full, and
     * that {@link #getPlaybackHeadPosition()} and {@link #getPositionUs()} continue to increment as
     * the remaining media is played out.
     *
     * @param writtenFrames The total number of frames that have been written.
     */
    public void handleEndOfStream(long writtenFrames) {
      stopPlaybackHeadPosition = getPlaybackHeadPosition();
      stopTimestampUs = SystemClock.elapsedRealtime() * 1000;
      endPlaybackHeadPosition = writtenFrames;
      audioTrack.stop();
    }

    /**
     * Pauses the audio track unless the end of the stream has been handled, in which case calling
     * this method does nothing.
     */
    public void pause() {
      if (stopTimestampUs != C.TIME_UNSET) {
        // We don't want to knock the audio track back into the paused state.
        return;
      }
      audioTrack.pause();
    }

    /**
     * {@link android.media.AudioTrack#getPlaybackHeadPosition()} returns a value intended to be
     * interpreted as an unsigned 32 bit integer, which also wraps around periodically. This method
     * returns the playback head position as a long that will only wrap around if the value exceeds
     * {@link Long#MAX_VALUE} (which in practice will never happen).
     *
     * @return The playback head position, in frames.
     */
    public long getPlaybackHeadPosition() {
      if (stopTimestampUs != C.TIME_UNSET) {
        // Simulate the playback head position up to the total number of frames submitted.
        long elapsedTimeSinceStopUs = (SystemClock.elapsedRealtime() * 1000) - stopTimestampUs;
        long framesSinceStop = (elapsedTimeSinceStopUs * sampleRate) / C.MICROS_PER_SECOND;
        return Math.min(endPlaybackHeadPosition, stopPlaybackHeadPosition + framesSinceStop);
      }

      int state = audioTrack.getPlayState();
      if (state == PLAYSTATE_STOPPED) {
        // The audio track hasn't been started.
        return 0;
      }

      long rawPlaybackHeadPosition = 0xFFFFFFFFL & audioTrack.getPlaybackHeadPosition();
      if (needsPassthroughWorkaround) {
        // Work around an issue with passthrough/direct AudioTracks on platform API versions 21/22
        // where the playback head position jumps back to zero on paused passthrough/direct audio
        // tracks. See [Internal: b/19187573].
        if (state == PLAYSTATE_PAUSED && rawPlaybackHeadPosition == 0) {
          passthroughWorkaroundPauseOffset = lastRawPlaybackHeadPosition;
        }
        rawPlaybackHeadPosition += passthroughWorkaroundPauseOffset;
      }
      if (lastRawPlaybackHeadPosition > rawPlaybackHeadPosition) {
        // The value must have wrapped around.
        rawPlaybackHeadWrapCount++;
      }
      lastRawPlaybackHeadPosition = rawPlaybackHeadPosition;
      return rawPlaybackHeadPosition + (rawPlaybackHeadWrapCount << 32);
    }

    /**
     * Returns the duration of played media since reconfiguration, in microseconds.
     */
    public long getPositionUs() {
      return (getPlaybackHeadPosition() * C.MICROS_PER_SECOND) / sampleRate;
    }

    /**
     * Updates the values returned by {@link #getTimestampNanoTime()} and
     * {@link #getTimestampFramePosition()}.
     *
     * @return Whether the timestamp values were updated.
     */
    public boolean updateTimestamp() {
      return false;
    }

    /**
     * Returns the {@link android.media.AudioTimestamp#nanoTime} obtained during the most recent
     * call to {@link #updateTimestamp()} that returned true.
     *
     * @return The nanoTime obtained during the most recent call to {@link #updateTimestamp()} that
     *     returned true.
     * @throws UnsupportedOperationException If the implementation does not support audio timestamp
     *     queries. {@link #updateTimestamp()} will always return false in this case.
     */
    public long getTimestampNanoTime() {
      // Should never be called if updateTimestamp() returned false.
      throw new UnsupportedOperationException();
    }

    /**
     * Returns the {@link android.media.AudioTimestamp#framePosition} obtained during the most
     * recent call to {@link #updateTimestamp()} that returned true. The value is adjusted so that
     * wrap around only occurs if the value exceeds {@link Long#MAX_VALUE} (which in practice will
     * never happen).
     *
     * @return The framePosition obtained during the most recent call to {@link #updateTimestamp()}
     *     that returned true.
     * @throws UnsupportedOperationException If the implementation does not support audio timestamp
     *     queries. {@link #updateTimestamp()} will always return false in this case.
     */
    public long getTimestampFramePosition() {
      // Should never be called if updateTimestamp() returned false.
      throw new UnsupportedOperationException();
    }

  }

  @TargetApi(19)
  private static class AudioTrackUtilV19 extends AudioTrackUtil {

    private final AudioTimestamp audioTimestamp;

    private long rawTimestampFramePositionWrapCount;
    private long lastRawTimestampFramePosition;
    private long lastTimestampFramePosition;

    public AudioTrackUtilV19() {
      audioTimestamp = new AudioTimestamp();
    }

    @Override
    public void reconfigure(android.media.AudioTrack audioTrack,
        boolean needsPassthroughWorkaround) {
      super.reconfigure(audioTrack, needsPassthroughWorkaround);
      rawTimestampFramePositionWrapCount = 0;
      lastRawTimestampFramePosition = 0;
      lastTimestampFramePosition = 0;
    }

    @Override
    public boolean updateTimestamp() {
      boolean updated = audioTrack.getTimestamp(audioTimestamp);
      if (updated) {
        long rawFramePosition = audioTimestamp.framePosition;
        if (lastRawTimestampFramePosition > rawFramePosition) {
          // The value must have wrapped around.
          rawTimestampFramePositionWrapCount++;
        }
        lastRawTimestampFramePosition = rawFramePosition;
        lastTimestampFramePosition = rawFramePosition + (rawTimestampFramePositionWrapCount << 32);
      }
      return updated;
    }

    @Override
    public long getTimestampNanoTime() {
      return audioTimestamp.nanoTime;
    }

    @Override
    public long getTimestampFramePosition() {
      return lastTimestampFramePosition;
    }

  }

  /**
   * Stores playback parameters with the position and media time at which they apply.
   */
  private static final class PlaybackParametersCheckpoint {

    private final PlaybackParameters playbackParameters;
    private final long mediaTimeUs;
    private final long positionUs;

    private PlaybackParametersCheckpoint(PlaybackParameters playbackParameters, long mediaTimeUs,
        long positionUs) {
      this.playbackParameters = playbackParameters;
      this.mediaTimeUs = mediaTimeUs;
      this.positionUs = positionUs;
    }

  }

}
