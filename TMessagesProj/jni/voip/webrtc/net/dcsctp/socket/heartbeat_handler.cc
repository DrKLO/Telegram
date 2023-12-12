/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/socket/heartbeat_handler.h"

#include <stddef.h>

#include <cstdint>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/functional/bind_front.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/packet/bounded_byte_reader.h"
#include "net/dcsctp/packet/bounded_byte_writer.h"
#include "net/dcsctp/packet/chunk/heartbeat_ack_chunk.h"
#include "net/dcsctp/packet/chunk/heartbeat_request_chunk.h"
#include "net/dcsctp/packet/parameter/heartbeat_info_parameter.h"
#include "net/dcsctp/packet/parameter/parameter.h"
#include "net/dcsctp/packet/sctp_packet.h"
#include "net/dcsctp/public/dcsctp_options.h"
#include "net/dcsctp/public/dcsctp_socket.h"
#include "net/dcsctp/socket/context.h"
#include "net/dcsctp/timer/timer.h"
#include "rtc_base/logging.h"

namespace dcsctp {

// This is stored (in serialized form) as HeartbeatInfoParameter sent in
// HeartbeatRequestChunk and received back in HeartbeatAckChunk. It should be
// well understood that this data may be modified by the peer, so it can't
// be trusted.
//
// It currently only stores a timestamp, in millisecond precision, to allow for
// RTT measurements. If that would be manipulated by the peer, it would just
// result in incorrect RTT measurements, which isn't an issue.
class HeartbeatInfo {
 public:
  static constexpr size_t kBufferSize = sizeof(uint64_t);
  static_assert(kBufferSize == 8, "Unexpected buffer size");

  explicit HeartbeatInfo(TimeMs created_at) : created_at_(created_at) {}

  std::vector<uint8_t> Serialize() {
    uint32_t high_bits = static_cast<uint32_t>(*created_at_ >> 32);
    uint32_t low_bits = static_cast<uint32_t>(*created_at_);

    std::vector<uint8_t> data(kBufferSize);
    BoundedByteWriter<kBufferSize> writer(data);
    writer.Store32<0>(high_bits);
    writer.Store32<4>(low_bits);
    return data;
  }

  static absl::optional<HeartbeatInfo> Deserialize(
      rtc::ArrayView<const uint8_t> data) {
    if (data.size() != kBufferSize) {
      RTC_LOG(LS_WARNING) << "Invalid heartbeat info: " << data.size()
                          << " bytes";
      return absl::nullopt;
    }

    BoundedByteReader<kBufferSize> reader(data);
    uint32_t high_bits = reader.Load32<0>();
    uint32_t low_bits = reader.Load32<4>();

    uint64_t created_at = static_cast<uint64_t>(high_bits) << 32 | low_bits;
    return HeartbeatInfo(TimeMs(created_at));
  }

  TimeMs created_at() const { return created_at_; }

 private:
  const TimeMs created_at_;
};

HeartbeatHandler::HeartbeatHandler(absl::string_view log_prefix,
                                   const DcSctpOptions& options,
                                   Context* context,
                                   TimerManager* timer_manager)
    : log_prefix_(std::string(log_prefix) + "heartbeat: "),
      ctx_(context),
      timer_manager_(timer_manager),
      interval_duration_(options.heartbeat_interval),
      interval_duration_should_include_rtt_(
          options.heartbeat_interval_include_rtt),
      interval_timer_(timer_manager_->CreateTimer(
          "heartbeat-interval",
          absl::bind_front(&HeartbeatHandler::OnIntervalTimerExpiry, this),
          TimerOptions(interval_duration_, TimerBackoffAlgorithm::kFixed))),
      timeout_timer_(timer_manager_->CreateTimer(
          "heartbeat-timeout",
          absl::bind_front(&HeartbeatHandler::OnTimeoutTimerExpiry, this),
          TimerOptions(options.rto_initial,
                       TimerBackoffAlgorithm::kExponential,
                       /*max_restarts=*/0))) {
  // The interval timer must always be running as long as the association is up.
  RestartTimer();
}

void HeartbeatHandler::RestartTimer() {
  if (interval_duration_ == DurationMs(0)) {
    // Heartbeating has been disabled.
    return;
  }

  if (interval_duration_should_include_rtt_) {
    // The RTT should be used, but it's not easy accessible. The RTO will
    // suffice.
    interval_timer_->set_duration(interval_duration_ + ctx_->current_rto());
  } else {
    interval_timer_->set_duration(interval_duration_);
  }

  interval_timer_->Start();
}

void HeartbeatHandler::HandleHeartbeatRequest(HeartbeatRequestChunk chunk) {
  // https://tools.ietf.org/html/rfc4960#section-8.3
  // "The receiver of the HEARTBEAT should immediately respond with a
  // HEARTBEAT ACK that contains the Heartbeat Information TLV, together with
  // any other received TLVs, copied unchanged from the received HEARTBEAT
  // chunk."
  ctx_->Send(ctx_->PacketBuilder().Add(
      HeartbeatAckChunk(std::move(chunk).extract_parameters())));
}

void HeartbeatHandler::HandleHeartbeatAck(HeartbeatAckChunk chunk) {
  timeout_timer_->Stop();
  absl::optional<HeartbeatInfoParameter> info_param = chunk.info();
  if (!info_param.has_value()) {
    ctx_->callbacks().OnError(
        ErrorKind::kParseFailed,
        "Failed to parse HEARTBEAT-ACK; No Heartbeat Info parameter");
    return;
  }
  absl::optional<HeartbeatInfo> info =
      HeartbeatInfo::Deserialize(info_param->info());
  if (!info.has_value()) {
    ctx_->callbacks().OnError(ErrorKind::kParseFailed,
                              "Failed to parse HEARTBEAT-ACK; Failed to "
                              "deserialized Heartbeat info parameter");
    return;
  }

  TimeMs now = ctx_->callbacks().TimeMillis();
  if (info->created_at() > TimeMs(0) && info->created_at() <= now) {
    ctx_->ObserveRTT(now - info->created_at());
  }

  // https://tools.ietf.org/html/rfc4960#section-8.1
  // "The counter shall be reset each time ... a HEARTBEAT ACK is received from
  // the peer endpoint."
  ctx_->ClearTxErrorCounter();
}

absl::optional<DurationMs> HeartbeatHandler::OnIntervalTimerExpiry() {
  if (ctx_->is_connection_established()) {
    HeartbeatInfo info(ctx_->callbacks().TimeMillis());
    timeout_timer_->set_duration(ctx_->current_rto());
    timeout_timer_->Start();
    RTC_DLOG(LS_INFO) << log_prefix_ << "Sending HEARTBEAT with timeout "
                      << *timeout_timer_->duration();

    Parameters parameters = Parameters::Builder()
                                .Add(HeartbeatInfoParameter(info.Serialize()))
                                .Build();

    ctx_->Send(ctx_->PacketBuilder().Add(
        HeartbeatRequestChunk(std::move(parameters))));
  } else {
    RTC_DLOG(LS_VERBOSE)
        << log_prefix_
        << "Will not send HEARTBEAT when connection not established";
  }
  return absl::nullopt;
}

absl::optional<DurationMs> HeartbeatHandler::OnTimeoutTimerExpiry() {
  // Note that the timeout timer is not restarted. It will be started again when
  // the interval timer expires.
  RTC_DCHECK(!timeout_timer_->is_running());
  ctx_->IncrementTxErrorCounter("HEARTBEAT timeout");
  return absl::nullopt;
}
}  // namespace dcsctp
