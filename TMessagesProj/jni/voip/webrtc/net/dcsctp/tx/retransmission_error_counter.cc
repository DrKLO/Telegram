/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/tx/retransmission_error_counter.h"

#include "absl/strings/string_view.h"
#include "rtc_base/logging.h"

namespace dcsctp {
bool RetransmissionErrorCounter::Increment(absl::string_view reason) {
  ++counter_;
  if (limit_.has_value() && counter_ > limit_.value()) {
    RTC_DLOG(LS_INFO) << log_prefix_ << reason
                      << ", too many retransmissions, counter=" << counter_;
    return false;
  }

  RTC_DLOG(LS_VERBOSE) << log_prefix_ << reason << ", new counter=" << counter_
                       << ", max=" << limit_.value_or(-1);
  return true;
}

void RetransmissionErrorCounter::Clear() {
  if (counter_ > 0) {
    RTC_DLOG(LS_VERBOSE) << log_prefix_
                         << "recovered from counter=" << counter_;
    counter_ = 0;
  }
}

}  // namespace dcsctp
