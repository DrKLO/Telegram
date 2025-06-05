/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/tools/neteq_replacement_input.h"

#include "modules/audio_coding/neteq/tools/fake_decode_from_file.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace test {

NetEqReplacementInput::NetEqReplacementInput(
    std::unique_ptr<NetEqInput> source,
    uint8_t replacement_payload_type,
    const std::set<uint8_t>& comfort_noise_types,
    const std::set<uint8_t>& forbidden_types)
    : source_(std::move(source)),
      replacement_payload_type_(replacement_payload_type),
      comfort_noise_types_(comfort_noise_types),
      forbidden_types_(forbidden_types) {
  RTC_CHECK(source_);
  packet_ = source_->PopPacket();
  ReplacePacket();
}

absl::optional<int64_t> NetEqReplacementInput::NextPacketTime() const {
  return packet_
             ? absl::optional<int64_t>(static_cast<int64_t>(packet_->time_ms))
             : absl::nullopt;
}

absl::optional<int64_t> NetEqReplacementInput::NextOutputEventTime() const {
  return source_->NextOutputEventTime();
}

absl::optional<NetEqInput::SetMinimumDelayInfo>
NetEqReplacementInput::NextSetMinimumDelayInfo() const {
  return source_->NextSetMinimumDelayInfo();
}

std::unique_ptr<NetEqInput::PacketData> NetEqReplacementInput::PopPacket() {
  std::unique_ptr<PacketData> to_return = std::move(packet_);
  while (true) {
    packet_ = source_->PopPacket();
    if (!packet_)
      break;
    if (packet_->payload.size() > packet_->header.paddingLength) {
      // Not padding only. Good to go. Skip this packet otherwise.
      break;
    }
  }
  ReplacePacket();
  return to_return;
}

void NetEqReplacementInput::AdvanceOutputEvent() {
  source_->AdvanceOutputEvent();
}

void NetEqReplacementInput::AdvanceSetMinimumDelay() {
  source_->AdvanceSetMinimumDelay();
}

bool NetEqReplacementInput::ended() const {
  return source_->ended();
}

absl::optional<RTPHeader> NetEqReplacementInput::NextHeader() const {
  return source_->NextHeader();
}

void NetEqReplacementInput::ReplacePacket() {
  if (!source_->NextPacketTime()) {
    // End of input. Cannot do proper replacement on the very last packet, so we
    // delete it instead.
    packet_.reset();
    return;
  }

  RTC_DCHECK(packet_);

  RTC_CHECK_EQ(forbidden_types_.count(packet_->header.payloadType), 0)
      << "Payload type " << static_cast<int>(packet_->header.payloadType)
      << " is forbidden.";

  // Check if this packet is comfort noise.
  if (comfort_noise_types_.count(packet_->header.payloadType) != 0) {
    // If CNG, simply insert a zero-energy one-byte payload.
    uint8_t cng_payload[1] = {127};  // Max attenuation of CNG.
    packet_->payload.SetData(cng_payload);
    return;
  }

  absl::optional<RTPHeader> next_hdr = source_->NextHeader();
  RTC_DCHECK(next_hdr);
  uint8_t payload[12];
  RTC_DCHECK_LE(last_frame_size_timestamps_, 120 * 48);
  uint32_t input_frame_size_timestamps = last_frame_size_timestamps_;
  const uint32_t timestamp_diff =
      next_hdr->timestamp - packet_->header.timestamp;
  const bool opus_dtx = packet_->payload.size() <= 2;
  if (next_hdr->sequenceNumber == packet_->header.sequenceNumber + 1 &&
      timestamp_diff <= 120 * 48 && timestamp_diff > 0 && !opus_dtx) {
    // Packets are in order and the timestamp diff is less than 5760 samples.
    // Accept the timestamp diff as a valid frame size.
    input_frame_size_timestamps = timestamp_diff;
    last_frame_size_timestamps_ = input_frame_size_timestamps;
  }
  RTC_DCHECK_LE(input_frame_size_timestamps, 120 * 48);
  FakeDecodeFromFile::PrepareEncoded(packet_->header.timestamp,
                                     input_frame_size_timestamps,
                                     packet_->payload.size(), payload);
  packet_->payload.SetData(payload);
  packet_->header.payloadType = replacement_payload_type_;
  return;
}

}  // namespace test
}  // namespace webrtc
