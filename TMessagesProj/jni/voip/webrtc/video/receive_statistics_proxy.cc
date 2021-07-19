/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/receive_statistics_proxy.h"

#include <algorithm>
#include <cmath>
#include <utility>

#include "modules/video_coding/include/video_codec_interface.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/time_utils.h"
#include "system_wrappers/include/clock.h"
#include "system_wrappers/include/field_trial.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {
namespace {
// Periodic time interval for processing samples for |freq_offset_counter_|.
const int64_t kFreqOffsetProcessIntervalMs = 40000;

// Configuration for bad call detection.
const int kBadCallMinRequiredSamples = 10;
const int kMinSampleLengthMs = 990;
const int kNumMeasurements = 10;
const int kNumMeasurementsVariance = kNumMeasurements * 1.5;
const float kBadFraction = 0.8f;
// For fps:
// Low means low enough to be bad, high means high enough to be good
const int kLowFpsThreshold = 12;
const int kHighFpsThreshold = 14;
// For qp and fps variance:
// Low means low enough to be good, high means high enough to be bad
const int kLowQpThresholdVp8 = 60;
const int kHighQpThresholdVp8 = 70;
const int kLowVarianceThreshold = 1;
const int kHighVarianceThreshold = 2;

// Some metrics are reported as a maximum over this period.
// This should be synchronized with a typical getStats polling interval in
// the clients.
const int kMovingMaxWindowMs = 1000;

// How large window we use to calculate the framerate/bitrate.
const int kRateStatisticsWindowSizeMs = 1000;

// Some sane ballpark estimate for maximum common value of inter-frame delay.
// Values below that will be stored explicitly in the array,
// values above - in the map.
const int kMaxCommonInterframeDelayMs = 500;

const char* UmaPrefixForContentType(VideoContentType content_type) {
  if (videocontenttypehelpers::IsScreenshare(content_type))
    return "WebRTC.Video.Screenshare";
  return "WebRTC.Video";
}

std::string UmaSuffixForContentType(VideoContentType content_type) {
  char ss_buf[1024];
  rtc::SimpleStringBuilder ss(ss_buf);
  int simulcast_id = videocontenttypehelpers::GetSimulcastId(content_type);
  if (simulcast_id > 0) {
    ss << ".S" << simulcast_id - 1;
  }
  int experiment_id = videocontenttypehelpers::GetExperimentId(content_type);
  if (experiment_id > 0) {
    ss << ".ExperimentGroup" << experiment_id - 1;
  }
  return ss.str();
}

}  // namespace

ReceiveStatisticsProxy::ReceiveStatisticsProxy(
    const VideoReceiveStream::Config* config,
    Clock* clock)
    : clock_(clock),
      config_(*config),
      start_ms_(clock->TimeInMilliseconds()),
      enable_decode_time_histograms_(
          !field_trial::IsEnabled("WebRTC-DecodeTimeHistogramsKillSwitch")),
      last_sample_time_(clock->TimeInMilliseconds()),
      fps_threshold_(kLowFpsThreshold,
                     kHighFpsThreshold,
                     kBadFraction,
                     kNumMeasurements),
      qp_threshold_(kLowQpThresholdVp8,
                    kHighQpThresholdVp8,
                    kBadFraction,
                    kNumMeasurements),
      variance_threshold_(kLowVarianceThreshold,
                          kHighVarianceThreshold,
                          kBadFraction,
                          kNumMeasurementsVariance),
      num_bad_states_(0),
      num_certain_states_(0),
      // 1000ms window, scale 1000 for ms to s.
      decode_fps_estimator_(1000, 1000),
      renders_fps_estimator_(1000, 1000),
      render_fps_tracker_(100, 10u),
      render_pixel_tracker_(100, 10u),
      video_quality_observer_(
          new VideoQualityObserver(VideoContentType::UNSPECIFIED)),
      interframe_delay_max_moving_(kMovingMaxWindowMs),
      freq_offset_counter_(clock, nullptr, kFreqOffsetProcessIntervalMs),
      avg_rtt_ms_(0),
      last_content_type_(VideoContentType::UNSPECIFIED),
      last_codec_type_(kVideoCodecVP8),
      num_delayed_frames_rendered_(0),
      sum_missed_render_deadline_ms_(0),
      timing_frame_info_counter_(kMovingMaxWindowMs) {
  decode_thread_.Detach();
  network_thread_.Detach();
  stats_.ssrc = config_.rtp.remote_ssrc;
}

