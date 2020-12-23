/*
 *  Copyright 2017 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/jsep_transport_controller.h"

#include <memory>
#include <utility>

#include "absl/algorithm/container.h"
#include "api/ice_transport_factory.h"
#include "p2p/base/ice_transport_internal.h"
#include "p2p/base/port.h"
#include "pc/srtp_filter.h"
#include "rtc_base/bind.h"
#include "rtc_base/checks.h"
#include "rtc_base/thread.h"

using webrtc::SdpType;

namespace {

webrtc::RTCError VerifyCandidate(const cricket::Candidate& cand) {
  // No address zero.
  if (cand.address().IsNil() || cand.address().IsAnyIP()) {
    return webrtc::RTCError(webrtc::RTCErrorType::INVALID_PARAMETER,
                            "candidate has address of zero");
  }

  // Disallow all ports below 1024, except for 80 and 443 on public addresses.
  int port = cand.address().port();
  if (cand.protocol() == cricket::TCP_PROTOCOL_NAME &&
      (cand.tcptype() == cricket::TCPTYPE_ACTIVE_STR || port == 0)) {
    // Expected for active-only candidates per
    // http://tools.ietf.org/html/rfc6544#section-4.5 so no error.
    // Libjingle clients emit port 0, in "active" mode.
    return webrtc::RTCError::OK();
  }
  if (port < 1024) {
    if ((port != 80) && (port != 443)) {
      return webrtc::RTCError(
          webrtc::RTCErrorType::INVALID_PARAMETER,
          "candidate has port below 1024, but not 80 or 443");
    }

    if (cand.address().IsPrivateIP()) {
      return webrtc::RTCError(
          webrtc::RTCErrorType::INVALID_PARAMETER,
          "candidate has port of 80 or 443 with private IP address");
    }
  }

  return webrtc::RTCError::OK();
}

webrtc::RTCError VerifyCandidates(const cricket::Candidates& candidates) {
  for (const cricket::Candidate& candidate : candidates) {
    webrtc::RTCError error = VerifyCandidate(candidate);
    if (!error.ok()) {
      return error;
    }
  }
  return webrtc::RTCError::OK();
}

}  // namespace

namespace webrtc {

JsepTransportController::JsepTransportController(
    rtc::Thread* signaling_thread,
    rtc::Thread* network_thread,
    cricket::PortAllocator* port_allocator,
    AsyncResolverFactory* async_resolver_factory,
    Config config)
    : signaling_thread_(signaling_thread),
      network_thread_(network_thread),
      port_allocator_(port_allocator),
      async_resolver_factory_(async_resolver_factory),
      config_(config) {
  // The |transport_observer| is assumed to be non-null.
  RTC_DCHECK(config_.transport_observer);
  RTC_DCHECK(config_.rtcp_handler);
  RTC_DCHECK(config_.ice_transport_factory);
}

JsepTransportController::~JsepTransportController() {
  // Channel destructors may try to send packets, so this needs to happen on
  // the network thread.
  network_thread_->Invoke<void>(
      RTC_FROM_HERE,
      rtc::Bind(&JsepTransportController::DestroyAllJsepTransports_n, this));
}

RTCError JsepTransportController::SetLocalDescription(
    SdpType type,
    const cricket::SessionDescription* description) {
  if (!network_thread_->IsCurrent()) {
    return network_thread_->Invoke<RTCError>(
        RTC_FROM_HERE, [=] { return SetLocalDescription(type, description); });
  }

  if (!initial_offerer_.has_value()) {
    initial_offerer_.emplace(type == SdpType::kOffer);
    if (*initial_offerer_) {
      SetIceRole_n(cricket::ICEROLE_CONTROLLING);
    } else {
      SetIceRole_n(cricket::ICEROLE_CONTROLLED);
    }
  }
  return ApplyDescription_n(/*local=*/true, type, description);
}

RTCError JsepTransportController::SetRemoteDescription(
    SdpType type,
    const cricket::SessionDescription* description) {
  if (!network_thread_->IsCurrent()) {
    return network_thread_->Invoke<RTCError>(
        RTC_FROM_HERE, [=] { return SetRemoteDescription(type, description); });
  }

  return ApplyDescription_n(/*local=*/false, type, description);
}

RtpTransportInternal* JsepTransportController::GetRtpTransport(
    const std::string& mid) const {
  auto jsep_transport = GetJsepTransportForMid(mid);
  if (!jsep_transport) {
    return nullptr;
  }
  return jsep_transport->rtp_transport();
}

DataChannelTransportInterface* JsepTransportController::GetDataChannelTransport(
    const std::string& mid) const {
  auto jsep_transport = GetJsepTransportForMid(mid);
  if (!jsep_transport) {
    return nullptr;
  }
  return jsep_transport->data_channel_transport();
}

cricket::DtlsTransportInternal* JsepTransportController::GetDtlsTransport(
    const std::string& mid) {
  auto jsep_transport = GetJsepTransportForMid(mid);
  if (!jsep_transport) {
    return nullptr;
  }
  return jsep_transport->rtp_dtls_transport();
}

const cricket::DtlsTransportInternal*
JsepTransportController::GetRtcpDtlsTransport(const std::string& mid) const {
  auto jsep_transport = GetJsepTransportForMid(mid);
  if (!jsep_transport) {
    return nullptr;
  }
  return jsep_transport->rtcp_dtls_transport();
}

rtc::scoped_refptr<webrtc::DtlsTransport>
JsepTransportController::LookupDtlsTransportByMid(const std::string& mid) {
  auto jsep_transport = GetJsepTransportForMid(mid);
  if (!jsep_transport) {
    return nullptr;
  }
  return jsep_transport->RtpDtlsTransport();
}

rtc::scoped_refptr<SctpTransport> JsepTransportController::GetSctpTransport(
    const std::string& mid) const {
  auto jsep_transport = GetJsepTransportForMid(mid);
  if (!jsep_transport) {
    return nullptr;
  }
  return jsep_transport->SctpTransport();
}

void JsepTransportController::SetIceConfig(const cricket::IceConfig& config) {
  if (!network_thread_->IsCurrent()) {
    network_thread_->Invoke<void>(RTC_FROM_HERE, [&] { SetIceConfig(config); });
    return;
  }

  ice_config_ = config;
  for (auto& dtls : GetDtlsTransports()) {
    dtls->ice_transport()->SetIceConfig(ice_config_);
  }
}

void JsepTransportController::SetNeedsIceRestartFlag() {
  for (auto& kv : jsep_transports_by_name_) {
    kv.second->SetNeedsIceRestartFlag();
  }
}

bool JsepTransportController::NeedsIceRestart(
    const std::string& transport_name) const {
  const cricket::JsepTransport* transport =
      GetJsepTransportByName(transport_name);
  if (!transport) {
    return false;
  }
  return transport->needs_ice_restart();
}

