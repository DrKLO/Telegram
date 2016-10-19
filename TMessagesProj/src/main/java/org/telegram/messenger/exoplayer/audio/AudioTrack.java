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
package org.telegram.messenger.exoplayer.audio;

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.media.PlaybackParams;
import android.os.ConditionVariable;
import android.os.SystemClock;
import android.util.Log;
import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.util.Ac3Util;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.DtsUtil;
import org.telegram.messenger.exoplayer.util.MimeTypes;
import org.telegram.messenger.exoplayer.util.Util;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * Plays audio data. The implementation delegates to an {@link android.media.AudioTrack} and handles
 * playback position smoothing, non-blocking writes and reconfiguration.
 * <p>
 * Before starting playback, specify the input audio format by calling one of the {@link #configure}
 * methods and {@link #initialize} the instance, optionally specifying an audio session.
 * <p>
 * Call {@link #handleBuffer(ByteBuffer, int, int, long)} to write data to play back, and
 * {@link #handleDiscontinuity()} when a buffer is skipped. Call {@link #play()} to start playing
 * back written data.
 * <p>
 * Call {@link #configure} again whenever the input format changes. If {@link #isInitialized()}
 * returns false after calling {@link #configure}, it is necessary to re-{@link #initialize} the
 * instance before writing more data.
 * <p>
 * The underlying framework audio track is created by {@link #initialize} and released
 * asynchronously by {@link #reset} (and {@link #configure}, unless the format is unchanged).
 * Reinitialization blocks until releasing the old audio track completes. It is safe to
 * re-{@link #initialize} the instance after calling {@link #reset()}, without reconfiguration.
 * <p>
 * Call {@link #release()} when the instance will no longer be used.
 */
public final class AudioTrack {

  /**
   * Thrown when a failure occurs instantiating an {@link android.media.AudioTrack}.
   */
  public static final class InitializationException extends Exception {

    /**
     * The state as reported by {@link android.media.AudioTrack#getState()}.
     */
    public final int audioTrackState;

    public InitializationException(
        int audioTrackState, int sampleRate, int channelConfig, int bufferSize) {
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
     * The value returned from {@link android.media.AudioTrack#write(byte[], int, int)}.
     */
    public final int errorCode;

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

    public InvalidAudioTrackTimestampException(String message) {
      super(message);
    }

  }

  /**
   * Returned in the result of {@link #handleBuffer} if the buffer was discontinuous.
   */
  public static final int RESULT_POSITION_DISCONTINUITY = 1;
  /**
   * Returned in the result of {@link #handleBuffer} if the buffer can be released.
   */
  public static final int RESULT_BUFFER_CONSUMED = 2;

  /**
   * Represents an unset {@link android.media.AudioTrack} session identifier.
   */
  public static final int SESSION_ID_NOT_SET = 0;

  /**
   * Returned by {@link #getCurrentPositionUs} when the position is not set.
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

  private static final String TAG = "AudioTrack";

  /**
   * AudioTrack timestamps are deemed spurious if they are offset from the system clock by more
   * than this amount.
   *
   * <p>This is a fail safe that should not be required on correctly functioning devices.
   */
  private static final long MAX_AUDIO_TIMESTAMP_OFFSET_US = 5 * C.MICROS_PER_SECOND;

  /**
   * AudioTrack latencies are deemed impossibly large if they are greater than this amount.
   *
   * <p>This is a fail safe that should not be required on correctly functioning devices.
   */
  private static final long MAX_LATENCY_US = 5 * C.MICROS_PER_SECOND;

  private static final int START_NOT_SET = 0;
  private static final int START_IN_SYNC = 1;
  private static final int START_NEED_SYNC = 2;

  private static final int MAX_PLAYHEAD_OFFSET_COUNT = 10;
  private static final int MIN_PLAYHEAD_OFFSET_SAMPLE_INTERVAL_US = 30000;
  private static final int MIN_TIMESTAMP_SAMPLE_INTERVAL_US = 500000;

  /**
   * Whether to enable a workaround for an issue where an audio effect does not keep its session
   * active across releasing/initializing a new audio track, on platform API version before 21.
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
  private final int streamType;
  private final ConditionVariable releasingConditionVariable;
  private final long[] playheadOffsets;
  private final AudioTrackUtil audioTrackUtil;

  /**
   * Used to keep the audio session active on pre-V21 builds (see {@link #initialize()}).
   */
  private android.media.AudioTrack keepSessionIdAudioTrack;

  private android.media.AudioTrack audioTrack;
  private int sampleRate;
  private int channelConfig;
  private int sourceEncoding;
  private int targetEncoding;
  private boolean passthrough;
  private int pcmFrameSize;
  private int bufferSize;
  private long bufferSizeUs;

  private int nextPlayheadOffsetIndex;
  private int playheadOffsetCount;
  private long smoothedPlayheadOffsetUs;
  private long lastPlayheadSampleTimeUs;
  private boolean audioTimestampSet;
  private long lastTimestampSampleTimeUs;

  private Method getLatencyMethod;
  private long submittedPcmBytes;
  private long submittedEncodedFrames;
  private int framesPerEncodedSample;
  private int startMediaTimeState;
  private long startMediaTimeUs;
  private long resumeSystemTimeUs;
  private long latencyUs;
  private float volume;

  private byte[] temporaryBuffer;
  private int temporaryBufferOffset;
  private int bufferBytesRemaining;

  private ByteBuffer resampledBuffer;
  private boolean useResampledBuffer;

  /**
   * Creates an audio track with default audio capabilities (no encoded audio passthrough support).
   */
  public AudioTrack() {
    this(null, AudioManager.STREAM_MUSIC);
  }

  /**
   * Creates an audio track using the specified audio capabilities and stream type.
   *
   * @param audioCapabilities The current audio playback capabilities.
   * @param streamType The type of audio stream for the underlying {@link android.media.AudioTrack}.
   */
  public AudioTrack(AudioCapabilities audioCapabilities, int streamType) {
    this.audioCapabilities = audioCapabilities;
    this.streamType = streamType;
    releasingConditionVariable = new ConditionVariable(true);
    if (Util.SDK_INT >= 18) {
      try {
        getLatencyMethod =
            android.media.AudioTrack.class.getMethod("getLatency", (Class<?>[]) null);
      } catch (NoSuchMethodException e) {
        // There's no guarantee this method exists. Do nothing.
      }
    }
    if (Util.SDK_INT >= 23) {
      audioTrackUtil = new AudioTrackUtilV23();
    } else if (Util.SDK_INT >= 19) {
      audioTrackUtil = new AudioTrackUtilV19();
    } else {
      audioTrackUtil = new AudioTrackUtil();
    }
    playheadOffsets = new long[MAX_PLAYHEAD_OFFSET_COUNT];
    volume = 1.0f;
    startMediaTimeState = START_NOT_SET;
  }

  /**
   * Returns whether it is possible to play back input audio in the specified format using encoded
   * audio passthrough.
   */
  public boolean isPassthroughSupported(String mimeType) {
    return audioCapabilities != null
        && audioCapabilities.supportsEncoding(getEncodingForMimeType(mimeType));
  }

  /**
   * Returns whether the audio track has been successfully initialized via {@link #initialize} and
   * not yet {@link #reset}.
   */
  public boolean isInitialized() {
    return audioTrack != null;
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

    if (audioTrack.getPlayState() == android.media.AudioTrack.PLAYSTATE_PLAYING) {
      maybeSampleSyncParams();
    }

    long systemClockUs = System.nanoTime() / 1000;
    long currentPositionUs;
    if (audioTimestampSet) {
      // How long ago in the past the audio timestamp is (negative if it's in the future).
      long presentationDiff = systemClockUs - (audioTrackUtil.getTimestampNanoTime() / 1000);
      // Fixes such difference if the playback speed is not real time speed.
      long actualSpeedPresentationDiff = (long) (presentationDiff
          * audioTrackUtil.getPlaybackSpeed());
      long framesDiff = durationUsToFrames(actualSpeedPresentationDiff);
      // The position of the frame that's currently being presented.
      long currentFramePosition = audioTrackUtil.getTimestampFramePosition() + framesDiff;
      currentPositionUs = framesToDurationUs(currentFramePosition) + startMediaTimeUs;
    } else {
      if (playheadOffsetCount == 0) {
        // The AudioTrack has started, but we don't have any samples to compute a smoothed position.
        currentPositionUs = audioTrackUtil.getPlaybackHeadPositionUs() + startMediaTimeUs;
      } else {
        // getPlayheadPositionUs() only has a granularity of ~20ms, so we base the position off the
        // system clock (and a smoothed offset between it and the playhead position) so as to
        // prevent jitter in the reported positions.
        currentPositionUs = systemClockUs + smoothedPlayheadOffsetUs + startMediaTimeUs;
      }
      if (!sourceEnded) {
        currentPositionUs -= latencyUs;
      }
    }

    return currentPositionUs;
  }

  /**
   * Configures (or reconfigures) the audio track, inferring a suitable buffer size automatically.
   *
   * @param mimeType The mime type.
   * @param channelCount The number of channels.
   * @param sampleRate The sample rate in Hz.
   * @param pcmEncoding For PCM formats, the encoding used. One of {@link C#ENCODING_PCM_16BIT},
   *     {@link C#ENCODING_PCM_16BIT}, {@link C#ENCODING_PCM_24BIT} and
   *     {@link C#ENCODING_PCM_32BIT}.
   */
  public void configure(String mimeType, int channelCount, int sampleRate, int pcmEncoding) {
    configure(mimeType, channelCount, sampleRate, pcmEncoding, 0);
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
   */
  public void configure(String mimeType, int channelCount, int sampleRate, int pcmEncoding,
      int specifiedBufferSize) {
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
        throw new IllegalArgumentException("Unsupported channel count: " + channelCount);
    }

    boolean passthrough = !MimeTypes.AUDIO_RAW.equals(mimeType);
    int sourceEncoding;
    if (passthrough) {
      sourceEncoding = getEncodingForMimeType(mimeType);
    } else if (pcmEncoding == C.ENCODING_PCM_8BIT || pcmEncoding == C.ENCODING_PCM_16BIT
        || pcmEncoding == C.ENCODING_PCM_24BIT || pcmEncoding == C.ENCODING_PCM_32BIT) {
      sourceEncoding = pcmEncoding;
    } else {
      throw new IllegalArgumentException("Unsupported PCM encoding: " + pcmEncoding);
    }

    if (isInitialized() && this.sourceEncoding == sourceEncoding && this.sampleRate == sampleRate
        && this.channelConfig == channelConfig) {
      // We already have an audio track with the correct sample rate, channel config and encoding.
      return;
    }

    reset();

    this.sourceEncoding = sourceEncoding;
    this.passthrough = passthrough;
    this.sampleRate = sampleRate;
    this.channelConfig = channelConfig;
    targetEncoding = passthrough ? sourceEncoding : C.ENCODING_PCM_16BIT;
    pcmFrameSize = 2 * channelCount; // 2 bytes per 16-bit sample * number of channels.

    if (specifiedBufferSize != 0) {
      bufferSize = specifiedBufferSize;
    } else if (passthrough) {
      // TODO: Set the minimum buffer size using getMinBufferSize when it takes the encoding into
      // account. [Internal: b/25181305]
      if (targetEncoding == C.ENCODING_AC3 || targetEncoding == C.ENCODING_E_AC3) {
        // AC-3 allows bitrates up to 640 kbit/s.
        bufferSize = (int) (PASSTHROUGH_BUFFER_DURATION_US * 80 * 1024 / C.MICROS_PER_SECOND);
      } else /* (targetEncoding == C.ENCODING_DTS || targetEncoding == C.ENCODING_DTS_HD) */ {
        // DTS allows an 'open' bitrate, but we assume the maximum listed value: 1536 kbit/s.
        bufferSize = (int) (PASSTHROUGH_BUFFER_DURATION_US * 192 * 1024 / C.MICROS_PER_SECOND);
      }
    } else {
      int minBufferSize =
          android.media.AudioTrack.getMinBufferSize(sampleRate, channelConfig, targetEncoding);
      Assertions.checkState(minBufferSize != android.media.AudioTrack.ERROR_BAD_VALUE);
      int multipliedBufferSize = minBufferSize * BUFFER_MULTIPLICATION_FACTOR;
      int minAppBufferSize = (int) durationUsToFrames(MIN_BUFFER_DURATION_US) * pcmFrameSize;
      int maxAppBufferSize = (int) Math.max(minBufferSize,
          durationUsToFrames(MAX_BUFFER_DURATION_US) * pcmFrameSize);
      bufferSize = multipliedBufferSize < minAppBufferSize ? minAppBufferSize
          : multipliedBufferSize > maxAppBufferSize ? maxAppBufferSize
          : multipliedBufferSize;
    }
    bufferSizeUs = passthrough ? C.UNKNOWN_TIME_US
        : framesToDurationUs(pcmBytesToFrames(bufferSize));
  }

  /**
   * Initializes the audio track for writing new buffers using {@link #handleBuffer}.
   *
   * @return The audio track session identifier.
   */
  public int initialize() throws InitializationException {
    return initialize(SESSION_ID_NOT_SET);
  }

  /**
   * Initializes the audio track for writing new buffers using {@link #handleBuffer}.
   *
   * @param sessionId Audio track session identifier to re-use, or {@link #SESSION_ID_NOT_SET} to
   *     create a new one.
   * @return The new (or re-used) session identifier.
   */
  public int initialize(int sessionId) throws InitializationException {
    // If we're asynchronously releasing a previous audio track then we block until it has been
    // released. This guarantees that we cannot end up in a state where we have multiple audio
    // track instances. Without this guarantee it would be possible, in extreme cases, to exhaust
    // the shared memory that's available for audio track buffers. This would in turn cause the
    // initialization of the audio track to fail.
    releasingConditionVariable.block();

    if (sessionId == SESSION_ID_NOT_SET) {
      audioTrack = new android.media.AudioTrack(streamType, sampleRate, channelConfig,
          targetEncoding, bufferSize, android.media.AudioTrack.MODE_STREAM);
    } else {
      // Re-attach to the same audio session.
      audioTrack = new android.media.AudioTrack(streamType, sampleRate, channelConfig,
          targetEncoding, bufferSize, android.media.AudioTrack.MODE_STREAM, sessionId);
    }
    checkAudioTrackInitialized();

    sessionId = audioTrack.getAudioSessionId();
    if (enablePreV21AudioSessionWorkaround) {
      if (Util.SDK_INT < 21) {
        // The workaround creates an audio track with a two byte buffer on the same session, and
        // does not release it until this object is released, which keeps the session active.
        if (keepSessionIdAudioTrack != null
            && sessionId != keepSessionIdAudioTrack.getAudioSessionId()) {
          releaseKeepSessionIdAudioTrack();
        }
        if (keepSessionIdAudioTrack == null) {
          int sampleRate = 4000; // Equal to private android.media.AudioTrack.MIN_SAMPLE_RATE.
          int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
          int encoding = C.ENCODING_PCM_16BIT;
          int bufferSize = 2; // Use a two byte buffer, as it is not actually used for playback.
          keepSessionIdAudioTrack = new android.media.AudioTrack(streamType, sampleRate,
              channelConfig, encoding, bufferSize, android.media.AudioTrack.MODE_STATIC, sessionId);
        }
      }
    }

    audioTrackUtil.reconfigure(audioTrack, needsPassthroughWorkarounds());
    setAudioTrackVolume();

    return sessionId;
  }

  /**
   * Returns the size of this {@link AudioTrack}'s buffer in bytes.
   * <p>
   * The value returned from this method may change as a result of calling one of the
   * {@link #configure} methods.
   *
   * @return The size of the buffer in bytes.
   */
  public int getBufferSize() {
    return bufferSize;
  }

  /**
   * Returns the size of the buffer in microseconds for PCM {@link AudioTrack}s, or
   * {@link C#UNKNOWN_TIME_US} for passthrough {@link AudioTrack}s.
   * <p>
   * The value returned from this method may change as a result of calling one of the
   * {@link #configure} methods.
   *
   * @return The size of the buffer in microseconds for PCM {@link AudioTrack}s, or
   *     {@link C#UNKNOWN_TIME_US} for passthrough {@link AudioTrack}s.
   */
  public long getBufferSizeUs() {
    return bufferSizeUs;
  }

  /**
   * Starts or resumes playing audio if the audio track has been initialized.
   */
  public void play() {
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
   * Attempts to write {@code size} bytes from {@code buffer} at {@code offset} to the audio track.
   * Returns a bit field containing {@link #RESULT_BUFFER_CONSUMED} if the buffer can be released
   * (due to having been written), and {@link #RESULT_POSITION_DISCONTINUITY} if the buffer was
   * discontinuous with previously written data.
   *
   * @param buffer The buffer containing audio data to play back.
   * @param offset The offset in the buffer from which to consume data.
   * @param size The number of bytes to consume from {@code buffer}.
   * @param presentationTimeUs Presentation timestamp of the next buffer in microseconds.
   * @return A bit field with {@link #RESULT_BUFFER_CONSUMED} if the buffer can be released, and
   *     {@link #RESULT_POSITION_DISCONTINUITY} if the buffer was not contiguous with previously
   *     written data.
   * @throws WriteException If an error occurs writing the audio data.
   */
  public int handleBuffer(ByteBuffer buffer, int offset, int size, long presentationTimeUs)
      throws WriteException {
    if (needsPassthroughWorkarounds()) {
      // An AC-3 audio track continues to play data written while it is paused. Stop writing so its
      // buffer empties. See [Internal: b/18899620].
      if (audioTrack.getPlayState() == android.media.AudioTrack.PLAYSTATE_PAUSED) {
        return 0;
      }

      // A new AC-3 audio track's playback position continues to increase from the old track's
      // position for a short time after is has been released. Avoid writing data until the playback
      // head position actually returns to zero.
      if (audioTrack.getPlayState() == android.media.AudioTrack.PLAYSTATE_STOPPED
          && audioTrackUtil.getPlaybackHeadPosition() != 0) {
        return 0;
      }
    }

    int result = 0;
    if (bufferBytesRemaining == 0) {
      // The previous buffer (if there was one) was fully written to the audio track. We're now
      // seeing a new buffer for the first time.
      if (size == 0) {
        return RESULT_BUFFER_CONSUMED;
      }

      useResampledBuffer = targetEncoding != sourceEncoding;
      if (useResampledBuffer) {
        Assertions.checkState(targetEncoding == C.ENCODING_PCM_16BIT);
        // Resample the buffer to get the data in the target encoding.
        resampledBuffer = resampleTo16BitPcm(buffer, offset, size, sourceEncoding, resampledBuffer);
        // Use the resampled buffer, offset and size.
        buffer = resampledBuffer;
        offset = resampledBuffer.position();
        size = resampledBuffer.limit();
      }

      bufferBytesRemaining = size;
      buffer.position(offset);
      if (passthrough && framesPerEncodedSample == 0) {
        // If this is the first encoded sample, calculate the sample size in frames.
        framesPerEncodedSample = getFramesPerEncodedSample(targetEncoding, buffer);
      }
      if (startMediaTimeState == START_NOT_SET) {
        startMediaTimeUs = Math.max(0, presentationTimeUs);
        startMediaTimeState = START_IN_SYNC;
      } else {
        // Sanity check that bufferStartTime is consistent with the expected value.
        long expectedBufferStartTime = startMediaTimeUs + framesToDurationUs(getSubmittedFrames());
        if (startMediaTimeState == START_IN_SYNC
            && Math.abs(expectedBufferStartTime - presentationTimeUs) > 200000) {
          Log.e(TAG, "Discontinuity detected [expected " + expectedBufferStartTime + ", got "
              + presentationTimeUs + "]");
          startMediaTimeState = START_NEED_SYNC;
        }
        if (startMediaTimeState == START_NEED_SYNC) {
          // Adjust startMediaTimeUs to be consistent with the current buffer's start time and the
          // number of bytes submitted.
          startMediaTimeUs += (presentationTimeUs - expectedBufferStartTime);
          startMediaTimeState = START_IN_SYNC;
          result |= RESULT_POSITION_DISCONTINUITY;
        }
      }
      if (Util.SDK_INT < 21) {
        // Copy {@code buffer} into {@code temporaryBuffer}.
        if (temporaryBuffer == null || temporaryBuffer.length < size) {
          temporaryBuffer = new byte[size];
        }
        buffer.get(temporaryBuffer, 0, size);
        temporaryBufferOffset = 0;
      }
    }

    int bytesWritten = 0;
    if (Util.SDK_INT < 21) { // passthrough == false
      // Work out how many bytes we can write without the risk of blocking.
      int bytesPending =
          (int) (submittedPcmBytes - (audioTrackUtil.getPlaybackHeadPosition() * pcmFrameSize));
      int bytesToWrite = bufferSize - bytesPending;
      if (bytesToWrite > 0) {
        bytesToWrite = Math.min(bufferBytesRemaining, bytesToWrite);
        bytesWritten = audioTrack.write(temporaryBuffer, temporaryBufferOffset, bytesToWrite);
        if (bytesWritten >= 0) {
          temporaryBufferOffset += bytesWritten;
        }
      }
    } else {
      ByteBuffer data = useResampledBuffer ? resampledBuffer : buffer;
      bytesWritten = writeNonBlockingV21(audioTrack, data, bufferBytesRemaining);
    }

    if (bytesWritten < 0) {
      throw new WriteException(bytesWritten);
    }

    bufferBytesRemaining -= bytesWritten;
    if (!passthrough) {
      submittedPcmBytes += bytesWritten;
    }
    if (bufferBytesRemaining == 0) {
      if (passthrough) {
        submittedEncodedFrames += framesPerEncodedSample;
      }
      result |= RESULT_BUFFER_CONSUMED;
    }
    return result;
  }

  /**
   * Ensures that the last data passed to {@link #handleBuffer(ByteBuffer, int, int, long)} is
   * played out in full.
   */
  public void handleEndOfStream() {
    if (isInitialized()) {
      audioTrackUtil.handleEndOfStream(getSubmittedFrames());
    }
  }

  /**
   * Returns whether the audio track has more data pending that will be played back.
   */
  public boolean hasPendingData() {
    return isInitialized()
        && (getSubmittedFrames() > audioTrackUtil.getPlaybackHeadPosition()
        || overrideHasPendingData());
  }

  /**
   * Sets the playback parameters. Only available for SDK_INT >= 23
   *
   * @throws UnsupportedOperationException if the Playback Parameters are not supported. That is,
   *     SDK_INT < 23.
   */
  public void setPlaybackParams(PlaybackParams playbackParams) {
    audioTrackUtil.setPlaybackParameters(playbackParams);
  }

  /**
   * Sets the playback volume.
   */
  public void setVolume(float volume) {
    if (this.volume != volume) {
      this.volume = volume;
      setAudioTrackVolume();
    }
  }

  private void setAudioTrackVolume() {
    if (!isInitialized()) {
      // Do nothing.
    } else if (Util.SDK_INT >= 21) {
      setAudioTrackVolumeV21(audioTrack, volume);
    } else {
      setAudioTrackVolumeV3(audioTrack, volume);
    }
  }

  /**
   * Pauses playback.
   */
  public void pause() {
    if (isInitialized()) {
      resetSyncParams();
      audioTrackUtil.pause();
    }
  }

  /**
   * Releases the underlying audio track asynchronously. Calling {@link #initialize} will block
   * until the audio track has been released, so it is safe to initialize immediately after
   * resetting. The audio session may remain active until the instance is {@link #release}d.
   */
  public void reset() {
    if (isInitialized()) {
      submittedPcmBytes = 0;
      submittedEncodedFrames = 0;
      framesPerEncodedSample = 0;
      bufferBytesRemaining = 0;
      startMediaTimeState = START_NOT_SET;
      latencyUs = 0;
      resetSyncParams();
      int playState = audioTrack.getPlayState();
      if (playState == android.media.AudioTrack.PLAYSTATE_PLAYING) {
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
   * Updates the audio track latency and playback position parameters.
   */
  private void maybeSampleSyncParams() {
    long playbackPositionUs = audioTrackUtil.getPlaybackHeadPositionUs();
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
              + playbackPositionUs;
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
              + playbackPositionUs;
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

  /**
   * Checks that {@link #audioTrack} has been successfully initialized. If it has then calling this
   * method is a no-op. If it hasn't then {@link #audioTrack} is released and set to null, and an
   * exception is thrown.
   *
   * @throws InitializationException If {@link #audioTrack} has not been successfully initialized.
   */
  private void checkAudioTrackInitialized() throws InitializationException {
    int state = audioTrack.getState();
    if (state == android.media.AudioTrack.STATE_INITIALIZED) {
      return;
    }
    // The track is not successfully initialized. Release and null the track.
    try {
      audioTrack.release();
    } catch (Exception e) {
      // The track has already failed to initialize, so it wouldn't be that surprising if release
      // were to fail too. Swallow the exception.
    } finally {
      audioTrack = null;
    }

    throw new InitializationException(state, sampleRate, channelConfig, bufferSize);
  }

  private long pcmBytesToFrames(long byteCount) {
    return byteCount / pcmFrameSize;
  }

  private long framesToDurationUs(long frameCount) {
    return (frameCount * C.MICROS_PER_SECOND) / sampleRate;
  }

  private long durationUsToFrames(long durationUs) {
    return (durationUs * sampleRate) / C.MICROS_PER_SECOND;
  }

  private long getSubmittedFrames() {
    return passthrough ? submittedEncodedFrames : pcmBytesToFrames(submittedPcmBytes);
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
        && (targetEncoding == C.ENCODING_AC3 || targetEncoding == C.ENCODING_E_AC3);
  }

  /**
   * Returns whether the audio track should behave as though it has pending data. This is to work
   * around an issue on platform API versions 21/22 where AC-3 audio tracks can't be paused, so we
   * empty their buffers when paused. In this case, they should still behave as if they have
   * pending data, otherwise writing will never resume.
   */
  private boolean overrideHasPendingData() {
    return needsPassthroughWorkarounds()
        && audioTrack.getPlayState() == android.media.AudioTrack.PLAYSTATE_PAUSED
        && audioTrack.getPlaybackHeadPosition() == 0;
  }

  /**
   * Converts the provided buffer into 16-bit PCM.
   *
   * @param buffer The buffer containing the data to convert.
   * @param offset The offset of the data in the buffer.
   * @param size The size in bytes of the data in the buffer.
   * @param sourceEncoding The data encoding.
   * @param out A buffer into which the output should be written, if its capacity is sufficient.
   * @return The 16-bit PCM output. Different to the out parameter if null was passed, or if the
   *     capacity was insufficient for the output.
   */
  private static ByteBuffer resampleTo16BitPcm(ByteBuffer buffer, int offset, int size,
      int sourceEncoding, ByteBuffer out) {
    int resampledSize;
    switch (sourceEncoding) {
      case C.ENCODING_PCM_8BIT:
        resampledSize = size * 2;
        break;
      case C.ENCODING_PCM_24BIT:
        resampledSize = (size / 3) * 2;
        break;
      case C.ENCODING_PCM_32BIT:
        resampledSize = size / 2;
        break;
      default:
        // Never happens.
        throw new IllegalStateException();
    }

    ByteBuffer resampledBuffer = out;
    if (resampledBuffer == null || resampledBuffer.capacity() < resampledSize) {
      resampledBuffer = ByteBuffer.allocateDirect(resampledSize);
    }
    resampledBuffer.position(0);
    resampledBuffer.limit(resampledSize);

    // Samples are little endian.
    int limit = offset + size;
    switch (sourceEncoding) {
      case C.ENCODING_PCM_8BIT:
        // 8->16 bit resampling. Shift each byte from [0, 256) to [-128, 128) and scale up.
        for (int i = offset; i < limit; i++) {
          resampledBuffer.put((byte) 0);
          resampledBuffer.put((byte) ((buffer.get(i) & 0xFF) - 128));
        }
        break;
      case C.ENCODING_PCM_24BIT:
        // 24->16 bit resampling. Drop the least significant byte.
        for (int i = offset; i < limit; i += 3) {
          resampledBuffer.put(buffer.get(i + 1));
          resampledBuffer.put(buffer.get(i + 2));
        }
        break;
      case C.ENCODING_PCM_32BIT:
        // 32->16 bit resampling. Drop the two least significant bytes.
        for (int i = offset; i < limit; i += 4) {
          resampledBuffer.put(buffer.get(i + 2));
          resampledBuffer.put(buffer.get(i + 3));
        }
        break;
      default:
        // Never happens.
        throw new IllegalStateException();
    }

    resampledBuffer.position(0);
    return resampledBuffer;
  }

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

  private static int getFramesPerEncodedSample(int encoding, ByteBuffer buffer) {
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
  private static int writeNonBlockingV21(
      android.media.AudioTrack audioTrack, ByteBuffer buffer, int size) {
    return audioTrack.write(buffer, size, android.media.AudioTrack.WRITE_NON_BLOCKING);
  }

  @TargetApi(21)
  private static void setAudioTrackVolumeV21(android.media.AudioTrack audioTrack, float volume) {
    audioTrack.setVolume(volume);
  }

  @SuppressWarnings("deprecation")
  private static void setAudioTrackVolumeV3(android.media.AudioTrack audioTrack, float volume) {
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
      stopTimestampUs = -1;
      lastRawPlaybackHeadPosition = 0;
      rawPlaybackHeadWrapCount = 0;
      passthroughWorkaroundPauseOffset = 0;
      if (audioTrack != null) {
        sampleRate = audioTrack.getSampleRate();
      }
    }

    /**
     * Stops the audio track in a way that ensures media written to it is played out in full, and
     * that {@link #getPlaybackHeadPosition()} and {@link #getPlaybackHeadPositionUs()} continue to
     * increment as the remaining media is played out.
     *
     * @param submittedFrames The total number of frames that have been submitted.
     */
    public void handleEndOfStream(long submittedFrames) {
      stopPlaybackHeadPosition = getPlaybackHeadPosition();
      stopTimestampUs = SystemClock.elapsedRealtime() * 1000;
      endPlaybackHeadPosition = submittedFrames;
      audioTrack.stop();
    }

    /**
     * Pauses the audio track unless the end of the stream has been handled, in which case calling
     * this method does nothing.
     */
    public void pause() {
      if (stopTimestampUs != -1) {
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
     * @return {@link android.media.AudioTrack#getPlaybackHeadPosition()} of {@link #audioTrack}
     *     expressed as a long.
     */
    public long getPlaybackHeadPosition() {
      if (stopTimestampUs != -1) {
        // Simulate the playback head position up to the total number of frames submitted.
        long elapsedTimeSinceStopUs = (SystemClock.elapsedRealtime() * 1000) - stopTimestampUs;
        long framesSinceStop = (elapsedTimeSinceStopUs * sampleRate) / C.MICROS_PER_SECOND;
        return Math.min(endPlaybackHeadPosition, stopPlaybackHeadPosition + framesSinceStop);
      }

      int state = audioTrack.getPlayState();
      if (state == android.media.AudioTrack.PLAYSTATE_STOPPED) {
        // The audio track hasn't been started.
        return 0;
      }

      long rawPlaybackHeadPosition = 0xFFFFFFFFL & audioTrack.getPlaybackHeadPosition();
      if (needsPassthroughWorkaround) {
        // Work around an issue with passthrough/direct AudioTracks on platform API versions 21/22
        // where the playback head position jumps back to zero on paused passthrough/direct audio
        // tracks. See [Internal: b/19187573].
        if (state == android.media.AudioTrack.PLAYSTATE_PAUSED && rawPlaybackHeadPosition == 0) {
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
     * Returns {@link #getPlaybackHeadPosition()} expressed as microseconds.
     */
    public long getPlaybackHeadPositionUs() {
      return (getPlaybackHeadPosition() * C.MICROS_PER_SECOND) / sampleRate;
    }

    /**
     * Updates the values returned by {@link #getTimestampNanoTime()} and
     * {@link #getTimestampFramePosition()}.
     *
     * @return True if the timestamp values were updated. False otherwise.
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

    /**
     * Sets the Playback Parameters to be used by the underlying {@link android.media.AudioTrack}.
     *
     * @param playbackParams to be used by the {@link android.media.AudioTrack}.
     * @throws UnsupportedOperationException If Playback Parameters are not supported
     *     (i.e. SDK_INT < 23).
     */
    public void setPlaybackParameters(PlaybackParams playbackParams) {
      throw new UnsupportedOperationException();
    }

    /**
     * Returns the configured playback speed according to the used Playback Parameters. If these are
     * not supported, 1.0f(normal speed) is returned.
     *
     * @return The speed factor used by the underlying {@link android.media.AudioTrack}.
     */
    public float getPlaybackSpeed() {
      return 1.0f;
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

  @TargetApi(23)
  private static class AudioTrackUtilV23 extends AudioTrackUtilV19 {

    private PlaybackParams playbackParams;
    private float playbackSpeed;

    public AudioTrackUtilV23() {
      playbackSpeed = 1.0f;
    }

    @Override
    public void reconfigure(android.media.AudioTrack audioTrack,
        boolean needsPassthroughWorkaround) {
      super.reconfigure(audioTrack, needsPassthroughWorkaround);
      maybeApplyPlaybackParams();
    }

    @Override
    public void setPlaybackParameters(PlaybackParams playbackParams) {
      playbackParams = (playbackParams != null ? playbackParams : new PlaybackParams())
          .allowDefaults();
      this.playbackParams = playbackParams;
      this.playbackSpeed = playbackParams.getSpeed();
      maybeApplyPlaybackParams();
    }

    @Override
    public float getPlaybackSpeed() {
      return playbackSpeed;
    }

    private void maybeApplyPlaybackParams() {
      if (audioTrack != null && playbackParams != null) {
        audioTrack.setPlaybackParams(playbackParams);
      }
    }

  }

}
