/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/peer_connection_interface.h"

#include <utility>

namespace webrtc {

PeerConnectionInterface::IceServer::IceServer() = default;
PeerConnectionInterface::IceServer::IceServer(const IceServer& rhs) = default;
PeerConnectionInterface::IceServer::~IceServer() = default;

PeerConnectionInterface::RTCConfiguration::RTCConfiguration() = default;

PeerConnectionInterface::RTCConfiguration::RTCConfiguration(
    const RTCConfiguration& rhs) = default;

PeerConnectionInterface::RTCConfiguration::RTCConfiguration(
    RTCConfigurationType type) {
  if (type == RTCConfigurationType::kAggressive) {
    // These parameters are also defined in Java and IOS configurations,
    // so their values may be overwritten by the Java or IOS configuration.
    bundle_policy = kBundlePolicyMaxBundle;
    rtcp_mux_policy = kRtcpMuxPolicyRequire;
    ice_connection_receiving_timeout = kAggressiveIceConnectionReceivingTimeout;

    // These parameters are not defined in Java or IOS configuration,
    // so their values will not be overwritten.
    enable_ice_renomination = true;
    redetermine_role_on_ice_restart = false;
  }
}

PeerConnectionInterface::RTCConfiguration::~RTCConfiguration() = default;

RTCError PeerConnectionInterface::SetConfiguration(
    const PeerConnectionInterface::RTCConfiguration& config) {
  return RTCError();
}

PeerConnectionDependencies::PeerConnectionDependencies(
    PeerConnectionObserver* observer_in)
    : observer(observer_in) {}

PeerConnectionDependencies::PeerConnectionDependencies(
    PeerConnectionDependencies&&) = default;

PeerConnectionDependencies::~PeerConnectionDependencies() = default;

PeerConnectionFactoryDependencies::PeerConnectionFactoryDependencies() =
    default;

PeerConnectionFactoryDependencies::PeerConnectionFactoryDependencies(
    PeerConnectionFactoryDependencies&&) = default;

PeerConnectionFactoryDependencies::~PeerConnectionFactoryDependencies() =
    default;

rtc::scoped_refptr<PeerConnectionInterface>
PeerConnectionFactoryInterface::CreatePeerConnection(
    const PeerConnectionInterface::RTCConfiguration& configuration,
    std::unique_ptr<cricket::PortAllocator> allocator,
    std::unique_ptr<rtc::RTCCertificateGeneratorInterface> cert_generator,
    PeerConnectionObserver* observer) {
  PeerConnectionDependencies dependencies(observer);
  dependencies.allocator = std::move(allocator);
  dependencies.cert_generator = std::move(cert_generator);
  auto result =
      CreatePeerConnectionOrError(configuration, std::move(dependencies));
  if (!result.ok()) {
    return nullptr;
  }
  return result.MoveValue();
}

rtc::scoped_refptr<PeerConnectionInterface>
PeerConnectionFactoryInterface::CreatePeerConnection(
    const PeerConnectionInterface::RTCConfiguration& configuration,
    PeerConnectionDependencies dependencies) {
  auto result =
      CreatePeerConnectionOrError(configuration, std::move(dependencies));
  if (!result.ok()) {
    return nullptr;
  }
  return result.MoveValue();
}

RTCErrorOr<rtc::scoped_refptr<PeerConnectionInterface>>
PeerConnectionFactoryInterface::CreatePeerConnectionOrError(
    const PeerConnectionInterface::RTCConfiguration& configuration,
    PeerConnectionDependencies dependencies) {
  return RTCError(RTCErrorType::INTERNAL_ERROR);
}

RtpCapabilities PeerConnectionFactoryInterface::GetRtpSenderCapabilities(
    cricket::MediaType kind) const {
  return {};
}

RtpCapabilities PeerConnectionFactoryInterface::GetRtpReceiverCapabilities(
    cricket::MediaType kind) const {
  return {};
}

}  // namespace webrtc
