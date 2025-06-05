/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/data_channel_interface.h"

#include "rtc_base/checks.h"

namespace webrtc {

bool DataChannelInterface::ordered() const {
  return false;
}

uint16_t DataChannelInterface::maxRetransmitTime() const {
  return 0;
}

uint16_t DataChannelInterface::maxRetransmits() const {
  return 0;
}

absl::optional<int> DataChannelInterface::maxRetransmitsOpt() const {
  return absl::nullopt;
}

absl::optional<int> DataChannelInterface::maxPacketLifeTime() const {
  return absl::nullopt;
}

std::string DataChannelInterface::protocol() const {
  return std::string();
}

bool DataChannelInterface::negotiated() const {
  return false;
}

uint64_t DataChannelInterface::MaxSendQueueSize() {
  return 16 * 1024 * 1024;  // 16 MiB
}

// TODO(tommi): Remove method once downstream implementations have been removed.
bool DataChannelInterface::Send(const DataBuffer& buffer) {
  RTC_DCHECK_NOTREACHED();
  return false;
}

// TODO(tommi): Remove implementation once method is pure virtual.
void DataChannelInterface::SendAsync(
    DataBuffer buffer,
    absl::AnyInvocable<void(RTCError) &&> on_complete) {
  RTC_DCHECK_NOTREACHED();
}

}  // namespace webrtc
