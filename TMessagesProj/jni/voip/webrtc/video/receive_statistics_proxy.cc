/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
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
#include "rtc_base/thread.h"
#include "rtc_base/time_utils.h"
#include "system_wrappers/include/clock.h"
#include "system_wrappers/include/metrics.h"
#include "video/video_receive_stream2.h"

namespace webrtc {
namespace internal {
namespace {
// Periodic time interval for processing samples for `freq_offset_counter_`.
const int64_t kFreqOffsetProcessIntervalMs = 40000;

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

// TODO(https://bugs.webrtc.org/11572): Workaround for an issue with some
// rtc::Thread instances and/or implementations that don't register as the
// current task queue.
bool IsCurrentTaskQueueOrThread(TaskQueueBase* task_queue) {
  if (task_queue->IsCurrent())
    return true;

  rtc::Thread* current_thread = rtc::ThreadManager::Instance()->CurrentThread();
  if (!current_thread)
    return false;

  return static_cast<TaskQueueBase*>(current_thread) == task_queue;
}

}  // namespace

ReceiveStatisticsProxy::ReceiveStatisticsProxy(uint32_t remote_ssrc,
                                               Clock* clock,
                                               TaskQueueBase* worker_thread)
    : clock_(clock),
      start_ms_(clock->TimeInMilliseconds()),
      remote_ssrc_(remote_ssrc),
      // 1000ms window, scale 1000 for ms to s.
      decode_fps_estimator_(1000, 1000),
      renders_fps_estimator_(1000, 1000),
      render_fps_tracker_(100, 10u),
      render_pixel_tracker_(100, 10u),
      video_quality_observer_(new VideoQualityObserver()),
      interframe_delay_max_moving_(kMovingMaxWindowMs),
      freq_offset_counter_(clock, nullptr, kFreqOffsetProcessIntervalMs),
      last_content_type_(VideoContentType::UNSPECIFIED),
      last_codec_type_(kVideoCodecVP8),
      num_delayed_frames_rendered_(0),
      sum_missed_render_deadline_ms_(0),
      timing_frame_info_counter_(kMovingMaxWindowMs),
      worker_thread_(worker_thread) {
  RTC_DCHECK(worker_thread);
  decode_queue_.Detach();
  incoming_render_queue_.Detach();
  stats_.ssrc = remote_ssrc_;
}

ReceiveStatisticsProxy::~ReceiveStatisticsProxy() {
  RTC_DCHECK_RUN_ON(&main_thread_);
}

void ReceiveStatisticsProxy::UpdateHistograms(
    absl::optional<int> fraction_lost,
    const StreamDataCounters& rtp_stats,
    const StreamDataCounters* rtx_stats) {
  RTC_DCHECK_RUN_ON(&main_thread_);

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
      jitter_delay_counter_.Avg(kMinRequiredSamples);
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
  absl::optional<int> delay_ms = oneway_delay_counter_.Avg(kMinRequiredSamples);
  if (delay_ms)
    RTC_HISTOGRAM_COUNTS_10000("WebRTC.Video.OnewayDelayInMs", *delay_ms);

  // Aggregate content_specific_stats_ by removing experiment or simulcast
  // information;
  std::map<VideoContentType, ContentSpecificStats> aggregated_stats;
  for (const auto& it : content_specific_stats_) {
    // Calculate simulcast specific metrics (".S0" ... ".S2" suffixes).
    VideoContentType content_type = it.first;
    // Calculate aggregated metrics (no suffixes. Aggregated on everything).
    content_type = it.first;
    aggregated_stats[content_type].Add(it.second);
  }

  for (const auto& it : aggregated_stats) {
    // For the metric Foo we report the following slices:
    // WebRTC.Video.Foo,
    // WebRTC.Video.Screenshare.Foo,
    auto content_type = it.first;
    auto stats = it.second;
    std::string uma_prefix = UmaPrefixForContentType(content_type);

    absl::optional<int> e2e_delay_ms =
        stats.e2e_delay_counter.Avg(kMinRequiredSamples);
    if (e2e_delay_ms) {
      RTC_HISTOGRAM_COUNTS_SPARSE_10000(uma_prefix + ".EndToEndDelayInMs",
                                        *e2e_delay_ms);
      log_stream << uma_prefix << ".EndToEndDelayInMs"
                 << " " << *e2e_delay_ms << '\n';
    }
    absl::optional<int> e2e_delay_max_ms = stats.e2e_delay_counter.Max();
    if (e2e_delay_max_ms && e2e_delay_ms) {
      RTC_HISTOGRAM_COUNTS_SPARSE_100000(uma_prefix + ".EndToEndDelayMaxInMs",
                                         *e2e_delay_max_ms);
      log_stream << uma_prefix << ".EndToEndDelayMaxInMs"
                 << " " << *e2e_delay_max_ms << '\n';
    }
    absl::optional<int> interframe_delay_ms =
        stats.interframe_delay_counter.Avg(kMinRequiredSamples);
    if (interframe_delay_ms) {
      RTC_HISTOGRAM_COUNTS_SPARSE_10000(uma_prefix + ".InterframeDelayInMs",
                                        *interframe_delay_ms);
      log_stream << uma_prefix << ".InterframeDelayInMs"
                 << " " << *interframe_delay_ms << '\n';
    }
    absl::optional<int> interframe_delay_max_ms =
        stats.interframe_delay_counter.Max();
    if (interframe_delay_max_ms && interframe_delay_ms) {
      RTC_HISTOGRAM_COUNTS_SPARSE_10000(uma_prefix + ".InterframeDelayMaxInMs",
                                        *interframe_delay_max_ms);
      log_stream << uma_prefix << ".InterframeDelayMaxInMs"
                 << " " << *interframe_delay_max_ms << '\n';
    }

    absl::optional<uint32_t> interframe_delay_95p_ms =
        stats.interframe_delay_percentiles.GetPercentile(0.95f);
    if (interframe_delay_95p_ms && interframe_delay_ms != -1) {
      RTC_HISTOGRAM_COUNTS_SPARSE_10000(
          uma_prefix + ".InterframeDelay95PercentileInMs",
          *interframe_delay_95p_ms);
      log_stream << uma_prefix << ".InterframeDelay95PercentileInMs"
                 << " " << *interframe_delay_95p_ms << '\n';
    }

    absl::optional<int> width = stats.received_width.Avg(kMinRequiredSamples);
    if (width) {
      RTC_HISTOGRAM_COUNTS_SPARSE_10000(uma_prefix + ".ReceivedWidthInPixels",
                                        *width);
      log_stream << uma_prefix << ".ReceivedWidthInPixels"
                 << " " << *width << '\n';
    }

    absl::optional<int> height = stats.received_height.Avg(kMinRequiredSamples);
    if (height) {
      RTC_HISTOGRAM_COUNTS_SPARSE_10000(uma_prefix + ".ReceivedHeightInPixels",
                                        *height);
      log_stream << uma_prefix << ".ReceivedHeightInPixels"
                 << " " << *height << '\n';
    }

    if (content_type != VideoContentType::UNSPECIFIED) {
      // Don't report these 3 metrics unsliced, as more precise variants
      // are reported separately in this method.
      float flow_duration_sec = stats.flow_duration_ms / 1000.0;
      if (flow_duration_sec >= metrics::kMinRunTimeInSeconds) {
        int media_bitrate_kbps = static_cast<int>(stats.total_media_bytes * 8 /
                                                  flow_duration_sec / 1000);
        RTC_HISTOGRAM_COUNTS_SPARSE_10000(
            uma_prefix + ".MediaBitrateReceivedInKbps", media_bitrate_kbps);
        log_stream << uma_prefix << ".MediaBitrateReceivedInKbps"
                   << " " << media_bitrate_kbps << '\n';
      }

      int num_total_frames =
          stats.frame_counts.key_frames + stats.frame_counts.delta_frames;
      if (num_total_frames >= kMinRequiredSamples) {
        int num_key_frames = stats.frame_counts.key_frames;
        int key_frames_permille =
            (num_key_frames * 1000 + num_total_frames / 2) / num_total_frames;
        RTC_HISTOGRAM_COUNTS_SPARSE_1000(
            uma_prefix + ".KeyFramesReceivedInPermille", key_frames_permille);
        log_stream << uma_prefix << ".KeyFramesReceivedInPermille"
                   << " " << key_frames_permille << '\n';
      }

      absl::optional<int> qp = stats.qp_counter.Avg(kMinRequiredSamples);
      if (qp) {
        RTC_HISTOGRAM_COUNTS_SPARSE_200(uma_prefix + ".Decoded.Vp8.Qp", *qp);
        log_stream << uma_prefix << ".Decoded.Vp8.Qp"
                   << " " << *qp << '\n';
      }
    }
  }

  StreamDataCounters rtp_rtx_stats = rtp_stats;
  if (rtx_stats)
    rtp_rtx_stats.Add(*rtx_stats);

  TimeDelta elapsed = rtp_rtx_stats.TimeSinceFirstPacket(clock_->CurrentTime());
  if (elapsed >= TimeDelta::Seconds(metrics::kMinRunTimeInSeconds)) {
    int64_t elapsed_sec = elapsed.seconds();
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

  RTC_LOG(LS_INFO) << log_stream.str();
  video_quality_observer_->UpdateHistograms(
      videocontenttypehelpers::IsScreenshare(last_content_type_));
}

void ReceiveStatisticsProxy::UpdateFramerate(int64_t now_ms) const {
  RTC_DCHECK_RUN_ON(&main_thread_);

  int64_t old_frames_ms = now_ms - kRateStatisticsWindowSizeMs;
  while (!frame_window_.empty() &&
         frame_window_.begin()->first < old_frames_ms) {
    frame_window_.erase(frame_window_.begin());
  }

  size_t framerate =
      (frame_window_.size() * 1000 + 500) / kRateStatisticsWindowSizeMs;

  stats_.network_frame_rate = static_cast<int>(framerate);
}

absl::optional<int64_t>
ReceiveStatisticsProxy::GetCurrentEstimatedPlayoutNtpTimestampMs(
    int64_t now_ms) const {
  RTC_DCHECK_RUN_ON(&main_thread_);
  if (!last_estimated_playout_ntp_timestamp_ms_ ||
      !last_estimated_playout_time_ms_) {
    return absl::nullopt;
  }
  int64_t elapsed_ms = now_ms - *last_estimated_playout_time_ms_;
  return *last_estimated_playout_ntp_timestamp_ms_ + elapsed_ms;
}

VideoReceiveStreamInterface::Stats ReceiveStatisticsProxy::GetStats() const {
  RTC_DCHECK_RUN_ON(&main_thread_);

  // Like VideoReceiveStreamInterface::GetStats, called on the worker thread
  // from StatsCollector::ExtractMediaInfo via worker_thread()->BlockingCall().
  // WebRtcVideoChannel::GetStats(), GetVideoReceiverInfo.

  // Get current frame rates here, as only updating them on new frames prevents
  // us from ever correctly displaying frame rate of 0.
  int64_t now_ms = clock_->TimeInMilliseconds();
  UpdateFramerate(now_ms);

  stats_.render_frame_rate = renders_fps_estimator_.Rate(now_ms).value_or(0);
  stats_.decode_frame_rate = decode_fps_estimator_.Rate(now_ms).value_or(0);

  if (last_decoded_frame_time_ms_) {
    // Avoid using a newer timestamp than might be pending for decoded frames.
    // If we do use now_ms, we might roll the max window to a value that is
    // higher than that of a decoded frame timestamp that we haven't yet
    // captured the data for (i.e. pending call to OnDecodedFrame).
    stats_.interframe_delay_max_ms =
        interframe_delay_max_moving_.Max(*last_decoded_frame_time_ms_)
            .value_or(-1);
  } else {
    // We're paused. Avoid changing the state of `interframe_delay_max_moving_`.
    stats_.interframe_delay_max_ms = -1;
  }

  stats_.freeze_count = video_quality_observer_->NumFreezes();
  stats_.pause_count = video_quality_observer_->NumPauses();
  stats_.total_freezes_duration_ms =
      video_quality_observer_->TotalFreezesDurationMs();
  stats_.total_pauses_duration_ms =
      video_quality_observer_->TotalPausesDurationMs();
  stats_.total_inter_frame_delay =
      static_cast<double>(video_quality_observer_->TotalFramesDurationMs()) /
      rtc::kNumMillisecsPerSec;
  stats_.total_squared_inter_frame_delay =
      video_quality_observer_->SumSquaredFrameDurationsSec();

  stats_.content_type = last_content_type_;
  stats_.timing_frame_info = timing_frame_info_counter_.Max(now_ms);
  stats_.estimated_playout_ntp_timestamp_ms =
      GetCurrentEstimatedPlayoutNtpTimestampMs(now_ms);
  return stats_;
}

void ReceiveStatisticsProxy::OnIncomingPayloadType(int payload_type) {
  RTC_DCHECK_RUN_ON(&decode_queue_);
  worker_thread_->PostTask(SafeTask(task_safety_.flag(), [payload_type, this] {
    RTC_DCHECK_RUN_ON(&main_thread_);
    stats_.current_payload_type = payload_type;
  }));
}

void ReceiveStatisticsProxy::OnDecoderInfo(
    const VideoDecoder::DecoderInfo& decoder_info) {
  RTC_DCHECK_RUN_ON(&decode_queue_);
  worker_thread_->PostTask(SafeTask(
      task_safety_.flag(),
      [this, name = decoder_info.implementation_name,
       is_hardware_accelerated = decoder_info.is_hardware_accelerated]() {
        RTC_DCHECK_RUN_ON(&main_thread_);
        stats_.decoder_implementation_name = name;
        stats_.power_efficient_decoder = is_hardware_accelerated;
      }));
}

void ReceiveStatisticsProxy::OnDecodableFrame(TimeDelta jitter_buffer_delay,
                                              TimeDelta target_delay,
                                              TimeDelta minimum_delay) {
  RTC_DCHECK_RUN_ON(&main_thread_);
  // Cumulative stats exposed through standardized GetStats.
  stats_.jitter_buffer_delay += jitter_buffer_delay;
  stats_.jitter_buffer_target_delay += target_delay;
  ++stats_.jitter_buffer_emitted_count;
  stats_.jitter_buffer_minimum_delay += minimum_delay;
}

void ReceiveStatisticsProxy::OnFrameBufferTimingsUpdated(
    int estimated_max_decode_time_ms,
    int current_delay_ms,
    int target_delay_ms,
    int jitter_delay_ms,
    int min_playout_delay_ms,
    int render_delay_ms) {
  RTC_DCHECK_RUN_ON(&main_thread_);
  // Instantaneous stats exposed through legacy GetStats.
  stats_.max_decode_ms = estimated_max_decode_time_ms;
  stats_.current_delay_ms = current_delay_ms;
  stats_.target_delay_ms = target_delay_ms;
  stats_.jitter_buffer_ms = jitter_delay_ms;
  stats_.min_playout_delay_ms = min_playout_delay_ms;
  stats_.render_delay_ms = render_delay_ms;

  // UMA stats.
  jitter_delay_counter_.Add(jitter_delay_ms);
  target_delay_counter_.Add(target_delay_ms);
  current_delay_counter_.Add(current_delay_ms);
  // Estimated one-way delay: network delay (rtt/2) + target_delay_ms (jitter
  // delay + decode time + render delay).
  oneway_delay_counter_.Add(target_delay_ms + avg_rtt_ms_ / 2);
}

void ReceiveStatisticsProxy::OnUniqueFramesCounted(int num_unique_frames) {
  RTC_DCHECK_RUN_ON(&main_thread_);
  num_unique_frames_.emplace(num_unique_frames);
}

void ReceiveStatisticsProxy::OnTimingFrameInfoUpdated(
    const TimingFrameInfo& info) {
  RTC_DCHECK_RUN_ON(&main_thread_);
  if (info.flags != VideoSendTiming::kInvalid) {
    int64_t now_ms = clock_->TimeInMilliseconds();
    timing_frame_info_counter_.Add(info, now_ms);
  }

  // Measure initial decoding latency between the first frame arriving and
  // the first frame being decoded.
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
  if (ssrc != remote_ssrc_)
    return;

  if (!IsCurrentTaskQueueOrThread(worker_thread_)) {
    // RtpRtcpInterface::Configuration has a single
    // RtcpPacketTypeCounterObserver and that same configuration may be used for
    // both receiver and sender (see ModuleRtpRtcpImpl::ModuleRtpRtcpImpl). The
    // RTCPSender implementation currently makes calls to this function on a
    // process thread whereas the RTCPReceiver implementation calls back on the
    // [main] worker thread.
    // So until the sender implementation has been updated, we work around this
    // here by posting the update to the expected thread. We make a by value
    // copy of the `task_safety_` to handle the case if the queued task
    // runs after the `ReceiveStatisticsProxy` has been deleted. In such a
    // case the packet_counter update won't be recorded.
    worker_thread_->PostTask(
        SafeTask(task_safety_.flag(), [ssrc, packet_counter, this]() {
          RtcpPacketTypesCounterUpdated(ssrc, packet_counter);
        }));
    return;
  }

  RTC_DCHECK_RUN_ON(&main_thread_);
  stats_.rtcp_packet_type_counts = packet_counter;
}

void ReceiveStatisticsProxy::OnCname(uint32_t ssrc, absl::string_view cname) {
  RTC_DCHECK_RUN_ON(&main_thread_);
  // TODO(pbos): Handle both local and remote ssrcs here and RTC_DCHECK that we
  // receive stats from one of them.
  if (remote_ssrc_ != ssrc)
    return;

  stats_.c_name = std::string(cname);
}

void ReceiveStatisticsProxy::OnDecodedFrame(const VideoFrame& frame,
                                            absl::optional<uint8_t> qp,
                                            TimeDelta decode_time,
                                            VideoContentType content_type,
                                            VideoFrameType frame_type) {
  TimeDelta processing_delay = TimeDelta::Zero();
  webrtc::Timestamp current_time = clock_->CurrentTime();
  // TODO(bugs.webrtc.org/13984): some tests do not fill packet_infos().
  TimeDelta assembly_time = TimeDelta::Zero();
  if (frame.packet_infos().size() > 0) {
    const auto [first_packet, last_packet] = std::minmax_element(
        frame.packet_infos().cbegin(), frame.packet_infos().cend(),
        [](const webrtc::RtpPacketInfo& a, const webrtc::RtpPacketInfo& b) {
          return a.receive_time() < b.receive_time();
        });
    if (first_packet->receive_time().IsFinite()) {
      processing_delay = current_time - first_packet->receive_time();
      // Extract frame assembly time (i.e. time between earliest and latest
      // packet arrival). Note: for single-packet frames this will be 0.
      assembly_time =
          last_packet->receive_time() - first_packet->receive_time();
    }
  }
  // See VCMDecodedFrameCallback::Decoded for more info on what thread/queue we
  // may be on. E.g. on iOS this gets called on
  // "com.apple.coremedia.decompressionsession.clientcallback"
  VideoFrameMetaData meta(frame, current_time);
  worker_thread_->PostTask(SafeTask(
      task_safety_.flag(), [meta, qp, decode_time, processing_delay,
                            assembly_time, content_type, frame_type, this]() {
        OnDecodedFrame(meta, qp, decode_time, processing_delay, assembly_time,
                       content_type, frame_type);
      }));
}

void ReceiveStatisticsProxy::OnDecodedFrame(
    const VideoFrameMetaData& frame_meta,
    absl::optional<uint8_t> qp,
    TimeDelta decode_time,
    TimeDelta processing_delay,
    TimeDelta assembly_time,
    VideoContentType content_type,
    VideoFrameType frame_type) {
  RTC_DCHECK_RUN_ON(&main_thread_);

  const bool is_screenshare =
      videocontenttypehelpers::IsScreenshare(content_type);
  const bool was_screenshare =
      videocontenttypehelpers::IsScreenshare(last_content_type_);

  if (is_screenshare != was_screenshare) {
    // Reset the quality observer if content type is switched. But first report
    // stats for the previous part of the call.
    video_quality_observer_->UpdateHistograms(was_screenshare);
    video_quality_observer_.reset(new VideoQualityObserver());
  }

  video_quality_observer_->OnDecodedFrame(frame_meta.rtp_timestamp, qp,
                                          last_codec_type_);

  ContentSpecificStats* content_specific_stats =
      &content_specific_stats_[content_type];

  ++stats_.frames_decoded;
  if (frame_type == VideoFrameType::kVideoFrameKey) {
    ++stats_.frame_counts.key_frames;
  } else {
    ++stats_.frame_counts.delta_frames;
  }
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
  decode_time_counter_.Add(decode_time.ms());
  stats_.decode_ms = decode_time.ms();
  stats_.total_decode_time += decode_time;
  stats_.total_processing_delay += processing_delay;
  stats_.total_assembly_time += assembly_time;
  if (!assembly_time.IsZero()) {
    ++stats_.frames_assembled_from_multiple_packets;
  }

  last_content_type_ = content_type;
  decode_fps_estimator_.Update(1, frame_meta.decode_timestamp.ms());

  if (last_decoded_frame_time_ms_) {
    int64_t interframe_delay_ms =
        frame_meta.decode_timestamp.ms() - *last_decoded_frame_time_ms_;
    RTC_DCHECK_GE(interframe_delay_ms, 0);
    interframe_delay_max_moving_.Add(interframe_delay_ms,
                                     frame_meta.decode_timestamp.ms());
    content_specific_stats->interframe_delay_counter.Add(interframe_delay_ms);
    content_specific_stats->interframe_delay_percentiles.Add(
        interframe_delay_ms);
    content_specific_stats->flow_duration_ms += interframe_delay_ms;
  }
  if (stats_.frames_decoded == 1) {
    first_decoded_frame_time_ms_.emplace(frame_meta.decode_timestamp.ms());
  }
  last_decoded_frame_time_ms_.emplace(frame_meta.decode_timestamp.ms());
}

void ReceiveStatisticsProxy::OnRenderedFrame(
    const VideoFrameMetaData& frame_meta) {
  RTC_DCHECK_RUN_ON(&main_thread_);
  // Called from VideoReceiveStream2::OnFrame.

  RTC_DCHECK_GT(frame_meta.width, 0);
  RTC_DCHECK_GT(frame_meta.height, 0);

  video_quality_observer_->OnRenderedFrame(frame_meta);

  ContentSpecificStats* content_specific_stats =
      &content_specific_stats_[last_content_type_];
  renders_fps_estimator_.Update(1, frame_meta.decode_timestamp.ms());

  ++stats_.frames_rendered;
  stats_.width = frame_meta.width;
  stats_.height = frame_meta.height;

  render_fps_tracker_.AddSamples(1);
  render_pixel_tracker_.AddSamples(sqrt(frame_meta.width * frame_meta.height));
  content_specific_stats->received_width.Add(frame_meta.width);
  content_specific_stats->received_height.Add(frame_meta.height);

  // Consider taking stats_.render_delay_ms into account.
  const int64_t time_until_rendering_ms =
      frame_meta.render_time_ms() - frame_meta.decode_timestamp.ms();
  if (time_until_rendering_ms < 0) {
    sum_missed_render_deadline_ms_ += -time_until_rendering_ms;
    ++num_delayed_frames_rendered_;
  }

  if (frame_meta.ntp_time_ms > 0) {
    int64_t delay_ms =
        clock_->CurrentNtpInMilliseconds() - frame_meta.ntp_time_ms;
    if (delay_ms >= 0) {
      content_specific_stats->e2e_delay_counter.Add(delay_ms);
    }
  }
}

void ReceiveStatisticsProxy::OnSyncOffsetUpdated(int64_t video_playout_ntp_ms,
                                                 int64_t sync_offset_ms,
                                                 double estimated_freq_khz) {
  RTC_DCHECK_RUN_ON(&main_thread_);

  const int64_t now_ms = clock_->TimeInMilliseconds();
  sync_offset_counter_.Add(std::abs(sync_offset_ms));
  stats_.sync_offset_ms = sync_offset_ms;
  last_estimated_playout_ntp_timestamp_ms_ = video_playout_ntp_ms;
  last_estimated_playout_time_ms_ = now_ms;

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
  RTC_DCHECK_RUN_ON(&main_thread_);

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
  // Can be called on either the decode queue or the worker thread
  // See FrameBuffer2 for more details.
  worker_thread_->PostTask(
      SafeTask(task_safety_.flag(), [frames_dropped, this]() {
        RTC_DCHECK_RUN_ON(&main_thread_);
        stats_.frames_dropped += frames_dropped;
      }));
}

void ReceiveStatisticsProxy::OnPreDecode(VideoCodecType codec_type, int qp) {
  RTC_DCHECK_RUN_ON(&main_thread_);
  last_codec_type_ = codec_type;
  if (last_codec_type_ == kVideoCodecVP8 && qp != -1) {
    qp_counters_.vp8.Add(qp);
  }
}

void ReceiveStatisticsProxy::OnStreamInactive() {
  RTC_DCHECK_RUN_ON(&main_thread_);

  // TODO(sprang): Figure out any other state that should be reset.

  // Don't report inter-frame delay if stream was paused.
  last_decoded_frame_time_ms_.reset();

  video_quality_observer_->OnStreamInactive();
}

void ReceiveStatisticsProxy::OnRttUpdate(int64_t avg_rtt_ms) {
  RTC_DCHECK_RUN_ON(&main_thread_);
  avg_rtt_ms_ = avg_rtt_ms;
}

void ReceiveStatisticsProxy::DecoderThreadStarting() {
  RTC_DCHECK_RUN_ON(&main_thread_);
}

void ReceiveStatisticsProxy::DecoderThreadStopped() {
  RTC_DCHECK_RUN_ON(&main_thread_);
  decode_queue_.Detach();
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

}  // namespace internal
}  // namespace webrtc
