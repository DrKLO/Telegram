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
  ss << "frame_number " << frame_number;
  ss << " decoded_width " << decoded_width;
  ss << " decoded_height " << decoded_height;
  ss << " spatial_idx " << spatial_idx;
  ss << " temporal_idx " << temporal_idx;
  ss << " inter_layer_predicted " << inter_layer_predicted;
  ss << " non_ref_for_inter_layer_pred " << non_ref_for_inter_layer_pred;
  ss << " frame_type " << static_cast<int>(frame_type);
  ss << " length_bytes " << length_bytes;
  ss << " qp " << qp;
  ss << " psnr " << psnr;
  ss << " psnr_y " << psnr_y;
  ss << " psnr_u " << psnr_u;
  ss << " psnr_v " << psnr_v;
  ss << " ssim " << ssim;
  ss << " encode_time_us " << encode_time_us;
  ss << " decode_time_us " << decode_time_us;
  ss << " rtp_timestamp " << rtp_timestamp;
  ss << " target_bitrate_kbps " << target_bitrate_kbps;
  ss << " target_framerate_fps " << target_framerate_fps;
  return ss.Release();
}

std::string VideoCodecTestStats::VideoStatistics::ToString(
    std::string prefix) const {
  rtc::StringBuilder ss;
  ss << prefix << "target_bitrate_kbps: " << target_bitrate_kbps;
  ss << "\n" << prefix << "input_framerate_fps: " << input_framerate_fps;
  ss << "\n" << prefix << "spatial_idx: " << spatial_idx;
  ss << "\n" << prefix << "temporal_idx: " << temporal_idx;
  ss << "\n" << prefix << "width: " << width;
  ss << "\n" << prefix << "height: " << height;
  ss << "\n" << prefix << "length_bytes: " << length_bytes;
  ss << "\n" << prefix << "bitrate_kbps: " << bitrate_kbps;
  ss << "\n" << prefix << "framerate_fps: " << framerate_fps;
  ss << "\n" << prefix << "enc_speed_fps: " << enc_speed_fps;
  ss << "\n" << prefix << "dec_speed_fps: " << dec_speed_fps;
  ss << "\n" << prefix << "avg_delay_sec: " << avg_delay_sec;
  ss << "\n"
     << prefix << "max_key_frame_delay_sec: " << max_key_frame_delay_sec;
  ss << "\n"
     << prefix << "max_delta_frame_delay_sec: " << max_delta_frame_delay_sec;
  ss << "\n"
     << prefix << "time_to_reach_target_bitrate_sec: "
     << time_to_reach_target_bitrate_sec;
  ss << "\n"
     << prefix << "avg_key_frame_size_bytes: " << avg_key_frame_size_bytes;
  ss << "\n"
     << prefix << "avg_delta_frame_size_bytes: " << avg_delta_frame_size_bytes;
  ss << "\n" << prefix << "avg_qp: " << avg_qp;
  ss << "\n" << prefix << "avg_psnr: " << avg_psnr;
  ss << "\n" << prefix << "min_psnr: " << min_psnr;
  ss << "\n" << prefix << "avg_ssim: " << avg_ssim;
  ss << "\n" << prefix << "min_ssim: " << min_ssim;
  ss << "\n" << prefix << "num_input_frames: " << num_input_frames;
  ss << "\n" << prefix << "num_encoded_frames: " << num_encoded_frames;
  ss << "\n" << prefix << "num_decoded_frames: " << num_decoded_frames;
  ss << "\n"
     << prefix
     << "num_dropped_frames: " << num_input_frames - num_encoded_frames;
  ss << "\n" << prefix << "num_key_frames: " << num_key_frames;
  ss << "\n" << prefix << "num_spatial_resizes: " << num_spatial_resizes;
  ss << "\n" << prefix << "max_nalu_size_bytes: " << max_nalu_size_bytes;
  return ss.Release();
}

}  // namespace test
}  // namespace webrtc
