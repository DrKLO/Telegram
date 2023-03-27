/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "rtc_base/experiments/field_trial_list.h"

#include "absl/strings/string_view.h"

namespace webrtc {

FieldTrialListBase::FieldTrialListBase(absl::string_view key)
    : FieldTrialParameterInterface(key),
      failed_(false),
      parse_got_called_(false) {}

bool FieldTrialListBase::Failed() const {
  return failed_;
}
bool FieldTrialListBase::Used() const {
  return parse_got_called_;
}

int FieldTrialListWrapper::Length() {
  return GetList()->Size();
}
bool FieldTrialListWrapper::Failed() {
  return GetList()->Failed();
}
bool FieldTrialListWrapper::Used() {
  return GetList()->Used();
}

bool FieldTrialStructListBase::Parse(absl::optional<std::string> str_value) {
  RTC_DCHECK_NOTREACHED();
  return true;
}

int FieldTrialStructListBase::ValidateAndGetLength() {
  int length = -1;
  for (std::unique_ptr<FieldTrialListWrapper>& list : sub_lists_) {
    if (list->Failed())
      return -1;
    else if (!list->Used())
      continue;
    else if (length == -1)
      length = list->Length();
    else if (length != list->Length())
      return -1;
  }

  return length;
}

}  // namespace webrtc
