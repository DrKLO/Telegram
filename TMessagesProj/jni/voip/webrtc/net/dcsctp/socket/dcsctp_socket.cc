/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/socket/dcsctp_socket.h"

#include <algorithm>
#include <cstdint>
#include <limits>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/functional/bind_front.h"
#include "absl/memory/memory.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/packet/chunk/abort_chunk.h"
#include "net/dcsctp/packet/chunk/chunk.h"
#include "net/dcsctp/packet/chunk/cookie_ack_chunk.h"
#include "net/dcsctp/packet/chunk/cookie_echo_chunk.h"
#include "net/dcsctp/packet/chunk/data_chunk.h"
#include "net/dcsctp/packet/chunk/data_common.h"
#include "net/dcsctp/packet/chunk/error_chunk.h"
#include "net/dcsctp/packet/chunk/forward_tsn_chunk.h"
#include "net/dcsctp/packet/chunk/forward_tsn_common.h"
#include "net/dcsctp/packet/chunk/heartbeat_ack_chunk.h"
#include "net/dcsctp/packet/chunk/heartbeat_request_chunk.h"
#include "net/dcsctp/packet/chunk/idata_chunk.h"
#include "net/dcsctp/packet/chunk/iforward_tsn_chunk.h"
#include "net/dcsctp/packet/chunk/init_ack_chunk.h"
#include "net/dcsctp/packet/chunk/init_chunk.h"
#include "net/dcsctp/packet/chunk/reconfig_chunk.h"
#include "net/dcsctp/packet/chunk/sack_chunk.h"
#include "net/dcsctp/packet/chunk/shutdown_ack_chunk.h"
#include "net/dcsctp/packet/chunk/shutdown_chunk.h"
#include "net/dcsctp/packet/chunk/shutdown_complete_chunk.h"
#include "net/dcsctp/packet/chunk_validators.h"
#include "net/dcsctp/packet/data.h"
#include "net/dcsctp/packet/error_cause/cookie_received_while_shutting_down_cause.h"
#include "net/dcsctp/packet/error_cause/error_cause.h"
#include "net/dcsctp/packet/error_cause/no_user_data_cause.h"
#include "net/dcsctp/packet/error_cause/out_of_resource_error_cause.h"
#include "net/dcsctp/packet/error_cause/protocol_violation_cause.h"
#include "net/dcsctp/packet/error_cause/unrecognized_chunk_type_cause.h"
#include "net/dcsctp/packet/error_cause/user_initiated_abort_cause.h"
#include "net/dcsctp/packet/parameter/forward_tsn_supported_parameter.h"
#include "net/dcsctp/packet/parameter/parameter.h"
#include "net/dcsctp/packet/parameter/state_cookie_parameter.h"
#include "net/dcsctp/packet/parameter/supported_extensions_parameter.h"
#include "net/dcsctp/packet/parameter/zero_checksum_acceptable_chunk_parameter.h"
#include "net/dcsctp/packet/sctp_packet.h"
#include "net/dcsctp/packet/tlv_trait.h"
#include "net/dcsctp/public/dcsctp_message.h"
#include "net/dcsctp/public/dcsctp_options.h"
#include "net/dcsctp/public/dcsctp_socket.h"
#include "net/dcsctp/public/packet_observer.h"
#include "net/dcsctp/public/types.h"
#include "net/dcsctp/rx/data_tracker.h"
#include "net/dcsctp/rx/reassembly_queue.h"
#include "net/dcsctp/socket/callback_deferrer.h"
#include "net/dcsctp/socket/capabilities.h"
#include "net/dcsctp/socket/heartbeat_handler.h"
#include "net/dcsctp/socket/state_cookie.h"
#include "net/dcsctp/socket/stream_reset_handler.h"
#include "net/dcsctp/socket/transmission_control_block.h"
#include "net/dcsctp/timer/timer.h"
#include "net/dcsctp/tx/retransmission_queue.h"
#include "net/dcsctp/tx/send_queue.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/strings/string_format.h"

