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

VideoReceiveStream::Decoder::Decoder(SdpVideoFormat video_format,
                                     int payload_type)
    : video_format(std::move(video_format)), payload_type(payload_type) {}
VideoReceiveStream::Decoder::Decoder() : video_format("Unset") {}
VideoReceiveStream::Decoder::Decoder(const Decoder&) = default;
VideoReceiveStream::Decoder::~Decoder() = default;

bool VideoReceiveStream::Decoder::operator==(const Decoder& other) const {
  return payload_type == other.payload_type &&
         video_format == other.video_format;
}

std::string VideoReceiveStream::Decoder::ToString() const {
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

VideoReceiveStream::Stats::Stats() = default;
VideoReceiveStream::Stats::~Stats() = default;

std::string VideoReceiveStream::Stats::ToString(int64_t time_ms) const {
  char buf[2048];
  rtc::SimpleStringBuilder ss(buf);
  ss << "VideoReceiveStream stats: " << time_ms << ", {ssrc: " << ssrc << ", ";
  ss << "total_bps: " << total_bitrate_bps << ", ";
  ss << "width: " << width << ", ";
  ss << "height: " << height << ", ";
  ss << "key: " << frame_counts.key_frames << ", ";
  ss << "delta: " << frame_counts.delta_frames << ", ";
  ss << "frames_dropped: " << frames_dropped << ", ";
  ss << "network_fps: " << network_frame_rate << ", ";
  ss << "decode_fps: " << decode_frame_rate << ", ";
  ss << "render_fps: " << render_frame_rate << ", ";
  ss << "decode_ms: " << decode_ms << ", ";
  ss << "max_decode_ms: " << max_decode_ms << ", ";
  ss << "first_frame_received_to_decoded_ms: "
     << first_frame_received_to_decoded_ms << ", ";
  ss << "cur_delay_ms: " << current_delay_ms << ", ";
  ss << "targ_delay_ms: " << target_delay_ms << ", ";
  ss << "jb_delay_ms: " << jitter_buffer_ms << ", ";
  ss << "jb_cumulative_delay_seconds: " << jitter_buffer_delay_seconds << ", ";
  ss << "jb_emitted_count: " << jitter_buffer_emitted_count << ", ";
  ss << "min_playout_delay_ms: " << min_playout_delay_ms << ", ";
  ss << "sync_offset_ms: " << sync_offset_ms << ", ";
  ss << "cum_loss: " << rtp_stats.packets_lost << ", ";
  ss << "nack: " << rtcp_packet_type_counts.nack_packets << ", ";
  ss << "fir: " << rtcp_packet_type_counts.fir_packets << ", ";
  ss << "pli: " << rtcp_packet_type_counts.pli_packets;
  ss << '}';
  return ss.str();
}

VideoReceiveStream::Config::Config(const Config&) = default;
VideoReceiveStream::Config::Config(Config&&) = default;
VideoReceiveStream::Config::Config(Transport* rtcp_send_transport,
                                   VideoDecoderFactory* decoder_factory)
    : decoder_factory(decoder_factory),
      rtcp_send_transport(rtcp_send_transport) {}

VideoReceiveStream::Config& VideoReceiveStream::Config::operator=(Config&&) =
    default;
VideoReceiveStream::Config::Config::~Config() = default;

std::string VideoReceiveStream::Config::ToString() const {
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
  ss << ", target_delay_ms: " << target_delay_ms;
  ss << '}';

  return ss.str();
}

VideoReceiveStream::Config::Rtp::Rtp() = default;
VideoReceiveStream::Config::Rtp::Rtp(const Rtp&) = default;
VideoReceiveStream::Config::Rtp::~Rtp() = default;

std::string VideoReceiveStream::Config::Rtp::ToString() const {
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
  ss << ", transport_cc: " << (transport_cc ? "on" : "off");
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
  ss << ", extensions: [";
  for (size_t i = 0; i < extensions.size(); ++i) {
    ss << extensions[i].ToString();
    if (i != extensions.size() - 1)
      ss << ", ";
  }
  ss << ']';
  ss << '}';
  return ss.str();
}

}  // namespace webrtc
