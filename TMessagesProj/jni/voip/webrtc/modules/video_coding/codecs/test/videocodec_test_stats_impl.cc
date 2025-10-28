/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codecs/test/videocodec_test_stats_impl.h"

#include <algorithm>
#include <cmath>
#include <iterator>
#include <limits>
#include <numeric>

#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/running_statistics.h"
#include "rtc_base/strings/string_builder.h"

namespace webrtc {
namespace test {

using FrameStatistics = VideoCodecTestStats::FrameStatistics;
using VideoStatistics = VideoCodecTestStats::VideoStatistics;

namespace {
const int kMaxBitrateMismatchPercent = 20;
}

VideoCodecTestStatsImpl::VideoCodecTestStatsImpl() = default;
VideoCodecTestStatsImpl::~VideoCodecTestStatsImpl() = default;

void VideoCodecTestStatsImpl::AddFrame(const FrameStatistics& frame_stat) {
  const size_t timestamp = frame_stat.rtp_timestamp;
  const size_t layer_idx = frame_stat.spatial_idx;
  RTC_DCHECK(rtp_timestamp_to_frame_num_[layer_idx].find(timestamp) ==
             rtp_timestamp_to_frame_num_[layer_idx].end());
  rtp_timestamp_to_frame_num_[layer_idx][timestamp] = frame_stat.frame_number;
  layer_stats_[layer_idx].push_back(frame_stat);
}

FrameStatistics* VideoCodecTestStatsImpl::GetFrame(size_t frame_num,
                                                   size_t layer_idx) {
  RTC_CHECK_LT(frame_num, layer_stats_[layer_idx].size());
  return &layer_stats_[layer_idx][frame_num];
}

FrameStatistics* VideoCodecTestStatsImpl::GetFrameWithTimestamp(
    size_t timestamp,
    size_t layer_idx) {
  RTC_DCHECK(rtp_timestamp_to_frame_num_[layer_idx].find(timestamp) !=
             rtp_timestamp_to_frame_num_[layer_idx].end());

  return GetFrame(rtp_timestamp_to_frame_num_[layer_idx][timestamp], layer_idx);
}

FrameStatistics* VideoCodecTestStatsImpl::GetOrAddFrame(size_t timestamp_rtp,
                                                        size_t spatial_idx) {
  if (rtp_timestamp_to_frame_num_[spatial_idx].count(timestamp_rtp) > 0) {
    return GetFrameWithTimestamp(timestamp_rtp, spatial_idx);
  }

  size_t frame_num = layer_stats_[spatial_idx].size();
  AddFrame(FrameStatistics(frame_num, timestamp_rtp, spatial_idx));

  return GetFrameWithTimestamp(timestamp_rtp, spatial_idx);
}

std::vector<FrameStatistics> VideoCodecTestStatsImpl::GetFrameStatistics()
    const {
  size_t capacity = 0;
  for (const auto& layer_stat : layer_stats_) {
    capacity += layer_stat.second.size();
  }

  std::vector<FrameStatistics> frame_statistics;
  frame_statistics.reserve(capacity);
  for (const auto& layer_stat : layer_stats_) {
    std::copy(layer_stat.second.cbegin(), layer_stat.second.cend(),
              std::back_inserter(frame_statistics));
  }

  return frame_statistics;
}

std::vector<VideoStatistics>
VideoCodecTestStatsImpl::SliceAndCalcLayerVideoStatistic(
    size_t first_frame_num,
    size_t last_frame_num) {
  std::vector<VideoStatistics> layer_stats;

  size_t num_spatial_layers = 0;
  size_t num_temporal_layers = 0;
  GetNumberOfEncodedLayers(first_frame_num, last_frame_num, &num_spatial_layers,
                           &num_temporal_layers);
  RTC_CHECK_GT(num_spatial_layers, 0);
  RTC_CHECK_GT(num_temporal_layers, 0);

  for (size_t spatial_idx = 0; spatial_idx < num_spatial_layers;
       ++spatial_idx) {
    for (size_t temporal_idx = 0; temporal_idx < num_temporal_layers;
         ++temporal_idx) {
      VideoStatistics layer_stat = SliceAndCalcVideoStatistic(
          first_frame_num, last_frame_num, spatial_idx, temporal_idx, false,
          /*target_bitrate=*/absl::nullopt, /*target_framerate=*/absl::nullopt);
      layer_stats.push_back(layer_stat);
    }
  }

  return layer_stats;
}

VideoStatistics VideoCodecTestStatsImpl::SliceAndCalcAggregatedVideoStatistic(
    size_t first_frame_num,
    size_t last_frame_num) {
  size_t num_spatial_layers = 0;
  size_t num_temporal_layers = 0;
  GetNumberOfEncodedLayers(first_frame_num, last_frame_num, &num_spatial_layers,
                           &num_temporal_layers);
  RTC_CHECK_GT(num_spatial_layers, 0);
  RTC_CHECK_GT(num_temporal_layers, 0);

  return SliceAndCalcVideoStatistic(
      first_frame_num, last_frame_num, num_spatial_layers - 1,
      num_temporal_layers - 1, true, /*target_bitrate=*/absl::nullopt,
      /*target_framerate=*/absl::nullopt);
}

VideoStatistics VideoCodecTestStatsImpl::CalcVideoStatistic(
    size_t first_frame_num,
    size_t last_frame_num,
    DataRate target_bitrate,
    Frequency target_framerate) {
  size_t num_spatial_layers = 0;
  size_t num_temporal_layers = 0;
  GetNumberOfEncodedLayers(first_frame_num, last_frame_num, &num_spatial_layers,
                           &num_temporal_layers);
  return SliceAndCalcVideoStatistic(
      first_frame_num, last_frame_num, num_spatial_layers - 1,
      num_temporal_layers - 1, true, target_bitrate, target_framerate);
}

size_t VideoCodecTestStatsImpl::Size(size_t spatial_idx) {
  return layer_stats_[spatial_idx].size();
}

void VideoCodecTestStatsImpl::Clear() {
  layer_stats_.clear();
  rtp_timestamp_to_frame_num_.clear();
}

FrameStatistics VideoCodecTestStatsImpl::AggregateFrameStatistic(
    size_t frame_num,
    size_t spatial_idx,
    bool aggregate_independent_layers) {
  FrameStatistics frame_stat = *GetFrame(frame_num, spatial_idx);
  bool inter_layer_predicted = frame_stat.inter_layer_predicted;
  while (spatial_idx-- > 0) {
    if (aggregate_independent_layers || inter_layer_predicted) {
      FrameStatistics* base_frame_stat = GetFrame(frame_num, spatial_idx);
      frame_stat.length_bytes += base_frame_stat->length_bytes;
      frame_stat.target_bitrate_kbps += base_frame_stat->target_bitrate_kbps;

      inter_layer_predicted = base_frame_stat->inter_layer_predicted;
    }
  }

  return frame_stat;
}

size_t VideoCodecTestStatsImpl::CalcLayerTargetBitrateKbps(
    size_t first_frame_num,
    size_t last_frame_num,
    size_t spatial_idx,
    size_t temporal_idx,
    bool aggregate_independent_layers) {
  size_t target_bitrate_kbps = 0;

  // We don't know if superframe includes all required spatial layers because
  // of possible frame drops. Run through all frames in specified range, find
  // and return maximum target bitrate. Assume that target bitrate in frame
  // statistic is specified per temporal layer.
  for (size_t frame_num = first_frame_num; frame_num <= last_frame_num;
       ++frame_num) {
    FrameStatistics superframe = AggregateFrameStatistic(
        frame_num, spatial_idx, aggregate_independent_layers);

    if (superframe.temporal_idx <= temporal_idx) {
      target_bitrate_kbps =
          std::max(target_bitrate_kbps, superframe.target_bitrate_kbps);
    }
  }

  RTC_DCHECK_GT(target_bitrate_kbps, 0);
  return target_bitrate_kbps;
}

VideoStatistics VideoCodecTestStatsImpl::SliceAndCalcVideoStatistic(
    size_t first_frame_num,
    size_t last_frame_num,
    size_t spatial_idx,
    size_t temporal_idx,
    bool aggregate_independent_layers,
    absl::optional<DataRate> target_bitrate,
    absl::optional<Frequency> target_framerate) {
  VideoStatistics video_stat;

  float buffer_level_bits = 0.0f;
  webrtc_impl::RunningStatistics<float> buffer_level_sec;

  webrtc_impl::RunningStatistics<size_t> key_frame_size_bytes;
  webrtc_impl::RunningStatistics<size_t> delta_frame_size_bytes;

  webrtc_impl::RunningStatistics<size_t> frame_encoding_time_us;
  webrtc_impl::RunningStatistics<size_t> frame_decoding_time_us;

  webrtc_impl::RunningStatistics<float> psnr_y;
  webrtc_impl::RunningStatistics<float> psnr_u;
  webrtc_impl::RunningStatistics<float> psnr_v;
  webrtc_impl::RunningStatistics<float> psnr;
  webrtc_impl::RunningStatistics<float> ssim;
  webrtc_impl::RunningStatistics<int> qp;

  size_t rtp_timestamp_first_frame = 0;
  size_t rtp_timestamp_prev_frame = 0;

  FrameStatistics last_successfully_decoded_frame(0, 0, 0);

  const size_t target_bitrate_kbps =
      target_bitrate.has_value()
          ? target_bitrate->kbps()
          : CalcLayerTargetBitrateKbps(first_frame_num, last_frame_num,
                                       spatial_idx, temporal_idx,
                                       aggregate_independent_layers);
  const size_t target_bitrate_bps = 1000 * target_bitrate_kbps;
  RTC_CHECK_GT(target_bitrate_kbps, 0);  // We divide by `target_bitrate_kbps`.

  for (size_t frame_num = first_frame_num; frame_num <= last_frame_num;
       ++frame_num) {
    FrameStatistics frame_stat = AggregateFrameStatistic(
        frame_num, spatial_idx, aggregate_independent_layers);

    float time_since_first_frame_sec =
        1.0f * (frame_stat.rtp_timestamp - rtp_timestamp_first_frame) /
        kVideoPayloadTypeFrequency;
    float time_since_prev_frame_sec =
        1.0f * (frame_stat.rtp_timestamp - rtp_timestamp_prev_frame) /
        kVideoPayloadTypeFrequency;

    if (frame_stat.temporal_idx > temporal_idx) {
      continue;
    }

    buffer_level_bits -= time_since_prev_frame_sec * 1000 * target_bitrate_kbps;
    buffer_level_bits = std::max(0.0f, buffer_level_bits);
    buffer_level_bits += 8.0 * frame_stat.length_bytes;
    buffer_level_sec.AddSample(buffer_level_bits /
                               (1000 * target_bitrate_kbps));

    video_stat.length_bytes += frame_stat.length_bytes;

    if (frame_stat.encoding_successful) {
      ++video_stat.num_encoded_frames;

      if (frame_stat.frame_type == VideoFrameType::kVideoFrameKey) {
        key_frame_size_bytes.AddSample(frame_stat.length_bytes);
        ++video_stat.num_key_frames;
      } else {
        delta_frame_size_bytes.AddSample(frame_stat.length_bytes);
      }

      frame_encoding_time_us.AddSample(frame_stat.encode_time_us);
      qp.AddSample(frame_stat.qp);

      video_stat.max_nalu_size_bytes = std::max(video_stat.max_nalu_size_bytes,
                                                frame_stat.max_nalu_size_bytes);
    }

    if (frame_stat.decoding_successful) {
      ++video_stat.num_decoded_frames;

      video_stat.width = std::max(video_stat.width, frame_stat.decoded_width);
      video_stat.height =
          std::max(video_stat.height, frame_stat.decoded_height);

      if (video_stat.num_decoded_frames > 1) {
        if (last_successfully_decoded_frame.decoded_width !=
                frame_stat.decoded_width ||
            last_successfully_decoded_frame.decoded_height !=
                frame_stat.decoded_height) {
          ++video_stat.num_spatial_resizes;
        }
      }

      frame_decoding_time_us.AddSample(frame_stat.decode_time_us);
      last_successfully_decoded_frame = frame_stat;
    }

    if (frame_stat.quality_analysis_successful) {
      psnr_y.AddSample(frame_stat.psnr_y);
      psnr_u.AddSample(frame_stat.psnr_u);
      psnr_v.AddSample(frame_stat.psnr_v);
      psnr.AddSample(frame_stat.psnr);
      ssim.AddSample(frame_stat.ssim);
    }

    if (video_stat.num_input_frames > 0) {
      if (video_stat.time_to_reach_target_bitrate_sec == 0.0f) {
        RTC_CHECK_GT(time_since_first_frame_sec, 0);
        const float curr_kbps =
            8.0 * video_stat.length_bytes / 1000 / time_since_first_frame_sec;
        const float bitrate_mismatch_percent =
            100 * std::fabs(curr_kbps - target_bitrate_kbps) /
            target_bitrate_kbps;
        if (bitrate_mismatch_percent < kMaxBitrateMismatchPercent) {
          video_stat.time_to_reach_target_bitrate_sec =
              time_since_first_frame_sec;
        }
      }
    }

    rtp_timestamp_prev_frame = frame_stat.rtp_timestamp;
    if (video_stat.num_input_frames == 0) {
      rtp_timestamp_first_frame = frame_stat.rtp_timestamp;
    }

    ++video_stat.num_input_frames;
  }

  const size_t num_frames = last_frame_num - first_frame_num + 1;
  const size_t timestamp_delta =
      GetFrame(first_frame_num + 1, spatial_idx)->rtp_timestamp -
      GetFrame(first_frame_num, spatial_idx)->rtp_timestamp;
  RTC_CHECK_GT(timestamp_delta, 0);
  const float input_framerate_fps =
      target_framerate.has_value()
          ? target_framerate->millihertz() / 1000.0
          : 1.0 * kVideoPayloadTypeFrequency / timestamp_delta;
  RTC_CHECK_GT(input_framerate_fps, 0);
  const float duration_sec = num_frames / input_framerate_fps;

  video_stat.target_bitrate_kbps = target_bitrate_kbps;
  video_stat.input_framerate_fps = input_framerate_fps;

  video_stat.spatial_idx = spatial_idx;
  video_stat.temporal_idx = temporal_idx;

  RTC_CHECK_GT(duration_sec, 0);
  const float bitrate_bps = 8 * video_stat.length_bytes / duration_sec;
  video_stat.bitrate_kbps = static_cast<size_t>((bitrate_bps + 500) / 1000);
  video_stat.framerate_fps = video_stat.num_encoded_frames / duration_sec;

  // http://bugs.webrtc.org/10400: On Windows, we only get millisecond
  // granularity in the frame encode/decode timing measurements.
  // So we need to softly avoid a div-by-zero here.
  const float mean_encode_time_us =
      frame_encoding_time_us.GetMean().value_or(0);
  video_stat.enc_speed_fps = mean_encode_time_us > 0.0f
                                 ? 1000000.0f / mean_encode_time_us
                                 : std::numeric_limits<float>::max();
  const float mean_decode_time_us =
      frame_decoding_time_us.GetMean().value_or(0);
  video_stat.dec_speed_fps = mean_decode_time_us > 0.0f
                                 ? 1000000.0f / mean_decode_time_us
                                 : std::numeric_limits<float>::max();

  video_stat.avg_encode_latency_sec =
      frame_encoding_time_us.GetMean().value_or(0) / 1000000.0f;
  video_stat.max_encode_latency_sec =
      frame_encoding_time_us.GetMax().value_or(0) / 1000000.0f;

  video_stat.avg_decode_latency_sec =
      frame_decoding_time_us.GetMean().value_or(0) / 1000000.0f;
  video_stat.max_decode_latency_sec =
      frame_decoding_time_us.GetMax().value_or(0) / 1000000.0f;

  auto MaxDelaySec = [target_bitrate_kbps](
                         const webrtc_impl::RunningStatistics<size_t>& stats) {
    return 8 * stats.GetMax().value_or(0) / 1000 / target_bitrate_kbps;
  };

  video_stat.avg_delay_sec = buffer_level_sec.GetMean().value_or(0);
  video_stat.max_key_frame_delay_sec = MaxDelaySec(key_frame_size_bytes);
  video_stat.max_delta_frame_delay_sec = MaxDelaySec(delta_frame_size_bytes);

  video_stat.avg_bitrate_mismatch_pct =
      100 * (bitrate_bps - target_bitrate_bps) / target_bitrate_bps;
  video_stat.avg_framerate_mismatch_pct =
      100 * (video_stat.framerate_fps - input_framerate_fps) /
      input_framerate_fps;

  video_stat.avg_key_frame_size_bytes =
      key_frame_size_bytes.GetMean().value_or(0);
  video_stat.avg_delta_frame_size_bytes =
      delta_frame_size_bytes.GetMean().value_or(0);
  video_stat.avg_qp = qp.GetMean().value_or(0);

  video_stat.avg_psnr_y = psnr_y.GetMean().value_or(0);
  video_stat.avg_psnr_u = psnr_u.GetMean().value_or(0);
  video_stat.avg_psnr_v = psnr_v.GetMean().value_or(0);
  video_stat.avg_psnr = psnr.GetMean().value_or(0);
  video_stat.min_psnr =
      psnr.GetMin().value_or(std::numeric_limits<float>::max());
  video_stat.avg_ssim = ssim.GetMean().value_or(0);
  video_stat.min_ssim =
      ssim.GetMin().value_or(std::numeric_limits<float>::max());

  return video_stat;
}

void VideoCodecTestStatsImpl::GetNumberOfEncodedLayers(
    size_t first_frame_num,
    size_t last_frame_num,
    size_t* num_encoded_spatial_layers,
    size_t* num_encoded_temporal_layers) {
  *num_encoded_spatial_layers = 0;
  *num_encoded_temporal_layers = 0;

  const size_t num_spatial_layers = layer_stats_.size();

  for (size_t frame_num = first_frame_num; frame_num <= last_frame_num;
       ++frame_num) {
    for (size_t spatial_idx = 0; spatial_idx < num_spatial_layers;
         ++spatial_idx) {
      FrameStatistics* frame_stat = GetFrame(frame_num, spatial_idx);
      if (frame_stat->encoding_successful) {
        *num_encoded_spatial_layers =
            std::max(*num_encoded_spatial_layers, frame_stat->spatial_idx + 1);
        *num_encoded_temporal_layers = std::max(*num_encoded_temporal_layers,
                                                frame_stat->temporal_idx + 1);
      }
    }
  }
}

}  // namespace test
}  // namespace webrtc
