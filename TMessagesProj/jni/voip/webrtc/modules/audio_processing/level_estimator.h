/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_LEVEL_ESTIMATOR_H_
#define MODULES_AUDIO_PROCESSING_LEVEL_ESTIMATOR_H_

#include "modules/audio_processing/audio_buffer.h"
#include "modules/audio_processing/rms_level.h"

namespace webrtc {

// An estimation component used to retrieve level metrics.
class LevelEstimator {
 public:
  LevelEstimator();
  ~LevelEstimator();

  LevelEstimator(LevelEstimator&) = delete;
  LevelEstimator& operator=(LevelEstimator&) = delete;

  void ProcessStream(const AudioBuffer& audio);

  // Returns the root mean square (RMS) level in dBFs (decibels from digital
  // full-scale), or alternately dBov. It is computed over all primary stream
  // frames since the last call to RMS(). The returned value is positive but
  // should be interpreted as negative. It is constrained to [0, 127].
  //
  // The computation follows: https://tools.ietf.org/html/rfc6465
  // with the intent that it can provide the RTP audio level indication.
  //
  // Frames passed to ProcessStream() with an |_energy| of zero are considered
  // to have been muted. The RMS of the frame will be interpreted as -127.
  int RMS() { return rms_.Average(); }

 private:
  RmsLevel rms_;
};
}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_LEVEL_ESTIMATOR_H_
