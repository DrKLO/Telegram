/*
 *  Copyright 2017 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/client/turn_port_factory.h"

#include <memory>
#include <utility>

#include "p2p/base/port_allocator.h"
#include "p2p/base/turn_port.h"

namespace cricket {

TurnPortFactory::~TurnPortFactory() {}

std::unique_ptr<Port> TurnPortFactory::Create(
    const CreateRelayPortArgs& args,
    rtc::AsyncPacketSocket* udp_socket) {
  auto port = TurnPort::Create(args, udp_socket);
  if (!port)
    return nullptr;
  port->SetTlsCertPolicy(args.config->tls_cert_policy);
  port->SetTurnLoggingId(args.config->turn_logging_id);
  return std::move(port);
}

std::unique_ptr<Port> TurnPortFactory::Create(const CreateRelayPortArgs& args,
                                              int min_port,
                                              int max_port) {
  auto port = TurnPort::Create(args, min_port, max_port);
  if (!port)
    return nullptr;
  port->SetTlsCertPolicy(args.config->tls_cert_policy);
  port->SetTurnLoggingId(args.config->turn_logging_id);
  return std::move(port);
}

}  // namespace cricket
