// Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the LICENSE file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS.  All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
//

#include "system_wrappers/include/field_trial.h"

#include <stddef.h>
#include <string>

// Simple field trial implementation, which allows client to
// specify desired flags in InitFieldTrialsFromString.
namespace webrtc {
namespace field_trial {

static const char* trials_init_string = NULL;

#ifndef WEBRTC_EXCLUDE_FIELD_TRIAL_DEFAULT
std::string FindFullName(const std::string& name) {
  if (trials_init_string == NULL)
    return std::string();

  std::string trials_string(trials_init_string);
  if (trials_string.empty())
    return std::string();

  static const char kPersistentStringSeparator = '/';
  size_t next_item = 0;
  while (next_item < trials_string.length()) {
    // Find next name/value pair in field trial configuration string.
    size_t field_name_end =
        trials_string.find(kPersistentStringSeparator, next_item);
    if (field_name_end == trials_string.npos || field_name_end == next_item)
      break;
    size_t field_value_end =
        trials_string.find(kPersistentStringSeparator, field_name_end + 1);
    if (field_value_end == trials_string.npos ||
        field_value_end == field_name_end + 1)
      break;
    std::string field_name(trials_string, next_item,
                           field_name_end - next_item);
    std::string field_value(trials_string, field_name_end + 1,
                            field_value_end - field_name_end - 1);
    next_item = field_value_end + 1;

    if (name == field_name)
      return field_value;
  }
  return std::string();
}
#endif  // WEBRTC_EXCLUDE_FIELD_TRIAL_DEFAULT

// Optionally initialize field trial from a string.
void InitFieldTrialsFromString(const char* trials_string) {
  trials_init_string = trials_string;
}

const char* GetFieldTrialString() {
  return trials_init_string;
}

}  // namespace field_trial
}  // namespace webrtc
