/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/send_statistics_proxy.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <limits>
#include <utility>

#include "absl/strings/match.h"
#include "api/video/video_codec_constants.h"
#include "api/video/video_codec_type.h"
#include "api/video_codecs/video_codec.h"
#include "modules/video_coding/include/video_codec_interface.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/mod_ops.h"
#include "rtc_base/strings/string_builder.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {
namespace {
const float kEncodeTimeWeigthFactor = 0.5f;
const size_t kMaxEncodedFrameMapSize = 150;
const int64_t kMaxEncodedFrameWindowMs = 800;
const uint32_t kMaxEncodedFrameTimestampDiff = 900000;  // 10 sec.
const int64_t kBucketSizeMs = 100;
const size_t kBucketCount = 10;

const char kVp8ForcedFallbackEncoderFieldTrial[] =
    "WebRTC-VP8-Forced-Fallback-Encoder-v2";
const char kVp8SwCodecName[] = "libvpx";

// Used by histograms. Values of entries should not be changed.
enum HistogramCodecType {
  kVideoUnknown = 0,
  kVideoVp8 = 1,
  kVideoVp9 = 2,
  kVideoH264 = 3,
  kVideoAv1 = 4,
#ifndef DISABLE_H265
  kVideoH265 = 5,
#endif
  kVideoMax = 64,
};

const char* kRealtimePrefix = "WebRTC.Video.";
const char* kScreenPrefix = "WebRTC.Video.Screenshare.";

const char* GetUmaPrefix(VideoEncoderConfig::ContentType content_type) {
  switch (content_type) {
    case VideoEncoderConfig::ContentType::kRealtimeVideo:
      return kRealtimePrefix;
    case VideoEncoderConfig::ContentType::kScreen:
      return kScreenPrefix;
  }
  RTC_DCHECK_NOTREACHED();
  return nullptr;
}

HistogramCodecType PayloadNameToHistogramCodecType(
    const std::string& payload_name) {
  VideoCodecType codecType = PayloadStringToCodecType(payload_name);
  switch (codecType) {
    case kVideoCodecVP8:
      return kVideoVp8;
    case kVideoCodecVP9:
      return kVideoVp9;
    case kVideoCodecH264:
      return kVideoH264;
    case kVideoCodecAV1:
      return kVideoAv1;
#ifndef DISABLE_H265
    case kVideoCodecH265:
      return kVideoH265;
#endif
    default:
      return kVideoUnknown;
  }
}

void UpdateCodecTypeHistogram(const std::string& payload_name) {
  RTC_HISTOGRAM_ENUMERATION("WebRTC.Video.Encoder.CodecType",
                            PayloadNameToHistogramCodecType(payload_name),
                            kVideoMax);
}

bool IsForcedFallbackPossible(const CodecSpecificInfo* codec_info,
                              int simulcast_index) {
  return codec_info->codecType == kVideoCodecVP8 && simulcast_index == 0 &&
         (codec_info->codecSpecific.VP8.temporalIdx == 0 ||
          codec_info->codecSpecific.VP8.temporalIdx == kNoTemporalIdx);
}

absl::optional<int> GetFallbackMaxPixels(const std::string& group) {
  if (group.empty())
    return absl::nullopt;

  int min_pixels;
  int max_pixels;
  int min_bps;
  if (sscanf(group.c_str(), "-%d,%d,%d", &min_pixels, &max_pixels, &min_bps) !=
      3) {
    return absl::optional<int>();
  }

  if (min_pixels <= 0 || max_pixels <= 0 || max_pixels < min_pixels)
    return absl::optional<int>();

  return absl::optional<int>(max_pixels);
}

absl::optional<int> GetFallbackMaxPixelsIfFieldTrialEnabled(
    const webrtc::FieldTrialsView& field_trials) {
  std::string group = field_trials.Lookup(kVp8ForcedFallbackEncoderFieldTrial);
  return (absl::StartsWith(group, "Enabled"))
             ? GetFallbackMaxPixels(group.substr(7))
             : absl::optional<int>();
}

absl::optional<int> GetFallbackMaxPixelsIfFieldTrialDisabled(
    const webrtc::FieldTrialsView& field_trials) {
  std::string group = field_trials.Lookup(kVp8ForcedFallbackEncoderFieldTrial);
  return (absl::StartsWith(group, "Disabled"))
             ? GetFallbackMaxPixels(group.substr(8))
             : absl::optional<int>();
}
}  // namespace

const int SendStatisticsProxy::kStatsTimeoutMs = 5000;

SendStatisticsProxy::SendStatisticsProxy(
    Clock* clock,
    const VideoSendStream::Config& config,
    VideoEncoderConfig::ContentType content_type,
    const FieldTrialsView& field_trials)
    : clock_(clock),
      payload_name_(config.rtp.payload_name),
      rtp_config_(config.rtp),
      fallback_max_pixels_(
          GetFallbackMaxPixelsIfFieldTrialEnabled(field_trials)),
      fallback_max_pixels_disabled_(
          GetFallbackMaxPixelsIfFieldTrialDisabled(field_trials)),
      content_type_(content_type),
      start_ms_(clock->TimeInMilliseconds()),
      encode_time_(kEncodeTimeWeigthFactor),
      quality_limitation_reason_tracker_(clock_),
      media_byte_rate_tracker_(kBucketSizeMs, kBucketCount),
      encoded_frame_rate_tracker_(kBucketSizeMs, kBucketCount),
      last_num_spatial_layers_(0),
      last_num_simulcast_streams_(0),
      last_spatial_layer_use_{},
      bw_limited_layers_(false),
      internal_encoder_scaler_(false),
      uma_container_(
          new UmaSamplesContainer(GetUmaPrefix(content_type_), stats_, clock)) {
}

SendStatisticsProxy::~SendStatisticsProxy() {
  MutexLock lock(&mutex_);
  uma_container_->UpdateHistograms(rtp_config_, stats_);

  int64_t elapsed_sec = (clock_->TimeInMilliseconds() - start_ms_) / 1000;
  RTC_HISTOGRAM_COUNTS_100000("WebRTC.Video.SendStreamLifetimeInSeconds",
                              elapsed_sec);

  if (elapsed_sec >= metrics::kMinRunTimeInSeconds)
    UpdateCodecTypeHistogram(payload_name_);
}

SendStatisticsProxy::FallbackEncoderInfo::FallbackEncoderInfo() = default;

SendStatisticsProxy::UmaSamplesContainer::UmaSamplesContainer(
    const char* prefix,
    const VideoSendStream::Stats& stats,
    Clock* const clock)
    : uma_prefix_(prefix),
      clock_(clock),
      input_frame_rate_tracker_(100, 10u),
      input_fps_counter_(clock, nullptr, true),
      sent_fps_counter_(clock, nullptr, true),
      total_byte_counter_(clock, nullptr, true),
      media_byte_counter_(clock, nullptr, true),
      rtx_byte_counter_(clock, nullptr, true),
      padding_byte_counter_(clock, nullptr, true),
      retransmit_byte_counter_(clock, nullptr, true),
      fec_byte_counter_(clock, nullptr, true),
      first_rtcp_stats_time_ms_(-1),
      first_rtp_stats_time_ms_(-1),
      start_stats_(stats),
      num_streams_(0),
      num_pixels_highest_stream_(0) {
  InitializeBitrateCounters(stats);
  static_assert(
      kMaxEncodedFrameTimestampDiff < std::numeric_limits<uint32_t>::max() / 2,
      "has to be smaller than half range");
}

SendStatisticsProxy::UmaSamplesContainer::~UmaSamplesContainer() {}

void SendStatisticsProxy::UmaSamplesContainer::InitializeBitrateCounters(
    const VideoSendStream::Stats& stats) {
  for (const auto& it : stats.substreams) {
    uint32_t ssrc = it.first;
    total_byte_counter_.SetLast(it.second.rtp_stats.transmitted.TotalBytes(),
                                ssrc);
    padding_byte_counter_.SetLast(it.second.rtp_stats.transmitted.padding_bytes,
                                  ssrc);
    retransmit_byte_counter_.SetLast(
        it.second.rtp_stats.retransmitted.TotalBytes(), ssrc);
    fec_byte_counter_.SetLast(it.second.rtp_stats.fec.TotalBytes(), ssrc);
    switch (it.second.type) {
      case VideoSendStream::StreamStats::StreamType::kMedia:
        media_byte_counter_.SetLast(it.second.rtp_stats.MediaPayloadBytes(),
                                    ssrc);
        break;
      case VideoSendStream::StreamStats::StreamType::kRtx:
        rtx_byte_counter_.SetLast(it.second.rtp_stats.transmitted.TotalBytes(),
                                  ssrc);
        break;
      case VideoSendStream::StreamStats::StreamType::kFlexfec:
        break;
    }
  }
}

