/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/engine/unhandled_packets_buffer.h"

#include "absl/algorithm/container.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"

namespace cricket {

UnhandledPacketsBuffer::UnhandledPacketsBuffer() {
  buffer_.reserve(kMaxStashedPackets);
}

UnhandledPacketsBuffer::~UnhandledPacketsBuffer() = default;

// Store packet in buffer.
void UnhandledPacketsBuffer::AddPacket(uint32_t ssrc,
                                       int64_t packet_time_us,
                                       rtc::CopyOnWriteBuffer packet) {
  if (buffer_.size() < kMaxStashedPackets) {
    buffer_.push_back({ssrc, packet_time_us, packet});
  } else {
    RTC_DCHECK_LT(insert_pos_, kMaxStashedPackets);
    buffer_[insert_pos_] = {ssrc, packet_time_us, packet};
  }
  insert_pos_ = (insert_pos_ + 1) % kMaxStashedPackets;
}

// Backfill `consumer` with all stored packet related `ssrcs`.
void UnhandledPacketsBuffer::BackfillPackets(
    rtc::ArrayView<const uint32_t> ssrcs,
    std::function<void(uint32_t, int64_t, rtc::CopyOnWriteBuffer)> consumer) {
  size_t start;
  if (buffer_.size() < kMaxStashedPackets) {
    start = 0;
  } else {
    start = insert_pos_;
  }

  std::vector<PacketWithMetadata> remaining;
  remaining.reserve(kMaxStashedPackets);
  for (size_t i = 0; i < buffer_.size(); ++i) {
    const size_t pos = (i + start) % kMaxStashedPackets;

    // One or maybe 2 ssrcs is expected => loop array instead of more elaborate
    // scheme.
    const uint32_t ssrc = buffer_[pos].ssrc;
    if (absl::c_linear_search(ssrcs, ssrc)) {
      consumer(ssrc, buffer_[pos].packet_time_us, buffer_[pos].packet);
    } else {
      remaining.push_back(buffer_[pos]);
    }
  }

  insert_pos_ = 0;  // insert_pos is only used when buffer is full.
  buffer_.swap(remaining);
}

}  // namespace cricket
