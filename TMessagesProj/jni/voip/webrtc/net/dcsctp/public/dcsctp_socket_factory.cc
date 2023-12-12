/*
 *  Copyright 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "net/dcsctp/public/dcsctp_socket_factory.h"

#include <memory>
#include <utility>

#include "absl/strings/string_view.h"
#include "net/dcsctp/public/dcsctp_options.h"
#include "net/dcsctp/public/dcsctp_socket.h"
#include "net/dcsctp/public/packet_observer.h"
#include "net/dcsctp/socket/dcsctp_socket.h"

namespace dcsctp {

DcSctpSocketFactory::~DcSctpSocketFactory() = default;

std::unique_ptr<DcSctpSocketInterface> DcSctpSocketFactory::Create(
    absl::string_view log_prefix,
    DcSctpSocketCallbacks& callbacks,
    std::unique_ptr<PacketObserver> packet_observer,
    const DcSctpOptions& options) {
  return std::make_unique<DcSctpSocket>(log_prefix, callbacks,
                                        std::move(packet_observer), options);
}
}  // namespace dcsctp
