/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/tools/encode_neteq_input.h"

#include <utility>

#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_conversions.h"

namespace webrtc {
namespace test {

EncodeNetEqInput::EncodeNetEqInput(std::unique_ptr<Generator> generator,
                                   std::unique_ptr<AudioEncoder> encoder,
                                   int64_t input_duration_ms)
    : generator_(std::move(generator)),
      encoder_(std::move(encoder)),
      input_duration_ms_(input_duration_ms) {
  CreatePacket();
}

EncodeNetEqInput::~EncodeNetEqInput() = default;

absl::optional<int64_t> EncodeNetEqInput::NextPacketTime() const {
  RTC_DCHECK(packet_data_);
  return static_cast<int64_t>(packet_data_->time_ms);
}

absl::optional<int64_t> EncodeNetEqInput::NextOutputEventTime() const {
  return next_output_event_ms_;
}

std::unique_ptr<NetEqInput::PacketData> EncodeNetEqInput::PopPacket() {
  RTC_DCHECK(packet_data_);
  // Grab the packet to return...
  std::unique_ptr<PacketData> packet_to_return = std::move(packet_data_);
  // ... and line up the next packet for future use.
  CreatePacket();

  return packet_to_return;
}

void EncodeNetEqInput::AdvanceOutputEvent() {
  next_output_event_ms_ += kOutputPeriodMs;
}

bool EncodeNetEqInput::ended() const {
  return next_output_event_ms_ > input_duration_ms_;
}

absl::optional<RTPHeader> EncodeNetEqInput::NextHeader() const {
  RTC_DCHECK(packet_data_);
  return packet_data_->header;
}

void EncodeNetEqInput::CreatePacket() {
  // Create a new PacketData object.
  RTC_DCHECK(!packet_data_);
  packet_data_.reset(new NetEqInput::PacketData);
  RTC_DCHECK_EQ(packet_data_->payload.size(), 0);

  // Loop until we get a packet.
  AudioEncoder::EncodedInfo info;
  RTC_DCHECK(!info.send_even_if_empty);
  int num_blocks = 0;
  while (packet_data_->payload.size() == 0 && !info.send_even_if_empty) {
    const size_t num_samples = rtc::CheckedDivExact(
        static_cast<int>(encoder_->SampleRateHz() * kOutputPeriodMs), 1000);

    info = encoder_->Encode(rtp_timestamp_, generator_->Generate(num_samples),
                            &packet_data_->payload);

    rtp_timestamp_ += rtc::dchecked_cast<uint32_t>(
        num_samples * encoder_->RtpTimestampRateHz() /
        encoder_->SampleRateHz());
    ++num_blocks;
  }
  packet_data_->header.timestamp = info.encoded_timestamp;
  packet_data_->header.payloadType = info.payload_type;
  packet_data_->header.sequenceNumber = sequence_number_++;
  packet_data_->time_ms = next_packet_time_ms_;
  next_packet_time_ms_ += num_blocks * kOutputPeriodMs;
}

}  // namespace test
}  // namespace webrtc
