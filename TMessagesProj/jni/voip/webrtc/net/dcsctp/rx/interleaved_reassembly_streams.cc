/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/rx/interleaved_reassembly_streams.h"

#include <stddef.h>

#include <cstdint>
#include <functional>
#include <iterator>
#include <map>
#include <numeric>
#include <unordered_map>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "api/array_view.h"
#include "net/dcsctp/common/sequence_numbers.h"
#include "net/dcsctp/packet/chunk/forward_tsn_common.h"
#include "net/dcsctp/packet/data.h"
#include "net/dcsctp/public/types.h"
#include "rtc_base/logging.h"

namespace dcsctp {

InterleavedReassemblyStreams::InterleavedReassemblyStreams(
    absl::string_view log_prefix,
    OnAssembledMessage on_assembled_message)
    : log_prefix_(log_prefix), on_assembled_message_(on_assembled_message) {}

size_t InterleavedReassemblyStreams::Stream::TryToAssembleMessage(
    UnwrappedMID mid) {
  std::map<UnwrappedMID, ChunkMap>::const_iterator it =
      chunks_by_mid_.find(mid);
  if (it == chunks_by_mid_.end()) {
    RTC_DLOG(LS_VERBOSE) << parent_.log_prefix_ << "TryToAssembleMessage "
                         << *mid.Wrap() << " - no chunks";
    return 0;
  }
  const ChunkMap& chunks = it->second;
  if (!chunks.begin()->second.second.is_beginning ||
      !chunks.rbegin()->second.second.is_end) {
    RTC_DLOG(LS_VERBOSE) << parent_.log_prefix_ << "TryToAssembleMessage "
                         << *mid.Wrap() << "- missing beginning or end";
    return 0;
  }
  int64_t fsn_diff = *chunks.rbegin()->first - *chunks.begin()->first;
  if (fsn_diff != (static_cast<int64_t>(chunks.size()) - 1)) {
    RTC_DLOG(LS_VERBOSE) << parent_.log_prefix_ << "TryToAssembleMessage "
                         << *mid.Wrap() << "- not all chunks exist (have "
                         << chunks.size() << ", expect " << (fsn_diff + 1)
                         << ")";
    return 0;
  }

  size_t removed_bytes = AssembleMessage(chunks);
  RTC_DLOG(LS_VERBOSE) << parent_.log_prefix_ << "TryToAssembleMessage "
                       << *mid.Wrap() << " - succeeded and removed "
                       << removed_bytes;

  chunks_by_mid_.erase(mid);
  return removed_bytes;
}

size_t InterleavedReassemblyStreams::Stream::AssembleMessage(
    const ChunkMap& tsn_chunks) {
  size_t count = tsn_chunks.size();
  if (count == 1) {
    // Fast path - zero-copy
    const Data& data = tsn_chunks.begin()->second.second;
    size_t payload_size = data.size();
    UnwrappedTSN tsns[1] = {tsn_chunks.begin()->second.first};
    DcSctpMessage message(data.stream_id, data.ppid, std::move(data.payload));
    parent_.on_assembled_message_(tsns, std::move(message));
    return payload_size;
  }

  // Slow path - will need to concatenate the payload.
  std::vector<UnwrappedTSN> tsns;
  tsns.reserve(count);

  std::vector<uint8_t> payload;
  size_t payload_size = absl::c_accumulate(
      tsn_chunks, 0,
      [](size_t v, const auto& p) { return v + p.second.second.size(); });
  payload.reserve(payload_size);

  for (auto& item : tsn_chunks) {
    const UnwrappedTSN tsn = item.second.first;
    const Data& data = item.second.second;
    tsns.push_back(tsn);
    payload.insert(payload.end(), data.payload.begin(), data.payload.end());
  }

  const Data& data = tsn_chunks.begin()->second.second;

  DcSctpMessage message(data.stream_id, data.ppid, std::move(payload));
  parent_.on_assembled_message_(tsns, std::move(message));
  return payload_size;
}

size_t InterleavedReassemblyStreams::Stream::EraseTo(MID message_id) {
  UnwrappedMID unwrapped_mid = mid_unwrapper_.Unwrap(message_id);

  size_t removed_bytes = 0;
  auto it = chunks_by_mid_.begin();
  while (it != chunks_by_mid_.end() && it->first <= unwrapped_mid) {
    removed_bytes += absl::c_accumulate(
        it->second, 0,
        [](size_t r2, const auto& q) { return r2 + q.second.second.size(); });
    it = chunks_by_mid_.erase(it);
  }

  if (!stream_id_.unordered) {
    // For ordered streams, erasing a message might suddenly unblock that queue
    // and allow it to deliver any following received messages.
    if (unwrapped_mid >= next_mid_) {
      next_mid_ = unwrapped_mid.next_value();
    }

    removed_bytes += TryToAssembleMessages();
  }

  return removed_bytes;
}

int InterleavedReassemblyStreams::Stream::Add(UnwrappedTSN tsn, Data data) {
  RTC_DCHECK_EQ(*data.is_unordered, *stream_id_.unordered);
  RTC_DCHECK_EQ(*data.stream_id, *stream_id_.stream_id);
  int queued_bytes = data.size();
  UnwrappedMID mid = mid_unwrapper_.Unwrap(data.message_id);
  FSN fsn = data.fsn;
  auto [unused, inserted] =
      chunks_by_mid_[mid].emplace(fsn, std::make_pair(tsn, std::move(data)));
  if (!inserted) {
    return 0;
  }

  if (stream_id_.unordered) {
    queued_bytes -= TryToAssembleMessage(mid);
  } else {
    if (mid == next_mid_) {
      queued_bytes -= TryToAssembleMessages();
    }
  }

  return queued_bytes;
}

size_t InterleavedReassemblyStreams::Stream::TryToAssembleMessages() {
  size_t removed_bytes = 0;

  for (;;) {
    size_t removed_bytes_this_iter = TryToAssembleMessage(next_mid_);
    if (removed_bytes_this_iter == 0) {
      break;
    }

    removed_bytes += removed_bytes_this_iter;
    next_mid_.Increment();
  }
  return removed_bytes;
}

void InterleavedReassemblyStreams::Stream::AddHandoverState(
    DcSctpSocketHandoverState& state) const {
  if (stream_id_.unordered) {
    DcSctpSocketHandoverState::UnorderedStream state_stream;
    state_stream.id = stream_id_.stream_id.value();
    state.rx.unordered_streams.push_back(std::move(state_stream));
  } else {
    DcSctpSocketHandoverState::OrderedStream state_stream;
    state_stream.id = stream_id_.stream_id.value();
    state_stream.next_ssn = next_mid_.Wrap().value();
    state.rx.ordered_streams.push_back(std::move(state_stream));
  }
}

InterleavedReassemblyStreams::Stream&
InterleavedReassemblyStreams::GetOrCreateStream(const FullStreamId& stream_id) {
  auto it = streams_.find(stream_id);
  if (it == streams_.end()) {
    it =
        streams_
            .emplace(std::piecewise_construct, std::forward_as_tuple(stream_id),
                     std::forward_as_tuple(stream_id, this))
            .first;
  }
  return it->second;
}

int InterleavedReassemblyStreams::Add(UnwrappedTSN tsn, Data data) {
  return GetOrCreateStream(FullStreamId(data.is_unordered, data.stream_id))
      .Add(tsn, std::move(data));
}

size_t InterleavedReassemblyStreams::HandleForwardTsn(
    UnwrappedTSN new_cumulative_ack_tsn,
    rtc::ArrayView<const AnyForwardTsnChunk::SkippedStream> skipped_streams) {
  size_t removed_bytes = 0;
  for (const auto& skipped : skipped_streams) {
    removed_bytes +=
        GetOrCreateStream(FullStreamId(skipped.unordered, skipped.stream_id))
            .EraseTo(skipped.message_id);
  }
  return removed_bytes;
}

void InterleavedReassemblyStreams::ResetStreams(
    rtc::ArrayView<const StreamID> stream_ids) {
  if (stream_ids.empty()) {
    for (auto& entry : streams_) {
      entry.second.Reset();
    }
  } else {
    for (StreamID stream_id : stream_ids) {
      GetOrCreateStream(FullStreamId(IsUnordered(true), stream_id)).Reset();
      GetOrCreateStream(FullStreamId(IsUnordered(false), stream_id)).Reset();
    }
  }
}

HandoverReadinessStatus InterleavedReassemblyStreams::GetHandoverReadiness()
    const {
  HandoverReadinessStatus status;
  for (const auto& [stream_id, stream] : streams_) {
    if (stream.has_unassembled_chunks()) {
      status.Add(
          stream_id.unordered
              ? HandoverUnreadinessReason::kUnorderedStreamHasUnassembledChunks
              : HandoverUnreadinessReason::kOrderedStreamHasUnassembledChunks);
      break;
    }
  }
  return status;
}

void InterleavedReassemblyStreams::AddHandoverState(
    DcSctpSocketHandoverState& state) {
  for (const auto& [unused, stream] : streams_) {
    stream.AddHandoverState(state);
  }
}

void InterleavedReassemblyStreams::RestoreFromState(
    const DcSctpSocketHandoverState& state) {
  // Validate that the component is in pristine state.
  RTC_DCHECK(streams_.empty());

  for (const DcSctpSocketHandoverState::OrderedStream& state :
       state.rx.ordered_streams) {
    FullStreamId stream_id(IsUnordered(false), StreamID(state.id));
    streams_.emplace(
        std::piecewise_construct, std::forward_as_tuple(stream_id),
        std::forward_as_tuple(stream_id, this, MID(state.next_ssn)));
  }
  for (const DcSctpSocketHandoverState::UnorderedStream& state :
       state.rx.unordered_streams) {
    FullStreamId stream_id(IsUnordered(true), StreamID(state.id));
    streams_.emplace(std::piecewise_construct, std::forward_as_tuple(stream_id),
                     std::forward_as_tuple(stream_id, this));
  }
}

}  // namespace dcsctp
