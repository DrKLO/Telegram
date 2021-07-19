/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef API_TRANSPORT_FIELD_TRIAL_BASED_CONFIG_H_
#define API_TRANSPORT_FIELD_TRIAL_BASED_CONFIG_H_

#include <string>

#include "absl/strings/string_view.h"
#include "api/transport/webrtc_key_value_config.h"

namespace webrtc {
// Implementation using the field trial API fo the key value lookup.
class FieldTrialBasedConfig : public WebRtcKeyValueConfig {
 public:
  std::string Lookup(absl::string_view key) const override;
};
}  // namespace webrtc

#endif  // API_TRANSPORT_FIELD_TRIAL_BASED_CONFIG_H_
