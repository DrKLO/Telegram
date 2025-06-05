/*
 * Copyright 2020 The Android Open Source Project
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

import static com.google.common.primitives.Longs.max;
import static java.lang.Math.abs;
import static java.lang.Math.max;

import android.os.SystemClock;
import com.google.android.exoplayer2.MediaItem.LiveConfiguration;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * A {@link LivePlaybackSpeedControl} that adjusts the playback speed using a proportional
 * controller.
 *
 * <p>The control mechanism calculates the adjusted speed as {@code 1.0 + proportionalControlFactor
 * x (currentLiveOffsetSec - targetLiveOffsetSec)}. Unit speed (1.0f) is used, if the {@code
 * currentLiveOffsetSec} is closer to {@code targetLiveOffsetSec} than the value set with {@link
 * Builder#setMaxLiveOffsetErrorMsForUnitSpeed(long)}.
 *
 * <p>The resulting speed is clamped to a minimum and maximum speed defined by the media, the
 * fallback values set with {@link Builder#setFallbackMinPlaybackSpeed(float)} and {@link
 * Builder#setFallbackMaxPlaybackSpeed(float)} or the {@link #DEFAULT_FALLBACK_MIN_PLAYBACK_SPEED
 * minimum} and {@link #DEFAULT_FALLBACK_MAX_PLAYBACK_SPEED maximum} fallback default values.
 *
 * <p>When the player rebuffers, the target live offset {@link
 * Builder#setTargetLiveOffsetIncrementOnRebufferMs(long) is increased} to adjust to the reduced
 * network capabilities. The live playback speed control also {@link
 * Builder#setMinPossibleLiveOffsetSmoothingFactor(float) keeps track} of the minimum possible live
 * offset to decrease the target live offset again if conditions improve. The minimum possible live
 * offset is derived from the current offset and the duration of buffered media.
 */
public final class DefaultLivePlaybackSpeedControl implements LivePlaybackSpeedControl {

  /**
   * The default minimum factor by which playback can be sped up that should be used if no minimum
   * playback speed is defined by the media.
   */
  public static final float DEFAULT_FALLBACK_MIN_PLAYBACK_SPEED = 0.97f;

  /**
   * The default maximum factor by which playback can be sped up that should be used if no maximum
   * playback speed is defined by the media.
   */
  public static final float DEFAULT_FALLBACK_MAX_PLAYBACK_SPEED = 1.03f;

  /**
   * The default {@link Builder#setMinUpdateIntervalMs(long) minimum interval} between playback
   * speed changes, in milliseconds.
   */
  public static final long DEFAULT_MIN_UPDATE_INTERVAL_MS = 1_000;

  /**
   * The default {@link Builder#setProportionalControlFactor(float) proportional control factor}
   * used to adjust the playback speed.
   */
  public static final float DEFAULT_PROPORTIONAL_CONTROL_FACTOR = 0.1f;

  /**
   * The default increment applied to the target live offset each time the player is rebuffering, in
   * milliseconds
   */
  public static final long DEFAULT_TARGET_LIVE_OFFSET_INCREMENT_ON_REBUFFER_MS = 500;

  /**
   * The default smoothing factor when smoothing the minimum possible live offset that can be
   * achieved during playback.
   */
  public static final float DEFAULT_MIN_POSSIBLE_LIVE_OFFSET_SMOOTHING_FACTOR = 0.999f;

  /**
   * The default maximum difference between the current live offset and the target live offset, in
   * milliseconds, for which unit speed (1.0f) is used.
   */
  public static final long DEFAULT_MAX_LIVE_OFFSET_ERROR_MS_FOR_UNIT_SPEED = 20;

  /** Builder for a {@link DefaultLivePlaybackSpeedControl}. */
  public static final class Builder {

    private float fallbackMinPlaybackSpeed;
    private float fallbackMaxPlaybackSpeed;
    private long minUpdateIntervalMs;
    private float proportionalControlFactorUs;
    private long maxLiveOffsetErrorUsForUnitSpeed;
    private long targetLiveOffsetIncrementOnRebufferUs;
    private float minPossibleLiveOffsetSmoothingFactor;

