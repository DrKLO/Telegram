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

/** BitrateAdjuster that tracks bitrate and framerate but does not adjust them. */
class BaseBitrateAdjuster implements BitrateAdjuster {
  protected int targetBitrateBps;
  protected double targetFramerateFps;

  @Override
  public void setTargets(int targetBitrateBps, double targetFramerateFps) {
    this.targetBitrateBps = targetBitrateBps;
    this.targetFramerateFps = targetFramerateFps;
  }

  @Override
  public void reportEncodedFrame(int size) {
    // No op.
  }

  @Override
  public int getAdjustedBitrateBps() {
    return targetBitrateBps;
  }

  @Override
  public double getAdjustedFramerateFps() {
    return targetFramerateFps;
  }
}
