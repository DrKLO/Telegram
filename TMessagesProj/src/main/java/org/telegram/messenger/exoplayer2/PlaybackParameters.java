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
package org.telegram.messenger.exoplayer2;

/**
 * The parameters that apply to playback.
 */
public final class PlaybackParameters {

  /**
   * The default playback parameters: real-time playback with no pitch modification.
   */
  public static final PlaybackParameters DEFAULT = new PlaybackParameters(1f, 1f);

  /**
   * The factor by which playback will be sped up.
   */
  public final float speed;

  /**
   * The factor by which the audio pitch will be scaled.
   */
  public final float pitch;

  private final int scaledUsPerMs;

  /**
   * Creates new playback parameters.
   *
   * @param speed The factor by which playback will be sped up.
   * @param pitch The factor by which the audio pitch will be scaled.
   */
  public PlaybackParameters(float speed, float pitch) {
    this.speed = speed;
    this.pitch = pitch;
    scaledUsPerMs = Math.round(speed * 1000f);
  }

  /**
   * Scales the millisecond duration {@code timeMs} by the playback speed, returning the result in
   * microseconds.
   *
   * @param timeMs The time to scale, in milliseconds.
   * @return The scaled time, in microseconds.
   */
  public long getSpeedAdjustedDurationUs(long timeMs) {
    return timeMs * scaledUsPerMs;
  }

  @Override
  public boolean equals(Object obj) {
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

}