void SendStatisticsProxy::UmaSamplesContainer::RemoveOld(int64_t now_ms) {
  while (!encoded_frames_.empty()) {
    auto it = encoded_frames_.begin();
    if (now_ms - it->second.send_ms < kMaxEncodedFrameWindowMs)
      break;

    // Use max per timestamp.
    sent_width_counter_.Add(it->second.max_width);
    sent_height_counter_.Add(it->second.max_height);

    // Check number of encoded streams per timestamp.
    if (num_streams_ > static_cast<size_t>(it->second.max_simulcast_idx)) {
      if (num_streams_ > 1) {
        int disabled_streams =
            static_cast<int>(num_streams_ - 1 - it->second.max_simulcast_idx);
        // Can be limited in resolution or framerate.
        uint32_t pixels = it->second.max_width * it->second.max_height;
        bool bw_limited_resolution =
            disabled_streams > 0 && pixels < num_pixels_highest_stream_;
        bw_limited_frame_counter_.Add(bw_limited_resolution);
        if (bw_limited_resolution) {
          bw_resolutions_disabled_counter_.Add(disabled_streams);
        }
      }
    }
    encoded_frames_.erase(it);
  }
}

bool SendStatisticsProxy::UmaSamplesContainer::InsertEncodedFrame(
    const EncodedImage& encoded_frame,
    int simulcast_idx) {
  int64_t now_ms = clock_->TimeInMilliseconds();
  RemoveOld(now_ms);
  if (encoded_frames_.size() > kMaxEncodedFrameMapSize) {
    encoded_frames_.clear();
  }

  // Check for jump in timestamp.
  if (!encoded_frames_.empty()) {
    uint32_t oldest_timestamp = encoded_frames_.begin()->first;
    if (ForwardDiff(oldest_timestamp, encoded_frame.Timestamp()) >
        kMaxEncodedFrameTimestampDiff) {
      // Gap detected, clear frames to have a sequence where newest timestamp
      // is not too far away from oldest in order to distinguish old and new.
      encoded_frames_.clear();
    }
  }

  auto it = encoded_frames_.find(encoded_frame.Timestamp());
  if (it == encoded_frames_.end()) {
    // First frame with this timestamp.
    encoded_frames_.insert(
        std::make_pair(encoded_frame.Timestamp(),
                       Frame(now_ms, encoded_frame._encodedWidth,
                             encoded_frame._encodedHeight, simulcast_idx)));
    sent_fps_counter_.Add(1);
    return true;
  }

  it->second.max_width =
      std::max(it->second.max_width, encoded_frame._encodedWidth);
  it->second.max_height =
      std::max(it->second.max_height, encoded_frame._encodedHeight);
  it->second.max_simulcast_idx =
      std::max(it->second.max_simulcast_idx, simulcast_idx);
  return false;
}

