/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/tools/neteq_rtp_dump_input.h"

#include "absl/strings/string_view.h"
#include "modules/audio_coding/neteq/tools/rtp_file_source.h"

namespace webrtc {
namespace test {
namespace {

// An adapter class to dress up a PacketSource object as a NetEqInput.
class NetEqRtpDumpInput : public NetEqInput {
 public:
  NetEqRtpDumpInput(absl::string_view file_name,
                    const std::map<int, RTPExtensionType>& hdr_ext_map,
                    absl::optional<uint32_t> ssrc_filter)
      : source_(RtpFileSource::Create(file_name, ssrc_filter)) {
    for (const auto& ext_pair : hdr_ext_map) {
      source_->RegisterRtpHeaderExtension(ext_pair.second, ext_pair.first);
    }
    LoadNextPacket();
  }

  absl::optional<int64_t> NextOutputEventTime() const override {
    return next_output_event_ms_;
  }

  absl::optional<SetMinimumDelayInfo> NextSetMinimumDelayInfo() const override {
    return absl::nullopt;
  }

  void AdvanceOutputEvent() override {
    if (next_output_event_ms_) {
      *next_output_event_ms_ += kOutputPeriodMs;
    }
    if (!NextPacketTime()) {
      next_output_event_ms_ = absl::nullopt;
    }
  }

  void AdvanceSetMinimumDelay() override {}

  absl::optional<int64_t> NextPacketTime() const override {
    return packet_ ? absl::optional<int64_t>(
                         static_cast<int64_t>(packet_->time_ms()))
                   : absl::nullopt;
  }

  std::unique_ptr<PacketData> PopPacket() override {
    if (!packet_) {
      return std::unique_ptr<PacketData>();
    }
    std::unique_ptr<PacketData> packet_data(new PacketData);
    packet_data->header = packet_->header();
    if (packet_->payload_length_bytes() == 0 &&
        packet_->virtual_payload_length_bytes() > 0) {
      // This is a header-only "dummy" packet. Set the payload to all zeros,
      // with length according to the virtual length.
      packet_data->payload.SetSize(packet_->virtual_payload_length_bytes());
      std::fill_n(packet_data->payload.data(), packet_data->payload.size(), 0);
    } else {
      packet_data->payload.SetData(packet_->payload(),
                                   packet_->payload_length_bytes());
    }
    packet_data->time_ms = packet_->time_ms();

    LoadNextPacket();

    return packet_data;
  }

  absl::optional<RTPHeader> NextHeader() const override {
    return packet_ ? absl::optional<RTPHeader>(packet_->header())
                   : absl::nullopt;
  }

  bool ended() const override { return !next_output_event_ms_; }

 private:
  void LoadNextPacket() { packet_ = source_->NextPacket(); }

  absl::optional<int64_t> next_output_event_ms_ = 0;
  static constexpr int64_t kOutputPeriodMs = 10;

  std::unique_ptr<RtpFileSource> source_;
  std::unique_ptr<Packet> packet_;
};

}  // namespace

std::unique_ptr<NetEqInput> CreateNetEqRtpDumpInput(
    absl::string_view file_name,
    const std::map<int, RTPExtensionType>& hdr_ext_map,
    absl::optional<uint32_t> ssrc_filter) {
  return std::make_unique<NetEqRtpDumpInput>(file_name, hdr_ext_map,
                                             ssrc_filter);
}

}  // namespace test
}  // namespace webrtc
