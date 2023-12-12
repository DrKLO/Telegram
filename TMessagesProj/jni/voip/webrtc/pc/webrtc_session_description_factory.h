/*
 *  Copyright 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_WEBRTC_SESSION_DESCRIPTION_FACTORY_H_
#define PC_WEBRTC_SESSION_DESCRIPTION_FACTORY_H_

#include <stdint.h>

#include <functional>
#include <memory>
#include <queue>
#include <string>

#include "absl/functional/any_invocable.h"
#include "api/jsep.h"
#include "api/peer_connection_interface.h"
#include "api/scoped_refptr.h"
#include "api/task_queue/task_queue_base.h"
#include "p2p/base/transport_description.h"
#include "p2p/base/transport_description_factory.h"
#include "pc/media_session.h"
#include "pc/sdp_state_provider.h"
#include "rtc_base/rtc_certificate.h"
#include "rtc_base/rtc_certificate_generator.h"
#include "rtc_base/unique_id_generator.h"
#include "rtc_base/weak_ptr.h"

namespace webrtc {
// This class is used to create offer/answer session description. Certificates
// for WebRtcSession/DTLS are either supplied at construction or generated
// asynchronously. It queues the create offer/answer request until the
// certificate generation has completed, i.e. when OnCertificateRequestFailed or
// OnCertificateReady is called.
class WebRtcSessionDescriptionFactory {
 public:
  // Can specify either a `cert_generator` or `certificate` to enable DTLS. If
  // a certificate generator is given, starts generating the certificate
  // asynchronously. If a certificate is given, will use that for identifying
  // over DTLS. If neither is specified, DTLS is disabled.
  WebRtcSessionDescriptionFactory(
      ConnectionContext* context,
      const SdpStateProvider* sdp_info,
      const std::string& session_id,
      bool dtls_enabled,
      std::unique_ptr<rtc::RTCCertificateGeneratorInterface> cert_generator,
      rtc::scoped_refptr<rtc::RTCCertificate> certificate,
      std::function<void(const rtc::scoped_refptr<rtc::RTCCertificate>&)>
          on_certificate_ready,
      const FieldTrialsView& field_trials);
  ~WebRtcSessionDescriptionFactory();

  WebRtcSessionDescriptionFactory(const WebRtcSessionDescriptionFactory&) =
      delete;
  WebRtcSessionDescriptionFactory& operator=(
      const WebRtcSessionDescriptionFactory&) = delete;

  static void CopyCandidatesFromSessionDescription(
      const SessionDescriptionInterface* source_desc,
      const std::string& content_name,
      SessionDescriptionInterface* dest_desc);

  void CreateOffer(
      CreateSessionDescriptionObserver* observer,
      const PeerConnectionInterface::RTCOfferAnswerOptions& options,
      const cricket::MediaSessionOptions& session_options);
  void CreateAnswer(CreateSessionDescriptionObserver* observer,
                    const cricket::MediaSessionOptions& session_options);

  void SetSdesPolicy(cricket::SecurePolicy secure_policy);
  cricket::SecurePolicy SdesPolicy() const;

  void set_enable_encrypted_rtp_header_extensions(bool enable) {
    session_desc_factory_.set_enable_encrypted_rtp_header_extensions(enable);
  }

  void set_is_unified_plan(bool is_unified_plan) {
    session_desc_factory_.set_is_unified_plan(is_unified_plan);
  }

  // For testing.
  bool waiting_for_certificate_for_testing() const {
    return certificate_request_state_ == CERTIFICATE_WAITING;
  }

 private:
  enum CertificateRequestState {
    CERTIFICATE_NOT_NEEDED,
    CERTIFICATE_WAITING,
    CERTIFICATE_SUCCEEDED,
    CERTIFICATE_FAILED,
  };

  struct CreateSessionDescriptionRequest {
    enum Type {
      kOffer,
      kAnswer,
    };

    CreateSessionDescriptionRequest(Type type,
                                    CreateSessionDescriptionObserver* observer,
                                    const cricket::MediaSessionOptions& options)
        : type(type), observer(observer), options(options) {}

    Type type;
    rtc::scoped_refptr<CreateSessionDescriptionObserver> observer;
    cricket::MediaSessionOptions options;
  };

  void InternalCreateOffer(CreateSessionDescriptionRequest request);
  void InternalCreateAnswer(CreateSessionDescriptionRequest request);
  // Posts failure notifications for all pending session description requests.
  void FailPendingRequests(const std::string& reason);
  void PostCreateSessionDescriptionFailed(
      CreateSessionDescriptionObserver* observer,
      const std::string& error);
  void PostCreateSessionDescriptionSucceeded(
      CreateSessionDescriptionObserver* observer,
      std::unique_ptr<SessionDescriptionInterface> description);
  // Posts `callback` to `signaling_thread_`, and ensures it will be called no
  // later than in the destructor.
  void Post(absl::AnyInvocable<void() &&> callback);

  void OnCertificateRequestFailed();
  void SetCertificate(rtc::scoped_refptr<rtc::RTCCertificate> certificate);

  std::queue<CreateSessionDescriptionRequest>
      create_session_description_requests_;
  TaskQueueBase* const signaling_thread_;
  cricket::TransportDescriptionFactory transport_desc_factory_;
  cricket::MediaSessionDescriptionFactory session_desc_factory_;
  uint64_t session_version_;
  const std::unique_ptr<rtc::RTCCertificateGeneratorInterface> cert_generator_;
  const SdpStateProvider* sdp_info_;
  const std::string session_id_;
  CertificateRequestState certificate_request_state_;
  std::queue<absl::AnyInvocable<void() &&>> callbacks_;

  std::function<void(const rtc::scoped_refptr<rtc::RTCCertificate>&)>
      on_certificate_ready_;
  rtc::WeakPtrFactory<WebRtcSessionDescriptionFactory> weak_factory_{this};
};
}  // namespace webrtc

#endif  // PC_WEBRTC_SESSION_DESCRIPTION_FACTORY_H_
