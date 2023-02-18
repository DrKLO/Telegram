/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_JSEP_TRANSPORT_H_
#define PC_JSEP_TRANSPORT_H_

#include <functional>
#include <map>
#include <memory>
#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "api/candidate.h"
#include "api/crypto_params.h"
#include "api/ice_transport_interface.h"
#include "api/jsep.h"
#include "api/rtc_error.h"
#include "api/scoped_refptr.h"
#include "api/sequence_checker.h"
#include "api/transport/data_channel_transport_interface.h"
#include "media/sctp/sctp_transport_internal.h"
#include "p2p/base/dtls_transport.h"
#include "p2p/base/dtls_transport_internal.h"
#include "p2p/base/ice_transport_internal.h"
#include "p2p/base/p2p_constants.h"
#include "p2p/base/transport_description.h"
#include "p2p/base/transport_info.h"
#include "pc/dtls_srtp_transport.h"
#include "pc/dtls_transport.h"
#include "pc/rtcp_mux_filter.h"
#include "pc/rtp_transport.h"
#include "pc/rtp_transport_internal.h"
#include "pc/sctp_transport.h"
#include "pc/session_description.h"
#include "pc/srtp_filter.h"
#include "pc/srtp_transport.h"
#include "pc/transport_stats.h"
#include "rtc_base/checks.h"
#include "rtc_base/rtc_certificate.h"
#include "rtc_base/ssl_fingerprint.h"
#include "rtc_base/ssl_stream_adapter.h"
#include "rtc_base/thread.h"
#include "rtc_base/thread_annotations.h"

namespace cricket {

class DtlsTransportInternal;

struct JsepTransportDescription {
 public:
  JsepTransportDescription();
  JsepTransportDescription(
      bool rtcp_mux_enabled,
      const std::vector<CryptoParams>& cryptos,
      const std::vector<int>& encrypted_header_extension_ids,
      int rtp_abs_sendtime_extn_id,
      const TransportDescription& transport_description);
  JsepTransportDescription(const JsepTransportDescription& from);
  ~JsepTransportDescription();

  JsepTransportDescription& operator=(const JsepTransportDescription& from);

  bool rtcp_mux_enabled = true;
  std::vector<CryptoParams> cryptos;
  std::vector<int> encrypted_header_extension_ids;
  int rtp_abs_sendtime_extn_id = -1;
  // TODO(zhihuang): Add the ICE and DTLS related variables and methods from
  // TransportDescription and remove this extra layer of abstraction.
  TransportDescription transport_desc;
};

// Helper class used by JsepTransportController that processes
// TransportDescriptions. A TransportDescription represents the
// transport-specific properties of an SDP m= section, processed according to
// JSEP. Each transport consists of DTLS and ICE transport channels for RTP
// (and possibly RTCP, if rtcp-mux isn't used).
//
// On Threading: JsepTransport performs work solely on the network thread, and
// so its methods should only be called on the network thread.
class JsepTransport {
 public:
  // `mid` is just used for log statements in order to identify the Transport.
  // Note that `local_certificate` is allowed to be null since a remote
  // description may be set before a local certificate is generated.
  JsepTransport(
      const std::string& mid,
      const rtc::scoped_refptr<rtc::RTCCertificate>& local_certificate,
      rtc::scoped_refptr<webrtc::IceTransportInterface> ice_transport,
      rtc::scoped_refptr<webrtc::IceTransportInterface> rtcp_ice_transport,
      std::unique_ptr<webrtc::RtpTransport> unencrypted_rtp_transport,
      std::unique_ptr<webrtc::SrtpTransport> sdes_transport,
      std::unique_ptr<webrtc::DtlsSrtpTransport> dtls_srtp_transport,
      std::unique_ptr<DtlsTransportInternal> rtp_dtls_transport,
      std::unique_ptr<DtlsTransportInternal> rtcp_dtls_transport,
      std::unique_ptr<SctpTransportInternal> sctp_transport,
      std::function<void()> rtcp_mux_active_callback);

  ~JsepTransport();

  JsepTransport(const JsepTransport&) = delete;
  JsepTransport& operator=(const JsepTransport&) = delete;

  // Returns the MID of this transport. This is only used for logging.
  const std::string& mid() const { return mid_; }