void ReceiveStatisticsProxy::UpdateHistograms(
    absl::optional<int> fraction_lost,
    const StreamDataCounters& rtp_stats,
    const StreamDataCounters* rtx_stats) {
  // Not actually running on the decoder thread, but must be called after
  // DecoderThreadStopped, which detaches the thread checker. It is therefore
  // safe to access |qp_counters_|, which were updated on the decode thread
  // earlier.
  RTC_DCHECK_RUN_ON(&decode_thread_);

  MutexLock lock(&mutex_);

  char log_stream_buf[8 * 1024];
  rtc::SimpleStringBuilder log_stream(log_stream_buf);
  int stream_duration_sec = (clock_->TimeInMilliseconds() - start_ms_) / 1000;
  if (stats_.frame_counts.key_frames > 0 ||
      stats_.frame_counts.delta_frames > 0) {
    RTC_HISTOGRAM_COUNTS_100000("WebRTC.Video.ReceiveStreamLifetimeInSeconds",
                                stream_duration_sec);
    log_stream << "WebRTC.Video.ReceiveStreamLifetimeInSeconds "
               << stream_duration_sec << '\n';
  }

  log_stream << "Frames decoded " << stats_.frames_decoded << '\n';

  if (num_unique_frames_) {
    int num_dropped_frames = *num_unique_frames_ - stats_.frames_decoded;
    RTC_HISTOGRAM_COUNTS_1000("WebRTC.Video.DroppedFrames.Receiver",
                              num_dropped_frames);
    log_stream << "WebRTC.Video.DroppedFrames.Receiver " << num_dropped_frames
               << '\n';
  }

  if (fraction_lost && stream_duration_sec >= metrics::kMinRunTimeInSeconds) {
    RTC_HISTOGRAM_PERCENTAGE("WebRTC.Video.ReceivedPacketsLostInPercent",
                             *fraction_lost);
    log_stream << "WebRTC.Video.ReceivedPacketsLostInPercent " << *fraction_lost
               << '\n';
  }

  if (first_decoded_frame_time_ms_) {
    const int64_t elapsed_ms =
        (clock_->TimeInMilliseconds() - *first_decoded_frame_time_ms_);
    if (elapsed_ms >=
        metrics::kMinRunTimeInSeconds * rtc::kNumMillisecsPerSec) {
      int decoded_fps = static_cast<int>(
          (stats_.frames_decoded * 1000.0f / elapsed_ms) + 0.5f);
      RTC_HISTOGRAM_COUNTS_100("WebRTC.Video.DecodedFramesPerSecond",
                               decoded_fps);
      log_stream << "WebRTC.Video.DecodedFramesPerSecond " << decoded_fps
                 << '\n';

      const uint32_t frames_rendered = stats_.frames_rendered;
      if (frames_rendered > 0) {
        RTC_HISTOGRAM_PERCENTAGE("WebRTC.Video.DelayedFramesToRenderer",
                                 static_cast<int>(num_delayed_frames_rendered_ *
                                                  100 / frames_rendered));
        if (num_delayed_frames_rendered_ > 0) {
          RTC_HISTOGRAM_COUNTS_1000(
              "WebRTC.Video.DelayedFramesToRenderer_AvgDelayInMs",
              static_cast<int>(sum_missed_render_deadline_ms_ /
                               num_delayed_frames_rendered_));
        }
      }
    }
  }

  const int kMinRequiredSamples = 200;
  int samples = static_cast<int>(render_fps_tracker_.TotalSampleCount());
  if (samples >= kMinRequiredSamples) {
    int rendered_fps = round(render_fps_tracker_.ComputeTotalRate());
    RTC_HISTOGRAM_COUNTS_100("WebRTC.Video.RenderFramesPerSecond",
                             rendered_fps);
    log_stream << "WebRTC.Video.RenderFramesPerSecond " << rendered_fps << '\n';
    RTC_HISTOGRAM_COUNTS_100000(
        "WebRTC.Video.RenderSqrtPixelsPerSecond",
        round(render_pixel_tracker_.ComputeTotalRate()));
  }

  absl::optional<int> sync_offset_ms =
      sync_offset_counter_.Avg(kMinRequiredSamples);
  if (sync_offset_ms) {
    RTC_HISTOGRAM_COUNTS_10000("WebRTC.Video.AVSyncOffsetInMs",
                               *sync_offset_ms);
    log_stream << "WebRTC.Video.AVSyncOffsetInMs " << *sync_offset_ms << '\n';
  }
  AggregatedStats freq_offset_stats = freq_offset_counter_.GetStats();
  if (freq_offset_stats.num_samples > 0) {
    RTC_HISTOGRAM_COUNTS_10000("WebRTC.Video.RtpToNtpFreqOffsetInKhz",
                               freq_offset_stats.average);
    log_stream << "WebRTC.Video.RtpToNtpFreqOffsetInKhz "
               << freq_offset_stats.ToString() << '\n';
  }

  int num_total_frames =
      stats_.frame_counts.key_frames + stats_.frame_counts.delta_frames;
  if (num_total_frames >= kMinRequiredSamples) {
    int num_key_frames = stats_.frame_counts.key_frames;
    int key_frames_permille =
        (num_key_frames * 1000 + num_total_frames / 2) / num_total_frames;
    RTC_HISTOGRAM_COUNTS_1000("WebRTC.Video.KeyFramesReceivedInPermille",
                              key_frames_permille);
    log_stream << "WebRTC.Video.KeyFramesReceivedInPermille "
               << key_frames_permille << '\n';
  }

  absl::optional<int> qp = qp_counters_.vp8.Avg(kMinRequiredSamples);
  if (qp) {
    RTC_HISTOGRAM_COUNTS_200("WebRTC.Video.Decoded.Vp8.Qp", *qp);
    log_stream << "WebRTC.Video.Decoded.Vp8.Qp " << *qp << '\n';
  }
  absl::optional<int> decode_ms = decode_time_counter_.Avg(kMinRequiredSamples);
  if (decode_ms) {
    RTC_HISTOGRAM_COUNTS_1000("WebRTC.Video.DecodeTimeInMs", *decode_ms);
    log_stream << "WebRTC.Video.DecodeTimeInMs " << *decode_ms << '\n';
  }
  absl::optional<int> jb_delay_ms =
      jitter_buffer_delay_counter_.Avg(kMinRequiredSamples);
  if (jb_delay_ms) {
    RTC_HISTOGRAM_COUNTS_10000("WebRTC.Video.JitterBufferDelayInMs",
                               *jb_delay_ms);
    log_stream << "WebRTC.Video.JitterBufferDelayInMs " << *jb_delay_ms << '\n';
  }

  absl::optional<int> target_delay_ms =
      target_delay_counter_.Avg(kMinRequiredSamples);
  if (target_delay_ms) {
    RTC_HISTOGRAM_COUNTS_10000("WebRTC.Video.TargetDelayInMs",
                               *target_delay_ms);
    log_stream << "WebRTC.Video.TargetDelayInMs " << *target_delay_ms << '\n';
  }
  absl::optional<int> current_delay_ms =
      current_delay_counter_.Avg(kMinRequiredSamples);
  if (current_delay_ms) {
    RTC_HISTOGRAM_COUNTS_10000("WebRTC.Video.CurrentDelayInMs",
                               *current_delay_ms);
    log_stream << "WebRTC.Video.CurrentDelayInMs " << *current_delay_ms << '\n';
  }
  absl::optional<int> delay_ms = delay_counter_.Avg(kMinRequiredSamples);
  if (delay_ms)
    RTC_HISTOGRAM_COUNTS_10000("WebRTC.Video.OnewayDelayInMs", *delay_ms);

  // Aggregate content_specific_stats_ by removing experiment or simulcast
  // information;
  std::map<VideoContentType, ContentSpecificStats> aggregated_stats;
  for (const auto& it : content_specific_stats_) {
    // Calculate simulcast specific metrics (".S0" ... ".S2" suffixes).
    VideoContentType content_type = it.first;
    if (videocontenttypehelpers::GetSimulcastId(content_type) > 0) {
      // Aggregate on experiment id.
      videocontenttypehelpers::SetExperimentId(&content_type, 0);
      aggregated_stats[content_type].Add(it.second);
    }
    // Calculate experiment specific metrics (".ExperimentGroup[0-7]" suffixes).
    content_type = it.first;
    if (videocontenttypehelpers::GetExperimentId(content_type) > 0) {
      // Aggregate on simulcast id.
      videocontenttypehelpers::SetSimulcastId(&content_type, 0);
      aggregated_stats[content_type].Add(it.second);
    }
    // Calculate aggregated metrics (no suffixes. Aggregated on everything).
    content_type = it.first;
    videocontenttypehelpers::SetSimulcastId(&content_type, 0);
    videocontenttypehelpers::SetExperimentId(&content_type, 0);
    aggregated_stats[content_type].Add(it.second);
  }

  for (const auto& it : aggregated_stats) {
    // For the metric Foo we report the following slices:
    // WebRTC.Video.Foo,
    // WebRTC.Video.Screenshare.Foo,
    // WebRTC.Video.Foo.S[0-3],
    // WebRTC.Video.Foo.ExperimentGroup[0-7],
    // WebRTC.Video.Screenshare.Foo.S[0-3],
    // WebRTC.Video.Screenshare.Foo.ExperimentGroup[0-7].
    auto content_type = it.first;
    auto stats = it.second;
    std::string uma_prefix = UmaPrefixForContentType(content_type);
    std::string uma_suffix = UmaSuffixForContentType(content_type);
    // Metrics can be sliced on either simulcast id or experiment id but not
    // both.
    RTC_DCHECK(videocontenttypehelpers::GetExperimentId(content_type) == 0 ||
               videocontenttypehelpers::GetSimulcastId(content_type) == 0);

    absl::optional<int> e2e_delay_ms =
        stats.e2e_delay_counter.Avg(kMinRequiredSamples);
    if (e2e_delay_ms) {
      RTC_HISTOGRAM_COUNTS_SPARSE_10000(
          uma_prefix + ".EndToEndDelayInMs" + uma_suffix, *e2e_delay_ms);
      log_stream << uma_prefix << ".EndToEndDelayInMs" << uma_suffix << " "
                 << *e2e_delay_ms << '\n';
    }
    absl::optional<int> e2e_delay_max_ms = stats.e2e_delay_counter.Max();
    if (e2e_delay_max_ms && e2e_delay_ms) {
      RTC_HISTOGRAM_COUNTS_SPARSE_100000(
          uma_prefix + ".EndToEndDelayMaxInMs" + uma_suffix, *e2e_delay_max_ms);
      log_stream << uma_prefix << ".EndToEndDelayMaxInMs" << uma_suffix << " "
                 << *e2e_delay_max_ms << '\n';
    }
    absl::optional<int> interframe_delay_ms =
        stats.interframe_delay_counter.Avg(kMinRequiredSamples);
    if (interframe_delay_ms) {
      RTC_HISTOGRAM_COUNTS_SPARSE_10000(
          uma_prefix + ".InterframeDelayInMs" + uma_suffix,
          *interframe_delay_ms);
      log_stream << uma_prefix << ".InterframeDelayInMs" << uma_suffix << " "
                 << *interframe_delay_ms << '\n';
    }
    absl::optional<int> interframe_delay_max_ms =
        stats.interframe_delay_counter.Max();
    if (interframe_delay_max_ms && interframe_delay_ms) {
      RTC_HISTOGRAM_COUNTS_SPARSE_10000(
          uma_prefix + ".InterframeDelayMaxInMs" + uma_suffix,
          *interframe_delay_max_ms);
      log_stream << uma_prefix << ".InterframeDelayMaxInMs" << uma_suffix << " "
                 << *interframe_delay_max_ms << '\n';
    }

    absl::optional<uint32_t> interframe_delay_95p_ms =
        stats.interframe_delay_percentiles.GetPercentile(0.95f);
    if (interframe_delay_95p_ms && interframe_delay_ms != -1) {
      RTC_HISTOGRAM_COUNTS_SPARSE_10000(
          uma_prefix + ".InterframeDelay95PercentileInMs" + uma_suffix,
          *interframe_delay_95p_ms);
      log_stream << uma_prefix << ".InterframeDelay95PercentileInMs"
                 << uma_suffix << " " << *interframe_delay_95p_ms << '\n';
    }

    absl::optional<int> width = stats.received_width.Avg(kMinRequiredSamples);
    if (width) {
      RTC_HISTOGRAM_COUNTS_SPARSE_10000(
          uma_prefix + ".ReceivedWidthInPixels" + uma_suffix, *width);
      log_stream << uma_prefix << ".ReceivedWidthInPixels" << uma_suffix << " "
                 << *width << '\n';
    }

    absl::optional<int> height = stats.received_height.Avg(kMinRequiredSamples);
    if (height) {
      RTC_HISTOGRAM_COUNTS_SPARSE_10000(
          uma_prefix + ".ReceivedHeightInPixels" + uma_suffix, *height);
      log_stream << uma_prefix << ".ReceivedHeightInPixels" << uma_suffix << " "
                 << *height << '\n';
    }

    if (content_type != VideoContentType::UNSPECIFIED) {
      // Don't report these 3 metrics unsliced, as more precise variants
      // are reported separately in this method.
      float flow_duration_sec = stats.flow_duration_ms / 1000.0;
      if (flow_duration_sec >= metrics::kMinRunTimeInSeconds) {
        int media_bitrate_kbps = static_cast<int>(stats.total_media_bytes * 8 /
                                                  flow_duration_sec / 1000);
        RTC_HISTOGRAM_COUNTS_SPARSE_10000(
            uma_prefix + ".MediaBitrateReceivedInKbps" + uma_suffix,
            media_bitrate_kbps);
        log_stream << uma_prefix << ".MediaBitrateReceivedInKbps" << uma_suffix
                   << " " << media_bitrate_kbps << '\n';
      }

      int num_total_frames =
          stats.frame_counts.key_frames + stats.frame_counts.delta_frames;
      if (num_total_frames >= kMinRequiredSamples) {
        int num_key_frames = stats.frame_counts.key_frames;
        int key_frames_permille =
            (num_key_frames * 1000 + num_total_frames / 2) / num_total_frames;
        RTC_HISTOGRAM_COUNTS_SPARSE_1000(
            uma_prefix + ".KeyFramesReceivedInPermille" + uma_suffix,
            key_frames_permille);
        log_stream << uma_prefix << ".KeyFramesReceivedInPermille" << uma_suffix
                   << " " << key_frames_permille << '\n';
      }

      absl::optional<int> qp = stats.qp_counter.Avg(kMinRequiredSamples);
      if (qp) {
        RTC_HISTOGRAM_COUNTS_SPARSE_200(
            uma_prefix + ".Decoded.Vp8.Qp" + uma_suffix, *qp);
        log_stream << uma_prefix << ".Decoded.Vp8.Qp" << uma_suffix << " "
                   << *qp << '\n';
      }
    }
  }

  StreamDataCounters rtp_rtx_stats = rtp_stats;
  if (rtx_stats)
    rtp_rtx_stats.Add(*rtx_stats);
  int64_t elapsed_sec =
      rtp_rtx_stats.TimeSinceFirstPacketInMs(clock_->TimeInMilliseconds()) /
      1000;
  if (elapsed_sec >= metrics::kMinRunTimeInSeconds) {
    RTC_HISTOGRAM_COUNTS_10000(
        "WebRTC.Video.BitrateReceivedInKbps",
        static_cast<int>(rtp_rtx_stats.transmitted.TotalBytes() * 8 /
                         elapsed_sec / 1000));
    int media_bitrate_kbs = static_cast<int>(rtp_stats.MediaPayloadBytes() * 8 /
                                             elapsed_sec / 1000);
    RTC_HISTOGRAM_COUNTS_10000("WebRTC.Video.MediaBitrateReceivedInKbps",
                               media_bitrate_kbs);
    log_stream << "WebRTC.Video.MediaBitrateReceivedInKbps "
               << media_bitrate_kbs << '\n';
    RTC_HISTOGRAM_COUNTS_10000(
        "WebRTC.Video.PaddingBitrateReceivedInKbps",
        static_cast<int>(rtp_rtx_stats.transmitted.padding_bytes * 8 /
                         elapsed_sec / 1000));
    RTC_HISTOGRAM_COUNTS_10000(
        "WebRTC.Video.RetransmittedBitrateReceivedInKbps",
        static_cast<int>(rtp_rtx_stats.retransmitted.TotalBytes() * 8 /
                         elapsed_sec / 1000));
    if (rtx_stats) {
      RTC_HISTOGRAM_COUNTS_10000(
          "WebRTC.Video.RtxBitrateReceivedInKbps",
          static_cast<int>(rtx_stats->transmitted.TotalBytes() * 8 /
                           elapsed_sec / 1000));
    }
    const RtcpPacketTypeCounter& counters = stats_.rtcp_packet_type_counts;
    RTC_HISTOGRAM_COUNTS_10000("WebRTC.Video.NackPacketsSentPerMinute",
                               counters.nack_packets * 60 / elapsed_sec);
    RTC_HISTOGRAM_COUNTS_10000("WebRTC.Video.FirPacketsSentPerMinute",
                               counters.fir_packets * 60 / elapsed_sec);
    RTC_HISTOGRAM_COUNTS_10000("WebRTC.Video.PliPacketsSentPerMinute",
                               counters.pli_packets * 60 / elapsed_sec);
    if (counters.nack_requests > 0) {
      RTC_HISTOGRAM_PERCENTAGE("WebRTC.Video.UniqueNackRequestsSentInPercent",
                               counters.UniqueNackRequestsInPercent());
    }
  }

  if (num_certain_states_ >= kBadCallMinRequiredSamples) {
    RTC_HISTOGRAM_PERCENTAGE("WebRTC.Video.BadCall.Any",
                             100 * num_bad_states_ / num_certain_states_);
  }
  absl::optional<double> fps_fraction =
      fps_threshold_.FractionHigh(kBadCallMinRequiredSamples);
  if (fps_fraction) {
    RTC_HISTOGRAM_PERCENTAGE("WebRTC.Video.BadCall.FrameRate",
                             static_cast<int>(100 * (1 - *fps_fraction)));
  }
  absl::optional<double> variance_fraction =
      variance_threshold_.FractionHigh(kBadCallMinRequiredSamples);
  if (variance_fraction) {
    RTC_HISTOGRAM_PERCENTAGE("WebRTC.Video.BadCall.FrameRateVariance",
                             static_cast<int>(100 * *variance_fraction));
  }
  absl::optional<double> qp_fraction =
      qp_threshold_.FractionHigh(kBadCallMinRequiredSamples);
  if (qp_fraction) {
    RTC_HISTOGRAM_PERCENTAGE("WebRTC.Video.BadCall.Qp",
                             static_cast<int>(100 * *qp_fraction));
  }

  RTC_LOG(LS_INFO) << log_stream.str();
  video_quality_observer_->UpdateHistograms();
}

