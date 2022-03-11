/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/delay_manager.h"

#include <stdio.h>
#include <stdlib.h>

#include <algorithm>
#include <memory>
#include <numeric>
#include <string>

#include "modules/include/module_common_types_public.h"
#include "rtc_base/checks.h"
#include "rtc_base/experiments/struct_parameters_parser.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/numerics/safe_minmax.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace {

constexpr int kMinBaseMinimumDelayMs = 0;
constexpr int kMaxBaseMinimumDelayMs = 10000;
constexpr int kStartDelayMs = 80;

std::unique_ptr<ReorderOptimizer> MaybeCreateReorderOptimizer(
    const DelayManager::Config& config) {
  if (!config.use_reorder_optimizer) {
    return nullptr;
  }
  return std::make_unique<ReorderOptimizer>(
      (1 << 15) * config.reorder_forget_factor, config.ms_per_loss_percent,
      config.start_forget_weight);
}

}  // namespace

DelayManager::Config::Config() {
  StructParametersParser::Create(                       //
      "quantile", &quantile,                            //
      "forget_factor", &forget_factor,                  //
      "start_forget_weight", &start_forget_weight,      //
      "resample_interval_ms", &resample_interval_ms,    //
      "max_history_ms", &max_history_ms,                //
      "use_reorder_optimizer", &use_reorder_optimizer,  //
      "reorder_forget_factor", &reorder_forget_factor,  //
      "ms_per_loss_percent", &ms_per_loss_percent)
      ->Parse(webrtc::field_trial::FindFullName(
          "WebRTC-Audio-NetEqDelayManagerConfig"));
}

void DelayManager::Config::Log() {
  RTC_LOG(LS_INFO) << "Delay manager config:"
                      " quantile="
                   << quantile << " forget_factor=" << forget_factor
                   << " start_forget_weight=" << start_forget_weight.value_or(0)
                   << " resample_interval_ms="
                   << resample_interval_ms.value_or(0)
                   << " max_history_ms=" << max_history_ms
                   << " use_reorder_optimizer=" << use_reorder_optimizer
                   << " reorder_forget_factor=" << reorder_forget_factor
                   << " ms_per_loss_percent=" << ms_per_loss_percent;
}

DelayManager::DelayManager(const Config& config, const TickTimer* tick_timer)
    : max_packets_in_buffer_(config.max_packets_in_buffer),
      underrun_optimizer_(tick_timer,
                          (1 << 30) * config.quantile,
                          (1 << 15) * config.forget_factor,
                          config.start_forget_weight,
                          config.resample_interval_ms),
      reorder_optimizer_(MaybeCreateReorderOptimizer(config)),
      relative_arrival_delay_tracker_(tick_timer, config.max_history_ms),
      base_minimum_delay_ms_(config.base_minimum_delay_ms),
      effective_minimum_delay_ms_(config.base_minimum_delay_ms),
      minimum_delay_ms_(0),
      maximum_delay_ms_(0),
      target_level_ms_(kStartDelayMs) {
  RTC_DCHECK_GE(base_minimum_delay_ms_, 0);

  Reset();
}

DelayManager::~DelayManager() {}

absl::optional<int> DelayManager::Update(uint32_t timestamp,
                                         int sample_rate_hz,
                                         bool reset) {
  if (reset) {
    relative_arrival_delay_tracker_.Reset();
  }
  absl::optional<int> relative_delay =
      relative_arrival_delay_tracker_.Update(timestamp, sample_rate_hz);
  if (!relative_delay) {
    return absl::nullopt;
  }

  bool reordered =
      relative_arrival_delay_tracker_.newest_timestamp() != timestamp;
  if (!reorder_optimizer_ || !reordered) {
    underrun_optimizer_.Update(*relative_delay);
  }
  target_level_ms_ =
      underrun_optimizer_.GetOptimalDelayMs().value_or(kStartDelayMs);
  if (reorder_optimizer_) {
    reorder_optimizer_->Update(*relative_delay, reordered, target_level_ms_);
    target_level_ms_ = std::max(
        target_level_ms_, reorder_optimizer_->GetOptimalDelayMs().value_or(0));
  }
  target_level_ms_ = std::max(target_level_ms_, effective_minimum_delay_ms_);
  if (maximum_delay_ms_ > 0) {
    target_level_ms_ = std::min(target_level_ms_, maximum_delay_ms_);
  }
  if (packet_len_ms_ > 0) {
    // Target level should be at least one packet.
    target_level_ms_ = std::max(target_level_ms_, packet_len_ms_);
    // Limit to 75% of maximum buffer size.
    target_level_ms_ = std::min(
        target_level_ms_, 3 * max_packets_in_buffer_ * packet_len_ms_ / 4);
  }

  return relative_delay;
}


