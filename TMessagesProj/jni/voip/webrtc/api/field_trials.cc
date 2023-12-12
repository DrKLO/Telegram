/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/field_trials.h"

#include <atomic>

#include "rtc_base/checks.h"
#include "system_wrappers/include/field_trial.h"

namespace {

// This part is copied from system_wrappers/field_trial.cc.
webrtc::flat_map<std::string, std::string> InsertIntoMap(const std::string& s) {
  std::string::size_type field_start = 0;
  webrtc::flat_map<std::string, std::string> key_value_map;
  while (field_start < s.size()) {
    std::string::size_type separator_pos = s.find('/', field_start);
    RTC_CHECK_NE(separator_pos, std::string::npos)
        << "Missing separator '/' after field trial key.";
    RTC_CHECK_GT(separator_pos, field_start)
        << "Field trial key cannot be empty.";
    std::string key = s.substr(field_start, separator_pos - field_start);
    field_start = separator_pos + 1;

    RTC_CHECK_LT(field_start, s.size())
        << "Missing value after field trial key. String ended.";
    separator_pos = s.find('/', field_start);
    RTC_CHECK_NE(separator_pos, std::string::npos)
        << "Missing terminating '/' in field trial string.";
    RTC_CHECK_GT(separator_pos, field_start)
        << "Field trial value cannot be empty.";
    std::string value = s.substr(field_start, separator_pos - field_start);
    field_start = separator_pos + 1;

    // If a key is specified multiple times, only the value linked to the first
    // key is stored. note: This will crash in debug build when calling
    // InitFieldTrialsFromString().
    key_value_map.emplace(key, value);
  }
  // This check is technically redundant due to earlier checks.
  // We nevertheless keep the check to make it clear that the entire
  // string has been processed, and without indexing past the end.
  RTC_CHECK_EQ(field_start, s.size());

  return key_value_map;
}

// Makes sure that only one instance is created, since the usage
// of global string makes behaviour unpredicatable otherwise.
// TODO(bugs.webrtc.org/10335): Remove once global string is gone.
std::atomic<bool> instance_created_{false};

}  // namespace

namespace webrtc {

FieldTrials::FieldTrials(const std::string& s)
    : uses_global_(true),
      field_trial_string_(s),
      previous_field_trial_string_(webrtc::field_trial::GetFieldTrialString()),
      key_value_map_(InsertIntoMap(s)) {
  // TODO(bugs.webrtc.org/10335): Remove the global string!
  field_trial::InitFieldTrialsFromString(field_trial_string_.c_str());
  RTC_CHECK(!instance_created_.exchange(true))
      << "Only one instance may be instanciated at any given time!";
}

std::unique_ptr<FieldTrials> FieldTrials::CreateNoGlobal(const std::string& s) {
  return std::unique_ptr<FieldTrials>(new FieldTrials(s, true));
}

FieldTrials::FieldTrials(const std::string& s, bool)
    : uses_global_(false),
      previous_field_trial_string_(nullptr),
      key_value_map_(InsertIntoMap(s)) {}

FieldTrials::~FieldTrials() {
  // TODO(bugs.webrtc.org/10335): Remove the global string!
  if (uses_global_) {
    field_trial::InitFieldTrialsFromString(previous_field_trial_string_);
    RTC_CHECK(instance_created_.exchange(false));
  }
}

std::string FieldTrials::GetValue(absl::string_view key) const {
  auto it = key_value_map_.find(std::string(key));
  if (it != key_value_map_.end())
    return it->second;

  // Check the global string so that programs using
  // a mix between FieldTrials and the global string continue to work
  // TODO(bugs.webrtc.org/10335): Remove the global string!
  if (uses_global_) {
    return field_trial::FindFullName(std::string(key));
  }
  return "";
}

}  // namespace webrtc
