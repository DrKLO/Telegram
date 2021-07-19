/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/inter_frame_delay.h"

namespace webrtc {

VCMInterFrameDelay::VCMInterFrameDelay(int64_t currentWallClock) {
  Reset(currentWallClock);
}

// Resets the delay estimate.
void VCMInterFrameDelay::Reset(int64_t currentWallClock) {
  _zeroWallClock = currentWallClock;
  _wrapArounds = 0;
  _prevWallClock = 0;
  _prevTimestamp = 0;
  _dTS = 0;
}

// Calculates the delay of a frame with the given timestamp.
// This method is called when the frame is complete.
bool VCMInterFrameDelay::CalculateDelay(uint32_t timestamp,
                                        int64_t* delay,
                                        int64_t currentWallClock) {
  if (_prevWallClock == 0) {
    // First set of data, initialization, wait for next frame.
    _prevWallClock = currentWallClock;
    _prevTimestamp = timestamp;
    *delay = 0;
    return true;
  }

  int32_t prevWrapArounds = _wrapArounds;
  CheckForWrapArounds(timestamp);

  // This will be -1 for backward wrap arounds and +1 for forward wrap arounds.
  int32_t wrapAroundsSincePrev = _wrapArounds - prevWrapArounds;

  // Account for reordering in jitter variance estimate in the future?
  // Note that this also captures incomplete frames which are grabbed for
  // decoding after a later frame has been complete, i.e. real packet losses.
  if ((wrapAroundsSincePrev == 0 && timestamp < _prevTimestamp) ||
      wrapAroundsSincePrev < 0) {
    *delay = 0;
    return false;
  }

  // Compute the compensated timestamp difference and convert it to ms and round
  // it to closest integer.
  _dTS = static_cast<int64_t>(
      (timestamp + wrapAroundsSincePrev * (static_cast<int64_t>(1) << 32) -
       _prevTimestamp) /
          90.0 +
      0.5);

  // frameDelay is the difference of dT and dTS -- i.e. the difference of the
  // wall clock time difference and the timestamp difference between two
  // following frames.
  *delay = static_cast<int64_t>(currentWallClock - _prevWallClock - _dTS);

  _prevTimestamp = timestamp;
  _prevWallClock = currentWallClock;

  return true;
}

// Investigates if the timestamp clock has overflowed since the last timestamp
// and keeps track of the number of wrap arounds since reset.
void VCMInterFrameDelay::CheckForWrapArounds(uint32_t timestamp) {
  if (timestamp < _prevTimestamp) {
    // This difference will probably be less than -2^31 if we have had a wrap
    // around (e.g. timestamp = 1, _prevTimestamp = 2^32 - 1). Since it is cast
    // to a int32_t, it should be positive.
    if (static_cast<int32_t>(timestamp - _prevTimestamp) > 0) {
      // Forward wrap around.
      _wrapArounds++;
    }
    // This difference will probably be less than -2^31 if we have had a
    // backward wrap around. Since it is cast to a int32_t, it should be
    // positive.
  } else if (static_cast<int32_t>(_prevTimestamp - timestamp) > 0) {
    // Backward wrap around.
    _wrapArounds--;
  }
}
}  // namespace webrtc