void SendStatisticsProxy::UmaSamplesContainer::UpdateHistograms(
    const RtpConfig& rtp_config,
    const VideoSendStream::Stats& current_stats) {
  RTC_DCHECK(uma_prefix_ == kRealtimePrefix || uma_prefix_ == kScreenPrefix);
  const int kIndex = uma_prefix_ == kScreenPrefix ? 1 : 0;
  const int kMinRequiredPeriodicSamples = 6;
  char log_stream_buf[8 * 1024];
  rtc::SimpleStringBuilder log_stream(log_stream_buf);
  int in_width = input_width_counter_.Avg(kMinRequiredMetricsSamples);
  int in_height = input_height_counter_.Avg(kMinRequiredMetricsSamples);
  if (in_width != -1) {
    RTC_HISTOGRAMS_COUNTS_10000(kIndex, uma_prefix_ + "InputWidthInPixels",
                                in_width);
    RTC_HISTOGRAMS_COUNTS_10000(kIndex, uma_prefix_ + "InputHeightInPixels",
                                in_height);
    log_stream << uma_prefix_ << "InputWidthInPixels " << in_width << "\n"
               << uma_prefix_ << "InputHeightInPixels " << in_height << "\n";
  }
  AggregatedStats in_fps = input_fps_counter_.GetStats();
  if (in_fps.num_samples >= kMinRequiredPeriodicSamples) {
    RTC_HISTOGRAMS_COUNTS_100(kIndex, uma_prefix_ + "InputFramesPerSecond",
                              in_fps.average);
    log_stream << uma_prefix_ << "InputFramesPerSecond " << in_fps.ToString()
               << "\n";
  }

  int sent_width = sent_width_counter_.Avg(kMinRequiredMetricsSamples);
  int sent_height = sent_height_counter_.Avg(kMinRequiredMetricsSamples);
  if (sent_width != -1) {
    RTC_HISTOGRAMS_COUNTS_10000(kIndex, uma_prefix_ + "SentWidthInPixels",
                                sent_width);
    RTC_HISTOGRAMS_COUNTS_10000(kIndex, uma_prefix_ + "SentHeightInPixels",
                                sent_height);
    log_stream << uma_prefix_ << "SentWidthInPixels " << sent_width << "\n"
               << uma_prefix_ << "SentHeightInPixels " << sent_height << "\n";
  }
  AggregatedStats sent_fps = sent_fps_counter_.GetStats();
  if (sent_fps.num_samples >= kMinRequiredPeriodicSamples) {
    RTC_HISTOGRAMS_COUNTS_100(kIndex, uma_prefix_ + "SentFramesPerSecond",
                              sent_fps.average);
    log_stream << uma_prefix_ << "SentFramesPerSecond " << sent_fps.ToString()
               << "\n";
  }

  if (in_fps.num_samples > kMinRequiredPeriodicSamples &&
      sent_fps.num_samples >= kMinRequiredPeriodicSamples) {
    int in_fps_avg = in_fps.average;
    if (in_fps_avg > 0) {
      int sent_fps_avg = sent_fps.average;
      int sent_to_in_fps_ratio_percent =
          (100 * sent_fps_avg + in_fps_avg / 2) / in_fps_avg;
      // If reported period is small, it may happen that sent_fps is larger than
      // input_fps briefly on average. This should be treated as 100% sent to
      // input ratio.
      if (sent_to_in_fps_ratio_percent > 100)
        sent_to_in_fps_ratio_percent = 100;
      RTC_HISTOGRAMS_PERCENTAGE(kIndex,
                                uma_prefix_ + "SentToInputFpsRatioPercent",
                                sent_to_in_fps_ratio_percent);
      log_stream << uma_prefix_ << "SentToInputFpsRatioPercent "
                 << sent_to_in_fps_ratio_percent << "\n";
    }
  }

  int encode_ms = encode_time_counter_.Avg(kMinRequiredMetricsSamples);
  if (encode_ms != -1) {
    RTC_HISTOGRAMS_COUNTS_1000(kIndex, uma_prefix_ + "EncodeTimeInMs",
                               encode_ms);
    log_stream << uma_prefix_ << "EncodeTimeInMs " << encode_ms << "\n";
  }
  int key_frames_permille =
      key_frame_counter_.Permille(kMinRequiredMetricsSamples);
  if (key_frames_permille != -1) {
    RTC_HISTOGRAMS_COUNTS_1000(kIndex, uma_prefix_ + "KeyFramesSentInPermille",
                               key_frames_permille);
    log_stream << uma_prefix_ << "KeyFramesSentInPermille "
               << key_frames_permille << "\n";
  }
  int quality_limited =
      quality_limited_frame_counter_.Percent(kMinRequiredMetricsSamples);
  if (quality_limited != -1) {
    RTC_HISTOGRAMS_PERCENTAGE(kIndex,
                              uma_prefix_ + "QualityLimitedResolutionInPercent",
                              quality_limited);
    log_stream << uma_prefix_ << "QualityLimitedResolutionInPercent "
               << quality_limited << "\n";
  }
  int downscales = quality_downscales_counter_.Avg(kMinRequiredMetricsSamples);
  if (downscales != -1) {
    RTC_HISTOGRAMS_ENUMERATION(
        kIndex, uma_prefix_ + "QualityLimitedResolutionDownscales", downscales,
        20);
  }
  int cpu_limited =
      cpu_limited_frame_counter_.Percent(kMinRequiredMetricsSamples);
  if (cpu_limited != -1) {
    RTC_HISTOGRAMS_PERCENTAGE(
        kIndex, uma_prefix_ + "CpuLimitedResolutionInPercent", cpu_limited);
  }
  int bw_limited =
      bw_limited_frame_counter_.Percent(kMinRequiredMetricsSamples);
  if (bw_limited != -1) {
    RTC_HISTOGRAMS_PERCENTAGE(
        kIndex, uma_prefix_ + "BandwidthLimitedResolutionInPercent",
        bw_limited);
  }
  int num_disabled =
      bw_resolutions_disabled_counter_.Avg(kMinRequiredMetricsSamples);
  if (num_disabled != -1) {
    RTC_HISTOGRAMS_ENUMERATION(
        kIndex, uma_prefix_ + "BandwidthLimitedResolutionsDisabled",
        num_disabled, 10);
  }
  int delay_ms = delay_counter_.Avg(kMinRequiredMetricsSamples);
  if (delay_ms != -1)
    RTC_HISTOGRAMS_COUNTS_100000(kIndex, uma_prefix_ + "SendSideDelayInMs",
                                 delay_ms);

  int max_delay_ms = max_delay_counter_.Avg(kMinRequiredMetricsSamples);
  if (max_delay_ms != -1) {
    RTC_HISTOGRAMS_COUNTS_100000(kIndex, uma_prefix_ + "SendSideDelayMaxInMs",
                                 max_delay_ms);
  }

  for (const auto& it : qp_counters_) {
    int qp_vp8 = it.second.vp8.Avg(kMinRequiredMetricsSamples);
    if (qp_vp8 != -1) {
      int spatial_idx = it.first;
      if (spatial_idx == -1) {
        RTC_HISTOGRAMS_COUNTS_200(kIndex, uma_prefix_ + "Encoded.Qp.Vp8",
                                  qp_vp8);
      } else if (spatial_idx == 0) {
        RTC_HISTOGRAMS_COUNTS_200(kIndex, uma_prefix_ + "Encoded.Qp.Vp8.S0",
                                  qp_vp8);
      } else if (spatial_idx == 1) {
        RTC_HISTOGRAMS_COUNTS_200(kIndex, uma_prefix_ + "Encoded.Qp.Vp8.S1",
                                  qp_vp8);
      } else if (spatial_idx == 2) {
        RTC_HISTOGRAMS_COUNTS_200(kIndex, uma_prefix_ + "Encoded.Qp.Vp8.S2",
                                  qp_vp8);
      } else {
        RTC_LOG(LS_WARNING)
            << "QP stats not recorded for VP8 spatial idx " << spatial_idx;
      }
    }
    int qp_vp9 = it.second.vp9.Avg(kMinRequiredMetricsSamples);
    if (qp_vp9 != -1) {
      int spatial_idx = it.first;
      if (spatial_idx == -1) {
        RTC_HISTOGRAMS_COUNTS_500(kIndex, uma_prefix_ + "Encoded.Qp.Vp9",
                                  qp_vp9);
      } else if (spatial_idx == 0) {
        RTC_HISTOGRAMS_COUNTS_500(kIndex, uma_prefix_ + "Encoded.Qp.Vp9.S0",
                                  qp_vp9);
      } else if (spatial_idx == 1) {
        RTC_HISTOGRAMS_COUNTS_500(kIndex, uma_prefix_ + "Encoded.Qp.Vp9.S1",
                                  qp_vp9);
      } else if (spatial_idx == 2) {
        RTC_HISTOGRAMS_COUNTS_500(kIndex, uma_prefix_ + "Encoded.Qp.Vp9.S2",
                                  qp_vp9);
      } else {
        RTC_LOG(LS_WARNING)
            << "QP stats not recorded for VP9 spatial layer " << spatial_idx;
      }
    }
    int qp_h264 = it.second.h264.Avg(kMinRequiredMetricsSamples);
    if (qp_h264 != -1) {
      int spatial_idx = it.first;
      if (spatial_idx == -1) {
        RTC_HISTOGRAMS_COUNTS_200(kIndex, uma_prefix_ + "Encoded.Qp.H264",
                                  qp_h264);
      } else if (spatial_idx == 0) {
        RTC_HISTOGRAMS_COUNTS_200(kIndex, uma_prefix_ + "Encoded.Qp.H264.S0",
                                  qp_h264);
      } else if (spatial_idx == 1) {
        RTC_HISTOGRAMS_COUNTS_200(kIndex, uma_prefix_ + "Encoded.Qp.H264.S1",
                                  qp_h264);
      } else if (spatial_idx == 2) {
        RTC_HISTOGRAMS_COUNTS_200(kIndex, uma_prefix_ + "Encoded.Qp.H264.S2",
                                  qp_h264);
      } else {
        RTC_LOG(LS_WARNING)
            << "QP stats not recorded for H264 spatial idx " << spatial_idx;
      }
    }
  }

  if (first_rtp_stats_time_ms_ != -1) {
    quality_adapt_timer_.Stop(clock_->TimeInMilliseconds());
    int64_t elapsed_sec = quality_adapt_timer_.total_ms / 1000;
    if (elapsed_sec >= metrics::kMinRunTimeInSeconds) {
      int quality_changes = current_stats.number_of_quality_adapt_changes -
                            start_stats_.number_of_quality_adapt_changes;
      // Only base stats on changes during a call, discard initial changes.
      int initial_changes =
          initial_quality_changes_.down + initial_quality_changes_.up;
      if (initial_changes <= quality_changes)
        quality_changes -= initial_changes;
      RTC_HISTOGRAMS_COUNTS_100(kIndex,
                                uma_prefix_ + "AdaptChangesPerMinute.Quality",
                                quality_changes * 60 / elapsed_sec);
    }
    cpu_adapt_timer_.Stop(clock_->TimeInMilliseconds());
    elapsed_sec = cpu_adapt_timer_.total_ms / 1000;
    if (elapsed_sec >= metrics::kMinRunTimeInSeconds) {
      int cpu_changes = current_stats.number_of_cpu_adapt_changes -
                        start_stats_.number_of_cpu_adapt_changes;
      RTC_HISTOGRAMS_COUNTS_100(kIndex,
                                uma_prefix_ + "AdaptChangesPerMinute.Cpu",
                                cpu_changes * 60 / elapsed_sec);
    }
  }

  if (first_rtcp_stats_time_ms_ != -1) {
    int64_t elapsed_sec =
        (clock_->TimeInMilliseconds() - first_rtcp_stats_time_ms_) / 1000;
    if (elapsed_sec >= metrics::kMinRunTimeInSeconds) {
      int fraction_lost = report_block_stats_.FractionLostInPercent();
      if (fraction_lost != -1) {
        RTC_HISTOGRAMS_PERCENTAGE(
            kIndex, uma_prefix_ + "SentPacketsLostInPercent", fraction_lost);
        log_stream << uma_prefix_ << "SentPacketsLostInPercent "
                   << fraction_lost << "\n";
      }

      // The RTCP packet type counters, delivered via the
      // RtcpPacketTypeCounterObserver interface, are aggregates over the entire
      // life of the send stream and are not reset when switching content type.
      // For the purpose of these statistics though, we want new counts when
      // switching since we switch histogram name. On every reset of the
      // UmaSamplesContainer, we save the initial state of the counters, so that
      // we can calculate the delta here and aggregate over all ssrcs.
      RtcpPacketTypeCounter counters;
      for (uint32_t ssrc : rtp_config.ssrcs) {
        auto kv = current_stats.substreams.find(ssrc);
        if (kv == current_stats.substreams.end())
          continue;

        RtcpPacketTypeCounter stream_counters =
            kv->second.rtcp_packet_type_counts;
        kv = start_stats_.substreams.find(ssrc);
        if (kv != start_stats_.substreams.end())
          stream_counters.Subtract(kv->second.rtcp_packet_type_counts);

        counters.Add(stream_counters);
      }
      RTC_HISTOGRAMS_COUNTS_10000(kIndex,
                                  uma_prefix_ + "NackPacketsReceivedPerMinute",
                                  counters.nack_packets * 60 / elapsed_sec);
      RTC_HISTOGRAMS_COUNTS_10000(kIndex,
                                  uma_prefix_ + "FirPacketsReceivedPerMinute",
                                  counters.fir_packets * 60 / elapsed_sec);
      RTC_HISTOGRAMS_COUNTS_10000(kIndex,
                                  uma_prefix_ + "PliPacketsReceivedPerMinute",
                                  counters.pli_packets * 60 / elapsed_sec);
      if (counters.nack_requests > 0) {
        RTC_HISTOGRAMS_PERCENTAGE(
            kIndex, uma_prefix_ + "UniqueNackRequestsReceivedInPercent",
            counters.UniqueNackRequestsInPercent());
      }
    }
  }

  if (first_rtp_stats_time_ms_ != -1) {
    int64_t elapsed_sec =
        (clock_->TimeInMilliseconds() - first_rtp_stats_time_ms_) / 1000;
    if (elapsed_sec >= metrics::kMinRunTimeInSeconds) {
      RTC_HISTOGRAMS_COUNTS_100(kIndex, uma_prefix_ + "NumberOfPauseEvents",
                                target_rate_updates_.pause_resume_events);
      log_stream << uma_prefix_ << "NumberOfPauseEvents "
                 << target_rate_updates_.pause_resume_events << "\n";

      int paused_time_percent =
          paused_time_counter_.Percent(metrics::kMinRunTimeInSeconds * 1000);
      if (paused_time_percent != -1) {
        RTC_HISTOGRAMS_PERCENTAGE(kIndex, uma_prefix_ + "PausedTimeInPercent",
                                  paused_time_percent);
        log_stream << uma_prefix_ << "PausedTimeInPercent "
                   << paused_time_percent << "\n";
      }
    }
  }

  if (fallback_info_.is_possible) {
    // Double interval since there is some time before fallback may occur.
    const int kMinRunTimeMs = 2 * metrics::kMinRunTimeInSeconds * 1000;
    int64_t elapsed_ms = fallback_info_.elapsed_ms;
    int fallback_time_percent = fallback_active_counter_.Percent(kMinRunTimeMs);
    if (fallback_time_percent != -1 && elapsed_ms >= kMinRunTimeMs) {
      RTC_HISTOGRAMS_PERCENTAGE(
          kIndex, uma_prefix_ + "Encoder.ForcedSwFallbackTimeInPercent.Vp8",
          fallback_time_percent);
      RTC_HISTOGRAMS_COUNTS_100(
          kIndex, uma_prefix_ + "Encoder.ForcedSwFallbackChangesPerMinute.Vp8",
          fallback_info_.on_off_events * 60 / (elapsed_ms / 1000));
    }
  }

  AggregatedStats total_bytes_per_sec = total_byte_counter_.GetStats();
  if (total_bytes_per_sec.num_samples > kMinRequiredPeriodicSamples) {
    RTC_HISTOGRAMS_COUNTS_10000(kIndex, uma_prefix_ + "BitrateSentInKbps",
                                total_bytes_per_sec.average * 8 / 1000);
    log_stream << uma_prefix_ << "BitrateSentInBps "
               << total_bytes_per_sec.ToStringWithMultiplier(8) << "\n";
  }
  AggregatedStats media_bytes_per_sec = media_byte_counter_.GetStats();
  if (media_bytes_per_sec.num_samples > kMinRequiredPeriodicSamples) {
    RTC_HISTOGRAMS_COUNTS_10000(kIndex, uma_prefix_ + "MediaBitrateSentInKbps",
                                media_bytes_per_sec.average * 8 / 1000);
    log_stream << uma_prefix_ << "MediaBitrateSentInBps "
               << media_bytes_per_sec.ToStringWithMultiplier(8) << "\n";
  }
  AggregatedStats padding_bytes_per_sec = padding_byte_counter_.GetStats();
  if (padding_bytes_per_sec.num_samples > kMinRequiredPeriodicSamples) {
    RTC_HISTOGRAMS_COUNTS_10000(kIndex,
                                uma_prefix_ + "PaddingBitrateSentInKbps",
                                padding_bytes_per_sec.average * 8 / 1000);
    log_stream << uma_prefix_ << "PaddingBitrateSentInBps "
               << padding_bytes_per_sec.ToStringWithMultiplier(8) << "\n";
  }
  AggregatedStats retransmit_bytes_per_sec =
      retransmit_byte_counter_.GetStats();
  if (retransmit_bytes_per_sec.num_samples > kMinRequiredPeriodicSamples) {
    RTC_HISTOGRAMS_COUNTS_10000(kIndex,
                                uma_prefix_ + "RetransmittedBitrateSentInKbps",
                                retransmit_bytes_per_sec.average * 8 / 1000);
    log_stream << uma_prefix_ << "RetransmittedBitrateSentInBps "
               << retransmit_bytes_per_sec.ToStringWithMultiplier(8) << "\n";
  }
  if (!rtp_config.rtx.ssrcs.empty()) {
    AggregatedStats rtx_bytes_per_sec = rtx_byte_counter_.GetStats();
    int rtx_bytes_per_sec_avg = -1;
    if (rtx_bytes_per_sec.num_samples > kMinRequiredPeriodicSamples) {
      rtx_bytes_per_sec_avg = rtx_bytes_per_sec.average;
      log_stream << uma_prefix_ << "RtxBitrateSentInBps "
                 << rtx_bytes_per_sec.ToStringWithMultiplier(8) << "\n";
    } else if (total_bytes_per_sec.num_samples > kMinRequiredPeriodicSamples) {
      rtx_bytes_per_sec_avg = 0;  // RTX enabled but no RTX data sent, record 0.
    }
    if (rtx_bytes_per_sec_avg != -1) {
      RTC_HISTOGRAMS_COUNTS_10000(kIndex, uma_prefix_ + "RtxBitrateSentInKbps",
                                  rtx_bytes_per_sec_avg * 8 / 1000);
    }
  }
  if (rtp_config.flexfec.payload_type != -1 ||
      rtp_config.ulpfec.red_payload_type != -1) {
    AggregatedStats fec_bytes_per_sec = fec_byte_counter_.GetStats();
    if (fec_bytes_per_sec.num_samples > kMinRequiredPeriodicSamples) {
      RTC_HISTOGRAMS_COUNTS_10000(kIndex, uma_prefix_ + "FecBitrateSentInKbps",
                                  fec_bytes_per_sec.average * 8 / 1000);
      log_stream << uma_prefix_ << "FecBitrateSentInBps "
                 << fec_bytes_per_sec.ToStringWithMultiplier(8) << "\n";
    }
  }
  log_stream << "Frames encoded " << current_stats.frames_encoded << "\n"
             << uma_prefix_ << "DroppedFrames.Capturer "
             << current_stats.frames_dropped_by_capturer << "\n";
  RTC_HISTOGRAMS_COUNTS_1000(kIndex, uma_prefix_ + "DroppedFrames.Capturer",
                             current_stats.frames_dropped_by_capturer);
  log_stream << uma_prefix_ << "DroppedFrames.EncoderQueue "
             << current_stats.frames_dropped_by_encoder_queue << "\n";
  RTC_HISTOGRAMS_COUNTS_1000(kIndex, uma_prefix_ + "DroppedFrames.EncoderQueue",
                             current_stats.frames_dropped_by_encoder_queue);
  log_stream << uma_prefix_ << "DroppedFrames.Encoder "
             << current_stats.frames_dropped_by_encoder << "\n";
  RTC_HISTOGRAMS_COUNTS_1000(kIndex, uma_prefix_ + "DroppedFrames.Encoder",
                             current_stats.frames_dropped_by_encoder);
  log_stream << uma_prefix_ << "DroppedFrames.Ratelimiter "
             << current_stats.frames_dropped_by_rate_limiter << "\n";
  RTC_HISTOGRAMS_COUNTS_1000(kIndex, uma_prefix_ + "DroppedFrames.Ratelimiter",
                             current_stats.frames_dropped_by_rate_limiter);
  log_stream << uma_prefix_ << "DroppedFrames.CongestionWindow "
             << current_stats.frames_dropped_by_congestion_window;

  RTC_LOG(LS_INFO) << log_stream.str();
}

