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

#include <queue>
#include <string>
#include <type_traits>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/types/optional.h"
#include "api/jsep.h"
#include "api/jsep_session_description.h"
#include "api/rtc_error.h"
#include "api/sequence_checker.h"
#include "pc/connection_context.h"
#include "pc/sdp_state_provider.h"
#include "pc/session_description.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/ssl_identity.h"
#include "rtc_base/ssl_stream_adapter.h"
#include "rtc_base/string_encode.h"
#include "rtc_base/unique_id_generator.h"

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
}  // namespace

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
    ConnectionContext* context,
    const SdpStateProvider* sdp_info,
    const std::string& session_id,
    bool dtls_enabled,
    std::unique_ptr<rtc::RTCCertificateGeneratorInterface> cert_generator,
    rtc::scoped_refptr<rtc::RTCCertificate> certificate,
    std::function<void(const rtc::scoped_refptr<rtc::RTCCertificate>&)>
        on_certificate_ready,
    const FieldTrialsView& field_trials)
    : signaling_thread_(context->signaling_thread()),
      transport_desc_factory_(field_trials),
      session_desc_factory_(context->media_engine(),
                            context->use_rtx(),
                            context->ssrc_generator(),
                            &transport_desc_factory_),
      // RFC 4566 suggested a Network Time Protocol (NTP) format timestamp
      // as the session id and session version. To simplify, it should be fine
      // to just use a random number as session id and start version from
      // `kInitSessionVersion`.
      session_version_(kInitSessionVersion),
      cert_generator_(dtls_enabled ? std::move(cert_generator) : nullptr),
      sdp_info_(sdp_info),
      session_id_(session_id),
      certificate_request_state_(CERTIFICATE_NOT_NEEDED),
      on_certificate_ready_(on_certificate_ready) {
  RTC_DCHECK(signaling_thread_);

  if (!dtls_enabled) {
    RTC_LOG(LS_INFO) << "DTLS-SRTP disabled";
    transport_desc_factory_.SetInsecureForTesting();
    return;
  }
  if (certificate) {
    // Use `certificate`.
    certificate_request_state_ = CERTIFICATE_WAITING;

    RTC_LOG(LS_VERBOSE) << "DTLS-SRTP enabled; has certificate parameter.";
    RTC_LOG(LS_INFO) << "Using certificate supplied to the constructor.";
    SetCertificate(certificate);
    return;
  }
  // Generate certificate.
  RTC_DCHECK(cert_generator_);
  certificate_request_state_ = CERTIFICATE_WAITING;

  auto callback = [weak_ptr = weak_factory_.GetWeakPtr()](
                      rtc::scoped_refptr<rtc::RTCCertificate> certificate) {
    if (!weak_ptr) {
      return;
    }
    if (certificate) {
      weak_ptr->SetCertificate(std::move(certificate));
    } else {
      weak_ptr->OnCertificateRequestFailed();
    }
  };

  rtc::KeyParams key_params = rtc::KeyParams();
  RTC_LOG(LS_VERBOSE)
      << "DTLS-SRTP enabled; sending DTLS identity request (key type: "
      << key_params.type() << ").";

  // Request certificate. This happens asynchronously on a different thread.
  cert_generator_->GenerateCertificateAsync(key_params, absl::nullopt,
                                            std::move(callback));
}

WebRtcSessionDescriptionFactory::~WebRtcSessionDescriptionFactory() {
  RTC_DCHECK_RUN_ON(signaling_thread_);

  // Fail any requests that were asked for before identity generation completed.
  FailPendingRequests(kFailedDueToSessionShutdown);

  // Process all pending notifications. If we don't do this, requests will
  // linger and not know they succeeded or failed.
  // All tasks that suppose to run them are protected with weak_factory_ and
  // will be cancelled. If we don't protect them, they might trigger after peer
  // connection is destroyed, which might be surprising.
  while (!callbacks_.empty()) {
    std::move(callbacks_.front())();
    callbacks_.pop();
  }
}

