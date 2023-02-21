/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/tx/stream_scheduler.h"

#include <algorithm>

#include "absl/algorithm/container.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/common/str_join.h"
#include "net/dcsctp/packet/data.h"
#include "net/dcsctp/public/dcsctp_message.h"
#include "net/dcsctp/public/dcsctp_socket.h"
#include "net/dcsctp/public/types.h"
#include "net/dcsctp/tx/send_queue.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace dcsctp {

void StreamScheduler::Stream::SetPriority(StreamPriority priority) {
  priority_ = priority;
  inverse_weight_ = InverseWeight(priority);
}

absl::optional<SendQueue::DataToSend> StreamScheduler::Produce(
    TimeMs now,
    size_t max_size) {
  // For non-interleaved streams, avoid rescheduling while still sending a
  // message as it needs to be sent in full. For interleaved messaging,
  // reschedule for every I-DATA chunk sent.
  bool rescheduling =
      enable_message_interleaving_ || !currently_sending_a_message_;

  RTC_LOG(LS_VERBOSE) << "Producing data, rescheduling=" << rescheduling
                      << ", active="
                      << StrJoin(active_streams_, ", ",
                                 [&](rtc::StringBuilder& sb, const auto& p) {
                                   sb << *p->stream_id() << "@"
                                      << *p->next_finish_time();
                                 });

  RTC_DCHECK(rescheduling || current_stream_ != nullptr);

  absl::optional<SendQueue::DataToSend> data;
  while (!data.has_value() && !active_streams_.empty()) {
    if (rescheduling) {
      auto it = active_streams_.begin();
      current_stream_ = *it;
      RTC_DLOG(LS_VERBOSE) << "Rescheduling to stream "
                           << *current_stream_->stream_id();

      active_streams_.erase(it);
      current_stream_->ForceMarkInactive();
    } else {
      RTC_DLOG(LS_VERBOSE) << "Producing from previous stream: "
                           << *current_stream_->stream_id();
      RTC_DCHECK(absl::c_any_of(active_streams_, [this](const auto* p) {
        return p == current_stream_;
      }));
    }

    data = current_stream_->Produce(now, max_size);
  }

  if (!data.has_value()) {
    RTC_DLOG(LS_VERBOSE)
        << "There is no stream with data; Can't produce any data.";
    RTC_DCHECK(IsConsistent());

    return absl::nullopt;
  }

  RTC_DCHECK(data->data.stream_id == current_stream_->stream_id());

  RTC_DLOG(LS_VERBOSE) << "Producing DATA, type="
                       << (data->data.is_unordered ? "unordered" : "ordered")
                       << "::"
                       << (*data->data.is_beginning && *data->data.is_end
                               ? "complete"
                           : *data->data.is_beginning ? "first"
                           : *data->data.is_end       ? "last"
                                                      : "middle")
                       << ", stream_id=" << *current_stream_->stream_id()
                       << ", ppid=" << *data->data.ppid
                       << ", length=" << data->data.payload.size();

  currently_sending_a_message_ = !*data->data.is_end;
  virtual_time_ = current_stream_->current_time();

  // One side-effect of rescheduling is that the new stream will not be present
  // in `active_streams`.
  size_t bytes_to_send_next = current_stream_->bytes_to_send_in_next_message();
  if (rescheduling && bytes_to_send_next > 0) {
    current_stream_->MakeActive(bytes_to_send_next);
  } else if (!rescheduling && bytes_to_send_next == 0) {
    current_stream_->MakeInactive();
  }

  RTC_DCHECK(IsConsistent());
  return data;
}

StreamScheduler::VirtualTime StreamScheduler::Stream::CalculateFinishTime(
    size_t bytes_to_send_next) const {
  if (parent_.enable_message_interleaving_) {
    // Perform weighted fair queuing scheduling.
    return VirtualTime(*current_virtual_time_ +
                       bytes_to_send_next * *inverse_weight_);
  }

  // Perform round-robin scheduling by letting the stream have its next virtual
  // finish time in the future. It doesn't matter how far into the future, just
  // any positive number so that any other stream that has the same virtual
  // finish time as this stream gets to produce their data before revisiting
  // this stream.
  return VirtualTime(*current_virtual_time_ + 1);
}

absl::optional<SendQueue::DataToSend> StreamScheduler::Stream::Produce(
    TimeMs now,
    size_t max_size) {
  absl::optional<SendQueue::DataToSend> data = producer_.Produce(now, max_size);

  if (data.has_value()) {
    VirtualTime new_current = CalculateFinishTime(data->data.payload.size());
    RTC_DLOG(LS_VERBOSE) << "Virtual time changed: " << *current_virtual_time_
                         << " -> " << *new_current;
    current_virtual_time_ = new_current;
  }

  return data;
}

bool StreamScheduler::IsConsistent() const {
  for (Stream* stream : active_streams_) {
    if (stream->next_finish_time_ == VirtualTime::Zero()) {
      RTC_DLOG(LS_VERBOSE) << "Stream " << *stream->stream_id()
                           << " is active, but has no next-finish-time";
      return false;
    }
  }
  return true;
}

void StreamScheduler::Stream::MaybeMakeActive() {
  RTC_DLOG(LS_VERBOSE) << "MaybeMakeActive(" << *stream_id() << ")";
  RTC_DCHECK(next_finish_time_ == VirtualTime::Zero());
  size_t bytes_to_send_next = bytes_to_send_in_next_message();
  if (bytes_to_send_next == 0) {
    return;
  }

  MakeActive(bytes_to_send_next);
}

void StreamScheduler::Stream::MakeActive(size_t bytes_to_send_next) {
  current_virtual_time_ = parent_.virtual_time_;
  RTC_DCHECK_GT(bytes_to_send_next, 0);
  VirtualTime next_finish_time = CalculateFinishTime(
      std::min(bytes_to_send_next, parent_.max_payload_bytes_));
  RTC_DCHECK_GT(*next_finish_time, 0);
  RTC_DLOG(LS_VERBOSE) << "Making stream " << *stream_id()
                       << " active, expiring at " << *next_finish_time;
  RTC_DCHECK(next_finish_time_ == VirtualTime::Zero());
  next_finish_time_ = next_finish_time;
  RTC_DCHECK(!absl::c_any_of(parent_.active_streams_,
                             [this](const auto* p) { return p == this; }));
  parent_.active_streams_.emplace(this);
}

void StreamScheduler::Stream::ForceMarkInactive() {
  RTC_DLOG(LS_VERBOSE) << "Making stream " << *stream_id() << " inactive";
  RTC_DCHECK(next_finish_time_ != VirtualTime::Zero());
  next_finish_time_ = VirtualTime::Zero();
}

void StreamScheduler::Stream::MakeInactive() {
  ForceMarkInactive();
  webrtc::EraseIf(parent_.active_streams_,
                  [&](const auto* s) { return s == this; });
}

std::set<StreamID> StreamScheduler::ActiveStreamsForTesting() const {
  std::set<StreamID> stream_ids;
  for (const auto& stream : active_streams_) {
    stream_ids.insert(stream->stream_id());
  }
  return stream_ids;
}

}  // namespace dcsctp