void SendStatisticsProxy::OnEncoderReconfigured(
    const VideoEncoderConfig& config,
    const std::vector<VideoStream>& streams) {
  // Called on VideoStreamEncoder's encoder_queue_.
  MutexLock lock(&mutex_);

  if (content_type_ != config.content_type) {
    uma_container_->UpdateHistograms(rtp_config_, stats_);
    uma_container_.reset(new UmaSamplesContainer(
        GetUmaPrefix(config.content_type), stats_, clock_));
    content_type_ = config.content_type;
  }
  uma_container_->encoded_frames_.clear();
  uma_container_->num_streams_ = streams.size();
  uma_container_->num_pixels_highest_stream_ =
      streams.empty() ? 0 : (streams.back().width * streams.back().height);
}

void SendStatisticsProxy::OnEncodedFrameTimeMeasured(int encode_time_ms,
                                                     int encode_usage_percent) {
  RTC_DCHECK_GE(encode_time_ms, 0);
  MutexLock lock(&mutex_);
  uma_container_->encode_time_counter_.Add(encode_time_ms);
  encode_time_.Apply(1.0f, encode_time_ms);
  stats_.avg_encode_time_ms = std::round(encode_time_.filtered());
  stats_.total_encode_time_ms += encode_time_ms;
  stats_.encode_usage_percent = encode_usage_percent;
}