void ReceiveStatisticsProxy::QualitySample() {
  int64_t now = clock_->TimeInMilliseconds();
  if (last_sample_time_ + kMinSampleLengthMs > now)
    return;

  double fps =
      render_fps_tracker_.ComputeRateForInterval(now - last_sample_time_);
  absl::optional<int> qp = qp_sample_.Avg(1);

  bool prev_fps_bad = !fps_threshold_.IsHigh().value_or(true);
  bool prev_qp_bad = qp_threshold_.IsHigh().value_or(false);
  bool prev_variance_bad = variance_threshold_.IsHigh().value_or(false);
  bool prev_any_bad = prev_fps_bad || prev_qp_bad || prev_variance_bad;

  fps_threshold_.AddMeasurement(static_cast<int>(fps));
  if (qp)
    qp_threshold_.AddMeasurement(*qp);
  absl::optional<double> fps_variance_opt = fps_threshold_.CalculateVariance();
  double fps_variance = fps_variance_opt.value_or(0);
  if (fps_variance_opt) {
    variance_threshold_.AddMeasurement(static_cast<int>(fps_variance));
  }

  bool fps_bad = !fps_threshold_.IsHigh().value_or(true);
  bool qp_bad = qp_threshold_.IsHigh().value_or(false);
  bool variance_bad = variance_threshold_.IsHigh().value_or(false);
  bool any_bad = fps_bad || qp_bad || variance_bad;

  if (!prev_any_bad && any_bad) {
    RTC_LOG(LS_INFO) << "Bad call (any) start: " << now;
  } else if (prev_any_bad && !any_bad) {
    RTC_LOG(LS_INFO) << "Bad call (any) end: " << now;
  }

  if (!prev_fps_bad && fps_bad) {
    RTC_LOG(LS_INFO) << "Bad call (fps) start: " << now;
  } else if (prev_fps_bad && !fps_bad) {
    RTC_LOG(LS_INFO) << "Bad call (fps) end: " << now;
  }

  if (!prev_qp_bad && qp_bad) {
    RTC_LOG(LS_INFO) << "Bad call (qp) start: " << now;
  } else if (prev_qp_bad && !qp_bad) {
    RTC_LOG(LS_INFO) << "Bad call (qp) end: " << now;
  }

  if (!prev_variance_bad && variance_bad) {
    RTC_LOG(LS_INFO) << "Bad call (variance) start: " << now;
  } else if (prev_variance_bad && !variance_bad) {
    RTC_LOG(LS_INFO) << "Bad call (variance) end: " << now;
  }

  RTC_LOG(LS_VERBOSE) << "SAMPLE: sample_length: " << (now - last_sample_time_)
                      << " fps: " << fps << " fps_bad: " << fps_bad
                      << " qp: " << qp.value_or(-1) << " qp_bad: " << qp_bad
                      << " variance_bad: " << variance_bad
                      << " fps_variance: " << fps_variance;

  last_sample_time_ = now;
  qp_sample_.Reset();

  if (fps_threshold_.IsHigh() || variance_threshold_.IsHigh() ||
      qp_threshold_.IsHigh()) {
    if (any_bad)
      ++num_bad_states_;
    ++num_certain_states_;
  }
}

