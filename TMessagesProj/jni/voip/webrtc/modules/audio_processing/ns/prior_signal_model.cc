/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/ns/prior_signal_model.h"

namespace webrtc {

PriorSignalModel::PriorSignalModel(float lrt_initial_value)
    : lrt(lrt_initial_value) {}

}  // namespace webrtc
