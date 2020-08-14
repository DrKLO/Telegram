/*
 *  Copyright 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/rtp_data_channel.h"

#include <memory>
#include <string>
#include <utility>

#include "api/proxy.h"
#include "rtc_base/checks.h"
#include "rtc_base/location.h"
#include "rtc_base/logging.h"
#include "rtc_base/ref_counted_object.h"
#include "rtc_base/thread.h"

namespace webrtc {

namespace {

static size_t kMaxQueuedReceivedDataBytes = 16 * 1024 * 1024;

static std::atomic<int> g_unique_id{0};

int GenerateUniqueId() {
  return ++g_unique_id;
}

// Define proxy for DataChannelInterface.
BEGIN_SIGNALING_PROXY_MAP(DataChannel)
PROXY_SIGNALING_THREAD_DESTRUCTOR()
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
END_PROXY_MAP()

}  // namespace

rtc::scoped_refptr<RtpDataChannel> RtpDataChannel::Create(
    RtpDataChannelProviderInterface* provider,
    const std::string& label,
    const DataChannelInit& config,
    rtc::Thread* signaling_thread) {
  rtc::scoped_refptr<RtpDataChannel> channel(
      new rtc::RefCountedObject<RtpDataChannel>(config, provider, label,
                                                signaling_thread));
  if (!channel->Init()) {
    return nullptr;
  }
  return channel;
}

// static
rtc::scoped_refptr<DataChannelInterface> RtpDataChannel::CreateProxy(
    rtc::scoped_refptr<RtpDataChannel> channel) {
  return DataChannelProxy::Create(channel->signaling_thread_, channel.get());
}

RtpDataChannel::RtpDataChannel(const DataChannelInit& config,
                               RtpDataChannelProviderInterface* provider,
                               const std::string& label,
                               rtc::Thread* signaling_thread)
    : signaling_thread_(signaling_thread),
      internal_id_(GenerateUniqueId()),
      label_(label),
      config_(config),
      provider_(provider) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
}

bool RtpDataChannel::Init() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (config_.reliable || config_.id != -1 || config_.maxRetransmits ||
      config_.maxRetransmitTime) {
    RTC_LOG(LS_ERROR) << "Failed to initialize the RTP data channel due to "
                         "invalid DataChannelInit.";
    return false;
  }

  return true;
}

RtpDataChannel::~RtpDataChannel() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
}

void RtpDataChannel::RegisterObserver(DataChannelObserver* observer) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  observer_ = observer;
  DeliverQueuedReceivedData();
}

void RtpDataChannel::UnregisterObserver() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  observer_ = nullptr;
}

void RtpDataChannel::Close() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (state_ == kClosed)
    return;
  send_ssrc_ = 0;
  send_ssrc_set_ = false;
  SetState(kClosing);
  UpdateState();
}

RtpDataChannel::DataState RtpDataChannel::state() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  return state_;
}

RTCError RtpDataChannel::error() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  return error_;
}

uint32_t RtpDataChannel::messages_sent() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  return messages_sent_;
}

uint64_t RtpDataChannel::bytes_sent() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  return bytes_sent_;
}

uint32_t RtpDataChannel::messages_received() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  return messages_received_;
}

uint64_t RtpDataChannel::bytes_received() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  return bytes_received_;
}

bool RtpDataChannel::Send(const DataBuffer& buffer) {
  RTC_DCHECK_RUN_ON(signaling_thread_);

  if (state_ != kOpen) {
    return false;
  }

  // TODO(jiayl): the spec is unclear about if the remote side should get the
  // onmessage event. We need to figure out the expected behavior and change the
  // code accordingly.
  if (buffer.size() == 0) {
    return true;
  }

  return SendDataMessage(buffer);
}

void RtpDataChannel::SetReceiveSsrc(uint32_t receive_ssrc) {
  RTC_DCHECK_RUN_ON(signaling_thread_);

  if (receive_ssrc_set_) {
    return;
  }
  receive_ssrc_ = receive_ssrc;
  receive_ssrc_set_ = true;
  UpdateState();
}

void RtpDataChannel::OnTransportChannelClosed() {
  RTCError error = RTCError(RTCErrorType::OPERATION_ERROR_WITH_DATA,
                            "Transport channel closed");
  CloseAbruptlyWithError(std::move(error));
}

DataChannelStats RtpDataChannel::GetStats() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  DataChannelStats stats{internal_id_,        id(),         label(),
                         protocol(),          state(),      messages_sent(),
                         messages_received(), bytes_sent(), bytes_received()};
  return stats;
}

// The remote peer request that this channel shall be closed.
void RtpDataChannel::RemotePeerRequestClose() {
  // Close with error code explicitly set to OK.
  CloseAbruptlyWithError(RTCError());
}

void RtpDataChannel::SetSendSsrc(uint32_t send_ssrc) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (send_ssrc_set_) {
    return;
  }
  send_ssrc_ = send_ssrc;
  send_ssrc_set_ = true;
  UpdateState();
}

void RtpDataChannel::OnDataReceived(const cricket::ReceiveDataParams& params,
                                    const rtc::CopyOnWriteBuffer& payload) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (params.ssrc != receive_ssrc_) {
    return;
  }

  RTC_DCHECK(params.type == cricket::DMT_BINARY ||
             params.type == cricket::DMT_TEXT);

  RTC_LOG(LS_VERBOSE) << "DataChannel received DATA message, sid = "
                      << params.sid;

  bool binary = (params.type == cricket::DMT_BINARY);
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

void RtpDataChannel::OnChannelReady(bool writable) {
  RTC_DCHECK_RUN_ON(signaling_thread_);

  writable_ = writable;
  if (!writable) {
    return;
  }

  UpdateState();
}

void RtpDataChannel::CloseAbruptlyWithError(RTCError error) {
  RTC_DCHECK_RUN_ON(signaling_thread_);

  if (state_ == kClosed) {
    return;
  }

  if (connected_to_provider_) {
    DisconnectFromProvider();
  }

  // Still go to "kClosing" before "kClosed", since observers may be expecting
  // that.
  SetState(kClosing);
  error_ = std::move(error);
  SetState(kClosed);
}

void RtpDataChannel::UpdateState() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  // UpdateState determines what to do from a few state variables.  Include
  // all conditions required for each state transition here for
  // clarity.
  switch (state_) {
    case kConnecting: {
      if (send_ssrc_set_ == receive_ssrc_set_) {
        if (!connected_to_provider_) {
          connected_to_provider_ = provider_->ConnectDataChannel(this);
        }
        if (connected_to_provider_ && writable_) {
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
      // For RTP data channels, we can go to "closed" after we finish
      // sending data and the send/recv SSRCs are unset.
      if (connected_to_provider_) {
        DisconnectFromProvider();
      }
      if (!send_ssrc_set_ && !receive_ssrc_set_) {
        SetState(kClosed);
      }
      break;
    }
    case kClosed:
      break;
  }
}

void RtpDataChannel::SetState(DataState state) {
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

void RtpDataChannel::DisconnectFromProvider() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (!connected_to_provider_)
    return;

  provider_->DisconnectDataChannel(this);
  connected_to_provider_ = false;
}

void RtpDataChannel::DeliverQueuedReceivedData() {
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

bool RtpDataChannel::SendDataMessage(const DataBuffer& buffer) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  cricket::SendDataParams send_params;

  send_params.ssrc = send_ssrc_;
  send_params.type = buffer.binary ? cricket::DMT_BINARY : cricket::DMT_TEXT;

  cricket::SendDataResult send_result = cricket::SDR_SUCCESS;
  bool success = provider_->SendData(send_params, buffer.data, &send_result);

  if (success) {
    ++messages_sent_;
    bytes_sent_ += buffer.size();
    if (observer_ && buffer.size() > 0) {
      observer_->OnBufferedAmountChange(buffer.size());
    }
    return true;
  }

  return false;
}

// static
void RtpDataChannel::ResetInternalIdAllocatorForTesting(int new_value) {
  g_unique_id = new_value;
}

}  // namespace webrtc
