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
package org.telegram.messenger.exoplayer2.util;

import android.os.SystemClock;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.PlaybackParameters;

/**
 * A {@link MediaClock} whose position advances with real time based on the playback parameters when
 * started.
 */
public final class StandaloneMediaClock implements MediaClock {

  private boolean started;
  private long baseUs;
  private long baseElapsedMs;
  private PlaybackParameters playbackParameters;

  /**
   * Creates a new standalone media clock.
   */
  public StandaloneMediaClock() {
    playbackParameters = PlaybackParameters.DEFAULT;
  }

  /**
   * Starts the clock. Does nothing if the clock is already started.
   */
  public void start() {
    if (!started) {
      baseElapsedMs = SystemClock.elapsedRealtime();
      started = true;
    }
  }

  /**
   * Stops the clock. Does nothing if the clock is already stopped.
   */
  public void stop() {
    if (started) {
      setPositionUs(getPositionUs());
      started = false;
    }
  }

  /**
   * Sets the clock's position.
   *
   * @param positionUs The position to set in microseconds.
   */
  public void setPositionUs(long positionUs) {
    baseUs = positionUs;
    if (started) {
      baseElapsedMs = SystemClock.elapsedRealtime();
    }
  }

  /**
   * Synchronizes this clock with the current state of {@code clock}.
   *
   * @param clock The clock with which to synchronize.
   */
  public void synchronize(MediaClock clock) {
    setPositionUs(clock.getPositionUs());
    playbackParameters = clock.getPlaybackParameters();
  }

  @Override
  public long getPositionUs() {
    long positionUs = baseUs;
    if (started) {
      long elapsedSinceBaseMs = SystemClock.elapsedRealtime() - baseElapsedMs;
      if (playbackParameters.speed == 1f) {
        positionUs += C.msToUs(elapsedSinceBaseMs);
      } else {
        positionUs += playbackParameters.getSpeedAdjustedDurationUs(elapsedSinceBaseMs);
      }
    }
    return positionUs;
  }

  @Override
  public PlaybackParameters setPlaybackParameters(PlaybackParameters playbackParameters) {
    // Store the current position as the new base, in case the playback speed has changed.
    if (started) {
      setPositionUs(getPositionUs());
    }
    this.playbackParameters = playbackParameters;
    return playbackParameters;
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return playbackParameters;
  }

}
