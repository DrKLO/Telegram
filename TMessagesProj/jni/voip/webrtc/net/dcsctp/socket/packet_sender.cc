/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/socket/packet_sender.h"

#include <utility>
#include <vector>

#include "net/dcsctp/public/types.h"

namespace dcsctp {

PacketSender::PacketSender(DcSctpSocketCallbacks& callbacks,
                           std::function<void(rtc::ArrayView<const uint8_t>,
                                              SendPacketStatus)> on_sent_packet)
    : callbacks_(callbacks), on_sent_packet_(std::move(on_sent_packet)) {}

bool PacketSender::Send(SctpPacket::Builder& builder, bool write_checksum) {
  if (builder.empty()) {
    return false;
  }

  std::vector<uint8_t> payload = builder.Build(write_checksum);

  SendPacketStatus status = callbacks_.SendPacketWithStatus(payload);
  on_sent_packet_(payload, status);
  switch (status) {
    case SendPacketStatus::kSuccess: {
      return true;
    }
    case SendPacketStatus::kTemporaryFailure: {
      // TODO(boivie): Queue this packet to be retried to be sent later.
      return false;
    }

    case SendPacketStatus::kError: {
      // Nothing that can be done.
      return false;
    }
  }
}
}  // namespace dcsctp