    /** Creates a builder. */
    public Builder() {
      fallbackMinPlaybackSpeed = DEFAULT_FALLBACK_MIN_PLAYBACK_SPEED;
      fallbackMaxPlaybackSpeed = DEFAULT_FALLBACK_MAX_PLAYBACK_SPEED;
      minUpdateIntervalMs = DEFAULT_MIN_UPDATE_INTERVAL_MS;
      proportionalControlFactorUs = DEFAULT_PROPORTIONAL_CONTROL_FACTOR / C.MICROS_PER_SECOND;
      maxLiveOffsetErrorUsForUnitSpeed =
          Util.msToUs(DEFAULT_MAX_LIVE_OFFSET_ERROR_MS_FOR_UNIT_SPEED);
      targetLiveOffsetIncrementOnRebufferUs =
          Util.msToUs(DEFAULT_TARGET_LIVE_OFFSET_INCREMENT_ON_REBUFFER_MS);
      minPossibleLiveOffsetSmoothingFactor = DEFAULT_MIN_POSSIBLE_LIVE_OFFSET_SMOOTHING_FACTOR;
    }

    /**
     * Sets the minimum playback speed that should be used if no minimum playback speed is defined
     * by the media.
     *
     * <p>The default is {@link #DEFAULT_FALLBACK_MIN_PLAYBACK_SPEED}.
     *
     * @param fallbackMinPlaybackSpeed The fallback minimum factor by which playback can be sped up.
     * @return This builder, for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setFallbackMinPlaybackSpeed(float fallbackMinPlaybackSpeed) {
      Assertions.checkArgument(0 < fallbackMinPlaybackSpeed && fallbackMinPlaybackSpeed <= 1f);
      this.fallbackMinPlaybackSpeed = fallbackMinPlaybackSpeed;
      return this;
    }

    /**
     * Sets the maximum playback speed that should be used if no maximum playback speed is defined
     * by the media.
     *
     * <p>The default is {@link #DEFAULT_FALLBACK_MAX_PLAYBACK_SPEED}.
     *
     * @param fallbackMaxPlaybackSpeed The fallback maximum factor by which playback can be sped up.
     * @return This builder, for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setFallbackMaxPlaybackSpeed(float fallbackMaxPlaybackSpeed) {
      Assertions.checkArgument(fallbackMaxPlaybackSpeed >= 1f);
      this.fallbackMaxPlaybackSpeed = fallbackMaxPlaybackSpeed;
      return this;
    }

    /**
     * Sets the minimum interval between playback speed changes, in milliseconds.
     *
     * <p>The default is {@link #DEFAULT_MIN_UPDATE_INTERVAL_MS}.
     *
     * @param minUpdateIntervalMs The minimum interval between playback speed changes, in
     *     milliseconds.
     * @return This builder, for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setMinUpdateIntervalMs(long minUpdateIntervalMs) {
      Assertions.checkArgument(minUpdateIntervalMs > 0);
      this.minUpdateIntervalMs = minUpdateIntervalMs;
      return this;
    }

    /**
     * Sets the proportional control factor used to adjust the playback speed.
     *
     * <p>The factor by which playback will be sped up is calculated as {@code 1.0 +
     * proportionalControlFactor x (currentLiveOffsetSec - targetLiveOffsetSec)}.
     *
     * <p>The default is {@link #DEFAULT_PROPORTIONAL_CONTROL_FACTOR}.
     *
     * @param proportionalControlFactor The proportional control factor used to adjust the playback
     *     speed.
     * @return This builder, for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setProportionalControlFactor(float proportionalControlFactor) {
      Assertions.checkArgument(proportionalControlFactor > 0);
      this.proportionalControlFactorUs = proportionalControlFactor / C.MICROS_PER_SECOND;
      return this;
    }

    /**
     * Sets the maximum difference between the current live offset and the target live offset, in
     * milliseconds, for which unit speed (1.0f) is used.
     *
     * <p>The default is {@link #DEFAULT_MAX_LIVE_OFFSET_ERROR_MS_FOR_UNIT_SPEED}.
     *
     * @param maxLiveOffsetErrorMsForUnitSpeed The maximum live offset error for which unit speed is
     *     used, in milliseconds.
     * @return This builder, for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setMaxLiveOffsetErrorMsForUnitSpeed(long maxLiveOffsetErrorMsForUnitSpeed) {
      Assertions.checkArgument(maxLiveOffsetErrorMsForUnitSpeed > 0);
      this.maxLiveOffsetErrorUsForUnitSpeed = Util.msToUs(maxLiveOffsetErrorMsForUnitSpeed);
      return this;
    }

    /**
     * Sets the increment applied to the target live offset each time the player is rebuffering, in
     * milliseconds.
     *
     * @param targetLiveOffsetIncrementOnRebufferMs The increment applied to the target live offset
     *     when the player is rebuffering, in milliseconds
     * @return This builder, for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setTargetLiveOffsetIncrementOnRebufferMs(
        long targetLiveOffsetIncrementOnRebufferMs) {
      Assertions.checkArgument(targetLiveOffsetIncrementOnRebufferMs >= 0);
      this.targetLiveOffsetIncrementOnRebufferUs =
          Util.msToUs(targetLiveOffsetIncrementOnRebufferMs);
      return this;
    }

    /**
     * Sets the smoothing factor when smoothing the minimum possible live offset that can be
     * achieved during playback.
     *
     * <p>The live playback speed control keeps track of the minimum possible live offset achievable
     * during playback to know whether it can reduce the current target live offset. The minimum
     * possible live offset is defined as {@code currentLiveOffset - bufferedDuration}. As the
     * minimum possible live offset is constantly changing, it is smoothed over recent samples by
     * applying exponential smoothing: {@code smoothedMinPossibleOffset = smoothingFactor x
     * smoothedMinPossibleOffset + (1-smoothingFactor) x currentMinPossibleOffset}.
     *
     * @param minPossibleLiveOffsetSmoothingFactor The smoothing factor. Must be &ge; 0 and &lt; 1.
     * @return This builder, for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setMinPossibleLiveOffsetSmoothingFactor(
        float minPossibleLiveOffsetSmoothingFactor) {
      Assertions.checkArgument(
          minPossibleLiveOffsetSmoothingFactor >= 0 && minPossibleLiveOffsetSmoothingFactor < 1f);
      this.minPossibleLiveOffsetSmoothingFactor = minPossibleLiveOffsetSmoothingFactor;
      return this;
    }

    /** Builds an instance. */
    public DefaultLivePlaybackSpeedControl build() {
      return new DefaultLivePlaybackSpeedControl(
          fallbackMinPlaybackSpeed,
          fallbackMaxPlaybackSpeed,
          minUpdateIntervalMs,
          proportionalControlFactorUs,
          maxLiveOffsetErrorUsForUnitSpeed,
          targetLiveOffsetIncrementOnRebufferUs,
          minPossibleLiveOffsetSmoothingFactor);
    }
  }

