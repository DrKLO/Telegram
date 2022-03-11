/*
 *  Copyright 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/transceiver_list.h"

#include <string>

#include "rtc_base/checks.h"

namespace webrtc {

void TransceiverStableState::set_newly_created() {
  RTC_DCHECK(!has_m_section_);
  newly_created_ = true;
}

void TransceiverStableState::SetMSectionIfUnset(
    absl::optional<std::string> mid,
    absl::optional<size_t> mline_index) {
  if (!has_m_section_) {
    mid_ = mid;
    mline_index_ = mline_index;
    has_m_section_ = true;
  }
}

void TransceiverStableState::SetRemoteStreamIdsIfUnset(
    const std::vector<std::string>& ids) {
  if (!remote_stream_ids_.has_value()) {
    remote_stream_ids_ = ids;
  }
}

void TransceiverStableState::SetInitSendEncodings(
    const std::vector<RtpEncodingParameters>& encodings) {
  init_send_encodings_ = encodings;
}

std::vector<RtpTransceiver*> TransceiverList::ListInternal() const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  std::vector<RtpTransceiver*> internals;
  for (auto transceiver : transceivers_) {
    internals.push_back(transceiver->internal());
  }
  return internals;
}

RtpTransceiverProxyRefPtr TransceiverList::FindBySender(
    rtc::scoped_refptr<RtpSenderInterface> sender) const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  for (auto transceiver : transceivers_) {
    if (transceiver->sender() == sender) {
      return transceiver;
    }
  }
  return nullptr;
}

RtpTransceiverProxyRefPtr TransceiverList::FindByMid(
    const std::string& mid) const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  for (auto transceiver : transceivers_) {
    if (transceiver->mid() == mid) {
      return transceiver;
    }
  }
  return nullptr;
}

RtpTransceiverProxyRefPtr TransceiverList::FindByMLineIndex(
    size_t mline_index) const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  for (auto transceiver : transceivers_) {
    if (transceiver->internal()->mline_index() == mline_index) {
      return transceiver;
    }
  }
  return nullptr;
}

}  // namespace webrtc