absl::optional<rtc::SSLRole> JsepTransportController::GetDtlsRole(
    const std::string& mid) const {
  if (!network_thread_->IsCurrent()) {
    return network_thread_->Invoke<absl::optional<rtc::SSLRole>>(
        RTC_FROM_HERE, [&] { return GetDtlsRole(mid); });
  }

  const cricket::JsepTransport* t = GetJsepTransportForMid(mid);
  if (!t) {
    return absl::optional<rtc::SSLRole>();
  }
  return t->GetDtlsRole();
}

bool JsepTransportController::SetLocalCertificate(
    const rtc::scoped_refptr<rtc::RTCCertificate>& certificate) {
  if (!network_thread_->IsCurrent()) {
    return network_thread_->Invoke<bool>(
        RTC_FROM_HERE, [&] { return SetLocalCertificate(certificate); });
  }

  // Can't change a certificate, or set a null certificate.
  if (certificate_ || !certificate) {
    return false;
  }
  certificate_ = certificate;

  // Set certificate for JsepTransport, which verifies it matches the
  // fingerprint in SDP, and DTLS transport.
  // Fallback from DTLS to SDES is not supported.
  for (auto& kv : jsep_transports_by_name_) {
    kv.second->SetLocalCertificate(certificate_);
  }
  for (auto& dtls : GetDtlsTransports()) {
    bool set_cert_success = dtls->SetLocalCertificate(certificate_);
    RTC_DCHECK(set_cert_success);
  }
  return true;
}

rtc::scoped_refptr<rtc::RTCCertificate>
JsepTransportController::GetLocalCertificate(
    const std::string& transport_name) const {
  if (!network_thread_->IsCurrent()) {
    return network_thread_->Invoke<rtc::scoped_refptr<rtc::RTCCertificate>>(
        RTC_FROM_HERE, [&] { return GetLocalCertificate(transport_name); });
  }

  const cricket::JsepTransport* t = GetJsepTransportByName(transport_name);
  if (!t) {
    return nullptr;
  }
  return t->GetLocalCertificate();
}

std::unique_ptr<rtc::SSLCertChain>
JsepTransportController::GetRemoteSSLCertChain(
    const std::string& transport_name) const {
  if (!network_thread_->IsCurrent()) {
    return network_thread_->Invoke<std::unique_ptr<rtc::SSLCertChain>>(
        RTC_FROM_HERE, [&] { return GetRemoteSSLCertChain(transport_name); });
  }

  // Get the certificate from the RTP transport's DTLS handshake. Should be
  // identical to the RTCP transport's, since they were given the same remote
  // fingerprint.
  auto jsep_transport = GetJsepTransportByName(transport_name);
  if (!jsep_transport) {
    return nullptr;
  }
  auto dtls = jsep_transport->rtp_dtls_transport();
  if (!dtls) {
    return nullptr;
  }

  return dtls->GetRemoteSSLCertChain();
}

void JsepTransportController::MaybeStartGathering() {
  if (!network_thread_->IsCurrent()) {
    network_thread_->Invoke<void>(RTC_FROM_HERE,
                                  [&] { MaybeStartGathering(); });
    return;
  }

  for (auto& dtls : GetDtlsTransports()) {
    dtls->ice_transport()->MaybeStartGathering();
  }
}

RTCError JsepTransportController::AddRemoteCandidates(
    const std::string& transport_name,
    const cricket::Candidates& candidates) {
  if (!network_thread_->IsCurrent()) {
    return network_thread_->Invoke<RTCError>(RTC_FROM_HERE, [&] {
      return AddRemoteCandidates(transport_name, candidates);
    });
  }

  // Verify each candidate before passing down to the transport layer.
  RTCError error = VerifyCandidates(candidates);
  if (!error.ok()) {
    return error;
  }
  auto jsep_transport = GetJsepTransportByName(transport_name);
  if (!jsep_transport) {
    RTC_LOG(LS_WARNING) << "Not adding candidate because the JsepTransport "
                           "doesn't exist. Ignore it.";
    return RTCError::OK();
  }
  return jsep_transport->AddRemoteCandidates(candidates);
}

RTCError JsepTransportController::RemoveRemoteCandidates(
    const cricket::Candidates& candidates) {
  if (!network_thread_->IsCurrent()) {
    return network_thread_->Invoke<RTCError>(
        RTC_FROM_HERE, [&] { return RemoveRemoteCandidates(candidates); });
  }

  // Verify each candidate before passing down to the transport layer.
  RTCError error = VerifyCandidates(candidates);
  if (!error.ok()) {
    return error;
  }

  std::map<std::string, cricket::Candidates> candidates_by_transport_name;
  for (const cricket::Candidate& cand : candidates) {
    if (!cand.transport_name().empty()) {
      candidates_by_transport_name[cand.transport_name()].push_back(cand);
    } else {
      RTC_LOG(LS_ERROR) << "Not removing candidate because it does not have a "
                           "transport name set: "
                        << cand.ToSensitiveString();
    }
  }

  for (const auto& kv : candidates_by_transport_name) {
    const std::string& transport_name = kv.first;
    const cricket::Candidates& candidates = kv.second;
    cricket::JsepTransport* jsep_transport =
        GetJsepTransportByName(transport_name);
    if (!jsep_transport) {
      RTC_LOG(LS_WARNING)
          << "Not removing candidate because the JsepTransport doesn't exist.";
      continue;
    }
    for (const cricket::Candidate& candidate : candidates) {
      cricket::DtlsTransportInternal* dtls =
          candidate.component() == cricket::ICE_CANDIDATE_COMPONENT_RTP
              ? jsep_transport->rtp_dtls_transport()
              : jsep_transport->rtcp_dtls_transport();
      if (dtls) {
        dtls->ice_transport()->RemoveRemoteCandidate(candidate);
      }
    }
  }
  return RTCError::OK();
}

bool JsepTransportController::GetStats(const std::string& transport_name,
                                       cricket::TransportStats* stats) {
  if (!network_thread_->IsCurrent()) {
    return network_thread_->Invoke<bool>(
        RTC_FROM_HERE, [=] { return GetStats(transport_name, stats); });
  }

  cricket::JsepTransport* transport = GetJsepTransportByName(transport_name);
  if (!transport) {
    return false;
  }
  return transport->GetStats(stats);
}

void JsepTransportController::SetActiveResetSrtpParams(
    bool active_reset_srtp_params) {
  if (!network_thread_->IsCurrent()) {
    network_thread_->Invoke<void>(RTC_FROM_HERE, [=] {
      SetActiveResetSrtpParams(active_reset_srtp_params);
    });
    return;
  }

  RTC_LOG(INFO)
      << "Updating the active_reset_srtp_params for JsepTransportController: "
      << active_reset_srtp_params;
  config_.active_reset_srtp_params = active_reset_srtp_params;
  for (auto& kv : jsep_transports_by_name_) {
    kv.second->SetActiveResetSrtpParams(active_reset_srtp_params);
  }
}

