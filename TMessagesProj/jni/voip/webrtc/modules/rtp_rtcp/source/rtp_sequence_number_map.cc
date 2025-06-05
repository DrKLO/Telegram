/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_sequence_number_map.h"

#include <algorithm>
#include <iterator>
#include <limits>

#include "absl/algorithm/container.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/sequence_number_util.h"

namespace webrtc {

RtpSequenceNumberMap::RtpSequenceNumberMap(size_t max_entries)
    : max_entries_(max_entries) {
  RTC_DCHECK_GT(max_entries_, 4);  // See code paring down to `max_entries_`.
  RTC_DCHECK_LE(max_entries_, 1 << 15);
}

RtpSequenceNumberMap::~RtpSequenceNumberMap() = default;

void RtpSequenceNumberMap::InsertPacket(uint16_t sequence_number, Info info) {
  RTC_DCHECK(associations_.size() < 2 ||
             AheadOf(associations_.back().sequence_number,
                     associations_.front().sequence_number));

  if (associations_.empty()) {
    associations_.emplace_back(sequence_number, info);
    return;
  }

  if (AheadOrAt(sequence_number, associations_.front().sequence_number) &&
      AheadOrAt(associations_.back().sequence_number, sequence_number)) {
    // The sequence number has wrapped around and is within the range
    // currently held by `associations_` - we should invalidate all entries.
    RTC_LOG(LS_WARNING) << "Sequence number wrapped-around unexpectedly.";
    associations_.clear();
    associations_.emplace_back(sequence_number, info);
    return;
  }

  std::deque<Association>::iterator erase_to = associations_.begin();

  RTC_DCHECK_LE(associations_.size(), max_entries_);
  if (associations_.size() == max_entries_) {
    // Pare down the container so that inserting some additional elements
    // would not exceed the maximum size.
    const size_t new_size = 3 * max_entries_ / 4;
    erase_to = std::next(erase_to, max_entries_ - new_size);
  }

  // It is guaranteed that `associations_` can be split into two partitions,
  // either partition possibly empty, such that:
  // * In the first partition, all elements are AheadOf the new element.
  //   This is the partition of the obsolete elements.
  // * In the second partition, the new element is AheadOf all the elements.
  //   The elements of this partition may stay.
  auto cmp = [](const Association& a, uint16_t sequence_number) {
    return AheadOf(a.sequence_number, sequence_number);
  };
  RTC_DCHECK(erase_to != associations_.end());
  erase_to =
      std::lower_bound(erase_to, associations_.end(), sequence_number, cmp);
  associations_.erase(associations_.begin(), erase_to);

  associations_.emplace_back(sequence_number, info);

  RTC_DCHECK(associations_.size() == 1 ||
             AheadOf(associations_.back().sequence_number,
                     associations_.front().sequence_number));
}

void RtpSequenceNumberMap::InsertFrame(uint16_t first_sequence_number,
                                       size_t packet_count,
                                       uint32_t timestamp) {
  RTC_DCHECK_GT(packet_count, 0);
  RTC_DCHECK_LE(packet_count, std::numeric_limits<size_t>::max());

  for (size_t i = 0; i < packet_count; ++i) {
    const bool is_first = (i == 0);
    const bool is_last = (i == packet_count - 1);
    InsertPacket(static_cast<uint16_t>(first_sequence_number + i),
                 Info(timestamp, is_first, is_last));
  }
}

absl::optional<RtpSequenceNumberMap::Info> RtpSequenceNumberMap::Get(
    uint16_t sequence_number) const {
  // To make the binary search easier to understand, we use the fact that
  // adding a constant offset to all elements, as well as to the searched
  // element, does not change the relative ordering. This way, we can find
  // an offset that would make all of the elements strictly ascending according
  // to normal integer comparison.
  // Finding such an offset is easy - the offset that would map the oldest
  // element to 0 would serve this purpose.

  if (associations_.empty()) {
    return absl::nullopt;
  }

  const uint16_t offset =
      static_cast<uint16_t>(0) - associations_.front().sequence_number;

  auto cmp = [offset](const Association& a, uint16_t sequence_number) {
    return static_cast<uint16_t>(a.sequence_number + offset) <
           static_cast<uint16_t>(sequence_number + offset);
  };
  const auto elem = absl::c_lower_bound(associations_, sequence_number, cmp);

  return elem != associations_.end() && elem->sequence_number == sequence_number
             ? absl::optional<Info>(elem->info)
             : absl::nullopt;
}

size_t RtpSequenceNumberMap::AssociationCountForTesting() const {
  return associations_.size();
}

}  // namespace webrtc