void ReceiveStatisticsProxy::UpdateFramerate(int64_t now_ms) const {
  int64_t old_frames_ms = now_ms - kRateStatisticsWindowSizeMs;
  while (!frame_window_.empty() &&
         frame_window_.begin()->first < old_frames_ms) {
    frame_window_.erase(frame_window_.begin());
  }

  size_t framerate =
      (frame_window_.size() * 1000 + 500) / kRateStatisticsWindowSizeMs;
  stats_.network_frame_rate = static_cast<int>(framerate);
}

void ReceiveStatisticsProxy::UpdateDecodeTimeHistograms(
    int width,
    int height,
    int decode_time_ms) const {
  bool is_4k = (width == 3840 || width == 4096) && height == 2160;
  bool is_hd = width == 1920 && height == 1080;
  // Only update histograms for 4k/HD and VP9/H264.
  if ((is_4k || is_hd) && (last_codec_type_ == kVideoCodecVP9 ||
                           last_codec_type_ == kVideoCodecH264)) {
    const std::string kDecodeTimeUmaPrefix =
        "WebRTC.Video.DecodeTimePerFrameInMs.";

    // Each histogram needs its own line for it to not be reused in the wrong
    // way when the format changes.
    if (last_codec_type_ == kVideoCodecVP9) {
      bool is_sw_decoder =
          stats_.decoder_implementation_name.compare(0, 6, "libvpx") == 0;
      if (is_4k) {
        if (is_sw_decoder)
          RTC_HISTOGRAM_COUNTS_1000(kDecodeTimeUmaPrefix + "Vp9.4k.Sw",
                                    decode_time_ms);
        else
          RTC_HISTOGRAM_COUNTS_1000(kDecodeTimeUmaPrefix + "Vp9.4k.Hw",
                                    decode_time_ms);
      } else {
        if (is_sw_decoder)
          RTC_HISTOGRAM_COUNTS_1000(kDecodeTimeUmaPrefix + "Vp9.Hd.Sw",
                                    decode_time_ms);
        else
          RTC_HISTOGRAM_COUNTS_1000(kDecodeTimeUmaPrefix + "Vp9.Hd.Hw",
                                    decode_time_ms);
      }
    } else {
      bool is_sw_decoder =
          stats_.decoder_implementation_name.compare(0, 6, "FFmpeg") == 0;
      if (is_4k) {
        if (is_sw_decoder)
          RTC_HISTOGRAM_COUNTS_1000(kDecodeTimeUmaPrefix + "H264.4k.Sw",
                                    decode_time_ms);
        else
          RTC_HISTOGRAM_COUNTS_1000(kDecodeTimeUmaPrefix + "H264.4k.Hw",
                                    decode_time_ms);

      } else {
        if (is_sw_decoder)
          RTC_HISTOGRAM_COUNTS_1000(kDecodeTimeUmaPrefix + "H264.Hd.Sw",
                                    decode_time_ms);
        else
          RTC_HISTOGRAM_COUNTS_1000(kDecodeTimeUmaPrefix + "H264.Hd.Hw",
                                    decode_time_ms);
      }
    }
  }
}

