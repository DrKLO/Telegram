/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/congestion_controller/goog_cc/alr_detector.h"

#include <cstdint>
#include <cstdio>
#include <memory>

#include "api/rtc_event_log/rtc_event.h"
#include "api/rtc_event_log/rtc_event_log.h"
#include "logging/rtc_event_log/events/rtc_event_alr_state.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/time_utils.h"

namespace webrtc {

namespace {
AlrDetectorConfig GetConfigFromTrials(
    const WebRtcKeyValueConfig* key_value_config) {
  RTC_CHECK(AlrExperimentSettings::MaxOneFieldTrialEnabled(*key_value_config));
  absl::optional<AlrExperimentSettings> experiment_settings =
      AlrExperimentSettings::CreateFromFieldTrial(
          *key_value_config,
          AlrExperimentSettings::kScreenshareProbingBweExperimentName);
  if (!experiment_settings) {
    experiment_settings = AlrExperimentSettings::CreateFromFieldTrial(
        *key_value_config,
        AlrExperimentSettings::kStrictPacingAndProbingExperimentName);
  }
  AlrDetectorConfig conf;
  if (experiment_settings) {
    conf.bandwidth_usage_ratio =
        experiment_settings->alr_bandwidth_usage_percent / 100.0;
    conf.start_budget_level_ratio =
        experiment_settings->alr_start_budget_level_percent / 100.0;
    conf.stop_budget_level_ratio =
        experiment_settings->alr_stop_budget_level_percent / 100.0;
  }
  conf.Parser()->Parse(
      key_value_config->Lookup("WebRTC-AlrDetectorParameters"));
  return conf;
}
}  //  namespace

std::unique_ptr<StructParametersParser> AlrDetectorConfig::Parser() {
  return StructParametersParser::Create(   //
      "bw_usage", &bandwidth_usage_ratio,  //
      "start", &start_budget_level_ratio,  //
      "stop", &stop_budget_level_ratio);
}

AlrDetector::AlrDetector(AlrDetectorConfig config, RtcEventLog* event_log)
    : conf_(config), alr_budget_(0, true), event_log_(event_log) {}

AlrDetector::AlrDetector(const WebRtcKeyValueConfig* key_value_config)
    : AlrDetector(GetConfigFromTrials(key_value_config), nullptr) {}

AlrDetector::AlrDetector(const WebRtcKeyValueConfig* key_value_config,
                         RtcEventLog* event_log)
    : AlrDetector(GetConfigFromTrials(key_value_config), event_log) {}
AlrDetector::~AlrDetector() {}

void AlrDetector::OnBytesSent(size_t bytes_sent, int64_t send_time_ms) {
  if (!last_send_time_ms_.has_value()) {
    last_send_time_ms_ = send_time_ms;
    // Since the duration for sending the bytes is unknwon, return without
    // updating alr state.
    return;
  }
  int64_t delta_time_ms = send_time_ms - *last_send_time_ms_;
  last_send_time_ms_ = send_time_ms;

  alr_budget_.UseBudget(bytes_sent);
  alr_budget_.IncreaseBudget(delta_time_ms);
  bool state_changed = false;
  if (alr_budget_.budget_ratio() > conf_.start_budget_level_ratio &&
      !alr_started_time_ms_) {
    alr_started_time_ms_.emplace(rtc::TimeMillis());
    state_changed = true;
  } else if (alr_budget_.budget_ratio() < conf_.stop_budget_level_ratio &&
             alr_started_time_ms_) {
    state_changed = true;
    alr_started_time_ms_.reset();
  }
  if (event_log_ && state_changed) {
    event_log_->Log(
        std::make_unique<RtcEventAlrState>(alr_started_time_ms_.has_value()));
  }
}

void AlrDetector::SetEstimatedBitrate(int bitrate_bps) {
  RTC_DCHECK(bitrate_bps);
  int target_rate_kbps =
      static_cast<double>(bitrate_bps) * conf_.bandwidth_usage_ratio / 1000;
  alr_budget_.set_target_rate_kbps(target_rate_kbps);
}

absl::optional<int64_t> AlrDetector::GetApplicationLimitedRegionStartTime()
    const {
  return alr_started_time_ms_;
}

}  // namespace webrtc
