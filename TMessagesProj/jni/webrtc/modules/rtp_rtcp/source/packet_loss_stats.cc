/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/packet_loss_stats.h"

#include <cstdint>
#include <iterator>
#include <vector>

#include "rtc_base/checks.h"

// After this many packets are added, adding additional packets will cause the
// oldest packets to be pruned from the buffer.
static const int kBufferSize = 100;

namespace webrtc {

PacketLossStats::PacketLossStats()
    : single_loss_historic_count_(0),
      multiple_loss_historic_event_count_(0),
      multiple_loss_historic_packet_count_(0) {}

PacketLossStats::~PacketLossStats() = default;

void PacketLossStats::AddLostPacket(uint16_t sequence_number) {
  // Detect sequence number wrap around.
  if (!lost_packets_buffer_.empty() &&
      static_cast<int>(*(lost_packets_buffer_.rbegin())) - sequence_number >
          0x8000) {
    // The buffer contains large numbers and this is a small number.
    lost_packets_wrapped_buffer_.insert(sequence_number);
  } else {
    lost_packets_buffer_.insert(sequence_number);
  }
  if (lost_packets_wrapped_buffer_.size() + lost_packets_buffer_.size() >
          kBufferSize ||
      (!lost_packets_wrapped_buffer_.empty() &&
       *(lost_packets_wrapped_buffer_.rbegin()) > 0x4000)) {
    PruneBuffer();
  }
}

int PacketLossStats::GetSingleLossCount() const {
  int single_loss_count, unused1, unused2;
  ComputeLossCounts(&single_loss_count, &unused1, &unused2);
  return single_loss_count;
}

int PacketLossStats::GetMultipleLossEventCount() const {
  int event_count, unused1, unused2;
  ComputeLossCounts(&unused1, &event_count, &unused2);
  return event_count;
}

int PacketLossStats::GetMultipleLossPacketCount() const {
  int packet_count, unused1, unused2;
  ComputeLossCounts(&unused1, &unused2, &packet_count);
  return packet_count;
}

void PacketLossStats::ComputeLossCounts(
    int* out_single_loss_count,
    int* out_multiple_loss_event_count,
    int* out_multiple_loss_packet_count) const {
  *out_single_loss_count = single_loss_historic_count_;
  *out_multiple_loss_event_count = multiple_loss_historic_event_count_;
  *out_multiple_loss_packet_count = multiple_loss_historic_packet_count_;
  if (lost_packets_buffer_.empty()) {
    RTC_DCHECK(lost_packets_wrapped_buffer_.empty());
    return;
  }
  uint16_t last_num = 0;
  int sequential_count = 0;
  std::vector<const std::set<uint16_t>*> buffers;
  buffers.push_back(&lost_packets_buffer_);
  buffers.push_back(&lost_packets_wrapped_buffer_);
  for (const auto* buffer : buffers) {
    for (auto it = buffer->begin(); it != buffer->end(); ++it) {
      uint16_t current_num = *it;
      if (sequential_count > 0 && current_num != ((last_num + 1) & 0xFFFF)) {
        if (sequential_count == 1) {
          (*out_single_loss_count)++;
        } else {
          (*out_multiple_loss_event_count)++;
          *out_multiple_loss_packet_count += sequential_count;
        }
        sequential_count = 0;
      }
      sequential_count++;
      last_num = current_num;
    }
  }
  if (sequential_count == 1) {
    (*out_single_loss_count)++;
  } else if (sequential_count > 1) {
    (*out_multiple_loss_event_count)++;
    *out_multiple_loss_packet_count += sequential_count;
  }
}

void PacketLossStats::PruneBuffer() {
  // Remove the oldest lost packet and any contiguous packets and move them
  // into the historic counts.
  auto it = lost_packets_buffer_.begin();
  uint16_t last_removed = 0;
  int remove_count = 0;
  // Count adjacent packets and continue counting if it is wrap around by
  // swapping in the wrapped buffer and letting our value wrap as well.
  while (remove_count == 0 || (!lost_packets_buffer_.empty() &&
                               *it == ((last_removed + 1) & 0xFFFF))) {
    last_removed = *it;
    remove_count++;
    auto to_erase = it++;
    lost_packets_buffer_.erase(to_erase);
    if (lost_packets_buffer_.empty()) {
      lost_packets_buffer_.swap(lost_packets_wrapped_buffer_);
      it = lost_packets_buffer_.begin();
    }
  }
  if (remove_count > 1) {
    multiple_loss_historic_event_count_++;
    multiple_loss_historic_packet_count_ += remove_count;
  } else {
    single_loss_historic_count_++;
  }
  // Continue pruning if the wrapped buffer is beyond a threshold and there are
  // things left in the pre-wrapped buffer.
  if (!lost_packets_wrapped_buffer_.empty() &&
      *(lost_packets_wrapped_buffer_.rbegin()) > 0x4000) {
    PruneBuffer();
  }
}

}  // namespace webrtc