absl::optional<int64_t>
ReceiveStatisticsProxy::GetCurrentEstimatedPlayoutNtpTimestampMs(
    int64_t now_ms) const {
  if (!last_estimated_playout_ntp_timestamp_ms_ ||
      !last_estimated_playout_time_ms_) {
    return absl::nullopt;
  }
  int64_t elapsed_ms = now_ms - *last_estimated_playout_time_ms_;
  return *last_estimated_playout_ntp_timestamp_ms_ + elapsed_ms;
}

VideoReceiveStream::Stats ReceiveStatisticsProxy::GetStats() const {
  MutexLock lock(&mutex_);
  // Get current frame rates here, as only updating them on new frames prevents
  // us from ever correctly displaying frame rate of 0.
  int64_t now_ms = clock_->TimeInMilliseconds();
  UpdateFramerate(now_ms);
  stats_.render_frame_rate = renders_fps_estimator_.Rate(now_ms).value_or(0);
  stats_.decode_frame_rate = decode_fps_estimator_.Rate(now_ms).value_or(0);
  stats_.interframe_delay_max_ms =
      interframe_delay_max_moving_.Max(now_ms).value_or(-1);
  stats_.freeze_count = video_quality_observer_->NumFreezes();
  stats_.pause_count = video_quality_observer_->NumPauses();
  stats_.total_freezes_duration_ms =
      video_quality_observer_->TotalFreezesDurationMs();
  stats_.total_pauses_duration_ms =
      video_quality_observer_->TotalPausesDurationMs();
  stats_.total_frames_duration_ms =
      video_quality_observer_->TotalFramesDurationMs();
  stats_.sum_squared_frame_durations =
      video_quality_observer_->SumSquaredFrameDurationsSec();
  stats_.content_type = last_content_type_;
  stats_.timing_frame_info = timing_frame_info_counter_.Max(now_ms);
  stats_.jitter_buffer_delay_seconds =
      static_cast<double>(current_delay_counter_.Sum(1).value_or(0)) /
      rtc::kNumMillisecsPerSec;
  stats_.jitter_buffer_emitted_count = current_delay_counter_.NumSamples();
  stats_.estimated_playout_ntp_timestamp_ms =
      GetCurrentEstimatedPlayoutNtpTimestampMs(now_ms);
  return stats_;
}

