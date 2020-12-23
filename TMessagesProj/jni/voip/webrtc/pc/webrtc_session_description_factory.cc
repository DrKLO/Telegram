/*
 *  Copyright 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/webrtc_session_description_factory.h"

#include <stddef.h>
#include <algorithm>
#include <memory>
#include <string>
#include <type_traits>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/types/optional.h"
#include "api/jsep.h"
#include "api/jsep_session_description.h"
#include "api/rtc_error.h"
#include "pc/sdp_state_provider.h"
#include "pc/session_description.h"
#include "rtc_base/checks.h"
#include "rtc_base/location.h"
#include "rtc_base/logging.h"
#include "rtc_base/ref_counted_object.h"
#include "rtc_base/ssl_identity.h"
#include "rtc_base/ssl_stream_adapter.h"
#include "rtc_base/string_encode.h"

using cricket::MediaSessionOptions;
using rtc::UniqueRandomIdGenerator;

namespace webrtc {
namespace {
static const char kFailedDueToIdentityFailed[] =
    " failed because DTLS identity request failed";
static const char kFailedDueToSessionShutdown[] =
    " failed because the session was shut down";

static const uint64_t kInitSessionVersion = 2;

// Check that each sender has a unique ID.
static bool ValidMediaSessionOptions(
    const cricket::MediaSessionOptions& session_options) {
  std::vector<cricket::SenderOptions> sorted_senders;
  for (const cricket::MediaDescriptionOptions& media_description_options :
       session_options.media_description_options) {
    sorted_senders.insert(sorted_senders.end(),
                          media_description_options.sender_options.begin(),
                          media_description_options.sender_options.end());
  }
  absl::c_sort(sorted_senders, [](const cricket::SenderOptions& sender1,
                                  const cricket::SenderOptions& sender2) {
    return sender1.track_id < sender2.track_id;
  });
  return absl::c_adjacent_find(sorted_senders,
                               [](const cricket::SenderOptions& sender1,
                                  const cricket::SenderOptions& sender2) {
                                 return sender1.track_id == sender2.track_id;
                               }) == sorted_senders.end();
}

enum {
  MSG_CREATE_SESSIONDESCRIPTION_SUCCESS,
  MSG_CREATE_SESSIONDESCRIPTION_FAILED,
  MSG_USE_CONSTRUCTOR_CERTIFICATE
};

struct CreateSessionDescriptionMsg : public rtc::MessageData {
  explicit CreateSessionDescriptionMsg(
      webrtc::CreateSessionDescriptionObserver* observer,
      RTCError error_in)
      : observer(observer), error(std::move(error_in)) {}

  rtc::scoped_refptr<webrtc::CreateSessionDescriptionObserver> observer;
  RTCError error;
  std::unique_ptr<webrtc::SessionDescriptionInterface> description;
};
}  // namespace

void WebRtcCertificateGeneratorCallback::OnFailure() {
  SignalRequestFailed();
}

void WebRtcCertificateGeneratorCallback::OnSuccess(
    const rtc::scoped_refptr<rtc::RTCCertificate>& certificate) {
  SignalCertificateReady(certificate);
}

// static
void WebRtcSessionDescriptionFactory::CopyCandidatesFromSessionDescription(
    const SessionDescriptionInterface* source_desc,
    const std::string& content_name,
    SessionDescriptionInterface* dest_desc) {
  if (!source_desc) {
    return;
  }
  const cricket::ContentInfos& contents =
      source_desc->description()->contents();
  const cricket::ContentInfo* cinfo =
      source_desc->description()->GetContentByName(content_name);
  if (!cinfo) {
    return;
  }
  size_t mediasection_index = static_cast<int>(cinfo - &contents[0]);
  const IceCandidateCollection* source_candidates =
      source_desc->candidates(mediasection_index);
  const IceCandidateCollection* dest_candidates =
      dest_desc->candidates(mediasection_index);
  if (!source_candidates || !dest_candidates) {
    return;
  }
  for (size_t n = 0; n < source_candidates->count(); ++n) {
    const IceCandidateInterface* new_candidate = source_candidates->at(n);
    if (!dest_candidates->HasCandidate(new_candidate)) {
      dest_desc->AddCandidate(source_candidates->at(n));
    }
  }
}

WebRtcSessionDescriptionFactory::WebRtcSessionDescriptionFactory(
    rtc::Thread* signaling_thread,
    cricket::ChannelManager* channel_manager,
    const SdpStateProvider* sdp_info,
    const std::string& session_id,
    bool dtls_enabled,
    std::unique_ptr<rtc::RTCCertificateGeneratorInterface> cert_generator,
    const rtc::scoped_refptr<rtc::RTCCertificate>& certificate,
    UniqueRandomIdGenerator* ssrc_generator,
    std::function<void(const rtc::scoped_refptr<rtc::RTCCertificate>&)>
        on_certificate_ready)
    : signaling_thread_(signaling_thread),
      session_desc_factory_(channel_manager,
                            &transport_desc_factory_,
                            ssrc_generator),
      // RFC 4566 suggested a Network Time Protocol (NTP) format timestamp
      // as the session id and session version. To simplify, it should be fine
      // to just use a random number as session id and start version from
      // |kInitSessionVersion|.
      session_version_(kInitSessionVersion),
      cert_generator_(dtls_enabled ? std::move(cert_generator) : nullptr),
      sdp_info_(sdp_info),
      session_id_(session_id),
      certificate_request_state_(CERTIFICATE_NOT_NEEDED),
      on_certificate_ready_(on_certificate_ready) {
  RTC_DCHECK(signaling_thread_);

  if (!dtls_enabled) {
    SetSdesPolicy(cricket::SEC_REQUIRED);
    RTC_LOG(LS_VERBOSE) << "DTLS-SRTP disabled.";
    return;
  }

  // SRTP-SDES is disabled if DTLS is on.
  SetSdesPolicy(cricket::SEC_DISABLED);
  if (certificate) {
    // Use |certificate|.
    certificate_request_state_ = CERTIFICATE_WAITING;

    RTC_LOG(LS_VERBOSE) << "DTLS-SRTP enabled; has certificate parameter.";
    // We already have a certificate but we wait to do |SetIdentity|; if we do
    // it in the constructor then the caller has not had a chance to connect to
    // |SignalCertificateReady|.
    signaling_thread_->Post(
        RTC_FROM_HERE, this, MSG_USE_CONSTRUCTOR_CERTIFICATE,
        new rtc::ScopedRefMessageData<rtc::RTCCertificate>(certificate));
  } else {
    // Generate certificate.
    certificate_request_state_ = CERTIFICATE_WAITING;

    rtc::scoped_refptr<WebRtcCertificateGeneratorCallback> callback(
        new rtc::RefCountedObject<WebRtcCertificateGeneratorCallback>());
    callback->SignalRequestFailed.connect(
        this, &WebRtcSessionDescriptionFactory::OnCertificateRequestFailed);
    callback->SignalCertificateReady.connect(
        this, &WebRtcSessionDescriptionFactory::SetCertificate);

    rtc::KeyParams key_params = rtc::KeyParams();
    RTC_LOG(LS_VERBOSE)
        << "DTLS-SRTP enabled; sending DTLS identity request (key type: "
        << key_params.type() << ").";

    // Request certificate. This happens asynchronously, so that the caller gets
    // a chance to connect to |SignalCertificateReady|.
    cert_generator_->GenerateCertificateAsync(key_params, absl::nullopt,
                                              callback);
  }
}

WebRtcSessionDescriptionFactory::~WebRtcSessionDescriptionFactory() {
  RTC_DCHECK(signaling_thread_->IsCurrent());

  // Fail any requests that were asked for before identity generation completed.
  FailPendingRequests(kFailedDueToSessionShutdown);

  // Process all pending notifications in the message queue.  If we don't do
  // this, requests will linger and not know they succeeded or failed.
  rtc::MessageList list;
  signaling_thread_->Clear(this, rtc::MQID_ANY, &list);
  for (auto& msg : list) {
    if (msg.message_id != MSG_USE_CONSTRUCTOR_CERTIFICATE) {
      OnMessage(&msg);
    } else {
      // Skip MSG_USE_CONSTRUCTOR_CERTIFICATE because we don't want to trigger
      // SetIdentity-related callbacks in the destructor. This can be a problem
      // when WebRtcSession listens to the callback but it was the WebRtcSession
      // destructor that caused WebRtcSessionDescriptionFactory's destruction.
      // The callback is then ignored, leaking memory allocated by OnMessage for
      // MSG_USE_CONSTRUCTOR_CERTIFICATE.
      delete msg.pdata;
    }
  }
}

void WebRtcSessionDescriptionFactory::CreateOffer(
    CreateSessionDescriptionObserver* observer,
    const PeerConnectionInterface::RTCOfferAnswerOptions& options,
    const cricket::MediaSessionOptions& session_options) {
  std::string error = "CreateOffer";
  if (certificate_request_state_ == CERTIFICATE_FAILED) {
    error += kFailedDueToIdentityFailed;
    RTC_LOG(LS_ERROR) << error;
    PostCreateSessionDescriptionFailed(observer, error);
    return;
  }

  if (!ValidMediaSessionOptions(session_options)) {
    error += " called with invalid session options";
    RTC_LOG(LS_ERROR) << error;
    PostCreateSessionDescriptionFailed(observer, error);
    return;
  }

  CreateSessionDescriptionRequest request(
      CreateSessionDescriptionRequest::kOffer, observer, session_options);
  if (certificate_request_state_ == CERTIFICATE_WAITING) {
    create_session_description_requests_.push(request);
  } else {
    RTC_DCHECK(certificate_request_state_ == CERTIFICATE_SUCCEEDED ||
               certificate_request_state_ == CERTIFICATE_NOT_NEEDED);
    InternalCreateOffer(request);
  }
}

void WebRtcSessionDescriptionFactory::CreateAnswer(
    CreateSessionDescriptionObserver* observer,
    const cricket::MediaSessionOptions& session_options) {
  std::string error = "CreateAnswer";
  if (certificate_request_state_ == CERTIFICATE_FAILED) {
    error += kFailedDueToIdentityFailed;
    RTC_LOG(LS_ERROR) << error;
    PostCreateSessionDescriptionFailed(observer, error);
    return;
  }
  if (!sdp_info_->remote_description()) {
    error += " can't be called before SetRemoteDescription.";
    RTC_LOG(LS_ERROR) << error;
    PostCreateSessionDescriptionFailed(observer, error);
    return;
  }
  if (sdp_info_->remote_description()->GetType() != SdpType::kOffer) {
    error += " failed because remote_description is not an offer.";
    RTC_LOG(LS_ERROR) << error;
    PostCreateSessionDescriptionFailed(observer, error);
    return;
  }

  if (!ValidMediaSessionOptions(session_options)) {
    error += " called with invalid session options.";
    RTC_LOG(LS_ERROR) << error;
    PostCreateSessionDescriptionFailed(observer, error);
    return;
  }

  CreateSessionDescriptionRequest request(
      CreateSessionDescriptionRequest::kAnswer, observer, session_options);
  if (certificate_request_state_ == CERTIFICATE_WAITING) {
    create_session_description_requests_.push(request);
  } else {
    RTC_DCHECK(certificate_request_state_ == CERTIFICATE_SUCCEEDED ||
               certificate_request_state_ == CERTIFICATE_NOT_NEEDED);
    InternalCreateAnswer(request);
  }
}

void WebRtcSessionDescriptionFactory::SetSdesPolicy(
    cricket::SecurePolicy secure_policy) {
  session_desc_factory_.set_secure(secure_policy);
}

cricket::SecurePolicy WebRtcSessionDescriptionFactory::SdesPolicy() const {
  return session_desc_factory_.secure();
}

void WebRtcSessionDescriptionFactory::OnMessage(rtc::Message* msg) {
  switch (msg->message_id) {
    case MSG_CREATE_SESSIONDESCRIPTION_SUCCESS: {
      CreateSessionDescriptionMsg* param =
          static_cast<CreateSessionDescriptionMsg*>(msg->pdata);
      param->observer->OnSuccess(param->description.release());
      delete param;
      break;
    }
    case MSG_CREATE_SESSIONDESCRIPTION_FAILED: {
      CreateSessionDescriptionMsg* param =
          static_cast<CreateSessionDescriptionMsg*>(msg->pdata);
      param->observer->OnFailure(std::move(param->error));
      delete param;
      break;
    }
    case MSG_USE_CONSTRUCTOR_CERTIFICATE: {
      rtc::ScopedRefMessageData<rtc::RTCCertificate>* param =
          static_cast<rtc::ScopedRefMessageData<rtc::RTCCertificate>*>(
              msg->pdata);
      RTC_LOG(LS_INFO) << "Using certificate supplied to the constructor.";
      SetCertificate(param->data());
      delete param;
      break;
    }
    default:
      RTC_NOTREACHED();
      break;
  }
}

void WebRtcSessionDescriptionFactory::InternalCreateOffer(
    CreateSessionDescriptionRequest request) {
  if (sdp_info_->local_description()) {
    // If the needs-ice-restart flag is set as described by JSEP, we should
    // generate an offer with a new ufrag/password to trigger an ICE restart.
    for (cricket::MediaDescriptionOptions& options :
         request.options.media_description_options) {
      if (sdp_info_->NeedsIceRestart(options.mid)) {
        options.transport_options.ice_restart = true;
      }
    }
  }

  std::unique_ptr<cricket::SessionDescription> desc =
      session_desc_factory_.CreateOffer(
          request.options, sdp_info_->local_description()
                               ? sdp_info_->local_description()->description()
                               : nullptr);
  if (!desc) {
    PostCreateSessionDescriptionFailed(request.observer,
                                       "Failed to initialize the offer.");
    return;
  }

  // RFC 3264
  // When issuing an offer that modifies the session,
  // the "o=" line of the new SDP MUST be identical to that in the
  // previous SDP, except that the version in the origin field MUST
  // increment by one from the previous SDP.

  // Just increase the version number by one each time when a new offer
  // is created regardless if it's identical to the previous one or not.
  // The |session_version_| is a uint64_t, the wrap around should not happen.
  RTC_DCHECK(session_version_ + 1 > session_version_);
  auto offer = std::make_unique<JsepSessionDescription>(
      SdpType::kOffer, std::move(desc), session_id_,
      rtc::ToString(session_version_++));
  if (sdp_info_->local_description()) {
    for (const cricket::MediaDescriptionOptions& options :
         request.options.media_description_options) {
      if (!options.transport_options.ice_restart) {
        CopyCandidatesFromSessionDescription(sdp_info_->local_description(),
                                             options.mid, offer.get());
      }
    }
  }
  PostCreateSessionDescriptionSucceeded(request.observer, std::move(offer));
}

void WebRtcSessionDescriptionFactory::InternalCreateAnswer(
    CreateSessionDescriptionRequest request) {
  if (sdp_info_->remote_description()) {
    for (cricket::MediaDescriptionOptions& options :
         request.options.media_description_options) {
      // According to http://tools.ietf.org/html/rfc5245#section-9.2.1.1
      // an answer should also contain new ICE ufrag and password if an offer
      // has been received with new ufrag and password.
      options.transport_options.ice_restart =
          sdp_info_->IceRestartPending(options.mid);
      // We should pass the current DTLS role to the transport description
      // factory, if there is already an existing ongoing session.
      absl::optional<rtc::SSLRole> dtls_role =
          sdp_info_->GetDtlsRole(options.mid);
      if (dtls_role) {
        options.transport_options.prefer_passive_role =
            (rtc::SSL_SERVER == *dtls_role);
      }
    }
  }

  std::unique_ptr<cricket::SessionDescription> desc =
      session_desc_factory_.CreateAnswer(
          sdp_info_->remote_description()
              ? sdp_info_->remote_description()->description()
              : nullptr,
          request.options,
          sdp_info_->local_description()
              ? sdp_info_->local_description()->description()
              : nullptr);
  if (!desc) {
    PostCreateSessionDescriptionFailed(request.observer,
                                       "Failed to initialize the answer.");
    return;
  }

  // RFC 3264
  // If the answer is different from the offer in any way (different IP
  // addresses, ports, etc.), the origin line MUST be different in the answer.
  // In that case, the version number in the "o=" line of the answer is
  // unrelated to the version number in the o line of the offer.
  // Get a new version number by increasing the |session_version_answer_|.
  // The |session_version_| is a uint64_t, the wrap around should not happen.
  RTC_DCHECK(session_version_ + 1 > session_version_);
  auto answer = std::make_unique<JsepSessionDescription>(
      SdpType::kAnswer, std::move(desc), session_id_,
      rtc::ToString(session_version_++));
  if (sdp_info_->local_description()) {
    // Include all local ICE candidates in the SessionDescription unless
    // the remote peer has requested an ICE restart.
    for (const cricket::MediaDescriptionOptions& options :
         request.options.media_description_options) {
      if (!options.transport_options.ice_restart) {
        CopyCandidatesFromSessionDescription(sdp_info_->local_description(),
                                             options.mid, answer.get());
      }
    }
  }
  PostCreateSessionDescriptionSucceeded(request.observer, std::move(answer));
}

void WebRtcSessionDescriptionFactory::FailPendingRequests(
    const std::string& reason) {
  RTC_DCHECK(signaling_thread_->IsCurrent());
  while (!create_session_description_requests_.empty()) {
    const CreateSessionDescriptionRequest& request =
        create_session_description_requests_.front();
    PostCreateSessionDescriptionFailed(
        request.observer,
        ((request.type == CreateSessionDescriptionRequest::kOffer)
             ? "CreateOffer"
             : "CreateAnswer") +
            reason);
    create_session_description_requests_.pop();
  }
}

void WebRtcSessionDescriptionFactory::PostCreateSessionDescriptionFailed(
    CreateSessionDescriptionObserver* observer,
    const std::string& error) {
  CreateSessionDescriptionMsg* msg = new CreateSessionDescriptionMsg(
      observer, RTCError(RTCErrorType::INTERNAL_ERROR, std::string(error)));
  signaling_thread_->Post(RTC_FROM_HERE, this,
                          MSG_CREATE_SESSIONDESCRIPTION_FAILED, msg);
  RTC_LOG(LS_ERROR) << "Create SDP failed: " << error;
}

void WebRtcSessionDescriptionFactory::PostCreateSessionDescriptionSucceeded(
    CreateSessionDescriptionObserver* observer,
    std::unique_ptr<SessionDescriptionInterface> description) {
  CreateSessionDescriptionMsg* msg =
      new CreateSessionDescriptionMsg(observer, RTCError::OK());
  msg->description = std::move(description);
  signaling_thread_->Post(RTC_FROM_HERE, this,
                          MSG_CREATE_SESSIONDESCRIPTION_SUCCESS, msg);
}

void WebRtcSessionDescriptionFactory::OnCertificateRequestFailed() {
  RTC_DCHECK(signaling_thread_->IsCurrent());

  RTC_LOG(LS_ERROR) << "Asynchronous certificate generation request failed.";
  certificate_request_state_ = CERTIFICATE_FAILED;

  FailPendingRequests(kFailedDueToIdentityFailed);
}

void WebRtcSessionDescriptionFactory::SetCertificate(
    const rtc::scoped_refptr<rtc::RTCCertificate>& certificate) {
  RTC_DCHECK(certificate);
  RTC_LOG(LS_VERBOSE) << "Setting new certificate.";

  certificate_request_state_ = CERTIFICATE_SUCCEEDED;

  on_certificate_ready_(certificate);

  transport_desc_factory_.set_certificate(certificate);
  transport_desc_factory_.set_secure(cricket::SEC_ENABLED);

  while (!create_session_description_requests_.empty()) {
    if (create_session_description_requests_.front().type ==
        CreateSessionDescriptionRequest::kOffer) {
      InternalCreateOffer(create_session_description_requests_.front());
    } else {
      InternalCreateAnswer(create_session_description_requests_.front());
    }
    create_session_description_requests_.pop();
  }
}
}  // namespace webrtc
