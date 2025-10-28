/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_packetizer_av1_test_helper.h"

#include <stdint.h>

#include <initializer_list>
#include <vector>

namespace webrtc {

Av1Obu::Av1Obu(uint8_t obu_type) : header_(obu_type | kAv1ObuSizePresentBit) {}

Av1Obu& Av1Obu::WithExtension(uint8_t extension) {
  extension_ = extension;
  header_ |= kAv1ObuExtensionPresentBit;
  return *this;
}
Av1Obu& Av1Obu::WithoutSize() {
  header_ &= ~kAv1ObuSizePresentBit;
  return *this;
}
Av1Obu& Av1Obu::WithPayload(std::vector<uint8_t> payload) {
  payload_ = std::move(payload);
  return *this;
}

std::vector<uint8_t> BuildAv1Frame(std::initializer_list<Av1Obu> obus) {
  std::vector<uint8_t> raw;
  for (const Av1Obu& obu : obus) {
    raw.push_back(obu.header_);
    if (obu.header_ & kAv1ObuExtensionPresentBit) {
      raw.push_back(obu.extension_);
    }
    if (obu.header_ & kAv1ObuSizePresentBit) {
      // write size in leb128 format.
      size_t payload_size = obu.payload_.size();
      while (payload_size >= 0x80) {
        raw.push_back(0x80 | (payload_size & 0x7F));
        payload_size >>= 7;
      }
      raw.push_back(payload_size);
    }
    raw.insert(raw.end(), obu.payload_.begin(), obu.payload_.end());
  }
  return raw;
}

}  // namespace webrtc
