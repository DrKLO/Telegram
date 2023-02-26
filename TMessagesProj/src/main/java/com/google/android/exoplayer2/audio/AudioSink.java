/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static java.lang.annotation.ElementType.TYPE_USE;

import android.media.AudioDeviceInfo;
import android.media.AudioTrack;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.analytics.PlayerId;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;

/**
 * A sink that consumes audio data.
 *
 * <p>Before starting playback, specify the input audio format by calling {@link #configure(Format,
 * int, int[])}.
 *
 * <p>Call {@link #handleBuffer(ByteBuffer, long, int)} to write data, and {@link
 * #handleDiscontinuity()} when the data being fed is discontinuous. Call {@link #play()} to start
 * playing the written data.
 *
 * <p>Call {@link #configure(Format, int, int[])} whenever the input format changes. The sink will
 * be reinitialized on the next call to {@link #handleBuffer(ByteBuffer, long, int)}.
 *
 * <p>Call {@link #flush()} to prepare the sink to receive audio data from a new playback position.
 *
 * <p>Call {@link #playToEndOfStream()} repeatedly to play out all data when no more input buffers
 * will be provided via {@link #handleBuffer(ByteBuffer, long, int)} until the next {@link
 * #flush()}. Call {@link #reset()} when the instance is no longer required.
 *
 * <p>The implementation may be backed by a platform {@link AudioTrack}. In this case, {@link
 * #setAudioSessionId(int)}, {@link #setAudioAttributes(AudioAttributes)}, {@link
 * #enableTunnelingV21()} and {@link #disableTunneling()} may be called before writing data to the
 * sink. These methods may also be called after writing data to the sink, in which case it will be
 * reinitialized as required. For implementations that are not based on platform {@link
 * AudioTrack}s, calling methods relating to audio sessions, audio attributes, and tunneling may
 * have no effect.
 */
public interface AudioSink {

  /** Listener for audio sink events. */
  interface Listener {

    /**
     * Called when the audio sink handles a buffer whose timestamp is discontinuous with the last
     * buffer handled since it was reset.
     */
    void onPositionDiscontinuity();

    /**
     * Called when the audio sink's position has increased for the first time since it was last
     * paused or flushed.
     *
     * @param playoutStartSystemTimeMs The approximate derived {@link System#currentTimeMillis()} at
     *     which playout started. Only valid if the audio track has not underrun.
     */
    default void onPositionAdvancing(long playoutStartSystemTimeMs) {}

    /**
     * Called when the audio sink runs out of data.
     *
     * <p>An audio sink implementation may never call this method (for example, if audio data is
     * consumed in batches rather than based on the sink's own clock).
     *
     * @param bufferSize The size of the sink's buffer, in bytes.
     * @param bufferSizeMs The size of the sink's buffer, in milliseconds, if it is configured for
     *     PCM output. {@link C#TIME_UNSET} if it is configured for encoded audio output, as the
     *     buffered media can have a variable bitrate so the duration may be unknown.
     * @param elapsedSinceLastFeedMs The time since the sink was last fed data, in milliseconds.
     */
    void onUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs);

    /**
     * Called when skipping silences is enabled or disabled.
     *
     * @param skipSilenceEnabled Whether skipping silences is enabled.
     */
    void onSkipSilenceEnabledChanged(boolean skipSilenceEnabled);

    /** Called when the offload buffer has been partially emptied. */
    default void onOffloadBufferEmptying() {}

    /** Called when the offload buffer has been filled completely. */
    default void onOffloadBufferFull() {}