void JsepTransportController::RollbackTransports() {
  if (!network_thread_->IsCurrent()) {
    network_thread_->Invoke<void>(RTC_FROM_HERE, [=] { RollbackTransports(); });
    return;
  }
  RTC_DCHECK_RUN_ON(network_thread_);
  for (auto&& mid : pending_mids_) {
    RemoveTransportForMid(mid);
  }
  for (auto&& mid : pending_mids_) {
    MaybeDestroyJsepTransport(mid);
  }
  pending_mids_.clear();
}

rtc::scoped_refptr<webrtc::IceTransportInterface>
JsepTransportController::CreateIceTransport(const std::string& transport_name,
                                            bool rtcp) {
  int component = rtcp ? cricket::ICE_CANDIDATE_COMPONENT_RTCP
                       : cricket::ICE_CANDIDATE_COMPONENT_RTP;

  IceTransportInit init;
  init.set_port_allocator(port_allocator_);
  init.set_async_resolver_factory(async_resolver_factory_);
  init.set_event_log(config_.event_log);
  return config_.ice_transport_factory->CreateIceTransport(
      transport_name, component, std::move(init));
}

std::unique_ptr<cricket::DtlsTransportInternal>
JsepTransportController::CreateDtlsTransport(
    const cricket::ContentInfo& content_info,
    cricket::IceTransportInternal* ice) {
  RTC_DCHECK(network_thread_->IsCurrent());

  std::unique_ptr<cricket::DtlsTransportInternal> dtls;

  if (config_.dtls_transport_factory) {
    dtls = config_.dtls_transport_factory->CreateDtlsTransport(
        ice, config_.crypto_options);
  } else {
    dtls = std::make_unique<cricket::DtlsTransport>(ice, config_.crypto_options,
                                                    config_.event_log);
  }

  RTC_DCHECK(dtls);
  dtls->SetSslMaxProtocolVersion(config_.ssl_max_version);
  dtls->ice_transport()->SetIceRole(ice_role_);
  dtls->ice_transport()->SetIceTiebreaker(ice_tiebreaker_);
  dtls->ice_transport()->SetIceConfig(ice_config_);
  if (certificate_) {
    bool set_cert_success = dtls->SetLocalCertificate(certificate_);
    RTC_DCHECK(set_cert_success);
  }

  // Connect to signals offered by the DTLS and ICE transport.
  dtls->SignalWritableState.connect(
      this, &JsepTransportController::OnTransportWritableState_n);
  dtls->SignalReceivingState.connect(
      this, &JsepTransportController::OnTransportReceivingState_n);
  dtls->SignalDtlsHandshakeError.connect(
      this, &JsepTransportController::OnDtlsHandshakeError);
  dtls->ice_transport()->SignalGatheringState.connect(
      this, &JsepTransportController::OnTransportGatheringState_n);
  dtls->ice_transport()->SignalCandidateGathered.connect(
      this, &JsepTransportController::OnTransportCandidateGathered_n);
  dtls->ice_transport()->SignalCandidateError.connect(
      this, &JsepTransportController::OnTransportCandidateError_n);
  dtls->ice_transport()->SignalCandidatesRemoved.connect(
      this, &JsepTransportController::OnTransportCandidatesRemoved_n);
  dtls->ice_transport()->SignalRoleConflict.connect(
      this, &JsepTransportController::OnTransportRoleConflict_n);
  dtls->ice_transport()->SignalStateChanged.connect(
      this, &JsepTransportController::OnTransportStateChanged_n);
  dtls->ice_transport()->SignalIceTransportStateChanged.connect(
      this, &JsepTransportController::OnTransportStateChanged_n);
  dtls->ice_transport()->SignalCandidatePairChanged.connect(
      this, &JsepTransportController::OnTransportCandidatePairChanged_n);
  return dtls;
}

std::unique_ptr<webrtc::RtpTransport>
JsepTransportController::CreateUnencryptedRtpTransport(
    const std::string& transport_name,
    rtc::PacketTransportInternal* rtp_packet_transport,
    rtc::PacketTransportInternal* rtcp_packet_transport) {
  RTC_DCHECK(network_thread_->IsCurrent());
  auto unencrypted_rtp_transport =
      std::make_unique<RtpTransport>(rtcp_packet_transport == nullptr);
  unencrypted_rtp_transport->SetRtpPacketTransport(rtp_packet_transport);
  if (rtcp_packet_transport) {
    unencrypted_rtp_transport->SetRtcpPacketTransport(rtcp_packet_transport);
  }
  return unencrypted_rtp_transport;
}

std::unique_ptr<webrtc::SrtpTransport>
JsepTransportController::CreateSdesTransport(
    const std::string& transport_name,
    cricket::DtlsTransportInternal* rtp_dtls_transport,
    cricket::DtlsTransportInternal* rtcp_dtls_transport) {
  RTC_DCHECK(network_thread_->IsCurrent());
  auto srtp_transport =
      std::make_unique<webrtc::SrtpTransport>(rtcp_dtls_transport == nullptr);
  RTC_DCHECK(rtp_dtls_transport);
  srtp_transport->SetRtpPacketTransport(rtp_dtls_transport);
  if (rtcp_dtls_transport) {
    srtp_transport->SetRtcpPacketTransport(rtcp_dtls_transport);
  }
  if (config_.enable_external_auth) {
    srtp_transport->EnableExternalAuth();
  }
  return srtp_transport;
}

std::unique_ptr<webrtc::DtlsSrtpTransport>
JsepTransportController::CreateDtlsSrtpTransport(
    const std::string& transport_name,
    cricket::DtlsTransportInternal* rtp_dtls_transport,
    cricket::DtlsTransportInternal* rtcp_dtls_transport) {
  RTC_DCHECK(network_thread_->IsCurrent());
  auto dtls_srtp_transport = std::make_unique<webrtc::DtlsSrtpTransport>(
      rtcp_dtls_transport == nullptr);
  if (config_.enable_external_auth) {
    dtls_srtp_transport->EnableExternalAuth();
  }

  dtls_srtp_transport->SetDtlsTransports(rtp_dtls_transport,
                                         rtcp_dtls_transport);
  dtls_srtp_transport->SetActiveResetSrtpParams(
      config_.active_reset_srtp_params);
  dtls_srtp_transport->SignalDtlsStateChange.connect(
      this, &JsepTransportController::UpdateAggregateStates_n);
  dtls_srtp_transport->SetOnErrorDemuxingPacket([this](uint32_t ssrc) {
      this->JsepTransportController::ErrorDemuxingPacket_n(ssrc);
  });
  return dtls_srtp_transport;
}

std::vector<cricket::DtlsTransportInternal*>
JsepTransportController::GetDtlsTransports() {
  std::vector<cricket::DtlsTransportInternal*> dtls_transports;
  for (auto it = jsep_transports_by_name_.begin();
       it != jsep_transports_by_name_.end(); ++it) {
    auto jsep_transport = it->second.get();
    RTC_DCHECK(jsep_transport);
    if (jsep_transport->rtp_dtls_transport()) {
      dtls_transports.push_back(jsep_transport->rtp_dtls_transport());
    }

    if (jsep_transport->rtcp_dtls_transport()) {
      dtls_transports.push_back(jsep_transport->rtcp_dtls_transport());
    }
  }
  return dtls_transports;
}