void ReceiveStatisticsProxy::OnIncomingPayloadType(int payload_type) {
  MutexLock lock(&mutex_);
  stats_.current_payload_type = payload_type;
}

void ReceiveStatisticsProxy::OnDecoderImplementationName(
    const char* implementation_name) {
  MutexLock lock(&mutex_);
  stats_.decoder_implementation_name = implementation_name;
}

void ReceiveStatisticsProxy::OnFrameBufferTimingsUpdated(
    int max_decode_ms,
    int current_delay_ms,
    int target_delay_ms,
    int jitter_buffer_ms,
    int min_playout_delay_ms,
    int render_delay_ms) {
  MutexLock lock(&mutex_);
  stats_.max_decode_ms = max_decode_ms;
  stats_.current_delay_ms = current_delay_ms;
  stats_.target_delay_ms = target_delay_ms;
  stats_.jitter_buffer_ms = jitter_buffer_ms;
  stats_.min_playout_delay_ms = min_playout_delay_ms;
  stats_.render_delay_ms = render_delay_ms;
  jitter_buffer_delay_counter_.Add(jitter_buffer_ms);
  target_delay_counter_.Add(target_delay_ms);
  current_delay_counter_.Add(current_delay_ms);
  // Network delay (rtt/2) + target_delay_ms (jitter delay + decode time +
  // render delay).
  delay_counter_.Add(target_delay_ms + avg_rtt_ms_ / 2);
}

