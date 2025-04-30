/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "logging/rtc_event_log/rtc_event_processor.h"

#include "rtc_base/numerics/sequence_number_util.h"

namespace webrtc {

RtcEventProcessor::RtcEventProcessor() = default;
RtcEventProcessor::~RtcEventProcessor() = default;

void RtcEventProcessor::ProcessEventsInOrder() {
  // `event_lists_` is a min-heap of lists ordered by the timestamp of the
  // first element in the list. We therefore process the first element of the
  // first list, then reinsert the remainder of that list into the heap
  // if the list still contains unprocessed elements.
  std::make_heap(event_lists_.begin(), event_lists_.end(), Cmp);

  while (!event_lists_.empty()) {
    event_lists_.front()->ProcessNext();
    std::pop_heap(event_lists_.begin(), event_lists_.end(), Cmp);
    if (event_lists_.back()->IsEmpty()) {
      event_lists_.pop_back();
    } else {
      std::push_heap(event_lists_.begin(), event_lists_.end(), Cmp);
    }
  }
}

bool RtcEventProcessor::Cmp(const RtcEventProcessor::ListPtrType& a,
                            const RtcEventProcessor::ListPtrType& b) {
  int64_t time_diff = a->GetNextTime() - b->GetNextTime();
  if (time_diff != 0)
    return time_diff > 0;

  if (a->GetTypeOrder() != b->GetTypeOrder())
    return a->GetTypeOrder() > b->GetTypeOrder();

  absl::optional<uint16_t> wrapped_seq_num_a = a->GetTransportSeqNum();
  absl::optional<uint16_t> wrapped_seq_num_b = b->GetTransportSeqNum();
  if (wrapped_seq_num_a && wrapped_seq_num_b) {
    return AheadOf<uint16_t>(*wrapped_seq_num_a, *wrapped_seq_num_b);
  } else if (wrapped_seq_num_a.has_value() != wrapped_seq_num_b.has_value()) {
    return wrapped_seq_num_a.has_value();
  }

  return a->GetInsertionOrder() > b->GetInsertionOrder();
}

}  // namespace webrtc