RTCError JsepTransportController::ApplyDescription_n(
    bool local,
    SdpType type,
    const cricket::SessionDescription* description) {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_DCHECK(description);

  if (local) {
    local_desc_ = description;
  } else {
    remote_desc_ = description;
  }

  RTCError error;
  error = ValidateAndMaybeUpdateBundleGroup(local, type, description);
  if (!error.ok()) {
    return error;
  }

  std::vector<int> merged_encrypted_extension_ids;
  if (bundle_group_) {
    merged_encrypted_extension_ids =
        MergeEncryptedHeaderExtensionIdsForBundle(description);
  }

  for (const cricket::ContentInfo& content_info : description->contents()) {
    // Don't create transports for rejected m-lines and bundled m-lines."
    if (content_info.rejected ||
        (IsBundled(content_info.name) && content_info.name != *bundled_mid())) {
      continue;
    }
    error = MaybeCreateJsepTransport(local, content_info, *description);
    if (!error.ok()) {
      return error;
    }
  }

  RTC_DCHECK(description->contents().size() ==
             description->transport_infos().size());
  for (size_t i = 0; i < description->contents().size(); ++i) {
    const cricket::ContentInfo& content_info = description->contents()[i];
    const cricket::TransportInfo& transport_info =
        description->transport_infos()[i];
    if (content_info.rejected) {
      HandleRejectedContent(content_info, description);
      continue;
    }

    if (IsBundled(content_info.name) && content_info.name != *bundled_mid()) {
      if (!HandleBundledContent(content_info)) {
        return RTCError(RTCErrorType::INVALID_PARAMETER,
                        "Failed to process the bundled m= section with mid='" +
                            content_info.name + "'.");
      }
      continue;
    }

    error = ValidateContent(content_info);
    if (!error.ok()) {
      return error;
    }

    std::vector<int> extension_ids;
    if (bundled_mid() && content_info.name == *bundled_mid()) {
      extension_ids = merged_encrypted_extension_ids;
    } else {
      extension_ids = GetEncryptedHeaderExtensionIds(content_info);
    }

    int rtp_abs_sendtime_extn_id =
        GetRtpAbsSendTimeHeaderExtensionId(content_info);

    cricket::JsepTransport* transport =
        GetJsepTransportForMid(content_info.name);
    RTC_DCHECK(transport);

    SetIceRole_n(DetermineIceRole(transport, transport_info, type, local));

    cricket::JsepTransportDescription jsep_description =
        CreateJsepTransportDescription(content_info, transport_info,
                                       extension_ids, rtp_abs_sendtime_extn_id);
    if (local) {
      error =
          transport->SetLocalJsepTransportDescription(jsep_description, type);
    } else {
      error =
          transport->SetRemoteJsepTransportDescription(jsep_description, type);
    }

    if (!error.ok()) {
      LOG_AND_RETURN_ERROR(
          RTCErrorType::INVALID_PARAMETER,
          "Failed to apply the description for m= section with mid='" +
              content_info.name + "': " + error.message());
    }
  }
  if (type == SdpType::kAnswer) {
    pending_mids_.clear();
  }
  return RTCError::OK();
}

RTCError JsepTransportController::ValidateAndMaybeUpdateBundleGroup(
    bool local,
    SdpType type,
    const cricket::SessionDescription* description) {
  RTC_DCHECK(description);
  const cricket::ContentGroup* new_bundle_group =
      description->GetGroupByName(cricket::GROUP_TYPE_BUNDLE);

  // The BUNDLE group containing a MID that no m= section has is invalid.
  if (new_bundle_group) {
    for (const std::string& content_name : new_bundle_group->content_names()) {
      if (!description->GetContentByName(content_name)) {
        return RTCError(RTCErrorType::INVALID_PARAMETER,
                        "The BUNDLE group contains MID='" + content_name +
                            "' matching no m= section.");
      }
    }
  }

  if (type == SdpType::kAnswer) {
    const cricket::ContentGroup* offered_bundle_group =
        local ? remote_desc_->GetGroupByName(cricket::GROUP_TYPE_BUNDLE)
              : local_desc_->GetGroupByName(cricket::GROUP_TYPE_BUNDLE);

    if (new_bundle_group) {
      // The BUNDLE group in answer should be a subset of offered group.
      for (const std::string& content_name :
           new_bundle_group->content_names()) {
        if (!offered_bundle_group ||
            !offered_bundle_group->HasContentName(content_name)) {
          return RTCError(RTCErrorType::INVALID_PARAMETER,
                          "The BUNDLE group in answer contains a MID='" +
                              content_name +
                              "' that was "
                              "not in the offered group.");
        }
      }
    }

    if (bundle_group_) {
      for (const std::string& content_name : bundle_group_->content_names()) {
        // An answer that removes m= sections from pre-negotiated BUNDLE group
        // without rejecting it, is invalid.
        if (!new_bundle_group ||
            !new_bundle_group->HasContentName(content_name)) {
          auto* content_info = description->GetContentByName(content_name);
          if (!content_info || !content_info->rejected) {
            return RTCError(RTCErrorType::INVALID_PARAMETER,
                            "Answer cannot remove m= section with mid='" +
                                content_name +
                                "' from already-established BUNDLE group.");
          }
        }
      }
    }
  }

  if (config_.bundle_policy ==
          PeerConnectionInterface::kBundlePolicyMaxBundle &&
      !description->HasGroup(cricket::GROUP_TYPE_BUNDLE)) {
    return RTCError(RTCErrorType::INVALID_PARAMETER,
                    "max-bundle is used but no bundle group found.");
  }

  if (ShouldUpdateBundleGroup(type, description)) {
    bundle_group_ = *new_bundle_group;
  }

  if (!bundled_mid()) {
    return RTCError::OK();
  }

  auto bundled_content = description->GetContentByName(*bundled_mid());
  if (!bundled_content) {
    return RTCError(
        RTCErrorType::INVALID_PARAMETER,
        "An m= section associated with the BUNDLE-tag doesn't exist.");
  }

  // If the |bundled_content| is rejected, other contents in the bundle group
  // should be rejected.
  if (bundled_content->rejected) {
    for (const auto& content_name : bundle_group_->content_names()) {
      auto other_content = description->GetContentByName(content_name);
      if (!other_content->rejected) {
        return RTCError(RTCErrorType::INVALID_PARAMETER,
                        "The m= section with mid='" + content_name +
                            "' should be rejected.");
      }
    }
  }

  return RTCError::OK();
}

RTCError JsepTransportController::ValidateContent(
    const cricket::ContentInfo& content_info) {
  if (config_.rtcp_mux_policy ==
          PeerConnectionInterface::kRtcpMuxPolicyRequire &&
      content_info.type == cricket::MediaProtocolType::kRtp &&
      !content_info.media_description()->rtcp_mux()) {
    return RTCError(RTCErrorType::INVALID_PARAMETER,
                    "The m= section with mid='" + content_info.name +
                        "' is invalid. RTCP-MUX is not "
                        "enabled when it is required.");
  }
  return RTCError::OK();
}