    /**
     * Called when {@link AudioSink} has encountered an error.
     *
     * <p>If the sink writes to a platform {@link AudioTrack}, this will called for all {@link
     * AudioTrack} errors.
     *
     * <p>This method being called does not indicate that playback has failed, or that it will fail.
     * The player may be able to recover from the error (for example by recreating the AudioTrack,
     * possibly with different settings) and continue. Hence applications should <em>not</em>
     * implement this method to display a user visible error or initiate an application level retry
     * ({@link Player.Listener#onPlayerError} is the appropriate place to implement such behavior).
     * This method is called to provide the application with an opportunity to log the error if it
     * wishes to do so.
     *
     * <p>Fatal errors that cannot be recovered will be reported wrapped in a {@link
     * ExoPlaybackException} by {@link Player.Listener#onPlayerError(PlaybackException)}.
     *
     * @param audioSinkError The error that occurred. Typically an {@link InitializationException},
     *     a {@link WriteException}, or an {@link UnexpectedDiscontinuityException}.
     */
    default void onAudioSinkError(Exception audioSinkError) {}
  }

  /** Thrown when a failure occurs configuring the sink. */
  final class ConfigurationException extends Exception {

    /** Input {@link Format} of the sink when the configuration failure occurs. */
    public final Format format;

    /** Creates a new configuration exception with the specified {@code cause} and no message. */
    public ConfigurationException(Throwable cause, Format format) {
      super(cause);
      this.format = format;
    }

    /** Creates a new configuration exception with the specified {@code message} and no cause. */
    public ConfigurationException(String message, Format format) {
      super(message);
      this.format = format;
    }
  }

  /** Thrown when a failure occurs initializing the sink. */
  final class InitializationException extends Exception {

    /** The underlying {@link AudioTrack}'s state. */
    public final int audioTrackState;
    /** If the exception can be recovered by recreating the sink. */
    public final boolean isRecoverable;
    /** The input {@link Format} of the sink when the error occurs. */
    public final Format format;

    /**
     * Creates a new instance.
     *
     * @param audioTrackState The underlying {@link AudioTrack}'s state.
     * @param sampleRate The requested sample rate in Hz.
     * @param channelConfig The requested channel configuration.
     * @param bufferSize The requested buffer size in bytes.
     * @param format The input format of the sink when the error occurs.
     * @param isRecoverable Whether the exception can be recovered by recreating the sink.
     * @param audioTrackException Exception thrown during the creation of the {@link AudioTrack}.
     */
    public InitializationException(
        int audioTrackState,
        int sampleRate,
        int channelConfig,
        int bufferSize,
        Format format,
        boolean isRecoverable,
        @Nullable Exception audioTrackException) {
      super(
          "AudioTrack init failed "
              + audioTrackState
              + " "
              + ("Config(" + sampleRate + ", " + channelConfig + ", " + bufferSize + ")")
              + (isRecoverable ? " (recoverable)" : ""),
          audioTrackException);
      this.audioTrackState = audioTrackState;
      this.isRecoverable = isRecoverable;
      this.format = format;
    }
  }

  /** Thrown when a failure occurs writing to the sink. */
  final class WriteException extends Exception {

    /**
     * The error value returned from the sink implementation. If the sink writes to a platform
     * {@link AudioTrack}, this will be the error value returned from {@link
     * AudioTrack#write(byte[], int, int)} or {@link AudioTrack#write(ByteBuffer, int, int)}.
     * Otherwise, the meaning of the error code depends on the sink implementation.
     */
    public final int errorCode;
    /** If the exception can be recovered by recreating the sink. */
    public final boolean isRecoverable;
    /** The input {@link Format} of the sink when the error occurs. */
    public final Format format;

    /**
     * Creates an instance.
     *
     * @param errorCode The error value returned from the sink implementation.
     * @param format The input format of the sink when the error occurs.
     * @param isRecoverable Whether the exception can be recovered by recreating the sink.
     */
    public WriteException(int errorCode, Format format, boolean isRecoverable) {
      super("AudioTrack write failed: " + errorCode);
      this.isRecoverable = isRecoverable;
      this.errorCode = errorCode;
      this.format = format;
    }
  }

  /** Thrown when the sink encounters an unexpected timestamp discontinuity. */
  final class UnexpectedDiscontinuityException extends Exception {
    /** The actual presentation time of a sample, in microseconds. */
    public final long actualPresentationTimeUs;
    /** The expected presentation time of a sample, in microseconds. */
    public final long expectedPresentationTimeUs;

    /**
     * Creates an instance.
     *
     * @param actualPresentationTimeUs The actual presentation time of a sample, in microseconds.
     * @param expectedPresentationTimeUs The expected presentation time of a sample, in
     *     microseconds.
     */
    public UnexpectedDiscontinuityException(
        long actualPresentationTimeUs, long expectedPresentationTimeUs) {
      super(
          "Unexpected audio track timestamp discontinuity: expected "
              + expectedPresentationTimeUs
              + ", got "
              + actualPresentationTimeUs);
      this.actualPresentationTimeUs = actualPresentationTimeUs;
      this.expectedPresentationTimeUs = expectedPresentationTimeUs;
    }
  }

  /**
   * The level of support the sink provides for a format. One of {@link
   * #SINK_FORMAT_SUPPORTED_DIRECTLY}, {@link #SINK_FORMAT_SUPPORTED_WITH_TRANSCODING} or {@link
   * #SINK_FORMAT_UNSUPPORTED}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    SINK_FORMAT_SUPPORTED_DIRECTLY,
    SINK_FORMAT_SUPPORTED_WITH_TRANSCODING,
    SINK_FORMAT_UNSUPPORTED
  })
  @interface SinkFormatSupport {}
  /** The sink supports the format directly, without the need for internal transcoding. */
  int SINK_FORMAT_SUPPORTED_DIRECTLY = 2;
  /**
   * The sink supports the format, but needs to transcode it internally to do so. Internal
   * transcoding may result in lower quality and higher CPU load in some cases.
   */
  int SINK_FORMAT_SUPPORTED_WITH_TRANSCODING = 1;
  /** The sink does not support the format. */
  int SINK_FORMAT_UNSUPPORTED = 0;

  /** Returned by {@link #getCurrentPositionUs(boolean)} when the position is not set. */
  long CURRENT_POSITION_NOT_SET = Long.MIN_VALUE;

  /**
   * Sets the listener for sink events, which should be the audio renderer.
   *
   * @param listener The listener for sink events, which should be the audio renderer.
   */
  void setListener(Listener listener);

  /**
   * Sets the {@link PlayerId} of the player using this audio sink.
   *
   * @param playerId The {@link PlayerId}, or null to clear a previously set id.
   */
  default void setPlayerId(@Nullable PlayerId playerId) {}

  /**
   * Returns whether the sink supports a given {@link Format}.
   *
   * @param format The format.
   * @return Whether the sink supports the format.
   */
  boolean supportsFormat(Format format);

  /**
   * Returns the level of support that the sink provides for a given {@link Format}.
   *
   * @param format The format.
   * @return The level of support provided.
   */
  @SinkFormatSupport
  int getFormatSupport(Format format);

  /**
   * Returns the playback position in the stream starting at zero, in microseconds, or {@link
   * #CURRENT_POSITION_NOT_SET} if it is not yet available.
   *
   * @param sourceEnded Specify {@code true} if no more input buffers will be provided.
   * @return The playback position relative to the start of playback, in microseconds.
   */
  long getCurrentPositionUs(boolean sourceEnded);

  /**
   * Configures (or reconfigures) the sink.
   *
   * @param inputFormat The format of audio data provided in the input buffers.
   * @param specifiedBufferSize A specific size for the playback buffer in bytes, or 0 to infer a
   *     suitable buffer size.
   * @param outputChannels A mapping from input to output channels that is applied to this sink's
   *     input as a preprocessing step, if handling PCM input. Specify {@code null} to leave the
   *     input unchanged. Otherwise, the element at index {@code i} specifies index of the input
   *     channel to map to output channel {@code i} when preprocessing input buffers. After the map
   *     is applied the audio data will have {@code outputChannels.length} channels.
   * @throws ConfigurationException If an error occurs configuring the sink.
   */
  void configure(Format inputFormat, int specifiedBufferSize, @Nullable int[] outputChannels)
      throws ConfigurationException;

  /** Starts or resumes consuming audio if initialized. */
  void play();

  /** Signals to the sink that the next buffer may be discontinuous with the previous buffer. */
  void handleDiscontinuity();

  /**
   * Attempts to process data from a {@link ByteBuffer}, starting from its current position and
   * ending at its limit (exclusive). The position of the {@link ByteBuffer} is advanced by the
   * number of bytes that were handled. {@link Listener#onPositionDiscontinuity()} will be called if
   * {@code presentationTimeUs} is discontinuous with the last buffer handled since the last reset.
   *
   * <p>Returns whether the data was handled in full. If the data was not handled in full then the
   * same {@link ByteBuffer} must be provided to subsequent calls until it has been fully consumed,
   * except in the case of an intervening call to {@link #flush()} (or to {@link #configure(Format,
   * int, int[])} that causes the sink to be flushed).
   *
   * @param buffer The buffer containing audio data.
   * @param presentationTimeUs The presentation timestamp of the buffer in microseconds.
   * @param encodedAccessUnitCount The number of encoded access units in the buffer, or 1 if the
   *     buffer contains PCM audio. This allows batching multiple encoded access units in one
   *     buffer.
   * @return Whether the buffer was handled fully.
   * @throws InitializationException If an error occurs initializing the sink.
   * @throws WriteException If an error occurs writing the audio data.
   */
  boolean handleBuffer(ByteBuffer buffer, long presentationTimeUs, int encodedAccessUnitCount)
      throws InitializationException, WriteException;

  /**
   * Processes any remaining data. {@link #isEnded()} will return {@code true} when no data remains.
   *
   * @throws WriteException If an error occurs draining data to the sink.
   */
  void playToEndOfStream() throws WriteException;

  /**
   * Returns whether {@link #playToEndOfStream} has been called and all buffers have been processed.
   */
  boolean isEnded();

  /** Returns whether the sink has data pending that has not been consumed yet. */
  boolean hasPendingData();

  /**
   * Attempts to set the playback parameters. The audio sink may override these parameters if they
   * are not supported.
   *
   * @param playbackParameters The new playback parameters to attempt to set.
   */
  void setPlaybackParameters(PlaybackParameters playbackParameters);

  /** Returns the active {@link PlaybackParameters}. */
  PlaybackParameters getPlaybackParameters();

  /** Sets whether silences should be skipped in the audio stream. */
  void setSkipSilenceEnabled(boolean skipSilenceEnabled);

  /** Returns whether silences are skipped in the audio stream. */
  boolean getSkipSilenceEnabled();

  /**
   * Sets attributes for audio playback. If the attributes have changed and if the sink is not
   * configured for use with tunneling, then it is reset and the audio session id is cleared.
   *
   * <p>If the sink is configured for use with tunneling then the audio attributes are ignored. The
   * sink is not reset and the audio session id is not cleared. The passed attributes will be used
   * if the sink is later re-configured into non-tunneled mode.
   *
   * @param audioAttributes The attributes for audio playback.
   */
  void setAudioAttributes(AudioAttributes audioAttributes);

  /**
   * Returns the audio attributes used for audio playback, or {@code null} if the sink does not use
   * audio attributes.
   */
  @Nullable
  AudioAttributes getAudioAttributes();

  /** Sets the audio session id. */
  void setAudioSessionId(int audioSessionId);

  /** Sets the auxiliary effect. */
  void setAuxEffectInfo(AuxEffectInfo auxEffectInfo);

  /**
   * Sets the preferred audio device.
   *
   * @param audioDeviceInfo The preferred {@linkplain AudioDeviceInfo audio device}, or null to
   *     restore the default.
   */
  @RequiresApi(23)
  default void setPreferredDevice(@Nullable AudioDeviceInfo audioDeviceInfo) {}

  /**
   * Sets the offset that is added to the media timestamp before it is passed as {@code
   * presentationTimeUs} in {@link #handleBuffer(ByteBuffer, long, int)}.
   *
   * @param outputStreamOffsetUs The output stream offset in microseconds.
   */
  default void setOutputStreamOffsetUs(long outputStreamOffsetUs) {}

  /**
   * Enables tunneling, if possible. The sink is reset if tunneling was previously disabled.
   * Enabling tunneling is only possible if the sink is based on a platform {@link AudioTrack}, and
   * requires platform API version 21 onwards.
   *
   * @throws IllegalStateException Thrown if enabling tunneling on platform API version &lt; 21.
   */
  void enableTunnelingV21();

  /**
   * Disables tunneling. If tunneling was previously enabled then the sink is reset and any audio
   * session id is cleared.
   */
  void disableTunneling();

  /**
   * Sets the playback volume.
   *
   * @param volume Linear output gain to apply to all channels. Should be in the range [0.0, 1.0].
   */
  void setVolume(float volume);

  /** Pauses playback. */
  void pause();

  /**
   * Flushes the sink, after which it is ready to receive buffers from a new playback position.
   *
   * <p>The audio session may remain active until {@link #reset()} is called.
   */
  void flush();

  /**
   * Flushes the sink, after which it is ready to receive buffers from a new playback position.
   *
   * <p>Does not release the {@link AudioTrack} held by the sink.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release.
   *
   * <p>Only for experimental use as part of {@link
   * MediaCodecAudioRenderer#experimentalSetEnableKeepAudioTrackOnSeek(boolean)}.
   */
  void experimentalFlushWithoutAudioTrackRelease();

  /** Resets the sink, releasing any resources that it currently holds. */
  void reset();
}