  private final float fallbackMinPlaybackSpeed;
  private final float fallbackMaxPlaybackSpeed;
  private final long minUpdateIntervalMs;
  private final float proportionalControlFactor;
  private final long maxLiveOffsetErrorUsForUnitSpeed;
  private final long targetLiveOffsetRebufferDeltaUs;
  private final float minPossibleLiveOffsetSmoothingFactor;

  private long mediaConfigurationTargetLiveOffsetUs;
  private long targetLiveOffsetOverrideUs;
  private long idealTargetLiveOffsetUs;
  private long minTargetLiveOffsetUs;
  private long maxTargetLiveOffsetUs;
  private long currentTargetLiveOffsetUs;

  private float maxPlaybackSpeed;
  private float minPlaybackSpeed;
  private float adjustedPlaybackSpeed;
  private long lastPlaybackSpeedUpdateMs;

  private long smoothedMinPossibleLiveOffsetUs;
  private long smoothedMinPossibleLiveOffsetDeviationUs;

  private DefaultLivePlaybackSpeedControl(
      float fallbackMinPlaybackSpeed,
      float fallbackMaxPlaybackSpeed,
      long minUpdateIntervalMs,
      float proportionalControlFactor,
      long maxLiveOffsetErrorUsForUnitSpeed,
      long targetLiveOffsetRebufferDeltaUs,
      float minPossibleLiveOffsetSmoothingFactor) {
    this.fallbackMinPlaybackSpeed = fallbackMinPlaybackSpeed;
    this.fallbackMaxPlaybackSpeed = fallbackMaxPlaybackSpeed;
    this.minUpdateIntervalMs = minUpdateIntervalMs;
    this.proportionalControlFactor = proportionalControlFactor;
    this.maxLiveOffsetErrorUsForUnitSpeed = maxLiveOffsetErrorUsForUnitSpeed;
    this.targetLiveOffsetRebufferDeltaUs = targetLiveOffsetRebufferDeltaUs;
    this.minPossibleLiveOffsetSmoothingFactor = minPossibleLiveOffsetSmoothingFactor;
    mediaConfigurationTargetLiveOffsetUs = C.TIME_UNSET;
    targetLiveOffsetOverrideUs = C.TIME_UNSET;
    minTargetLiveOffsetUs = C.TIME_UNSET;
    maxTargetLiveOffsetUs = C.TIME_UNSET;
    minPlaybackSpeed = fallbackMinPlaybackSpeed;
    maxPlaybackSpeed = fallbackMaxPlaybackSpeed;
    adjustedPlaybackSpeed = 1.0f;
    lastPlaybackSpeedUpdateMs = C.TIME_UNSET;
    idealTargetLiveOffsetUs = C.TIME_UNSET;
    currentTargetLiveOffsetUs = C.TIME_UNSET;
    smoothedMinPossibleLiveOffsetUs = C.TIME_UNSET;
    smoothedMinPossibleLiveOffsetDeviationUs = C.TIME_UNSET;
  }

