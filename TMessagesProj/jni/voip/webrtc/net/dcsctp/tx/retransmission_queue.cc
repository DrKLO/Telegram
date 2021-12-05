/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/tx/retransmission_queue.h"

#include <algorithm>
#include <cstdint>
#include <functional>
#include <iterator>
#include <map>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/common/math.h"
#include "net/dcsctp/common/pair_hash.h"
#include "net/dcsctp/common/sequence_numbers.h"
#include "net/dcsctp/common/str_join.h"
#include "net/dcsctp/packet/chunk/data_chunk.h"
#include "net/dcsctp/packet/chunk/forward_tsn_chunk.h"
#include "net/dcsctp/packet/chunk/forward_tsn_common.h"
#include "net/dcsctp/packet/chunk/idata_chunk.h"
#include "net/dcsctp/packet/chunk/iforward_tsn_chunk.h"
#include "net/dcsctp/packet/chunk/sack_chunk.h"
#include "net/dcsctp/packet/data.h"
#include "net/dcsctp/public/dcsctp_options.h"
#include "net/dcsctp/public/types.h"
#include "net/dcsctp/timer/timer.h"
#include "net/dcsctp/tx/send_queue.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"

namespace dcsctp {
namespace {

// The number of times a packet must be NACKed before it's retransmitted.
// See https://tools.ietf.org/html/rfc4960#section-7.2.4
constexpr size_t kNumberOfNacksForRetransmission = 3;
}  // namespace

RetransmissionQueue::RetransmissionQueue(
    absl::string_view log_prefix,
    TSN initial_tsn,
    size_t a_rwnd,
    SendQueue& send_queue,
    std::function<void(DurationMs rtt)> on_new_rtt,
    std::function<void()> on_send_queue_empty,
    std::function<void()> on_clear_retransmission_counter,
    Timer& t3_rtx,
    const DcSctpOptions& options,
    bool supports_partial_reliability,
    bool use_message_interleaving)
    : options_(options),
      partial_reliability_(supports_partial_reliability),
      log_prefix_(std::string(log_prefix) + "tx: "),
      data_chunk_header_size_(use_message_interleaving
                                  ? IDataChunk::kHeaderSize
                                  : DataChunk::kHeaderSize),
      on_new_rtt_(std::move(on_new_rtt)),
      on_send_queue_empty_(std::move(on_send_queue_empty)),
      on_clear_retransmission_counter_(
          std::move(on_clear_retransmission_counter)),
      t3_rtx_(t3_rtx),
      cwnd_(options_.cwnd_mtus_initial * options_.mtu),
      rwnd_(a_rwnd),
      // https://tools.ietf.org/html/rfc4960#section-7.2.1
      // "The initial value of ssthresh MAY be arbitrarily high (for
      // example, implementations MAY use the size of the receiver advertised
      // window).""
      ssthresh_(rwnd_),
      next_tsn_(tsn_unwrapper_.Unwrap(initial_tsn)),
      last_cumulative_tsn_ack_(tsn_unwrapper_.Unwrap(TSN(*initial_tsn - 1))),
      send_queue_(send_queue) {}

// Returns how large a chunk will be, serialized, carrying the data
size_t RetransmissionQueue::GetSerializedChunkSize(const Data& data) const {
  return RoundUpTo4(data_chunk_header_size_ + data.size());
}

void RetransmissionQueue::RemoveAcked(UnwrappedTSN cumulative_tsn_ack,
                                      AckInfo& ack_info) {
  auto first_unacked = outstanding_data_.upper_bound(cumulative_tsn_ack);

  for (auto it = outstanding_data_.begin(); it != first_unacked; ++it) {
    ack_info.bytes_acked_by_cumulative_tsn_ack += it->second.data().size();
    ack_info.acked_tsns.push_back(it->first.Wrap());
  }

  outstanding_data_.erase(outstanding_data_.begin(), first_unacked);
}

void RetransmissionQueue::AckGapBlocks(
    UnwrappedTSN cumulative_tsn_ack,
    rtc::ArrayView<const SackChunk::GapAckBlock> gap_ack_blocks,
    AckInfo& ack_info) {
  // Mark all non-gaps as ACKED (but they can't be removed) as (from RFC)
  // "SCTP considers the information carried in the Gap Ack Blocks in the
  // SACK chunk as advisory.". Note that when NR-SACK is supported, this can be
  // handled differently.

  for (auto& block : gap_ack_blocks) {
    auto start = outstanding_data_.lower_bound(
        UnwrappedTSN::AddTo(cumulative_tsn_ack, block.start));
    auto end = outstanding_data_.upper_bound(
        UnwrappedTSN::AddTo(cumulative_tsn_ack, block.end));
    for (auto iter = start; iter != end; ++iter) {
      if (iter->second.state() != State::kAcked) {
        ack_info.bytes_acked_by_new_gap_ack_blocks +=
            iter->second.data().size();
        iter->second.SetState(State::kAcked);
        ack_info.highest_tsn_acked =
            std::max(ack_info.highest_tsn_acked, iter->first);
        ack_info.acked_tsns.push_back(iter->first.Wrap());
      }
    }
  }
}

void RetransmissionQueue::NackBetweenAckBlocks(
    UnwrappedTSN cumulative_tsn_ack,
    rtc::ArrayView<const SackChunk::GapAckBlock> gap_ack_blocks,
    AckInfo& ack_info) {
  // Mark everything between the blocks as NACKED/TO_BE_RETRANSMITTED.
  // https://tools.ietf.org/html/rfc4960#section-7.2.4
  // "Mark the DATA chunk(s) with three miss indications for retransmission."
  // "For each incoming SACK, miss indications are incremented only for
  // missing TSNs prior to the highest TSN newly acknowledged in the SACK."
  //
  // What this means is that only when there is a increasing stream of data
  // received and there are new packets seen (since last time), packets that are
  // in-flight and between gaps should be nacked. This means that SCTP relies on
  // the T3-RTX-timer to re-send packets otherwise.
  UnwrappedTSN max_tsn_to_nack = ack_info.highest_tsn_acked;
  if (is_in_fast_recovery() && cumulative_tsn_ack > last_cumulative_tsn_ack_) {
    // https://tools.ietf.org/html/rfc4960#section-7.2.4
    // "If an endpoint is in Fast Recovery and a SACK arrives that advances
    // the Cumulative TSN Ack Point, the miss indications are incremented for
    // all TSNs reported missing in the SACK."
    max_tsn_to_nack = UnwrappedTSN::AddTo(
        cumulative_tsn_ack,
        gap_ack_blocks.empty() ? 0 : gap_ack_blocks.rbegin()->end);
  }

  UnwrappedTSN prev_block_last_acked = cumulative_tsn_ack;
  for (auto& block : gap_ack_blocks) {
    UnwrappedTSN cur_block_first_acked =
        UnwrappedTSN::AddTo(cumulative_tsn_ack, block.start);
    for (auto iter = outstanding_data_.upper_bound(prev_block_last_acked);
         iter != outstanding_data_.lower_bound(cur_block_first_acked); ++iter) {
      if (iter->first <= max_tsn_to_nack) {
        iter->second.Nack();

        if (iter->second.state() == State::kToBeRetransmitted) {
          ack_info.has_packet_loss = true;
          RTC_DLOG(LS_VERBOSE) << log_prefix_ << *iter->first.Wrap()
                               << " marked for retransmission";
        }
      }
    }
    prev_block_last_acked = UnwrappedTSN::AddTo(cumulative_tsn_ack, block.end);
  }

  // Note that packets are not NACKED which are above the highest gap-ack-block
  // (or above the cumulative ack TSN if no gap-ack-blocks) as only packets
  // up until the highest_tsn_acked (see above) should be considered when
  // NACKing.
}

void RetransmissionQueue::MaybeExitFastRecovery(
    UnwrappedTSN cumulative_tsn_ack) {
  // https://tools.ietf.org/html/rfc4960#section-7.2.4
  // "When a SACK acknowledges all TSNs up to and including this [fast
  // recovery] exit point, Fast Recovery is exited."
  if (fast_recovery_exit_tsn_.has_value() &&
      cumulative_tsn_ack >= *fast_recovery_exit_tsn_) {
    RTC_DLOG(LS_VERBOSE) << log_prefix_
                         << "exit_point=" << *fast_recovery_exit_tsn_->Wrap()
                         << " reached - exiting fast recovery";
    fast_recovery_exit_tsn_ = absl::nullopt;
  }
}

void RetransmissionQueue::HandleIncreasedCumulativeTsnAck(
    size_t outstanding_bytes,
    size_t total_bytes_acked) {
  // Allow some margin for classifying as fully utilized, due to e.g. that too
  // small packets (less than kMinimumFragmentedPayload) are not sent +
  // overhead.
  bool is_fully_utilized = outstanding_bytes + options_.mtu >= cwnd_;
  size_t old_cwnd = cwnd_;
  if (phase() == CongestionAlgorithmPhase::kSlowStart) {
    if (is_fully_utilized && !is_in_fast_recovery()) {
      // https://tools.ietf.org/html/rfc4960#section-7.2.1
      // "Only when these three conditions are met can the cwnd be
      // increased; otherwise, the cwnd MUST not be increased. If these
      // conditions are met, then cwnd MUST be increased by, at most, the
      // lesser of 1) the total size of the previously outstanding DATA
      // chunk(s) acknowledged, and 2) the destination's path MTU."
      if (options_.slow_start_tcp_style) {
        cwnd_ += std::min(total_bytes_acked, cwnd_);
      } else {
        cwnd_ += std::min(total_bytes_acked, options_.mtu);
      }
      RTC_DLOG(LS_VERBOSE) << log_prefix_ << "SS increase cwnd=" << cwnd_
                           << " (" << old_cwnd << ")";
    }
  } else if (phase() == CongestionAlgorithmPhase::kCongestionAvoidance) {
    // https://tools.ietf.org/html/rfc4960#section-7.2.2
    // "Whenever cwnd is greater than ssthresh, upon each SACK arrival
    // that advances the Cumulative TSN Ack Point, increase
    // partial_bytes_acked by the total number of bytes of all new chunks
    // acknowledged in that SACK including chunks acknowledged by the new
    // Cumulative TSN Ack and by Gap Ack Blocks."
    size_t old_pba = partial_bytes_acked_;
    partial_bytes_acked_ += total_bytes_acked;

    if (partial_bytes_acked_ >= cwnd_ && is_fully_utilized) {
      // https://tools.ietf.org/html/rfc4960#section-7.2.2
      // "When partial_bytes_acked is equal to or greater than cwnd and
      // before the arrival of the SACK the sender had cwnd or more bytes of
      // data outstanding (i.e., before arrival of the SACK, flightsize was
      // greater than or equal to cwnd), increase cwnd by MTU, and reset
      // partial_bytes_acked to (partial_bytes_acked - cwnd)."
      cwnd_ += options_.mtu;
      partial_bytes_acked_ -= cwnd_;
      RTC_DLOG(LS_VERBOSE) << log_prefix_ << "CA increase cwnd=" << cwnd_
                           << " (" << old_cwnd << ") ssthresh=" << ssthresh_
                           << ", pba=" << partial_bytes_acked_ << " ("
                           << old_pba << ")";
    } else {
      RTC_DLOG(LS_VERBOSE) << log_prefix_ << "CA unchanged cwnd=" << cwnd_
                           << " (" << old_cwnd << ") ssthresh=" << ssthresh_
                           << ", pba=" << partial_bytes_acked_ << " ("
                           << old_pba << ")";
    }
  }
}

void RetransmissionQueue::HandlePacketLoss(UnwrappedTSN highest_tsn_acked) {
  if (!is_in_fast_recovery()) {
    // https://tools.ietf.org/html/rfc4960#section-7.2.4
    // "If not in Fast Recovery, adjust the ssthresh and cwnd of the
    // destination address(es) to which the missing DATA chunks were last
    // sent, according to the formula described in Section 7.2.3."
    size_t old_cwnd = cwnd_;
    size_t old_pba = partial_bytes_acked_;
    ssthresh_ = std::max(cwnd_ / 2, options_.cwnd_mtus_min * options_.mtu);
    cwnd_ = ssthresh_;
    partial_bytes_acked_ = 0;

    RTC_DLOG(LS_VERBOSE) << log_prefix_
                         << "packet loss detected (not fast recovery). cwnd="
                         << cwnd_ << " (" << old_cwnd
                         << "), ssthresh=" << ssthresh_
                         << ", pba=" << partial_bytes_acked_ << " (" << old_pba
                         << ")";

    // https://tools.ietf.org/html/rfc4960#section-7.2.4
    // "If not in Fast Recovery, enter Fast Recovery and mark the highest
    // outstanding TSN as the Fast Recovery exit point."
    fast_recovery_exit_tsn_ = outstanding_data_.empty()
                                  ? last_cumulative_tsn_ack_
                                  : outstanding_data_.rbegin()->first;
    RTC_DLOG(LS_VERBOSE) << log_prefix_
                         << "fast recovery initiated with exit_point="
                         << *fast_recovery_exit_tsn_->Wrap();
  } else {
    // https://tools.ietf.org/html/rfc4960#section-7.2.4
    // "While in Fast Recovery, the ssthresh and cwnd SHOULD NOT change for
    // any destinations due to a subsequent Fast Recovery event (i.e., one
    // SHOULD NOT reduce the cwnd further due to a subsequent Fast Retransmit)."
    RTC_DLOG(LS_VERBOSE) << log_prefix_
                         << "packet loss detected (fast recovery). No changes.";
  }
}

void RetransmissionQueue::UpdateReceiverWindow(uint32_t a_rwnd) {
  rwnd_ = outstanding_bytes_ >= a_rwnd ? 0 : a_rwnd - outstanding_bytes_;
}

void RetransmissionQueue::StartT3RtxTimerIfOutstandingData() {
  // Note: Can't use `outstanding_bytes()` as that one doesn't count chunks to
  // be retransmitted.
  if (outstanding_data_.empty()) {
    // https://tools.ietf.org/html/rfc4960#section-6.3.2
    // "Whenever all outstanding data sent to an address have been
    // acknowledged, turn off the T3-rtx timer of that address.
    // Note: Already stopped in `StopT3RtxTimerOnIncreasedCumulativeTsnAck`."
  } else {
    // https://tools.ietf.org/html/rfc4960#section-6.3.2
    // "Whenever a SACK is received that acknowledges the DATA chunk
    // with the earliest outstanding TSN for that address, restart the T3-rtx
    // timer for that address with its current RTO (if there is still
    // outstanding data on that address)."
    // "Whenever a SACK is received missing a TSN that was previously
    // acknowledged via a Gap Ack Block, start the T3-rtx for the destination
    // address to which the DATA chunk was originally transmitted if it is not
    // already running."
    if (!t3_rtx_.is_running()) {
      t3_rtx_.Start();
    }
  }
}

bool RetransmissionQueue::IsSackValid(const SackChunk& sack) const {
  // https://tools.ietf.org/html/rfc4960#section-6.2.1
  // "If Cumulative TSN Ack is less than the Cumulative TSN Ack Point,
  // then drop the SACK.  Since Cumulative TSN Ack is monotonically increasing,
  // a SACK whose Cumulative TSN Ack is less than the Cumulative TSN Ack Point
  // indicates an out-of- order SACK."
  //
  // Note: Important not to drop SACKs with identical TSN to that previously
  // received, as the gap ack blocks or dup tsn fields may have changed.
  UnwrappedTSN cumulative_tsn_ack =
      tsn_unwrapper_.PeekUnwrap(sack.cumulative_tsn_ack());
  if (cumulative_tsn_ack < last_cumulative_tsn_ack_) {
    // https://tools.ietf.org/html/rfc4960#section-6.2.1
    // "If Cumulative TSN Ack is less than the Cumulative TSN Ack Point,
    // then drop the SACK.  Since Cumulative TSN Ack is monotonically
    // increasing, a SACK whose Cumulative TSN Ack is less than the Cumulative
    // TSN Ack Point indicates an out-of- order SACK."
    return false;
  } else if (outstanding_data_.empty() &&
             cumulative_tsn_ack > last_cumulative_tsn_ack_) {
    // No in-flight data and cum-tsn-ack above what was last ACKed - not valid.
    return false;
  } else if (!outstanding_data_.empty() &&
             cumulative_tsn_ack > outstanding_data_.rbegin()->first) {
    // There is in-flight data, but the cum-tsn-ack is beyond that - not valid.
    return false;
  }
  return true;
}

bool RetransmissionQueue::HandleSack(TimeMs now, const SackChunk& sack) {
  if (!IsSackValid(sack)) {
    return false;
  }

  size_t old_outstanding_bytes = outstanding_bytes_;
  size_t old_rwnd = rwnd_;
  UnwrappedTSN cumulative_tsn_ack =
      tsn_unwrapper_.Unwrap(sack.cumulative_tsn_ack());

  if (sack.gap_ack_blocks().empty()) {
    UpdateRTT(now, cumulative_tsn_ack);
  }

  AckInfo ack_info(cumulative_tsn_ack);
  // Erase all items up to cumulative_tsn_ack.
  RemoveAcked(cumulative_tsn_ack, ack_info);

  // ACK packets reported in the gap ack blocks
  AckGapBlocks(cumulative_tsn_ack, sack.gap_ack_blocks(), ack_info);

  // NACK and possibly mark for retransmit chunks that weren't acked.
  NackBetweenAckBlocks(cumulative_tsn_ack, sack.gap_ack_blocks(), ack_info);

  RecalculateOutstandingBytes();
  // Update of outstanding_data_ is now done. Congestion control remains.
  UpdateReceiverWindow(sack.a_rwnd());

  RTC_DLOG(LS_VERBOSE) << log_prefix_ << "Received SACK. Acked TSN: "
                       << StrJoin(ack_info.acked_tsns, ",",
                                  [](rtc::StringBuilder& sb, TSN tsn) {
                                    sb << *tsn;
                                  })
                       << ", cum_tsn_ack=" << *cumulative_tsn_ack.Wrap() << " ("
                       << *last_cumulative_tsn_ack_.Wrap()
                       << "), outstanding_bytes=" << outstanding_bytes_ << " ("
                       << old_outstanding_bytes << "), rwnd=" << rwnd_ << " ("
                       << old_rwnd << ")";

  MaybeExitFastRecovery(cumulative_tsn_ack);

  if (cumulative_tsn_ack > last_cumulative_tsn_ack_) {
    // https://tools.ietf.org/html/rfc4960#section-6.3.2
    // "Whenever a SACK is received that acknowledges the DATA chunk
    // with the earliest outstanding TSN for that address, restart the T3-rtx
    // timer for that address with its current RTO (if there is still
    // outstanding data on that address)."
    // Note: It may be started again in a bit further down.
    t3_rtx_.Stop();

    HandleIncreasedCumulativeTsnAck(
        old_outstanding_bytes, ack_info.bytes_acked_by_cumulative_tsn_ack +
                                   ack_info.bytes_acked_by_new_gap_ack_blocks);
  }

  if (ack_info.has_packet_loss) {
    is_in_fast_retransmit_ = true;
    HandlePacketLoss(ack_info.highest_tsn_acked);
  }

  // https://tools.ietf.org/html/rfc4960#section-8.2
  // "When an outstanding TSN is acknowledged [...] the endpoint shall clear
  // the error counter ..."
  if (ack_info.bytes_acked_by_cumulative_tsn_ack > 0 ||
      ack_info.bytes_acked_by_new_gap_ack_blocks > 0) {
    on_clear_retransmission_counter_();
  }

  last_cumulative_tsn_ack_ = cumulative_tsn_ack;
  StartT3RtxTimerIfOutstandingData();
  return true;
}

void RetransmissionQueue::UpdateRTT(TimeMs now,
                                    UnwrappedTSN cumulative_tsn_ack) {
  // RTT updating is flawed in SCTP, as explained in e.g. Pedersen J, Griwodz C,
  // Halvorsen P (2006) Considerations of SCTP retransmission delays for thin
  // streams.
  // Due to delayed acknowledgement, the SACK may be sent much later which
  // increases the calculated RTT.
  // TODO(boivie): Consider occasionally sending DATA chunks with I-bit set and
  // use only those packets for measurement.

  auto it = outstanding_data_.find(cumulative_tsn_ack);
  if (it != outstanding_data_.end()) {
    if (!it->second.has_been_retransmitted()) {
      // https://tools.ietf.org/html/rfc4960#section-6.3.1
      // "Karn's algorithm: RTT measurements MUST NOT be made using
      // packets that were retransmitted (and thus for which it is ambiguous
      // whether the reply was for the first instance of the chunk or for a
      // later instance)"
      DurationMs rtt = now - it->second.time_sent();
      on_new_rtt_(rtt);
    }
  }
}

void RetransmissionQueue::RecalculateOutstandingBytes() {
  outstanding_bytes_ = absl::c_accumulate(
      outstanding_data_, 0,
      [&](size_t r, const std::pair<const UnwrappedTSN, TxData>& d) {
        // Packets that have been ACKED or NACKED are not outstanding, as they
        // are received. And packets that are marked for retransmission or
        // abandoned are lost, and not outstanding.
        return r + (d.second.state() == State::kInFlight
                        ? GetSerializedChunkSize(d.second.data())
                        : 0);
      });
}

void RetransmissionQueue::HandleT3RtxTimerExpiry() {
  size_t old_cwnd = cwnd_;
  size_t old_outstanding_bytes = outstanding_bytes_;
  // https://tools.ietf.org/html/rfc4960#section-6.3.3
  // "For the destination address for which the timer expires, adjust
  // its ssthresh with rules defined in Section 7.2.3 and set the cwnd <- MTU."
  ssthresh_ = std::max(cwnd_ / 2, 4 * options_.mtu);
  cwnd_ = 1 * options_.mtu;

  // https://tools.ietf.org/html/rfc4960#section-6.3.3
  // "For the destination address for which the timer expires, set RTO
  // <- RTO * 2 ("back off the timer").  The maximum value discussed in rule C7
  // above (RTO.max) may be used to provide an upper bound to this doubling
  // operation."

  // Already done by the Timer implementation.

  // https://tools.ietf.org/html/rfc4960#section-6.3.3
  // "Determine how many of the earliest (i.e., lowest TSN) outstanding
  // DATA chunks for the address for which the T3-rtx has expired will fit into
  // a single packet"

  // https://tools.ietf.org/html/rfc4960#section-6.3.3
  // "Note: Any DATA chunks that were sent to the address for which the
  // T3-rtx timer expired but did not fit in one MTU (rule E3 above) should be
  // marked for retransmission and sent as soon as cwnd allows (normally, when a
  // SACK arrives)."
  int count = 0;
  for (auto& elem : outstanding_data_) {
    UnwrappedTSN tsn = elem.first;
    TxData& item = elem.second;
    if (item.state() == State::kInFlight || item.state() == State::kNacked) {
      RTC_DLOG(LS_VERBOSE) << log_prefix_ << "Chunk " << *tsn.Wrap()
                           << " will be retransmitted due to T3-RTX";
      item.SetState(State::kToBeRetransmitted);
      ++count;
    }
  }

  // Marking some packets as retransmitted changes outstanding bytes.
  RecalculateOutstandingBytes();

  // https://tools.ietf.org/html/rfc4960#section-6.3.3
  // "Start the retransmission timer T3-rtx on the destination address
  // to which the retransmission is sent, if rule R1 above indicates to do so."

  // Already done by the Timer implementation.

  RTC_DLOG(LS_INFO) << log_prefix_ << "t3-rtx expired. new cwnd=" << cwnd_
                    << " (" << old_cwnd << "), ssthresh=" << ssthresh_
                    << ", rtx-packets=" << count << ", outstanding_bytes "
                    << outstanding_bytes_ << " (" << old_outstanding_bytes
                    << ")";
}

std::vector<std::pair<TSN, Data>>
RetransmissionQueue::GetChunksToBeRetransmitted(size_t max_size) {
  std::vector<std::pair<TSN, Data>> result;
  for (auto& elem : outstanding_data_) {
    UnwrappedTSN tsn = elem.first;
    TxData& item = elem.second;

    size_t serialized_size = GetSerializedChunkSize(item.data());
    if (item.state() == State::kToBeRetransmitted &&
        serialized_size <= max_size) {
      item.Retransmit();
      result.emplace_back(tsn.Wrap(), item.data().Clone());
      max_size -= serialized_size;
    }
    // No point in continuing if the packet is full.
    if (max_size <= data_chunk_header_size_) {
      break;
    }
  }
  // As some chunks may have switched state, that needs to be reflected here.
  if (!result.empty()) {
    RecalculateOutstandingBytes();
  }
  return result;
}

std::vector<std::pair<TSN, Data>> RetransmissionQueue::GetChunksToSend(
    TimeMs now,
    size_t bytes_remaining_in_packet) {
  // Chunks are always padded to even divisible by four.
  RTC_DCHECK(IsDivisibleBy4(bytes_remaining_in_packet));

  std::vector<std::pair<TSN, Data>> to_be_sent;
  size_t old_outstanding_bytes = outstanding_bytes_;
  size_t old_rwnd = rwnd_;
  if (is_in_fast_retransmit()) {
    // https://tools.ietf.org/html/rfc4960#section-7.2.4
    // "Determine how many of the earliest (i.e., lowest TSN) DATA chunks
    // marked for retransmission will fit into a single packet ... Retransmit
    // those K DATA chunks in a single packet.  When a Fast Retransmit is being
    // performed, the sender SHOULD ignore the value of cwnd and SHOULD NOT
    // delay retransmission for this single packet."
    is_in_fast_retransmit_ = false;
    to_be_sent = GetChunksToBeRetransmitted(bytes_remaining_in_packet);
    size_t to_be_sent_bytes = absl::c_accumulate(
        to_be_sent, 0, [&](size_t r, const std::pair<TSN, Data>& d) {
          return r + GetSerializedChunkSize(d.second);
        });
    RTC_DLOG(LS_VERBOSE) << log_prefix_ << "fast-retransmit: sending "
                         << to_be_sent.size() << " chunks, " << to_be_sent_bytes
                         << " bytes";
  } else {
    // Normal sending. Calculate the bandwidth budget (how many bytes that is
    // allowed to be sent), and fill that up first with chunks that are
    // scheduled to be retransmitted. If there is still budget, send new chunks
    // (which will have their TSN assigned here.)
    size_t remaining_cwnd_bytes =
        outstanding_bytes_ >= cwnd_ ? 0 : cwnd_ - outstanding_bytes_;
    size_t max_bytes = RoundDownTo4(std::min(
        std::min(bytes_remaining_in_packet, rwnd()), remaining_cwnd_bytes));

    to_be_sent = GetChunksToBeRetransmitted(max_bytes);
    max_bytes -= absl::c_accumulate(
        to_be_sent, 0, [&](size_t r, const std::pair<TSN, Data>& d) {
          return r + GetSerializedChunkSize(d.second);
        });

    while (max_bytes > data_chunk_header_size_) {
      RTC_DCHECK(IsDivisibleBy4(max_bytes));
      absl::optional<SendQueue::DataToSend> chunk_opt =
          send_queue_.Produce(now, max_bytes - data_chunk_header_size_);
      if (!chunk_opt.has_value()) {
        on_send_queue_empty_();
        break;
      }

      UnwrappedTSN tsn = next_tsn_;
      next_tsn_.Increment();
      to_be_sent.emplace_back(tsn.Wrap(), chunk_opt->data.Clone());

      // All chunks are always padded to be even divisible by 4.
      size_t chunk_size = GetSerializedChunkSize(chunk_opt->data);
      max_bytes -= chunk_size;
      outstanding_bytes_ += chunk_size;
      rwnd_ -= chunk_size;
      outstanding_data_.emplace(
          tsn, RetransmissionQueue::TxData(std::move(chunk_opt->data),
                                           chunk_opt->max_retransmissions, now,
                                           chunk_opt->expires_at));
    }
  }

  if (!to_be_sent.empty()) {
    // https://tools.ietf.org/html/rfc4960#section-6.3.2
    // "Every time a DATA chunk is sent to any address (including a
    // retransmission), if the T3-rtx timer of that address is not running,
    // start it running so that it will expire after the RTO of that address."
    if (!t3_rtx_.is_running()) {
      t3_rtx_.Start();
    }
    RTC_DLOG(LS_VERBOSE) << log_prefix_ << "Sending TSN "
                         << StrJoin(to_be_sent, ",",
                                    [&](rtc::StringBuilder& sb,
                                        const std::pair<TSN, Data>& c) {
                                      sb << *c.first;
                                    })
                         << " - "
                         << absl::c_accumulate(
                                to_be_sent, 0,
                                [&](size_t r, const std::pair<TSN, Data>& d) {
                                  return r + GetSerializedChunkSize(d.second);
                                })
                         << " bytes. outstanding_bytes=" << outstanding_bytes_
                         << " (" << old_outstanding_bytes << "), cwnd=" << cwnd_
                         << ", rwnd=" << rwnd_ << " (" << old_rwnd << ")";
  }
  return to_be_sent;
}

std::vector<std::pair<TSN, RetransmissionQueue::State>>
RetransmissionQueue::GetChunkStatesForTesting() const {
  std::vector<std::pair<TSN, RetransmissionQueue::State>> states;
  states.emplace_back(last_cumulative_tsn_ack_.Wrap(), State::kAcked);
  for (const auto& elem : outstanding_data_) {
    states.emplace_back(elem.first.Wrap(), elem.second.state());
  }
  return states;
}

bool RetransmissionQueue::ShouldSendForwardTsn(TimeMs now) {
  if (!partial_reliability_) {
    return false;
  }
  ExpireChunks(now);
  if (!outstanding_data_.empty()) {
    auto it = outstanding_data_.begin();
    return it->first == last_cumulative_tsn_ack_.next_value() &&
           it->second.state() == State::kAbandoned;
  }
  return false;
}

void RetransmissionQueue::TxData::Nack() {
  ++nack_count_;
  if (nack_count_ >= kNumberOfNacksForRetransmission) {
    state_ = State::kToBeRetransmitted;
  } else {
    state_ = State::kNacked;
  }
}

void RetransmissionQueue::TxData::Retransmit() {
  state_ = State::kInFlight;
  nack_count_ = 0;
  ++num_retransmissions_;
}

bool RetransmissionQueue::TxData::has_expired(TimeMs now) const {
  if (state_ != State::kAcked && state_ != State::kAbandoned) {
    if (max_retransmissions_.has_value() &&
        num_retransmissions_ >= *max_retransmissions_) {
      return true;
    } else if (expires_at_.has_value() && *expires_at_ <= now) {
      return true;
    }
  }
  return false;
}

void RetransmissionQueue::ExpireChunks(TimeMs now) {
  for (const auto& elem : outstanding_data_) {
    UnwrappedTSN tsn = elem.first;
    const TxData& item = elem.second;

    // Chunks that are in-flight (possibly lost?), nacked or to be retransmitted
    // can be expired easily. There is always a risk that a message is expired
    // that was already received by the peer, but for which there haven't been
    // a SACK received. But that's acceptable, and handled.
    if (item.has_expired(now)) {
      RTC_DLOG(LS_VERBOSE) << log_prefix_ << "Marking chunk " << *tsn.Wrap()
                           << " and message " << *item.data().message_id
                           << " as expired";
      ExpireAllFor(item);
    }
  }
}

void RetransmissionQueue::ExpireAllFor(
    const RetransmissionQueue::TxData& item) {
  // Erase all remaining chunks from the producer, if any.
  send_queue_.Discard(item.data().is_unordered, item.data().stream_id,
                      item.data().message_id);
  for (auto& elem : outstanding_data_) {
    UnwrappedTSN tsn = elem.first;
    TxData& other = elem.second;

    if (other.state() != State::kAbandoned &&
        other.data().stream_id == item.data().stream_id &&
        other.data().is_unordered == item.data().is_unordered &&
        other.data().message_id == item.data().message_id) {
      RTC_DLOG(LS_VERBOSE) << log_prefix_ << "Marking chunk " << *tsn.Wrap()
                           << " as abandoned";
      other.SetState(State::kAbandoned);
    }
  }
}

ForwardTsnChunk RetransmissionQueue::CreateForwardTsn() const {
  std::unordered_map<StreamID, SSN, StreamID::Hasher>
      skipped_per_ordered_stream;
  UnwrappedTSN new_cumulative_ack = last_cumulative_tsn_ack_;

  for (const auto& elem : outstanding_data_) {
    UnwrappedTSN tsn = elem.first;
    const TxData& item = elem.second;

    if ((tsn != new_cumulative_ack.next_value()) ||
        item.state() != State::kAbandoned) {
      break;
    }
    new_cumulative_ack = tsn;
    if (!item.data().is_unordered &&
        item.data().ssn > skipped_per_ordered_stream[item.data().stream_id]) {
      skipped_per_ordered_stream[item.data().stream_id] = item.data().ssn;
    }
  }

  std::vector<ForwardTsnChunk::SkippedStream> skipped_streams;
  skipped_streams.reserve(skipped_per_ordered_stream.size());
  for (const auto& elem : skipped_per_ordered_stream) {
    skipped_streams.emplace_back(elem.first, elem.second);
  }
  return ForwardTsnChunk(new_cumulative_ack.Wrap(), std::move(skipped_streams));
}

IForwardTsnChunk RetransmissionQueue::CreateIForwardTsn() const {
  std::unordered_map<std::pair<IsUnordered, StreamID>, MID, UnorderedStreamHash>
      skipped_per_stream;
  UnwrappedTSN new_cumulative_ack = last_cumulative_tsn_ack_;

  for (const auto& elem : outstanding_data_) {
    UnwrappedTSN tsn = elem.first;
    const TxData& item = elem.second;

    if ((tsn != new_cumulative_ack.next_value()) ||
        item.state() != State::kAbandoned) {
      break;
    }
    new_cumulative_ack = tsn;
    std::pair<IsUnordered, StreamID> stream_id =
        std::make_pair(item.data().is_unordered, item.data().stream_id);

    if (item.data().message_id > skipped_per_stream[stream_id]) {
      skipped_per_stream[stream_id] = item.data().message_id;
    }
  }

  std::vector<IForwardTsnChunk::SkippedStream> skipped_streams;
  skipped_streams.reserve(skipped_per_stream.size());
  for (const auto& elem : skipped_per_stream) {
    const std::pair<IsUnordered, StreamID>& stream = elem.first;
    MID message_id = elem.second;
    skipped_streams.emplace_back(stream.first, stream.second, message_id);
  }

  return IForwardTsnChunk(new_cumulative_ack.Wrap(),
                          std::move(skipped_streams));
}

void RetransmissionQueue::PrepareResetStreams(
    rtc::ArrayView<const StreamID> streams) {
  // TODO(boivie): These calls are now only affecting the send queue. The
  // packet buffer can also change behavior - for example draining the chunk
  // producer and eagerly assign TSNs so that an "Outgoing SSN Reset Request"
  // can be sent quickly, with a known `sender_last_assigned_tsn`.
  send_queue_.PrepareResetStreams(streams);
}
bool RetransmissionQueue::CanResetStreams() const {
  return send_queue_.CanResetStreams();
}
void RetransmissionQueue::CommitResetStreams() {
  send_queue_.CommitResetStreams();
}
void RetransmissionQueue::RollbackResetStreams() {
  send_queue_.RollbackResetStreams();
}

}  // namespace dcsctp
