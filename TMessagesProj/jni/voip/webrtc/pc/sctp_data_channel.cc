/*
 *  Copyright 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/sctp_data_channel.h"

#include <limits>
#include <memory>
#include <string>
#include <utility>

#include "media/sctp/sctp_transport_internal.h"
#include "pc/proxy.h"
#include "pc/sctp_utils.h"
#include "rtc_base/checks.h"
#include "rtc_base/location.h"
#include "rtc_base/logging.h"
#include "rtc_base/ref_counted_object.h"
#include "rtc_base/system/unused.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "rtc_base/thread.h"

namespace webrtc {

namespace {

static size_t kMaxQueuedReceivedDataBytes = 16 * 1024 * 1024;

static std::atomic<int> g_unique_id{0};

int GenerateUniqueId() {
  return ++g_unique_id;
}

// Define proxy for DataChannelInterface.
BEGIN_PRIMARY_PROXY_MAP(DataChannel)
PROXY_PRIMARY_THREAD_DESTRUCTOR()
PROXY_METHOD1(void, RegisterObserver, DataChannelObserver*)
PROXY_METHOD0(void, UnregisterObserver)
BYPASS_PROXY_CONSTMETHOD0(std::string, label)
BYPASS_PROXY_CONSTMETHOD0(bool, reliable)
BYPASS_PROXY_CONSTMETHOD0(bool, ordered)
BYPASS_PROXY_CONSTMETHOD0(uint16_t, maxRetransmitTime)
BYPASS_PROXY_CONSTMETHOD0(uint16_t, maxRetransmits)
BYPASS_PROXY_CONSTMETHOD0(absl::optional<int>, maxRetransmitsOpt)
BYPASS_PROXY_CONSTMETHOD0(absl::optional<int>, maxPacketLifeTime)
BYPASS_PROXY_CONSTMETHOD0(std::string, protocol)
BYPASS_PROXY_CONSTMETHOD0(bool, negotiated)
// Can't bypass the proxy since the id may change.
PROXY_CONSTMETHOD0(int, id)
BYPASS_PROXY_CONSTMETHOD0(Priority, priority)
PROXY_CONSTMETHOD0(DataState, state)
PROXY_CONSTMETHOD0(RTCError, error)
PROXY_CONSTMETHOD0(uint32_t, messages_sent)
PROXY_CONSTMETHOD0(uint64_t, bytes_sent)
PROXY_CONSTMETHOD0(uint32_t, messages_received)
PROXY_CONSTMETHOD0(uint64_t, bytes_received)
PROXY_CONSTMETHOD0(uint64_t, buffered_amount)
PROXY_METHOD0(void, Close)
// TODO(bugs.webrtc.org/11547): Change to run on the network thread.
PROXY_METHOD1(bool, Send, const DataBuffer&)
END_PROXY_MAP(DataChannel)

}  // namespace

InternalDataChannelInit::InternalDataChannelInit(const DataChannelInit& base)
    : DataChannelInit(base), open_handshake_role(kOpener) {
  // If the channel is externally negotiated, do not send the OPEN message.
  if (base.negotiated) {
    open_handshake_role = kNone;
  } else {
    // Datachannel is externally negotiated. Ignore the id value.
    // Specified in createDataChannel, WebRTC spec section 6.1 bullet 13.
    id = -1;
  }
  // Backwards compatibility: If maxRetransmits or maxRetransmitTime
  // are negative, the feature is not enabled.
  // Values are clamped to a 16bit range.
  if (maxRetransmits) {
    if (*maxRetransmits < 0) {
      RTC_LOG(LS_ERROR)
          << "Accepting maxRetransmits < 0 for backwards compatibility";
      maxRetransmits = absl::nullopt;
    } else if (*maxRetransmits > std::numeric_limits<uint16_t>::max()) {
      maxRetransmits = std::numeric_limits<uint16_t>::max();
    }
  }

  if (maxRetransmitTime) {
    if (*maxRetransmitTime < 0) {
      RTC_LOG(LS_ERROR)
          << "Accepting maxRetransmitTime < 0 for backwards compatibility";
      maxRetransmitTime = absl::nullopt;
    } else if (*maxRetransmitTime > std::numeric_limits<uint16_t>::max()) {
      maxRetransmitTime = std::numeric_limits<uint16_t>::max();
    }
  }
}

bool SctpSidAllocator::AllocateSid(rtc::SSLRole role, int* sid) {
  int potential_sid = (role == rtc::SSL_CLIENT) ? 0 : 1;
  while (!IsSidAvailable(potential_sid)) {
    potential_sid += 2;
    if (potential_sid > static_cast<int>(cricket::kMaxSctpSid)) {
      return false;
    }
  }

  *sid = potential_sid;
  used_sids_.insert(potential_sid);
  return true;
}

bool SctpSidAllocator::ReserveSid(int sid) {
  if (!IsSidAvailable(sid)) {
    return false;
  }
  used_sids_.insert(sid);
  return true;
}

void SctpSidAllocator::ReleaseSid(int sid) {
  auto it = used_sids_.find(sid);
  if (it != used_sids_.end()) {
    used_sids_.erase(it);
  }
}

bool SctpSidAllocator::IsSidAvailable(int sid) const {
  if (sid < static_cast<int>(cricket::kMinSctpSid) ||
      sid > static_cast<int>(cricket::kMaxSctpSid)) {
    return false;
  }
  return used_sids_.find(sid) == used_sids_.end();
}

rtc::scoped_refptr<SctpDataChannel> SctpDataChannel::Create(
    SctpDataChannelProviderInterface* provider,
    const std::string& label,
    const InternalDataChannelInit& config,
    rtc::Thread* signaling_thread,
    rtc::Thread* network_thread) {
  auto channel = rtc::make_ref_counted<SctpDataChannel>(
      config, provider, label, signaling_thread, network_thread);
  if (!channel->Init()) {
    return nullptr;
  }
  return channel;
}

// static
rtc::scoped_refptr<DataChannelInterface> SctpDataChannel::CreateProxy(
    rtc::scoped_refptr<SctpDataChannel> channel) {
  // TODO(bugs.webrtc.org/11547): incorporate the network thread in the proxy.
  // Also, consider allowing the proxy object to own the reference (std::move).
  // As is, the proxy has a raw pointer and no reference to the channel object
  // and trusting that the lifetime management aligns with the
  // sctp_data_channels_ array in SctpDataChannelController.
  return DataChannelProxy::Create(channel->signaling_thread_, channel.get());
}

SctpDataChannel::SctpDataChannel(const InternalDataChannelInit& config,
                                 SctpDataChannelProviderInterface* provider,
                                 const std::string& label,
                                 rtc::Thread* signaling_thread,
                                 rtc::Thread* network_thread)
    : signaling_thread_(signaling_thread),
      network_thread_(network_thread),
      internal_id_(GenerateUniqueId()),
      label_(label),
      config_(config),
      observer_(nullptr),
      provider_(provider) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  RTC_UNUSED(network_thread_);
}

bool SctpDataChannel::Init() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (config_.id < -1 ||
      (config_.maxRetransmits && *config_.maxRetransmits < 0) ||
      (config_.maxRetransmitTime && *config_.maxRetransmitTime < 0)) {
    RTC_LOG(LS_ERROR) << "Failed to initialize the SCTP data channel due to "
                         "invalid DataChannelInit.";
    return false;
  }
  if (config_.maxRetransmits && config_.maxRetransmitTime) {
    RTC_LOG(LS_ERROR)
        << "maxRetransmits and maxRetransmitTime should not be both set.";
    return false;
  }

  switch (config_.open_handshake_role) {
    case webrtc::InternalDataChannelInit::kNone:  // pre-negotiated
      handshake_state_ = kHandshakeReady;
      break;
    case webrtc::InternalDataChannelInit::kOpener:
      handshake_state_ = kHandshakeShouldSendOpen;
      break;
    case webrtc::InternalDataChannelInit::kAcker:
      handshake_state_ = kHandshakeShouldSendAck;
      break;
  }

  // Try to connect to the transport in case the transport channel already
  // exists.
  OnTransportChannelCreated();

  // Checks if the transport is ready to send because the initial channel
  // ready signal may have been sent before the DataChannel creation.
  // This has to be done async because the upper layer objects (e.g.
  // Chrome glue and WebKit) are not wired up properly until after this
  // function returns.
  if (provider_->ReadyToSendData()) {
    AddRef();
    rtc::Thread::Current()->PostTask(ToQueuedTask(
        [this] {
          RTC_DCHECK_RUN_ON(signaling_thread_);
          if (state_ != kClosed)
            OnTransportReady(true);
        },
        [this] { Release(); }));
  }

  return true;
}

SctpDataChannel::~SctpDataChannel() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
}

void SctpDataChannel::RegisterObserver(DataChannelObserver* observer) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  observer_ = observer;
  DeliverQueuedReceivedData();
}

void SctpDataChannel::UnregisterObserver() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  observer_ = nullptr;
}

bool SctpDataChannel::reliable() const {
  // May be called on any thread.
  return !config_.maxRetransmits && !config_.maxRetransmitTime;
}

uint64_t SctpDataChannel::buffered_amount() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  return queued_send_data_.byte_count();
}

void SctpDataChannel::Close() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (state_ == kClosed)
    return;
  SetState(kClosing);
  // Will send queued data before beginning the underlying closing procedure.
  UpdateState();
}

SctpDataChannel::DataState SctpDataChannel::state() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  return state_;
}

RTCError SctpDataChannel::error() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  return error_;
}

uint32_t SctpDataChannel::messages_sent() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  return messages_sent_;
}

uint64_t SctpDataChannel::bytes_sent() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  return bytes_sent_;
}

uint32_t SctpDataChannel::messages_received() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  return messages_received_;
}

uint64_t SctpDataChannel::bytes_received() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  return bytes_received_;
}

bool SctpDataChannel::Send(const DataBuffer& buffer) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  // TODO(bugs.webrtc.org/11547): Expect this method to be called on the network
  // thread. Bring buffer management etc to the network thread and keep the
  // operational state management on the signaling thread.

  if (state_ != kOpen) {
    return false;
  }

  // If the queue is non-empty, we're waiting for SignalReadyToSend,
  // so just add to the end of the queue and keep waiting.
  if (!queued_send_data_.Empty()) {
    if (!QueueSendDataMessage(buffer)) {
      // Queue is full
      return false;
    }
    return true;
  }

  SendDataMessage(buffer, true);

  // Always return true for SCTP DataChannel per the spec.
  return true;
}

void SctpDataChannel::SetSctpSid(int sid) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  RTC_DCHECK_LT(config_.id, 0);
  RTC_DCHECK_GE(sid, 0);
  RTC_DCHECK_NE(handshake_state_, kHandshakeWaitingForAck);
  RTC_DCHECK_EQ(state_, kConnecting);

  if (config_.id == sid) {
    return;
  }

  const_cast<InternalDataChannelInit&>(config_).id = sid;
  provider_->AddSctpDataStream(sid);
}

void SctpDataChannel::OnClosingProcedureStartedRemotely(int sid) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (sid == config_.id && state_ != kClosing && state_ != kClosed) {
    // Don't bother sending queued data since the side that initiated the
    // closure wouldn't receive it anyway. See crbug.com/559394 for a lengthy
    // discussion about this.
    queued_send_data_.Clear();
    queued_control_data_.Clear();
    // Just need to change state to kClosing, SctpTransport will handle the
    // rest of the closing procedure and OnClosingProcedureComplete will be
    // called later.
    started_closing_procedure_ = true;
    SetState(kClosing);
  }
}

void SctpDataChannel::OnClosingProcedureComplete(int sid) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (sid == config_.id) {
    // If the closing procedure is complete, we should have finished sending
    // all pending data and transitioned to kClosing already.
    RTC_DCHECK_EQ(state_, kClosing);
    RTC_DCHECK(queued_send_data_.Empty());
    DisconnectFromProvider();
    SetState(kClosed);
  }
}

void SctpDataChannel::OnTransportChannelCreated() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (!connected_to_provider_) {
    connected_to_provider_ = provider_->ConnectDataChannel(this);
  }
  // The sid may have been unassigned when provider_->ConnectDataChannel was
  // done. So always add the streams even if connected_to_provider_ is true.
  if (config_.id >= 0) {
    provider_->AddSctpDataStream(config_.id);
  }
}

void SctpDataChannel::OnTransportChannelClosed(RTCError error) {
  // The SctpTransport is unusable, which could come from multiplie reasons:
  // - the SCTP m= section was rejected
  // - the DTLS transport is closed
  // - the SCTP transport is closed
  CloseAbruptlyWithError(std::move(error));
}

DataChannelStats SctpDataChannel::GetStats() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  DataChannelStats stats{internal_id_,        id(),         label(),
                         protocol(),          state(),      messages_sent(),
                         messages_received(), bytes_sent(), bytes_received()};
  return stats;
}

void SctpDataChannel::OnDataReceived(const cricket::ReceiveDataParams& params,
                                     const rtc::CopyOnWriteBuffer& payload) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (params.sid != config_.id) {
    return;
  }

  if (params.type == DataMessageType::kControl) {
    if (handshake_state_ != kHandshakeWaitingForAck) {
      // Ignore it if we are not expecting an ACK message.
      RTC_LOG(LS_WARNING)
          << "DataChannel received unexpected CONTROL message, sid = "
          << params.sid;
      return;
    }
    if (ParseDataChannelOpenAckMessage(payload)) {
      // We can send unordered as soon as we receive the ACK message.
      handshake_state_ = kHandshakeReady;
      RTC_LOG(LS_INFO) << "DataChannel received OPEN_ACK message, sid = "
                       << params.sid;
    } else {
      RTC_LOG(LS_WARNING)
          << "DataChannel failed to parse OPEN_ACK message, sid = "
          << params.sid;
    }
    return;
  }

  RTC_DCHECK(params.type == DataMessageType::kBinary ||
             params.type == DataMessageType::kText);

  RTC_LOG(LS_VERBOSE) << "DataChannel received DATA message, sid = "
                      << params.sid;
  // We can send unordered as soon as we receive any DATA message since the
  // remote side must have received the OPEN (and old clients do not send
  // OPEN_ACK).
  if (handshake_state_ == kHandshakeWaitingForAck) {
    handshake_state_ = kHandshakeReady;
  }

  bool binary = (params.type == webrtc::DataMessageType::kBinary);
  auto buffer = std::make_unique<DataBuffer>(payload, binary);
  if (state_ == kOpen && observer_) {
    ++messages_received_;
    bytes_received_ += buffer->size();
    observer_->OnMessage(*buffer.get());
  } else {
    if (queued_received_data_.byte_count() + payload.size() >
        kMaxQueuedReceivedDataBytes) {
      RTC_LOG(LS_ERROR) << "Queued received data exceeds the max buffer size.";

      queued_received_data_.Clear();
      CloseAbruptlyWithError(
          RTCError(RTCErrorType::RESOURCE_EXHAUSTED,
                   "Queued received data exceeds the max buffer size."));

      return;
    }
    queued_received_data_.PushBack(std::move(buffer));
  }
}

void SctpDataChannel::OnTransportReady(bool writable) {
  RTC_DCHECK_RUN_ON(signaling_thread_);

  writable_ = writable;
  if (!writable) {
    return;
  }

  SendQueuedControlMessages();
  SendQueuedDataMessages();

  UpdateState();
}

void SctpDataChannel::CloseAbruptlyWithError(RTCError error) {
  RTC_DCHECK_RUN_ON(signaling_thread_);

  if (state_ == kClosed) {
    return;
  }

  if (connected_to_provider_) {
    DisconnectFromProvider();
  }

  // Closing abruptly means any queued data gets thrown away.
  queued_send_data_.Clear();
  queued_control_data_.Clear();

  // Still go to "kClosing" before "kClosed", since observers may be expecting
  // that.
  SetState(kClosing);
  error_ = std::move(error);
  SetState(kClosed);
}

void SctpDataChannel::CloseAbruptlyWithDataChannelFailure(
    const std::string& message) {
  RTCError error(RTCErrorType::OPERATION_ERROR_WITH_DATA, message);
  error.set_error_detail(RTCErrorDetailType::DATA_CHANNEL_FAILURE);
  CloseAbruptlyWithError(std::move(error));
}

void SctpDataChannel::UpdateState() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  // UpdateState determines what to do from a few state variables. Include
  // all conditions required for each state transition here for
  // clarity. OnTransportReady(true) will send any queued data and then invoke
  // UpdateState().

  switch (state_) {
    case kConnecting: {
      if (connected_to_provider_) {
        if (handshake_state_ == kHandshakeShouldSendOpen) {
          rtc::CopyOnWriteBuffer payload;
          WriteDataChannelOpenMessage(label_, config_, &payload);
          SendControlMessage(payload);
        } else if (handshake_state_ == kHandshakeShouldSendAck) {
          rtc::CopyOnWriteBuffer payload;
          WriteDataChannelOpenAckMessage(&payload);
          SendControlMessage(payload);
        }
        if (writable_ && (handshake_state_ == kHandshakeReady ||
                          handshake_state_ == kHandshakeWaitingForAck)) {
          SetState(kOpen);
          // If we have received buffers before the channel got writable.
          // Deliver them now.
          DeliverQueuedReceivedData();
        }
      }
      break;
    }
    case kOpen: {
      break;
    }
    case kClosing: {
      // Wait for all queued data to be sent before beginning the closing
      // procedure.
      if (queued_send_data_.Empty() && queued_control_data_.Empty()) {
        // For SCTP data channels, we need to wait for the closing procedure
        // to complete; after calling RemoveSctpDataStream,
        // OnClosingProcedureComplete will end up called asynchronously
        // afterwards.
        if (connected_to_provider_ && !started_closing_procedure_ &&
            config_.id >= 0) {
          started_closing_procedure_ = true;
          provider_->RemoveSctpDataStream(config_.id);
        }
      }
      break;
    }
    case kClosed:
      break;
  }
}

void SctpDataChannel::SetState(DataState state) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (state_ == state) {
    return;
  }

  state_ = state;
  if (observer_) {
    observer_->OnStateChange();
  }
  if (state_ == kOpen) {
    SignalOpened(this);
  } else if (state_ == kClosed) {
    SignalClosed(this);
  }
}

void SctpDataChannel::DisconnectFromProvider() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (!connected_to_provider_)
    return;

  provider_->DisconnectDataChannel(this);
  connected_to_provider_ = false;
}

void SctpDataChannel::DeliverQueuedReceivedData() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (!observer_) {
    return;
  }

  while (!queued_received_data_.Empty()) {
    std::unique_ptr<DataBuffer> buffer = queued_received_data_.PopFront();
    ++messages_received_;
    bytes_received_ += buffer->size();
    observer_->OnMessage(*buffer);
  }
}

void SctpDataChannel::SendQueuedDataMessages() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (queued_send_data_.Empty()) {
    return;
  }

  RTC_DCHECK(state_ == kOpen || state_ == kClosing);

  while (!queued_send_data_.Empty()) {
    std::unique_ptr<DataBuffer> buffer = queued_send_data_.PopFront();
    if (!SendDataMessage(*buffer, false)) {
      // Return the message to the front of the queue if sending is aborted.
      queued_send_data_.PushFront(std::move(buffer));
      break;
    }
  }
}

bool SctpDataChannel::SendDataMessage(const DataBuffer& buffer,
                                      bool queue_if_blocked) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  SendDataParams send_params;

  send_params.ordered = config_.ordered;
  // Send as ordered if it is still going through OPEN/ACK signaling.
  if (handshake_state_ != kHandshakeReady && !config_.ordered) {
    send_params.ordered = true;
    RTC_LOG(LS_VERBOSE)
        << "Sending data as ordered for unordered DataChannel "
           "because the OPEN_ACK message has not been received.";
  }

  send_params.max_rtx_count = config_.maxRetransmits;
  send_params.max_rtx_ms = config_.maxRetransmitTime;
  send_params.type =
      buffer.binary ? DataMessageType::kBinary : DataMessageType::kText;

  cricket::SendDataResult send_result = cricket::SDR_SUCCESS;
  bool success =
      provider_->SendData(config_.id, send_params, buffer.data, &send_result);

  if (success) {
    ++messages_sent_;
    bytes_sent_ += buffer.size();

    if (observer_ && buffer.size() > 0) {
      observer_->OnBufferedAmountChange(buffer.size());
    }
    return true;
  }

  if (send_result == cricket::SDR_BLOCK) {
    if (!queue_if_blocked || QueueSendDataMessage(buffer)) {
      return false;
    }
  }
  // Close the channel if the error is not SDR_BLOCK, or if queuing the
  // message failed.
  RTC_LOG(LS_ERROR) << "Closing the DataChannel due to a failure to send data, "
                       "send_result = "
                    << send_result;
  CloseAbruptlyWithError(
      RTCError(RTCErrorType::NETWORK_ERROR, "Failure to send data"));

  return false;
}

bool SctpDataChannel::QueueSendDataMessage(const DataBuffer& buffer) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  size_t start_buffered_amount = queued_send_data_.byte_count();
  if (start_buffered_amount + buffer.size() >
      DataChannelInterface::MaxSendQueueSize()) {
    RTC_LOG(LS_ERROR) << "Can't buffer any more data for the data channel.";
    return false;
  }
  queued_send_data_.PushBack(std::make_unique<DataBuffer>(buffer));
  return true;
}

void SctpDataChannel::SendQueuedControlMessages() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  PacketQueue control_packets;
  control_packets.Swap(&queued_control_data_);

  while (!control_packets.Empty()) {
    std::unique_ptr<DataBuffer> buf = control_packets.PopFront();
    SendControlMessage(buf->data);
  }
}

void SctpDataChannel::QueueControlMessage(
    const rtc::CopyOnWriteBuffer& buffer) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  queued_control_data_.PushBack(std::make_unique<DataBuffer>(buffer, true));
}

bool SctpDataChannel::SendControlMessage(const rtc::CopyOnWriteBuffer& buffer) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  RTC_DCHECK(writable_);
  RTC_DCHECK_GE(config_.id, 0);

  bool is_open_message = handshake_state_ == kHandshakeShouldSendOpen;
  RTC_DCHECK(!is_open_message || !config_.negotiated);

  SendDataParams send_params;
  // Send data as ordered before we receive any message from the remote peer to
  // make sure the remote peer will not receive any data before it receives the
  // OPEN message.
  send_params.ordered = config_.ordered || is_open_message;
  send_params.type = DataMessageType::kControl;

  cricket::SendDataResult send_result = cricket::SDR_SUCCESS;
  bool retval =
      provider_->SendData(config_.id, send_params, buffer, &send_result);
  if (retval) {
    RTC_LOG(LS_VERBOSE) << "Sent CONTROL message on channel " << config_.id;

    if (handshake_state_ == kHandshakeShouldSendAck) {
      handshake_state_ = kHandshakeReady;
    } else if (handshake_state_ == kHandshakeShouldSendOpen) {
      handshake_state_ = kHandshakeWaitingForAck;
    }
  } else if (send_result == cricket::SDR_BLOCK) {
    QueueControlMessage(buffer);
  } else {
    RTC_LOG(LS_ERROR) << "Closing the DataChannel due to a failure to send"
                         " the CONTROL message, send_result = "
                      << send_result;
    CloseAbruptlyWithError(RTCError(RTCErrorType::NETWORK_ERROR,
                                    "Failed to send a CONTROL message"));
  }
  return retval;
}

// static
void SctpDataChannel::ResetInternalIdAllocatorForTesting(int new_value) {
  g_unique_id = new_value;
}

}  // namespace webrtc