  // Must be called before applying local session description.
  // Needed in order to verify the local fingerprint.
  void SetLocalCertificate(
      const rtc::scoped_refptr<rtc::RTCCertificate>& local_certificate) {
    RTC_DCHECK_RUN_ON(network_thread_);
    local_certificate_ = local_certificate;
  }

  // Return the local certificate provided by SetLocalCertificate.
  rtc::scoped_refptr<rtc::RTCCertificate> GetLocalCertificate() const {
    RTC_DCHECK_RUN_ON(network_thread_);
    return local_certificate_;
  }

  webrtc::RTCError SetLocalJsepTransportDescription(
      const JsepTransportDescription& jsep_description,
      webrtc::SdpType type);

  // Set the remote TransportDescription to be used by DTLS and ICE channels
  // that are part of this Transport.
  webrtc::RTCError SetRemoteJsepTransportDescription(
      const JsepTransportDescription& jsep_description,
      webrtc::SdpType type);
  webrtc::RTCError AddRemoteCandidates(const Candidates& candidates);

  // Set the "needs-ice-restart" flag as described in JSEP. After the flag is
  // set, offers should generate new ufrags/passwords until an ICE restart
  // occurs.
  //
  // This and `needs_ice_restart()` must be called on the network thread.
  void SetNeedsIceRestartFlag();

  // Returns true if the ICE restart flag above was set, and no ICE restart has
  // occurred yet for this transport (by applying a local description with
  // changed ufrag/password).
  bool needs_ice_restart() const {
    RTC_DCHECK_RUN_ON(network_thread_);
    return needs_ice_restart_;
  }

  // Returns role if negotiated, or empty absl::optional if it hasn't been
  // negotiated yet.
  absl::optional<rtc::SSLRole> GetDtlsRole() const;

  // TODO(deadbeef): Make this const. See comment in transportcontroller.h.
  bool GetStats(TransportStats* stats);

  const JsepTransportDescription* local_description() const {
    RTC_DCHECK_RUN_ON(network_thread_);
    return local_description_.get();
  }

  const JsepTransportDescription* remote_description() const {
    RTC_DCHECK_RUN_ON(network_thread_);
    return remote_description_.get();
  }

  // Returns the rtp transport, if any.
  webrtc::RtpTransportInternal* rtp_transport() const {
    if (dtls_srtp_transport_) {
      return dtls_srtp_transport_.get();
    }
    if (sdes_transport_) {
      return sdes_transport_.get();
    }
    if (unencrypted_rtp_transport_) {
      return unencrypted_rtp_transport_.get();
    }
    return nullptr;
  }

  const DtlsTransportInternal* rtp_dtls_transport() const {
    if (rtp_dtls_transport_) {
      return rtp_dtls_transport_->internal();
    }
    return nullptr;
  }

  DtlsTransportInternal* rtp_dtls_transport() {
    if (rtp_dtls_transport_) {
      return rtp_dtls_transport_->internal();
    }
    return nullptr;
  }

  const DtlsTransportInternal* rtcp_dtls_transport() const {
    RTC_DCHECK_RUN_ON(network_thread_);
    if (rtcp_dtls_transport_) {
      return rtcp_dtls_transport_->internal();
    }
    return nullptr;
  }

  DtlsTransportInternal* rtcp_dtls_transport() {
    RTC_DCHECK_RUN_ON(network_thread_);
    if (rtcp_dtls_transport_) {
      return rtcp_dtls_transport_->internal();
    }
    return nullptr;
  }

  rtc::scoped_refptr<webrtc::DtlsTransport> RtpDtlsTransport() {
    return rtp_dtls_transport_;
  }

  rtc::scoped_refptr<webrtc::SctpTransport> SctpTransport() const {
    return sctp_transport_;
  }

  // TODO(bugs.webrtc.org/9719): Delete method, update callers to use
  // SctpTransport() instead.
  webrtc::DataChannelTransportInterface* data_channel_transport() const {
    return sctp_transport_.get();
  }

  // TODO(deadbeef): The methods below are only public for testing. Should make
  // them utility functions or objects so they can be tested independently from
  // this class.

  // Returns an error if the certificate's identity does not match the
  // fingerprint, or either is NULL.
  webrtc::RTCError VerifyCertificateFingerprint(
      const rtc::RTCCertificate* certificate,
      const rtc::SSLFingerprint* fingerprint) const;

  void SetActiveResetSrtpParams(bool active_reset_srtp_params);

