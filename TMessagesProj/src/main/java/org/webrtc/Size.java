/*
 *  Copyright 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

/**
 * Class for representing size of an object. Very similar to android.util.Size but available on all
 * devices.
 */
public class Size {
  public int width;
  public int height;

  public Size(int width, int height) {
    this.width = width;
    this.height = height;
  }

  @Override
  public String toString() {
    return width + "x" + height;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof Size)) {
      return false;
    }
    final Size otherSize = (Size) other;
    return width == otherSize.width && height == otherSize.height;
  }

  @Override
  public int hashCode() {
    // Use prime close to 2^16 to avoid collisions for normal values less than 2^16.
    return 1 + 65537 * width + height;
  }
}
