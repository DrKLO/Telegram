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

/** Object that adjusts the bitrate of a hardware codec. */
interface BitrateAdjuster {
  /**
   * Sets the target bitrate in bits per second and framerate in frames per second.
   */
  void setTargets(int targetBitrateBps, double targetFramerateFps);

  /**
   * Should be used to report the size of an encoded frame to the bitrate adjuster. Use
   * getAdjustedBitrateBps to get the updated bitrate after calling this method.
   */
  void reportEncodedFrame(int size);

  /** Gets the current bitrate. */
  int getAdjustedBitrateBps();

  /** Gets the current framerate. */
  double getAdjustedFramerateFps();
}
