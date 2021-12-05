/*
 *  Copyright 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/usage_pattern.h"

#include "api/peer_connection_interface.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {

void UsagePattern::NoteUsageEvent(UsageEvent event) {
  usage_event_accumulator_ |= static_cast<int>(event);
}

void UsagePattern::ReportUsagePattern(PeerConnectionObserver* observer) const {
  RTC_DLOG(LS_INFO) << "Usage signature is " << usage_event_accumulator_;
  RTC_HISTOGRAM_ENUMERATION_SPARSE("WebRTC.PeerConnection.UsagePattern",
                                   usage_event_accumulator_,
                                   static_cast<int>(UsageEvent::MAX_VALUE));
  const int bad_bits =
      static_cast<int>(UsageEvent::SET_LOCAL_DESCRIPTION_SUCCEEDED) |
      static_cast<int>(UsageEvent::CANDIDATE_COLLECTED);
  const int good_bits =
      static_cast<int>(UsageEvent::SET_REMOTE_DESCRIPTION_SUCCEEDED) |
      static_cast<int>(UsageEvent::REMOTE_CANDIDATE_ADDED) |
      static_cast<int>(UsageEvent::ICE_STATE_CONNECTED);
  if ((usage_event_accumulator_ & bad_bits) == bad_bits &&
      (usage_event_accumulator_ & good_bits) == 0) {
    // If called after close(), we can't report, because observer may have
    // been deallocated, and therefore pointer is null. Write to log instead.
    if (observer) {
      observer->OnInterestingUsage(usage_event_accumulator_);
    } else {
      RTC_LOG(LS_INFO) << "Interesting usage signature "
                       << usage_event_accumulator_
                       << " observed after observer shutdown";
    }
  }
}

}  // namespace webrtc