  @Override
  public void setLiveConfiguration(LiveConfiguration liveConfiguration) {
    mediaConfigurationTargetLiveOffsetUs = Util.msToUs(liveConfiguration.targetOffsetMs);
    minTargetLiveOffsetUs = Util.msToUs(liveConfiguration.minOffsetMs);
    maxTargetLiveOffsetUs = Util.msToUs(liveConfiguration.maxOffsetMs);
    minPlaybackSpeed =
        liveConfiguration.minPlaybackSpeed != C.RATE_UNSET
            ? liveConfiguration.minPlaybackSpeed
            : fallbackMinPlaybackSpeed;
    maxPlaybackSpeed =
        liveConfiguration.maxPlaybackSpeed != C.RATE_UNSET
            ? liveConfiguration.maxPlaybackSpeed
            : fallbackMaxPlaybackSpeed;
    if (minPlaybackSpeed == 1f && maxPlaybackSpeed == 1f) {
      // Don't bother calculating adjustments if it's not possible to change the speed.
      mediaConfigurationTargetLiveOffsetUs = C.TIME_UNSET;
    }
    maybeResetTargetLiveOffsetUs();
  }

  @Override
  public void setTargetLiveOffsetOverrideUs(long liveOffsetUs) {
    targetLiveOffsetOverrideUs = liveOffsetUs;
    maybeResetTargetLiveOffsetUs();
  }

  @Override
  public void notifyRebuffer() {
    if (currentTargetLiveOffsetUs == C.TIME_UNSET) {
      return;
    }
    currentTargetLiveOffsetUs += targetLiveOffsetRebufferDeltaUs;
    if (maxTargetLiveOffsetUs != C.TIME_UNSET
        && currentTargetLiveOffsetUs > maxTargetLiveOffsetUs) {
      currentTargetLiveOffsetUs = maxTargetLiveOffsetUs;
    }
    lastPlaybackSpeedUpdateMs = C.TIME_UNSET;
  }

  @Override
  public float getAdjustedPlaybackSpeed(long liveOffsetUs, long bufferedDurationUs) {
    if (mediaConfigurationTargetLiveOffsetUs == C.TIME_UNSET) {
      return 1f;
    }

    updateSmoothedMinPossibleLiveOffsetUs(liveOffsetUs, bufferedDurationUs);

    if (lastPlaybackSpeedUpdateMs != C.TIME_UNSET
        && SystemClock.elapsedRealtime() - lastPlaybackSpeedUpdateMs < minUpdateIntervalMs) {
      return adjustedPlaybackSpeed;
    }
    lastPlaybackSpeedUpdateMs = SystemClock.elapsedRealtime();

    adjustTargetLiveOffsetUs(liveOffsetUs);
    long liveOffsetErrorUs = liveOffsetUs - currentTargetLiveOffsetUs;
    if (Math.abs(liveOffsetErrorUs) < maxLiveOffsetErrorUsForUnitSpeed) {
      adjustedPlaybackSpeed = 1f;
    } else {
      float calculatedSpeed = 1f + proportionalControlFactor * liveOffsetErrorUs;
      adjustedPlaybackSpeed =
          Util.constrainValue(calculatedSpeed, minPlaybackSpeed, maxPlaybackSpeed);
    }
    return adjustedPlaybackSpeed;
  }

  @Override
  public long getTargetLiveOffsetUs() {
    return currentTargetLiveOffsetUs;
  }