void SendStatisticsProxy::OnSuspendChange(bool is_suspended) {
  int64_t now_ms = clock_->TimeInMilliseconds();
  MutexLock lock(&mutex_);
  stats_.suspended = is_suspended;
  if (is_suspended) {
    // Pause framerate (add min pause time since there may be frames/packets
    // that are not yet sent).
    const int64_t kMinMs = 500;
    uma_container_->input_fps_counter_.ProcessAndPauseForDuration(kMinMs);
    uma_container_->sent_fps_counter_.ProcessAndPauseForDuration(kMinMs);
    // Pause bitrate stats.
    uma_container_->total_byte_counter_.ProcessAndPauseForDuration(kMinMs);
    uma_container_->media_byte_counter_.ProcessAndPauseForDuration(kMinMs);
    uma_container_->rtx_byte_counter_.ProcessAndPauseForDuration(kMinMs);
    uma_container_->padding_byte_counter_.ProcessAndPauseForDuration(kMinMs);
    uma_container_->retransmit_byte_counter_.ProcessAndPauseForDuration(kMinMs);
    uma_container_->fec_byte_counter_.ProcessAndPauseForDuration(kMinMs);
    // Stop adaptation stats.
    uma_container_->cpu_adapt_timer_.Stop(now_ms);
    uma_container_->quality_adapt_timer_.Stop(now_ms);
  } else {
    // Start adaptation stats if scaling is enabled.
    if (adaptation_limitations_.MaskedCpuCounts()
            .resolution_adaptations.has_value())
      uma_container_->cpu_adapt_timer_.Start(now_ms);
    if (adaptation_limitations_.MaskedQualityCounts()
            .resolution_adaptations.has_value())
      uma_container_->quality_adapt_timer_.Start(now_ms);
    // Stop pause explicitly for stats that may be zero/not updated for some
    // time.
    uma_container_->rtx_byte_counter_.ProcessAndStopPause();
    uma_container_->padding_byte_counter_.ProcessAndStopPause();
    uma_container_->retransmit_byte_counter_.ProcessAndStopPause();
    uma_container_->fec_byte_counter_.ProcessAndStopPause();
  }
}

VideoSendStream::Stats SendStatisticsProxy::GetStats() {
  MutexLock lock(&mutex_);
  PurgeOldStats();
  stats_.input_frame_rate =
      uma_container_->input_frame_rate_tracker_.ComputeRate();
  stats_.frames =
      uma_container_->input_frame_rate_tracker_.TotalSampleCount();
  stats_.content_type =
      content_type_ == VideoEncoderConfig::ContentType::kRealtimeVideo
          ? VideoContentType::UNSPECIFIED
          : VideoContentType::SCREENSHARE;
  stats_.encode_frame_rate = round(encoded_frame_rate_tracker_.ComputeRate());
  stats_.media_bitrate_bps = media_byte_rate_tracker_.ComputeRate() * 8;
  stats_.quality_limitation_durations_ms =
      quality_limitation_reason_tracker_.DurationsMs();

  for (auto& substream : stats_.substreams) {
    uint32_t ssrc = substream.first;
    if (encoded_frame_rate_trackers_.count(ssrc) > 0) {
      substream.second.encode_frame_rate =
          encoded_frame_rate_trackers_[ssrc]->ComputeRate();
    }
  }
  return stats_;
}

void SendStatisticsProxy::PurgeOldStats() {
  int64_t old_stats_ms = clock_->TimeInMilliseconds() - kStatsTimeoutMs;
  for (std::map<uint32_t, VideoSendStream::StreamStats>::iterator it =
           stats_.substreams.begin();
       it != stats_.substreams.end(); ++it) {
    uint32_t ssrc = it->first;
    if (update_times_[ssrc].resolution_update_ms <= old_stats_ms) {
      it->second.width = 0;
      it->second.height = 0;
    }
  }
}

VideoSendStream::StreamStats* SendStatisticsProxy::GetStatsEntry(
    uint32_t ssrc) {
  std::map<uint32_t, VideoSendStream::StreamStats>::iterator it =
      stats_.substreams.find(ssrc);
  if (it != stats_.substreams.end())
    return &it->second;

  bool is_media = rtp_config_.IsMediaSsrc(ssrc);
  bool is_flexfec = rtp_config_.flexfec.payload_type != -1 &&
                    ssrc == rtp_config_.flexfec.ssrc;
  bool is_rtx = rtp_config_.IsRtxSsrc(ssrc);
  if (!is_media && !is_flexfec && !is_rtx)
    return nullptr;

  // Insert new entry and return ptr.
  VideoSendStream::StreamStats* entry = &stats_.substreams[ssrc];
  if (is_media) {
    entry->type = VideoSendStream::StreamStats::StreamType::kMedia;
  } else if (is_rtx) {
    entry->type = VideoSendStream::StreamStats::StreamType::kRtx;
  } else if (is_flexfec) {
    entry->type = VideoSendStream::StreamStats::StreamType::kFlexfec;
  } else {
    RTC_DCHECK_NOTREACHED();
  }
  switch (entry->type) {
    case VideoSendStream::StreamStats::StreamType::kMedia:
      break;
    case VideoSendStream::StreamStats::StreamType::kRtx:
      entry->referenced_media_ssrc =
          rtp_config_.GetMediaSsrcAssociatedWithRtxSsrc(ssrc);
      break;
    case VideoSendStream::StreamStats::StreamType::kFlexfec:
      entry->referenced_media_ssrc =
          rtp_config_.GetMediaSsrcAssociatedWithFlexfecSsrc(ssrc);
      break;
  }

  return entry;
}

void SendStatisticsProxy::OnInactiveSsrc(uint32_t ssrc) {
  MutexLock lock(&mutex_);
  VideoSendStream::StreamStats* stats = GetStatsEntry(ssrc);
  if (!stats)
    return;

  stats->total_bitrate_bps = 0;
  stats->retransmit_bitrate_bps = 0;
  stats->height = 0;
  stats->width = 0;
}

void SendStatisticsProxy::OnSetEncoderTargetRate(uint32_t bitrate_bps) {
  MutexLock lock(&mutex_);
  if (uma_container_->target_rate_updates_.last_ms == -1 && bitrate_bps == 0)
    return;  // Start on first non-zero bitrate, may initially be zero.

  int64_t now = clock_->TimeInMilliseconds();
  if (uma_container_->target_rate_updates_.last_ms != -1) {
    bool was_paused = stats_.target_media_bitrate_bps == 0;
    int64_t diff_ms = now - uma_container_->target_rate_updates_.last_ms;
    uma_container_->paused_time_counter_.Add(was_paused, diff_ms);

    // Use last to not include update when stream is stopped and video disabled.
    if (uma_container_->target_rate_updates_.last_paused_or_resumed)
      ++uma_container_->target_rate_updates_.pause_resume_events;

    // Check if video is paused/resumed.
    uma_container_->target_rate_updates_.last_paused_or_resumed =
        (bitrate_bps == 0) != was_paused;
  }
  uma_container_->target_rate_updates_.last_ms = now;

  stats_.target_media_bitrate_bps = bitrate_bps;
}

