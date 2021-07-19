/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/transport/field_trial_based_config.h"

#include "system_wrappers/include/field_trial.h"

namespace webrtc {
std::string FieldTrialBasedConfig::Lookup(absl::string_view key) const {
  return webrtc::field_trial::FindFullName(std::string(key));
}
}  // namespace webrtc
