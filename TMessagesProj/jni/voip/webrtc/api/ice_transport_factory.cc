/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/ice_transport_factory.h"

#include <memory>
#include <utility>

#include "p2p/base/ice_transport_internal.h"
#include "p2p/base/p2p_constants.h"
#include "p2p/base/p2p_transport_channel.h"
#include "p2p/base/port_allocator.h"
#include "rtc_base/thread.h"

namespace webrtc {

namespace {

// This implementation of IceTransportInterface is used in cases where
// the only reference to the P2PTransport will be through this class.
// It must be constructed, accessed and destroyed on the signaling thread.
class IceTransportWithTransportChannel : public IceTransportInterface {
 public:
  IceTransportWithTransportChannel(
      std::unique_ptr<cricket::IceTransportInternal> internal)
      : internal_(std::move(internal)) {}

  ~IceTransportWithTransportChannel() override {
    RTC_DCHECK_RUN_ON(&thread_checker_);
  }

  cricket::IceTransportInternal* internal() override {
    RTC_DCHECK_RUN_ON(&thread_checker_);
    return internal_.get();
  }

 private:
  const SequenceChecker thread_checker_{};
  const std::unique_ptr<cricket::IceTransportInternal> internal_
      RTC_GUARDED_BY(thread_checker_);
};

}  // namespace

rtc::scoped_refptr<IceTransportInterface> CreateIceTransport(
    cricket::PortAllocator* port_allocator) {
  IceTransportInit init;
  init.set_port_allocator(port_allocator);
  return CreateIceTransport(std::move(init));
}

rtc::scoped_refptr<IceTransportInterface> CreateIceTransport(
    IceTransportInit init) {
  if (init.async_resolver_factory()) {
    // Backwards compatibility mode
    return rtc::make_ref_counted<IceTransportWithTransportChannel>(
        std::make_unique<cricket::P2PTransportChannel>(
            "", cricket::ICE_CANDIDATE_COMPONENT_RTP, init.port_allocator(),
            init.async_resolver_factory(), init.event_log()));
  } else {
    return rtc::make_ref_counted<IceTransportWithTransportChannel>(
        cricket::P2PTransportChannel::Create(
            "", cricket::ICE_CANDIDATE_COMPONENT_RTP, init.port_allocator(),
            init.async_dns_resolver_factory(), init.event_log()));
  }
}

}  // namespace webrtc
