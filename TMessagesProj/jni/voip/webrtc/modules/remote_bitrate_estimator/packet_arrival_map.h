/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef MODULES_REMOTE_BITRATE_ESTIMATOR_PACKET_ARRIVAL_MAP_H_
#define MODULES_REMOTE_BITRATE_ESTIMATOR_PACKET_ARRIVAL_MAP_H_

#include <cstddef>
#include <cstdint>
#include <deque>

#include "rtc_base/checks.h"

namespace webrtc {

// PacketArrivalTimeMap is an optimized map of packet sequence number to arrival
// time, limited in size to never exceed `kMaxNumberOfPackets`. It will grow as
// needed, and remove old packets, and will expand to allow earlier packets to
// be added (out-of-order).
//
// Not yet received packets have the arrival time zero. The queue will not span
// larger than necessary and the last packet should always be received. The
// first packet in the queue doesn't have to be received in case of receiving
// packets out-of-order.
class PacketArrivalTimeMap {
 public:
  // Impossible to request feedback older than what can be represented by 15
  // bits.
  static constexpr size_t kMaxNumberOfPackets = (1 << 15);

  // Indicates if the packet with `sequence_number` has already been received.
  bool has_received(int64_t sequence_number) const;

  // Returns the sequence number of the first entry in the map, i.e. the
  // sequence number that a `begin()` iterator would represent.
  int64_t begin_sequence_number() const { return begin_sequence_number_; }

  // Returns the sequence number of the element just after the map, i.e. the
  // sequence number that an `end()` iterator would represent.
  int64_t end_sequence_number() const {
    return begin_sequence_number_ + arrival_times.size();
  }

  // Returns an element by `sequence_number`, which must be valid, i.e.
  // between [begin_sequence_number, end_sequence_number).
  int64_t get(int64_t sequence_number) {
    int64_t pos = sequence_number - begin_sequence_number_;
    RTC_DCHECK(pos >= 0 && pos < static_cast<int64_t>(arrival_times.size()));
    return arrival_times[pos];
  }

  // Clamps `sequence_number` between [begin_sequence_number,
  // end_sequence_number].
  int64_t clamp(int64_t sequence_number) const;

  // Erases all elements from the beginning of the map until `sequence_number`.
  void EraseTo(int64_t sequence_number);

  // Records the fact that a packet with `sequence_number` arrived at
  // `arrival_time_ms`.
  void AddPacket(int64_t sequence_number, int64_t arrival_time_ms);

  // Removes packets from the beginning of the map as long as they are received
  // before `sequence_number` and with an age older than `arrival_time_limit`
  void RemoveOldPackets(int64_t sequence_number, int64_t arrival_time_limit);

 private:
  // Deque representing unwrapped sequence number -> time, where the index +
  // `begin_sequence_number_` represents the packet's sequence number.
  std::deque<int64_t> arrival_times;

  // The unwrapped sequence number for the first element in
  // `arrival_times`.
  int64_t begin_sequence_number_ = 0;

  // Indicates if this map has had any packet added to it. The first packet
  // decides the initial sequence number.
  bool has_seen_packet_ = false;
};

}  // namespace webrtc

#endif  // MODULES_REMOTE_BITRATE_ESTIMATOR_PACKET_ARRIVAL_MAP_H_