void JsepTransportController::HandleRejectedContent(
    const cricket::ContentInfo& content_info,
    const cricket::SessionDescription* description) {
  // If the content is rejected, let the
  // BaseChannel/SctpTransport change the RtpTransport/DtlsTransport first,
  // then destroy the cricket::JsepTransport.
  RemoveTransportForMid(content_info.name);
  if (content_info.name == bundled_mid()) {
    for (const auto& content_name : bundle_group_->content_names()) {
      RemoveTransportForMid(content_name);
    }
    bundle_group_.reset();
  } else if (IsBundled(content_info.name)) {
    // Remove the rejected content from the |bundle_group_|.
    bundle_group_->RemoveContentName(content_info.name);
    // Reset the bundle group if nothing left.
    if (!bundle_group_->FirstContentName()) {
      bundle_group_.reset();
    }
  }
  MaybeDestroyJsepTransport(content_info.name);
}

bool JsepTransportController::HandleBundledContent(
    const cricket::ContentInfo& content_info) {
  auto jsep_transport = GetJsepTransportByName(*bundled_mid());
  RTC_DCHECK(jsep_transport);
  // If the content is bundled, let the
  // BaseChannel/SctpTransport change the RtpTransport/DtlsTransport first,
  // then destroy the cricket::JsepTransport.
  if (SetTransportForMid(content_info.name, jsep_transport)) {
    // TODO(bugs.webrtc.org/9719) For media transport this is far from ideal,
    // because it means that we first create media transport and start
    // connecting it, and then we destroy it. We will need to address it before
    // video path is enabled.
    MaybeDestroyJsepTransport(content_info.name);
    return true;
  }
  return false;
}

bool JsepTransportController::SetTransportForMid(
    const std::string& mid,
    cricket::JsepTransport* jsep_transport) {
  RTC_DCHECK(jsep_transport);
  if (mid_to_transport_[mid] == jsep_transport) {
    return true;
  }
  RTC_DCHECK_RUN_ON(network_thread_);
  pending_mids_.push_back(mid);
  mid_to_transport_[mid] = jsep_transport;
  return config_.transport_observer->OnTransportChanged(
      mid, jsep_transport->rtp_transport(), jsep_transport->RtpDtlsTransport(),
      jsep_transport->data_channel_transport());
}

void JsepTransportController::RemoveTransportForMid(const std::string& mid) {
  bool ret = config_.transport_observer->OnTransportChanged(mid, nullptr,
                                                            nullptr, nullptr);
  // Calling OnTransportChanged with nullptr should always succeed, since it is
  // only expected to fail when adding media to a transport (not removing).
  RTC_DCHECK(ret);
  mid_to_transport_.erase(mid);
}

cricket::JsepTransportDescription
JsepTransportController::CreateJsepTransportDescription(
    const cricket::ContentInfo& content_info,
    const cricket::TransportInfo& transport_info,
    const std::vector<int>& encrypted_extension_ids,
    int rtp_abs_sendtime_extn_id) {
  const cricket::MediaContentDescription* content_desc =
      content_info.media_description();
  RTC_DCHECK(content_desc);
  bool rtcp_mux_enabled = content_info.type == cricket::MediaProtocolType::kSctp
                              ? true
                              : content_desc->rtcp_mux();

  return cricket::JsepTransportDescription(
      rtcp_mux_enabled, content_desc->cryptos(), encrypted_extension_ids,
      rtp_abs_sendtime_extn_id, transport_info.description);
}

bool JsepTransportController::ShouldUpdateBundleGroup(
    SdpType type,
    const cricket::SessionDescription* description) {
  if (config_.bundle_policy ==
      PeerConnectionInterface::kBundlePolicyMaxBundle) {
    return true;
  }

  if (type != SdpType::kAnswer) {
    return false;
  }

  RTC_DCHECK(local_desc_ && remote_desc_);
  const cricket::ContentGroup* local_bundle =
      local_desc_->GetGroupByName(cricket::GROUP_TYPE_BUNDLE);
  const cricket::ContentGroup* remote_bundle =
      remote_desc_->GetGroupByName(cricket::GROUP_TYPE_BUNDLE);
  return local_bundle && remote_bundle;
}

std::vector<int> JsepTransportController::GetEncryptedHeaderExtensionIds(
    const cricket::ContentInfo& content_info) {
  const cricket::MediaContentDescription* content_desc =
      content_info.media_description();

  if (!config_.crypto_options.srtp.enable_encrypted_rtp_header_extensions) {
    return std::vector<int>();
  }

  std::vector<int> encrypted_header_extension_ids;
  for (const auto& extension : content_desc->rtp_header_extensions()) {
    if (!extension.encrypt) {
      continue;
    }
    if (!absl::c_linear_search(encrypted_header_extension_ids, extension.id)) {
      encrypted_header_extension_ids.push_back(extension.id);
    }
  }
  return encrypted_header_extension_ids;
}

std::vector<int>
JsepTransportController::MergeEncryptedHeaderExtensionIdsForBundle(
    const cricket::SessionDescription* description) {
  RTC_DCHECK(description);
  RTC_DCHECK(bundle_group_);

  std::vector<int> merged_ids;
  // Union the encrypted header IDs in the group when bundle is enabled.
  for (const cricket::ContentInfo& content_info : description->contents()) {
    if (bundle_group_->HasContentName(content_info.name)) {
      std::vector<int> extension_ids =
          GetEncryptedHeaderExtensionIds(content_info);
      for (int id : extension_ids) {
        if (!absl::c_linear_search(merged_ids, id)) {
          merged_ids.push_back(id);
        }
      }
    }
  }
  return merged_ids;
}

int JsepTransportController::GetRtpAbsSendTimeHeaderExtensionId(
    const cricket::ContentInfo& content_info) {
  if (!config_.enable_external_auth) {
    return -1;
  }

  const cricket::MediaContentDescription* content_desc =
      content_info.media_description();

  const webrtc::RtpExtension* send_time_extension =
      webrtc::RtpExtension::FindHeaderExtensionByUri(
          content_desc->rtp_header_extensions(),
          webrtc::RtpExtension::kAbsSendTimeUri);
  return send_time_extension ? send_time_extension->id : -1;
}

const cricket::JsepTransport* JsepTransportController::GetJsepTransportForMid(
    const std::string& mid) const {
  auto it = mid_to_transport_.find(mid);
  return it == mid_to_transport_.end() ? nullptr : it->second;
}

cricket::JsepTransport* JsepTransportController::GetJsepTransportForMid(
    const std::string& mid) {
  auto it = mid_to_transport_.find(mid);
  return it == mid_to_transport_.end() ? nullptr : it->second;
}

const cricket::JsepTransport* JsepTransportController::GetJsepTransportByName(
    const std::string& transport_name) const {
  auto it = jsep_transports_by_name_.find(transport_name);
  return (it == jsep_transports_by_name_.end()) ? nullptr : it->second.get();
}