void ReceiveStatisticsProxy::OnUniqueFramesCounted(int num_unique_frames) {
  MutexLock lock(&mutex_);
  num_unique_frames_.emplace(num_unique_frames);
}

void ReceiveStatisticsProxy::OnTimingFrameInfoUpdated(
    const TimingFrameInfo& info) {
  MutexLock lock(&mutex_);
  if (info.flags != VideoSendTiming::kInvalid) {
    int64_t now_ms = clock_->TimeInMilliseconds();
    timing_frame_info_counter_.Add(info, now_ms);
  }

  // Measure initial decoding latency between the first frame arriving and the
  // first frame being decoded.
  if (!first_frame_received_time_ms_.has_value()) {
    first_frame_received_time_ms_ = info.receive_finish_ms;
  }
  if (stats_.first_frame_received_to_decoded_ms == -1 &&
      first_decoded_frame_time_ms_) {
    stats_.first_frame_received_to_decoded_ms =
        *first_decoded_frame_time_ms_ - *first_frame_received_time_ms_;
  }
}

void ReceiveStatisticsProxy::RtcpPacketTypesCounterUpdated(
    uint32_t ssrc,
    const RtcpPacketTypeCounter& packet_counter) {
  MutexLock lock(&mutex_);
  if (stats_.ssrc != ssrc)
    return;
  stats_.rtcp_packet_type_counts = packet_counter;
}

void ReceiveStatisticsProxy::OnCname(uint32_t ssrc, absl::string_view cname) {
  MutexLock lock(&mutex_);
  // TODO(pbos): Handle both local and remote ssrcs here and RTC_DCHECK that we
  // receive stats from one of them.
  if (stats_.ssrc != ssrc)
    return;
  stats_.c_name = std::string(cname);
}

void ReceiveStatisticsProxy::OnDecodedFrame(const VideoFrame& frame,
                                            absl::optional<uint8_t> qp,
                                            int32_t decode_time_ms,
                                            VideoContentType content_type) {
  MutexLock lock(&mutex_);

  uint64_t now_ms = clock_->TimeInMilliseconds();

  if (videocontenttypehelpers::IsScreenshare(content_type) !=
      videocontenttypehelpers::IsScreenshare(last_content_type_)) {
    // Reset the quality observer if content type is switched. But first report
    // stats for the previous part of the call.
    video_quality_observer_->UpdateHistograms();
    video_quality_observer_.reset(new VideoQualityObserver(content_type));
  }

  video_quality_observer_->OnDecodedFrame(frame, qp, last_codec_type_);

  ContentSpecificStats* content_specific_stats =
      &content_specific_stats_[content_type];
  ++stats_.frames_decoded;
  if (qp) {
    if (!stats_.qp_sum) {
      if (stats_.frames_decoded != 1) {
        RTC_LOG(LS_WARNING)
            << "Frames decoded was not 1 when first qp value was received.";
      }
      stats_.qp_sum = 0;
    }
    *stats_.qp_sum += *qp;
    content_specific_stats->qp_counter.Add(*qp);
  } else if (stats_.qp_sum) {
    RTC_LOG(LS_WARNING)
        << "QP sum was already set and no QP was given for a frame.";
    stats_.qp_sum.reset();
  }
  decode_time_counter_.Add(decode_time_ms);
  stats_.decode_ms = decode_time_ms;
  stats_.total_decode_time_ms += decode_time_ms;
  if (enable_decode_time_histograms_) {
    UpdateDecodeTimeHistograms(frame.width(), frame.height(), decode_time_ms);
  }

  last_content_type_ = content_type;
  decode_fps_estimator_.Update(1, now_ms);
  if (last_decoded_frame_time_ms_) {
    int64_t interframe_delay_ms = now_ms - *last_decoded_frame_time_ms_;
    RTC_DCHECK_GE(interframe_delay_ms, 0);
    double interframe_delay = interframe_delay_ms / 1000.0;
    stats_.total_inter_frame_delay += interframe_delay;
    stats_.total_squared_inter_frame_delay +=
        interframe_delay * interframe_delay;
    interframe_delay_max_moving_.Add(interframe_delay_ms, now_ms);
    content_specific_stats->interframe_delay_counter.Add(interframe_delay_ms);
    content_specific_stats->interframe_delay_percentiles.Add(
        interframe_delay_ms);
    content_specific_stats->flow_duration_ms += interframe_delay_ms;
  }
  if (stats_.frames_decoded == 1) {
    first_decoded_frame_time_ms_.emplace(now_ms);
  }
  last_decoded_frame_time_ms_.emplace(now_ms);
}

