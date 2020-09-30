/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/composite_rtp_transport.h"

#include <string>
#include <utility>

#include "absl/memory/memory.h"
#include "p2p/base/packet_transport_internal.h"

namespace webrtc {

CompositeRtpTransport::CompositeRtpTransport(
    std::vector<RtpTransportInternal*> transports)
    : transports_(std::move(transports)) {
  RTC_DCHECK(!transports_.empty()) << "Cannot have an empty composite";
  std::vector<rtc::PacketTransportInternal*> rtp_transports;
  std::vector<rtc::PacketTransportInternal*> rtcp_transports;
  for (RtpTransportInternal* transport : transports_) {
    RTC_DCHECK_EQ(transport->rtcp_mux_enabled(), rtcp_mux_enabled())
        << "Either all or none of the transports in a composite must enable "
           "rtcp mux";
    RTC_DCHECK_EQ(transport->transport_name(), transport_name())
        << "All transports in a composite must have the same transport name";

    transport->SignalNetworkRouteChanged.connect(
        this, &CompositeRtpTransport::OnNetworkRouteChanged);
    transport->SignalRtcpPacketReceived.connect(
        this, &CompositeRtpTransport::OnRtcpPacketReceived);
  }
}

void CompositeRtpTransport::SetSendTransport(
    RtpTransportInternal* send_transport) {
  if (send_transport_ == send_transport) {
    return;
  }

  RTC_DCHECK(absl::c_linear_search(transports_, send_transport))
      << "Cannot set a send transport that isn't part of the composite";

  if (send_transport_) {
    send_transport_->SignalReadyToSend.disconnect(this);
    send_transport_->SignalWritableState.disconnect(this);
    send_transport_->SignalSentPacket.disconnect(this);
  }

  send_transport_ = send_transport;
  send_transport_->SignalReadyToSend.connect(
      this, &CompositeRtpTransport::OnReadyToSend);
  send_transport_->SignalWritableState.connect(
      this, &CompositeRtpTransport::OnWritableState);
  send_transport_->SignalSentPacket.connect(
      this, &CompositeRtpTransport::OnSentPacket);

  SignalWritableState(send_transport_->IsWritable(/*rtcp=*/true) &&
                      send_transport_->IsWritable(/*rtcp=*/false));
  if (send_transport_->IsReadyToSend()) {
    SignalReadyToSend(true);
  }
}

void CompositeRtpTransport::RemoveTransport(RtpTransportInternal* transport) {
  RTC_DCHECK(transport != send_transport_) << "Cannot remove send transport";

  auto it = absl::c_find(transports_, transport);
  if (it == transports_.end()) {
    return;
  }

  transport->SignalNetworkRouteChanged.disconnect(this);
  transport->SignalRtcpPacketReceived.disconnect(this);
  for (auto sink : rtp_demuxer_sinks_) {
    transport->UnregisterRtpDemuxerSink(sink);
  }

  transports_.erase(it);
}

const std::string& CompositeRtpTransport::transport_name() const {
  return transports_.front()->transport_name();
}

int CompositeRtpTransport::SetRtpOption(rtc::Socket::Option opt, int value) {
  int result = 0;
  for (auto transport : transports_) {
    result |= transport->SetRtpOption(opt, value);
  }
  return result;
}

int CompositeRtpTransport::SetRtcpOption(rtc::Socket::Option opt, int value) {
  int result = 0;
  for (auto transport : transports_) {
    result |= transport->SetRtcpOption(opt, value);
  }
  return result;
}

bool CompositeRtpTransport::rtcp_mux_enabled() const {
  return transports_.front()->rtcp_mux_enabled();
}

void CompositeRtpTransport::SetRtcpMuxEnabled(bool enabled) {
  for (auto transport : transports_) {
    transport->SetRtcpMuxEnabled(enabled);
  }
}

bool CompositeRtpTransport::IsReadyToSend() const {
  return send_transport_ && send_transport_->IsReadyToSend();
}

bool CompositeRtpTransport::IsWritable(bool rtcp) const {
  return send_transport_ && send_transport_->IsWritable(rtcp);
}

bool CompositeRtpTransport::SendRtpPacket(rtc::CopyOnWriteBuffer* packet,
                                          const rtc::PacketOptions& options,
                                          int flags) {
  if (!send_transport_) {
    return false;
  }
  return send_transport_->SendRtpPacket(packet, options, flags);
}

bool CompositeRtpTransport::SendRtcpPacket(rtc::CopyOnWriteBuffer* packet,
                                           const rtc::PacketOptions& options,
                                           int flags) {
  if (!send_transport_) {
    return false;
  }
  return send_transport_->SendRtcpPacket(packet, options, flags);
}

void CompositeRtpTransport::UpdateRtpHeaderExtensionMap(
    const cricket::RtpHeaderExtensions& header_extensions) {
  for (RtpTransportInternal* transport : transports_) {
    transport->UpdateRtpHeaderExtensionMap(header_extensions);
  }
}

bool CompositeRtpTransport::IsSrtpActive() const {
  bool active = true;
  for (RtpTransportInternal* transport : transports_) {
    active &= transport->IsSrtpActive();
  }
  return active;
}

bool CompositeRtpTransport::RegisterRtpDemuxerSink(
    const RtpDemuxerCriteria& criteria,
    RtpPacketSinkInterface* sink) {
  for (RtpTransportInternal* transport : transports_) {
    transport->RegisterRtpDemuxerSink(criteria, sink);
  }
  rtp_demuxer_sinks_.insert(sink);
  return true;
}

bool CompositeRtpTransport::UnregisterRtpDemuxerSink(
    RtpPacketSinkInterface* sink) {
  for (RtpTransportInternal* transport : transports_) {
    transport->UnregisterRtpDemuxerSink(sink);
  }
  rtp_demuxer_sinks_.erase(sink);
  return true;
}

void CompositeRtpTransport::OnNetworkRouteChanged(
    absl::optional<rtc::NetworkRoute> route) {
  SignalNetworkRouteChanged(route);
}

void CompositeRtpTransport::OnRtcpPacketReceived(rtc::CopyOnWriteBuffer* packet,
                                                 int64_t packet_time_us) {
  SignalRtcpPacketReceived(packet, packet_time_us);
}

void CompositeRtpTransport::OnWritableState(bool writable) {
  SignalWritableState(writable);
}

void CompositeRtpTransport::OnReadyToSend(bool ready_to_send) {
  SignalReadyToSend(ready_to_send);
}

void CompositeRtpTransport::OnSentPacket(const rtc::SentPacket& packet) {
  SignalSentPacket(packet);
}

}  // namespace webrtc
