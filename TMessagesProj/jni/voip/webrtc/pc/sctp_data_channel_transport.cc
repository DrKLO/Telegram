/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/sctp_data_channel_transport.h"

#include "absl/types/optional.h"
#include "pc/sctp_utils.h"

namespace webrtc {

SctpDataChannelTransport::SctpDataChannelTransport(
    cricket::SctpTransportInternal* sctp_transport)
    : sctp_transport_(sctp_transport) {
  sctp_transport_->SignalReadyToSendData.connect(
      this, &SctpDataChannelTransport::OnReadyToSendData);
  sctp_transport_->SignalDataReceived.connect(
      this, &SctpDataChannelTransport::OnDataReceived);
  sctp_transport_->SignalClosingProcedureStartedRemotely.connect(
      this, &SctpDataChannelTransport::OnClosingProcedureStartedRemotely);
  sctp_transport_->SignalClosingProcedureComplete.connect(
      this, &SctpDataChannelTransport::OnClosingProcedureComplete);
  sctp_transport_->SignalClosedAbruptly.connect(
      this, &SctpDataChannelTransport::OnClosedAbruptly);
}

RTCError SctpDataChannelTransport::OpenChannel(int channel_id) {
  sctp_transport_->OpenStream(channel_id);
  return RTCError::OK();
}

RTCError SctpDataChannelTransport::SendData(
    int channel_id,
    const SendDataParams& params,
    const rtc::CopyOnWriteBuffer& buffer) {
  cricket::SendDataResult result;
  sctp_transport_->SendData(channel_id, params, buffer, &result);

  // TODO(mellem):  See about changing the interfaces to not require mapping
  // SendDataResult to RTCError and back again.
  switch (result) {
    case cricket::SendDataResult::SDR_SUCCESS:
      return RTCError::OK();
    case cricket::SendDataResult::SDR_BLOCK: {
      // Send buffer is full.
      ready_to_send_ = false;
      return RTCError(RTCErrorType::RESOURCE_EXHAUSTED);
    }
    case cricket::SendDataResult::SDR_ERROR:
      return RTCError(RTCErrorType::NETWORK_ERROR);
  }
  return RTCError(RTCErrorType::NETWORK_ERROR);
}

RTCError SctpDataChannelTransport::CloseChannel(int channel_id) {
  sctp_transport_->ResetStream(channel_id);
  return RTCError::OK();
}

void SctpDataChannelTransport::SetDataSink(DataChannelSink* sink) {
  sink_ = sink;
  if (sink_ && ready_to_send_) {
    sink_->OnReadyToSend();
  }
}

bool SctpDataChannelTransport::IsReadyToSend() const {
  return ready_to_send_;
}

void SctpDataChannelTransport::OnReadyToSendData() {
  ready_to_send_ = true;
  if (sink_) {
    sink_->OnReadyToSend();
  }
}

void SctpDataChannelTransport::OnDataReceived(
    const cricket::ReceiveDataParams& params,
    const rtc::CopyOnWriteBuffer& buffer) {
  if (sink_) {
    sink_->OnDataReceived(params.sid, params.type, buffer);
  }
}

void SctpDataChannelTransport::OnClosingProcedureStartedRemotely(
    int channel_id) {
  if (sink_) {
    sink_->OnChannelClosing(channel_id);
  }
}

void SctpDataChannelTransport::OnClosingProcedureComplete(int channel_id) {
  if (sink_) {
    sink_->OnChannelClosed(channel_id);
  }
}

void SctpDataChannelTransport::OnClosedAbruptly() {
  if (sink_) {
    sink_->OnTransportClosed();
  }
}

}  // namespace webrtc