void ReceiveStatisticsProxy::OnRenderedFrame(const VideoFrame& frame) {
  int width = frame.width();
  int height = frame.height();
  RTC_DCHECK_GT(width, 0);
  RTC_DCHECK_GT(height, 0);
  int64_t now_ms = clock_->TimeInMilliseconds();
  MutexLock lock(&mutex_);

  video_quality_observer_->OnRenderedFrame(frame, now_ms);

  ContentSpecificStats* content_specific_stats =
      &content_specific_stats_[last_content_type_];
  renders_fps_estimator_.Update(1, now_ms);
  ++stats_.frames_rendered;
  stats_.width = width;
  stats_.height = height;
  render_fps_tracker_.AddSamples(1);
  render_pixel_tracker_.AddSamples(sqrt(width * height));
  content_specific_stats->received_width.Add(width);
  content_specific_stats->received_height.Add(height);

  // Consider taking stats_.render_delay_ms into account.
  const int64_t time_until_rendering_ms = frame.render_time_ms() - now_ms;
  if (time_until_rendering_ms < 0) {
    sum_missed_render_deadline_ms_ += -time_until_rendering_ms;
    ++num_delayed_frames_rendered_;
  }

  if (frame.ntp_time_ms() > 0) {
    int64_t delay_ms = clock_->CurrentNtpInMilliseconds() - frame.ntp_time_ms();
    if (delay_ms >= 0) {
      content_specific_stats->e2e_delay_counter.Add(delay_ms);
    }
  }
  QualitySample();
}

void ReceiveStatisticsProxy::OnSyncOffsetUpdated(int64_t video_playout_ntp_ms,
                                                 int64_t sync_offset_ms,
                                                 double estimated_freq_khz) {
  MutexLock lock(&mutex_);
  sync_offset_counter_.Add(std::abs(sync_offset_ms));
  stats_.sync_offset_ms = sync_offset_ms;
  last_estimated_playout_ntp_timestamp_ms_ = video_playout_ntp_ms;
  last_estimated_playout_time_ms_ = clock_->TimeInMilliseconds();

  const double kMaxFreqKhz = 10000.0;
  int offset_khz = kMaxFreqKhz;
  // Should not be zero or negative. If so, report max.
  if (estimated_freq_khz < kMaxFreqKhz && estimated_freq_khz > 0.0)
    offset_khz = static_cast<int>(std::fabs(estimated_freq_khz - 90.0) + 0.5);

  freq_offset_counter_.Add(offset_khz);
}

void ReceiveStatisticsProxy::OnCompleteFrame(bool is_keyframe,
                                             size_t size_bytes,
                                             VideoContentType content_type) {
  MutexLock lock(&mutex_);
  if (is_keyframe) {
    ++stats_.frame_counts.key_frames;
  } else {
    ++stats_.frame_counts.delta_frames;
  }

  // Content type extension is set only for keyframes and should be propagated
  // for all the following delta frames. Here we may receive frames out of order
  // and miscategorise some delta frames near the layer switch.
  // This may slightly offset calculated bitrate and keyframes permille metrics.
  VideoContentType propagated_content_type =
      is_keyframe ? content_type : last_content_type_;

  ContentSpecificStats* content_specific_stats =
      &content_specific_stats_[propagated_content_type];

  content_specific_stats->total_media_bytes += size_bytes;
  if (is_keyframe) {
    ++content_specific_stats->frame_counts.key_frames;
  } else {
    ++content_specific_stats->frame_counts.delta_frames;
  }

  int64_t now_ms = clock_->TimeInMilliseconds();
  frame_window_.insert(std::make_pair(now_ms, size_bytes));
  UpdateFramerate(now_ms);
}

void ReceiveStatisticsProxy::OnDroppedFrames(uint32_t frames_dropped) {
  MutexLock lock(&mutex_);
  stats_.frames_dropped += frames_dropped;
}

void ReceiveStatisticsProxy::OnPreDecode(VideoCodecType codec_type, int qp) {
  RTC_DCHECK_RUN_ON(&decode_thread_);
  MutexLock lock(&mutex_);
  last_codec_type_ = codec_type;
  if (last_codec_type_ == kVideoCodecVP8 && qp != -1) {
    qp_counters_.vp8.Add(qp);
    qp_sample_.Add(qp);
  }
}

void ReceiveStatisticsProxy::OnStreamInactive() {
  // TODO(sprang): Figure out any other state that should be reset.

  MutexLock lock(&mutex_);
  // Don't report inter-frame delay if stream was paused.
  last_decoded_frame_time_ms_.reset();
  video_quality_observer_->OnStreamInactive();
}

void ReceiveStatisticsProxy::OnRttUpdate(int64_t avg_rtt_ms,
                                         int64_t max_rtt_ms) {
  MutexLock lock(&mutex_);
  avg_rtt_ms_ = avg_rtt_ms;
}

void ReceiveStatisticsProxy::DecoderThreadStarting() {
  RTC_DCHECK_RUN_ON(&main_thread_);
}

void ReceiveStatisticsProxy::DecoderThreadStopped() {
  RTC_DCHECK_RUN_ON(&main_thread_);
  decode_thread_.Detach();
}

ReceiveStatisticsProxy::ContentSpecificStats::ContentSpecificStats()
    : interframe_delay_percentiles(kMaxCommonInterframeDelayMs) {}

ReceiveStatisticsProxy::ContentSpecificStats::~ContentSpecificStats() = default;

void ReceiveStatisticsProxy::ContentSpecificStats::Add(
    const ContentSpecificStats& other) {
  e2e_delay_counter.Add(other.e2e_delay_counter);
  interframe_delay_counter.Add(other.interframe_delay_counter);
  flow_duration_ms += other.flow_duration_ms;
  total_media_bytes += other.total_media_bytes;
  received_height.Add(other.received_height);
  received_width.Add(other.received_width);
  qp_counter.Add(other.qp_counter);
  frame_counts.key_frames += other.frame_counts.key_frames;
  frame_counts.delta_frames += other.frame_counts.delta_frames;
  interframe_delay_percentiles.Add(other.interframe_delay_percentiles);
}
}  // namespace webrtc
