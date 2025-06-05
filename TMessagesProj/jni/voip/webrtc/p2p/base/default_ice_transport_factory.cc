/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/default_ice_transport_factory.h"

#include <utility>

#include "api/make_ref_counted.h"
#include "p2p/base/basic_ice_controller.h"
#include "p2p/base/ice_controller_factory_interface.h"

namespace {

class BasicIceControllerFactory
    : public cricket::IceControllerFactoryInterface {
 public:
  std::unique_ptr<cricket::IceControllerInterface> Create(
      const cricket::IceControllerFactoryArgs& args) override {
    return std::make_unique<cricket::BasicIceController>(args);
  }
};

}  // namespace

namespace webrtc {

DefaultIceTransport::DefaultIceTransport(
    std::unique_ptr<cricket::P2PTransportChannel> internal)
    : internal_(std::move(internal)) {}

DefaultIceTransport::~DefaultIceTransport() {
  RTC_DCHECK_RUN_ON(&thread_checker_);
}

rtc::scoped_refptr<IceTransportInterface>
DefaultIceTransportFactory::CreateIceTransport(
    const std::string& transport_name,
    int component,
    IceTransportInit init) {
  BasicIceControllerFactory factory;
  init.set_ice_controller_factory(&factory);
  return rtc::make_ref_counted<DefaultIceTransport>(
      cricket::P2PTransportChannel::Create(transport_name, component,
                                           std::move(init)));
}

}  // namespace webrtc
