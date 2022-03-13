/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef VIDEO_SEND_STATISTICS_PROXY_H_
#define VIDEO_SEND_STATISTICS_PROXY_H_

#include <array>
#include <map>
#include <memory>
#include <string>
#include <vector>

#include "api/video/video_codec_constants.h"
#include "api/video/video_stream_encoder_observer.h"
#include "api/video_codecs/video_encoder_config.h"
#include "call/video_send_stream.h"
#include "modules/include/module_common_types_public.h"
#include "modules/rtp_rtcp/include/report_block_data.h"
#include "modules/video_coding/include/video_codec_interface.h"
#include "modules/video_coding/include/video_coding_defines.h"
#include "rtc_base/numerics/exp_filter.h"
#include "rtc_base/rate_tracker.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread_annotations.h"
#include "system_wrappers/include/clock.h"
#include "video/quality_limitation_reason_tracker.h"
#include "video/report_block_stats.h"
#include "video/stats_counter.h"

namespace webrtc {

class SendStatisticsProxy : public VideoStreamEncoderObserver,
                            public ReportBlockDataObserver,
                            public RtcpPacketTypeCounterObserver,
                            public StreamDataCountersCallback,
                            public BitrateStatisticsObserver,
                            public FrameCountObserver,
                            public SendSideDelayObserver {
 public:
  static const int kStatsTimeoutMs;
  // Number of required samples to be collected before a metric is added
  // to a rtc histogram.
  static const int kMinRequiredMetricsSamples = 200;

  SendStatisticsProxy(Clock* clock,
                      const VideoSendStream::Config& config,
                      VideoEncoderConfig::ContentType content_type);
  ~SendStatisticsProxy() override;

  virtual VideoSendStream::Stats GetStats();

  void OnSendEncodedImage(const EncodedImage& encoded_image,
                          const CodecSpecificInfo* codec_info) override;

  void OnEncoderImplementationChanged(
      const std::string& implementation_name) override;

  // Used to update incoming frame rate.
  void OnIncomingFrame(int width, int height) override;

  // Dropped frame stats.
  void OnFrameDropped(DropReason) override;

  // Adaptation stats.
  void OnAdaptationChanged(
      VideoAdaptationReason reason,
      const VideoAdaptationCounters& cpu_counters,
      const VideoAdaptationCounters& quality_counters) override;
  void ClearAdaptationStats() override;
  void UpdateAdaptationSettings(AdaptationSettings cpu_settings,
                                AdaptationSettings quality_settings) override;

  void OnBitrateAllocationUpdated(
      const VideoCodec& codec,
      const VideoBitrateAllocation& allocation) override;

  void OnEncoderInternalScalerUpdate(bool is_scaled) override;

  void OnMinPixelLimitReached() override;
  void OnInitialQualityResolutionAdaptDown() override;

  void OnSuspendChange(bool is_suspended) override;
  void OnInactiveSsrc(uint32_t ssrc);

  // Used to indicate change in content type, which may require a change in
  // how stats are collected.
  void OnEncoderReconfigured(const VideoEncoderConfig& encoder_config,
                             const std::vector<VideoStream>& streams) override;

  // Used to update the encoder target rate.
  void OnSetEncoderTargetRate(uint32_t bitrate_bps);

  // Implements CpuOveruseMetricsObserver.
  void OnEncodedFrameTimeMeasured(int encode_time_ms,
                                  int encode_usage_percent) override;

  int GetInputFrameRate() const override;
  int GetSendFrameRate() const;

 protected:
  // From ReportBlockDataObserver.
  void OnReportBlockDataUpdated(ReportBlockData report_block_data) override;
  // From RtcpPacketTypeCounterObserver.
  void RtcpPacketTypesCounterUpdated(
      uint32_t ssrc,
      const RtcpPacketTypeCounter& packet_counter) override;
  // From StreamDataCountersCallback.
  void DataCountersUpdated(const StreamDataCounters& counters,
                           uint32_t ssrc) override;

  // From BitrateStatisticsObserver.
  void Notify(uint32_t total_bitrate_bps,
              uint32_t retransmit_bitrate_bps,
              uint32_t ssrc) override;

  // From FrameCountObserver.
  void FrameCountUpdated(const FrameCounts& frame_counts,
                         uint32_t ssrc) override;

  void SendSideDelayUpdated(int avg_delay_ms,
                            int max_delay_ms,
                            uint64_t total_delay_ms,
                            uint32_t ssrc) override;

 private:
  class SampleCounter {
   public:
    SampleCounter() : sum(0), num_samples(0) {}
    ~SampleCounter() {}
    void Add(int sample);
    int Avg(int64_t min_required_samples) const;

   private:
    int64_t sum;
    int64_t num_samples;
  };
  class BoolSampleCounter {
   public:
    BoolSampleCounter() : sum(0), num_samples(0) {}
    ~BoolSampleCounter() {}
    void Add(bool sample);
    void Add(bool sample, int64_t count);
    int Percent(int64_t min_required_samples) const;
    int Permille(int64_t min_required_samples) const;

   private:
    int Fraction(int64_t min_required_samples, float multiplier) const;
    int64_t sum;
    int64_t num_samples;
  };
  struct StatsUpdateTimes {
    StatsUpdateTimes() : resolution_update_ms(0), bitrate_update_ms(0) {}
    int64_t resolution_update_ms;
    int64_t bitrate_update_ms;
  };
  struct TargetRateUpdates {
    TargetRateUpdates()
        : pause_resume_events(0), last_paused_or_resumed(false), last_ms(-1) {}
    int pause_resume_events;
    bool last_paused_or_resumed;
    int64_t last_ms;
  };
  struct FallbackEncoderInfo {
    FallbackEncoderInfo();
    bool is_possible = true;
    bool is_active = false;
    int on_off_events = 0;
    int64_t elapsed_ms = 0;
    absl::optional<int64_t> last_update_ms;
    const int max_frame_diff_ms = 2000;
  };
  struct FallbackEncoderInfoDisabled {
    bool is_possible = true;
    bool min_pixel_limit_reached = false;
  };
  struct StatsTimer {
    void Start(int64_t now_ms);
    void Stop(int64_t now_ms);
    void Restart(int64_t now_ms);
    int64_t start_ms = -1;
    int64_t total_ms = 0;
  };
  struct QpCounters {
    SampleCounter vp8;   // QP range: 0-127.
    SampleCounter vp9;   // QP range: 0-255.
    SampleCounter h264;  // QP range: 0-51.
  };
  struct AdaptChanges {
    int down = 0;
    int up = 0;
  };

  // Map holding encoded frames (mapped by timestamp).
  // If simulcast layers are encoded on different threads, there is no guarantee
  // that one frame of all layers are encoded before the next start.
  struct TimestampOlderThan {
    bool operator()(uint32_t ts1, uint32_t ts2) const {
      return IsNewerTimestamp(ts2, ts1);
    }
  };
  struct Frame {
    Frame(int64_t send_ms, uint32_t width, uint32_t height, int simulcast_idx)
        : send_ms(send_ms),
          max_width(width),
          max_height(height),
          max_simulcast_idx(simulcast_idx) {}
    const int64_t
        send_ms;          // Time when first frame with this timestamp is sent.
    uint32_t max_width;   // Max width with this timestamp.
    uint32_t max_height;  // Max height with this timestamp.
    int max_simulcast_idx;  // Max simulcast index with this timestamp.
  };
  typedef std::map<uint32_t, Frame, TimestampOlderThan> EncodedFrameMap;

  void PurgeOldStats() RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);
  VideoSendStream::StreamStats* GetStatsEntry(uint32_t ssrc)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  struct MaskedAdaptationCounts {
    absl::optional<int> resolution_adaptations = absl::nullopt;
    absl::optional<int> num_framerate_reductions = absl::nullopt;
  };

