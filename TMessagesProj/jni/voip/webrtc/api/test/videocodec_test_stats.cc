/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/test/videocodec_test_stats.h"

#include "rtc_base/strings/string_builder.h"

namespace webrtc {
namespace test {

VideoCodecTestStats::FrameStatistics::FrameStatistics(size_t frame_number,
                                                      size_t rtp_timestamp,
                                                      size_t spatial_idx)
    : frame_number(frame_number),
      rtp_timestamp(rtp_timestamp),
      spatial_idx(spatial_idx) {}

std::string VideoCodecTestStats::FrameStatistics::ToString() const {
  rtc::StringBuilder ss;
  for (const auto& entry : ToMap()) {
    if (ss.size() > 0) {
      ss << " ";
    }
    ss << entry.first << " " << entry.second;
  }
  return ss.Release();
}

std::map<std::string, std::string> VideoCodecTestStats::FrameStatistics::ToMap()
    const {
  std::map<std::string, std::string> map;
  map["frame_number"] = std::to_string(frame_number);
  map["decoded_width"] = std::to_string(decoded_width);
  map["decoded_height"] = std::to_string(decoded_height);
  map["spatial_idx"] = std::to_string(spatial_idx);
  map["temporal_idx"] = std::to_string(temporal_idx);
  map["inter_layer_predicted"] = std::to_string(inter_layer_predicted);
  map["non_ref_for_inter_layer_pred"] =
      std::to_string(non_ref_for_inter_layer_pred);
  map["frame_type"] = std::to_string(static_cast<int>(frame_type));
  map["length_bytes"] = std::to_string(length_bytes);
  map["qp"] = std::to_string(qp);
  map["psnr"] = std::to_string(psnr);
  map["psnr_y"] = std::to_string(psnr_y);
  map["psnr_u"] = std::to_string(psnr_u);
  map["psnr_v"] = std::to_string(psnr_v);
  map["ssim"] = std::to_string(ssim);
  map["encode_time_us"] = std::to_string(encode_time_us);
  map["decode_time_us"] = std::to_string(decode_time_us);
  map["rtp_timestamp"] = std::to_string(rtp_timestamp);
  map["target_bitrate_kbps"] = std::to_string(target_bitrate_kbps);
  map["target_framerate_fps"] = std::to_string(target_framerate_fps);
  return map;
}

std::string VideoCodecTestStats::VideoStatistics::ToString(
    std::string prefix) const {
  rtc::StringBuilder ss;
  for (const auto& entry : ToMap()) {
    if (ss.size() > 0) {
      ss << "\n";
    }
    ss << prefix << entry.first << ": " << entry.second;
  }
  return ss.Release();
}

std::map<std::string, std::string> VideoCodecTestStats::VideoStatistics::ToMap()
    const {
  std::map<std::string, std::string> map;
  map["target_bitrate_kbps"] = std::to_string(target_bitrate_kbps);
  map["input_framerate_fps"] = std::to_string(input_framerate_fps);
  map["spatial_idx"] = std::to_string(spatial_idx);
  map["temporal_idx"] = std::to_string(temporal_idx);
  map["width"] = std::to_string(width);
  map["height"] = std::to_string(height);
  map["length_bytes"] = std::to_string(length_bytes);
  map["bitrate_kbps"] = std::to_string(bitrate_kbps);
  map["framerate_fps"] = std::to_string(framerate_fps);
  map["enc_speed_fps"] = std::to_string(enc_speed_fps);
  map["dec_speed_fps"] = std::to_string(dec_speed_fps);
  map["avg_encode_latency_sec"] = std::to_string(avg_encode_latency_sec);
  map["max_encode_latency_sec"] = std::to_string(max_encode_latency_sec);
  map["avg_decode_latency_sec"] = std::to_string(avg_decode_latency_sec);
  map["max_decode_latency_sec"] = std::to_string(max_decode_latency_sec);
  map["avg_delay_sec"] = std::to_string(avg_delay_sec);
  map["max_key_frame_delay_sec"] = std::to_string(max_key_frame_delay_sec);
  map["max_delta_frame_delay_sec"] = std::to_string(max_delta_frame_delay_sec);
  map["time_to_reach_target_bitrate_sec"] =
      std::to_string(time_to_reach_target_bitrate_sec);
  map["avg_bitrate_mismatch_pct"] = std::to_string(avg_bitrate_mismatch_pct);
  map["avg_framerate_mismatch_pct"] =
      std::to_string(avg_framerate_mismatch_pct);
  map["avg_key_frame_size_bytes"] = std::to_string(avg_key_frame_size_bytes);
  map["avg_delta_frame_size_bytes"] =
      std::to_string(avg_delta_frame_size_bytes);
  map["avg_qp"] = std::to_string(avg_qp);
  map["avg_psnr"] = std::to_string(avg_psnr);
  map["min_psnr"] = std::to_string(min_psnr);
  map["avg_ssim"] = std::to_string(avg_ssim);
  map["min_ssim"] = std::to_string(min_ssim);
  map["num_input_frames"] = std::to_string(num_input_frames);
  map["num_encoded_frames"] = std::to_string(num_encoded_frames);
  map["num_decoded_frames"] = std::to_string(num_decoded_frames);
  map["num_dropped_frames"] =
      std::to_string(num_input_frames - num_encoded_frames);
  map["num_key_frames"] = std::to_string(num_key_frames);
  map["num_spatial_resizes"] = std::to_string(num_spatial_resizes);
  map["max_nalu_size_bytes"] = std::to_string(max_nalu_size_bytes);
  return map;
}

}  // namespace test
}  // namespace webrtc