void SendStatisticsProxy::UpdateEncoderFallbackStats(
    const CodecSpecificInfo* codec_info,
    int pixels,
    int simulcast_index) {
  UpdateFallbackDisabledStats(codec_info, pixels, simulcast_index);

  if (!fallback_max_pixels_ || !uma_container_->fallback_info_.is_possible) {
    return;
  }

  if (!IsForcedFallbackPossible(codec_info, simulcast_index)) {
    uma_container_->fallback_info_.is_possible = false;
    return;
  }

  FallbackEncoderInfo* fallback_info = &uma_container_->fallback_info_;

  const int64_t now_ms = clock_->TimeInMilliseconds();
  bool is_active = fallback_info->is_active;
  if (encoder_changed_) {
    // Implementation changed.
    const bool last_was_vp8_software =
        encoder_changed_->previous_encoder_implementation == kVp8SwCodecName;
    is_active = encoder_changed_->new_encoder_implementation == kVp8SwCodecName;
    encoder_changed_.reset();
    if (!is_active && !last_was_vp8_software) {
      // First or not a VP8 SW change, update stats on next call.
      return;
    }
    if (is_active && (pixels > *fallback_max_pixels_)) {
      // Pixels should not be above `fallback_max_pixels_`. If above skip to
      // avoid fallbacks due to failure.
      fallback_info->is_possible = false;
      return;
    }
    stats_.has_entered_low_resolution = true;
    ++fallback_info->on_off_events;
  }

  if (fallback_info->last_update_ms) {
    int64_t diff_ms = now_ms - *(fallback_info->last_update_ms);
    // If the time diff since last update is greater than `max_frame_diff_ms`,
    // video is considered paused/muted and the change is not included.
    if (diff_ms < fallback_info->max_frame_diff_ms) {
      uma_container_->fallback_active_counter_.Add(fallback_info->is_active,
                                                   diff_ms);
      fallback_info->elapsed_ms += diff_ms;
    }
  }
  fallback_info->is_active = is_active;
  fallback_info->last_update_ms.emplace(now_ms);
}

void SendStatisticsProxy::UpdateFallbackDisabledStats(
    const CodecSpecificInfo* codec_info,
    int pixels,
    int simulcast_index) {
  if (!fallback_max_pixels_disabled_ ||
      !uma_container_->fallback_info_disabled_.is_possible ||
      stats_.has_entered_low_resolution) {
    return;
  }

  if (!IsForcedFallbackPossible(codec_info, simulcast_index) ||
      stats_.encoder_implementation_name == kVp8SwCodecName) {
    uma_container_->fallback_info_disabled_.is_possible = false;
    return;
  }

  if (pixels <= *fallback_max_pixels_disabled_ ||
      uma_container_->fallback_info_disabled_.min_pixel_limit_reached) {
    stats_.has_entered_low_resolution = true;
  }
}

void SendStatisticsProxy::OnMinPixelLimitReached() {
  MutexLock lock(&mutex_);
  uma_container_->fallback_info_disabled_.min_pixel_limit_reached = true;
}

void SendStatisticsProxy::OnSendEncodedImage(
    const EncodedImage& encoded_image,
    const CodecSpecificInfo* codec_info) {
  // Simulcast is used for VP8, H264 and Generic.
  int simulcast_idx =
      (codec_info && (codec_info->codecType == kVideoCodecVP8 ||
                      codec_info->codecType == kVideoCodecH264 ||
                      codec_info->codecType == kVideoCodecGeneric))
          ? encoded_image.SpatialIndex().value_or(0)
          : 0;

  MutexLock lock(&mutex_);
  ++stats_.frames_encoded;
  // The current encode frame rate is based on previously encoded frames.
  double encode_frame_rate = encoded_frame_rate_tracker_.ComputeRate();
  // We assume that less than 1 FPS is not a trustworthy estimate - perhaps we
  // just started encoding for the first time or after a pause. Assuming frame
  // rate is at least 1 FPS is conservative to avoid too large increments.
  if (encode_frame_rate < 1.0)
    encode_frame_rate = 1.0;
  double target_frame_size_bytes =
      stats_.target_media_bitrate_bps / (8.0 * encode_frame_rate);
  // `stats_.target_media_bitrate_bps` is set in
  // SendStatisticsProxy::OnSetEncoderTargetRate.
  stats_.total_encoded_bytes_target += round(target_frame_size_bytes);
  if (codec_info) {
    UpdateEncoderFallbackStats(
        codec_info, encoded_image._encodedWidth * encoded_image._encodedHeight,
        simulcast_idx);
  }

  if (static_cast<size_t>(simulcast_idx) >= rtp_config_.ssrcs.size()) {
    RTC_LOG(LS_ERROR) << "Encoded image outside simulcast range ("
                      << simulcast_idx << " >= " << rtp_config_.ssrcs.size()
                      << ").";
    return;
  }
  uint32_t ssrc = rtp_config_.ssrcs[simulcast_idx];

  VideoSendStream::StreamStats* stats = GetStatsEntry(ssrc);
  if (!stats)
    return;

  if (encoded_frame_rate_trackers_.count(ssrc) == 0) {
    encoded_frame_rate_trackers_[ssrc] =
        std::make_unique<rtc::RateTracker>(kBucketSizeMs, kBucketCount);
  }

  stats->frames_encoded++;
  stats->total_encode_time_ms += encoded_image.timing_.encode_finish_ms -
                                 encoded_image.timing_.encode_start_ms;
  // Report resolution of the top spatial layer.
  bool is_top_spatial_layer =
      codec_info == nullptr || codec_info->end_of_picture;

  if (!stats->width || !stats->height || is_top_spatial_layer) {
    stats->width = encoded_image._encodedWidth;
    stats->height = encoded_image._encodedHeight;
    update_times_[ssrc].resolution_update_ms = clock_->TimeInMilliseconds();
  }

  uma_container_->key_frame_counter_.Add(encoded_image._frameType ==
                                         VideoFrameType::kVideoFrameKey);

  if (encoded_image.qp_ != -1) {
    if (!stats->qp_sum)
      stats->qp_sum = 0;
    *stats->qp_sum += encoded_image.qp_;

    if (codec_info) {
      if (codec_info->codecType == kVideoCodecVP8) {
        int spatial_idx = (rtp_config_.ssrcs.size() == 1) ? -1 : simulcast_idx;
        uma_container_->qp_counters_[spatial_idx].vp8.Add(encoded_image.qp_);
      } else if (codec_info->codecType == kVideoCodecVP9) {
        int spatial_idx = encoded_image.SpatialIndex().value_or(-1);
        uma_container_->qp_counters_[spatial_idx].vp9.Add(encoded_image.qp_);
      } else if (codec_info->codecType == kVideoCodecH264) {
        int spatial_idx = (rtp_config_.ssrcs.size() == 1) ? -1 : simulcast_idx;
        uma_container_->qp_counters_[spatial_idx].h264.Add(encoded_image.qp_);
      }
    }
  }

  // If any of the simulcast streams have a huge frame, it should be counted
  // as a single difficult input frame.
  // https://w3c.github.io/webrtc-stats/#dom-rtcvideosenderstats-hugeframessent
  if (encoded_image.timing_.flags & VideoSendTiming::kTriggeredBySize) {
    ++stats->huge_frames_sent;
    if (!last_outlier_timestamp_ ||
        *last_outlier_timestamp_ < encoded_image.capture_time_ms_) {
      last_outlier_timestamp_.emplace(encoded_image.capture_time_ms_);
      ++stats_.huge_frames_sent;
    }
  }

  media_byte_rate_tracker_.AddSamples(encoded_image.size());

  if (uma_container_->InsertEncodedFrame(encoded_image, simulcast_idx)) {
    // First frame seen with this timestamp, track overall fps.
    encoded_frame_rate_tracker_.AddSamples(1);
  }
  // is_top_spatial_layer pertains only to SVC, will always be true for
  // simulcast.
  if (is_top_spatial_layer)
    encoded_frame_rate_trackers_[ssrc]->AddSamples(1);

  absl::optional<int> downscales =
      adaptation_limitations_.MaskedQualityCounts().resolution_adaptations;
  stats_.bw_limited_resolution |=
      (downscales.has_value() && downscales.value() > 0);

  if (downscales.has_value()) {
    uma_container_->quality_limited_frame_counter_.Add(downscales.value() > 0);
    if (downscales.value() > 0)
      uma_container_->quality_downscales_counter_.Add(downscales.value());
  }
}

void SendStatisticsProxy::OnEncoderImplementationChanged(
    EncoderImplementation implementation) {
  MutexLock lock(&mutex_);
  encoder_changed_ = EncoderChangeEvent{stats_.encoder_implementation_name,
                                        implementation.name};
  stats_.encoder_implementation_name = implementation.name;
  stats_.power_efficient_encoder = implementation.is_hardware_accelerated;
}

int SendStatisticsProxy::GetInputFrameRate() const {
  MutexLock lock(&mutex_);
  return round(uma_container_->input_frame_rate_tracker_.ComputeRate());
}

int SendStatisticsProxy::GetSendFrameRate() const {
  MutexLock lock(&mutex_);
  return round(encoded_frame_rate_tracker_.ComputeRate());
}

