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

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>

#include <algorithm>
#include <memory>
#include <numeric>
#include <string>

#include "modules/audio_coding/neteq/histogram.h"
#include "modules/include/module_common_types_public.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/numerics/safe_minmax.h"
#include "system_wrappers/include/field_trial.h"

namespace {

constexpr int kMinBaseMinimumDelayMs = 0;
constexpr int kMaxBaseMinimumDelayMs = 10000;
constexpr int kMaxReorderedPackets =
    10;  // Max number of consecutive reordered packets.
constexpr int kMaxHistoryMs = 2000;  // Oldest packet to include in history to
                                     // calculate relative packet arrival delay.
constexpr int kDelayBuckets = 100;
constexpr int kBucketSizeMs = 20;
constexpr int kDecelerationTargetLevelOffsetMs = 85 << 8;  // In Q8.

int PercentileToQuantile(double percentile) {
  return static_cast<int>((1 << 30) * percentile / 100.0 + 0.5);
}

struct DelayHistogramConfig {
  int quantile = 1041529569;  // 0.97 in Q30.
  int forget_factor = 32745;  // 0.9993 in Q15.
  absl::optional<double> start_forget_weight = 2;
};

DelayHistogramConfig GetDelayHistogramConfig() {
  constexpr char kDelayHistogramFieldTrial[] =
      "WebRTC-Audio-NetEqDelayHistogram";
  DelayHistogramConfig config;
  if (webrtc::field_trial::IsEnabled(kDelayHistogramFieldTrial)) {
    const auto field_trial_string =
        webrtc::field_trial::FindFullName(kDelayHistogramFieldTrial);
    double percentile = -1.0;
    double forget_factor = -1.0;
    double start_forget_weight = -1.0;
    if (sscanf(field_trial_string.c_str(), "Enabled-%lf-%lf-%lf", &percentile,
               &forget_factor, &start_forget_weight) >= 2 &&
        percentile >= 0.0 && percentile <= 100.0 && forget_factor >= 0.0 &&
        forget_factor <= 1.0) {
      config.quantile = PercentileToQuantile(percentile);
      config.forget_factor = (1 << 15) * forget_factor;
      config.start_forget_weight =
          start_forget_weight >= 1 ? absl::make_optional(start_forget_weight)
                                   : absl::nullopt;
    }
  }
  RTC_LOG(LS_INFO) << "Delay histogram config:"
                      " quantile="
                   << config.quantile
                   << " forget_factor=" << config.forget_factor
                   << " start_forget_weight="
                   << config.start_forget_weight.value_or(0);
  return config;
}

}  // namespace

