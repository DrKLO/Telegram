/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/ns/signal_model.h"

namespace webrtc {

SignalModel::SignalModel() {
  constexpr float kSfFeatureThr = 0.5f;

  lrt = kLtrFeatureThr;
  spectral_flatness = kSfFeatureThr;
  spectral_diff = kSfFeatureThr;
  avg_log_lrt.fill(kLtrFeatureThr);
}

}  // namespace webrtc