cricket::JsepTransport* JsepTransportController::GetJsepTransportByName(
    const std::string& transport_name) {
  auto it = jsep_transports_by_name_.find(transport_name);
  return (it == jsep_transports_by_name_.end()) ? nullptr : it->second.get();
}

RTCError JsepTransportController::MaybeCreateJsepTransport(
    bool local,
    const cricket::ContentInfo& content_info,
    const cricket::SessionDescription& description) {
  RTC_DCHECK(network_thread_->IsCurrent());
  cricket::JsepTransport* transport = GetJsepTransportByName(content_info.name);
  if (transport) {
    return RTCError::OK();
  }

  const cricket::MediaContentDescription* content_desc =
      content_info.media_description();
  if (certificate_ && !content_desc->cryptos().empty()) {
    return RTCError(RTCErrorType::INVALID_PARAMETER,
                    "SDES and DTLS-SRTP cannot be enabled at the same time.");
  }

  rtc::scoped_refptr<webrtc::IceTransportInterface> ice =
      CreateIceTransport(content_info.name, /*rtcp=*/false);
  RTC_DCHECK(ice);

  std::unique_ptr<cricket::DtlsTransportInternal> rtp_dtls_transport =
      CreateDtlsTransport(content_info, ice->internal());

  std::unique_ptr<cricket::DtlsTransportInternal> rtcp_dtls_transport;
  std::unique_ptr<RtpTransport> unencrypted_rtp_transport;
  std::unique_ptr<SrtpTransport> sdes_transport;
  std::unique_ptr<DtlsSrtpTransport> dtls_srtp_transport;
  std::unique_ptr<RtpTransportInternal> datagram_rtp_transport;

  rtc::scoped_refptr<webrtc::IceTransportInterface> rtcp_ice;
  if (config_.rtcp_mux_policy !=
          PeerConnectionInterface::kRtcpMuxPolicyRequire &&
      content_info.type == cricket::MediaProtocolType::kRtp) {
    rtcp_ice = CreateIceTransport(content_info.name, /*rtcp=*/true);
    rtcp_dtls_transport =
        CreateDtlsTransport(content_info, rtcp_ice->internal());
  }

  if (config_.disable_encryption) {
    RTC_LOG(LS_INFO)
        << "Creating UnencryptedRtpTransport, becayse encryption is disabled.";
    unencrypted_rtp_transport = CreateUnencryptedRtpTransport(
        content_info.name, rtp_dtls_transport.get(), rtcp_dtls_transport.get());
  } else if (!content_desc->cryptos().empty()) {
    sdes_transport = CreateSdesTransport(
        content_info.name, rtp_dtls_transport.get(), rtcp_dtls_transport.get());
    RTC_LOG(LS_INFO) << "Creating SdesTransport.";
  } else {
    RTC_LOG(LS_INFO) << "Creating DtlsSrtpTransport.";
    dtls_srtp_transport = CreateDtlsSrtpTransport(
        content_info.name, rtp_dtls_transport.get(), rtcp_dtls_transport.get());
  }

  std::unique_ptr<cricket::SctpTransportInternal> sctp_transport;
  if (config_.sctp_factory) {
    sctp_transport =
        config_.sctp_factory->CreateSctpTransport(rtp_dtls_transport.get());
  }

  std::unique_ptr<cricket::JsepTransport> jsep_transport =
      std::make_unique<cricket::JsepTransport>(
          content_info.name, certificate_, std::move(ice), std::move(rtcp_ice),
          std::move(unencrypted_rtp_transport), std::move(sdes_transport),
          std::move(dtls_srtp_transport), std::move(datagram_rtp_transport),
          std::move(rtp_dtls_transport), std::move(rtcp_dtls_transport),
          std::move(sctp_transport));

  jsep_transport->rtp_transport()->SignalRtcpPacketReceived.connect(
      this, &JsepTransportController::OnRtcpPacketReceived_n);

  jsep_transport->SignalRtcpMuxActive.connect(
      this, &JsepTransportController::UpdateAggregateStates_n);
  SetTransportForMid(content_info.name, jsep_transport.get());

  jsep_transports_by_name_[content_info.name] = std::move(jsep_transport);
  UpdateAggregateStates_n();
  return RTCError::OK();
}

void JsepTransportController::MaybeDestroyJsepTransport(
    const std::string& mid) {
  auto jsep_transport = GetJsepTransportByName(mid);
  if (!jsep_transport) {
    return;
  }

  // Don't destroy the JsepTransport if there are still media sections referring
  // to it.
  for (const auto& kv : mid_to_transport_) {
    if (kv.second == jsep_transport) {
      return;
    }
  }

  jsep_transports_by_name_.erase(mid);
  UpdateAggregateStates_n();
}

void JsepTransportController::DestroyAllJsepTransports_n() {
  RTC_DCHECK(network_thread_->IsCurrent());

  for (const auto& jsep_transport : jsep_transports_by_name_) {
    config_.transport_observer->OnTransportChanged(jsep_transport.first,
                                                   nullptr, nullptr, nullptr);
  }

  jsep_transports_by_name_.clear();
}

void JsepTransportController::SetIceRole_n(cricket::IceRole ice_role) {
  RTC_DCHECK(network_thread_->IsCurrent());

  ice_role_ = ice_role;
  for (auto& dtls : GetDtlsTransports()) {
    dtls->ice_transport()->SetIceRole(ice_role_);
  }
}

cricket::IceRole JsepTransportController::DetermineIceRole(
    cricket::JsepTransport* jsep_transport,
    const cricket::TransportInfo& transport_info,
    SdpType type,
    bool local) {
  cricket::IceRole ice_role = ice_role_;
  auto tdesc = transport_info.description;
  if (local) {
    // The initial offer side may use ICE Lite, in which case, per RFC5245
    // Section 5.1.1, the answer side should take the controlling role if it is
    // in the full ICE mode.
    //
    // When both sides use ICE Lite, the initial offer side must take the
    // controlling role, and this is the default logic implemented in
    // SetLocalDescription in JsepTransportController.
    if (jsep_transport->remote_description() &&
        jsep_transport->remote_description()->transport_desc.ice_mode ==
            cricket::ICEMODE_LITE &&
        ice_role_ == cricket::ICEROLE_CONTROLLED &&
        tdesc.ice_mode == cricket::ICEMODE_FULL) {
      ice_role = cricket::ICEROLE_CONTROLLING;
    }
  } else {
    // If our role is cricket::ICEROLE_CONTROLLED and the remote endpoint
    // supports only ice_lite, this local endpoint should take the CONTROLLING
    // role.
    // TODO(deadbeef): This is a session-level attribute, so it really shouldn't
    // be in a TransportDescription in the first place...
    if (ice_role_ == cricket::ICEROLE_CONTROLLED &&
        tdesc.ice_mode == cricket::ICEMODE_LITE) {
      ice_role = cricket::ICEROLE_CONTROLLING;
    }

    // If we use ICE Lite and the remote endpoint uses the full implementation
    // of ICE, the local endpoint must take the controlled role, and the other
    // side must be the controlling role.
    if (jsep_transport->local_description() &&
        jsep_transport->local_description()->transport_desc.ice_mode ==
            cricket::ICEMODE_LITE &&
        ice_role_ == cricket::ICEROLE_CONTROLLING &&
        tdesc.ice_mode == cricket::ICEMODE_FULL) {
      ice_role = cricket::ICEROLE_CONTROLLED;
    }
  }

  return ice_role;
}