int DelayManager::SetPacketAudioLength(int length_ms) {
  if (length_ms <= 0) {
    RTC_LOG_F(LS_ERROR) << "length_ms = " << length_ms;
    return -1;
  }
  packet_len_ms_ = length_ms;
  return 0;
}

void DelayManager::Reset() {
  packet_len_ms_ = 0;
  underrun_optimizer_.Reset();
  relative_arrival_delay_tracker_.Reset();
  target_level_ms_ = kStartDelayMs;
  if (reorder_optimizer_) {
    reorder_optimizer_->Reset();
  }
}

int DelayManager::TargetDelayMs() const {
  return target_level_ms_;
}

bool DelayManager::IsValidMinimumDelay(int delay_ms) const {
  return 0 <= delay_ms && delay_ms <= MinimumDelayUpperBound();
}

bool DelayManager::IsValidBaseMinimumDelay(int delay_ms) const {
  return kMinBaseMinimumDelayMs <= delay_ms &&
         delay_ms <= kMaxBaseMinimumDelayMs;
}

bool DelayManager::SetMinimumDelay(int delay_ms) {
  if (!IsValidMinimumDelay(delay_ms)) {
    return false;
  }

  minimum_delay_ms_ = delay_ms;
  UpdateEffectiveMinimumDelay();
  return true;
}

bool DelayManager::SetMaximumDelay(int delay_ms) {
  // If `delay_ms` is zero then it unsets the maximum delay and target level is
  // unconstrained by maximum delay.
  if (delay_ms != 0 &&
      (delay_ms < minimum_delay_ms_ || delay_ms < packet_len_ms_)) {
    // Maximum delay shouldn't be less than minimum delay or less than a packet.
    return false;
  }

  maximum_delay_ms_ = delay_ms;
  UpdateEffectiveMinimumDelay();
  return true;
}

bool DelayManager::SetBaseMinimumDelay(int delay_ms) {
  if (!IsValidBaseMinimumDelay(delay_ms)) {
    return false;
  }

  base_minimum_delay_ms_ = delay_ms;
  UpdateEffectiveMinimumDelay();
  return true;
}

int DelayManager::GetBaseMinimumDelay() const {
  return base_minimum_delay_ms_;
}

void DelayManager::UpdateEffectiveMinimumDelay() {
  // Clamp `base_minimum_delay_ms_` into the range which can be effectively
  // used.
  const int base_minimum_delay_ms =
      rtc::SafeClamp(base_minimum_delay_ms_, 0, MinimumDelayUpperBound());
  effective_minimum_delay_ms_ =
      std::max(minimum_delay_ms_, base_minimum_delay_ms);
}

int DelayManager::MinimumDelayUpperBound() const {
  // Choose the lowest possible bound discarding 0 cases which mean the value
  // is not set and unconstrained.
  int q75 = max_packets_in_buffer_ * packet_len_ms_ * 3 / 4;
  q75 = q75 > 0 ? q75 : kMaxBaseMinimumDelayMs;
  const int maximum_delay_ms =
      maximum_delay_ms_ > 0 ? maximum_delay_ms_ : kMaxBaseMinimumDelayMs;
  return std::min(maximum_delay_ms, q75);
}

}  // namespace webrtc
