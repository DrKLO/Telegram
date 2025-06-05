/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/video_send_stream.h"

#include <utility>

#include "api/crypto/frame_encryptor_interface.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/strings/string_format.h"

namespace webrtc {

namespace {

const char* StreamTypeToString(VideoSendStream::StreamStats::StreamType type) {
  switch (type) {
    case VideoSendStream::StreamStats::StreamType::kMedia:
      return "media";
    case VideoSendStream::StreamStats::StreamType::kRtx:
      return "rtx";
    case VideoSendStream::StreamStats::StreamType::kFlexfec:
      return "flexfec";
  }
  RTC_CHECK_NOTREACHED();
}

}  // namespace

VideoSendStream::StreamStats::StreamStats() = default;
VideoSendStream::StreamStats::~StreamStats() = default;

std::string VideoSendStream::StreamStats::ToString() const {
  char buf[1024];
  rtc::SimpleStringBuilder ss(buf);
  ss << "type: " << StreamTypeToString(type);
  if (referenced_media_ssrc.has_value())
    ss << " (for: " << referenced_media_ssrc.value() << ")";
  ss << ", ";
  ss << "width: " << width << ", ";
  ss << "height: " << height << ", ";
  ss << "key: " << frame_counts.key_frames << ", ";
  ss << "delta: " << frame_counts.delta_frames << ", ";
  ss << "total_bps: " << total_bitrate_bps << ", ";
  ss << "retransmit_bps: " << retransmit_bitrate_bps << ", ";
  ss << "avg_delay_ms: " << avg_delay_ms << ", ";
  ss << "max_delay_ms: " << max_delay_ms << ", ";
  if (report_block_data) {
    ss << "cum_loss: " << report_block_data->cumulative_lost() << ", ";
    ss << "max_ext_seq: "
       << report_block_data->extended_highest_sequence_number() << ", ";
  }
  ss << "nack: " << rtcp_packet_type_counts.nack_packets << ", ";
  ss << "fir: " << rtcp_packet_type_counts.fir_packets << ", ";
  ss << "pli: " << rtcp_packet_type_counts.pli_packets;
  return ss.str();
}

VideoSendStream::Stats::Stats() = default;
VideoSendStream::Stats::~Stats() = default;

std::string VideoSendStream::Stats::ToString(int64_t time_ms) const {
  char buf[2048];
  rtc::SimpleStringBuilder ss(buf);
  ss << "VideoSendStream stats: " << time_ms << ", {";
  ss << "input_fps: " << rtc::StringFormat("%.1f", input_frame_rate) << ", ";
  ss << "encode_fps: " << encode_frame_rate << ", ";
  ss << "encode_ms: " << avg_encode_time_ms << ", ";
  ss << "encode_usage_perc: " << encode_usage_percent << ", ";
  ss << "target_bps: " << target_media_bitrate_bps << ", ";
  ss << "media_bps: " << media_bitrate_bps << ", ";
  ss << "suspended: " << (suspended ? "true" : "false") << ", ";
  ss << "bw_adapted_res: " << (bw_limited_resolution ? "true" : "false")
     << ", ";
  ss << "cpu_adapted_res: " << (cpu_limited_resolution ? "true" : "false")
     << ", ";
  ss << "bw_adapted_fps: " << (bw_limited_framerate ? "true" : "false") << ", ";
  ss << "cpu_adapted_fps: " << (cpu_limited_framerate ? "true" : "false")
     << ", ";
  ss << "#cpu_adaptations: " << number_of_cpu_adapt_changes << ", ";
  ss << "#quality_adaptations: " << number_of_quality_adapt_changes;
  ss << '}';
  for (const auto& substream : substreams) {
    if (substream.second.type ==
        VideoSendStream::StreamStats::StreamType::kMedia) {
      ss << " {ssrc: " << substream.first << ", ";
      ss << substream.second.ToString();
      ss << '}';
    }
  }
  return ss.str();
}

VideoSendStream::Config::Config(const Config&) = default;
VideoSendStream::Config::Config(Config&&) = default;
VideoSendStream::Config::Config(Transport* send_transport)
    : rtp(),
      encoder_settings(VideoEncoder::Capabilities(rtp.lntf.enabled)),
      send_transport(send_transport) {}

VideoSendStream::Config& VideoSendStream::Config::operator=(Config&&) = default;
VideoSendStream::Config::Config::~Config() = default;

std::string VideoSendStream::Config::ToString() const {
  char buf[2 * 1024];
  rtc::SimpleStringBuilder ss(buf);
  ss << "{encoder_settings: { experiment_cpu_load_estimator: "
     << (encoder_settings.experiment_cpu_load_estimator ? "on" : "off") << "}}";
  ss << ", rtp: " << rtp.ToString();
  ss << ", rtcp_report_interval_ms: " << rtcp_report_interval_ms;
  ss << ", send_transport: " << (send_transport ? "(Transport)" : "nullptr");
  ss << ", render_delay_ms: " << render_delay_ms;
  ss << ", target_delay_ms: " << target_delay_ms;
  ss << ", suspend_below_min_bitrate: "
     << (suspend_below_min_bitrate ? "on" : "off");
  ss << '}';
  return ss.str();
}

}  // namespace webrtc