namespace webrtc {

DelayManager::DelayManager(size_t max_packets_in_buffer,
                           int base_minimum_delay_ms,
                           int histogram_quantile,
                           bool enable_rtx_handling,
                           const TickTimer* tick_timer,
                           std::unique_ptr<Histogram> histogram)
    : first_packet_received_(false),
      max_packets_in_buffer_(max_packets_in_buffer),
      histogram_(std::move(histogram)),
      histogram_quantile_(histogram_quantile),
      tick_timer_(tick_timer),
      base_minimum_delay_ms_(base_minimum_delay_ms),
      effective_minimum_delay_ms_(base_minimum_delay_ms),
      base_target_level_(4),                   // In Q0 domain.
      target_level_(base_target_level_ << 8),  // In Q8 domain.
      packet_len_ms_(0),
      last_seq_no_(0),
      last_timestamp_(0),
      minimum_delay_ms_(0),
      maximum_delay_ms_(0),
      last_pack_cng_or_dtmf_(1),
      enable_rtx_handling_(enable_rtx_handling) {
  RTC_CHECK(histogram_);
  RTC_DCHECK_GE(base_minimum_delay_ms_, 0);

  Reset();
}

std::unique_ptr<DelayManager> DelayManager::Create(
    size_t max_packets_in_buffer,
    int base_minimum_delay_ms,
    bool enable_rtx_handling,
    const TickTimer* tick_timer) {
  DelayHistogramConfig config = GetDelayHistogramConfig();
  const int quantile = config.quantile;
  std::unique_ptr<Histogram> histogram = std::make_unique<Histogram>(
      kDelayBuckets, config.forget_factor, config.start_forget_weight);
  return std::make_unique<DelayManager>(
      max_packets_in_buffer, base_minimum_delay_ms, quantile,
      enable_rtx_handling, tick_timer, std::move(histogram));
}

DelayManager::~DelayManager() {}

absl::optional<int> DelayManager::Update(uint16_t sequence_number,
                                         uint32_t timestamp,
                                         int sample_rate_hz) {
  if (sample_rate_hz <= 0) {
    return absl::nullopt;
  }

  if (!first_packet_received_) {
    // Prepare for next packet arrival.
    packet_iat_stopwatch_ = tick_timer_->GetNewStopwatch();
    last_seq_no_ = sequence_number;
    last_timestamp_ = timestamp;
    first_packet_received_ = true;
    return absl::nullopt;
  }

  // Try calculating packet length from current and previous timestamps.
  int packet_len_ms;
  if (!IsNewerTimestamp(timestamp, last_timestamp_) ||
      !IsNewerSequenceNumber(sequence_number, last_seq_no_)) {
    // Wrong timestamp or sequence order; use stored value.
    packet_len_ms = packet_len_ms_;
  } else {
    // Calculate timestamps per packet and derive packet length in ms.
    int64_t packet_len_samp =
        static_cast<uint32_t>(timestamp - last_timestamp_) /
        static_cast<uint16_t>(sequence_number - last_seq_no_);
    packet_len_ms =
        rtc::saturated_cast<int>(1000 * packet_len_samp / sample_rate_hz);
  }

  bool reordered = false;
  absl::optional<int> relative_delay;
  if (packet_len_ms > 0) {
    // Cannot update statistics unless |packet_len_ms| is valid.

    // Inter-arrival time (IAT) in integer "packet times" (rounding down). This
    // is the value added to the inter-arrival time histogram.
    int iat_ms = packet_iat_stopwatch_->ElapsedMs();
    // Check for discontinuous packet sequence and re-ordering.
    if (IsNewerSequenceNumber(sequence_number, last_seq_no_ + 1)) {
      // Compensate for gap in the sequence numbers. Reduce IAT with the
      // expected extra time due to lost packets.
      int packet_offset =
          static_cast<uint16_t>(sequence_number - last_seq_no_ - 1);
      iat_ms -= packet_offset * packet_len_ms;
    } else if (!IsNewerSequenceNumber(sequence_number, last_seq_no_)) {
      int packet_offset =
          static_cast<uint16_t>(last_seq_no_ + 1 - sequence_number);
      iat_ms += packet_offset * packet_len_ms;
      reordered = true;
    }

    int iat_delay = iat_ms - packet_len_ms;
    if (reordered) {
      relative_delay = std::max(iat_delay, 0);
    } else {
      UpdateDelayHistory(iat_delay, timestamp, sample_rate_hz);
      relative_delay = CalculateRelativePacketArrivalDelay();
    }

    const int index = relative_delay.value() / kBucketSizeMs;
    if (index < histogram_->NumBuckets()) {
      // Maximum delay to register is 2000 ms.
      histogram_->Add(index);
    }
    // Calculate new |target_level_| based on updated statistics.
    target_level_ = CalculateTargetLevel();

    LimitTargetLevel();
  }  // End if (packet_len_ms > 0).

  if (enable_rtx_handling_ && reordered &&
      num_reordered_packets_ < kMaxReorderedPackets) {
    ++num_reordered_packets_;
    return relative_delay;
  }
  num_reordered_packets_ = 0;
  // Prepare for next packet arrival.
  packet_iat_stopwatch_ = tick_timer_->GetNewStopwatch();
  last_seq_no_ = sequence_number;
  last_timestamp_ = timestamp;
  return relative_delay;
}

void DelayManager::UpdateDelayHistory(int iat_delay_ms,
                                      uint32_t timestamp,
                                      int sample_rate_hz) {
  PacketDelay delay;
  delay.iat_delay_ms = iat_delay_ms;
  delay.timestamp = timestamp;
  delay_history_.push_back(delay);
  while (timestamp - delay_history_.front().timestamp >
         static_cast<uint32_t>(kMaxHistoryMs * sample_rate_hz / 1000)) {
    delay_history_.pop_front();
  }
}

int DelayManager::CalculateRelativePacketArrivalDelay() const {
  // This effectively calculates arrival delay of a packet relative to the
  // packet preceding the history window. If the arrival delay ever becomes
  // smaller than zero, it means the reference packet is invalid, and we
  // move the reference.
  int relative_delay = 0;
  for (const PacketDelay& delay : delay_history_) {
    relative_delay += delay.iat_delay_ms;
    relative_delay = std::max(relative_delay, 0);
  }
  return relative_delay;
}

// Enforces upper and lower limits for |target_level_|. The upper limit is
// chosen to be minimum of i) 75% of |max_packets_in_buffer_|, to leave some
// headroom for natural fluctuations around the target, and ii) equivalent of
// |maximum_delay_ms_| in packets. Note that in practice, if no
// |maximum_delay_ms_| is specified, this does not have any impact, since the
// target level is far below the buffer capacity in all reasonable cases.
// The lower limit is equivalent of |effective_minimum_delay_ms_| in packets.
// We update |least_required_level_| while the above limits are applied.
// TODO(hlundin): Move this check to the buffer logistics class.
void DelayManager::LimitTargetLevel() {
  if (packet_len_ms_ > 0 && effective_minimum_delay_ms_ > 0) {
    int minimum_delay_packet_q8 =
        (effective_minimum_delay_ms_ << 8) / packet_len_ms_;
    target_level_ = std::max(target_level_, minimum_delay_packet_q8);
  }

  if (maximum_delay_ms_ > 0 && packet_len_ms_ > 0) {
    int maximum_delay_packet_q8 = (maximum_delay_ms_ << 8) / packet_len_ms_;
    target_level_ = std::min(target_level_, maximum_delay_packet_q8);
  }

  // Shift to Q8, then 75%.;
  int max_buffer_packets_q8 =
      static_cast<int>((3 * (max_packets_in_buffer_ << 8)) / 4);
  target_level_ = std::min(target_level_, max_buffer_packets_q8);

  // Sanity check, at least 1 packet (in Q8).
  target_level_ = std::max(target_level_, 1 << 8);
}

int DelayManager::CalculateTargetLevel() {
  int limit_probability = histogram_quantile_;

  int bucket_index = histogram_->Quantile(limit_probability);
  int target_level = 1;
  if (packet_len_ms_ > 0) {
    target_level += bucket_index * kBucketSizeMs / packet_len_ms_;
  }
  base_target_level_ = target_level;

  // Sanity check. |target_level| must be strictly positive.
  target_level = std::max(target_level, 1);
  // Scale to Q8 and assign to member variable.
  target_level_ = target_level << 8;
  return target_level_;
}

int DelayManager::SetPacketAudioLength(int length_ms) {
  if (length_ms <= 0) {
    RTC_LOG_F(LS_ERROR) << "length_ms = " << length_ms;
    return -1;
  }

  packet_len_ms_ = length_ms;
  packet_iat_stopwatch_ = tick_timer_->GetNewStopwatch();
  last_pack_cng_or_dtmf_ = 1;  // TODO(hlundin): Legacy. Remove?
  return 0;
}

void DelayManager::Reset() {
  packet_len_ms_ = 0;  // Packet size unknown.
  histogram_->Reset();
  delay_history_.clear();
  base_target_level_ = 4;
  target_level_ = base_target_level_ << 8;
  packet_iat_stopwatch_ = tick_timer_->GetNewStopwatch();
  last_pack_cng_or_dtmf_ = 1;
}

void DelayManager::ResetPacketIatCount() {
  packet_iat_stopwatch_ = tick_timer_->GetNewStopwatch();
}

void DelayManager::BufferLimits(int* lower_limit, int* higher_limit) const {
  BufferLimits(target_level_, lower_limit, higher_limit);
}

// Note that |low_limit| and |higher_limit| are not assigned to
// |minimum_delay_ms_| and |maximum_delay_ms_| defined by the client of this
// class. They are computed from |target_level| in Q8 and used for decision
// making.
void DelayManager::BufferLimits(int target_level,
                                int* lower_limit,
                                int* higher_limit) const {
  if (!lower_limit || !higher_limit) {
    RTC_LOG_F(LS_ERROR) << "NULL pointers supplied as input";
    assert(false);
    return;
  }

  // |target_level| is in Q8 already.
  *lower_limit = (target_level * 3) / 4;

  if (packet_len_ms_ > 0) {
    *lower_limit =
        std::max(*lower_limit, target_level - kDecelerationTargetLevelOffsetMs /
                                                  packet_len_ms_);
  }

  int window_20ms = 0x7FFF;  // Default large value for legacy bit-exactness.
  if (packet_len_ms_ > 0) {
    window_20ms = (20 << 8) / packet_len_ms_;
  }
  // |higher_limit| is equal to |target_level|, but should at
  // least be 20 ms higher than |lower_limit|.
  *higher_limit = std::max(target_level, *lower_limit + window_20ms);
}

int DelayManager::TargetLevel() const {
  return target_level_;
}

void DelayManager::LastDecodedWasCngOrDtmf(bool it_was) {
  if (it_was) {
    last_pack_cng_or_dtmf_ = 1;
  } else if (last_pack_cng_or_dtmf_ != 0) {
    last_pack_cng_or_dtmf_ = -1;
  }
}

void DelayManager::RegisterEmptyPacket() {
  ++last_seq_no_;
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
  // If |delay_ms| is zero then it unsets the maximum delay and target level is
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

int DelayManager::base_target_level() const {
  return base_target_level_;
}
int DelayManager::last_pack_cng_or_dtmf() const {
  return last_pack_cng_or_dtmf_;
}

void DelayManager::set_last_pack_cng_or_dtmf(int value) {
  last_pack_cng_or_dtmf_ = value;
}

void DelayManager::UpdateEffectiveMinimumDelay() {
  // Clamp |base_minimum_delay_ms_| into the range which can be effectively
  // used.
  const int base_minimum_delay_ms =
      rtc::SafeClamp(base_minimum_delay_ms_, 0, MinimumDelayUpperBound());
  effective_minimum_delay_ms_ =
      std::max(minimum_delay_ms_, base_minimum_delay_ms);
}

int DelayManager::MinimumDelayUpperBound() const {
  // Choose the lowest possible bound discarding 0 cases which mean the value
  // is not set and unconstrained.
  int q75 = MaxBufferTimeQ75();
  q75 = q75 > 0 ? q75 : kMaxBaseMinimumDelayMs;
  const int maximum_delay_ms =
      maximum_delay_ms_ > 0 ? maximum_delay_ms_ : kMaxBaseMinimumDelayMs;
  return std::min(maximum_delay_ms, q75);
}

int DelayManager::MaxBufferTimeQ75() const {
  const int max_buffer_time = max_packets_in_buffer_ * packet_len_ms_;
  return rtc::dchecked_cast<int>(3 * max_buffer_time / 4);
}

}  // namespace webrtc
