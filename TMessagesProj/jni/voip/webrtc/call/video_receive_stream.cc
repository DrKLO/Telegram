/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/video_receive_stream.h"

#include "rtc_base/strings/string_builder.h"

namespace webrtc {

VideoReceiveStreamInterface::Decoder::Decoder(SdpVideoFormat video_format,
                                              int payload_type)
    : video_format(std::move(video_format)), payload_type(payload_type) {}
VideoReceiveStreamInterface::Decoder::Decoder() : video_format("Unset") {}
VideoReceiveStreamInterface::Decoder::Decoder(const Decoder&) = default;
VideoReceiveStreamInterface::Decoder::~Decoder() = default;

bool VideoReceiveStreamInterface::Decoder::operator==(
    const Decoder& other) const {
  return payload_type == other.payload_type &&
         video_format == other.video_format;
}

std::string VideoReceiveStreamInterface::Decoder::ToString() const {
  char buf[1024];
  rtc::SimpleStringBuilder ss(buf);
  ss << "{payload_type: " << payload_type;
  ss << ", payload_name: " << video_format.name;
  ss << ", codec_params: {";
  for (auto it = video_format.parameters.begin();
       it != video_format.parameters.end(); ++it) {
    if (it != video_format.parameters.begin()) {
      ss << ", ";
    }
    ss << it->first << ": " << it->second;
  }
  ss << '}';
  ss << '}';

  return ss.str();
}

VideoReceiveStreamInterface::Stats::Stats() = default;
VideoReceiveStreamInterface::Stats::~Stats() = default;

std::string VideoReceiveStreamInterface::Stats::ToString(
    int64_t time_ms) const {
  char buf[2048];
  rtc::SimpleStringBuilder ss(buf);
  ss << "VideoReceiveStreamInterface stats: " << time_ms << ", {ssrc: " << ssrc
     << ", ";
  ss << "total_bps: " << total_bitrate_bps << ", ";
  // Spec-compliant stats are camelCased to distinguish them from
  // the legacy and internal stats.
  ss << "frameWidth: " << width << ", ";
  ss << "frameHeight: " << height << ", ";
  // TODO(crbug.com/webrtc/15166): `key` and `delta` will not
  // perfectly match the other frame counters.
  ss << "key: " << frame_counts.key_frames << ", ";
  ss << "delta: " << frame_counts.delta_frames << ", ";
  ss << "framesAssembledFromMultiplePackets: "
     << frames_assembled_from_multiple_packets << ", ";
  ss << "framesDecoded: " << frames_decoded << ", ";
  ss << "framesDropped: " << frames_dropped << ", ";
  ss << "network_fps: " << network_frame_rate << ", ";
  ss << "decode_fps: " << decode_frame_rate << ", ";
  ss << "render_fps: " << render_frame_rate << ", ";
  ss << "decode_ms: " << decode_ms << ", ";
  ss << "max_decode_ms: " << max_decode_ms << ", ";
  ss << "first_frame_received_to_decoded_ms: "
     << first_frame_received_to_decoded_ms << ", ";
  ss << "current_delay_ms: " << current_delay_ms << ", ";
  ss << "target_delay_ms: " << target_delay_ms << ", ";
  ss << "jitter_delay_ms: " << jitter_buffer_ms << ", ";
  ss << "totalAssemblyTime: " << total_assembly_time.seconds<double>() << ", ";
  ss << "jitterBufferDelay: " << jitter_buffer_delay.seconds<double>() << ", ";
  ss << "jitterBufferTargetDelay: "
     << jitter_buffer_target_delay.seconds<double>() << ", ";
  ss << "jitterBufferEmittedCount: " << jitter_buffer_emitted_count << ", ";
  ss << "jitterBufferMinimumDelay: "
     << jitter_buffer_minimum_delay.seconds<double>() << ", ";
  ss << "totalDecodeTime: " << total_decode_time.seconds<double>() << ", ";
  ss << "totalProcessingDelay: " << total_processing_delay.seconds<double>()
     << ", ";
  ss << "min_playout_delay_ms: " << min_playout_delay_ms << ", ";
  ss << "sync_offset_ms: " << sync_offset_ms << ", ";
  ss << "cum_loss: " << rtp_stats.packets_lost << ", ";
  ss << "nackCount: " << rtcp_packet_type_counts.nack_packets << ", ";
  ss << "firCount: " << rtcp_packet_type_counts.fir_packets << ", ";
  ss << "pliCount: " << rtcp_packet_type_counts.pli_packets;
  ss << '}';
  return ss.str();
}

VideoReceiveStreamInterface::Config::Config(const Config&) = default;
VideoReceiveStreamInterface::Config::Config(Config&&) = default;
VideoReceiveStreamInterface::Config::Config(
    Transport* rtcp_send_transport,
    VideoDecoderFactory* decoder_factory)
    : decoder_factory(decoder_factory),
      rtcp_send_transport(rtcp_send_transport) {}

VideoReceiveStreamInterface::Config&
VideoReceiveStreamInterface::Config::operator=(Config&&) = default;
VideoReceiveStreamInterface::Config::Config::~Config() = default;

std::string VideoReceiveStreamInterface::Config::ToString() const {
  char buf[4 * 1024];
  rtc::SimpleStringBuilder ss(buf);
  ss << "{decoders: [";
  for (size_t i = 0; i < decoders.size(); ++i) {
    ss << decoders[i].ToString();
    if (i != decoders.size() - 1)
      ss << ", ";
  }
  ss << ']';
  ss << ", rtp: " << rtp.ToString();
  ss << ", renderer: " << (renderer ? "(renderer)" : "nullptr");
  ss << ", render_delay_ms: " << render_delay_ms;
  if (!sync_group.empty())
    ss << ", sync_group: " << sync_group;
  ss << '}';

  return ss.str();
}

VideoReceiveStreamInterface::Config::Rtp::Rtp() = default;
VideoReceiveStreamInterface::Config::Rtp::Rtp(const Rtp&) = default;
VideoReceiveStreamInterface::Config::Rtp::~Rtp() = default;

std::string VideoReceiveStreamInterface::Config::Rtp::ToString() const {
  char buf[2 * 1024];
  rtc::SimpleStringBuilder ss(buf);
  ss << "{remote_ssrc: " << remote_ssrc;
  ss << ", local_ssrc: " << local_ssrc;
  ss << ", rtcp_mode: "
     << (rtcp_mode == RtcpMode::kCompound ? "RtcpMode::kCompound"
                                          : "RtcpMode::kReducedSize");
  ss << ", rtcp_xr: ";
  ss << "{receiver_reference_time_report: "
     << (rtcp_xr.receiver_reference_time_report ? "on" : "off");
  ss << '}';
  ss << ", lntf: {enabled: " << (lntf.enabled ? "true" : "false") << '}';
  ss << ", nack: {rtp_history_ms: " << nack.rtp_history_ms << '}';
  ss << ", ulpfec_payload_type: " << ulpfec_payload_type;
  ss << ", red_type: " << red_payload_type;
  ss << ", rtx_ssrc: " << rtx_ssrc;
  ss << ", rtx_payload_types: {";
  for (auto& kv : rtx_associated_payload_types) {
    ss << kv.first << " (pt) -> " << kv.second << " (apt), ";
  }
  ss << '}';
  ss << ", raw_payload_types: {";
  for (const auto& pt : raw_payload_types) {
    ss << pt << ", ";
  }
  ss << '}';
  ss << '}';
  return ss.str();
}

}  // namespace webrtc