void SendStatisticsProxy::OnIncomingFrame(int width, int height) {
  MutexLock lock(&mutex_);
  uma_container_->input_frame_rate_tracker_.AddSamples(1);
  uma_container_->input_fps_counter_.Add(1);
  uma_container_->input_width_counter_.Add(width);
  uma_container_->input_height_counter_.Add(height);
  if (adaptation_limitations_.MaskedCpuCounts()
          .resolution_adaptations.has_value()) {
    uma_container_->cpu_limited_frame_counter_.Add(
        stats_.cpu_limited_resolution);
  }
  if (encoded_frame_rate_tracker_.TotalSampleCount() == 0) {
    // Set start time now instead of when first key frame is encoded to avoid a
    // too high initial estimate.
    encoded_frame_rate_tracker_.AddSamples(0);
  }
}

void SendStatisticsProxy::OnFrameDropped(DropReason reason) {
  MutexLock lock(&mutex_);
  switch (reason) {
    case DropReason::kSource:
      ++stats_.frames_dropped_by_capturer;
      break;
    case DropReason::kEncoderQueue:
      ++stats_.frames_dropped_by_encoder_queue;
      break;
    case DropReason::kEncoder:
      ++stats_.frames_dropped_by_encoder;
      break;
    case DropReason::kMediaOptimization:
      ++stats_.frames_dropped_by_rate_limiter;
      break;
    case DropReason::kCongestionWindow:
      ++stats_.frames_dropped_by_congestion_window;
      break;
  }
}

void SendStatisticsProxy::ClearAdaptationStats() {
  MutexLock lock(&mutex_);
  adaptation_limitations_.set_cpu_counts(VideoAdaptationCounters());
  adaptation_limitations_.set_quality_counts(VideoAdaptationCounters());
  UpdateAdaptationStats();
}

void SendStatisticsProxy::UpdateAdaptationSettings(
    VideoStreamEncoderObserver::AdaptationSettings cpu_settings,
    VideoStreamEncoderObserver::AdaptationSettings quality_settings) {
  MutexLock lock(&mutex_);
  adaptation_limitations_.UpdateMaskingSettings(cpu_settings, quality_settings);
  SetAdaptTimer(adaptation_limitations_.MaskedCpuCounts(),
                &uma_container_->cpu_adapt_timer_);
  SetAdaptTimer(adaptation_limitations_.MaskedQualityCounts(),
                &uma_container_->quality_adapt_timer_);
  UpdateAdaptationStats();
}

void SendStatisticsProxy::OnAdaptationChanged(
    VideoAdaptationReason reason,
    const VideoAdaptationCounters& cpu_counters,
    const VideoAdaptationCounters& quality_counters) {
  MutexLock lock(&mutex_);

  MaskedAdaptationCounts receiver =
      adaptation_limitations_.MaskedQualityCounts();
  adaptation_limitations_.set_cpu_counts(cpu_counters);
  adaptation_limitations_.set_quality_counts(quality_counters);
  switch (reason) {
    case VideoAdaptationReason::kCpu:
      ++stats_.number_of_cpu_adapt_changes;
      break;
    case VideoAdaptationReason::kQuality:
      TryUpdateInitialQualityResolutionAdaptUp(
          receiver.resolution_adaptations,
          adaptation_limitations_.MaskedQualityCounts().resolution_adaptations);
      ++stats_.number_of_quality_adapt_changes;
      break;
  }
  UpdateAdaptationStats();
}

void SendStatisticsProxy::UpdateAdaptationStats() {
  auto cpu_counts = adaptation_limitations_.MaskedCpuCounts();
  auto quality_counts = adaptation_limitations_.MaskedQualityCounts();

  bool is_cpu_limited = cpu_counts.resolution_adaptations > 0 ||
                        cpu_counts.num_framerate_reductions > 0;
  bool is_bandwidth_limited = quality_counts.resolution_adaptations > 0 ||
                              quality_counts.num_framerate_reductions > 0 ||
                              bw_limited_layers_ || internal_encoder_scaler_;
  if (is_bandwidth_limited) {
    // We may be both CPU limited and bandwidth limited at the same time but
    // there is no way to express this in standardized stats. Heuristically,
    // bandwidth is more likely to be a limiting factor than CPU, and more
    // likely to vary over time, so only when we aren't bandwidth limited do we
    // want to know about our CPU being the bottleneck.
    quality_limitation_reason_tracker_.SetReason(
        QualityLimitationReason::kBandwidth);
  } else if (is_cpu_limited) {
    quality_limitation_reason_tracker_.SetReason(QualityLimitationReason::kCpu);
  } else {
    quality_limitation_reason_tracker_.SetReason(
        QualityLimitationReason::kNone);
  }

  stats_.cpu_limited_resolution = cpu_counts.resolution_adaptations > 0;
  stats_.cpu_limited_framerate = cpu_counts.num_framerate_reductions > 0;
  stats_.bw_limited_resolution = quality_counts.resolution_adaptations > 0;
  stats_.bw_limited_framerate = quality_counts.num_framerate_reductions > 0;
  // If bitrate allocator has disabled some layers frame-rate or resolution are
  // limited depending on the encoder configuration.
  if (bw_limited_layers_) {
    switch (content_type_) {
      case VideoEncoderConfig::ContentType::kRealtimeVideo: {
        stats_.bw_limited_resolution = true;
        break;
      }
      case VideoEncoderConfig::ContentType::kScreen: {
        stats_.bw_limited_framerate = true;
        break;
      }
    }
  }
  if (internal_encoder_scaler_) {
    stats_.bw_limited_resolution = true;
  }

  stats_.quality_limitation_reason =
      quality_limitation_reason_tracker_.current_reason();

  // `stats_.quality_limitation_durations_ms` depends on the current time
  // when it is polled; it is updated in SendStatisticsProxy::GetStats().
}

void SendStatisticsProxy::OnBitrateAllocationUpdated(
    const VideoCodec& codec,
    const VideoBitrateAllocation& allocation) {
  int num_spatial_layers = 0;
  for (int i = 0; i < kMaxSpatialLayers; i++) {
    if (codec.spatialLayers[i].active) {
      num_spatial_layers++;
    }
  }
  int num_simulcast_streams = 0;
  for (int i = 0; i < kMaxSimulcastStreams; i++) {
    if (codec.simulcastStream[i].active) {
      num_simulcast_streams++;
    }
  }

  std::array<bool, kMaxSpatialLayers> spatial_layers;
  for (int i = 0; i < kMaxSpatialLayers; i++) {
    spatial_layers[i] = (allocation.GetSpatialLayerSum(i) > 0);
  }

  MutexLock lock(&mutex_);

  bw_limited_layers_ = allocation.is_bw_limited();
  UpdateAdaptationStats();

  if (spatial_layers != last_spatial_layer_use_) {
    // If the number of spatial layers has changed, the resolution change is
    // not due to quality limitations, it is because the configuration
    // changed.
    if (last_num_spatial_layers_ == num_spatial_layers &&
        last_num_simulcast_streams_ == num_simulcast_streams) {
      ++stats_.quality_limitation_resolution_changes;
    }
    last_spatial_layer_use_ = spatial_layers;
  }
  last_num_spatial_layers_ = num_spatial_layers;
  last_num_simulcast_streams_ = num_simulcast_streams;
}

// Informes observer if an internal encoder scaler has reduced video
// resolution or not. `is_scaled` is a flag indicating if the video is scaled
// down.
void SendStatisticsProxy::OnEncoderInternalScalerUpdate(bool is_scaled) {
  MutexLock lock(&mutex_);
  internal_encoder_scaler_ = is_scaled;
  UpdateAdaptationStats();
}

// TODO(asapersson): Include fps changes.
void SendStatisticsProxy::OnInitialQualityResolutionAdaptDown() {
  MutexLock lock(&mutex_);
  ++uma_container_->initial_quality_changes_.down;
}

void SendStatisticsProxy::TryUpdateInitialQualityResolutionAdaptUp(
    absl::optional<int> old_quality_downscales,
    absl::optional<int> updated_quality_downscales) {
  if (uma_container_->initial_quality_changes_.down == 0)
    return;

  if (old_quality_downscales.has_value() &&
      old_quality_downscales.value() > 0 &&
      updated_quality_downscales.value_or(-1) <
          old_quality_downscales.value()) {
    // Adapting up in quality.
    if (uma_container_->initial_quality_changes_.down >
        uma_container_->initial_quality_changes_.up) {
      ++uma_container_->initial_quality_changes_.up;
    }
  }
}

