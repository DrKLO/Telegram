/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/socket/callback_deferrer.h"

#include "api/make_ref_counted.h"

namespace dcsctp {

void CallbackDeferrer::Prepare() {
  RTC_DCHECK(!prepared_);
  prepared_ = true;
}

void CallbackDeferrer::TriggerDeferred() {
  // Need to swap here. The client may call into the library from within a
  // callback, and that might result in adding new callbacks to this instance,
  // and the vector can't be modified while iterated on.
  RTC_DCHECK(prepared_);
  prepared_ = false;
  if (deferred_.empty()) {
    return;
  }
  std::vector<std::pair<Callback, CallbackData>> deferred;
  // Reserve a small buffer to prevent too much reallocation on growth.
  deferred.reserve(8);
  deferred.swap(deferred_);
  for (auto& [cb, data] : deferred) {
    cb(std::move(data), underlying_);
  }
}

SendPacketStatus CallbackDeferrer::SendPacketWithStatus(
    rtc::ArrayView<const uint8_t> data) {
  // Will not be deferred - call directly.
  return underlying_.SendPacketWithStatus(data);
}

std::unique_ptr<Timeout> CallbackDeferrer::CreateTimeout(
    webrtc::TaskQueueBase::DelayPrecision precision) {
  // Will not be deferred - call directly.
  return underlying_.CreateTimeout(precision);
}

TimeMs CallbackDeferrer::TimeMillis() {
  // This should not be called by the library - it's migrated to `Now()`.
  RTC_DCHECK(false);
  // Will not be deferred - call directly.
  return underlying_.TimeMillis();
}

uint32_t CallbackDeferrer::GetRandomInt(uint32_t low, uint32_t high) {
  // Will not be deferred - call directly.
  return underlying_.GetRandomInt(low, high);
}

void CallbackDeferrer::OnMessageReceived(DcSctpMessage message) {
  RTC_DCHECK(prepared_);
  deferred_.emplace_back(
      +[](CallbackData data, DcSctpSocketCallbacks& cb) {
        return cb.OnMessageReceived(absl::get<DcSctpMessage>(std::move(data)));
      },
      std::move(message));
}

void CallbackDeferrer::OnError(ErrorKind error, absl::string_view message) {
  RTC_DCHECK(prepared_);
  deferred_.emplace_back(
      +[](CallbackData data, DcSctpSocketCallbacks& cb) {
        Error error = absl::get<Error>(std::move(data));
        return cb.OnError(error.error, error.message);
      },
      Error{error, std::string(message)});
}

void CallbackDeferrer::OnAborted(ErrorKind error, absl::string_view message) {
  RTC_DCHECK(prepared_);
  deferred_.emplace_back(
      +[](CallbackData data, DcSctpSocketCallbacks& cb) {
        Error error = absl::get<Error>(std::move(data));
        return cb.OnAborted(error.error, error.message);
      },
      Error{error, std::string(message)});
}

void CallbackDeferrer::OnConnected() {
  RTC_DCHECK(prepared_);
  deferred_.emplace_back(
      +[](CallbackData data, DcSctpSocketCallbacks& cb) {
        return cb.OnConnected();
      },
      absl::monostate{});
}

void CallbackDeferrer::OnClosed() {
  RTC_DCHECK(prepared_);
  deferred_.emplace_back(
      +[](CallbackData data, DcSctpSocketCallbacks& cb) {
        return cb.OnClosed();
      },
      absl::monostate{});
}

void CallbackDeferrer::OnConnectionRestarted() {
  RTC_DCHECK(prepared_);
  deferred_.emplace_back(
      +[](CallbackData data, DcSctpSocketCallbacks& cb) {
        return cb.OnConnectionRestarted();
      },
      absl::monostate{});
}

void CallbackDeferrer::OnStreamsResetFailed(
    rtc::ArrayView<const StreamID> outgoing_streams,
    absl::string_view reason) {
  RTC_DCHECK(prepared_);
  deferred_.emplace_back(
      +[](CallbackData data, DcSctpSocketCallbacks& cb) {
        StreamReset stream_reset = absl::get<StreamReset>(std::move(data));
        return cb.OnStreamsResetFailed(stream_reset.streams,
                                       stream_reset.message);
      },
      StreamReset{{outgoing_streams.begin(), outgoing_streams.end()},
                  std::string(reason)});
}

void CallbackDeferrer::OnStreamsResetPerformed(
    rtc::ArrayView<const StreamID> outgoing_streams) {
  RTC_DCHECK(prepared_);
  deferred_.emplace_back(
      +[](CallbackData data, DcSctpSocketCallbacks& cb) {
        StreamReset stream_reset = absl::get<StreamReset>(std::move(data));
        return cb.OnStreamsResetPerformed(stream_reset.streams);
      },
      StreamReset{{outgoing_streams.begin(), outgoing_streams.end()}});
}

void CallbackDeferrer::OnIncomingStreamsReset(
    rtc::ArrayView<const StreamID> incoming_streams) {
  RTC_DCHECK(prepared_);
  deferred_.emplace_back(
      +[](CallbackData data, DcSctpSocketCallbacks& cb) {
        StreamReset stream_reset = absl::get<StreamReset>(std::move(data));
        return cb.OnIncomingStreamsReset(stream_reset.streams);
      },
      StreamReset{{incoming_streams.begin(), incoming_streams.end()}});
}

void CallbackDeferrer::OnBufferedAmountLow(StreamID stream_id) {
  RTC_DCHECK(prepared_);
  deferred_.emplace_back(
      +[](CallbackData data, DcSctpSocketCallbacks& cb) {
        return cb.OnBufferedAmountLow(absl::get<StreamID>(std::move(data)));
      },
      stream_id);
}

void CallbackDeferrer::OnTotalBufferedAmountLow() {
  RTC_DCHECK(prepared_);
  deferred_.emplace_back(
      +[](CallbackData data, DcSctpSocketCallbacks& cb) {
        return cb.OnTotalBufferedAmountLow();
      },
      absl::monostate{});
}

void CallbackDeferrer::OnLifecycleMessageExpired(LifecycleId lifecycle_id,
                                                 bool maybe_delivered) {
  // Will not be deferred - call directly.
  underlying_.OnLifecycleMessageExpired(lifecycle_id, maybe_delivered);
}
void CallbackDeferrer::OnLifecycleMessageFullySent(LifecycleId lifecycle_id) {
  // Will not be deferred - call directly.
  underlying_.OnLifecycleMessageFullySent(lifecycle_id);
}
void CallbackDeferrer::OnLifecycleMessageDelivered(LifecycleId lifecycle_id) {
  // Will not be deferred - call directly.
  underlying_.OnLifecycleMessageDelivered(lifecycle_id);
}
void CallbackDeferrer::OnLifecycleEnd(LifecycleId lifecycle_id) {
  // Will not be deferred - call directly.
  underlying_.OnLifecycleEnd(lifecycle_id);
}
}  // namespace dcsctp
