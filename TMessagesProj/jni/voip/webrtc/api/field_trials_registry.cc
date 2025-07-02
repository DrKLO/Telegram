/*
 *  Copyright 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/field_trials_registry.h"

#include <string>

#include "absl/algorithm/container.h"
#include "absl/strings/string_view.h"
#include "rtc_base/checks.h"
#include "rtc_base/containers/flat_set.h"
#include "rtc_base/logging.h"

namespace webrtc {

std::string FieldTrialsRegistry::Lookup(absl::string_view key) const {
#if WEBRTC_STRICT_FIELD_TRIALS == 1
  RTC_DCHECK(absl::c_linear_search(kRegisteredFieldTrials, key) ||
             test_keys_.contains(key))
      << key << " is not registered, see g3doc/field-trials.md.";
#elif WEBRTC_STRICT_FIELD_TRIALS == 2
  RTC_LOG_IF(LS_WARNING, !(absl::c_linear_search(kRegisteredFieldTrials, key) ||
                           test_keys_.contains(key)))
      << key << " is not registered, see g3doc/field-trials.md.";
#endif
  return GetValue(key);
}

}  // namespace webrtc