void JsepTransportController::OnTransportWritableState_n(
    rtc::PacketTransportInternal* transport) {
  RTC_DCHECK(network_thread_->IsCurrent());
  RTC_LOG(LS_INFO) << " Transport " << transport->transport_name()
                   << " writability changed to " << transport->writable()
                   << ".";
  UpdateAggregateStates_n();
}

void JsepTransportController::OnTransportReceivingState_n(
    rtc::PacketTransportInternal* transport) {
  RTC_DCHECK(network_thread_->IsCurrent());
  UpdateAggregateStates_n();
}

void JsepTransportController::OnTransportGatheringState_n(
    cricket::IceTransportInternal* transport) {
  RTC_DCHECK(network_thread_->IsCurrent());
  UpdateAggregateStates_n();
}

void JsepTransportController::OnTransportCandidateGathered_n(
    cricket::IceTransportInternal* transport,
    const cricket::Candidate& candidate) {
  RTC_DCHECK(network_thread_->IsCurrent());

  // We should never signal peer-reflexive candidates.
  if (candidate.type() == cricket::PRFLX_PORT_TYPE) {
    RTC_NOTREACHED();
    return;
  }
  std::string transport_name = transport->transport_name();
  invoker_.AsyncInvoke<void>(
      RTC_FROM_HERE, signaling_thread_, [this, transport_name, candidate] {
        SignalIceCandidatesGathered(transport_name, {candidate});
      });
}

void JsepTransportController::OnTransportCandidateError_n(
    cricket::IceTransportInternal* transport,
    const cricket::IceCandidateErrorEvent& event) {
  RTC_DCHECK(network_thread_->IsCurrent());

  invoker_.AsyncInvoke<void>(RTC_FROM_HERE, signaling_thread_,
                             [this, event] { SignalIceCandidateError(event); });
}
void JsepTransportController::OnTransportCandidatesRemoved_n(
    cricket::IceTransportInternal* transport,
    const cricket::Candidates& candidates) {
  invoker_.AsyncInvoke<void>(
      RTC_FROM_HERE, signaling_thread_,
      [this, candidates] { SignalIceCandidatesRemoved(candidates); });
}
void JsepTransportController::OnTransportCandidatePairChanged_n(
    const cricket::CandidatePairChangeEvent& event) {
  invoker_.AsyncInvoke<void>(RTC_FROM_HERE, signaling_thread_, [this, event] {
    SignalIceCandidatePairChanged(event);
  });
}

void JsepTransportController::OnTransportRoleConflict_n(
    cricket::IceTransportInternal* transport) {
  RTC_DCHECK(network_thread_->IsCurrent());
  // Note: since the role conflict is handled entirely on the network thread,
  // we don't need to worry about role conflicts occurring on two ports at
  // once. The first one encountered should immediately reverse the role.
  cricket::IceRole reversed_role = (ice_role_ == cricket::ICEROLE_CONTROLLING)
                                       ? cricket::ICEROLE_CONTROLLED
                                       : cricket::ICEROLE_CONTROLLING;
  RTC_LOG(LS_INFO) << "Got role conflict; switching to "
                   << (reversed_role == cricket::ICEROLE_CONTROLLING
                           ? "controlling"
                           : "controlled")
                   << " role.";
  SetIceRole_n(reversed_role);
}

void JsepTransportController::OnTransportStateChanged_n(
    cricket::IceTransportInternal* transport) {
  RTC_DCHECK(network_thread_->IsCurrent());
  RTC_LOG(LS_INFO) << transport->transport_name() << " Transport "
                   << transport->component()
                   << " state changed. Check if state is complete.";
  UpdateAggregateStates_n();
}

