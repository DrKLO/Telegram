/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/sctp_transport.h"

#include <algorithm>
#include <utility>

#include "absl/types/optional.h"
#include "api/dtls_transport_interface.h"
#include "api/sequence_checker.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

SctpTransport::SctpTransport(
    std::unique_ptr<cricket::SctpTransportInternal> internal)
    : owner_thread_(rtc::Thread::Current()),
      info_(SctpTransportState::kNew),
      internal_sctp_transport_(std::move(internal)) {
  RTC_DCHECK(internal_sctp_transport_.get());
  internal_sctp_transport_->SetOnConnectedCallback(
      [this]() { OnAssociationChangeCommunicationUp(); });

  if (dtls_transport_) {
    UpdateInformation(SctpTransportState::kConnecting);
  } else {
    UpdateInformation(SctpTransportState::kNew);
  }
}

SctpTransport::~SctpTransport() {
  // We depend on the network thread to call Clear() before dropping
  // its last reference to this object.
  RTC_DCHECK(owner_thread_->IsCurrent() || !internal_sctp_transport_);
}

SctpTransportInformation SctpTransport::Information() const {
  // TODO(tommi): Update PeerConnection::GetSctpTransport to hand out a proxy
  // to the transport so that we can be sure that methods get called on the
  // expected thread. Chromium currently calls this method from
  // TransceiverStateSurfacer.
  if (!owner_thread_->IsCurrent()) {
    return owner_thread_->BlockingCall([this] { return Information(); });
  }
  RTC_DCHECK_RUN_ON(owner_thread_);
  return info_;
}

void SctpTransport::RegisterObserver(SctpTransportObserverInterface* observer) {
  RTC_DCHECK_RUN_ON(owner_thread_);
  RTC_DCHECK(observer);
  RTC_DCHECK(!observer_);
  observer_ = observer;
}

void SctpTransport::UnregisterObserver() {
  RTC_DCHECK_RUN_ON(owner_thread_);
  observer_ = nullptr;
}

RTCError SctpTransport::OpenChannel(int channel_id) {
  RTC_DCHECK_RUN_ON(owner_thread_);
  RTC_DCHECK(internal_sctp_transport_);
  internal_sctp_transport_->OpenStream(channel_id);
  return RTCError::OK();
}

RTCError SctpTransport::SendData(int channel_id,
                                 const SendDataParams& params,
                                 const rtc::CopyOnWriteBuffer& buffer) {
  RTC_DCHECK_RUN_ON(owner_thread_);
  RTC_DCHECK(internal_sctp_transport_);
  cricket::SendDataResult result;
  internal_sctp_transport_->SendData(channel_id, params, buffer, &result);

  // TODO(mellem):  See about changing the interfaces to not require mapping
  // SendDataResult to RTCError and back again.
  switch (result) {
    case cricket::SendDataResult::SDR_SUCCESS:
      return RTCError::OK();
    case cricket::SendDataResult::SDR_BLOCK:
      // Send buffer is full.
      return RTCError(RTCErrorType::RESOURCE_EXHAUSTED);
    case cricket::SendDataResult::SDR_ERROR:
      return RTCError(RTCErrorType::NETWORK_ERROR);
  }
  return RTCError(RTCErrorType::NETWORK_ERROR);
}

RTCError SctpTransport::CloseChannel(int channel_id) {
  RTC_DCHECK_RUN_ON(owner_thread_);
  RTC_DCHECK(internal_sctp_transport_);
  internal_sctp_transport_->ResetStream(channel_id);
  return RTCError::OK();
}

void SctpTransport::SetDataSink(DataChannelSink* sink) {
  RTC_DCHECK_RUN_ON(owner_thread_);
  RTC_DCHECK(internal_sctp_transport_);
  internal_sctp_transport_->SetDataChannelSink(sink);
}

bool SctpTransport::IsReadyToSend() const {
  RTC_DCHECK_RUN_ON(owner_thread_);
  RTC_DCHECK(internal_sctp_transport_);
  return internal_sctp_transport_->ReadyToSendData();
}

