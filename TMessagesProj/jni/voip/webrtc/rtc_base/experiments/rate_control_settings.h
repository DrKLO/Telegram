/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_EXPERIMENTS_RATE_CONTROL_SETTINGS_H_
#define RTC_BASE_EXPERIMENTS_RATE_CONTROL_SETTINGS_H_

#include "absl/types/optional.h"
#include "api/field_trials_view.h"
#include "api/units/data_size.h"
#include "api/video_codecs/video_codec.h"
#include "rtc_base/experiments/struct_parameters_parser.h"
#include "video/config/video_encoder_config.h"

namespace webrtc {

struct CongestionWindowConfig {
  static constexpr char kKey[] = "WebRTC-CongestionWindow";
  absl::optional<int> queue_size_ms;
  absl::optional<int> min_bitrate_bps;
  absl::optional<DataSize> initial_data_window;
  bool drop_frame_only = false;
  std::unique_ptr<StructParametersParser> Parser();
  static CongestionWindowConfig Parse(absl::string_view config);
};

struct VideoRateControlConfig {
  static constexpr char kKey[] = "WebRTC-VideoRateControl";
  absl::optional<double> pacing_factor;
  bool alr_probing = false;
  absl::optional<int> vp8_qp_max;
  absl::optional<int> vp8_min_pixels;
  bool trust_vp8 = true;
  bool trust_vp9 = true;
  bool bitrate_adjuster = true;
  bool adjuster_use_headroom = true;
  bool vp8_s0_boost = false;
  bool vp8_base_heavy_tl3_alloc = false;

  std::unique_ptr<StructParametersParser> Parser();
};

class RateControlSettings final {
 public:
  ~RateControlSettings();
  RateControlSettings(RateControlSettings&&);

  static RateControlSettings ParseFromFieldTrials();
  static RateControlSettings ParseFromKeyValueConfig(
      const FieldTrialsView* const key_value_config);

  // When CongestionWindowPushback is enabled, the pacer is oblivious to
  // the congestion window. The relation between outstanding data and
  // the congestion window affects encoder allocations directly.
  bool UseCongestionWindow() const;
  int64_t GetCongestionWindowAdditionalTimeMs() const;
  bool UseCongestionWindowPushback() const;
  bool UseCongestionWindowDropFrameOnly() const;
  uint32_t CongestionWindowMinPushbackTargetBitrateBps() const;
  absl::optional<DataSize> CongestionWindowInitialDataWindow() const;

  absl::optional<double> GetPacingFactor() const;
  bool UseAlrProbing() const;

  absl::optional<int> LibvpxVp8QpMax() const;
  absl::optional<int> LibvpxVp8MinPixels() const;
  bool LibvpxVp8TrustedRateController() const;
  bool Vp8BoostBaseLayerQuality() const;
  bool Vp8DynamicRateSettings() const;
  bool LibvpxVp9TrustedRateController() const;
  bool Vp9DynamicRateSettings() const;

  bool Vp8BaseHeavyTl3RateAllocation() const;

  bool UseEncoderBitrateAdjuster() const;
  bool BitrateAdjusterCanUseNetworkHeadroom() const;

 private:
  explicit RateControlSettings(const FieldTrialsView* const key_value_config);

  CongestionWindowConfig congestion_window_config_;
  VideoRateControlConfig video_config_;
};

}  // namespace webrtc

#endif  // RTC_BASE_EXPERIMENTS_RATE_CONTROL_SETTINGS_H_
