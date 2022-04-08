/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/rx/traditional_reassembly_streams.h"

#include <stddef.h>

#include <cstdint>
#include <functional>
#include <iterator>
#include <map>
#include <numeric>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/common/sequence_numbers.h"
#include "net/dcsctp/packet/chunk/forward_tsn_common.h"
#include "net/dcsctp/packet/data.h"
#include "net/dcsctp/public/dcsctp_message.h"
#include "rtc_base/logging.h"

namespace dcsctp {
namespace {

// Given a map (`chunks`) and an iterator to within that map (`iter`), this
// function will return an iterator to the first chunk in that message, which
// has the `is_beginning` flag set. If there are any gaps, or if the beginning
// can't be found, `absl::nullopt` is returned.
absl::optional<std::map<UnwrappedTSN, Data>::iterator> FindBeginning(
    const std::map<UnwrappedTSN, Data>& chunks,
    std::map<UnwrappedTSN, Data>::iterator iter) {
  UnwrappedTSN prev_tsn = iter->first;
  for (;;) {
    if (iter->second.is_beginning) {
      return iter;
    }
    if (iter == chunks.begin()) {
      return absl::nullopt;
    }
    --iter;
    if (iter->first.next_value() != prev_tsn) {
      return absl::nullopt;
    }
    prev_tsn = iter->first;
  }
}

// Given a map (`chunks`) and an iterator to within that map (`iter`), this
// function will return an iterator to the chunk after the last chunk in that
// message, which has the `is_end` flag set. If there are any gaps, or if the
// end can't be found, `absl::nullopt` is returned.
absl::optional<std::map<UnwrappedTSN, Data>::iterator> FindEnd(
    std::map<UnwrappedTSN, Data>& chunks,
    std::map<UnwrappedTSN, Data>::iterator iter) {
  UnwrappedTSN prev_tsn = iter->first;
  for (;;) {
    if (iter->second.is_end) {
      return ++iter;
    }
    ++iter;
    if (iter == chunks.end()) {
      return absl::nullopt;
    }
    if (iter->first != prev_tsn.next_value()) {
      return absl::nullopt;
    }
    prev_tsn = iter->first;
  }
}
}  // namespace

TraditionalReassemblyStreams::TraditionalReassemblyStreams(
    absl::string_view log_prefix,
    OnAssembledMessage on_assembled_message,
    const DcSctpSocketHandoverState* handover_state)
    : log_prefix_(log_prefix),
      on_assembled_message_(std::move(on_assembled_message)) {
  if (handover_state) {
    for (const DcSctpSocketHandoverState::OrderedStream& state_stream :
         handover_state->rx.ordered_streams) {
      ordered_streams_.emplace(
          std::piecewise_construct,
          std::forward_as_tuple(StreamID(state_stream.id)),
          std::forward_as_tuple(this, SSN(state_stream.next_ssn)));
    }
    for (const DcSctpSocketHandoverState::UnorderedStream& state_stream :
         handover_state->rx.unordered_streams) {
      unordered_streams_.emplace(
          std::piecewise_construct,
          std::forward_as_tuple(StreamID(state_stream.id)),
          std::forward_as_tuple(this));
    }
  }
}

int TraditionalReassemblyStreams::UnorderedStream::Add(UnwrappedTSN tsn,
                                                       Data data) {
  int queued_bytes = data.size();
  auto p = chunks_.emplace(tsn, std::move(data));
  if (!p.second /* !inserted */) {
    return 0;
  }

  queued_bytes -= TryToAssembleMessage(p.first);

  return queued_bytes;
}

size_t TraditionalReassemblyStreams::UnorderedStream::TryToAssembleMessage(
    ChunkMap::iterator iter) {
  // TODO(boivie): This method is O(N) with the number of fragments in a
  // message, which can be inefficient for very large values of N. This could be
  // optimized by e.g. only trying to assemble a message once _any_ beginning
  // and _any_ end has been found.
  absl::optional<ChunkMap::iterator> start = FindBeginning(chunks_, iter);
  if (!start.has_value()) {
    return 0;
  }
  absl::optional<ChunkMap::iterator> end = FindEnd(chunks_, iter);
  if (!end.has_value()) {
    return 0;
  }

  size_t bytes_assembled = AssembleMessage(*start, *end);
  chunks_.erase(*start, *end);
  return bytes_assembled;
}

size_t TraditionalReassemblyStreams::StreamBase::AssembleMessage(
    const ChunkMap::iterator start,
    const ChunkMap::iterator end) {
  size_t count = std::distance(start, end);

  if (count == 1) {
    // Fast path - zero-copy
    const Data& data = start->second;
    size_t payload_size = start->second.size();
    UnwrappedTSN tsns[1] = {start->first};
    DcSctpMessage message(data.stream_id, data.ppid, std::move(data.payload));
    parent_.on_assembled_message_(tsns, std::move(message));
    return payload_size;
  }

  // Slow path - will need to concatenate the payload.
  std::vector<UnwrappedTSN> tsns;
  std::vector<uint8_t> payload;

  size_t payload_size = std::accumulate(
      start, end, 0,
      [](size_t v, const auto& p) { return v + p.second.size(); });

  tsns.reserve(count);
  payload.reserve(payload_size);
  for (auto it = start; it != end; ++it) {
    const Data& data = it->second;
    tsns.push_back(it->first);
    payload.insert(payload.end(), data.payload.begin(), data.payload.end());
  }

  DcSctpMessage message(start->second.stream_id, start->second.ppid,
                        std::move(payload));
  parent_.on_assembled_message_(tsns, std::move(message));

  return payload_size;
}

size_t TraditionalReassemblyStreams::UnorderedStream::EraseTo(
    UnwrappedTSN tsn) {
  auto end_iter = chunks_.upper_bound(tsn);
  size_t removed_bytes = std::accumulate(
      chunks_.begin(), end_iter, 0,
      [](size_t r, const auto& p) { return r + p.second.size(); });

  chunks_.erase(chunks_.begin(), end_iter);
  return removed_bytes;
}

size_t TraditionalReassemblyStreams::OrderedStream::TryToAssembleMessage() {
  if (chunks_by_ssn_.empty() || chunks_by_ssn_.begin()->first != next_ssn_) {
    return 0;
  }

  ChunkMap& chunks = chunks_by_ssn_.begin()->second;

  if (!chunks.begin()->second.is_beginning || !chunks.rbegin()->second.is_end) {
    return 0;
  }

  uint32_t tsn_diff =
      UnwrappedTSN::Difference(chunks.rbegin()->first, chunks.begin()->first);
  if (tsn_diff != chunks.size() - 1) {
    return 0;
  }

  size_t assembled_bytes = AssembleMessage(chunks.begin(), chunks.end());
  chunks_by_ssn_.erase(chunks_by_ssn_.begin());
  next_ssn_.Increment();
  return assembled_bytes;
}

size_t TraditionalReassemblyStreams::OrderedStream::TryToAssembleMessages() {
  size_t assembled_bytes = 0;

  for (;;) {
    size_t assembled_bytes_this_iter = TryToAssembleMessage();
    if (assembled_bytes_this_iter == 0) {
      break;
    }
    assembled_bytes += assembled_bytes_this_iter;
  }
  return assembled_bytes;
}

int TraditionalReassemblyStreams::OrderedStream::Add(UnwrappedTSN tsn,
                                                     Data data) {
  int queued_bytes = data.size();

  UnwrappedSSN ssn = ssn_unwrapper_.Unwrap(data.ssn);
  auto p = chunks_by_ssn_[ssn].emplace(tsn, std::move(data));
  if (!p.second /* !inserted */) {
    return 0;
  }

  if (ssn == next_ssn_) {
    queued_bytes -= TryToAssembleMessages();
  }

  return queued_bytes;
}

size_t TraditionalReassemblyStreams::OrderedStream::EraseTo(SSN ssn) {
  UnwrappedSSN unwrapped_ssn = ssn_unwrapper_.Unwrap(ssn);

  auto end_iter = chunks_by_ssn_.upper_bound(unwrapped_ssn);
  size_t removed_bytes = std::accumulate(
      chunks_by_ssn_.begin(), end_iter, 0, [](size_t r1, const auto& p) {
        return r1 +
               absl::c_accumulate(p.second, 0, [](size_t r2, const auto& q) {
                 return r2 + q.second.size();
               });
      });
  chunks_by_ssn_.erase(chunks_by_ssn_.begin(), end_iter);

  if (unwrapped_ssn >= next_ssn_) {
    unwrapped_ssn.Increment();
    next_ssn_ = unwrapped_ssn;
  }

  removed_bytes += TryToAssembleMessages();
  return removed_bytes;
}

int TraditionalReassemblyStreams::Add(UnwrappedTSN tsn, Data data) {
  if (data.is_unordered) {
    auto it = unordered_streams_.emplace(data.stream_id, this).first;
    return it->second.Add(tsn, std::move(data));
  }

  auto it = ordered_streams_.emplace(data.stream_id, this).first;
  return it->second.Add(tsn, std::move(data));
}

size_t TraditionalReassemblyStreams::HandleForwardTsn(
    UnwrappedTSN new_cumulative_ack_tsn,
    rtc::ArrayView<const AnyForwardTsnChunk::SkippedStream> skipped_streams) {
  size_t bytes_removed = 0;
  // The `skipped_streams` only cover ordered messages - need to
  // iterate all unordered streams manually to remove those chunks.
  for (auto& entry : unordered_streams_) {
    bytes_removed += entry.second.EraseTo(new_cumulative_ack_tsn);
  }

  for (const auto& skipped_stream : skipped_streams) {
    auto it = ordered_streams_.find(skipped_stream.stream_id);
    if (it != ordered_streams_.end()) {
      bytes_removed += it->second.EraseTo(skipped_stream.ssn);
    }
  }

  return bytes_removed;
}

void TraditionalReassemblyStreams::ResetStreams(
    rtc::ArrayView<const StreamID> stream_ids) {
  if (stream_ids.empty()) {
    for (auto& entry : ordered_streams_) {
      const StreamID& stream_id = entry.first;
      OrderedStream& stream = entry.second;
      RTC_DLOG(LS_VERBOSE) << log_prefix_
                           << "Resetting implicit stream_id=" << *stream_id;
      stream.Reset();
    }
  } else {
    for (StreamID stream_id : stream_ids) {
      auto it = ordered_streams_.find(stream_id);
      if (it != ordered_streams_.end()) {
        RTC_DLOG(LS_VERBOSE)
            << log_prefix_ << "Resetting explicit stream_id=" << *stream_id;
        it->second.Reset();
      }
    }
  }
}

HandoverReadinessStatus TraditionalReassemblyStreams::GetHandoverReadiness()
    const {
  HandoverReadinessStatus status;
  for (const auto& entry : ordered_streams_) {
    if (entry.second.has_unassembled_chunks()) {
      status.Add(HandoverUnreadinessReason::kOrderedStreamHasUnassembledChunks);
      break;
    }
  }
  for (const auto& entry : unordered_streams_) {
    if (entry.second.has_unassembled_chunks()) {
      status.Add(
          HandoverUnreadinessReason::kUnorderedStreamHasUnassembledChunks);
      break;
    }
  }
  return status;
}

void TraditionalReassemblyStreams::AddHandoverState(
    DcSctpSocketHandoverState& state) {
  for (const auto& entry : ordered_streams_) {
    DcSctpSocketHandoverState::OrderedStream state_stream;
    state_stream.id = entry.first.value();
    state_stream.next_ssn = entry.second.next_ssn().value();
    state.rx.ordered_streams.push_back(std::move(state_stream));
  }
  for (const auto& entry : unordered_streams_) {
    DcSctpSocketHandoverState::UnorderedStream state_stream;
    state_stream.id = entry.first.value();
    state.rx.unordered_streams.push_back(std::move(state_stream));
  }
}

}  // namespace dcsctp
