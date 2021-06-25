/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/remote_bitrate_estimator/packet_arrival_map.h"

#include <algorithm>

#include "rtc_base/numerics/safe_minmax.h"

namespace webrtc {

constexpr size_t PacketArrivalTimeMap::kMaxNumberOfPackets;

void PacketArrivalTimeMap::AddPacket(int64_t sequence_number,
                                     int64_t arrival_time_ms) {
  if (!has_seen_packet_) {
    // First packet.
    has_seen_packet_ = true;
    begin_sequence_number_ = sequence_number;
    arrival_times.push_back(arrival_time_ms);
    return;
  }

  int64_t pos = sequence_number - begin_sequence_number_;
  if (pos >= 0 && pos < static_cast<int64_t>(arrival_times.size())) {
    // The packet is within the buffer - no need to expand it.
    arrival_times[pos] = arrival_time_ms;
    return;
  }

  if (pos < 0) {
    // The packet goes before the current buffer. Expand to add packet, but only
    // if it fits within kMaxNumberOfPackets.
    size_t missing_packets = -pos;
    if (missing_packets + arrival_times.size() > kMaxNumberOfPackets) {
      // Don't expand the buffer further, as that would remove newly received
      // packets.
      return;
    }

    arrival_times.insert(arrival_times.begin(), missing_packets, 0);
    arrival_times[0] = arrival_time_ms;
    begin_sequence_number_ = sequence_number;
    return;
  }

  // The packet goes after the buffer.

  if (static_cast<size_t>(pos) >= kMaxNumberOfPackets) {
    // The buffer grows too large - old packets have to be removed.
    size_t packets_to_remove = pos - kMaxNumberOfPackets + 1;
    if (packets_to_remove >= arrival_times.size()) {
      arrival_times.clear();
      begin_sequence_number_ = sequence_number;
      pos = 0;
    } else {
      // Also trim the buffer to remove leading non-received packets, to
      // ensure that the buffer only spans received packets.
      while (packets_to_remove < arrival_times.size() &&
             arrival_times[packets_to_remove] == 0) {
        ++packets_to_remove;
      }

      arrival_times.erase(arrival_times.begin(),
                          arrival_times.begin() + packets_to_remove);
      begin_sequence_number_ += packets_to_remove;
      pos -= packets_to_remove;
      RTC_DCHECK_GE(pos, 0);
    }
  }

  // Packets can be received out-of-order. If this isn't the next expected
  // packet, add enough placeholders to fill the gap.
  size_t missing_gap_packets = pos - arrival_times.size();
  if (missing_gap_packets > 0) {
    arrival_times.insert(arrival_times.end(), missing_gap_packets, 0);
  }
  RTC_DCHECK_EQ(arrival_times.size(), pos);
  arrival_times.push_back(arrival_time_ms);
  RTC_DCHECK_LE(arrival_times.size(), kMaxNumberOfPackets);
}

void PacketArrivalTimeMap::RemoveOldPackets(int64_t sequence_number,
                                            int64_t arrival_time_limit) {
  while (!arrival_times.empty() && begin_sequence_number_ < sequence_number &&
         arrival_times.front() <= arrival_time_limit) {
    arrival_times.pop_front();
    ++begin_sequence_number_;
  }
}

bool PacketArrivalTimeMap::has_received(int64_t sequence_number) const {
  int64_t pos = sequence_number - begin_sequence_number_;
  if (pos >= 0 && pos < static_cast<int64_t>(arrival_times.size()) &&
      arrival_times[pos] != 0) {
    return true;
  }
  return false;
}

void PacketArrivalTimeMap::EraseTo(int64_t sequence_number) {
  if (sequence_number > begin_sequence_number_) {
    size_t count =
        std::min(static_cast<size_t>(sequence_number - begin_sequence_number_),
                 arrival_times.size());

    arrival_times.erase(arrival_times.begin(), arrival_times.begin() + count);
    begin_sequence_number_ += count;
  }
}

int64_t PacketArrivalTimeMap::clamp(int64_t sequence_number) const {
  return rtc::SafeClamp(sequence_number, begin_sequence_number(),
                        end_sequence_number());
}

}  // namespace webrtc
