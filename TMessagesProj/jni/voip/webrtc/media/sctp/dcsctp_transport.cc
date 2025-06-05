/*
 *  Copyright 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/sctp/dcsctp_transport.h"

#include <atomic>
#include <cstdint>
#include <limits>
#include <utility>
#include <vector>

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "media/base/media_channel.h"
#include "net/dcsctp/public/dcsctp_socket_factory.h"
#include "net/dcsctp/public/packet_observer.h"
#include "net/dcsctp/public/text_pcap_packet_observer.h"
#include "net/dcsctp/public/types.h"
#include "p2p/base/packet_transport_internal.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/socket.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/thread.h"
#include "rtc_base/trace_event.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

namespace {
using ::dcsctp::SendPacketStatus;

// When there is packet loss for a long time, the SCTP retry timers will use
// exponential backoff, which can grow to very long durations and when the
// connection recovers, it may take a long time to reach the new backoff
// duration. By limiting it to a reasonable limit, the time to recover reduces.
constexpr dcsctp::DurationMs kMaxTimerBackoffDuration =
    dcsctp::DurationMs(3000);

enum class WebrtcPPID : dcsctp::PPID::UnderlyingType {
  // https://www.rfc-editor.org/rfc/rfc8832.html#section-8.1
  kDCEP = 50,
  // https://www.rfc-editor.org/rfc/rfc8831.html#section-8
  kString = 51,
  kBinaryPartial = 52,  // Deprecated
  kBinary = 53,
  kStringPartial = 54,  // Deprecated
  kStringEmpty = 56,
  kBinaryEmpty = 57,
};

WebrtcPPID ToPPID(DataMessageType message_type, size_t size) {
  switch (message_type) {
    case DataMessageType::kControl:
      return WebrtcPPID::kDCEP;
    case DataMessageType::kText:
      return size > 0 ? WebrtcPPID::kString : WebrtcPPID::kStringEmpty;
    case DataMessageType::kBinary:
      return size > 0 ? WebrtcPPID::kBinary : WebrtcPPID::kBinaryEmpty;
  }
}

absl::optional<DataMessageType> ToDataMessageType(dcsctp::PPID ppid) {
  switch (static_cast<WebrtcPPID>(ppid.value())) {
    case WebrtcPPID::kDCEP:
      return DataMessageType::kControl;
    case WebrtcPPID::kString:
    case WebrtcPPID::kStringPartial:
    case WebrtcPPID::kStringEmpty:
      return DataMessageType::kText;
    case WebrtcPPID::kBinary:
    case WebrtcPPID::kBinaryPartial:
    case WebrtcPPID::kBinaryEmpty:
      return DataMessageType::kBinary;
  }
  return absl::nullopt;
}

absl::optional<cricket::SctpErrorCauseCode> ToErrorCauseCode(
    dcsctp::ErrorKind error) {
  switch (error) {
    case dcsctp::ErrorKind::kParseFailed:
      return cricket::SctpErrorCauseCode::kUnrecognizedParameters;
    case dcsctp::ErrorKind::kPeerReported:
      return cricket::SctpErrorCauseCode::kUserInitiatedAbort;
    case dcsctp::ErrorKind::kWrongSequence:
    case dcsctp::ErrorKind::kProtocolViolation:
      return cricket::SctpErrorCauseCode::kProtocolViolation;
    case dcsctp::ErrorKind::kResourceExhaustion:
      return cricket::SctpErrorCauseCode::kOutOfResource;
    case dcsctp::ErrorKind::kTooManyRetries:
    case dcsctp::ErrorKind::kUnsupportedOperation:
    case dcsctp::ErrorKind::kNoError:
    case dcsctp::ErrorKind::kNotConnected:
      // No SCTP error cause code matches those
      break;
  }
  return absl::nullopt;
}

bool IsEmptyPPID(dcsctp::PPID ppid) {
  WebrtcPPID webrtc_ppid = static_cast<WebrtcPPID>(ppid.value());
  return webrtc_ppid == WebrtcPPID::kStringEmpty ||
         webrtc_ppid == WebrtcPPID::kBinaryEmpty;
}
}  // namespace

DcSctpTransport::DcSctpTransport(rtc::Thread* network_thread,
                                 rtc::PacketTransportInternal* transport,
                                 Clock* clock)
    : DcSctpTransport(network_thread,
                      transport,
                      clock,
                      std::make_unique<dcsctp::DcSctpSocketFactory>()) {}

DcSctpTransport::DcSctpTransport(
    rtc::Thread* network_thread,
    rtc::PacketTransportInternal* transport,
    Clock* clock,
    std::unique_ptr<dcsctp::DcSctpSocketFactory> socket_factory)
    : network_thread_(network_thread),
      transport_(transport),
      clock_(clock),
      random_(clock_->TimeInMicroseconds()),
      socket_factory_(std::move(socket_factory)),
      task_queue_timeout_factory_(
          *network_thread,
          [this]() { return TimeMillis(); },
          [this](dcsctp::TimeoutID timeout_id) {
            socket_->HandleTimeout(timeout_id);
          }) {
  RTC_DCHECK_RUN_ON(network_thread_);
  static std::atomic<int> instance_count = 0;
  rtc::StringBuilder sb;
  sb << debug_name_ << instance_count++;
  debug_name_ = sb.Release();
  ConnectTransportSignals();
}

DcSctpTransport::~DcSctpTransport() {
  if (socket_) {
    socket_->Close();
  }
}

void DcSctpTransport::SetOnConnectedCallback(std::function<void()> callback) {
  RTC_DCHECK_RUN_ON(network_thread_);
  on_connected_callback_ = std::move(callback);
}

void DcSctpTransport::SetDataChannelSink(DataChannelSink* sink) {
  RTC_DCHECK_RUN_ON(network_thread_);
  data_channel_sink_ = sink;
  if (data_channel_sink_ && ready_to_send_data_) {
    data_channel_sink_->OnReadyToSend();
  }
}

void DcSctpTransport::SetDtlsTransport(
    rtc::PacketTransportInternal* transport) {
  RTC_DCHECK_RUN_ON(network_thread_);
  DisconnectTransportSignals();
  transport_ = transport;
  ConnectTransportSignals();
  MaybeConnectSocket();
}

bool DcSctpTransport::Start(int local_sctp_port,
                            int remote_sctp_port,
                            int max_message_size) {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_DCHECK(max_message_size > 0);
  RTC_DLOG(LS_INFO) << debug_name_ << "->Start(local=" << local_sctp_port
                    << ", remote=" << remote_sctp_port
                    << ", max_message_size=" << max_message_size << ")";

  if (!socket_) {
    dcsctp::DcSctpOptions options;
    options.local_port = local_sctp_port;
    options.remote_port = remote_sctp_port;
    options.max_message_size = max_message_size;
    options.max_timer_backoff_duration = kMaxTimerBackoffDuration;
    // Don't close the connection automatically on too many retransmissions.
    options.max_retransmissions = absl::nullopt;
    options.max_init_retransmits = absl::nullopt;

    std::unique_ptr<dcsctp::PacketObserver> packet_observer;
    if (RTC_LOG_CHECK_LEVEL(LS_VERBOSE)) {
      packet_observer =
          std::make_unique<dcsctp::TextPcapPacketObserver>(debug_name_);
    }

    socket_ = socket_factory_->Create(debug_name_, *this,
                                      std::move(packet_observer), options);
  } else {
    if (local_sctp_port != socket_->options().local_port ||
        remote_sctp_port != socket_->options().remote_port) {
      RTC_LOG(LS_ERROR)
          << debug_name_ << "->Start(local=" << local_sctp_port
          << ", remote=" << remote_sctp_port
          << "): Can't change ports on already started transport.";
      return false;
    }
    socket_->SetMaxMessageSize(max_message_size);
  }

  MaybeConnectSocket();

  return true;
}

bool DcSctpTransport::OpenStream(int sid) {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_DLOG(LS_INFO) << debug_name_ << "->OpenStream(" << sid << ").";

  StreamState stream_state;
  stream_states_.insert_or_assign(dcsctp::StreamID(static_cast<uint16_t>(sid)),
                                  stream_state);
  return true;
}

bool DcSctpTransport::ResetStream(int sid) {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_DLOG(LS_INFO) << debug_name_ << "->ResetStream(" << sid << ").";
  if (!socket_) {
    RTC_LOG(LS_ERROR) << debug_name_ << "->ResetStream(sid=" << sid
                      << "): Transport is not started.";
    return false;
  }

  dcsctp::StreamID streams[1] = {dcsctp::StreamID(static_cast<uint16_t>(sid))};

  auto it = stream_states_.find(streams[0]);
  if (it == stream_states_.end()) {
    RTC_LOG(LS_ERROR) << debug_name_ << "->ResetStream(sid=" << sid
                      << "): Stream is not open.";
    return false;
  }

  StreamState& stream_state = it->second;
  if (stream_state.closure_initiated || stream_state.incoming_reset_done ||
      stream_state.outgoing_reset_done) {
    // The closing procedure was already initiated by the remote, don't do
    // anything.
    return false;
  }
  stream_state.closure_initiated = true;
  socket_->ResetStreams(streams);
  return true;
}

RTCError DcSctpTransport::SendData(int sid,
                                   const SendDataParams& params,
                                   const rtc::CopyOnWriteBuffer& payload) {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_DLOG(LS_VERBOSE) << debug_name_ << "->SendData(sid=" << sid
                       << ", type=" << static_cast<int>(params.type)
                       << ", length=" << payload.size() << ").";

  if (!socket_) {
    RTC_LOG(LS_ERROR) << debug_name_
                      << "->SendData(...): Transport is not started.";
    return RTCError(RTCErrorType::INVALID_STATE);
  }

  // It is possible for a message to be sent from the signaling thread at the
  // same time a data-channel is closing, but before the signaling thread is
  // aware of it. So we need to keep track of currently active data channels and
  // skip sending messages for the ones that are not open or closing.
  // The sending errors are not impacting the data channel API contract as
  // it is allowed to discard queued messages when the channel is closing.
  auto stream_state =
      stream_states_.find(dcsctp::StreamID(static_cast<uint16_t>(sid)));
  if (stream_state == stream_states_.end()) {
    RTC_LOG(LS_VERBOSE) << "Skipping message on non-open stream with sid: "
                        << sid;
    return RTCError(RTCErrorType::INVALID_STATE);
  }

  if (stream_state->second.closure_initiated ||
      stream_state->second.incoming_reset_done ||
      stream_state->second.outgoing_reset_done) {
    RTC_LOG(LS_VERBOSE) << "Skipping message on closing stream with sid: "
                        << sid;
    return RTCError(RTCErrorType::INVALID_STATE);
  }

  auto max_message_size = socket_->options().max_message_size;
  if (max_message_size > 0 && payload.size() > max_message_size) {
    RTC_LOG(LS_WARNING) << debug_name_
                        << "->SendData(...): "
                           "Trying to send packet bigger "
                           "than the max message size: "
                        << payload.size() << " vs max of " << max_message_size;
    return RTCError(RTCErrorType::INVALID_RANGE);
  }

  std::vector<uint8_t> message_payload(payload.cdata(),
                                       payload.cdata() + payload.size());
  if (message_payload.empty()) {
    // https://www.rfc-editor.org/rfc/rfc8831.html#section-6.6
    // SCTP does not support the sending of empty user messages. Therefore, if
    // an empty message has to be sent, the appropriate PPID (WebRTC String
    // Empty or WebRTC Binary Empty) is used, and the SCTP user message of one
    // zero byte is sent.
    message_payload.push_back('\0');
  }

  dcsctp::DcSctpMessage message(
      dcsctp::StreamID(static_cast<uint16_t>(sid)),
      dcsctp::PPID(static_cast<uint16_t>(ToPPID(params.type, payload.size()))),
      std::move(message_payload));

  dcsctp::SendOptions send_options;
  send_options.unordered = dcsctp::IsUnordered(!params.ordered);
  if (params.max_rtx_ms.has_value()) {
    RTC_DCHECK(*params.max_rtx_ms >= 0 &&
               *params.max_rtx_ms <= std::numeric_limits<uint16_t>::max());
    send_options.lifetime = dcsctp::DurationMs(*params.max_rtx_ms);
  }
  if (params.max_rtx_count.has_value()) {
    RTC_DCHECK(*params.max_rtx_count >= 0 &&
               *params.max_rtx_count <= std::numeric_limits<uint16_t>::max());
    send_options.max_retransmissions = *params.max_rtx_count;
  }

  dcsctp::SendStatus error = socket_->Send(std::move(message), send_options);
  switch (error) {
    case dcsctp::SendStatus::kSuccess:
      return RTCError::OK();
    case dcsctp::SendStatus::kErrorResourceExhaustion:
      ready_to_send_data_ = false;
      return RTCError(RTCErrorType::RESOURCE_EXHAUSTED);
    default:
      absl::string_view message = dcsctp::ToString(error);
      RTC_LOG(LS_ERROR) << debug_name_
                        << "->SendData(...): send() failed with error "
                        << message << ".";
      return RTCError(RTCErrorType::NETWORK_ERROR, message);
  }
}

bool DcSctpTransport::ReadyToSendData() {
  RTC_DCHECK_RUN_ON(network_thread_);
  return ready_to_send_data_;
}

int DcSctpTransport::max_message_size() const {
  if (!socket_) {
    RTC_LOG(LS_ERROR) << debug_name_
                      << "->max_message_size(...): Transport is not started.";
    return 0;
  }
  return socket_->options().max_message_size;
}

absl::optional<int> DcSctpTransport::max_outbound_streams() const {
  if (!socket_)
    return absl::nullopt;
  return socket_->options().announced_maximum_outgoing_streams;
}

absl::optional<int> DcSctpTransport::max_inbound_streams() const {
  if (!socket_)
    return absl::nullopt;
  return socket_->options().announced_maximum_incoming_streams;
}

void DcSctpTransport::set_debug_name_for_testing(const char* debug_name) {
  debug_name_ = debug_name;
}

SendPacketStatus DcSctpTransport::SendPacketWithStatus(
    rtc::ArrayView<const uint8_t> data) {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_DCHECK(socket_);

  if (data.size() > (socket_->options().mtu)) {
    RTC_LOG(LS_ERROR) << debug_name_
                      << "->SendPacket(...): "
                         "SCTP seems to have made a packet that is bigger "
                         "than its official MTU: "
                      << data.size() << " vs max of " << socket_->options().mtu;
    return SendPacketStatus::kError;
  }
  TRACE_EVENT0("webrtc", "DcSctpTransport::SendPacket");

  if (!transport_ || !transport_->writable())
    return SendPacketStatus::kError;

  RTC_DLOG(LS_VERBOSE) << debug_name_ << "->SendPacket(length=" << data.size()
                       << ")";

  auto result =
      transport_->SendPacket(reinterpret_cast<const char*>(data.data()),
                             data.size(), rtc::PacketOptions(), 0);

  if (result < 0) {
    RTC_LOG(LS_WARNING) << debug_name_ << "->SendPacket(length=" << data.size()
                        << ") failed with error: " << transport_->GetError()
                        << ".";

    if (rtc::IsBlockingError(transport_->GetError())) {
      return SendPacketStatus::kTemporaryFailure;
    }
    return SendPacketStatus::kError;
  }
  return SendPacketStatus::kSuccess;
}

std::unique_ptr<dcsctp::Timeout> DcSctpTransport::CreateTimeout(
    TaskQueueBase::DelayPrecision precision) {
  return task_queue_timeout_factory_.CreateTimeout(precision);
}

dcsctp::TimeMs DcSctpTransport::TimeMillis() {
  return dcsctp::TimeMs(clock_->TimeInMilliseconds());
}

uint32_t DcSctpTransport::GetRandomInt(uint32_t low, uint32_t high) {
  return random_.Rand(low, high);
}

void DcSctpTransport::OnTotalBufferedAmountLow() {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (!ready_to_send_data_) {
    ready_to_send_data_ = true;
    if (data_channel_sink_) {
      data_channel_sink_->OnReadyToSend();
    }
  }
}

void DcSctpTransport::OnMessageReceived(dcsctp::DcSctpMessage message) {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_DLOG(LS_VERBOSE) << debug_name_ << "->OnMessageReceived(sid="
                       << message.stream_id().value()
                       << ", ppid=" << message.ppid().value()
                       << ", length=" << message.payload().size() << ").";
  auto type = ToDataMessageType(message.ppid());
  if (!type.has_value()) {
    RTC_LOG(LS_VERBOSE) << debug_name_
                        << "->OnMessageReceived(): Received an unknown PPID "
                        << message.ppid().value()
                        << " on an SCTP packet. Dropping.";
    return;
  }
  receive_buffer_.Clear();
  if (!IsEmptyPPID(message.ppid()))
    receive_buffer_.AppendData(message.payload().data(),
                               message.payload().size());

  if (data_channel_sink_) {
    data_channel_sink_->OnDataReceived(message.stream_id().value(), *type,
                                       receive_buffer_);
  }
}

void DcSctpTransport::OnError(dcsctp::ErrorKind error,
                              absl::string_view message) {
  if (error == dcsctp::ErrorKind::kResourceExhaustion) {
    // Indicates that a message failed to be enqueued, because the send buffer
    // is full, which is a very common (and wanted) state for high throughput
    // sending/benchmarks.
    RTC_LOG(LS_VERBOSE) << debug_name_
                        << "->OnError(error=" << dcsctp::ToString(error)
                        << ", message=" << message << ").";
  } else {
    RTC_LOG(LS_ERROR) << debug_name_
                      << "->OnError(error=" << dcsctp::ToString(error)
                      << ", message=" << message << ").";
  }
}

void DcSctpTransport::OnAborted(dcsctp::ErrorKind error,
                                absl::string_view message) {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_LOG(LS_ERROR) << debug_name_
                    << "->OnAborted(error=" << dcsctp::ToString(error)
                    << ", message=" << message << ").";
  ready_to_send_data_ = false;
  RTCError rtc_error(RTCErrorType::OPERATION_ERROR_WITH_DATA,
                     std::string(message));
  rtc_error.set_error_detail(RTCErrorDetailType::SCTP_FAILURE);
  auto code = ToErrorCauseCode(error);
  if (code.has_value()) {
    rtc_error.set_sctp_cause_code(static_cast<uint16_t>(*code));
  }
  if (data_channel_sink_) {
    data_channel_sink_->OnTransportClosed(rtc_error);
  }
}

void DcSctpTransport::OnConnected() {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_DLOG(LS_INFO) << debug_name_ << "->OnConnected().";
  ready_to_send_data_ = true;
  if (data_channel_sink_) {
    data_channel_sink_->OnReadyToSend();
  }
  if (on_connected_callback_) {
    on_connected_callback_();
  }
}

void DcSctpTransport::OnClosed() {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_DLOG(LS_INFO) << debug_name_ << "->OnClosed().";
  ready_to_send_data_ = false;
}

void DcSctpTransport::OnConnectionRestarted() {
  RTC_DLOG(LS_INFO) << debug_name_ << "->OnConnectionRestarted().";
}

void DcSctpTransport::OnStreamsResetFailed(
    rtc::ArrayView<const dcsctp::StreamID> outgoing_streams,
    absl::string_view reason) {
  // TODO(orphis): Need a test to check for correct behavior
  for (auto& stream_id : outgoing_streams) {
    RTC_LOG(LS_WARNING)
        << debug_name_
        << "->OnStreamsResetFailed(...): Outgoing stream reset failed"
        << ", sid=" << stream_id.value() << ", reason: " << reason << ".";
  }
}

void DcSctpTransport::OnStreamsResetPerformed(
    rtc::ArrayView<const dcsctp::StreamID> outgoing_streams) {
  RTC_DCHECK_RUN_ON(network_thread_);
  for (auto& stream_id : outgoing_streams) {
    RTC_LOG(LS_INFO) << debug_name_
                     << "->OnStreamsResetPerformed(...): Outgoing stream reset"
                     << ", sid=" << stream_id.value();

    auto it = stream_states_.find(stream_id);
    if (it == stream_states_.end()) {
      // Ignoring an outgoing stream reset for a closed stream
      return;
    }

    StreamState& stream_state = it->second;
    stream_state.outgoing_reset_done = true;

    if (stream_state.incoming_reset_done) {
      //  When the close was not initiated locally, we can signal the end of the
      //  data channel close procedure when the remote ACKs the reset.
      if (data_channel_sink_) {
        data_channel_sink_->OnChannelClosed(stream_id.value());
      }
      stream_states_.erase(stream_id);
    }
  }
}

void DcSctpTransport::OnIncomingStreamsReset(
    rtc::ArrayView<const dcsctp::StreamID> incoming_streams) {
  RTC_DCHECK_RUN_ON(network_thread_);
  for (auto& stream_id : incoming_streams) {
    RTC_LOG(LS_INFO) << debug_name_
                     << "->OnIncomingStreamsReset(...): Incoming stream reset"
                     << ", sid=" << stream_id.value();

    auto it = stream_states_.find(stream_id);
    if (it == stream_states_.end())
      return;

    StreamState& stream_state = it->second;
    stream_state.incoming_reset_done = true;

    if (!stream_state.closure_initiated) {
      // When receiving an incoming stream reset event for a non local close
      // procedure, the transport needs to reset the stream in the other
      // direction too.
      dcsctp::StreamID streams[1] = {stream_id};
      socket_->ResetStreams(streams);
      if (data_channel_sink_) {
        data_channel_sink_->OnChannelClosing(stream_id.value());
      }
    }

    if (stream_state.outgoing_reset_done) {
      // The close procedure that was initiated locally is complete when we
      // receive and incoming reset event.
      if (data_channel_sink_) {
        data_channel_sink_->OnChannelClosed(stream_id.value());
      }
      stream_states_.erase(stream_id);
    }
  }
}

void DcSctpTransport::ConnectTransportSignals() {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (!transport_) {
    return;
  }
  transport_->SignalWritableState.connect(
      this, &DcSctpTransport::OnTransportWritableState);
  transport_->SignalReadPacket.connect(this,
                                       &DcSctpTransport::OnTransportReadPacket);
  transport_->SignalClosed.connect(this, &DcSctpTransport::OnTransportClosed);
}

void DcSctpTransport::DisconnectTransportSignals() {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (!transport_) {
    return;
  }
  transport_->SignalWritableState.disconnect(this);
  transport_->SignalReadPacket.disconnect(this);
  transport_->SignalClosed.disconnect(this);
}

void DcSctpTransport::OnTransportWritableState(
    rtc::PacketTransportInternal* transport) {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_DCHECK_EQ(transport_, transport);
  RTC_DLOG(LS_VERBOSE) << debug_name_
                       << "->OnTransportWritableState(), writable="
                       << transport->writable();
  MaybeConnectSocket();
}

void DcSctpTransport::OnTransportReadPacket(
    rtc::PacketTransportInternal* transport,
    const char* data,
    size_t length,
    const int64_t& /* packet_time_us */,
    int flags) {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (flags) {
    // We are only interested in SCTP packets.
    return;
  }

  RTC_DLOG(LS_VERBOSE) << debug_name_
                       << "->OnTransportReadPacket(), length=" << length;
  if (socket_) {
    socket_->ReceivePacket(rtc::ArrayView<const uint8_t>(
        reinterpret_cast<const uint8_t*>(data), length));
  }
}

void DcSctpTransport::OnTransportClosed(
    rtc::PacketTransportInternal* transport) {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_DLOG(LS_VERBOSE) << debug_name_ << "->OnTransportClosed().";
  if (data_channel_sink_) {
    data_channel_sink_->OnTransportClosed({});
  }
}

void DcSctpTransport::MaybeConnectSocket() {
  if (transport_ && transport_->writable() && socket_ &&
      socket_->state() == dcsctp::SocketState::kClosed) {
    socket_->Connect();
  }
}
}  // namespace webrtc