void WebRtcSessionDescriptionFactory::CreateOffer(
    CreateSessionDescriptionObserver* observer,
    const PeerConnectionInterface::RTCOfferAnswerOptions& options,
    const cricket::MediaSessionOptions& session_options) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  std::string error = "CreateOffer";
  if (certificate_request_state_ == CERTIFICATE_FAILED) {
    error += kFailedDueToIdentityFailed;
    PostCreateSessionDescriptionFailed(
        observer, RTCError(RTCErrorType::INTERNAL_ERROR, std::move(error)));
    return;
  }

  if (!ValidMediaSessionOptions(session_options)) {
    error += " called with invalid session options";
    PostCreateSessionDescriptionFailed(
        observer, RTCError(RTCErrorType::INTERNAL_ERROR, std::move(error)));
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
    PostCreateSessionDescriptionFailed(
        observer, RTCError(RTCErrorType::INTERNAL_ERROR, std::move(error)));
    return;
  }
  if (!sdp_info_->remote_description()) {
    error += " can't be called before SetRemoteDescription.";
    PostCreateSessionDescriptionFailed(
        observer, RTCError(RTCErrorType::INTERNAL_ERROR, std::move(error)));
    return;
  }
  if (sdp_info_->remote_description()->GetType() != SdpType::kOffer) {
    error += " failed because remote_description is not an offer.";
    PostCreateSessionDescriptionFailed(
        observer, RTCError(RTCErrorType::INTERNAL_ERROR, std::move(error)));
    return;
  }

  if (!ValidMediaSessionOptions(session_options)) {
    error += " called with invalid session options.";
    PostCreateSessionDescriptionFailed(
        observer, RTCError(RTCErrorType::INTERNAL_ERROR, std::move(error)));
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

  auto result = session_desc_factory_.CreateOfferOrError(
      request.options, sdp_info_->local_description()
                           ? sdp_info_->local_description()->description()
                           : nullptr);
  if (!result.ok()) {
    PostCreateSessionDescriptionFailed(request.observer.get(), result.error());
    return;
  }
  std::unique_ptr<cricket::SessionDescription> desc = std::move(result.value());
  RTC_CHECK(desc);

  // RFC 3264
  // When issuing an offer that modifies the session,
  // the "o=" line of the new SDP MUST be identical to that in the
  // previous SDP, except that the version in the origin field MUST
  // increment by one from the previous SDP.

  // Just increase the version number by one each time when a new offer
  // is created regardless if it's identical to the previous one or not.
  // The `session_version_` is a uint64_t, the wrap around should not happen.
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
  PostCreateSessionDescriptionSucceeded(request.observer.get(),
                                        std::move(offer));
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

  auto result = session_desc_factory_.CreateAnswerOrError(
      sdp_info_->remote_description()
          ? sdp_info_->remote_description()->description()
          : nullptr,
      request.options,
      sdp_info_->local_description()
          ? sdp_info_->local_description()->description()
          : nullptr);
  if (!result.ok()) {
    PostCreateSessionDescriptionFailed(request.observer.get(), result.error());
    return;
  }
  std::unique_ptr<cricket::SessionDescription> desc = std::move(result.value());
  RTC_CHECK(desc);

  // RFC 3264
  // If the answer is different from the offer in any way (different IP
  // addresses, ports, etc.), the origin line MUST be different in the answer.
  // In that case, the version number in the "o=" line of the answer is
  // unrelated to the version number in the o line of the offer.
  // Get a new version number by increasing the `session_version_answer_`.
  // The `session_version_` is a uint64_t, the wrap around should not happen.
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
  PostCreateSessionDescriptionSucceeded(request.observer.get(),
                                        std::move(answer));
}

void WebRtcSessionDescriptionFactory::FailPendingRequests(
    const std::string& reason) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  while (!create_session_description_requests_.empty()) {
    const CreateSessionDescriptionRequest& request =
        create_session_description_requests_.front();
    PostCreateSessionDescriptionFailed(
        request.observer.get(),
        RTCError(RTCErrorType::INTERNAL_ERROR,
                 ((request.type == CreateSessionDescriptionRequest::kOffer)
                      ? "CreateOffer"
                      : "CreateAnswer") +
                     reason));
    create_session_description_requests_.pop();
  }
}

void WebRtcSessionDescriptionFactory::PostCreateSessionDescriptionFailed(
    CreateSessionDescriptionObserver* observer,
    RTCError error) {
  Post([observer =
            rtc::scoped_refptr<CreateSessionDescriptionObserver>(observer),
        error]() mutable { observer->OnFailure(error); });
  RTC_LOG(LS_ERROR) << "CreateSessionDescription failed: " << error.message();
}

void WebRtcSessionDescriptionFactory::PostCreateSessionDescriptionSucceeded(
    CreateSessionDescriptionObserver* observer,
    std::unique_ptr<SessionDescriptionInterface> description) {
  Post([observer =
            rtc::scoped_refptr<CreateSessionDescriptionObserver>(observer),
        description = std::move(description)]() mutable {
    observer->OnSuccess(description.release());
  });
}

void WebRtcSessionDescriptionFactory::Post(
    absl::AnyInvocable<void() &&> callback) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  callbacks_.push(std::move(callback));
  signaling_thread_->PostTask([weak_ptr = weak_factory_.GetWeakPtr()] {
    if (weak_ptr) {
      auto& callbacks = weak_ptr->callbacks_;
      // Callbacks are pushed from the same thread, thus this task should
      // corresond to the first entry in the queue.
      RTC_DCHECK(!callbacks.empty());
      std::move(callbacks.front())();
      callbacks.pop();
    }
  });
}

void WebRtcSessionDescriptionFactory::OnCertificateRequestFailed() {
  RTC_DCHECK_RUN_ON(signaling_thread_);

  RTC_LOG(LS_ERROR) << "Asynchronous certificate generation request failed.";
  certificate_request_state_ = CERTIFICATE_FAILED;

  FailPendingRequests(kFailedDueToIdentityFailed);
}

void WebRtcSessionDescriptionFactory::SetCertificate(
    rtc::scoped_refptr<rtc::RTCCertificate> certificate) {
  RTC_DCHECK(certificate);
  RTC_LOG(LS_VERBOSE) << "Setting new certificate.";

  certificate_request_state_ = CERTIFICATE_SUCCEEDED;

  on_certificate_ready_(certificate);

  transport_desc_factory_.set_certificate(std::move(certificate));

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