void SendStatisticsProxy::SetAdaptTimer(const MaskedAdaptationCounts& counts,
                                        StatsTimer* timer) {
  if (counts.resolution_adaptations || counts.num_framerate_reductions) {
    // Adaptation enabled.
    if (!stats_.suspended)
      timer->Start(clock_->TimeInMilliseconds());
    return;
  }
  timer->Stop(clock_->TimeInMilliseconds());
}

void SendStatisticsProxy::RtcpPacketTypesCounterUpdated(
    uint32_t ssrc,
    const RtcpPacketTypeCounter& packet_counter) {
  MutexLock lock(&mutex_);
  VideoSendStream::StreamStats* stats = GetStatsEntry(ssrc);
  if (!stats)
    return;

  stats->rtcp_packet_type_counts = packet_counter;
  if (uma_container_->first_rtcp_stats_time_ms_ == -1)
    uma_container_->first_rtcp_stats_time_ms_ = clock_->TimeInMilliseconds();
}

void SendStatisticsProxy::OnReportBlockDataUpdated(
    ReportBlockData report_block_data) {
  MutexLock lock(&mutex_);
  VideoSendStream::StreamStats* stats =
      GetStatsEntry(report_block_data.report_block().source_ssrc);
  if (!stats)
    return;
  const RTCPReportBlock& report_block = report_block_data.report_block();
  uma_container_->report_block_stats_.Store(
      /*ssrc=*/report_block.source_ssrc,
      /*packets_lost=*/report_block.packets_lost,
      /*extended_highest_sequence_number=*/
      report_block.extended_highest_sequence_number);

  stats->report_block_data = std::move(report_block_data);
}

void SendStatisticsProxy::DataCountersUpdated(
    const StreamDataCounters& counters,
    uint32_t ssrc) {
  MutexLock lock(&mutex_);
  VideoSendStream::StreamStats* stats = GetStatsEntry(ssrc);
  RTC_DCHECK(stats) << "DataCountersUpdated reported for unknown ssrc " << ssrc;

  if (stats->type == VideoSendStream::StreamStats::StreamType::kFlexfec) {
    // The same counters are reported for both the media ssrc and flexfec ssrc.
    // Bitrate stats are summed for all SSRCs. Use fec stats from media update.
    return;
  }

  stats->rtp_stats = counters;
  if (uma_container_->first_rtp_stats_time_ms_ == -1) {
    int64_t now_ms = clock_->TimeInMilliseconds();
    uma_container_->first_rtp_stats_time_ms_ = now_ms;
    uma_container_->cpu_adapt_timer_.Restart(now_ms);
    uma_container_->quality_adapt_timer_.Restart(now_ms);
  }

  uma_container_->total_byte_counter_.Set(counters.transmitted.TotalBytes(),
                                          ssrc);
  uma_container_->padding_byte_counter_.Set(counters.transmitted.padding_bytes,
                                            ssrc);
  uma_container_->retransmit_byte_counter_.Set(
      counters.retransmitted.TotalBytes(), ssrc);
  uma_container_->fec_byte_counter_.Set(counters.fec.TotalBytes(), ssrc);
  switch (stats->type) {
    case VideoSendStream::StreamStats::StreamType::kMedia:
      uma_container_->media_byte_counter_.Set(counters.MediaPayloadBytes(),
                                              ssrc);
      break;
    case VideoSendStream::StreamStats::StreamType::kRtx:
      uma_container_->rtx_byte_counter_.Set(counters.transmitted.TotalBytes(),
                                            ssrc);
      break;
    case VideoSendStream::StreamStats::StreamType::kFlexfec:
      break;
  }
}

void SendStatisticsProxy::Notify(uint32_t total_bitrate_bps,
                                 uint32_t retransmit_bitrate_bps,
                                 uint32_t ssrc) {
  MutexLock lock(&mutex_);
  VideoSendStream::StreamStats* stats = GetStatsEntry(ssrc);
  if (!stats)
    return;

  stats->total_bitrate_bps = total_bitrate_bps;
  stats->retransmit_bitrate_bps = retransmit_bitrate_bps;
}

void SendStatisticsProxy::FrameCountUpdated(const FrameCounts& frame_counts,
                                            uint32_t ssrc) {
  MutexLock lock(&mutex_);
  VideoSendStream::StreamStats* stats = GetStatsEntry(ssrc);
  if (!stats)
    return;

  stats->frame_counts = frame_counts;
}

void SendStatisticsProxy::SendSideDelayUpdated(int avg_delay_ms,
                                               int max_delay_ms,
                                               uint32_t ssrc) {
  MutexLock lock(&mutex_);
  VideoSendStream::StreamStats* stats = GetStatsEntry(ssrc);
  if (!stats)
    return;
  stats->avg_delay_ms = avg_delay_ms;
  stats->max_delay_ms = max_delay_ms;

  uma_container_->delay_counter_.Add(avg_delay_ms);
  uma_container_->max_delay_counter_.Add(max_delay_ms);
}

void SendStatisticsProxy::StatsTimer::Start(int64_t now_ms) {
  if (start_ms == -1)
    start_ms = now_ms;
}

void SendStatisticsProxy::StatsTimer::Stop(int64_t now_ms) {
  if (start_ms != -1) {
    total_ms += now_ms - start_ms;
    start_ms = -1;
  }
}

void SendStatisticsProxy::StatsTimer::Restart(int64_t now_ms) {
  total_ms = 0;
  if (start_ms != -1)
    start_ms = now_ms;
}

void SendStatisticsProxy::SampleCounter::Add(int sample) {
  sum += sample;
  ++num_samples;
}

int SendStatisticsProxy::SampleCounter::Avg(
    int64_t min_required_samples) const {
  if (num_samples < min_required_samples || num_samples == 0)
    return -1;
  return static_cast<int>((sum + (num_samples / 2)) / num_samples);
}

void SendStatisticsProxy::BoolSampleCounter::Add(bool sample) {
  if (sample)
    ++sum;
  ++num_samples;
}

void SendStatisticsProxy::BoolSampleCounter::Add(bool sample, int64_t count) {
  if (sample)
    sum += count;
  num_samples += count;
}
int SendStatisticsProxy::BoolSampleCounter::Percent(
    int64_t min_required_samples) const {
  return Fraction(min_required_samples, 100.0f);
}

int SendStatisticsProxy::BoolSampleCounter::Permille(
    int64_t min_required_samples) const {
  return Fraction(min_required_samples, 1000.0f);
}

int SendStatisticsProxy::BoolSampleCounter::Fraction(
    int64_t min_required_samples,
    float multiplier) const {
  if (num_samples < min_required_samples || num_samples == 0)
    return -1;
  return static_cast<int>((sum * multiplier / num_samples) + 0.5f);
}

SendStatisticsProxy::MaskedAdaptationCounts
SendStatisticsProxy::Adaptations::MaskedCpuCounts() const {
  return Mask(cpu_counts_, cpu_settings_);
}

SendStatisticsProxy::MaskedAdaptationCounts
SendStatisticsProxy::Adaptations::MaskedQualityCounts() const {
  return Mask(quality_counts_, quality_settings_);
}

void SendStatisticsProxy::Adaptations::set_cpu_counts(
    const VideoAdaptationCounters& cpu_counts) {
  cpu_counts_ = cpu_counts;
}

void SendStatisticsProxy::Adaptations::set_quality_counts(
    const VideoAdaptationCounters& quality_counts) {
  quality_counts_ = quality_counts;
}

VideoAdaptationCounters SendStatisticsProxy::Adaptations::cpu_counts() const {
  return cpu_counts_;
}

VideoAdaptationCounters SendStatisticsProxy::Adaptations::quality_counts()
    const {
  return quality_counts_;
}

void SendStatisticsProxy::Adaptations::UpdateMaskingSettings(
    VideoStreamEncoderObserver::AdaptationSettings cpu_settings,
    VideoStreamEncoderObserver::AdaptationSettings quality_settings) {
  cpu_settings_ = std::move(cpu_settings);
  quality_settings_ = std::move(quality_settings);
}

SendStatisticsProxy::MaskedAdaptationCounts
SendStatisticsProxy::Adaptations::Mask(
    const VideoAdaptationCounters& counters,
    const VideoStreamEncoderObserver::AdaptationSettings& settings) const {
  MaskedAdaptationCounts masked_counts;
  if (settings.resolution_scaling_enabled) {
    masked_counts.resolution_adaptations = counters.resolution_adaptations;
  }
  if (settings.framerate_scaling_enabled) {
    masked_counts.num_framerate_reductions = counters.fps_adaptations;
  }
  return masked_counts;
}

}  // namespace webrtc
