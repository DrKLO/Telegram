/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/tools/constant_pcm_packet_source.h"

#include <algorithm>

#include "modules/audio_coding/codecs/pcm16b/pcm16b.h"
#include "modules/audio_coding/neteq/tools/packet.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace test {

ConstantPcmPacketSource::ConstantPcmPacketSource(size_t payload_len_samples,
                                                 int16_t sample_value,
                                                 int sample_rate_hz,
                                                 int payload_type)
    : payload_len_samples_(payload_len_samples),
      packet_len_bytes_(2 * payload_len_samples_ + kHeaderLenBytes),
      samples_per_ms_(sample_rate_hz / 1000),
      next_arrival_time_ms_(0.0),
      payload_type_(payload_type),
      seq_number_(0),
      timestamp_(0),
      payload_ssrc_(0xABCD1234) {
  size_t encoded_len = WebRtcPcm16b_Encode(&sample_value, 1, encoded_sample_);
  RTC_CHECK_EQ(2U, encoded_len);
}

std::unique_ptr<Packet> ConstantPcmPacketSource::NextPacket() {
  RTC_CHECK_GT(packet_len_bytes_, kHeaderLenBytes);
  rtc::CopyOnWriteBuffer packet_buffer(packet_len_bytes_);
  uint8_t* packet_memory = packet_buffer.MutableData();
  // Fill the payload part of the packet memory with the pre-encoded value.
  for (unsigned i = 0; i < 2 * payload_len_samples_; ++i)
    packet_memory[kHeaderLenBytes + i] = encoded_sample_[i % 2];
  WriteHeader(packet_memory);
  // `packet` assumes ownership of `packet_memory`.
  auto packet =
      std::make_unique<Packet>(std::move(packet_buffer), next_arrival_time_ms_);
  next_arrival_time_ms_ += payload_len_samples_ / samples_per_ms_;
  return packet;
}

void ConstantPcmPacketSource::WriteHeader(uint8_t* packet_memory) {
  packet_memory[0] = 0x80;
  packet_memory[1] = static_cast<uint8_t>(payload_type_);
  packet_memory[2] = seq_number_ >> 8;
  packet_memory[3] = seq_number_ & 0xFF;
  packet_memory[4] = timestamp_ >> 24;
  packet_memory[5] = (timestamp_ >> 16) & 0xFF;
  packet_memory[6] = (timestamp_ >> 8) & 0xFF;
  packet_memory[7] = timestamp_ & 0xFF;
  packet_memory[8] = payload_ssrc_ >> 24;
  packet_memory[9] = (payload_ssrc_ >> 16) & 0xFF;
  packet_memory[10] = (payload_ssrc_ >> 8) & 0xFF;
  packet_memory[11] = payload_ssrc_ & 0xFF;
  ++seq_number_;
  timestamp_ += static_cast<uint32_t>(payload_len_samples_);
}

}  // namespace test
}  // namespace webrtc
