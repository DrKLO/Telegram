/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_COMPOSITE_RTP_TRANSPORT_H_
#define PC_COMPOSITE_RTP_TRANSPORT_H_

#include <memory>
#include <set>
#include <string>
#include <vector>

#include "call/rtp_demuxer.h"
#include "call/rtp_packet_sink_interface.h"
#include "pc/rtp_transport_internal.h"
#include "pc/session_description.h"
#include "rtc_base/async_packet_socket.h"
#include "rtc_base/copy_on_write_buffer.h"

namespace webrtc {

// Composite RTP transport capable of receiving from multiple sub-transports.
//
// CompositeRtpTransport is receive-only until the caller explicitly chooses
// which transport will be used to send and calls |SetSendTransport|.  This
// choice must be made as part of the SDP negotiation process, based on receipt
// of a provisional answer.  |CompositeRtpTransport| does not become writable or
// ready to send until |SetSendTransport| is called.
//
// When a full answer is received, the user should replace the composite
// transport with the single, chosen RTP transport, then delete the composite
// and all non-chosen transports.
class CompositeRtpTransport : public RtpTransportInternal {
 public:
  // Constructs a composite out of the given |transports|.  |transports| must
  // not be empty.  All |transports| must outlive the composite.
  explicit CompositeRtpTransport(std::vector<RtpTransportInternal*> transports);

  // Sets which transport will be used for sending packets.  Once called,
  // |IsReadyToSend|, |IsWritable|, and the associated signals will reflect the
  // state of |send_tranpsort|.
  void SetSendTransport(RtpTransportInternal* send_transport);

  // Removes |transport| from the composite.  No-op if |transport| is null or
  // not found in the composite.  Removing a transport disconnects all signals
  // and RTP demux sinks from that transport.  The send transport may not be
  // removed.
  void RemoveTransport(RtpTransportInternal* transport);

  // All transports within a composite must have the same name.
  const std::string& transport_name() const override;

  int SetRtpOption(rtc::Socket::Option opt, int value) override;
  int SetRtcpOption(rtc::Socket::Option opt, int value) override;

  // All transports within a composite must either enable or disable RTCP mux.
  bool rtcp_mux_enabled() const override;

  // Enables or disables RTCP mux for all component transports.
  void SetRtcpMuxEnabled(bool enabled) override;

  // The composite is ready to send if |send_transport_| is set and ready to
  // send.
  bool IsReadyToSend() const override;

  // The composite is writable if |send_transport_| is set and writable.
  bool IsWritable(bool rtcp) const override;

  // Sends an RTP packet.  May only be called after |send_transport_| is set.
  bool SendRtpPacket(rtc::CopyOnWriteBuffer* packet,
                     const rtc::PacketOptions& options,
                     int flags) override;

  // Sends an RTCP packet.  May only be called after |send_transport_| is set.
  bool SendRtcpPacket(rtc::CopyOnWriteBuffer* packet,
                      const rtc::PacketOptions& options,
                      int flags) override;

  // Updates the mapping of RTP header extensions for all component transports.
  void UpdateRtpHeaderExtensionMap(
      const cricket::RtpHeaderExtensions& header_extensions) override;

  // SRTP is only active for a composite if it is active for all component
  // transports.
  bool IsSrtpActive() const override;

  // Registers an RTP demux sink with all component transports.
  bool RegisterRtpDemuxerSink(const RtpDemuxerCriteria& criteria,
                              RtpPacketSinkInterface* sink) override;
  bool UnregisterRtpDemuxerSink(RtpPacketSinkInterface* sink) override;

 private:
  // Receive-side signals.
  void OnNetworkRouteChanged(absl::optional<rtc::NetworkRoute> route);
  void OnRtcpPacketReceived(rtc::CopyOnWriteBuffer* packet,
                            int64_t packet_time_us);

  // Send-side signals.
  void OnWritableState(bool writable);
  void OnReadyToSend(bool ready_to_send);
  void OnSentPacket(const rtc::SentPacket& packet);

  std::vector<RtpTransportInternal*> transports_;
  RtpTransportInternal* send_transport_ = nullptr;

  // Record of registered RTP demuxer sinks.  Used to unregister sinks when a
  // transport is removed.
  std::set<RtpPacketSinkInterface*> rtp_demuxer_sinks_;
};

}  // namespace webrtc

#endif  // PC_COMPOSITE_RTP_TRANSPORT_H_
