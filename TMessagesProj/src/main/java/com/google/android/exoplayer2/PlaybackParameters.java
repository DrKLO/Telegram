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
package com.google.android.exoplayer2;

import android.os.Bundle;
import androidx.annotation.CheckResult;
import androidx.annotation.FloatRange;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

/** Parameters that apply to playback, including speed setting. */
public final class PlaybackParameters implements Bundleable {

  /** The default playback parameters: real-time playback with no silence skipping. */
  public static final PlaybackParameters DEFAULT = new PlaybackParameters(/* speed= */ 1f);

  /** The factor by which playback will be sped up. */
  public final float speed;

  /** The factor by which pitch will be shifted. */
  public final float pitch;

  private final int scaledUsPerMs;

  /**
   * Creates new playback parameters that set the playback speed. The pitch of audio will not be
   * adjusted, so the effect is to time-stretch the audio.
   *
   * @param speed The factor by which playback will be sped up. Must be greater than zero.
   */
  public PlaybackParameters(float speed) {
    this(speed, /* pitch= */ 1f);
  }

  /**
   * Creates new playback parameters that set the playback speed/pitch.
   *
   * @param speed The factor by which playback will be sped up. Must be greater than zero.
   * @param pitch The factor by which the pitch of audio will be adjusted. Must be greater than
   *     zero. Useful values are {@code 1} (to time-stretch audio) and the same value as passed in
   *     as the {@code speed} (to resample audio, which is useful for slow-motion videos).
   */
  public PlaybackParameters(
      @FloatRange(from = 0, fromInclusive = false) float speed,
      @FloatRange(from = 0, fromInclusive = false) float pitch) {
    Assertions.checkArgument(speed > 0);
    Assertions.checkArgument(pitch > 0);
    this.speed = speed;
    this.pitch = pitch;
    scaledUsPerMs = Math.round(speed * 1000f);
  }

  /**
   * Returns the media time in microseconds that will elapse in {@code timeMs} milliseconds of
   * wallclock time.
   *
   * @param timeMs The time to scale, in milliseconds.
   * @return The scaled time, in microseconds.
   */
  public long getMediaTimeUsForPlayoutTimeMs(long timeMs) {
    return timeMs * scaledUsPerMs;
  }

  /**
   * Returns a copy with the given speed.
   *
   * @param speed The new speed. Must be greater than zero.
   * @return The copied playback parameters.
   */
  @CheckResult
  public PlaybackParameters withSpeed(@FloatRange(from = 0, fromInclusive = false) float speed) {
    return new PlaybackParameters(speed, pitch);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    PlaybackParameters other = (PlaybackParameters) obj;
    return this.speed == other.speed && this.pitch == other.pitch;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + Float.floatToRawIntBits(speed);
    result = 31 * result + Float.floatToRawIntBits(pitch);
    return result;
  }

  @Override
  public String toString() {
    return Util.formatInvariant("PlaybackParameters(speed=%.2f, pitch=%.2f)", speed, pitch);
  }

  // Bundleable implementation.

  private static final String FIELD_SPEED = Util.intToStringMaxRadix(0);
  private static final String FIELD_PITCH = Util.intToStringMaxRadix(1);

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putFloat(FIELD_SPEED, speed);
    bundle.putFloat(FIELD_PITCH, pitch);
    return bundle;
  }

  /** Object that can restore {@link PlaybackParameters} from a {@link Bundle}. */
  public static final Creator<PlaybackParameters> CREATOR =
      bundle -> {
        float speed = bundle.getFloat(FIELD_SPEED, /* defaultValue= */ 1f);
        float pitch = bundle.getFloat(FIELD_PITCH, /* defaultValue= */ 1f);
        return new PlaybackParameters(speed, pitch);
      };
}
