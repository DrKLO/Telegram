/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/packet.h"

namespace webrtc {

Packet::Packet() = default;
Packet::Packet(Packet&& b) = default;

Packet::~Packet() = default;

Packet& Packet::operator=(Packet&& b) = default;

Packet Packet::Clone() const {
  RTC_CHECK(!frame);

  Packet clone;
  clone.timestamp = timestamp;
  clone.sequence_number = sequence_number;
  clone.payload_type = payload_type;
  clone.payload.SetData(payload.data(), payload.size());
  clone.priority = priority;
  clone.packet_info = packet_info;

  return clone;
}

}  // namespace webrtc
