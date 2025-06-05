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

#include <map>
#include <string>
#include <utility>

#include "absl/algorithm/container.h"
#include "absl/strings/string_view.h"
#include "experiments/registered_field_trials.h"
#include "rtc_base/checks.h"
#include "rtc_base/containers/flat_set.h"
#include "rtc_base/logging.h"
#include "rtc_base/string_encode.h"

// Simple field trial implementation, which allows client to
// specify desired flags in InitFieldTrialsFromString.
namespace webrtc {
namespace field_trial {

static const char* trials_init_string = NULL;

namespace {

constexpr char kPersistentStringSeparator = '/';

flat_set<std::string>& TestKeys() {
  static auto* test_keys = new flat_set<std::string>();
  return *test_keys;
}

// Validates the given field trial string.
//  E.g.:
//    "WebRTC-experimentFoo/Enabled/WebRTC-experimentBar/Enabled100kbps/"
//    Assigns the process to group "Enabled" on WebRTCExperimentFoo trial
//    and to group "Enabled100kbps" on WebRTCExperimentBar.
//
//  E.g. invalid config:
//    "WebRTC-experiment1/Enabled"  (note missing / separator at the end).
bool FieldTrialsStringIsValidInternal(const absl::string_view trials) {
  if (trials.empty())
    return true;

  size_t next_item = 0;
  std::map<absl::string_view, absl::string_view> field_trials;
  while (next_item < trials.length()) {
    size_t name_end = trials.find(kPersistentStringSeparator, next_item);
    if (name_end == trials.npos || next_item == name_end)
      return false;
    size_t group_name_end =
        trials.find(kPersistentStringSeparator, name_end + 1);
    if (group_name_end == trials.npos || name_end + 1 == group_name_end)
      return false;
    absl::string_view name = trials.substr(next_item, name_end - next_item);
    absl::string_view group_name =
        trials.substr(name_end + 1, group_name_end - name_end - 1);

    next_item = group_name_end + 1;

    // Fail if duplicate with different group name.
    if (field_trials.find(name) != field_trials.end() &&
        field_trials.find(name)->second != group_name) {
      return false;
    }

    field_trials[name] = group_name;
  }

  return true;
}

}  // namespace

bool FieldTrialsStringIsValid(absl::string_view trials_string) {
  return FieldTrialsStringIsValidInternal(trials_string);
}

void InsertOrReplaceFieldTrialStringsInMap(
    std::map<std::string, std::string>* fieldtrial_map,
    const absl::string_view trials_string) {
  if (FieldTrialsStringIsValidInternal(trials_string)) {
    std::vector<absl::string_view> tokens = rtc::split(trials_string, '/');
    // Skip last token which is empty due to trailing '/'.
    for (size_t idx = 0; idx < tokens.size() - 1; idx += 2) {
      (*fieldtrial_map)[std::string(tokens[idx])] =
          std::string(tokens[idx + 1]);
    }
  } else {
    RTC_DCHECK_NOTREACHED() << "Invalid field trials string:" << trials_string;
  }
}

std::string MergeFieldTrialsStrings(absl::string_view first,
                                    absl::string_view second) {
  std::map<std::string, std::string> fieldtrial_map;
  InsertOrReplaceFieldTrialStringsInMap(&fieldtrial_map, first);
  InsertOrReplaceFieldTrialStringsInMap(&fieldtrial_map, second);

  // Merge into fieldtrial string.
  std::string merged = "";
  for (auto const& fieldtrial : fieldtrial_map) {
    merged += fieldtrial.first + '/' + fieldtrial.second + '/';
  }
  return merged;
}

#ifndef WEBRTC_EXCLUDE_FIELD_TRIAL_DEFAULT
std::string FindFullName(absl::string_view name) {
#if WEBRTC_STRICT_FIELD_TRIALS == 1
  RTC_DCHECK(absl::c_linear_search(kRegisteredFieldTrials, name) ||
             TestKeys().contains(name))
      << name << " is not registered, see g3doc/field-trials.md.";
#elif WEBRTC_STRICT_FIELD_TRIALS == 2
  RTC_LOG_IF(LS_WARNING,
             !(absl::c_linear_search(kRegisteredFieldTrials, name) ||
               TestKeys().contains(name)))
      << name << " is not registered, see g3doc/field-trials.md.";
#endif

  if (trials_init_string == NULL)
    return std::string();

  absl::string_view trials_string(trials_init_string);
  if (trials_string.empty())
    return std::string();

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
    absl::string_view field_name =
        trials_string.substr(next_item, field_name_end - next_item);
    absl::string_view field_value = trials_string.substr(
        field_name_end + 1, field_value_end - field_name_end - 1);
    next_item = field_value_end + 1;

    if (name == field_name)
      return std::string(field_value);
  }
  return std::string();
}
#endif  // WEBRTC_EXCLUDE_FIELD_TRIAL_DEFAULT

// Optionally initialize field trial from a string.
void InitFieldTrialsFromString(const char* trials_string) {
  RTC_LOG(LS_INFO) << "Setting field trial string:" << trials_string;
  if (trials_string) {
    RTC_DCHECK(FieldTrialsStringIsValidInternal(trials_string))
        << "Invalid field trials string:" << trials_string;
  };
  trials_init_string = trials_string;
}

const char* GetFieldTrialString() {
  return trials_init_string;
}

FieldTrialsAllowedInScopeForTesting::FieldTrialsAllowedInScopeForTesting(
    flat_set<std::string> keys) {
  TestKeys() = std::move(keys);
}

FieldTrialsAllowedInScopeForTesting::~FieldTrialsAllowedInScopeForTesting() {
  TestKeys().clear();
}

}  // namespace field_trial
}  // namespace webrtc