  struct Adaptations {
   public:
    MaskedAdaptationCounts MaskedCpuCounts() const;
    MaskedAdaptationCounts MaskedQualityCounts() const;

    void set_cpu_counts(const VideoAdaptationCounters& cpu_counts);
    void set_quality_counts(const VideoAdaptationCounters& quality_counts);

    VideoAdaptationCounters cpu_counts() const;
    VideoAdaptationCounters quality_counts() const;

    void UpdateMaskingSettings(AdaptationSettings cpu_settings,
                               AdaptationSettings quality_settings);

   private:
    VideoAdaptationCounters cpu_counts_;
    AdaptationSettings cpu_settings_;
    VideoAdaptationCounters quality_counts_;
    AdaptationSettings quality_settings_;

    MaskedAdaptationCounts Mask(const VideoAdaptationCounters& counters,
                                const AdaptationSettings& settings) const;
  };

  void SetAdaptTimer(const MaskedAdaptationCounts& counts, StatsTimer* timer)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);
  void UpdateAdaptationStats() RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);
  void TryUpdateInitialQualityResolutionAdaptUp(
      absl::optional<int> old_quality_downscales,
      absl::optional<int> updated_quality_downscales)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  void UpdateEncoderFallbackStats(const CodecSpecificInfo* codec_info,
                                  int pixels,
                                  int simulcast_index)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);
  void UpdateFallbackDisabledStats(const CodecSpecificInfo* codec_info,
                                   int pixels,
                                   int simulcast_index)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  Clock* const clock_;
  const std::string payload_name_;
  const RtpConfig rtp_config_;
  const absl::optional<int> fallback_max_pixels_;
  const absl::optional<int> fallback_max_pixels_disabled_;
  mutable Mutex mutex_;
  VideoEncoderConfig::ContentType content_type_ RTC_GUARDED_BY(mutex_);
  const int64_t start_ms_;
  VideoSendStream::Stats stats_ RTC_GUARDED_BY(mutex_);
  std::map<uint32_t, StatsUpdateTimes> update_times_ RTC_GUARDED_BY(mutex_);
  rtc::ExpFilter encode_time_ RTC_GUARDED_BY(mutex_);
  QualityLimitationReasonTracker quality_limitation_reason_tracker_
      RTC_GUARDED_BY(mutex_);
  rtc::RateTracker media_byte_rate_tracker_ RTC_GUARDED_BY(mutex_);
  rtc::RateTracker encoded_frame_rate_tracker_ RTC_GUARDED_BY(mutex_);
  // Rate trackers mapped by ssrc.
  std::map<uint32_t, std::unique_ptr<rtc::RateTracker>>
      encoded_frame_rate_trackers_ RTC_GUARDED_BY(mutex_);

  absl::optional<int64_t> last_outlier_timestamp_ RTC_GUARDED_BY(mutex_);

  int last_num_spatial_layers_ RTC_GUARDED_BY(mutex_);
  int last_num_simulcast_streams_ RTC_GUARDED_BY(mutex_);
  std::array<bool, kMaxSpatialLayers> last_spatial_layer_use_
      RTC_GUARDED_BY(mutex_);
  // Indicates if the latest bitrate allocation had layers disabled by low
  // available bandwidth.
  bool bw_limited_layers_ RTC_GUARDED_BY(mutex_);
  // Indicastes if the encoder internally downscales input image.
  bool internal_encoder_scaler_ RTC_GUARDED_BY(mutex_);
  Adaptations adaptation_limitations_ RTC_GUARDED_BY(mutex_);

  struct EncoderChangeEvent {
    std::string previous_encoder_implementation;
    std::string new_encoder_implementation;
  };
  // Stores the last change in encoder implementation in an optional, so that
  // the event can be consumed.
  absl::optional<EncoderChangeEvent> encoder_changed_;

  // Contains stats used for UMA histograms. These stats will be reset if
  // content type changes between real-time video and screenshare, since these
  // will be reported separately.
  struct UmaSamplesContainer {
    UmaSamplesContainer(const char* prefix,
                        const VideoSendStream::Stats& start_stats,
                        Clock* clock);
    ~UmaSamplesContainer();

    void UpdateHistograms(const RtpConfig& rtp_config,
                          const VideoSendStream::Stats& current_stats);

    void InitializeBitrateCounters(const VideoSendStream::Stats& stats);

    bool InsertEncodedFrame(const EncodedImage& encoded_frame,
                            int simulcast_idx);
    void RemoveOld(int64_t now_ms);

    const std::string uma_prefix_;
    Clock* const clock_;
    SampleCounter input_width_counter_;
    SampleCounter input_height_counter_;
    SampleCounter sent_width_counter_;
    SampleCounter sent_height_counter_;
    SampleCounter encode_time_counter_;
    BoolSampleCounter key_frame_counter_;
    BoolSampleCounter quality_limited_frame_counter_;
    SampleCounter quality_downscales_counter_;
    BoolSampleCounter cpu_limited_frame_counter_;
    BoolSampleCounter bw_limited_frame_counter_;
    SampleCounter bw_resolutions_disabled_counter_;
    SampleCounter delay_counter_;
    SampleCounter max_delay_counter_;
    rtc::RateTracker input_frame_rate_tracker_;
    RateCounter input_fps_counter_;
    RateCounter sent_fps_counter_;
    RateAccCounter total_byte_counter_;
    RateAccCounter media_byte_counter_;
    RateAccCounter rtx_byte_counter_;
    RateAccCounter padding_byte_counter_;
    RateAccCounter retransmit_byte_counter_;
    RateAccCounter fec_byte_counter_;
    int64_t first_rtcp_stats_time_ms_;
    int64_t first_rtp_stats_time_ms_;
    StatsTimer cpu_adapt_timer_;
    StatsTimer quality_adapt_timer_;
    BoolSampleCounter paused_time_counter_;
    TargetRateUpdates target_rate_updates_;
    BoolSampleCounter fallback_active_counter_;
    FallbackEncoderInfo fallback_info_;
    FallbackEncoderInfoDisabled fallback_info_disabled_;
    ReportBlockStats report_block_stats_;
    const VideoSendStream::Stats start_stats_;
    size_t num_streams_;  // Number of configured streams to encoder.
    size_t num_pixels_highest_stream_;
    EncodedFrameMap encoded_frames_;
    AdaptChanges initial_quality_changes_;

    std::map<int, QpCounters>
        qp_counters_;  // QP counters mapped by spatial idx.
  };

  std::unique_ptr<UmaSamplesContainer> uma_container_ RTC_GUARDED_BY(mutex_);
};

}  // namespace webrtc
#endif  // VIDEO_SEND_STATISTICS_PROXY_H_