  private void maybeResetTargetLiveOffsetUs() {
    long idealOffsetUs = C.TIME_UNSET;
    if (mediaConfigurationTargetLiveOffsetUs != C.TIME_UNSET) {
      idealOffsetUs =
          targetLiveOffsetOverrideUs != C.TIME_UNSET
              ? targetLiveOffsetOverrideUs
              : mediaConfigurationTargetLiveOffsetUs;
      if (minTargetLiveOffsetUs != C.TIME_UNSET && idealOffsetUs < minTargetLiveOffsetUs) {
        idealOffsetUs = minTargetLiveOffsetUs;
      }
      if (maxTargetLiveOffsetUs != C.TIME_UNSET && idealOffsetUs > maxTargetLiveOffsetUs) {
        idealOffsetUs = maxTargetLiveOffsetUs;
      }
    }
    if (idealTargetLiveOffsetUs == idealOffsetUs) {
      return;
    }
    idealTargetLiveOffsetUs = idealOffsetUs;
    currentTargetLiveOffsetUs = idealOffsetUs;
    smoothedMinPossibleLiveOffsetUs = C.TIME_UNSET;
    smoothedMinPossibleLiveOffsetDeviationUs = C.TIME_UNSET;
    lastPlaybackSpeedUpdateMs = C.TIME_UNSET;
  }

  private void updateSmoothedMinPossibleLiveOffsetUs(long liveOffsetUs, long bufferedDurationUs) {
    long minPossibleLiveOffsetUs = liveOffsetUs - bufferedDurationUs;
    if (smoothedMinPossibleLiveOffsetUs == C.TIME_UNSET) {
      smoothedMinPossibleLiveOffsetUs = minPossibleLiveOffsetUs;
      smoothedMinPossibleLiveOffsetDeviationUs = 0;
    } else {
      // Use the maximum here to ensure we keep track of the upper bound of what is safely possible,
      // not the average.
      smoothedMinPossibleLiveOffsetUs =
          max(
              minPossibleLiveOffsetUs,
              smooth(
                  smoothedMinPossibleLiveOffsetUs,
                  minPossibleLiveOffsetUs,
                  minPossibleLiveOffsetSmoothingFactor));
      long minPossibleLiveOffsetDeviationUs =
          abs(minPossibleLiveOffsetUs - smoothedMinPossibleLiveOffsetUs);
      smoothedMinPossibleLiveOffsetDeviationUs =
          smooth(
              smoothedMinPossibleLiveOffsetDeviationUs,
              minPossibleLiveOffsetDeviationUs,
              minPossibleLiveOffsetSmoothingFactor);
    }
  }

  private void adjustTargetLiveOffsetUs(long liveOffsetUs) {
    // Stay in a safe distance (3 standard deviations = >99%) to the minimum possible live offset.
    long safeOffsetUs =
        smoothedMinPossibleLiveOffsetUs + 3 * smoothedMinPossibleLiveOffsetDeviationUs;
    if (currentTargetLiveOffsetUs > safeOffsetUs) {
      // There is room for decreasing the target offset towards the ideal or safe offset (whichever
      // is larger). We want to limit the decrease so that the playback speed delta we achieve is
      // the same as the maximum delta when slowing down towards the target.
      long minUpdateIntervalUs = Util.msToUs(minUpdateIntervalMs);
      long decrementToOffsetCurrentSpeedUs =
          (long) ((adjustedPlaybackSpeed - 1f) * minUpdateIntervalUs);
      long decrementToIncreaseSpeedUs = (long) ((maxPlaybackSpeed - 1f) * minUpdateIntervalUs);
      long maxDecrementUs = decrementToOffsetCurrentSpeedUs + decrementToIncreaseSpeedUs;
      currentTargetLiveOffsetUs =
          max(safeOffsetUs, idealTargetLiveOffsetUs, currentTargetLiveOffsetUs - maxDecrementUs);
    } else {
      // We'd like to reach a stable condition where the current live offset stays just below the
      // safe offset. But don't increase the target offset to more than what would allow us to slow
      // down gradually from the current offset.
      long offsetWhenSlowingDownNowUs =
          liveOffsetUs - (long) (max(0f, adjustedPlaybackSpeed - 1f) / proportionalControlFactor);
      currentTargetLiveOffsetUs =
          Util.constrainValue(offsetWhenSlowingDownNowUs, currentTargetLiveOffsetUs, safeOffsetUs);
      if (maxTargetLiveOffsetUs != C.TIME_UNSET
          && currentTargetLiveOffsetUs > maxTargetLiveOffsetUs) {
        currentTargetLiveOffsetUs = maxTargetLiveOffsetUs;
      }
    }
  }

  private static long smooth(long smoothedValue, long newValue, float smoothingFactor) {
    return (long) (smoothingFactor * smoothedValue + (1f - smoothingFactor) * newValue);
  }
}
