/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

/**
 * BitrateAdjuster that tracks the bandwidth produced by an encoder and dynamically adjusts the
 * bitrate.  Used for hardware codecs that pay attention to framerate but still deviate from the
 * target bitrate by unacceptable margins.
 */
class DynamicBitrateAdjuster extends BaseBitrateAdjuster {
  // Change the bitrate at most once every three seconds.
  private static final double BITRATE_ADJUSTMENT_SEC = 3.0;
  // Maximum bitrate adjustment scale - no more than 4 times.
  private static final double BITRATE_ADJUSTMENT_MAX_SCALE = 4;
  // Amount of adjustment steps to reach maximum scale.
  private static final int BITRATE_ADJUSTMENT_STEPS = 20;

  private static final double BITS_PER_BYTE = 8.0;

  // How far the codec has deviated above (or below) the target bitrate (tracked in bytes).
  private double deviationBytes;
  private double timeSinceLastAdjustmentMs;
  private int bitrateAdjustmentScaleExp;

  @Override
  public void setTargets(int targetBitrateBps, int targetFps) {
    if (this.targetBitrateBps > 0 && targetBitrateBps < this.targetBitrateBps) {
      // Rescale the accumulator level if the accumulator max decreases
      deviationBytes = deviationBytes * targetBitrateBps / this.targetBitrateBps;
    }
    super.setTargets(targetBitrateBps, targetFps);
  }

  @Override
  public void reportEncodedFrame(int size) {
    if (targetFps == 0) {
      return;
    }

    // Accumulate the difference between actual and expected frame sizes.
    double expectedBytesPerFrame = (targetBitrateBps / BITS_PER_BYTE) / targetFps;
    deviationBytes += (size - expectedBytesPerFrame);
    timeSinceLastAdjustmentMs += 1000.0 / targetFps;

    // Adjust the bitrate when the encoder accumulates one second's worth of data in excess or
    // shortfall of the target.
    double deviationThresholdBytes = targetBitrateBps / BITS_PER_BYTE;

    // Cap the deviation, i.e., don't let it grow beyond some level to avoid using too old data for
    // bitrate adjustment.  This also prevents taking more than 3 "steps" in a given 3-second cycle.
    double deviationCap = BITRATE_ADJUSTMENT_SEC * deviationThresholdBytes;
    deviationBytes = Math.min(deviationBytes, deviationCap);
    deviationBytes = Math.max(deviationBytes, -deviationCap);

    // Do bitrate adjustment every 3 seconds if actual encoder bitrate deviates too much
    // from the target value.
    if (timeSinceLastAdjustmentMs <= 1000 * BITRATE_ADJUSTMENT_SEC) {
      return;
    }

    if (deviationBytes > deviationThresholdBytes) {
      // Encoder generates too high bitrate - need to reduce the scale.
      int bitrateAdjustmentInc = (int) (deviationBytes / deviationThresholdBytes + 0.5);
      bitrateAdjustmentScaleExp -= bitrateAdjustmentInc;
      // Don't let the adjustment scale drop below -BITRATE_ADJUSTMENT_STEPS.
      // This sets a minimum exponent of -1 (bitrateAdjustmentScaleExp / BITRATE_ADJUSTMENT_STEPS).
      bitrateAdjustmentScaleExp = Math.max(bitrateAdjustmentScaleExp, -BITRATE_ADJUSTMENT_STEPS);
      deviationBytes = deviationThresholdBytes;
    } else if (deviationBytes < -deviationThresholdBytes) {
      // Encoder generates too low bitrate - need to increase the scale.
      int bitrateAdjustmentInc = (int) (-deviationBytes / deviationThresholdBytes + 0.5);
      bitrateAdjustmentScaleExp += bitrateAdjustmentInc;
      // Don't let the adjustment scale exceed BITRATE_ADJUSTMENT_STEPS.
      // This sets a maximum exponent of 1 (bitrateAdjustmentScaleExp / BITRATE_ADJUSTMENT_STEPS).
      bitrateAdjustmentScaleExp = Math.min(bitrateAdjustmentScaleExp, BITRATE_ADJUSTMENT_STEPS);
      deviationBytes = -deviationThresholdBytes;
    }
    timeSinceLastAdjustmentMs = 0;
  }

  private double getBitrateAdjustmentScale() {
    return Math.pow(BITRATE_ADJUSTMENT_MAX_SCALE,
        (double) bitrateAdjustmentScaleExp / BITRATE_ADJUSTMENT_STEPS);
  }

  @Override
  public int getAdjustedBitrateBps() {
    return (int) (targetBitrateBps * getBitrateAdjustmentScale());
  }
}
