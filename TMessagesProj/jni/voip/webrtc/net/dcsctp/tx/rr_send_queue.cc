/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/tx/rr_send_queue.h"

#include <cstdint>
#include <deque>
#include <limits>
#include <map>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/packet/data.h"
#include "net/dcsctp/public/dcsctp_message.h"
#include "net/dcsctp/public/dcsctp_socket.h"
#include "net/dcsctp/public/types.h"
#include "net/dcsctp/tx/send_queue.h"
#include "rtc_base/logging.h"

namespace dcsctp {

RRSendQueue::RRSendQueue(absl::string_view log_prefix,
                         size_t buffer_size,
                         std::function<void(StreamID)> on_buffered_amount_low,
                         size_t total_buffered_amount_low_threshold,
                         std::function<void()> on_total_buffered_amount_low,
                         const DcSctpSocketHandoverState* handover_state)
    : log_prefix_(std::string(log_prefix) + "fcfs: "),
      buffer_size_(buffer_size),
      on_buffered_amount_low_(std::move(on_buffered_amount_low)),
      total_buffered_amount_(std::move(on_total_buffered_amount_low)) {
  total_buffered_amount_.SetLowThreshold(total_buffered_amount_low_threshold);
}

bool RRSendQueue::OutgoingStream::HasDataToSend(TimeMs now) {
  while (!items_.empty()) {
    RRSendQueue::OutgoingStream::Item& item = items_.front();
    if (item.message_id.has_value()) {
      // Already partially sent messages can always continue to be sent.
      return true;
    }

    // Message has expired. Remove it and inspect the next one.
    if (item.expires_at <= now) {
      buffered_amount_.Decrease(item.remaining_size);
      total_buffered_amount_.Decrease(item.remaining_size);
      items_.pop_front();
      RTC_DCHECK(IsConsistent());
      continue;
    }

    if (is_paused_) {
      // The stream has paused (and there is no partially sent message).
      return false;
    }
    return true;
  }
  return false;
}

void RRSendQueue::OutgoingStream::AddHandoverState(
    DcSctpSocketHandoverState::OutgoingStream& state) const {
  state.next_ssn = next_ssn_.value();
  state.next_ordered_mid = next_ordered_mid_.value();
  state.next_unordered_mid = next_unordered_mid_.value();
}

bool RRSendQueue::IsConsistent() const {
  size_t total_buffered_amount = 0;
  for (const auto& [unused, stream] : streams_) {
    total_buffered_amount += stream.buffered_amount().value();
  }

  if (previous_message_has_ended_) {
    auto it = streams_.find(current_stream_id_);
    if (it != streams_.end() && it->second.has_partially_sent_message()) {
      RTC_DLOG(LS_ERROR)
          << "Previous message has ended, but still partial message in stream";
      return false;
    }
  } else {
    auto it = streams_.find(current_stream_id_);
    if (it == streams_.end() || !it->second.has_partially_sent_message()) {
      RTC_DLOG(LS_ERROR)
          << "Previous message has NOT ended, but there is no partial message";
      return false;
    }
  }

  return total_buffered_amount == total_buffered_amount_.value();
}

bool RRSendQueue::OutgoingStream::IsConsistent() const {
  size_t bytes = 0;
  for (const auto& item : items_) {
    bytes += item.remaining_size;
  }
  return bytes == buffered_amount_.value();
}

void RRSendQueue::ThresholdWatcher::Decrease(size_t bytes) {
  RTC_DCHECK(bytes <= value_);
  size_t old_value = value_;
  value_ -= bytes;

  if (old_value > low_threshold_ && value_ <= low_threshold_) {
    on_threshold_reached_();
  }
}

void RRSendQueue::ThresholdWatcher::SetLowThreshold(size_t low_threshold) {
  // Betting on https://github.com/w3c/webrtc-pc/issues/2654 being accepted.
  if (low_threshold_ < value_ && low_threshold >= value_) {
    on_threshold_reached_();
  }
  low_threshold_ = low_threshold;
}

void RRSendQueue::OutgoingStream::Add(DcSctpMessage message,
                                      TimeMs expires_at,
                                      const SendOptions& send_options) {
  buffered_amount_.Increase(message.payload().size());
  total_buffered_amount_.Increase(message.payload().size());
  items_.emplace_back(std::move(message), expires_at, send_options);

  RTC_DCHECK(IsConsistent());
}

absl::optional<SendQueue::DataToSend> RRSendQueue::OutgoingStream::Produce(
    TimeMs now,
    size_t max_size) {
  RTC_DCHECK(!items_.empty());

  Item* item = &items_.front();
  DcSctpMessage& message = item->message;

  if (item->remaining_size > max_size && max_size < kMinimumFragmentedPayload) {
    RTC_DCHECK(IsConsistent());
    return absl::nullopt;
  }

  // Allocate Message ID and SSN when the first fragment is sent.
  if (!item->message_id.has_value()) {
    MID& mid =
        item->send_options.unordered ? next_unordered_mid_ : next_ordered_mid_;
    item->message_id = mid;
    mid = MID(*mid + 1);
  }
  if (!item->send_options.unordered && !item->ssn.has_value()) {
    item->ssn = next_ssn_;
    next_ssn_ = SSN(*next_ssn_ + 1);
  }

  // Grab the next `max_size` fragment from this message and calculate flags.
  rtc::ArrayView<const uint8_t> chunk_payload =
      item->message.payload().subview(item->remaining_offset, max_size);
  rtc::ArrayView<const uint8_t> message_payload = message.payload();
  Data::IsBeginning is_beginning(chunk_payload.data() ==
                                 message_payload.data());
  Data::IsEnd is_end((chunk_payload.data() + chunk_payload.size()) ==
                     (message_payload.data() + message_payload.size()));

  StreamID stream_id = message.stream_id();
  PPID ppid = message.ppid();

  // Zero-copy the payload if the message fits in a single chunk.
  std::vector<uint8_t> payload =
      is_beginning && is_end
          ? std::move(message).ReleasePayload()
          : std::vector<uint8_t>(chunk_payload.begin(), chunk_payload.end());

  FSN fsn(item->current_fsn);
  item->current_fsn = FSN(*item->current_fsn + 1);
  buffered_amount_.Decrease(payload.size());
  total_buffered_amount_.Decrease(payload.size());

  SendQueue::DataToSend chunk(Data(stream_id, item->ssn.value_or(SSN(0)),
                                   item->message_id.value(), fsn, ppid,
                                   std::move(payload), is_beginning, is_end,
                                   item->send_options.unordered));
  if (item->send_options.max_retransmissions.has_value() &&
      *item->send_options.max_retransmissions >=
          std::numeric_limits<MaxRetransmits::UnderlyingType>::min() &&
      *item->send_options.max_retransmissions <=
          std::numeric_limits<MaxRetransmits::UnderlyingType>::max()) {
    chunk.max_retransmissions =
        MaxRetransmits(*item->send_options.max_retransmissions);
  }
  chunk.expires_at = item->expires_at;

  if (is_end) {
    // The entire message has been sent, and its last data copied to `chunk`, so
    // it can safely be discarded.
    items_.pop_front();
  } else {
    item->remaining_offset += chunk_payload.size();
    item->remaining_size -= chunk_payload.size();
    RTC_DCHECK(item->remaining_offset + item->remaining_size ==
               item->message.payload().size());
    RTC_DCHECK(item->remaining_size > 0);
  }
  RTC_DCHECK(IsConsistent());
  return chunk;
}

bool RRSendQueue::OutgoingStream::Discard(IsUnordered unordered,
                                          MID message_id) {
  bool result = false;
  if (!items_.empty()) {
    Item& item = items_.front();
    if (item.send_options.unordered == unordered &&
        item.message_id.has_value() && *item.message_id == message_id) {
      buffered_amount_.Decrease(item.remaining_size);
      total_buffered_amount_.Decrease(item.remaining_size);
      items_.pop_front();
      // As the item still existed, it had unsent data.
      result = true;
    }
  }
  RTC_DCHECK(IsConsistent());
  return result;
}

void RRSendQueue::OutgoingStream::Pause() {
  is_paused_ = true;

  // A stream is paused when it's about to be reset. In this implementation,
  // it will throw away all non-partially send messages. This is subject to
  // change. It will however not discard any partially sent messages - only
  // whole messages. Partially delivered messages (at the time of receiving a
  // Stream Reset command) will always deliver all the fragments before
  // actually resetting the stream.
  for (auto it = items_.begin(); it != items_.end();) {
    if (it->remaining_offset == 0) {
      buffered_amount_.Decrease(it->remaining_size);
      total_buffered_amount_.Decrease(it->remaining_size);
      it = items_.erase(it);
    } else {
      ++it;
    }
  }
  RTC_DCHECK(IsConsistent());
}

void RRSendQueue::OutgoingStream::Reset() {
  if (!items_.empty()) {
    // If this message has been partially sent, reset it so that it will be
    // re-sent.
    auto& item = items_.front();
    buffered_amount_.Increase(item.message.payload().size() -
                              item.remaining_size);
    total_buffered_amount_.Increase(item.message.payload().size() -
                                    item.remaining_size);
    item.remaining_offset = 0;
    item.remaining_size = item.message.payload().size();
    item.message_id = absl::nullopt;
    item.ssn = absl::nullopt;
    item.current_fsn = FSN(0);
  }
  is_paused_ = false;
  next_ordered_mid_ = MID(0);
  next_unordered_mid_ = MID(0);
  next_ssn_ = SSN(0);
  RTC_DCHECK(IsConsistent());
}

bool RRSendQueue::OutgoingStream::has_partially_sent_message() const {
  if (items_.empty()) {
    return false;
  }
  return items_.front().message_id.has_value();
}

void RRSendQueue::Add(TimeMs now,
                      DcSctpMessage message,
                      const SendOptions& send_options) {
  RTC_DCHECK(!message.payload().empty());
  // Any limited lifetime should start counting from now - when the message
  // has been added to the queue.
  TimeMs expires_at = TimeMs::InfiniteFuture();
  if (send_options.lifetime.has_value()) {
    // `expires_at` is the time when it expires. Which is slightly larger than
    // the message's lifetime, as the message is alive during its entire
    // lifetime (which may be zero).
    expires_at = now + *send_options.lifetime + DurationMs(1);
  }
  GetOrCreateStreamInfo(message.stream_id())
      .Add(std::move(message), expires_at, send_options);
  RTC_DCHECK(IsConsistent());
}

bool RRSendQueue::IsFull() const {
  return total_buffered_amount() >= buffer_size_;
}

bool RRSendQueue::IsEmpty() const {
  return total_buffered_amount() == 0;
}

std::map<StreamID, RRSendQueue::OutgoingStream>::iterator
RRSendQueue::GetNextStream(TimeMs now) {
  auto start_it = streams_.lower_bound(StreamID(*current_stream_id_ + 1));

  for (auto it = start_it; it != streams_.end(); ++it) {
    if (it->second.HasDataToSend(now)) {
      current_stream_id_ = it->first;
      return it;
    }
  }

  for (auto it = streams_.begin(); it != start_it; ++it) {
    if (it->second.HasDataToSend(now)) {
      current_stream_id_ = it->first;
      return it;
    }
  }
  return streams_.end();
}

absl::optional<SendQueue::DataToSend> RRSendQueue::Produce(TimeMs now,
                                                           size_t max_size) {
  std::map<StreamID, RRSendQueue::OutgoingStream>::iterator stream_it;

  if (previous_message_has_ended_) {
    // Previous message has ended. Round-robin to a different stream, if there
    // even is one with data to send.
    stream_it = GetNextStream(now);
    if (stream_it == streams_.end()) {
      RTC_DLOG(LS_VERBOSE)
          << log_prefix_
          << "There is no stream with data; Can't produce any data.";
      return absl::nullopt;
    }
  } else {
    // The previous message has not ended; Continue from the current stream.
    stream_it = streams_.find(current_stream_id_);
    RTC_DCHECK(stream_it != streams_.end());
  }

  absl::optional<DataToSend> data = stream_it->second.Produce(now, max_size);
  if (data.has_value()) {
    RTC_DLOG(LS_VERBOSE) << log_prefix_ << "Producing DATA, type="
                         << (data->data.is_unordered ? "unordered" : "ordered")
                         << "::"
                         << (*data->data.is_beginning && *data->data.is_end
                                 ? "complete"
                                 : *data->data.is_beginning
                                       ? "first"
                                       : *data->data.is_end ? "last" : "middle")
                         << ", stream_id=" << *stream_it->first
                         << ", ppid=" << *data->data.ppid
                         << ", length=" << data->data.payload.size();

    previous_message_has_ended_ = *data->data.is_end;
  }

  RTC_DCHECK(IsConsistent());
  return data;
}

bool RRSendQueue::Discard(IsUnordered unordered,
                          StreamID stream_id,
                          MID message_id) {
  bool has_discarded =
      GetOrCreateStreamInfo(stream_id).Discard(unordered, message_id);
  if (has_discarded) {
    // Only partially sent messages are discarded, so if a message was
    // discarded, then it was the currently sent message.
    previous_message_has_ended_ = true;
  }

  return has_discarded;
}

void RRSendQueue::PrepareResetStreams(rtc::ArrayView<const StreamID> streams) {
  for (StreamID stream_id : streams) {
    GetOrCreateStreamInfo(stream_id).Pause();
  }
  RTC_DCHECK(IsConsistent());
}

bool RRSendQueue::CanResetStreams() const {
  // Streams can be reset if those streams that are paused don't have any
  // messages that are partially sent.
  for (auto& [unused, stream] : streams_) {
    if (stream.is_paused() && stream.has_partially_sent_message()) {
      return false;
    }
  }
  return true;
}

void RRSendQueue::CommitResetStreams() {
  for (auto& [unused, stream] : streams_) {
    if (stream.is_paused()) {
      stream.Reset();
    }
  }
  RTC_DCHECK(IsConsistent());
}

void RRSendQueue::RollbackResetStreams() {
  for (auto& [unused, stream] : streams_) {
    stream.Resume();
  }
  RTC_DCHECK(IsConsistent());
}

void RRSendQueue::Reset() {
  // Recalculate buffered amount, as partially sent messages may have been put
  // fully back in the queue.
  for (auto& [unused, stream] : streams_) {
    stream.Reset();
  }
  previous_message_has_ended_ = true;
}

size_t RRSendQueue::buffered_amount(StreamID stream_id) const {
  auto it = streams_.find(stream_id);
  if (it == streams_.end()) {
    return 0;
  }
  return it->second.buffered_amount().value();
}

size_t RRSendQueue::buffered_amount_low_threshold(StreamID stream_id) const {
  auto it = streams_.find(stream_id);
  if (it == streams_.end()) {
    return 0;
  }
  return it->second.buffered_amount().low_threshold();
}

void RRSendQueue::SetBufferedAmountLowThreshold(StreamID stream_id,
                                                size_t bytes) {
  GetOrCreateStreamInfo(stream_id).buffered_amount().SetLowThreshold(bytes);
}

RRSendQueue::OutgoingStream& RRSendQueue::GetOrCreateStreamInfo(
    StreamID stream_id) {
  auto it = streams_.find(stream_id);
  if (it != streams_.end()) {
    return it->second;
  }

  return streams_
      .emplace(stream_id,
               OutgoingStream(
                   [this, stream_id]() { on_buffered_amount_low_(stream_id); },
                   total_buffered_amount_))
      .first->second;
}

HandoverReadinessStatus RRSendQueue::GetHandoverReadiness() const {
  HandoverReadinessStatus status;
  if (!IsEmpty()) {
    status.Add(HandoverUnreadinessReason::kSendQueueNotEmpty);
  }
  return status;
}

void RRSendQueue::AddHandoverState(DcSctpSocketHandoverState& state) {
  for (const auto& [stream_id, stream] : streams_) {
    DcSctpSocketHandoverState::OutgoingStream state_stream;
    state_stream.id = stream_id.value();
    stream.AddHandoverState(state_stream);
    state.tx.streams.push_back(std::move(state_stream));
  }
}

void RRSendQueue::RestoreFromState(const DcSctpSocketHandoverState& state) {
  for (const DcSctpSocketHandoverState::OutgoingStream& state_stream :
       state.tx.streams) {
    StreamID stream_id(state_stream.id);
    streams_.emplace(stream_id, OutgoingStream(
                                    [this, stream_id]() {
                                      on_buffered_amount_low_(stream_id);
                                    },
                                    total_buffered_amount_, &state_stream));
  }
}
}  // namespace dcsctp
