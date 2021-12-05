/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/transport_description_factory.h"

#include <stddef.h>

#include <memory>
#include <string>

#include "p2p/base/transport_description.h"
#include "rtc_base/logging.h"
#include "rtc_base/ssl_fingerprint.h"

namespace cricket {

TransportDescriptionFactory::TransportDescriptionFactory()
    : secure_(SEC_DISABLED) {}

TransportDescriptionFactory::~TransportDescriptionFactory() = default;

std::unique_ptr<TransportDescription> TransportDescriptionFactory::CreateOffer(
    const TransportOptions& options,
    const TransportDescription* current_description,
    IceCredentialsIterator* ice_credentials) const {
  auto desc = std::make_unique<TransportDescription>();

  // Generate the ICE credentials if we don't already have them.
  if (!current_description || options.ice_restart) {
    IceParameters credentials = ice_credentials->GetIceCredentials();
    desc->ice_ufrag = credentials.ufrag;
    desc->ice_pwd = credentials.pwd;
  } else {
    desc->ice_ufrag = current_description->ice_ufrag;
    desc->ice_pwd = current_description->ice_pwd;
  }
  desc->AddOption(ICE_OPTION_TRICKLE);
  if (options.enable_ice_renomination) {
    desc->AddOption(ICE_OPTION_RENOMINATION);
  }

  // If we are trying to establish a secure transport, add a fingerprint.
  if (secure_ == SEC_ENABLED || secure_ == SEC_REQUIRED) {
    // Fail if we can't create the fingerprint.
    // If we are the initiator set role to "actpass".
    if (!SetSecurityInfo(desc.get(), CONNECTIONROLE_ACTPASS)) {
      return NULL;
    }
  }

  return desc;
}

std::unique_ptr<TransportDescription> TransportDescriptionFactory::CreateAnswer(
    const TransportDescription* offer,
    const TransportOptions& options,
    bool require_transport_attributes,
    const TransportDescription* current_description,
    IceCredentialsIterator* ice_credentials) const {
  // TODO(juberti): Figure out why we get NULL offers, and fix this upstream.
  if (!offer) {
    RTC_LOG(LS_WARNING) << "Failed to create TransportDescription answer "
                           "because offer is NULL";
    return NULL;
  }

  auto desc = std::make_unique<TransportDescription>();
  // Generate the ICE credentials if we don't already have them or ice is
  // being restarted.
  if (!current_description || options.ice_restart) {
    IceParameters credentials = ice_credentials->GetIceCredentials();
    desc->ice_ufrag = credentials.ufrag;
    desc->ice_pwd = credentials.pwd;
  } else {
    desc->ice_ufrag = current_description->ice_ufrag;
    desc->ice_pwd = current_description->ice_pwd;
  }
  desc->AddOption(ICE_OPTION_TRICKLE);
  if (options.enable_ice_renomination) {
    desc->AddOption(ICE_OPTION_RENOMINATION);
  }

  // Negotiate security params.
  if (offer && offer->identity_fingerprint.get()) {
    // The offer supports DTLS, so answer with DTLS, as long as we support it.
    if (secure_ == SEC_ENABLED || secure_ == SEC_REQUIRED) {
      // Fail if we can't create the fingerprint.
      // Setting DTLS role to active.
      ConnectionRole role = (options.prefer_passive_role)
                                ? CONNECTIONROLE_PASSIVE
                                : CONNECTIONROLE_ACTIVE;

      if (!SetSecurityInfo(desc.get(), role)) {
        return NULL;
      }
    }
  } else if (require_transport_attributes && secure_ == SEC_REQUIRED) {
    // We require DTLS, but the other side didn't offer it. Fail.
    RTC_LOG(LS_WARNING) << "Failed to create TransportDescription answer "
                           "because of incompatible security settings";
    return NULL;
  }

  return desc;
}

bool TransportDescriptionFactory::SetSecurityInfo(TransportDescription* desc,
                                                  ConnectionRole role) const {
  if (!certificate_) {
    RTC_LOG(LS_ERROR) << "Cannot create identity digest with no certificate";
    return false;
  }

  // This digest algorithm is used to produce the a=fingerprint lines in SDP.
  // RFC 4572 Section 5 requires that those lines use the same hash function as
  // the certificate's signature, which is what CreateFromCertificate does.
  desc->identity_fingerprint =
      rtc::SSLFingerprint::CreateFromCertificate(*certificate_);
  if (!desc->identity_fingerprint) {
    return false;
  }

  // Assign security role.
  desc->connection_role = role;
  return true;
}

}  // namespace cricket
