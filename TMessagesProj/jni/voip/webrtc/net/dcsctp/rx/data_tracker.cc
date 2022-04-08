/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/rx/data_tracker.h"

#include <algorithm>
#include <cstdint>
#include <iterator>
#include <set>
#include <string>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "net/dcsctp/common/sequence_numbers.h"
#include "net/dcsctp/packet/chunk/sack_chunk.h"
#include "net/dcsctp/timer/timer.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"

namespace dcsctp {

constexpr size_t DataTracker::kMaxDuplicateTsnReported;
constexpr size_t DataTracker::kMaxGapAckBlocksReported;

bool DataTracker::AdditionalTsnBlocks::Add(UnwrappedTSN tsn) {
  // Find any block to expand. It will look for any block that includes (also
  // when expanded) the provided `tsn`. It will return the block that is greater
  // than, or equal to `tsn`.
  auto it = absl::c_lower_bound(
      blocks_, tsn, [&](const TsnRange& elem, const UnwrappedTSN& t) {
        return elem.last.next_value() < t;
      });

  if (it == blocks_.end()) {
    // No matching block found. There is no greater than, or equal block - which
    // means that this TSN is greater than any block. It can then be inserted at
    // the end.
    blocks_.emplace_back(tsn, tsn);
    return true;
  }

  if (tsn >= it->first && tsn <= it->last) {
    // It's already in this block.
    return false;
  }

  if (it->last.next_value() == tsn) {
    // This block can be expanded to the right, or merged with the next.
    auto next_it = it + 1;
    if (next_it != blocks_.end() && tsn.next_value() == next_it->first) {
      // Expanding it would make it adjacent to next block - merge those.
      it->last = next_it->last;
      blocks_.erase(next_it);
      return true;
    }

    // Expand to the right
    it->last = tsn;
    return true;
  }

  if (it->first == tsn.next_value()) {
    // This block can be expanded to the left. Merging to the left would've been
    // covered by the above "merge to the right". Both blocks (expand a
    // right-most block to the left and expand a left-most block to the right)
    // would match, but the left-most would be returned by std::lower_bound.
    RTC_DCHECK(it == blocks_.begin() || (it - 1)->last.next_value() != tsn);

    // Expand to the left.
    it->first = tsn;
    return true;
  }

  // Need to create a new block in the middle.
  blocks_.emplace(it, tsn, tsn);
  return true;
}

void DataTracker::AdditionalTsnBlocks::EraseTo(UnwrappedTSN tsn) {
  // Find the block that is greater than or equals `tsn`.
  auto it = absl::c_lower_bound(
      blocks_, tsn, [&](const TsnRange& elem, const UnwrappedTSN& t) {
        return elem.last < t;
      });

  // The block that is found is greater or equal (or possibly ::end, when no
  // block is greater or equal). All blocks before this block can be safely
  // removed. the TSN might be within this block, so possibly truncate it.
  bool tsn_is_within_block = it != blocks_.end() && tsn >= it->first;
  blocks_.erase(blocks_.begin(), it);

  if (tsn_is_within_block) {
    blocks_.front().first = tsn.next_value();
  }
}

void DataTracker::AdditionalTsnBlocks::PopFront() {
  RTC_DCHECK(!blocks_.empty());
  blocks_.erase(blocks_.begin());
}

bool DataTracker::IsTSNValid(TSN tsn) const {
  UnwrappedTSN unwrapped_tsn = tsn_unwrapper_.PeekUnwrap(tsn);

  // Note that this method doesn't return `false` for old DATA chunks, as those
  // are actually valid, and receiving those may affect the generated SACK
  // response (by setting "duplicate TSNs").

  uint32_t difference =
      UnwrappedTSN::Difference(unwrapped_tsn, last_cumulative_acked_tsn_);
  if (difference > kMaxAcceptedOutstandingFragments) {
    return false;
  }
  return true;
}

void DataTracker::Observe(TSN tsn,
                          AnyDataChunk::ImmediateAckFlag immediate_ack) {
  UnwrappedTSN unwrapped_tsn = tsn_unwrapper_.Unwrap(tsn);

  // IsTSNValid must be called prior to calling this method.
  RTC_DCHECK(
      UnwrappedTSN::Difference(unwrapped_tsn, last_cumulative_acked_tsn_) <=
      kMaxAcceptedOutstandingFragments);

  // Old chunk already seen before?
  if (unwrapped_tsn <= last_cumulative_acked_tsn_) {
    if (duplicate_tsns_.size() < kMaxDuplicateTsnReported) {
      duplicate_tsns_.insert(unwrapped_tsn.Wrap());
    }
    // https://datatracker.ietf.org/doc/html/rfc4960#section-6.2
    // "When a packet arrives with duplicate DATA chunk(s) and with no new DATA
    // chunk(s), the endpoint MUST immediately send a SACK with no delay. If a
    // packet arrives with duplicate DATA chunk(s) bundled with new DATA chunks,
    // the endpoint MAY immediately send a SACK."
    UpdateAckState(AckState::kImmediate, "duplicate data");
  } else {
    if (unwrapped_tsn == last_cumulative_acked_tsn_.next_value()) {
      last_cumulative_acked_tsn_ = unwrapped_tsn;
      // The cumulative acked tsn may be moved even further, if a gap was
      // filled.
      if (!additional_tsn_blocks_.empty() &&
          additional_tsn_blocks_.front().first ==
              last_cumulative_acked_tsn_.next_value()) {
        last_cumulative_acked_tsn_ = additional_tsn_blocks_.front().last;
        additional_tsn_blocks_.PopFront();
      }
    } else {
      bool inserted = additional_tsn_blocks_.Add(unwrapped_tsn);
      if (!inserted) {
        // Already seen before.
        if (duplicate_tsns_.size() < kMaxDuplicateTsnReported) {
          duplicate_tsns_.insert(unwrapped_tsn.Wrap());
        }
        // https://datatracker.ietf.org/doc/html/rfc4960#section-6.2
        // "When a packet arrives with duplicate DATA chunk(s) and with no new
        // DATA chunk(s), the endpoint MUST immediately send a SACK with no
        // delay. If a packet arrives with duplicate DATA chunk(s) bundled with
        // new DATA chunks, the endpoint MAY immediately send a SACK."
        // No need to do this. SACKs are sent immediately on packet loss below.
      }
    }
  }

  // https://tools.ietf.org/html/rfc4960#section-6.7
  // "Upon the reception of a new DATA chunk, an endpoint shall examine the
  // continuity of the TSNs received.  If the endpoint detects a gap in
  // the received DATA chunk sequence, it SHOULD send a SACK with Gap Ack
  // Blocks immediately.  The data receiver continues sending a SACK after
  // receipt of each SCTP packet that doesn't fill the gap."
  if (!additional_tsn_blocks_.empty()) {
    UpdateAckState(AckState::kImmediate, "packet loss");
  }

  // https://tools.ietf.org/html/rfc7053#section-5.2
  // "Upon receipt of an SCTP packet containing a DATA chunk with the I
  // bit set, the receiver SHOULD NOT delay the sending of the corresponding
  // SACK chunk, i.e., the receiver SHOULD immediately respond with the
  // corresponding SACK chunk."
  if (*immediate_ack) {
    UpdateAckState(AckState::kImmediate, "immediate-ack bit set");
  }

  if (!seen_packet_) {
    // https://tools.ietf.org/html/rfc4960#section-5.1
    // "After the reception of the first DATA chunk in an association the
    // endpoint MUST immediately respond with a SACK to acknowledge the DATA
    // chunk."
    seen_packet_ = true;
    UpdateAckState(AckState::kImmediate, "first DATA chunk");
  }

  // https://tools.ietf.org/html/rfc4960#section-6.2
  // "Specifically, an acknowledgement SHOULD be generated for at least
  // every second packet (not every second DATA chunk) received, and SHOULD be
  // generated within 200 ms of the arrival of any unacknowledged DATA chunk."
  if (ack_state_ == AckState::kIdle) {
    UpdateAckState(AckState::kBecomingDelayed, "received DATA when idle");
  } else if (ack_state_ == AckState::kDelayed) {
    UpdateAckState(AckState::kImmediate, "received DATA when already delayed");
  }
}

void DataTracker::HandleForwardTsn(TSN new_cumulative_ack) {
  // ForwardTSN is sent to make the receiver (this socket) "forget" about partly
  // received (or not received at all) data, up until `new_cumulative_ack`.

  UnwrappedTSN unwrapped_tsn = tsn_unwrapper_.Unwrap(new_cumulative_ack);
  UnwrappedTSN prev_last_cum_ack_tsn = last_cumulative_acked_tsn_;

  // Old chunk already seen before?
  if (unwrapped_tsn <= last_cumulative_acked_tsn_) {
    // https://tools.ietf.org/html/rfc3758#section-3.6
    // "Note, if the "New Cumulative TSN" value carried in the arrived
    // FORWARD TSN chunk is found to be behind or at the current cumulative TSN
    // point, the data receiver MUST treat this FORWARD TSN as out-of-date and
    // MUST NOT update its Cumulative TSN.  The receiver SHOULD send a SACK to
    // its peer (the sender of the FORWARD TSN) since such a duplicate may
    // indicate the previous SACK was lost in the network."
    UpdateAckState(AckState::kImmediate,
                   "FORWARD_TSN new_cumulative_tsn was behind");
    return;
  }

  // https://tools.ietf.org/html/rfc3758#section-3.6
  // "When a FORWARD TSN chunk arrives, the data receiver MUST first update
  // its cumulative TSN point to the value carried in the FORWARD TSN chunk, and
  // then MUST further advance its cumulative TSN point locally if possible, as
  // shown by the following example..."

  // The `new_cumulative_ack` will become the current
  // `last_cumulative_acked_tsn_`, and if there have been prior "gaps" that are
  // now overlapping with the new value, remove them.
  last_cumulative_acked_tsn_ = unwrapped_tsn;
  additional_tsn_blocks_.EraseTo(unwrapped_tsn);

  // See if the `last_cumulative_acked_tsn_` can be moved even further:
  if (!additional_tsn_blocks_.empty() &&
      additional_tsn_blocks_.front().first ==
          last_cumulative_acked_tsn_.next_value()) {
    last_cumulative_acked_tsn_ = additional_tsn_blocks_.front().last;
    additional_tsn_blocks_.PopFront();
  }

  RTC_DLOG(LS_VERBOSE) << log_prefix_ << "FORWARD_TSN, cum_ack_tsn="
                       << *prev_last_cum_ack_tsn.Wrap() << "->"
                       << *new_cumulative_ack << "->"
                       << *last_cumulative_acked_tsn_.Wrap();

  // https://tools.ietf.org/html/rfc3758#section-3.6
  // "Any time a FORWARD TSN chunk arrives, for the purposes of sending a
  // SACK, the receiver MUST follow the same rules as if a DATA chunk had been
  // received (i.e., follow the delayed sack rules specified in ..."
  if (ack_state_ == AckState::kIdle) {
    UpdateAckState(AckState::kBecomingDelayed,
                   "received FORWARD_TSN when idle");
  } else if (ack_state_ == AckState::kDelayed) {
    UpdateAckState(AckState::kImmediate,
                   "received FORWARD_TSN when already delayed");
  }
}

SackChunk DataTracker::CreateSelectiveAck(size_t a_rwnd) {
  // Note that in SCTP, the receiver side is allowed to discard received data
  // and signal that to the sender, but only chunks that have previously been
  // reported in the gap-ack-blocks. However, this implementation will never do
  // that. So this SACK produced is more like a NR-SACK as explained in
  // https://ieeexplore.ieee.org/document/4697037 and which there is an RFC
  // draft at https://tools.ietf.org/html/draft-tuexen-tsvwg-sctp-multipath-17.
  std::set<TSN> duplicate_tsns;
  duplicate_tsns_.swap(duplicate_tsns);

  return SackChunk(last_cumulative_acked_tsn_.Wrap(), a_rwnd,
                   CreateGapAckBlocks(), std::move(duplicate_tsns));
}

std::vector<SackChunk::GapAckBlock> DataTracker::CreateGapAckBlocks() const {
  const auto& blocks = additional_tsn_blocks_.blocks();
  std::vector<SackChunk::GapAckBlock> gap_ack_blocks;
  gap_ack_blocks.reserve(std::min(blocks.size(), kMaxGapAckBlocksReported));
  for (size_t i = 0; i < blocks.size() && i < kMaxGapAckBlocksReported; ++i) {
    auto start_diff =
        UnwrappedTSN::Difference(blocks[i].first, last_cumulative_acked_tsn_);
    auto end_diff =
        UnwrappedTSN::Difference(blocks[i].last, last_cumulative_acked_tsn_);
    gap_ack_blocks.emplace_back(static_cast<uint16_t>(start_diff),
                                static_cast<uint16_t>(end_diff));
  }

  return gap_ack_blocks;
}

bool DataTracker::ShouldSendAck(bool also_if_delayed) {
  if (ack_state_ == AckState::kImmediate ||
      (also_if_delayed && (ack_state_ == AckState::kBecomingDelayed ||
                           ack_state_ == AckState::kDelayed))) {
    UpdateAckState(AckState::kIdle, "sending SACK");
    return true;
  }

  return false;
}

bool DataTracker::will_increase_cum_ack_tsn(TSN tsn) const {
  UnwrappedTSN unwrapped = tsn_unwrapper_.PeekUnwrap(tsn);
  return unwrapped == last_cumulative_acked_tsn_.next_value();
}

void DataTracker::ForceImmediateSack() {
  ack_state_ = AckState::kImmediate;
}

void DataTracker::HandleDelayedAckTimerExpiry() {
  UpdateAckState(AckState::kImmediate, "delayed ack timer expired");
}

void DataTracker::ObservePacketEnd() {
  if (ack_state_ == AckState::kBecomingDelayed) {
    UpdateAckState(AckState::kDelayed, "packet end");
  }
}

void DataTracker::UpdateAckState(AckState new_state, absl::string_view reason) {
  if (new_state != ack_state_) {
    RTC_DLOG(LS_VERBOSE) << log_prefix_ << "State changed from "
                         << ToString(ack_state_) << " to "
                         << ToString(new_state) << " due to " << reason;
    if (ack_state_ == AckState::kDelayed) {
      delayed_ack_timer_.Stop();
    } else if (new_state == AckState::kDelayed) {
      delayed_ack_timer_.Start();
    }
    ack_state_ = new_state;
  }
}

absl::string_view DataTracker::ToString(AckState ack_state) {
  switch (ack_state) {
    case AckState::kIdle:
      return "IDLE";
    case AckState::kBecomingDelayed:
      return "BECOMING_DELAYED";
    case AckState::kDelayed:
      return "DELAYED";
    case AckState::kImmediate:
      return "IMMEDIATE";
  }
}

HandoverReadinessStatus DataTracker::GetHandoverReadiness() const {
  HandoverReadinessStatus status;
  if (!additional_tsn_blocks_.empty()) {
    status.Add(HandoverUnreadinessReason::kDataTrackerTsnBlocksPending);
  }
  return status;
}

void DataTracker::AddHandoverState(DcSctpSocketHandoverState& state) {
  state.rx.last_cumulative_acked_tsn = last_cumulative_acked_tsn().value();
  state.rx.seen_packet = seen_packet_;
}

}  // namespace dcsctp
