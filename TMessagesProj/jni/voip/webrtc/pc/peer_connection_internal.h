/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_PEER_CONNECTION_INTERNAL_H_
#define PC_PEER_CONNECTION_INTERNAL_H_

#include <map>
#include <memory>
#include <set>
#include <string>
#include <vector>

#include "api/peer_connection_interface.h"
#include "call/call.h"
#include "pc/rtp_data_channel.h"
#include "pc/rtp_transceiver.h"
#include "pc/sctp_data_channel.h"

namespace webrtc {

// Internal interface for extra PeerConnection methods.
class PeerConnectionInternal : public PeerConnectionInterface {
 public:
  virtual rtc::Thread* network_thread() const = 0;
  virtual rtc::Thread* worker_thread() const = 0;

  // The SDP session ID as defined by RFC 3264.
  virtual std::string session_id() const = 0;

  // Returns true if we were the initial offerer.
  virtual bool initial_offerer() const = 0;

  virtual std::vector<
      rtc::scoped_refptr<RtpTransceiverProxyWithInternal<RtpTransceiver>>>
  GetTransceiversInternal() const = 0;

  virtual sigslot::signal1<RtpDataChannel*>& SignalRtpDataChannelCreated() = 0;
  virtual sigslot::signal1<SctpDataChannel*>&
  SignalSctpDataChannelCreated() = 0;

  // Only valid when using deprecated RTP data channels.
  virtual cricket::RtpDataChannel* rtp_data_channel() const = 0;

  // Call on the network thread to fetch stats for all the data channels.
  // TODO(tommi): Make pure virtual after downstream updates.
  virtual std::vector<DataChannelStats> GetDataChannelStats() const {
    return {};
  }

  virtual absl::optional<std::string> sctp_transport_name() const = 0;

  virtual cricket::CandidateStatsList GetPooledCandidateStats() const = 0;

  // Returns a map from MID to transport name for all active media sections.
  virtual std::map<std::string, std::string> GetTransportNamesByMid() const = 0;

  // Returns a map from transport name to transport stats for all given
  // transport names.
  virtual std::map<std::string, cricket::TransportStats>
  GetTransportStatsByNames(const std::set<std::string>& transport_names) = 0;

  virtual Call::Stats GetCallStats() = 0;

  virtual bool GetLocalCertificate(
      const std::string& transport_name,
      rtc::scoped_refptr<rtc::RTCCertificate>* certificate) = 0;
  virtual std::unique_ptr<rtc::SSLCertChain> GetRemoteSSLCertChain(
      const std::string& transport_name) = 0;

  // Returns true if there was an ICE restart initiated by the remote offer.
  virtual bool IceRestartPending(const std::string& content_name) const = 0;

  // Returns true if the ICE restart flag above was set, and no ICE restart has
  // occurred yet for this transport (by applying a local description with
  // changed ufrag/password). If the transport has been deleted as a result of
  // bundling, returns false.
  virtual bool NeedsIceRestart(const std::string& content_name) const = 0;

  // Get SSL role for an arbitrary m= section (handles bundling correctly).
  virtual bool GetSslRole(const std::string& content_name,
                          rtc::SSLRole* role) = 0;
};

}  // namespace webrtc

#endif  // PC_PEER_CONNECTION_INTERNAL_H_