void JsepTransportController::UpdateAggregateStates_n() {
  RTC_DCHECK(network_thread_->IsCurrent());

  auto dtls_transports = GetDtlsTransports();
  cricket::IceConnectionState new_connection_state =
      cricket::kIceConnectionConnecting;
  PeerConnectionInterface::IceConnectionState new_ice_connection_state =
      PeerConnectionInterface::IceConnectionState::kIceConnectionNew;
  PeerConnectionInterface::PeerConnectionState new_combined_state =
      PeerConnectionInterface::PeerConnectionState::kNew;
  cricket::IceGatheringState new_gathering_state = cricket::kIceGatheringNew;
  bool any_failed = false;
  bool all_connected = !dtls_transports.empty();
  bool all_completed = !dtls_transports.empty();
  bool any_gathering = false;
  bool all_done_gathering = !dtls_transports.empty();

  std::map<IceTransportState, int> ice_state_counts;
  std::map<cricket::DtlsTransportState, int> dtls_state_counts;

  for (const auto& dtls : dtls_transports) {
    any_failed = any_failed || dtls->ice_transport()->GetState() ==
                                   cricket::IceTransportState::STATE_FAILED;
    all_connected = all_connected && dtls->writable();
    all_completed =
        all_completed && dtls->writable() &&
        dtls->ice_transport()->GetState() ==
            cricket::IceTransportState::STATE_COMPLETED &&
        dtls->ice_transport()->GetIceRole() == cricket::ICEROLE_CONTROLLING &&
        dtls->ice_transport()->gathering_state() ==
            cricket::kIceGatheringComplete;
    any_gathering = any_gathering || dtls->ice_transport()->gathering_state() !=
                                         cricket::kIceGatheringNew;
    all_done_gathering =
        all_done_gathering && dtls->ice_transport()->gathering_state() ==
                                  cricket::kIceGatheringComplete;

    dtls_state_counts[dtls->dtls_state()]++;
    ice_state_counts[dtls->ice_transport()->GetIceTransportState()]++;
  }

  if (any_failed) {
    new_connection_state = cricket::kIceConnectionFailed;
  } else if (all_completed) {
    new_connection_state = cricket::kIceConnectionCompleted;
  } else if (all_connected) {
    new_connection_state = cricket::kIceConnectionConnected;
  }
  if (ice_connection_state_ != new_connection_state) {
    ice_connection_state_ = new_connection_state;

    invoker_.AsyncInvoke<void>(
        RTC_FROM_HERE, signaling_thread_, [this, new_connection_state] {
          SignalIceConnectionState.Send(new_connection_state);
        });
  }

  // Compute the current RTCIceConnectionState as described in
  // https://www.w3.org/TR/webrtc/#dom-rtciceconnectionstate.
  // The PeerConnection is responsible for handling the "closed" state.
  int total_ice_checking = ice_state_counts[IceTransportState::kChecking];
  int total_ice_connected = ice_state_counts[IceTransportState::kConnected];
  int total_ice_completed = ice_state_counts[IceTransportState::kCompleted];
  int total_ice_failed = ice_state_counts[IceTransportState::kFailed];
  int total_ice_disconnected =
      ice_state_counts[IceTransportState::kDisconnected];
  int total_ice_closed = ice_state_counts[IceTransportState::kClosed];
  int total_ice_new = ice_state_counts[IceTransportState::kNew];
  int total_ice = dtls_transports.size();

  if (total_ice_failed > 0) {
    // Any RTCIceTransports are in the "failed" state.
    new_ice_connection_state = PeerConnectionInterface::kIceConnectionFailed;
  } else if (total_ice_disconnected > 0) {
    // None of the previous states apply and any RTCIceTransports are in the
    // "disconnected" state.
    new_ice_connection_state =
        PeerConnectionInterface::kIceConnectionDisconnected;
  } else if (total_ice_new + total_ice_closed == total_ice) {
    // None of the previous states apply and all RTCIceTransports are in the
    // "new" or "closed" state, or there are no transports.
    new_ice_connection_state = PeerConnectionInterface::kIceConnectionNew;
  } else if (total_ice_new + total_ice_checking > 0) {
    // None of the previous states apply and any RTCIceTransports are in the
    // "new" or "checking" state.
    new_ice_connection_state = PeerConnectionInterface::kIceConnectionChecking;
  } else if (total_ice_completed + total_ice_closed == total_ice ||
             all_completed) {
    // None of the previous states apply and all RTCIceTransports are in the
    // "completed" or "closed" state.
    //
    // TODO(https://bugs.webrtc.org/10356): The all_completed condition is added
    // to mimic the behavior of the old ICE connection state, and should be
    // removed once we get end-of-candidates signaling in place.
    new_ice_connection_state = PeerConnectionInterface::kIceConnectionCompleted;
  } else if (total_ice_connected + total_ice_completed + total_ice_closed ==
             total_ice) {
    // None of the previous states apply and all RTCIceTransports are in the
    // "connected", "completed" or "closed" state.
    new_ice_connection_state = PeerConnectionInterface::kIceConnectionConnected;
  } else {
    RTC_NOTREACHED();
  }

  if (standardized_ice_connection_state_ != new_ice_connection_state) {
    if (standardized_ice_connection_state_ ==
            PeerConnectionInterface::kIceConnectionChecking &&
        new_ice_connection_state ==
            PeerConnectionInterface::kIceConnectionCompleted) {
      // Ensure that we never skip over the "connected" state.
      invoker_.AsyncInvoke<void>(RTC_FROM_HERE, signaling_thread_, [this] {
        SignalStandardizedIceConnectionState(
            PeerConnectionInterface::kIceConnectionConnected);
      });
    }
    standardized_ice_connection_state_ = new_ice_connection_state;
    invoker_.AsyncInvoke<void>(
        RTC_FROM_HERE, signaling_thread_, [this, new_ice_connection_state] {
          SignalStandardizedIceConnectionState(new_ice_connection_state);
        });
  }

  // Compute the current RTCPeerConnectionState as described in
  // https://www.w3.org/TR/webrtc/#dom-rtcpeerconnectionstate.
  // The PeerConnection is responsible for handling the "closed" state.
  // Note that "connecting" is only a valid state for DTLS transports while
  // "checking", "completed" and "disconnected" are only valid for ICE
  // transports.
  int total_connected = total_ice_connected +
                        dtls_state_counts[cricket::DTLS_TRANSPORT_CONNECTED];
  int total_dtls_connecting =
      dtls_state_counts[cricket::DTLS_TRANSPORT_CONNECTING];
  int total_failed =
      total_ice_failed + dtls_state_counts[cricket::DTLS_TRANSPORT_FAILED];
  int total_closed =
      total_ice_closed + dtls_state_counts[cricket::DTLS_TRANSPORT_CLOSED];
  int total_new =
      total_ice_new + dtls_state_counts[cricket::DTLS_TRANSPORT_NEW];
  int total_transports = total_ice * 2;

  if (total_failed > 0) {
    // Any of the RTCIceTransports or RTCDtlsTransports are in a "failed" state.
    new_combined_state = PeerConnectionInterface::PeerConnectionState::kFailed;
  } else if (total_ice_disconnected > 0) {
    // None of the previous states apply and any RTCIceTransports or
    // RTCDtlsTransports are in the "disconnected" state.
    new_combined_state =
        PeerConnectionInterface::PeerConnectionState::kDisconnected;
  } else if (total_new + total_closed == total_transports) {
    // None of the previous states apply and all RTCIceTransports and
    // RTCDtlsTransports are in the "new" or "closed" state, or there are no
    // transports.
    new_combined_state = PeerConnectionInterface::PeerConnectionState::kNew;
  } else if (total_new + total_dtls_connecting + total_ice_checking > 0) {
    // None of the previous states apply and all RTCIceTransports or
    // RTCDtlsTransports are in the "new", "connecting" or "checking" state.
    new_combined_state =
        PeerConnectionInterface::PeerConnectionState::kConnecting;
  } else if (total_connected + total_ice_completed + total_closed ==
             total_transports) {
    // None of the previous states apply and all RTCIceTransports and
    // RTCDtlsTransports are in the "connected", "completed" or "closed" state.
    new_combined_state =
        PeerConnectionInterface::PeerConnectionState::kConnected;
  } else {
    RTC_NOTREACHED();
  }

  if (combined_connection_state_ != new_combined_state) {
    combined_connection_state_ = new_combined_state;
    invoker_.AsyncInvoke<void>(RTC_FROM_HERE, signaling_thread_,
                               [this, new_combined_state] {
                                 SignalConnectionState(new_combined_state);
                               });
  }

  // Compute the gathering state.
  if (dtls_transports.empty()) {
    new_gathering_state = cricket::kIceGatheringNew;
  } else if (all_done_gathering) {
    new_gathering_state = cricket::kIceGatheringComplete;
  } else if (any_gathering) {
    new_gathering_state = cricket::kIceGatheringGathering;
  }
  if (ice_gathering_state_ != new_gathering_state) {
    ice_gathering_state_ = new_gathering_state;
    invoker_.AsyncInvoke<void>(RTC_FROM_HERE, signaling_thread_,
                               [this, new_gathering_state] {
                                 SignalIceGatheringState(new_gathering_state);
                               });
  }
}

void JsepTransportController::ErrorDemuxingPacket_n(uint32_t ssrc) {
  SignalErrorDemuxingPacket.emit(ssrc);
}

void JsepTransportController::OnRtcpPacketReceived_n(
    rtc::CopyOnWriteBuffer* packet,
    int64_t packet_time_us) {
  RTC_DCHECK(config_.rtcp_handler);
  config_.rtcp_handler(*packet, packet_time_us);
}

void JsepTransportController::OnDtlsHandshakeError(
    rtc::SSLHandshakeError error) {
  SignalDtlsHandshakeError(error);
}

}  // namespace webrtc