 private:
  bool SetRtcpMux(bool enable, webrtc::SdpType type, ContentSource source);

  void ActivateRtcpMux() RTC_RUN_ON(network_thread_);

  bool SetSdes(const std::vector<CryptoParams>& cryptos,
               const std::vector<int>& encrypted_extension_ids,
               webrtc::SdpType type,
               ContentSource source);

  // Negotiates and sets the DTLS parameters based on the current local and
  // remote transport description, such as the DTLS role to use, and whether
  // DTLS should be activated.
  //
  // Called when an answer TransportDescription is applied.
  webrtc::RTCError NegotiateAndSetDtlsParameters(
      webrtc::SdpType local_description_type);

  // Negotiates the DTLS role based off the offer and answer as specified by
  // RFC 4145, section-4.1. Returns an RTCError if role cannot be determined
  // from the local description and remote description.
  webrtc::RTCError NegotiateDtlsRole(
      webrtc::SdpType local_description_type,
      ConnectionRole local_connection_role,
      ConnectionRole remote_connection_role,
      absl::optional<rtc::SSLRole>* negotiated_dtls_role);

  // Pushes down the ICE parameters from the remote description.
  void SetRemoteIceParameters(const IceParameters& ice_parameters,
                              IceTransportInternal* ice);

  // Pushes down the DTLS parameters obtained via negotiation.
  static webrtc::RTCError SetNegotiatedDtlsParameters(
      DtlsTransportInternal* dtls_transport,
      absl::optional<rtc::SSLRole> dtls_role,
      rtc::SSLFingerprint* remote_fingerprint);

  bool GetTransportStats(DtlsTransportInternal* dtls_transport,
                         int component,
                         TransportStats* stats);

  // Owning thread, for safety checks
  const rtc::Thread* const network_thread_;
  const std::string mid_;
  // needs-ice-restart bit as described in JSEP.
  bool needs_ice_restart_ RTC_GUARDED_BY(network_thread_) = false;
  rtc::scoped_refptr<rtc::RTCCertificate> local_certificate_
      RTC_GUARDED_BY(network_thread_);
  std::unique_ptr<JsepTransportDescription> local_description_
      RTC_GUARDED_BY(network_thread_);
  std::unique_ptr<JsepTransportDescription> remote_description_
      RTC_GUARDED_BY(network_thread_);

  // Ice transport which may be used by any of upper-layer transports (below).
  // Owned by JsepTransport and guaranteed to outlive the transports below.
  const rtc::scoped_refptr<webrtc::IceTransportInterface> ice_transport_;
  const rtc::scoped_refptr<webrtc::IceTransportInterface> rtcp_ice_transport_;

  // To avoid downcasting and make it type safe, keep three unique pointers for
  // different SRTP mode and only one of these is non-nullptr.
  const std::unique_ptr<webrtc::RtpTransport> unencrypted_rtp_transport_;
  const std::unique_ptr<webrtc::SrtpTransport> sdes_transport_;
  const std::unique_ptr<webrtc::DtlsSrtpTransport> dtls_srtp_transport_;

  const rtc::scoped_refptr<webrtc::DtlsTransport> rtp_dtls_transport_;
  // The RTCP transport is const for all usages, except that it is cleared
  // when RTCP multiplexing is turned on; this happens on the network thread.
  rtc::scoped_refptr<webrtc::DtlsTransport> rtcp_dtls_transport_
      RTC_GUARDED_BY(network_thread_);

  const rtc::scoped_refptr<webrtc::SctpTransport> sctp_transport_;

  SrtpFilter sdes_negotiator_ RTC_GUARDED_BY(network_thread_);
  RtcpMuxFilter rtcp_mux_negotiator_ RTC_GUARDED_BY(network_thread_);

  // Cache the encrypted header extension IDs for SDES negoitation.
  absl::optional<std::vector<int>> send_extension_ids_
      RTC_GUARDED_BY(network_thread_);
  absl::optional<std::vector<int>> recv_extension_ids_
      RTC_GUARDED_BY(network_thread_);

  // This is invoked when RTCP-mux becomes active and
  // `rtcp_dtls_transport_` is destroyed. The JsepTransportController will
  // receive the callback and update the aggregate transport states.
  std::function<void()> rtcp_mux_active_callback_;
};

}  // namespace cricket

#endif  // PC_JSEP_TRANSPORT_H_
