/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/tools/neteq_event_log_input.h"

#include <limits>
#include <memory>

#include "absl/strings/string_view.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace test {
namespace {

class NetEqEventLogInput : public NetEqInput {
 public:
  NetEqEventLogInput(const std::vector<LoggedRtpPacketIncoming>& packet_stream,
                     const std::vector<LoggedAudioPlayoutEvent>& output_events,
                     const std::vector<LoggedNetEqSetMinimumDelayEvent>&
                         neteq_set_minimum_delay_events,
                     absl::optional<int64_t> end_time_ms)
      : packet_stream_(packet_stream),
        packet_stream_it_(packet_stream_.begin()),
        output_events_(output_events),
        output_events_it_(output_events_.begin()),
        neteq_set_minimum_delay_events_(neteq_set_minimum_delay_events),
        neteq_set_minimum_delay_events_it_(
            neteq_set_minimum_delay_events_.begin()),
        end_time_ms_(end_time_ms) {
    // Ignore all output events before the first packet.
    while (output_events_it_ != output_events_.end() &&
           output_events_it_->log_time_ms() <
               packet_stream_it_->log_time_ms()) {
      ++output_events_it_;
    }
  }

  absl::optional<int64_t> NextPacketTime() const override {
    if (packet_stream_it_ == packet_stream_.end()) {
      return absl::nullopt;
    }
    if (end_time_ms_ && packet_stream_it_->rtp.log_time_ms() > *end_time_ms_) {
      return absl::nullopt;
    }
    return packet_stream_it_->rtp.log_time_ms();
  }

  absl::optional<int64_t> NextOutputEventTime() const override {
    if (output_events_it_ == output_events_.end()) {
      return absl::nullopt;
    }
    if (end_time_ms_ && output_events_it_->log_time_ms() > *end_time_ms_) {
      return absl::nullopt;
    }
    return output_events_it_->log_time_ms();
  }

  absl::optional<SetMinimumDelayInfo> NextSetMinimumDelayInfo() const override {
    if (neteq_set_minimum_delay_events_it_ ==
        neteq_set_minimum_delay_events_.end()) {
      return absl::nullopt;
    }
    if (end_time_ms_ &&
        neteq_set_minimum_delay_events_it_->log_time_ms() > *end_time_ms_) {
      return absl::nullopt;
    }
    return SetMinimumDelayInfo(
        neteq_set_minimum_delay_events_it_->log_time_ms(),
        neteq_set_minimum_delay_events_it_->minimum_delay_ms);
  }

  std::unique_ptr<PacketData> PopPacket() override {
    if (packet_stream_it_ == packet_stream_.end()) {
      return nullptr;
    }
    auto packet_data = std::make_unique<PacketData>();
    packet_data->header = packet_stream_it_->rtp.header;
    packet_data->time_ms = packet_stream_it_->rtp.log_time_ms();

    // This is a header-only "dummy" packet. Set the payload to all zeros, with
    // length according to the virtual length.
    packet_data->payload.SetSize(packet_stream_it_->rtp.total_length -
                                 packet_stream_it_->rtp.header_length);
    std::fill_n(packet_data->payload.data(), packet_data->payload.size(), 0);

    ++packet_stream_it_;
    return packet_data;
  }

  void AdvanceOutputEvent() override {
    if (output_events_it_ != output_events_.end()) {
      ++output_events_it_;
    }
  }

  void AdvanceSetMinimumDelay() override {
    if (neteq_set_minimum_delay_events_it_ !=
        neteq_set_minimum_delay_events_.end()) {
      ++neteq_set_minimum_delay_events_it_;
    }
  }

  bool ended() const override { return !NextEventTime(); }

  absl::optional<RTPHeader> NextHeader() const override {
    if (packet_stream_it_ == packet_stream_.end()) {
      return absl::nullopt;
    }
    return packet_stream_it_->rtp.header;
  }

 private:
  const std::vector<LoggedRtpPacketIncoming> packet_stream_;
  std::vector<LoggedRtpPacketIncoming>::const_iterator packet_stream_it_;
  const std::vector<LoggedAudioPlayoutEvent> output_events_;
  std::vector<LoggedAudioPlayoutEvent>::const_iterator output_events_it_;
  const std::vector<LoggedNetEqSetMinimumDelayEvent>
      neteq_set_minimum_delay_events_;
  std::vector<LoggedNetEqSetMinimumDelayEvent>::const_iterator
      neteq_set_minimum_delay_events_it_;
  const absl::optional<int64_t> end_time_ms_;
};

}  // namespace

std::unique_ptr<NetEqInput> CreateNetEqEventLogInput(
    const ParsedRtcEventLog& parsed_log,
    absl::optional<uint32_t> ssrc) {
  if (parsed_log.incoming_audio_ssrcs().empty()) {
    return nullptr;
  }
  // Pick the first SSRC if none was provided.
  ssrc = ssrc.value_or(*parsed_log.incoming_audio_ssrcs().begin());
  auto streams = parsed_log.incoming_rtp_packets_by_ssrc();
  auto stream =
      std::find_if(streams.begin(), streams.end(),
                   [ssrc](auto stream) { return stream.ssrc == ssrc; });
  if (stream == streams.end()) {
    return nullptr;
  }
  auto output_events_it = parsed_log.audio_playout_events().find(*ssrc);
  if (output_events_it == parsed_log.audio_playout_events().end()) {
    return nullptr;
  }
  std::vector<LoggedNetEqSetMinimumDelayEvent> neteq_set_minimum_delay_events;
  auto neteq_set_minimum_delay_events_it =
      parsed_log.neteq_set_minimum_delay_events().find(*ssrc);
  if (neteq_set_minimum_delay_events_it !=
      parsed_log.neteq_set_minimum_delay_events().end()) {
    neteq_set_minimum_delay_events = neteq_set_minimum_delay_events_it->second;
  }
  int64_t end_time_ms = parsed_log.first_log_segment().stop_time_ms();
  return std::make_unique<NetEqEventLogInput>(
      stream->incoming_packets, output_events_it->second,
      neteq_set_minimum_delay_events, end_time_ms);
}

}  // namespace test
}  // namespace webrtc