rtc::scoped_refptr<DtlsTransportInterface> SctpTransport::dtls_transport()
    const {
  RTC_DCHECK_RUN_ON(owner_thread_);
  return dtls_transport_;
}

// Internal functions
void SctpTransport::Clear() {
  RTC_DCHECK_RUN_ON(owner_thread_);
  RTC_DCHECK(internal());
  // Note that we delete internal_sctp_transport_, but
  // only drop the reference to dtls_transport_.
  dtls_transport_ = nullptr;
  internal_sctp_transport_ = nullptr;
  UpdateInformation(SctpTransportState::kClosed);
}

void SctpTransport::SetDtlsTransport(
    rtc::scoped_refptr<DtlsTransport> transport) {
  RTC_DCHECK_RUN_ON(owner_thread_);
  SctpTransportState next_state = info_.state();
  dtls_transport_ = transport;
  if (internal_sctp_transport_) {
    if (transport) {
      internal_sctp_transport_->SetDtlsTransport(transport->internal());

      transport->internal()->SubscribeDtlsTransportState(
          [this](cricket::DtlsTransportInternal* transport,
                 DtlsTransportState state) {
            OnDtlsStateChange(transport, state);
          });
      if (info_.state() == SctpTransportState::kNew) {
        next_state = SctpTransportState::kConnecting;
      }
    } else {
      internal_sctp_transport_->SetDtlsTransport(nullptr);
    }
  }

  UpdateInformation(next_state);
}

void SctpTransport::Start(int local_port,
                          int remote_port,
                          int max_message_size) {
  RTC_DCHECK_RUN_ON(owner_thread_);
  info_ = SctpTransportInformation(info_.state(), info_.dtls_transport(),
                                   max_message_size, info_.MaxChannels());

  if (!internal()->Start(local_port, remote_port, max_message_size)) {
    RTC_LOG(LS_ERROR) << "Failed to push down SCTP parameters, closing.";
    UpdateInformation(SctpTransportState::kClosed);
  }
}

void SctpTransport::UpdateInformation(SctpTransportState state) {
  RTC_DCHECK_RUN_ON(owner_thread_);
  bool must_send_update = (state != info_.state());
  // TODO(https://bugs.webrtc.org/10358): Update max channels from internal
  // SCTP transport when available.
  if (internal_sctp_transport_) {
    info_ = SctpTransportInformation(
        state, dtls_transport_, info_.MaxMessageSize(), info_.MaxChannels());
  } else {
    info_ = SctpTransportInformation(
        state, dtls_transport_, info_.MaxMessageSize(), info_.MaxChannels());
  }

  if (observer_ && must_send_update) {
    observer_->OnStateChange(info_);
  }
}

void SctpTransport::OnAssociationChangeCommunicationUp() {
  RTC_DCHECK_RUN_ON(owner_thread_);
  RTC_DCHECK(internal_sctp_transport_);
  if (internal_sctp_transport_->max_outbound_streams() &&
      internal_sctp_transport_->max_inbound_streams()) {
    int max_channels =
        std::min(*(internal_sctp_transport_->max_outbound_streams()),
                 *(internal_sctp_transport_->max_inbound_streams()));
    // Record max channels.
    info_ = SctpTransportInformation(info_.state(), info_.dtls_transport(),
                                     info_.MaxMessageSize(), max_channels);
  }

  UpdateInformation(SctpTransportState::kConnected);
}

void SctpTransport::OnDtlsStateChange(cricket::DtlsTransportInternal* transport,
                                      DtlsTransportState state) {
  RTC_DCHECK_RUN_ON(owner_thread_);
  RTC_CHECK(transport == dtls_transport_->internal());
  if (state == DtlsTransportState::kClosed ||
      state == DtlsTransportState::kFailed) {
    UpdateInformation(SctpTransportState::kClosed);
    // TODO(http://bugs.webrtc.org/11090): Close all the data channels
  }
}

}  // namespace webrtc
