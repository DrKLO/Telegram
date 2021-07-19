/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/tx/fcfs_send_queue.h"

#include <cstdint>
#include <deque>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/packet/data.h"
#include "net/dcsctp/public/dcsctp_message.h"
#include "net/dcsctp/public/dcsctp_socket.h"
#include "net/dcsctp/tx/send_queue.h"
#include "rtc_base/logging.h"

namespace dcsctp {
void FCFSSendQueue::Add(TimeMs now,
                        DcSctpMessage message,
                        const SendOptions& send_options) {
  RTC_DCHECK(!message.payload().empty());
  std::deque<Item>& queue =
      IsPaused(message.stream_id()) ? paused_items_ : items_;
  // Any limited lifetime should start counting from now - when the message
  // has been added to the queue.
  absl::optional<TimeMs> expires_at = absl::nullopt;
  if (send_options.lifetime.has_value()) {
    // `expires_at` is the time when it expires. Which is slightly larger than
    // the message's lifetime, as the message is alive during its entire
    // lifetime (which may be zero).
    expires_at = now + *send_options.lifetime + DurationMs(1);
  }
  queue.emplace_back(std::move(message), expires_at, send_options);
}

size_t FCFSSendQueue::total_bytes() const {
  // TODO(boivie): Have the current size as a member variable, so that's it not
  // calculated for every operation.
  return absl::c_accumulate(items_, 0,
                            [](size_t size, const Item& item) {
                              return size + item.remaining_size;
                            }) +
         absl::c_accumulate(paused_items_, 0,
                            [](size_t size, const Item& item) {
                              return size + item.remaining_size;
                            });
}

bool FCFSSendQueue::IsFull() const {
  return total_bytes() >= buffer_size_;
}

bool FCFSSendQueue::IsEmpty() const {
  return items_.empty();
}

FCFSSendQueue::Item* FCFSSendQueue::GetFirstNonExpiredMessage(TimeMs now) {
  while (!items_.empty()) {
    FCFSSendQueue::Item& item = items_.front();
    // An entire item can be discarded iff:
    // 1) It hasn't been partially sent (has been allocated a message_id).
    // 2) It has a non-negative expiry time.
    // 3) And that expiry time has passed.
    if (!item.message_id.has_value() && item.expires_at.has_value() &&
        *item.expires_at <= now) {
      // TODO(boivie): This should be reported to the client.
      RTC_DLOG(LS_VERBOSE)
          << log_prefix_
          << "Message is expired before even partially sent - discarding";
      items_.pop_front();
      continue;
    }

    return &item;
  }
  return nullptr;
}

absl::optional<SendQueue::DataToSend> FCFSSendQueue::Produce(TimeMs now,
                                                             size_t max_size) {
  Item* item = GetFirstNonExpiredMessage(now);
  if (item == nullptr) {
    return absl::nullopt;
  }

  DcSctpMessage& message = item->message;

  // Don't make too small fragments as that can result in increased risk of
  // failure to assemble a message if a small fragment is missing.
  if (item->remaining_size > max_size && max_size < kMinimumFragmentedPayload) {
    RTC_DLOG(LS_VERBOSE) << log_prefix_ << "tx-msg: Will not fragment "
                         << item->remaining_size << " bytes into buffer of "
                         << max_size << " bytes";
    return absl::nullopt;
  }

  // Allocate Message ID and SSN when the first fragment is sent.
  if (!item->message_id.has_value()) {
    MID& mid =
        mid_by_stream_id_[{item->send_options.unordered, message.stream_id()}];
    item->message_id = mid;
    mid = MID(*mid + 1);
  }
  if (!item->send_options.unordered && !item->ssn.has_value()) {
    SSN& ssn = ssn_by_stream_id_[message.stream_id()];
    item->ssn = ssn;
    ssn = SSN(*ssn + 1);
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

  SendQueue::DataToSend chunk(Data(stream_id, item->ssn.value_or(SSN(0)),
                                   item->message_id.value(), fsn, ppid,
                                   std::move(payload), is_beginning, is_end,
                                   item->send_options.unordered));
  chunk.max_retransmissions = item->send_options.max_retransmissions;
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
  RTC_DLOG(LS_VERBOSE) << log_prefix_ << "tx-msg: Producing chunk of "
                       << chunk.data.size() << " bytes (max: " << max_size
                       << ")";
  return chunk;
}

void FCFSSendQueue::Discard(IsUnordered unordered,
                            StreamID stream_id,
                            MID message_id) {
  // As this method will only discard partially sent messages, and as the queue
  // is a FIFO queue, the only partially sent message would be the topmost
  // message.
  if (!items_.empty()) {
    Item& item = items_.front();
    if (item.send_options.unordered == unordered &&
        item.message.stream_id() == stream_id && item.message_id.has_value() &&
        *item.message_id == message_id) {
      items_.pop_front();
    }
  }
}

void FCFSSendQueue::PrepareResetStreams(
    rtc::ArrayView<const StreamID> streams) {
  for (StreamID stream_id : streams) {
    paused_streams_.insert(stream_id);
  }

  // Will not discard partially sent messages - only whole messages. Partially
  // delivered messages (at the time of receiving a Stream Reset command) will
  // always deliver all the fragments before actually resetting the stream.
  for (auto it = items_.begin(); it != items_.end();) {
    if (IsPaused(it->message.stream_id()) && it->remaining_offset == 0) {
      it = items_.erase(it);
    } else {
      ++it;
    }
  }
}

bool FCFSSendQueue::CanResetStreams() const {
  for (auto& item : items_) {
    if (IsPaused(item.message.stream_id())) {
      return false;
    }
  }
  return true;
}

void FCFSSendQueue::CommitResetStreams() {
  for (StreamID stream_id : paused_streams_) {
    ssn_by_stream_id_[stream_id] = SSN(0);
    // https://tools.ietf.org/html/rfc8260#section-2.3.2
    // "When an association resets the SSN using the SCTP extension defined
    // in [RFC6525], the two counters (one for the ordered messages, one for
    // the unordered messages) used for the MIDs MUST be reset to 0."
    mid_by_stream_id_[{IsUnordered(false), stream_id}] = MID(0);
    mid_by_stream_id_[{IsUnordered(true), stream_id}] = MID(0);
  }
  RollbackResetStreams();
}

void FCFSSendQueue::RollbackResetStreams() {
  while (!paused_items_.empty()) {
    items_.push_back(std::move(paused_items_.front()));
    paused_items_.pop_front();
  }
  paused_streams_.clear();
}

void FCFSSendQueue::Reset() {
  if (!items_.empty()) {
    // If this message has been partially sent, reset it so that it will be
    // re-sent.
    auto& item = items_.front();
    item.remaining_offset = 0;
    item.remaining_size = item.message.payload().size();
    item.message_id = absl::nullopt;
    item.ssn = absl::nullopt;
    item.current_fsn = FSN(0);
  }
  RollbackResetStreams();
  mid_by_stream_id_.clear();
  ssn_by_stream_id_.clear();
}

bool FCFSSendQueue::IsPaused(StreamID stream_id) const {
  return paused_streams_.find(stream_id) != paused_streams_.end();
}

}  // namespace dcsctp
