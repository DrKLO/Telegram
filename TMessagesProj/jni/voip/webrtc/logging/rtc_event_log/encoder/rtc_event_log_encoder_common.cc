/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/encoder/rtc_event_log_encoder_common.h"

#include "rtc_base/checks.h"

namespace webrtc {
namespace {
// We use 0x3fff because that gives decent precision (compared to the underlying
// measurement producing the packet loss fraction) on the one hand, while
// allowing us to use no more than 2 bytes in varint form on the other hand.
// (We might also fixed-size encode using at most 14 bits.)
constexpr uint32_t kPacketLossFractionRange = (1 << 14) - 1;  // 0x3fff
constexpr float kPacketLossFractionRangeFloat =
    static_cast<float>(kPacketLossFractionRange);
}  // namespace

uint32_t ConvertPacketLossFractionToProtoFormat(float packet_loss_fraction) {
  RTC_DCHECK_GE(packet_loss_fraction, 0);
  RTC_DCHECK_LE(packet_loss_fraction, 1);
  return static_cast<uint32_t>(packet_loss_fraction * kPacketLossFractionRange);
}

bool ParsePacketLossFractionFromProtoFormat(uint32_t proto_packet_loss_fraction,
                                            float* output) {
  if (proto_packet_loss_fraction >= kPacketLossFractionRange) {
    return false;
  }
  *output = proto_packet_loss_fraction / kPacketLossFractionRangeFloat;
  return true;
}
}  // namespace webrtc