namespace dcsctp {
namespace {
using ::webrtc::TimeDelta;
using ::webrtc::Timestamp;

// https://tools.ietf.org/html/rfc4960#section-5.1
constexpr uint32_t kMinVerificationTag = 1;
constexpr uint32_t kMaxVerificationTag = std::numeric_limits<uint32_t>::max();

// https://tools.ietf.org/html/rfc4960#section-3.3.2
constexpr uint32_t kMinInitialTsn = 0;
constexpr uint32_t kMaxInitialTsn = std::numeric_limits<uint32_t>::max();

Capabilities ComputeCapabilities(const DcSctpOptions& options,
                                 uint16_t peer_nbr_outbound_streams,
                                 uint16_t peer_nbr_inbound_streams,
                                 const Parameters& parameters) {
  Capabilities capabilities;
  absl::optional<SupportedExtensionsParameter> supported_extensions =
      parameters.get<SupportedExtensionsParameter>();

  if (options.enable_partial_reliability) {
    capabilities.partial_reliability =
        parameters.get<ForwardTsnSupportedParameter>().has_value();
    if (supported_extensions.has_value()) {
      capabilities.partial_reliability |=
          supported_extensions->supports(ForwardTsnChunk::kType);
    }
  }

  if (options.enable_message_interleaving && supported_extensions.has_value()) {
    capabilities.message_interleaving =
        supported_extensions->supports(IDataChunk::kType) &&
        supported_extensions->supports(IForwardTsnChunk::kType);
  }
  if (supported_extensions.has_value() &&
      supported_extensions->supports(ReConfigChunk::kType)) {
    capabilities.reconfig = true;
  }

  if (options.zero_checksum_alternate_error_detection_method !=
          ZeroChecksumAlternateErrorDetectionMethod::None() &&
      parameters.get<ZeroChecksumAcceptableChunkParameter>().has_value() &&
      parameters.get<ZeroChecksumAcceptableChunkParameter>()
              ->error_detection_method() ==
          options.zero_checksum_alternate_error_detection_method) {
    capabilities.zero_checksum = true;
  }

  capabilities.negotiated_maximum_incoming_streams = std::min(
      options.announced_maximum_incoming_streams, peer_nbr_outbound_streams);
  capabilities.negotiated_maximum_outgoing_streams = std::min(
      options.announced_maximum_outgoing_streams, peer_nbr_inbound_streams);

  return capabilities;
}

void AddCapabilityParameters(const DcSctpOptions& options,
                             bool support_zero_checksum,
                             Parameters::Builder& builder) {
  std::vector<uint8_t> chunk_types = {ReConfigChunk::kType};

  if (options.enable_partial_reliability) {
    builder.Add(ForwardTsnSupportedParameter());
    chunk_types.push_back(ForwardTsnChunk::kType);
  }
  if (options.enable_message_interleaving) {
    chunk_types.push_back(IDataChunk::kType);
    chunk_types.push_back(IForwardTsnChunk::kType);
  }
  if (support_zero_checksum) {
    RTC_DCHECK(options.zero_checksum_alternate_error_detection_method !=
               ZeroChecksumAlternateErrorDetectionMethod::None());
    builder.Add(ZeroChecksumAcceptableChunkParameter(
        options.zero_checksum_alternate_error_detection_method));
  }
  builder.Add(SupportedExtensionsParameter(std::move(chunk_types)));
}

TieTag MakeTieTag(DcSctpSocketCallbacks& cb) {
  uint32_t tie_tag_upper =
      cb.GetRandomInt(0, std::numeric_limits<uint32_t>::max());
  uint32_t tie_tag_lower =
      cb.GetRandomInt(1, std::numeric_limits<uint32_t>::max());
  return TieTag(static_cast<uint64_t>(tie_tag_upper) << 32 |
                static_cast<uint64_t>(tie_tag_lower));
}

SctpImplementation DeterminePeerImplementation(
    rtc::ArrayView<const uint8_t> cookie) {
  if (cookie.size() > 8) {
    absl::string_view magic(reinterpret_cast<const char*>(cookie.data()), 8);
    if (magic == "dcSCTP00") {
      return SctpImplementation::kDcsctp;
    }
    if (magic == "KAME-BSD") {
      return SctpImplementation::kUsrSctp;
    }
  }
  return SctpImplementation::kOther;
}
}  // namespace

DcSctpSocket::DcSctpSocket(absl::string_view log_prefix,
                           DcSctpSocketCallbacks& callbacks,
                           std::unique_ptr<PacketObserver> packet_observer,
                           const DcSctpOptions& options)
    : log_prefix_(std::string(log_prefix) + ": "),
      packet_observer_(std::move(packet_observer)),
      options_(options),
      callbacks_(callbacks),
      timer_manager_([this](webrtc::TaskQueueBase::DelayPrecision precision) {
        return callbacks_.CreateTimeout(precision);
      }),
      t1_init_(timer_manager_.CreateTimer(
          "t1-init",
          absl::bind_front(&DcSctpSocket::OnInitTimerExpiry, this),
          TimerOptions(options.t1_init_timeout.ToTimeDelta(),
                       TimerBackoffAlgorithm::kExponential,
                       options.max_init_retransmits))),
      t1_cookie_(timer_manager_.CreateTimer(
          "t1-cookie",
          absl::bind_front(&DcSctpSocket::OnCookieTimerExpiry, this),
          TimerOptions(options.t1_cookie_timeout.ToTimeDelta(),
                       TimerBackoffAlgorithm::kExponential,
                       options.max_init_retransmits))),
      t2_shutdown_(timer_manager_.CreateTimer(
          "t2-shutdown",
          absl::bind_front(&DcSctpSocket::OnShutdownTimerExpiry, this),
          TimerOptions(options.t2_shutdown_timeout.ToTimeDelta(),
                       TimerBackoffAlgorithm::kExponential,
                       options.max_retransmissions))),
      packet_sender_(callbacks_,
                     absl::bind_front(&DcSctpSocket::OnSentPacket, this)),
      send_queue_(log_prefix_,
                  &callbacks_,
                  options_.max_send_buffer_size,
                  options_.mtu,
                  options_.default_stream_priority,
                  options_.total_buffered_amount_low_threshold) {}

std::string DcSctpSocket::log_prefix() const {
  return log_prefix_ + "[" + std::string(ToString(state_)) + "] ";
}

bool DcSctpSocket::IsConsistent() const {
  if (tcb_ != nullptr && tcb_->reassembly_queue().HasMessages()) {
    return false;
  }
  switch (state_) {
    case State::kClosed:
      return (tcb_ == nullptr && !t1_init_->is_running() &&
              !t1_cookie_->is_running() && !t2_shutdown_->is_running());
    case State::kCookieWait:
      return (tcb_ == nullptr && t1_init_->is_running() &&
              !t1_cookie_->is_running() && !t2_shutdown_->is_running());
    case State::kCookieEchoed:
      return (tcb_ != nullptr && !t1_init_->is_running() &&
              t1_cookie_->is_running() && !t2_shutdown_->is_running() &&
              tcb_->has_cookie_echo_chunk());
    case State::kEstablished:
      return (tcb_ != nullptr && !t1_init_->is_running() &&
              !t1_cookie_->is_running() && !t2_shutdown_->is_running());
    case State::kShutdownPending:
      return (tcb_ != nullptr && !t1_init_->is_running() &&
              !t1_cookie_->is_running() && !t2_shutdown_->is_running());
    case State::kShutdownSent:
      return (tcb_ != nullptr && !t1_init_->is_running() &&
              !t1_cookie_->is_running() && t2_shutdown_->is_running());
    case State::kShutdownReceived:
      return (tcb_ != nullptr && !t1_init_->is_running() &&
              !t1_cookie_->is_running() && !t2_shutdown_->is_running());
    case State::kShutdownAckSent:
      return (tcb_ != nullptr && !t1_init_->is_running() &&
              !t1_cookie_->is_running() && t2_shutdown_->is_running());
  }
}

constexpr absl::string_view DcSctpSocket::ToString(DcSctpSocket::State state) {
  switch (state) {
    case DcSctpSocket::State::kClosed:
      return "CLOSED";
    case DcSctpSocket::State::kCookieWait:
      return "COOKIE_WAIT";
    case DcSctpSocket::State::kCookieEchoed:
      return "COOKIE_ECHOED";
    case DcSctpSocket::State::kEstablished:
      return "ESTABLISHED";
    case DcSctpSocket::State::kShutdownPending:
      return "SHUTDOWN_PENDING";
    case DcSctpSocket::State::kShutdownSent:
      return "SHUTDOWN_SENT";
    case DcSctpSocket::State::kShutdownReceived:
      return "SHUTDOWN_RECEIVED";
    case DcSctpSocket::State::kShutdownAckSent:
      return "SHUTDOWN_ACK_SENT";
  }
}

void DcSctpSocket::SetState(State state, absl::string_view reason) {
  if (state_ != state) {
    RTC_DLOG(LS_VERBOSE) << log_prefix_ << "Socket state changed from "
                         << ToString(state_) << " to " << ToString(state)
                         << " due to " << reason;
    state_ = state;
  }
}

void DcSctpSocket::SendInit() {
  Parameters::Builder params_builder;
  AddCapabilityParameters(
      options_, /*support_zero_checksum=*/
      options_.zero_checksum_alternate_error_detection_method !=
          ZeroChecksumAlternateErrorDetectionMethod::None(),
      params_builder);
  InitChunk init(/*initiate_tag=*/connect_params_.verification_tag,
                 /*a_rwnd=*/options_.max_receiver_window_buffer_size,
                 options_.announced_maximum_outgoing_streams,
                 options_.announced_maximum_incoming_streams,
                 connect_params_.initial_tsn, params_builder.Build());
  SctpPacket::Builder b(VerificationTag(0), options_);
  b.Add(init);
  // https://www.ietf.org/archive/id/draft-tuexen-tsvwg-sctp-zero-checksum-01.html#section-4.2
  // "When an end point sends a packet containing an INIT chunk, it MUST include
  // a correct CRC32c checksum in the packet containing the INIT chunk."
  packet_sender_.Send(b, /*write_checksum=*/true);
}

void DcSctpSocket::Connect() {
  CallbackDeferrer::ScopedDeferrer deferrer(callbacks_);

  if (state_ == State::kClosed) {
    connect_params_.initial_tsn =
        TSN(callbacks_.GetRandomInt(kMinInitialTsn, kMaxInitialTsn));
    connect_params_.verification_tag = VerificationTag(
        callbacks_.GetRandomInt(kMinVerificationTag, kMaxVerificationTag));
    RTC_DLOG(LS_INFO)
        << log_prefix()
        << rtc::StringFormat(
               "Connecting. my_verification_tag=%08x, my_initial_tsn=%u",
               *connect_params_.verification_tag, *connect_params_.initial_tsn);
    SendInit();
    t1_init_->Start();
    SetState(State::kCookieWait, "Connect called");
  } else {
    RTC_DLOG(LS_WARNING) << log_prefix()
                         << "Called Connect on a socket that is not closed";
  }
  RTC_DCHECK(IsConsistent());
}

void DcSctpSocket::CreateTransmissionControlBlock(
    const Capabilities& capabilities,
    VerificationTag my_verification_tag,
    TSN my_initial_tsn,
    VerificationTag peer_verification_tag,
    TSN peer_initial_tsn,
    size_t a_rwnd,
    TieTag tie_tag) {
  metrics_.uses_message_interleaving = capabilities.message_interleaving;
  metrics_.uses_zero_checksum = capabilities.zero_checksum;
  metrics_.negotiated_maximum_incoming_streams =
      capabilities.negotiated_maximum_incoming_streams;
  metrics_.negotiated_maximum_outgoing_streams =
      capabilities.negotiated_maximum_outgoing_streams;
  tcb_ = std::make_unique<TransmissionControlBlock>(
      timer_manager_, log_prefix_, options_, capabilities, callbacks_,
      send_queue_, my_verification_tag, my_initial_tsn, peer_verification_tag,
      peer_initial_tsn, a_rwnd, tie_tag, packet_sender_,
      [this]() { return state_ == State::kEstablished; });
  RTC_DLOG(LS_VERBOSE) << log_prefix() << "Created TCB: " << tcb_->ToString();
}

void DcSctpSocket::RestoreFromState(const DcSctpSocketHandoverState& state) {
  CallbackDeferrer::ScopedDeferrer deferrer(callbacks_);

  if (state_ != State::kClosed) {
    callbacks_.OnError(ErrorKind::kUnsupportedOperation,
                       "Only closed socket can be restored from state");
  } else {
    if (state.socket_state ==
        DcSctpSocketHandoverState::SocketState::kConnected) {
      VerificationTag my_verification_tag =
          VerificationTag(state.my_verification_tag);
      connect_params_.verification_tag = my_verification_tag;

      Capabilities capabilities;
      capabilities.partial_reliability = state.capabilities.partial_reliability;
      capabilities.message_interleaving =
          state.capabilities.message_interleaving;
      capabilities.reconfig = state.capabilities.reconfig;
      capabilities.zero_checksum = state.capabilities.zero_checksum;
      capabilities.negotiated_maximum_incoming_streams =
          state.capabilities.negotiated_maximum_incoming_streams;
      capabilities.negotiated_maximum_outgoing_streams =
          state.capabilities.negotiated_maximum_outgoing_streams;

      send_queue_.RestoreFromState(state);

      CreateTransmissionControlBlock(
          capabilities, my_verification_tag, TSN(state.my_initial_tsn),
          VerificationTag(state.peer_verification_tag),
          TSN(state.peer_initial_tsn), static_cast<size_t>(0),
          TieTag(state.tie_tag));

      tcb_->RestoreFromState(state);

      SetState(State::kEstablished, "restored from handover state");
      callbacks_.OnConnected();
    }
  }

  RTC_DCHECK(IsConsistent());
}

void DcSctpSocket::Shutdown() {
  CallbackDeferrer::ScopedDeferrer deferrer(callbacks_);

  if (tcb_ != nullptr) {
    // https://tools.ietf.org/html/rfc4960#section-9.2
    // "Upon receipt of the SHUTDOWN primitive from its upper layer, the
    // endpoint enters the SHUTDOWN-PENDING state and remains there until all
    // outstanding data has been acknowledged by its peer."

    // TODO(webrtc:12739): Remove this check, as it just hides the problem that
    // the socket can transition from ShutdownSent to ShutdownPending, or
    // ShutdownAckSent to ShutdownPending which is illegal.
    if (state_ != State::kShutdownSent && state_ != State::kShutdownAckSent) {
      SetState(State::kShutdownPending, "Shutdown called");
      t1_init_->Stop();
      t1_cookie_->Stop();
      MaybeSendShutdownOrAck();
    }
  } else {
    // Connection closed before even starting to connect, or during the initial
    // connection phase. There is no outstanding data, so the socket can just
    // be closed (stopping any connection timers, if any), as this is the
    // client's intention, by calling Shutdown.
    InternalClose(ErrorKind::kNoError, "");
  }
  RTC_DCHECK(IsConsistent());
}

void DcSctpSocket::Close() {
  CallbackDeferrer::ScopedDeferrer deferrer(callbacks_);

  if (state_ != State::kClosed) {
    if (tcb_ != nullptr) {
      SctpPacket::Builder b = tcb_->PacketBuilder();
      b.Add(AbortChunk(/*filled_in_verification_tag=*/true,
                       Parameters::Builder()
                           .Add(UserInitiatedAbortCause("Close called"))
                           .Build()));
      packet_sender_.Send(b);
    }
    InternalClose(ErrorKind::kNoError, "");
  } else {
    RTC_DLOG(LS_INFO) << log_prefix() << "Called Close on a closed socket";
  }
  RTC_DCHECK(IsConsistent());
}

void DcSctpSocket::CloseConnectionBecauseOfTooManyTransmissionErrors() {
  packet_sender_.Send(tcb_->PacketBuilder().Add(AbortChunk(
      true, Parameters::Builder()
                .Add(UserInitiatedAbortCause("Too many retransmissions"))
                .Build())));
  InternalClose(ErrorKind::kTooManyRetries, "Too many retransmissions");
}

void DcSctpSocket::InternalClose(ErrorKind error, absl::string_view message) {
  if (state_ != State::kClosed) {
    t1_init_->Stop();
    t1_cookie_->Stop();
    t2_shutdown_->Stop();
    tcb_ = nullptr;

    if (error == ErrorKind::kNoError) {
      callbacks_.OnClosed();
    } else {
      callbacks_.OnAborted(error, message);
    }
    SetState(State::kClosed, message);
  }
  // This method's purpose is to abort/close and make it consistent by ensuring
  // that e.g. all timers really are stopped.
  RTC_DCHECK(IsConsistent());
}

void DcSctpSocket::SetStreamPriority(StreamID stream_id,
                                     StreamPriority priority) {
  send_queue_.SetStreamPriority(stream_id, priority);
}
StreamPriority DcSctpSocket::GetStreamPriority(StreamID stream_id) const {
  return send_queue_.GetStreamPriority(stream_id);
}

SendStatus DcSctpSocket::Send(DcSctpMessage message,
                              const SendOptions& send_options) {
  CallbackDeferrer::ScopedDeferrer deferrer(callbacks_);
  SendStatus send_status = InternalSend(message, send_options);
  if (send_status != SendStatus::kSuccess)
    return send_status;
  Timestamp now = callbacks_.Now();
  ++metrics_.tx_messages_count;
  send_queue_.Add(now, std::move(message), send_options);
  if (tcb_ != nullptr)
    tcb_->SendBufferedPackets(now);
  RTC_DCHECK(IsConsistent());
  return SendStatus::kSuccess;
}

std::vector<SendStatus> DcSctpSocket::SendMany(
    rtc::ArrayView<DcSctpMessage> messages,
    const SendOptions& send_options) {
  CallbackDeferrer::ScopedDeferrer deferrer(callbacks_);
  Timestamp now = callbacks_.Now();
  std::vector<SendStatus> send_statuses;
  send_statuses.reserve(messages.size());
  for (DcSctpMessage& message : messages) {
    SendStatus send_status = InternalSend(message, send_options);
    send_statuses.push_back(send_status);
    if (send_status != SendStatus::kSuccess)
      continue;
    ++metrics_.tx_messages_count;
    send_queue_.Add(now, std::move(message), send_options);
  }
  if (tcb_ != nullptr)
    tcb_->SendBufferedPackets(now);
  RTC_DCHECK(IsConsistent());
  return send_statuses;
}

SendStatus DcSctpSocket::InternalSend(const DcSctpMessage& message,
                                      const SendOptions& send_options) {
  LifecycleId lifecycle_id = send_options.lifecycle_id;
  if (message.payload().empty()) {
    if (lifecycle_id.IsSet()) {
      callbacks_.OnLifecycleEnd(lifecycle_id);
    }
    callbacks_.OnError(ErrorKind::kProtocolViolation,
                       "Unable to send empty message");
    return SendStatus::kErrorMessageEmpty;
  }
  if (message.payload().size() > options_.max_message_size) {
    if (lifecycle_id.IsSet()) {
      callbacks_.OnLifecycleEnd(lifecycle_id);
    }
    callbacks_.OnError(ErrorKind::kProtocolViolation,
                       "Unable to send too large message");
    return SendStatus::kErrorMessageTooLarge;
  }
  if (state_ == State::kShutdownPending || state_ == State::kShutdownSent ||
      state_ == State::kShutdownReceived || state_ == State::kShutdownAckSent) {
    // https://tools.ietf.org/html/rfc4960#section-9.2
    // "An endpoint should reject any new data request from its upper layer
    // if it is in the SHUTDOWN-PENDING, SHUTDOWN-SENT, SHUTDOWN-RECEIVED, or
    // SHUTDOWN-ACK-SENT state."
    if (lifecycle_id.IsSet()) {
      callbacks_.OnLifecycleEnd(lifecycle_id);
    }
    callbacks_.OnError(ErrorKind::kWrongSequence,
                       "Unable to send message as the socket is shutting down");
    return SendStatus::kErrorShuttingDown;
  }
  if (send_queue_.IsFull()) {
    if (lifecycle_id.IsSet()) {
      callbacks_.OnLifecycleEnd(lifecycle_id);
    }
    callbacks_.OnError(ErrorKind::kResourceExhaustion,
                       "Unable to send message as the send queue is full");
    return SendStatus::kErrorResourceExhaustion;
  }
  return SendStatus::kSuccess;
}

ResetStreamsStatus DcSctpSocket::ResetStreams(
    rtc::ArrayView<const StreamID> outgoing_streams) {
  CallbackDeferrer::ScopedDeferrer deferrer(callbacks_);

  if (tcb_ == nullptr) {
    callbacks_.OnError(ErrorKind::kWrongSequence,
                       "Can't reset streams as the socket is not connected");
    return ResetStreamsStatus::kNotConnected;
  }
  if (!tcb_->capabilities().reconfig) {
    callbacks_.OnError(ErrorKind::kUnsupportedOperation,
                       "Can't reset streams as the peer doesn't support it");
    return ResetStreamsStatus::kNotSupported;
  }

  tcb_->stream_reset_handler().ResetStreams(outgoing_streams);
  MaybeSendResetStreamsRequest();

  RTC_DCHECK(IsConsistent());
  return ResetStreamsStatus::kPerformed;
}

SocketState DcSctpSocket::state() const {
  switch (state_) {
    case State::kClosed:
      return SocketState::kClosed;
    case State::kCookieWait:
    case State::kCookieEchoed:
      return SocketState::kConnecting;
    case State::kEstablished:
      return SocketState::kConnected;
    case State::kShutdownPending:
    case State::kShutdownSent:
    case State::kShutdownReceived:
    case State::kShutdownAckSent:
      return SocketState::kShuttingDown;
  }
}

void DcSctpSocket::SetMaxMessageSize(size_t max_message_size) {
  options_.max_message_size = max_message_size;
}

size_t DcSctpSocket::buffered_amount(StreamID stream_id) const {
  return send_queue_.buffered_amount(stream_id);
}

size_t DcSctpSocket::buffered_amount_low_threshold(StreamID stream_id) const {
  return send_queue_.buffered_amount_low_threshold(stream_id);
}

void DcSctpSocket::SetBufferedAmountLowThreshold(StreamID stream_id,
                                                 size_t bytes) {
  send_queue_.SetBufferedAmountLowThreshold(stream_id, bytes);
}

absl::optional<Metrics> DcSctpSocket::GetMetrics() const {
  if (tcb_ == nullptr) {
    return absl::nullopt;
  }

  Metrics metrics = metrics_;
  metrics.cwnd_bytes = tcb_->cwnd();
  metrics.srtt_ms = tcb_->current_srtt().ms();
  size_t packet_payload_size =
      options_.mtu - SctpPacket::kHeaderSize - DataChunk::kHeaderSize;
  metrics.unack_data_count =
      tcb_->retransmission_queue().unacked_items() +
      (send_queue_.total_buffered_amount() + packet_payload_size - 1) /
          packet_payload_size;
  metrics.peer_rwnd_bytes = tcb_->retransmission_queue().rwnd();
  metrics.negotiated_maximum_incoming_streams =
      tcb_->capabilities().negotiated_maximum_incoming_streams;
  metrics.negotiated_maximum_incoming_streams =
      tcb_->capabilities().negotiated_maximum_incoming_streams;
  metrics.rtx_packets_count = tcb_->retransmission_queue().rtx_packets_count();
  metrics.rtx_bytes_count = tcb_->retransmission_queue().rtx_bytes_count();

  return metrics;
}

void DcSctpSocket::MaybeSendShutdownOnPacketReceived(const SctpPacket& packet) {
  if (state_ == State::kShutdownSent) {
    bool has_data_chunk =
        std::find_if(packet.descriptors().begin(), packet.descriptors().end(),
                     [](const SctpPacket::ChunkDescriptor& descriptor) {
                       return descriptor.type == DataChunk::kType;
                     }) != packet.descriptors().end();
    if (has_data_chunk) {
      // https://tools.ietf.org/html/rfc4960#section-9.2
      // "While in the SHUTDOWN-SENT state, the SHUTDOWN sender MUST immediately
      // respond to each received packet containing one or more DATA chunks with
      // a SHUTDOWN chunk and restart the T2-shutdown timer.""
      SendShutdown();
      t2_shutdown_->set_duration(tcb_->current_rto());
      t2_shutdown_->Start();
    }
  }
}

void DcSctpSocket::MaybeSendResetStreamsRequest() {
  absl::optional<ReConfigChunk> reconfig =
      tcb_->stream_reset_handler().MakeStreamResetRequest();
  if (reconfig.has_value()) {
    SctpPacket::Builder builder = tcb_->PacketBuilder();
    builder.Add(*reconfig);
    packet_sender_.Send(builder);
  }
}

bool DcSctpSocket::ValidatePacket(const SctpPacket& packet) {
  const CommonHeader& header = packet.common_header();
  VerificationTag my_verification_tag =
      tcb_ != nullptr ? tcb_->my_verification_tag() : VerificationTag(0);

  if (header.verification_tag == VerificationTag(0)) {
    if (packet.descriptors().size() == 1 &&
        packet.descriptors()[0].type == InitChunk::kType) {
      // https://tools.ietf.org/html/rfc4960#section-8.5.1
      // "When an endpoint receives an SCTP packet with the Verification Tag
      // set to 0, it should verify that the packet contains only an INIT chunk.
      // Otherwise, the receiver MUST silently discard the packet.""
      return true;
    }
    callbacks_.OnError(
        ErrorKind::kParseFailed,
        "Only a single INIT chunk can be present in packets sent on "
        "verification_tag = 0");
    return false;
  }

  if (packet.descriptors().size() == 1 &&
      packet.descriptors()[0].type == AbortChunk::kType) {
    // https://tools.ietf.org/html/rfc4960#section-8.5.1
    // "The receiver of an ABORT MUST accept the packet if the Verification
    // Tag field of the packet matches its own tag and the T bit is not set OR
    // if it is set to its peer's tag and the T bit is set in the Chunk Flags.
    // Otherwise, the receiver MUST silently discard the packet and take no
    // further action."
    bool t_bit = (packet.descriptors()[0].flags & 0x01) != 0;
    if (t_bit && tcb_ == nullptr) {
      // Can't verify the tag - assume it's okey.
      return true;
    }
    if ((!t_bit && header.verification_tag == my_verification_tag) ||
        (t_bit && header.verification_tag == tcb_->peer_verification_tag())) {
      return true;
    }
    callbacks_.OnError(ErrorKind::kParseFailed,
                       "ABORT chunk verification tag was wrong");
    return false;
  }

  if (packet.descriptors()[0].type == InitAckChunk::kType) {
    if (header.verification_tag == connect_params_.verification_tag) {
      return true;
    }
    callbacks_.OnError(
        ErrorKind::kParseFailed,
        rtc::StringFormat(
            "Packet has invalid verification tag: %08x, expected %08x",
            *header.verification_tag, *connect_params_.verification_tag));
    return false;
  }

  if (packet.descriptors()[0].type == CookieEchoChunk::kType) {
    // Handled in chunk handler (due to RFC 4960, section 5.2.4).
    return true;
  }

  if (packet.descriptors().size() == 1 &&
      packet.descriptors()[0].type == ShutdownCompleteChunk::kType) {
    // https://tools.ietf.org/html/rfc4960#section-8.5.1
    // "The receiver of a SHUTDOWN COMPLETE shall accept the packet if the
    // Verification Tag field of the packet matches its own tag and the T bit is
    // not set OR if it is set to its peer's tag and the T bit is set in the
    // Chunk Flags.  Otherwise, the receiver MUST silently discard the packet
    // and take no further action."
    bool t_bit = (packet.descriptors()[0].flags & 0x01) != 0;
    if (t_bit && tcb_ == nullptr) {
      // Can't verify the tag - assume it's okey.
      return true;
    }
    if ((!t_bit && header.verification_tag == my_verification_tag) ||
        (t_bit && header.verification_tag == tcb_->peer_verification_tag())) {
      return true;
    }
    callbacks_.OnError(ErrorKind::kParseFailed,
                       "SHUTDOWN_COMPLETE chunk verification tag was wrong");
    return false;
  }

  // https://tools.ietf.org/html/rfc4960#section-8.5
  // "When receiving an SCTP packet, the endpoint MUST ensure that the value
  // in the Verification Tag field of the received SCTP packet matches its own
  // tag.  If the received Verification Tag value does not match the receiver's
  // own tag value, the receiver shall silently discard the packet and shall not
  // process it any further..."
  if (header.verification_tag == my_verification_tag) {
    return true;
  }

  callbacks_.OnError(
      ErrorKind::kParseFailed,
      rtc::StringFormat(
          "Packet has invalid verification tag: %08x, expected %08x",
          *header.verification_tag, *my_verification_tag));
  return false;
}

void DcSctpSocket::HandleTimeout(TimeoutID timeout_id) {
  CallbackDeferrer::ScopedDeferrer deferrer(callbacks_);

  timer_manager_.HandleTimeout(timeout_id);

  if (tcb_ != nullptr && tcb_->HasTooManyTxErrors()) {
    // Tearing down the TCB has to be done outside the handlers.
    CloseConnectionBecauseOfTooManyTransmissionErrors();
  }

  RTC_DCHECK(IsConsistent());
}

void DcSctpSocket::ReceivePacket(rtc::ArrayView<const uint8_t> data) {
  CallbackDeferrer::ScopedDeferrer deferrer(callbacks_);

  ++metrics_.rx_packets_count;

  if (packet_observer_ != nullptr) {
    packet_observer_->OnReceivedPacket(TimeMs(callbacks_.Now().ms()), data);
  }

  absl::optional<SctpPacket> packet = SctpPacket::Parse(data, options_);
  if (!packet.has_value()) {
    // https://tools.ietf.org/html/rfc4960#section-6.8
    // "The default procedure for handling invalid SCTP packets is to
    // silently discard them."
    callbacks_.OnError(ErrorKind::kParseFailed,
                       "Failed to parse received SCTP packet");
    RTC_DCHECK(IsConsistent());
    return;
  }

  if (RTC_DLOG_IS_ON) {
    for (const auto& descriptor : packet->descriptors()) {
      RTC_DLOG(LS_VERBOSE) << log_prefix() << "Received "
                           << DebugConvertChunkToString(descriptor.data);
    }
  }

  if (!ValidatePacket(*packet)) {
    RTC_DLOG(LS_VERBOSE) << log_prefix()
                         << "Packet failed verification tag check - dropping";
    RTC_DCHECK(IsConsistent());
    return;
  }

  MaybeSendShutdownOnPacketReceived(*packet);

  for (const auto& descriptor : packet->descriptors()) {
    if (!Dispatch(packet->common_header(), descriptor)) {
      break;
    }
  }

  if (tcb_ != nullptr) {
    tcb_->data_tracker().ObservePacketEnd();
    tcb_->MaybeSendSack();
  }

  RTC_DCHECK(IsConsistent());
}

void DcSctpSocket::DebugPrintOutgoing(rtc::ArrayView<const uint8_t> payload) {
  auto packet = SctpPacket::Parse(payload, options_);
  RTC_DCHECK(packet.has_value());

  for (const auto& desc : packet->descriptors()) {
    RTC_DLOG(LS_VERBOSE) << log_prefix() << "Sent "
                         << DebugConvertChunkToString(desc.data);
  }
}

bool DcSctpSocket::Dispatch(const CommonHeader& header,
                            const SctpPacket::ChunkDescriptor& descriptor) {
  switch (descriptor.type) {
    case DataChunk::kType:
      HandleData(header, descriptor);
      break;
    case InitChunk::kType:
      HandleInit(header, descriptor);
      break;
    case InitAckChunk::kType:
      HandleInitAck(header, descriptor);
      break;
    case SackChunk::kType:
      HandleSack(header, descriptor);
      break;
    case HeartbeatRequestChunk::kType:
      HandleHeartbeatRequest(header, descriptor);
      break;
    case HeartbeatAckChunk::kType:
      HandleHeartbeatAck(header, descriptor);
      break;
    case AbortChunk::kType:
      HandleAbort(header, descriptor);
      break;
    case ErrorChunk::kType:
      HandleError(header, descriptor);
      break;
    case CookieEchoChunk::kType:
      HandleCookieEcho(header, descriptor);
      break;
    case CookieAckChunk::kType:
      HandleCookieAck(header, descriptor);
      break;
    case ShutdownChunk::kType:
      HandleShutdown(header, descriptor);
      break;
    case ShutdownAckChunk::kType:
      HandleShutdownAck(header, descriptor);
      break;
    case ShutdownCompleteChunk::kType:
      HandleShutdownComplete(header, descriptor);
      break;
    case ReConfigChunk::kType:
      HandleReconfig(header, descriptor);
      break;
    case ForwardTsnChunk::kType:
      HandleForwardTsn(header, descriptor);
      break;
    case IDataChunk::kType:
      HandleIData(header, descriptor);
      break;
    case IForwardTsnChunk::kType:
      HandleIForwardTsn(header, descriptor);
      break;
    default:
      return HandleUnrecognizedChunk(descriptor);
  }
  return true;
}

bool DcSctpSocket::HandleUnrecognizedChunk(
    const SctpPacket::ChunkDescriptor& descriptor) {
  bool report_as_error = (descriptor.type & 0x40) != 0;
  bool continue_processing = (descriptor.type & 0x80) != 0;
  RTC_DLOG(LS_VERBOSE) << log_prefix() << "Received unknown chunk: "
                       << static_cast<int>(descriptor.type);
  if (report_as_error) {
    rtc::StringBuilder sb;
    sb << "Received unknown chunk of type: "
       << static_cast<int>(descriptor.type) << " with report-error bit set";
    callbacks_.OnError(ErrorKind::kParseFailed, sb.str());
    RTC_DLOG(LS_VERBOSE)
        << log_prefix()
        << "Unknown chunk, with type indicating it should be reported.";

    // https://tools.ietf.org/html/rfc4960#section-3.2
    // "... report in an ERROR chunk using the 'Unrecognized Chunk Type'
    // cause."
    if (tcb_ != nullptr) {
      // Need TCB - this chunk must be sent with a correct verification tag.
      packet_sender_.Send(tcb_->PacketBuilder().Add(
          ErrorChunk(Parameters::Builder()
                         .Add(UnrecognizedChunkTypeCause(std::vector<uint8_t>(
                             descriptor.data.begin(), descriptor.data.end())))
                         .Build())));
    }
  }
  if (!continue_processing) {
    // https://tools.ietf.org/html/rfc4960#section-3.2
    // "Stop processing this SCTP packet and discard it, do not process any
    // further chunks within it."
    RTC_DLOG(LS_VERBOSE) << log_prefix()
                         << "Unknown chunk, with type indicating not to "
                            "process any further chunks";
  }

  return continue_processing;
}

TimeDelta DcSctpSocket::OnInitTimerExpiry() {
  RTC_DLOG(LS_VERBOSE) << log_prefix() << "Timer " << t1_init_->name()
                       << " has expired: " << t1_init_->expiration_count()
                       << "/" << t1_init_->options().max_restarts.value_or(-1);
  RTC_DCHECK(state_ == State::kCookieWait);

  if (t1_init_->is_running()) {
    SendInit();
  } else {
    InternalClose(ErrorKind::kTooManyRetries, "No INIT_ACK received");
  }
  RTC_DCHECK(IsConsistent());
  return TimeDelta::Zero();
}

TimeDelta DcSctpSocket::OnCookieTimerExpiry() {
  // https://tools.ietf.org/html/rfc4960#section-4
  // "If the T1-cookie timer expires, the endpoint MUST retransmit COOKIE
  // ECHO and restart the T1-cookie timer without changing state.  This MUST
  // be repeated up to 'Max.Init.Retransmits' times. After that, the endpoint
  // MUST abort the initialization process and report the error to the SCTP
  // user."
  RTC_DLOG(LS_VERBOSE) << log_prefix() << "Timer " << t1_cookie_->name()
                       << " has expired: " << t1_cookie_->expiration_count()
                       << "/"
                       << t1_cookie_->options().max_restarts.value_or(-1);

  RTC_DCHECK(state_ == State::kCookieEchoed);

  if (t1_cookie_->is_running()) {
    tcb_->SendBufferedPackets(callbacks_.Now());
  } else {
    InternalClose(ErrorKind::kTooManyRetries, "No COOKIE_ACK received");
  }

  RTC_DCHECK(IsConsistent());
  return TimeDelta::Zero();
}

TimeDelta DcSctpSocket::OnShutdownTimerExpiry() {
  RTC_DLOG(LS_VERBOSE) << log_prefix() << "Timer " << t2_shutdown_->name()
                       << " has expired: " << t2_shutdown_->expiration_count()
                       << "/"
                       << t2_shutdown_->options().max_restarts.value_or(-1);

  if (!t2_shutdown_->is_running()) {
    // https://tools.ietf.org/html/rfc4960#section-9.2
    // "An endpoint should limit the number of retransmissions of the SHUTDOWN
    // chunk to the protocol parameter 'Association.Max.Retrans'. If this
    // threshold is exceeded, the endpoint should destroy the TCB..."

    packet_sender_.Send(tcb_->PacketBuilder().Add(
        AbortChunk(true, Parameters::Builder()
                             .Add(UserInitiatedAbortCause(
                                 "Too many retransmissions of SHUTDOWN"))
                             .Build())));

    InternalClose(ErrorKind::kTooManyRetries, "No SHUTDOWN_ACK received");
    RTC_DCHECK(IsConsistent());
    return TimeDelta::Zero();
  }

  // https://tools.ietf.org/html/rfc4960#section-9.2
  // "If the timer expires, the endpoint must resend the SHUTDOWN with the
  // updated last sequential TSN received from its peer."
  SendShutdown();
  RTC_DCHECK(IsConsistent());
  return tcb_->current_rto();
}

void DcSctpSocket::OnSentPacket(rtc::ArrayView<const uint8_t> packet,
                                SendPacketStatus status) {
  // The packet observer is invoked even if the packet was failed to be sent, to
  // indicate an attempt was made.
  if (packet_observer_ != nullptr) {
    packet_observer_->OnSentPacket(TimeMs(callbacks_.Now().ms()), packet);
  }

  if (status == SendPacketStatus::kSuccess) {
    if (RTC_DLOG_IS_ON) {
      DebugPrintOutgoing(packet);
    }

    // The heartbeat interval timer is restarted for every sent packet, to
    // fire when the outgoing channel is inactive.
    if (tcb_ != nullptr) {
      tcb_->heartbeat_handler().RestartTimer();
    }

    ++metrics_.tx_packets_count;
  }
}

bool DcSctpSocket::ValidateHasTCB() {
  if (tcb_ != nullptr) {
    return true;
  }

  callbacks_.OnError(
      ErrorKind::kNotConnected,
      "Received unexpected commands on socket that is not connected");
  return false;
}

void DcSctpSocket::ReportFailedToParseChunk(int chunk_type) {
  rtc::StringBuilder sb;
  sb << "Failed to parse chunk of type: " << chunk_type;
  callbacks_.OnError(ErrorKind::kParseFailed, sb.str());
}

void DcSctpSocket::HandleData(const CommonHeader& header,
                              const SctpPacket::ChunkDescriptor& descriptor) {
  absl::optional<DataChunk> chunk = DataChunk::Parse(descriptor.data);
  if (ValidateParseSuccess(chunk) && ValidateHasTCB()) {
    HandleDataCommon(*chunk);
  }
}

void DcSctpSocket::HandleIData(const CommonHeader& header,
                               const SctpPacket::ChunkDescriptor& descriptor) {
  absl::optional<IDataChunk> chunk = IDataChunk::Parse(descriptor.data);
  if (ValidateParseSuccess(chunk) && ValidateHasTCB()) {
    HandleDataCommon(*chunk);
  }
}

void DcSctpSocket::HandleDataCommon(AnyDataChunk& chunk) {
  TSN tsn = chunk.tsn();
  AnyDataChunk::ImmediateAckFlag immediate_ack = chunk.options().immediate_ack;
  Data data = std::move(chunk).extract();

  if (data.payload.empty()) {
    // Empty DATA chunks are illegal.
    packet_sender_.Send(tcb_->PacketBuilder().Add(
        ErrorChunk(Parameters::Builder().Add(NoUserDataCause(tsn)).Build())));
    callbacks_.OnError(ErrorKind::kProtocolViolation,
                       "Received DATA chunk with no user data");
    return;
  }

  RTC_DLOG(LS_VERBOSE) << log_prefix() << "Handle DATA, queue_size="
                       << tcb_->reassembly_queue().queued_bytes()
                       << ", water_mark="
                       << tcb_->reassembly_queue().watermark_bytes()
                       << ", full=" << tcb_->reassembly_queue().is_full()
                       << ", above="
                       << tcb_->reassembly_queue().is_above_watermark();

  if (tcb_->reassembly_queue().is_full()) {
    // If the reassembly queue is full, there is nothing that can be done. The
    // specification only allows dropping gap-ack-blocks, and that's not
    // likely to help as the socket has been trying to fill gaps since the
    // watermark was reached.
    packet_sender_.Send(tcb_->PacketBuilder().Add(AbortChunk(
        true, Parameters::Builder().Add(OutOfResourceErrorCause()).Build())));
    InternalClose(ErrorKind::kResourceExhaustion,
                  "Reassembly Queue is exhausted");
    return;
  }

  if (tcb_->reassembly_queue().is_above_watermark()) {
    RTC_DLOG(LS_VERBOSE) << log_prefix() << "Is above high watermark";
    // If the reassembly queue is above its high watermark, only accept data
    // chunks that increase its cumulative ack tsn in an attempt to fill gaps
    // to deliver messages.
    if (!tcb_->data_tracker().will_increase_cum_ack_tsn(tsn)) {
      RTC_DLOG(LS_VERBOSE) << log_prefix()
                           << "Rejected data because of exceeding watermark";
      tcb_->data_tracker().ForceImmediateSack();
      return;
    }
  }

  if (!tcb_->data_tracker().IsTSNValid(tsn)) {
    RTC_DLOG(LS_VERBOSE) << log_prefix()
                         << "Rejected data because of failing TSN validity";
    return;
  }

  if (tcb_->data_tracker().Observe(tsn, immediate_ack)) {
    tcb_->reassembly_queue().Add(tsn, std::move(data));
    MaybeDeliverMessages();
  }
}

void DcSctpSocket::HandleInit(const CommonHeader& header,
                              const SctpPacket::ChunkDescriptor& descriptor) {
  absl::optional<InitChunk> chunk = InitChunk::Parse(descriptor.data);
  if (!ValidateParseSuccess(chunk)) {
    return;
  }

  if (chunk->initiate_tag() == VerificationTag(0) ||
      chunk->nbr_outbound_streams() == 0 || chunk->nbr_inbound_streams() == 0) {
    // https://tools.ietf.org/html/rfc4960#section-3.3.2
    // "If the value of the Initiate Tag in a received INIT chunk is found
    // to be 0, the receiver MUST treat it as an error and close the
    // association by transmitting an ABORT."

    // "A receiver of an INIT with the OS value set to 0 SHOULD abort the
    // association."

    // "A receiver of an INIT with the MIS value of 0 SHOULD abort the
    // association."

    packet_sender_.Send(
        SctpPacket::Builder(VerificationTag(0), options_)
            .Add(AbortChunk(
                /*filled_in_verification_tag=*/false,
                Parameters::Builder()
                    .Add(ProtocolViolationCause("INIT malformed"))
                    .Build())));
    InternalClose(ErrorKind::kProtocolViolation, "Received invalid INIT");
    return;
  }

  if (state_ == State::kShutdownAckSent) {
    // https://tools.ietf.org/html/rfc4960#section-9.2
    // "If an endpoint is in the SHUTDOWN-ACK-SENT state and receives an
    // INIT chunk (e.g., if the SHUTDOWN COMPLETE was lost) with source and
    // destination transport addresses (either in the IP addresses or in the
    // INIT chunk) that belong to this association, it should discard the INIT
    // chunk and retransmit the SHUTDOWN ACK chunk."
    RTC_DLOG(LS_VERBOSE) << log_prefix()
                         << "Received Init indicating lost ShutdownComplete";
    SendShutdownAck();
    return;
  }

  TieTag tie_tag(0);
  VerificationTag my_verification_tag;
  TSN my_initial_tsn;
  if (state_ == State::kClosed) {
    RTC_DLOG(LS_VERBOSE) << log_prefix()
                         << "Received Init in closed state (normal)";

    my_verification_tag = VerificationTag(
        callbacks_.GetRandomInt(kMinVerificationTag, kMaxVerificationTag));
    my_initial_tsn =
        TSN(callbacks_.GetRandomInt(kMinInitialTsn, kMaxInitialTsn));
  } else if (state_ == State::kCookieWait || state_ == State::kCookieEchoed) {
    // https://tools.ietf.org/html/rfc4960#section-5.2.1
    // "This usually indicates an initialization collision, i.e., each
    // endpoint is attempting, at about the same time, to establish an
    // association with the other endpoint. Upon receipt of an INIT in the
    // COOKIE-WAIT state, an endpoint MUST respond with an INIT ACK using the
    // same parameters it sent in its original INIT chunk (including its
    // Initiate Tag, unchanged).  When responding, the endpoint MUST send the
    // INIT ACK back to the same address that the original INIT (sent by this
    // endpoint) was sent."
    RTC_DLOG(LS_VERBOSE) << log_prefix()
                         << "Received Init indicating simultaneous connections";
    my_verification_tag = connect_params_.verification_tag;
    my_initial_tsn = connect_params_.initial_tsn;
  } else {
    RTC_DCHECK(tcb_ != nullptr);
    // https://tools.ietf.org/html/rfc4960#section-5.2.2
    // "The outbound SCTP packet containing this INIT ACK MUST carry a
    // Verification Tag value equal to the Initiate Tag found in the
    // unexpected INIT.  And the INIT ACK MUST contain a new Initiate Tag
    // (randomly generated; see Section 5.3.1).  Other parameters for the
    // endpoint SHOULD be copied from the existing parameters of the
    // association (e.g., number of outbound streams) into the INIT ACK and
    // cookie."
    RTC_DLOG(LS_VERBOSE) << log_prefix()
                         << "Received Init indicating restarted connection";
    // Create a new verification tag - different from the previous one.
    for (int tries = 0; tries < 10; ++tries) {
      my_verification_tag = VerificationTag(
          callbacks_.GetRandomInt(kMinVerificationTag, kMaxVerificationTag));
      if (my_verification_tag != tcb_->my_verification_tag()) {
        break;
      }
    }

    // Make the initial TSN make a large jump, so that there is no overlap
    // with the old and new association.
    my_initial_tsn = TSN(*tcb_->retransmission_queue().next_tsn() + 1000000);
    tie_tag = tcb_->tie_tag();
  }

  RTC_DLOG(LS_VERBOSE)
      << log_prefix()
      << rtc::StringFormat(
             "Proceeding with connection. my_verification_tag=%08x, "
             "my_initial_tsn=%u, peer_verification_tag=%08x, "
             "peer_initial_tsn=%u",
             *my_verification_tag, *my_initial_tsn, *chunk->initiate_tag(),
             *chunk->initial_tsn());

  Capabilities capabilities =
      ComputeCapabilities(options_, chunk->nbr_outbound_streams(),
                          chunk->nbr_inbound_streams(), chunk->parameters());

  SctpPacket::Builder b(chunk->initiate_tag(), options_);
  Parameters::Builder params_builder =
      Parameters::Builder().Add(StateCookieParameter(
          StateCookie(chunk->initiate_tag(), my_verification_tag,
                      chunk->initial_tsn(), my_initial_tsn, chunk->a_rwnd(),
                      tie_tag, capabilities)
              .Serialize()));
  AddCapabilityParameters(options_, capabilities.zero_checksum, params_builder);

  InitAckChunk init_ack(/*initiate_tag=*/my_verification_tag,
                        options_.max_receiver_window_buffer_size,
                        options_.announced_maximum_outgoing_streams,
                        options_.announced_maximum_incoming_streams,
                        my_initial_tsn, params_builder.Build());
  b.Add(init_ack);
  // If the peer has signaled that it supports zero checksum, INIT-ACK can then
  // have its checksum as zero.
  packet_sender_.Send(b, /*write_checksum=*/!capabilities.zero_checksum);
}

void DcSctpSocket::HandleInitAck(
    const CommonHeader& header,
    const SctpPacket::ChunkDescriptor& descriptor) {
  absl::optional<InitAckChunk> chunk = InitAckChunk::Parse(descriptor.data);
  if (!ValidateParseSuccess(chunk)) {
    return;
  }

  if (state_ != State::kCookieWait) {
    // https://tools.ietf.org/html/rfc4960#section-5.2.3
    // "If an INIT ACK is received by an endpoint in any state other than
    // the COOKIE-WAIT state, the endpoint should discard the INIT ACK chunk."
    RTC_DLOG(LS_VERBOSE) << log_prefix()
                         << "Received INIT_ACK in unexpected state";
    return;
  }

  auto cookie = chunk->parameters().get<StateCookieParameter>();
  if (!cookie.has_value()) {
    packet_sender_.Send(
        SctpPacket::Builder(connect_params_.verification_tag, options_)
            .Add(AbortChunk(
                /*filled_in_verification_tag=*/false,
                Parameters::Builder()
                    .Add(ProtocolViolationCause("INIT-ACK malformed"))
                    .Build())));
    InternalClose(ErrorKind::kProtocolViolation,
                  "InitAck chunk doesn't contain a cookie");
    return;
  }
  Capabilities capabilities =
      ComputeCapabilities(options_, chunk->nbr_outbound_streams(),
                          chunk->nbr_inbound_streams(), chunk->parameters());
  t1_init_->Stop();

  metrics_.peer_implementation = DeterminePeerImplementation(cookie->data());

  // If the connection is re-established (peer restarted, but re-used old
  // connection), make sure that all message identifiers are reset and any
  // partly sent message is re-sent in full. The same is true when the socket
  // is closed and later re-opened, which never happens in WebRTC, but is a
  // valid operation on the SCTP level. Note that in case of handover, the
  // send queue is already re-configured, and shouldn't be reset.
  send_queue_.Reset();

  CreateTransmissionControlBlock(capabilities, connect_params_.verification_tag,
                                 connect_params_.initial_tsn,
                                 chunk->initiate_tag(), chunk->initial_tsn(),
                                 chunk->a_rwnd(), MakeTieTag(callbacks_));

  SetState(State::kCookieEchoed, "INIT_ACK received");

  // The connection isn't fully established just yet.
  tcb_->SetCookieEchoChunk(CookieEchoChunk(cookie->data()));
  tcb_->SendBufferedPackets(callbacks_.Now());
  t1_cookie_->Start();
}

void DcSctpSocket::HandleCookieEcho(
    const CommonHeader& header,
    const SctpPacket::ChunkDescriptor& descriptor) {
  absl::optional<CookieEchoChunk> chunk =
      CookieEchoChunk::Parse(descriptor.data);
  if (!ValidateParseSuccess(chunk)) {
    return;
  }

  absl::optional<StateCookie> cookie =
      StateCookie::Deserialize(chunk->cookie());
  if (!cookie.has_value()) {
    callbacks_.OnError(ErrorKind::kParseFailed, "Failed to parse state cookie");
    return;
  }

  if (tcb_ != nullptr) {
    if (!HandleCookieEchoWithTCB(header, *cookie)) {
      return;
    }
  } else {
    if (header.verification_tag != cookie->my_tag()) {
      callbacks_.OnError(
          ErrorKind::kParseFailed,
          rtc::StringFormat(
              "Received CookieEcho with invalid verification tag: %08x, "
              "expected %08x",
              *header.verification_tag, *cookie->my_tag()));
      return;
    }
  }

  // The init timer can be running on simultaneous connections.
  t1_init_->Stop();
  t1_cookie_->Stop();
  if (state_ != State::kEstablished) {
    if (tcb_ != nullptr) {
      tcb_->ClearCookieEchoChunk();
    }
    SetState(State::kEstablished, "COOKIE_ECHO received");
    callbacks_.OnConnected();
  }

  if (tcb_ == nullptr) {
    // If the connection is re-established (peer restarted, but re-used old
    // connection), make sure that all message identifiers are reset and any
    // partly sent message is re-sent in full. The same is true when the socket
    // is closed and later re-opened, which never happens in WebRTC, but is a
    // valid operation on the SCTP level. Note that in case of handover, the
    // send queue is already re-configured, and shouldn't be reset.
    send_queue_.Reset();

    CreateTransmissionControlBlock(cookie->capabilities(), cookie->my_tag(),
                                   cookie->my_initial_tsn(), cookie->peer_tag(),
                                   cookie->peer_initial_tsn(), cookie->a_rwnd(),
                                   MakeTieTag(callbacks_));
  }

  SctpPacket::Builder b = tcb_->PacketBuilder();
  b.Add(CookieAckChunk());

  // https://tools.ietf.org/html/rfc4960#section-5.1
  // "A COOKIE ACK chunk may be bundled with any pending DATA chunks (and/or
  // SACK chunks), but the COOKIE ACK chunk MUST be the first chunk in the
  // packet."
  tcb_->SendBufferedPackets(b, callbacks_.Now());
}

bool DcSctpSocket::HandleCookieEchoWithTCB(const CommonHeader& header,
                                           const StateCookie& cookie) {
  RTC_DLOG(LS_VERBOSE) << log_prefix()
                       << "Handling CookieEchoChunk with TCB. local_tag="
                       << *tcb_->my_verification_tag()
                       << ", peer_tag=" << *header.verification_tag
                       << ", tcb_tag=" << *tcb_->peer_verification_tag()
                       << ", peer_tag=" << *cookie.peer_tag()
                       << ", local_tie_tag=" << *tcb_->tie_tag()
                       << ", peer_tie_tag=" << *cookie.tie_tag();
  // https://tools.ietf.org/html/rfc4960#section-5.2.4
  // "Handle a COOKIE ECHO when a TCB Exists"
  if (header.verification_tag != tcb_->my_verification_tag() &&
      tcb_->peer_verification_tag() != cookie.peer_tag() &&
      cookie.tie_tag() == tcb_->tie_tag()) {
    // "A) In this case, the peer may have restarted."
    if (state_ == State::kShutdownAckSent) {
      // "If the endpoint is in the SHUTDOWN-ACK-SENT state and recognizes
      // that the peer has restarted ...  it MUST NOT set up a new association
      // but instead resend the SHUTDOWN ACK and send an ERROR chunk with a
      // "Cookie Received While Shutting Down" error cause to its peer."
      SctpPacket::Builder b(cookie.peer_tag(), options_);
      b.Add(ShutdownAckChunk());
      b.Add(ErrorChunk(Parameters::Builder()
                           .Add(CookieReceivedWhileShuttingDownCause())
                           .Build()));
      packet_sender_.Send(b);
      callbacks_.OnError(ErrorKind::kWrongSequence,
                         "Received COOKIE-ECHO while shutting down");
      return false;
    }

    RTC_DLOG(LS_VERBOSE) << log_prefix()
                         << "Received COOKIE-ECHO indicating a restarted peer";

    tcb_ = nullptr;
    callbacks_.OnConnectionRestarted();
  } else if (header.verification_tag == tcb_->my_verification_tag() &&
             tcb_->peer_verification_tag() != cookie.peer_tag()) {
    // TODO(boivie): Handle the peer_tag == 0?
    // "B) In this case, both sides may be attempting to start an
    // association at about the same time, but the peer endpoint started its
    // INIT after responding to the local endpoint's INIT."
    RTC_DLOG(LS_VERBOSE)
        << log_prefix()
        << "Received COOKIE-ECHO indicating simultaneous connections";
    tcb_ = nullptr;
  } else if (header.verification_tag != tcb_->my_verification_tag() &&
             tcb_->peer_verification_tag() == cookie.peer_tag() &&
             cookie.tie_tag() == TieTag(0)) {
    // "C) In this case, the local endpoint's cookie has arrived late.
    // Before it arrived, the local endpoint sent an INIT and received an
    // INIT ACK and finally sent a COOKIE ECHO with the peer's same tag but
    // a new tag of its own. The cookie should be silently discarded. The
    // endpoint SHOULD NOT change states and should leave any timers
    // running."
    RTC_DLOG(LS_VERBOSE)
        << log_prefix()
        << "Received COOKIE-ECHO indicating a late COOKIE-ECHO. Discarding";
    return false;
  } else if (header.verification_tag == tcb_->my_verification_tag() &&
             tcb_->peer_verification_tag() == cookie.peer_tag()) {
    // "D) When both local and remote tags match, the endpoint should enter
    // the ESTABLISHED state, if it is in the COOKIE-ECHOED state.  It
    // should stop any cookie timer that may be running and send a COOKIE
    // ACK."
    RTC_DLOG(LS_VERBOSE)
        << log_prefix()
        << "Received duplicate COOKIE-ECHO, probably because of peer not "
           "receiving COOKIE-ACK and retransmitting COOKIE-ECHO. Continuing.";
  }
  return true;
}

void DcSctpSocket::HandleCookieAck(
    const CommonHeader& header,
    const SctpPacket::ChunkDescriptor& descriptor) {
  absl::optional<CookieAckChunk> chunk = CookieAckChunk::Parse(descriptor.data);
  if (!ValidateParseSuccess(chunk)) {
    return;
  }

  if (state_ != State::kCookieEchoed) {
    // https://tools.ietf.org/html/rfc4960#section-5.2.5
    // "At any state other than COOKIE-ECHOED, an endpoint should silently
    // discard a received COOKIE ACK chunk."
    RTC_DLOG(LS_VERBOSE) << log_prefix()
                         << "Received COOKIE_ACK not in COOKIE_ECHOED state";
    return;
  }

  // RFC 4960, Errata ID: 4400
  t1_cookie_->Stop();
  tcb_->ClearCookieEchoChunk();
  SetState(State::kEstablished, "COOKIE_ACK received");
  tcb_->SendBufferedPackets(callbacks_.Now());
  callbacks_.OnConnected();
}

void DcSctpSocket::MaybeDeliverMessages() {
  for (auto& message : tcb_->reassembly_queue().FlushMessages()) {
    ++metrics_.rx_messages_count;
    callbacks_.OnMessageReceived(std::move(message));
  }
}

void DcSctpSocket::HandleSack(const CommonHeader& header,
                              const SctpPacket::ChunkDescriptor& descriptor) {
  absl::optional<SackChunk> chunk = SackChunk::Parse(descriptor.data);

  if (ValidateParseSuccess(chunk) && ValidateHasTCB()) {
    Timestamp now = callbacks_.Now();
    SackChunk sack = ChunkValidators::Clean(*std::move(chunk));

    if (tcb_->retransmission_queue().HandleSack(now, sack)) {
      MaybeSendShutdownOrAck();
      // Receiving an ACK may make the socket go into fast recovery mode.
      // https://datatracker.ietf.org/doc/html/rfc4960#section-7.2.4
      // "Determine how many of the earliest (i.e., lowest TSN) DATA chunks
      // marked for retransmission will fit into a single packet, subject to
      // constraint of the path MTU of the destination transport address to
      // which the packet is being sent.  Call this value K. Retransmit those K
      // DATA chunks in a single packet.  When a Fast Retransmit is being
      // performed, the sender SHOULD ignore the value of cwnd and SHOULD NOT
      // delay retransmission for this single packet."
      tcb_->MaybeSendFastRetransmit();

      // Receiving an ACK will decrease outstanding bytes (maybe now below
      // cwnd?) or indicate packet loss that may result in sending FORWARD-TSN.
      tcb_->SendBufferedPackets(now);
    } else {
      RTC_DLOG(LS_VERBOSE) << log_prefix()
                           << "Dropping out-of-order SACK with TSN "
                           << *sack.cumulative_tsn_ack();
    }
  }
}

void DcSctpSocket::HandleHeartbeatRequest(
    const CommonHeader& header,
    const SctpPacket::ChunkDescriptor& descriptor) {
  absl::optional<HeartbeatRequestChunk> chunk =
      HeartbeatRequestChunk::Parse(descriptor.data);

  if (ValidateParseSuccess(chunk) && ValidateHasTCB()) {
    tcb_->heartbeat_handler().HandleHeartbeatRequest(*std::move(chunk));
  }
}

void DcSctpSocket::HandleHeartbeatAck(
    const CommonHeader& header,
    const SctpPacket::ChunkDescriptor& descriptor) {
  absl::optional<HeartbeatAckChunk> chunk =
      HeartbeatAckChunk::Parse(descriptor.data);

  if (ValidateParseSuccess(chunk) && ValidateHasTCB()) {
    tcb_->heartbeat_handler().HandleHeartbeatAck(*std::move(chunk));
  }
}

void DcSctpSocket::HandleAbort(const CommonHeader& header,
                               const SctpPacket::ChunkDescriptor& descriptor) {
  absl::optional<AbortChunk> chunk = AbortChunk::Parse(descriptor.data);
  if (ValidateParseSuccess(chunk)) {
    std::string error_string = ErrorCausesToString(chunk->error_causes());
    if (tcb_ == nullptr) {
      // https://tools.ietf.org/html/rfc4960#section-3.3.7
      // "If an endpoint receives an ABORT with a format error or no TCB is
      // found, it MUST silently discard it."
      RTC_DLOG(LS_VERBOSE) << log_prefix() << "Received ABORT (" << error_string
                           << ") on a connection with no TCB. Ignoring";
      return;
    }

    RTC_DLOG(LS_WARNING) << log_prefix() << "Received ABORT (" << error_string
                         << ") - closing connection.";
    InternalClose(ErrorKind::kPeerReported, error_string);
  }
}

void DcSctpSocket::HandleError(const CommonHeader& header,
                               const SctpPacket::ChunkDescriptor& descriptor) {
  absl::optional<ErrorChunk> chunk = ErrorChunk::Parse(descriptor.data);
  if (ValidateParseSuccess(chunk)) {
    std::string error_string = ErrorCausesToString(chunk->error_causes());
    if (tcb_ == nullptr) {
      RTC_DLOG(LS_VERBOSE) << log_prefix() << "Received ERROR (" << error_string
                           << ") on a connection with no TCB. Ignoring";
      return;
    }

    RTC_DLOG(LS_WARNING) << log_prefix() << "Received ERROR: " << error_string;
    callbacks_.OnError(ErrorKind::kPeerReported,
                       "Peer reported error: " + error_string);
  }
}

void DcSctpSocket::HandleReconfig(
    const CommonHeader& header,
    const SctpPacket::ChunkDescriptor& descriptor) {
  Timestamp now = callbacks_.Now();
  absl::optional<ReConfigChunk> chunk = ReConfigChunk::Parse(descriptor.data);
  if (ValidateParseSuccess(chunk) && ValidateHasTCB()) {
    tcb_->stream_reset_handler().HandleReConfig(*std::move(chunk));
    // Handling this response may result in outgoing stream resets finishing
    // (either successfully or with failure). If there still are pending streams
    // that were waiting for this request to finish, continue resetting them.
    MaybeSendResetStreamsRequest();

    // If a response was processed, pending to-be-reset streams may now have
    // become unpaused. Try to send more DATA chunks.
    tcb_->SendBufferedPackets(now);

    // If it leaves "deferred reset processing", there may be chunks to deliver
    // that were queued while waiting for the stream to reset.
    MaybeDeliverMessages();
  }
}

void DcSctpSocket::HandleShutdown(
    const CommonHeader& header,
    const SctpPacket::ChunkDescriptor& descriptor) {
  if (!ValidateParseSuccess(ShutdownChunk::Parse(descriptor.data))) {
    return;
  }

  if (state_ == State::kClosed) {
    return;
  } else if (state_ == State::kCookieWait || state_ == State::kCookieEchoed) {
    // https://tools.ietf.org/html/rfc4960#section-9.2
    // "If a SHUTDOWN is received in the COOKIE-WAIT or COOKIE ECHOED state,
    // the SHUTDOWN chunk SHOULD be silently discarded."
  } else if (state_ == State::kShutdownSent) {
    // https://tools.ietf.org/html/rfc4960#section-9.2
    // "If an endpoint is in the SHUTDOWN-SENT state and receives a
    // SHUTDOWN chunk from its peer, the endpoint shall respond immediately
    // with a SHUTDOWN ACK to its peer, and move into the SHUTDOWN-ACK-SENT
    // state restarting its T2-shutdown timer."
    SendShutdownAck();
    SetState(State::kShutdownAckSent, "SHUTDOWN received");
  } else if (state_ == State::kShutdownAckSent) {
    // TODO(webrtc:12739): This condition should be removed and handled by the
    // next (state_ != State::kShutdownReceived).
    return;
  } else if (state_ != State::kShutdownReceived) {
    RTC_DLOG(LS_VERBOSE) << log_prefix()
                         << "Received SHUTDOWN - shutting down the socket";
    // https://tools.ietf.org/html/rfc4960#section-9.2
    // "Upon reception of the SHUTDOWN, the peer endpoint shall enter the
    // SHUTDOWN-RECEIVED state, stop accepting new data from its SCTP user,
    // and verify, by checking the Cumulative TSN Ack field of the chunk, that
    // all its outstanding DATA chunks have been received by the SHUTDOWN
    // sender."
    SetState(State::kShutdownReceived, "SHUTDOWN received");
    MaybeSendShutdownOrAck();
  }
}

void DcSctpSocket::HandleShutdownAck(
    const CommonHeader& header,
    const SctpPacket::ChunkDescriptor& descriptor) {
  if (!ValidateParseSuccess(ShutdownAckChunk::Parse(descriptor.data))) {
    return;
  }

  if (state_ == State::kShutdownSent || state_ == State::kShutdownAckSent) {
    // https://tools.ietf.org/html/rfc4960#section-9.2
    // "Upon the receipt of the SHUTDOWN ACK, the SHUTDOWN sender shall stop
    // the T2-shutdown timer, send a SHUTDOWN COMPLETE chunk to its peer, and
    // remove all record of the association."

    // "If an endpoint is in the SHUTDOWN-ACK-SENT state and receives a
    // SHUTDOWN ACK, it shall stop the T2-shutdown timer, send a SHUTDOWN
    // COMPLETE chunk to its peer, and remove all record of the association."

    SctpPacket::Builder b = tcb_->PacketBuilder();
    b.Add(ShutdownCompleteChunk(/*tag_reflected=*/false));
    packet_sender_.Send(b);
    InternalClose(ErrorKind::kNoError, "");
  } else {
    // https://tools.ietf.org/html/rfc4960#section-8.5.1
    // "If the receiver is in COOKIE-ECHOED or COOKIE-WAIT state
    // the procedures in Section 8.4 SHOULD be followed; in other words, it
    // should be treated as an Out Of The Blue packet."

    // https://tools.ietf.org/html/rfc4960#section-8.4
    // "If the packet contains a SHUTDOWN ACK chunk, the receiver
    // should respond to the sender of the OOTB packet with a SHUTDOWN
    // COMPLETE. When sending the SHUTDOWN COMPLETE, the receiver of the OOTB
    // packet must fill in the Verification Tag field of the outbound packet
    // with the Verification Tag received in the SHUTDOWN ACK and set the T
    // bit in the Chunk Flags to indicate that the Verification Tag is
    // reflected."

    SctpPacket::Builder b(header.verification_tag, options_);
    b.Add(ShutdownCompleteChunk(/*tag_reflected=*/true));
    packet_sender_.Send(b);
  }
}

void DcSctpSocket::HandleShutdownComplete(
    const CommonHeader& header,
    const SctpPacket::ChunkDescriptor& descriptor) {
  if (!ValidateParseSuccess(ShutdownCompleteChunk::Parse(descriptor.data))) {
    return;
  }

  if (state_ == State::kShutdownAckSent) {
    // https://tools.ietf.org/html/rfc4960#section-9.2
    // "Upon reception of the SHUTDOWN COMPLETE chunk, the endpoint will
    // verify that it is in the SHUTDOWN-ACK-SENT state; if it is not, the
    // chunk should be discarded.  If the endpoint is in the SHUTDOWN-ACK-SENT
    // state, the endpoint should stop the T2-shutdown timer and remove all
    // knowledge of the association (and thus the association enters the
    // CLOSED state)."
    InternalClose(ErrorKind::kNoError, "");
  }
}

void DcSctpSocket::HandleForwardTsn(
    const CommonHeader& header,
    const SctpPacket::ChunkDescriptor& descriptor) {
  absl::optional<ForwardTsnChunk> chunk =
      ForwardTsnChunk::Parse(descriptor.data);
  if (ValidateParseSuccess(chunk) && ValidateHasTCB()) {
    HandleForwardTsnCommon(*chunk);
  }
}

void DcSctpSocket::HandleIForwardTsn(
    const CommonHeader& header,
    const SctpPacket::ChunkDescriptor& descriptor) {
  absl::optional<IForwardTsnChunk> chunk =
      IForwardTsnChunk::Parse(descriptor.data);
  if (ValidateParseSuccess(chunk) && ValidateHasTCB()) {
    HandleForwardTsnCommon(*chunk);
  }
}

void DcSctpSocket::HandleForwardTsnCommon(const AnyForwardTsnChunk& chunk) {
  if (!tcb_->capabilities().partial_reliability) {
    SctpPacket::Builder b = tcb_->PacketBuilder();
    b.Add(AbortChunk(/*filled_in_verification_tag=*/true,
                     Parameters::Builder()
                         .Add(ProtocolViolationCause(
                             "I-FORWARD-TSN received, but not indicated "
                             "during connection establishment"))
                         .Build()));
    packet_sender_.Send(b);

    callbacks_.OnError(ErrorKind::kProtocolViolation,
                       "Received a FORWARD_TSN without announced peer support");
    return;
  }
  if (tcb_->data_tracker().HandleForwardTsn(chunk.new_cumulative_tsn())) {
    tcb_->reassembly_queue().HandleForwardTsn(chunk.new_cumulative_tsn(),
                                              chunk.skipped_streams());
  }

  // A forward TSN - for ordered streams - may allow messages to be delivered.
  MaybeDeliverMessages();
}

void DcSctpSocket::MaybeSendShutdownOrAck() {
  if (tcb_->retransmission_queue().unacked_bytes() != 0) {
    return;
  }

  if (state_ == State::kShutdownPending) {
    // https://tools.ietf.org/html/rfc4960#section-9.2
    // "Once all its outstanding data has been acknowledged, the endpoint
    // shall send a SHUTDOWN chunk to its peer including in the Cumulative TSN
    // Ack field the last sequential TSN it has received from the peer. It
    // shall then start the T2-shutdown timer and enter the SHUTDOWN-SENT
    // state.""

    SendShutdown();
    t2_shutdown_->set_duration(tcb_->current_rto());
    t2_shutdown_->Start();
    SetState(State::kShutdownSent, "No more outstanding data");
  } else if (state_ == State::kShutdownReceived) {
    // https://tools.ietf.org/html/rfc4960#section-9.2
    // "If the receiver of the SHUTDOWN has no more outstanding DATA
    // chunks, the SHUTDOWN receiver MUST send a SHUTDOWN ACK and start a
    // T2-shutdown timer of its own, entering the SHUTDOWN-ACK-SENT state.  If
    // the timer expires, the endpoint must resend the SHUTDOWN ACK."

    SendShutdownAck();
    SetState(State::kShutdownAckSent, "No more outstanding data");
  }
}

void DcSctpSocket::SendShutdown() {
  SctpPacket::Builder b = tcb_->PacketBuilder();
  b.Add(ShutdownChunk(tcb_->data_tracker().last_cumulative_acked_tsn()));
  packet_sender_.Send(b);
}

void DcSctpSocket::SendShutdownAck() {
  packet_sender_.Send(tcb_->PacketBuilder().Add(ShutdownAckChunk()));
  t2_shutdown_->set_duration(tcb_->current_rto());
  t2_shutdown_->Start();
}

HandoverReadinessStatus DcSctpSocket::GetHandoverReadiness() const {
  HandoverReadinessStatus status;
  if (state_ != State::kClosed && state_ != State::kEstablished) {
    status.Add(HandoverUnreadinessReason::kWrongConnectionState);
  }
  status.Add(send_queue_.GetHandoverReadiness());
  if (tcb_) {
    status.Add(tcb_->GetHandoverReadiness());
  }
  return status;
}

absl::optional<DcSctpSocketHandoverState>
DcSctpSocket::GetHandoverStateAndClose() {
  CallbackDeferrer::ScopedDeferrer deferrer(callbacks_);

  if (!GetHandoverReadiness().IsReady()) {
    return absl::nullopt;
  }

  DcSctpSocketHandoverState state;

  if (state_ == State::kClosed) {
    state.socket_state = DcSctpSocketHandoverState::SocketState::kClosed;
  } else if (state_ == State::kEstablished) {
    state.socket_state = DcSctpSocketHandoverState::SocketState::kConnected;
    tcb_->AddHandoverState(state);
    send_queue_.AddHandoverState(state);
    InternalClose(ErrorKind::kNoError, "handover");
  }

  return std::move(state);
}

}  // namespace dcsctp
