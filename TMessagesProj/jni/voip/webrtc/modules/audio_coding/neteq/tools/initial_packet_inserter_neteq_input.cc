/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/tools/initial_packet_inserter_neteq_input.h"

#include <limits>
#include <memory>
#include <utility>

#include "rtc_base/checks.h"

namespace webrtc {
namespace test {

InitialPacketInserterNetEqInput::InitialPacketInserterNetEqInput(
    std::unique_ptr<NetEqInput> source,
    int number_of_initial_packets,
    int sample_rate_hz)
    : source_(std::move(source)),
      packets_to_insert_(number_of_initial_packets),
      sample_rate_hz_(sample_rate_hz) {}

absl::optional<int64_t> InitialPacketInserterNetEqInput::NextPacketTime()
    const {
  return source_->NextPacketTime();
}

absl::optional<int64_t> InitialPacketInserterNetEqInput::NextOutputEventTime()
    const {
  return source_->NextOutputEventTime();
}

absl::optional<NetEqInput::SetMinimumDelayInfo>
InitialPacketInserterNetEqInput::NextSetMinimumDelayInfo() const {
  return source_->NextSetMinimumDelayInfo();
}

std::unique_ptr<InitialPacketInserterNetEqInput::PacketData>
InitialPacketInserterNetEqInput::PopPacket() {
  if (!first_packet_) {
    first_packet_ = source_->PopPacket();
    if (!first_packet_) {
      // The source has no packets, so we should not insert any dummy packets.
      packets_to_insert_ = 0;
    }
  }
  if (packets_to_insert_ > 0) {
    RTC_CHECK(first_packet_);
    auto dummy_packet = std::unique_ptr<PacketData>(new PacketData());
    dummy_packet->header = first_packet_->header;
    dummy_packet->payload = rtc::Buffer(first_packet_->payload.data(),
                                        first_packet_->payload.size());
    dummy_packet->time_ms = first_packet_->time_ms;
    dummy_packet->header.sequenceNumber -= packets_to_insert_;
    // This assumes 20ms per packet.
    dummy_packet->header.timestamp -=
        20 * sample_rate_hz_ * packets_to_insert_ / 1000;
    packets_to_insert_--;
    return dummy_packet;
  }
  return source_->PopPacket();
}

void InitialPacketInserterNetEqInput::AdvanceSetMinimumDelay() {
  source_->AdvanceSetMinimumDelay();
}

void InitialPacketInserterNetEqInput::AdvanceOutputEvent() {
  source_->AdvanceOutputEvent();
}

bool InitialPacketInserterNetEqInput::ended() const {
  return source_->ended();
}

absl::optional<RTPHeader> InitialPacketInserterNetEqInput::NextHeader() const {
  return source_->NextHeader();
}

}  // namespace test
}  // namespace webrtc
