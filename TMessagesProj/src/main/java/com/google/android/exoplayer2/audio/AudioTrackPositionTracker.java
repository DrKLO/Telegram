/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.os.SystemClock;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;

/**
 * Wraps an {@link AudioTrack}, exposing a position based on {@link
 * AudioTrack#getPlaybackHeadPosition()} and {@link AudioTrack#getTimestamp(AudioTimestamp)}.
 *
 * <p>Call {@link #setAudioTrack(AudioTrack, int, int, int)} to set the audio track to wrap. Call
 * {@link #mayHandleBuffer(long)} if there is input data to write to the track. If it returns false,
 * the audio track position is stabilizing and no data may be written. Call {@link #start()}
 * immediately before calling {@link AudioTrack#play()}. Call {@link #pause()} when pausing the
 * track. Call {@link #handleEndOfStream(long)} when no more data will be written to the track. When
 * the audio track will no longer be used, call {@link #reset()}.
 */
/* package */ final class AudioTrackPositionTracker {

  /** Listener for position tracker events. */
  public interface Listener {

    /**
     * Called when the frame position is too far from the expected frame position.
     *
     * @param audioTimestampPositionFrames The frame position of the last known audio track
     *     timestamp.
     * @param audioTimestampSystemTimeUs The system time associated with the last known audio track
     *     timestamp, in microseconds.
     * @param systemTimeUs The current time.
     * @param playbackPositionUs The current playback head position in microseconds.
     */
    void onPositionFramesMismatch(
        long audioTimestampPositionFrames,
        long audioTimestampSystemTimeUs,
        long systemTimeUs,
        long playbackPositionUs);

    /**
     * Called when the system time associated with the last known audio track timestamp is
     * unexpectedly far from the current time.
     *
     * @param audioTimestampPositionFrames The frame position of the last known audio track
     *     timestamp.
     * @param audioTimestampSystemTimeUs The system time associated with the last known audio track
     *     timestamp, in microseconds.
     * @param systemTimeUs The current time.
     * @param playbackPositionUs The current playback head position in microseconds.
     */
    void onSystemTimeUsMismatch(
        long audioTimestampPositionFrames,
        long audioTimestampSystemTimeUs,
        long systemTimeUs,
        long playbackPositionUs);

    /**
     * Called when the audio track has provided an invalid latency.
     *
     * @param latencyUs The reported latency in microseconds.
     */
    void onInvalidLatency(long latencyUs);

    /**
     * Called when the audio track runs out of data to play.
     *
     * @param bufferSize The size of the sink's buffer, in bytes.
     * @param bufferSizeMs The size of the sink's buffer, in milliseconds, if it is configured for
     *     PCM output. {@link C#TIME_UNSET} if it is configured for encoded audio output, as the
     *     buffered media can have a variable bitrate so the duration may be unknown.
     */
    void onUnderrun(int bufferSize, long bufferSizeMs);
  }

  /** {@link AudioTrack} playback states. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({PLAYSTATE_STOPPED, PLAYSTATE_PAUSED, PLAYSTATE_PLAYING})
  private @interface PlayState {}
  /** @see AudioTrack#PLAYSTATE_STOPPED */
  private static final int PLAYSTATE_STOPPED = AudioTrack.PLAYSTATE_STOPPED;
  /** @see AudioTrack#PLAYSTATE_PAUSED */
  private static final int PLAYSTATE_PAUSED = AudioTrack.PLAYSTATE_PAUSED;
  /** @see AudioTrack#PLAYSTATE_PLAYING */
  private static final int PLAYSTATE_PLAYING = AudioTrack.PLAYSTATE_PLAYING;

  /**
   * AudioTrack timestamps are deemed spurious if they are offset from the system clock by more than
   * this amount.
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
  /** The duration of time used to smooth over an adjustment between position sampling modes. */
  private static final long MODE_SWITCH_SMOOTHING_DURATION_US = C.MICROS_PER_SECOND;

  private static final long FORCE_RESET_WORKAROUND_TIMEOUT_MS = 200;

  private static final int MAX_PLAYHEAD_OFFSET_COUNT = 10;
  private static final int MIN_PLAYHEAD_OFFSET_SAMPLE_INTERVAL_US = 30000;
  private static final int MIN_LATENCY_SAMPLE_INTERVAL_US = 500000;

  private final Listener listener;
  private final long[] playheadOffsets;

  @Nullable private AudioTrack audioTrack;
  private int outputPcmFrameSize;
  private int bufferSize;
  @Nullable private AudioTimestampPoller audioTimestampPoller;
  private int outputSampleRate;
  private boolean needsPassthroughWorkarounds;
  private long bufferSizeUs;

  private long smoothedPlayheadOffsetUs;
  private long lastPlayheadSampleTimeUs;

  @Nullable private Method getLatencyMethod;
  private long latencyUs;
  private boolean hasData;

  private boolean isOutputPcm;
  private long lastLatencySampleTimeUs;
  private long lastRawPlaybackHeadPosition;
  private long rawPlaybackHeadWrapCount;
  private long passthroughWorkaroundPauseOffset;
  private int nextPlayheadOffsetIndex;
  private int playheadOffsetCount;
  private long stopTimestampUs;
  private long forceResetWorkaroundTimeMs;
  private long stopPlaybackHeadPosition;
  private long endPlaybackHeadPosition;

  // Results from the previous call to getCurrentPositionUs.
  private long lastPositionUs;
  private long lastSystemTimeUs;
  private boolean lastSampleUsedGetTimestampMode;

  // Results from the last call to getCurrentPositionUs that used a different sample mode.
  private long previousModePositionUs;
  private long previousModeSystemTimeUs;

  /**
   * Creates a new audio track position tracker.
   *
   * @param listener A listener for position tracking events.
   */
  public AudioTrackPositionTracker(Listener listener) {
    this.listener = Assertions.checkNotNull(listener);
    if (Util.SDK_INT >= 18) {
      try {
        getLatencyMethod = AudioTrack.class.getMethod("getLatency", (Class<?>[]) null);
      } catch (NoSuchMethodException e) {
        // There's no guarantee this method exists. Do nothing.
      }
    }
    playheadOffsets = new long[MAX_PLAYHEAD_OFFSET_COUNT];
  }

  /**
   * Sets the {@link AudioTrack} to wrap. Subsequent method calls on this instance relate to this
   * track's position, until the next call to {@link #reset()}.
   *
   * @param audioTrack The audio track to wrap.
   * @param outputEncoding The encoding of the audio track.
   * @param outputPcmFrameSize For PCM output encodings, the frame size. The value is ignored
   *     otherwise.
   * @param bufferSize The audio track buffer size in bytes.
   */
  public void setAudioTrack(
      AudioTrack audioTrack,
      @C.Encoding int outputEncoding,
      int outputPcmFrameSize,
      int bufferSize) {
    this.audioTrack = audioTrack;
    this.outputPcmFrameSize = outputPcmFrameSize;
    this.bufferSize = bufferSize;
    audioTimestampPoller = new AudioTimestampPoller(audioTrack);
    outputSampleRate = audioTrack.getSampleRate();
    needsPassthroughWorkarounds = needsPassthroughWorkarounds(outputEncoding);
    isOutputPcm = Util.isEncodingLinearPcm(outputEncoding);
    bufferSizeUs = isOutputPcm ? framesToDurationUs(bufferSize / outputPcmFrameSize) : C.TIME_UNSET;
    lastRawPlaybackHeadPosition = 0;
    rawPlaybackHeadWrapCount = 0;
    passthroughWorkaroundPauseOffset = 0;
    hasData = false;
    stopTimestampUs = C.TIME_UNSET;
    forceResetWorkaroundTimeMs = C.TIME_UNSET;
    lastLatencySampleTimeUs = 0;
    latencyUs = 0;
  }

  public long getCurrentPositionUs(boolean sourceEnded) {
    if (Assertions.checkNotNull(this.audioTrack).getPlayState() == PLAYSTATE_PLAYING) {
      maybeSampleSyncParams();
    }

    // If the device supports it, use the playback timestamp from AudioTrack.getTimestamp.
    // Otherwise, derive a smoothed position by sampling the track's frame position.
    long systemTimeUs = System.nanoTime() / 1000;
    long positionUs;
    AudioTimestampPoller audioTimestampPoller = Assertions.checkNotNull(this.audioTimestampPoller);
    boolean useGetTimestampMode = audioTimestampPoller.hasAdvancingTimestamp();
    if (useGetTimestampMode) {
      // Calculate the speed-adjusted position using the timestamp (which may be in the future).
      long timestampPositionFrames = audioTimestampPoller.getTimestampPositionFrames();
      long timestampPositionUs = framesToDurationUs(timestampPositionFrames);
      long elapsedSinceTimestampUs = systemTimeUs - audioTimestampPoller.getTimestampSystemTimeUs();
      positionUs = timestampPositionUs + elapsedSinceTimestampUs;
    } else {
      if (playheadOffsetCount == 0) {
        // The AudioTrack has started, but we don't have any samples to compute a smoothed position.
        positionUs = getPlaybackHeadPositionUs();
      } else {
        // getPlaybackHeadPositionUs() only has a granularity of ~20 ms, so we base the position off
        // the system clock (and a smoothed offset between it and the playhead position) so as to
        // prevent jitter in the reported positions.
        positionUs = systemTimeUs + smoothedPlayheadOffsetUs;
      }
      if (!sourceEnded) {
        positionUs = Math.max(0, positionUs - latencyUs);
      }
    }

    if (lastSampleUsedGetTimestampMode != useGetTimestampMode) {
      // We've switched sampling mode.
      previousModeSystemTimeUs = lastSystemTimeUs;
      previousModePositionUs = lastPositionUs;
    }
    long elapsedSincePreviousModeUs = systemTimeUs - previousModeSystemTimeUs;
    if (elapsedSincePreviousModeUs < MODE_SWITCH_SMOOTHING_DURATION_US) {
      // Use a ramp to smooth between the old mode and the new one to avoid introducing a sudden
      // jump if the two modes disagree.
      long previousModeProjectedPositionUs = previousModePositionUs + elapsedSincePreviousModeUs;
      // A ramp consisting of 1000 points distributed over MODE_SWITCH_SMOOTHING_DURATION_US.
      long rampPoint = (elapsedSincePreviousModeUs * 1000) / MODE_SWITCH_SMOOTHING_DURATION_US;
      positionUs *= rampPoint;
      positionUs += (1000 - rampPoint) * previousModeProjectedPositionUs;
      positionUs /= 1000;
    }

    lastSystemTimeUs = systemTimeUs;
    lastPositionUs = positionUs;
    lastSampleUsedGetTimestampMode = useGetTimestampMode;
    return positionUs;
  }

  /** Starts position tracking. Must be called immediately before {@link AudioTrack#play()}. */
  public void start() {
    Assertions.checkNotNull(audioTimestampPoller).reset();
  }

  /** Returns whether the audio track is in the playing state. */
  public boolean isPlaying() {
    return Assertions.checkNotNull(audioTrack).getPlayState() == PLAYSTATE_PLAYING;
  }

  /**
   * Checks the state of the audio track and returns whether the caller can write data to the track.
   * Notifies {@link Listener#onUnderrun(int, long)} if the track has underrun.
   *
   * @param writtenFrames The number of frames that have been written.
   * @return Whether the caller can write data to the track.
   */
  public boolean mayHandleBuffer(long writtenFrames) {
    @PlayState int playState = Assertions.checkNotNull(audioTrack).getPlayState();
    if (needsPassthroughWorkarounds) {
      // An AC-3 audio track continues to play data written while it is paused. Stop writing so its
      // buffer empties. See [Internal: b/18899620].
      if (playState == PLAYSTATE_PAUSED) {
        // We force an underrun to pause the track, so don't notify the listener in this case.
        hasData = false;
        return false;
      }

      // A new AC-3 audio track's playback position continues to increase from the old track's
      // position for a short time after is has been released. Avoid writing data until the playback
      // head position actually returns to zero.
      if (playState == PLAYSTATE_STOPPED && getPlaybackHeadPosition() == 0) {
        return false;
      }
    }

    boolean hadData = hasData;
    hasData = hasPendingData(writtenFrames);
    if (hadData && !hasData && playState != PLAYSTATE_STOPPED && listener != null) {
      listener.onUnderrun(bufferSize, C.usToMs(bufferSizeUs));
    }

    return true;
  }

  /**
   * Returns an estimate of the number of additional bytes that can be written to the audio track's
   * buffer without running out of space.
   *
   * <p>May only be called if the output encoding is one of the PCM encodings.
   *
   * @param writtenBytes The number of bytes written to the audio track so far.
   * @return An estimate of the number of bytes that can be written.
   */
  public int getAvailableBufferSize(long writtenBytes) {
    int bytesPending = (int) (writtenBytes - (getPlaybackHeadPosition() * outputPcmFrameSize));
    return bufferSize - bytesPending;
  }

  /** Returns whether the track is in an invalid state and must be recreated. */
  public boolean isStalled(long writtenFrames) {
    return forceResetWorkaroundTimeMs != C.TIME_UNSET
        && writtenFrames > 0
        && SystemClock.elapsedRealtime() - forceResetWorkaroundTimeMs
            >= FORCE_RESET_WORKAROUND_TIMEOUT_MS;
  }

  /**
   * Records the writing position at which the stream ended, so that the reported position can
   * continue to increment while remaining data is played out.
   *
   * @param writtenFrames The number of frames that have been written.
   */
  public void handleEndOfStream(long writtenFrames) {
    stopPlaybackHeadPosition = getPlaybackHeadPosition();
    stopTimestampUs = SystemClock.elapsedRealtime() * 1000;
    endPlaybackHeadPosition = writtenFrames;
  }

  /**
   * Returns whether the audio track has any pending data to play out at its current position.
   *
   * @param writtenFrames The number of frames written to the audio track.
   * @return Whether the audio track has any pending data to play out.
   */
  public boolean hasPendingData(long writtenFrames) {
    return writtenFrames > getPlaybackHeadPosition()
        || forceHasPendingData();
  }

  /**
   * Pauses the audio track position tracker, returning whether the audio track needs to be paused
   * to cause playback to pause. If {@code false} is returned the audio track will pause without
   * further interaction, as the end of stream has been handled.
   */
  public boolean pause() {
    resetSyncParams();
    if (stopTimestampUs == C.TIME_UNSET) {
      // The audio track is going to be paused, so reset the timestamp poller to ensure it doesn't
      // supply an advancing position.
      Assertions.checkNotNull(audioTimestampPoller).reset();
      return true;
    }
    // We've handled the end of the stream already, so there's no need to pause the track.
    return false;
  }

  /**
   * Resets the position tracker. Should be called when the audio track previously passed to {@link
   * #setAudioTrack(AudioTrack, int, int, int)} is no longer in use.
   */
  public void reset() {
    resetSyncParams();
    audioTrack = null;
    audioTimestampPoller = null;
  }

  private void maybeSampleSyncParams() {
    long playbackPositionUs = getPlaybackHeadPositionUs();
    if (playbackPositionUs == 0) {
      // The AudioTrack hasn't output anything yet.
      return;
    }
    long systemTimeUs = System.nanoTime() / 1000;
    if (systemTimeUs - lastPlayheadSampleTimeUs >= MIN_PLAYHEAD_OFFSET_SAMPLE_INTERVAL_US) {
      // Take a new sample and update the smoothed offset between the system clock and the playhead.
      playheadOffsets[nextPlayheadOffsetIndex] = playbackPositionUs - systemTimeUs;
      nextPlayheadOffsetIndex = (nextPlayheadOffsetIndex + 1) % MAX_PLAYHEAD_OFFSET_COUNT;
      if (playheadOffsetCount < MAX_PLAYHEAD_OFFSET_COUNT) {
        playheadOffsetCount++;
      }
      lastPlayheadSampleTimeUs = systemTimeUs;
      smoothedPlayheadOffsetUs = 0;
      for (int i = 0; i < playheadOffsetCount; i++) {
        smoothedPlayheadOffsetUs += playheadOffsets[i] / playheadOffsetCount;
      }
    }

    if (needsPassthroughWorkarounds) {
      // Don't sample the timestamp and latency if this is an AC-3 passthrough AudioTrack on
      // platform API versions 21/22, as incorrect values are returned. See [Internal: b/21145353].
      return;
    }

    maybePollAndCheckTimestamp(systemTimeUs, playbackPositionUs);
    maybeUpdateLatency(systemTimeUs);
  }

  private void maybePollAndCheckTimestamp(long systemTimeUs, long playbackPositionUs) {
    AudioTimestampPoller audioTimestampPoller = Assertions.checkNotNull(this.audioTimestampPoller);
    if (!audioTimestampPoller.maybePollTimestamp(systemTimeUs)) {
      return;
    }

    // Perform sanity checks on the timestamp and accept/reject it.
    long audioTimestampSystemTimeUs = audioTimestampPoller.getTimestampSystemTimeUs();
    long audioTimestampPositionFrames = audioTimestampPoller.getTimestampPositionFrames();
    if (Math.abs(audioTimestampSystemTimeUs - systemTimeUs) > MAX_AUDIO_TIMESTAMP_OFFSET_US) {
      listener.onSystemTimeUsMismatch(
          audioTimestampPositionFrames,
          audioTimestampSystemTimeUs,
          systemTimeUs,
          playbackPositionUs);
      audioTimestampPoller.rejectTimestamp();
    } else if (Math.abs(framesToDurationUs(audioTimestampPositionFrames) - playbackPositionUs)
        > MAX_AUDIO_TIMESTAMP_OFFSET_US) {
      listener.onPositionFramesMismatch(
          audioTimestampPositionFrames,
          audioTimestampSystemTimeUs,
          systemTimeUs,
          playbackPositionUs);
      audioTimestampPoller.rejectTimestamp();
    } else {
      audioTimestampPoller.acceptTimestamp();
    }
  }

  private void maybeUpdateLatency(long systemTimeUs) {
    if (isOutputPcm
        && getLatencyMethod != null
        && systemTimeUs - lastLatencySampleTimeUs >= MIN_LATENCY_SAMPLE_INTERVAL_US) {
      try {
        // Compute the audio track latency, excluding the latency due to the buffer (leaving
        // latency due to the mixer and audio hardware driver).
        latencyUs =
            castNonNull((Integer) getLatencyMethod.invoke(Assertions.checkNotNull(audioTrack)))
                    * 1000L
                - bufferSizeUs;
        // Sanity check that the latency is non-negative.
        latencyUs = Math.max(latencyUs, 0);
        // Sanity check that the latency isn't too large.
        if (latencyUs > MAX_LATENCY_US) {
          listener.onInvalidLatency(latencyUs);
          latencyUs = 0;
        }
      } catch (Exception e) {
        // The method existed, but doesn't work. Don't try again.
        getLatencyMethod = null;
      }
      lastLatencySampleTimeUs = systemTimeUs;
    }
  }

  private long framesToDurationUs(long frameCount) {
    return (frameCount * C.MICROS_PER_SECOND) / outputSampleRate;
  }

  private void resetSyncParams() {
    smoothedPlayheadOffsetUs = 0;
    playheadOffsetCount = 0;
    nextPlayheadOffsetIndex = 0;
    lastPlayheadSampleTimeUs = 0;
    lastSystemTimeUs = 0;
    previousModeSystemTimeUs = 0;
  }

  /**
   * If passthrough workarounds are enabled, pausing is implemented by forcing the AudioTrack to
   * underrun. In this case, still behave as if we have pending data, otherwise writing won't
   * resume.
   */
  private boolean forceHasPendingData() {
    return needsPassthroughWorkarounds
        && Assertions.checkNotNull(audioTrack).getPlayState() == AudioTrack.PLAYSTATE_PAUSED
        && getPlaybackHeadPosition() == 0;
  }

  /**
   * Returns whether to work around problems with passthrough audio tracks. See [Internal:
   * b/18899620, b/19187573, b/21145353].
   */
  private static boolean needsPassthroughWorkarounds(@C.Encoding int outputEncoding) {
    return Util.SDK_INT < 23
        && (outputEncoding == C.ENCODING_AC3 || outputEncoding == C.ENCODING_E_AC3);
  }

  private long getPlaybackHeadPositionUs() {
    return framesToDurationUs(getPlaybackHeadPosition());
  }

  /**
   * {@link AudioTrack#getPlaybackHeadPosition()} returns a value intended to be interpreted as an
   * unsigned 32 bit integer, which also wraps around periodically. This method returns the playback
   * head position as a long that will only wrap around if the value exceeds {@link Long#MAX_VALUE}
   * (which in practice will never happen).
   *
   * @return The playback head position, in frames.
   */
  private long getPlaybackHeadPosition() {
    AudioTrack audioTrack = Assertions.checkNotNull(this.audioTrack);
    if (stopTimestampUs != C.TIME_UNSET) {
      // Simulate the playback head position up to the total number of frames submitted.
      long elapsedTimeSinceStopUs = (SystemClock.elapsedRealtime() * 1000) - stopTimestampUs;
      long framesSinceStop = (elapsedTimeSinceStopUs * outputSampleRate) / C.MICROS_PER_SECOND;
      return Math.min(endPlaybackHeadPosition, stopPlaybackHeadPosition + framesSinceStop);
    }

    int state = audioTrack.getPlayState();
    if (state == PLAYSTATE_STOPPED) {
      // The audio track hasn't been started.
      return 0;
    }

    long rawPlaybackHeadPosition = 0xFFFFFFFFL & audioTrack.getPlaybackHeadPosition();
    if (needsPassthroughWorkarounds) {
      // Work around an issue with passthrough/direct AudioTracks on platform API versions 21/22
      // where the playback head position jumps back to zero on paused passthrough/direct audio
      // tracks. See [Internal: b/19187573].
      if (state == PLAYSTATE_PAUSED && rawPlaybackHeadPosition == 0) {
        passthroughWorkaroundPauseOffset = lastRawPlaybackHeadPosition;
      }
      rawPlaybackHeadPosition += passthroughWorkaroundPauseOffset;
    }

    if (Util.SDK_INT <= 29) {
      if (rawPlaybackHeadPosition == 0
          && lastRawPlaybackHeadPosition > 0
          && state == PLAYSTATE_PLAYING) {
        // If connecting a Bluetooth audio device fails, the AudioTrack may be left in a state
        // where its Java API is in the playing state, but the native track is stopped. When this
        // happens the playback head position gets stuck at zero. In this case, return the old
        // playback head position and force the track to be reset after
        // {@link #FORCE_RESET_WORKAROUND_TIMEOUT_MS} has elapsed.
        if (forceResetWorkaroundTimeMs == C.TIME_UNSET) {
          forceResetWorkaroundTimeMs = SystemClock.elapsedRealtime();
        }
        return lastRawPlaybackHeadPosition;
      } else {
        forceResetWorkaroundTimeMs = C.TIME_UNSET;
      }
    }

    if (lastRawPlaybackHeadPosition > rawPlaybackHeadPosition) {
      // The value must have wrapped around.
      rawPlaybackHeadWrapCount++;
    }
    lastRawPlaybackHeadPosition = rawPlaybackHeadPosition;
    return rawPlaybackHeadPosition + (rawPlaybackHeadWrapCount << 32);
  }
}
