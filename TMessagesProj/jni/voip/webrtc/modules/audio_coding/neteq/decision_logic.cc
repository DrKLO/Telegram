/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/decision_logic.h"

#include <stdio.h>

#include <cstdint>
#include <memory>
#include <string>

#include "absl/types/optional.h"
#include "api/neteq/neteq.h"
#include "api/neteq/neteq_controller.h"
#include "modules/audio_coding/neteq/packet_arrival_history.h"
#include "modules/audio_coding/neteq/packet_buffer.h"
#include "rtc_base/checks.h"
#include "rtc_base/experiments/field_trial_parser.h"
#include "rtc_base/experiments/struct_parameters_parser.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {

namespace {

constexpr int kPostponeDecodingLevel = 50;
constexpr int kTargetLevelWindowMs = 100;
constexpr int kMaxWaitForPacketTicks = 10;
// The granularity of delay adjustments (accelerate/preemptive expand) is 15ms,
// but round up since the clock has a granularity of 10ms.
constexpr int kDelayAdjustmentGranularityMs = 20;

std::unique_ptr<DelayManager> CreateDelayManager(
    const NetEqController::Config& neteq_config) {
  DelayManager::Config config;
  config.max_packets_in_buffer = neteq_config.max_packets_in_buffer;
  config.base_minimum_delay_ms = neteq_config.base_min_delay_ms;
  config.Log();
  return std::make_unique<DelayManager>(config, neteq_config.tick_timer);
}

bool IsTimestretch(NetEq::Mode mode) {
  return mode == NetEq::Mode::kAccelerateSuccess ||
         mode == NetEq::Mode::kAccelerateLowEnergy ||
         mode == NetEq::Mode::kPreemptiveExpandSuccess ||
         mode == NetEq::Mode::kPreemptiveExpandLowEnergy;
}

bool IsCng(NetEq::Mode mode) {
  return mode == NetEq::Mode::kRfc3389Cng ||
         mode == NetEq::Mode::kCodecInternalCng;
}

bool IsExpand(NetEq::Mode mode) {
  return mode == NetEq::Mode::kExpand || mode == NetEq::Mode::kCodecPlc;
}

}  // namespace

DecisionLogic::Config::Config() {
  StructParametersParser::Create(
      "enable_stable_playout_delay", &enable_stable_playout_delay,  //
      "reinit_after_expands", &reinit_after_expands,                //
      "packet_history_size_ms", &packet_history_size_ms,            //
      "deceleration_target_level_offset_ms",
      &deceleration_target_level_offset_ms)
      ->Parse(webrtc::field_trial::FindFullName(
          "WebRTC-Audio-NetEqDecisionLogicConfig"));
  RTC_LOG(LS_INFO) << "NetEq decision logic config:"
                   << " enable_stable_playout_delay="
                   << enable_stable_playout_delay
                   << " reinit_after_expands=" << reinit_after_expands
                   << " packet_history_size_ms=" << packet_history_size_ms
                   << " deceleration_target_level_offset_ms="
                   << deceleration_target_level_offset_ms;
}

DecisionLogic::DecisionLogic(NetEqController::Config config)
    : DecisionLogic(config,
                    CreateDelayManager(config),
                    std::make_unique<BufferLevelFilter>()) {}

DecisionLogic::DecisionLogic(
    NetEqController::Config config,
    std::unique_ptr<DelayManager> delay_manager,
    std::unique_ptr<BufferLevelFilter> buffer_level_filter)
    : delay_manager_(std::move(delay_manager)),
      buffer_level_filter_(std::move(buffer_level_filter)),
      packet_arrival_history_(config_.packet_history_size_ms),
      tick_timer_(config.tick_timer),
      disallow_time_stretching_(!config.allow_time_stretching),
      timescale_countdown_(
          tick_timer_->GetNewCountdown(kMinTimescaleInterval + 1)) {}

DecisionLogic::~DecisionLogic() = default;

void DecisionLogic::SoftReset() {
  packet_length_samples_ = 0;
  sample_memory_ = 0;
  prev_time_scale_ = false;
  timescale_countdown_ =
      tick_timer_->GetNewCountdown(kMinTimescaleInterval + 1);
  time_stretched_cn_samples_ = 0;
  delay_manager_->Reset();
  buffer_level_filter_->Reset();
  packet_arrival_history_.Reset();
  last_playout_delay_ms_ = 0;
}

void DecisionLogic::SetSampleRate(int fs_hz, size_t output_size_samples) {
  // TODO(hlundin): Change to an enumerator and skip assert.
  RTC_DCHECK(fs_hz == 8000 || fs_hz == 16000 || fs_hz == 32000 ||
             fs_hz == 48000);
  sample_rate_khz_ = fs_hz / 1000;
  output_size_samples_ = output_size_samples;
  packet_arrival_history_.set_sample_rate(fs_hz);
}

NetEq::Operation DecisionLogic::GetDecision(const NetEqStatus& status,
                                            bool* reset_decoder) {
  // If last mode was CNG (or Expand, since this could be covering up for
  // a lost CNG packet), remember that CNG is on. This is needed if comfort
  // noise is interrupted by DTMF.
  if (status.last_mode == NetEq::Mode::kRfc3389Cng) {
    cng_state_ = kCngRfc3389On;
  } else if (status.last_mode == NetEq::Mode::kCodecInternalCng) {
    cng_state_ = kCngInternalOn;
  }

  if (IsExpand(status.last_mode)) {
    ++num_consecutive_expands_;
  } else {
    num_consecutive_expands_ = 0;
  }

  if (!IsExpand(status.last_mode) && !IsCng(status.last_mode)) {
    last_playout_delay_ms_ = GetPlayoutDelayMs(status);
  }

  prev_time_scale_ = prev_time_scale_ && IsTimestretch(status.last_mode);
  if (prev_time_scale_) {
    timescale_countdown_ = tick_timer_->GetNewCountdown(kMinTimescaleInterval);
  }
  if (!IsCng(status.last_mode)) {
    FilterBufferLevel(status.packet_buffer_info.span_samples);
  }

  // Guard for errors, to avoid getting stuck in error mode.
  if (status.last_mode == NetEq::Mode::kError) {
    if (!status.next_packet) {
      return NetEq::Operation::kExpand;
    } else {
      // Use kUndefined to flag for a reset.
      return NetEq::Operation::kUndefined;
    }
  }

  if (status.next_packet && status.next_packet->is_cng) {
    return CngOperation(status);
  }

  // Handle the case with no packet at all available (except maybe DTMF).
  if (!status.next_packet) {
    return NoPacket(status);
  }

  // If the expand period was very long, reset NetEQ since it is likely that the
  // sender was restarted.
  if (num_consecutive_expands_ > config_.reinit_after_expands) {
    *reset_decoder = true;
    return NetEq::Operation::kNormal;
  }

  // Make sure we don't restart audio too soon after an expansion to avoid
  // running out of data right away again. We should only wait if there are no
  // DTX or CNG packets in the buffer (otherwise we should just play out what we
  // have, since we cannot know the exact duration of DTX or CNG packets), and
  // if the mute factor is low enough (otherwise the expansion was short enough
  // to not be noticable).
  // Note that the MuteFactor is in Q14, so a value of 16384 corresponds to 1.
  const int target_level_samples = TargetLevelMs() * sample_rate_khz_;
  if (!config_.enable_stable_playout_delay && IsExpand(status.last_mode) &&
      status.expand_mutefactor < 16384 / 2 &&
      status.packet_buffer_info.span_samples <
          static_cast<size_t>(target_level_samples * kPostponeDecodingLevel /
                              100) &&
      !status.packet_buffer_info.dtx_or_cng) {
    return NetEq::Operation::kExpand;
  }

  const uint32_t five_seconds_samples =
      static_cast<uint32_t>(5000 * sample_rate_khz_);
  // Check if the required packet is available.
  if (status.target_timestamp == status.next_packet->timestamp) {
    return ExpectedPacketAvailable(status);
  }
  if (!PacketBuffer::IsObsoleteTimestamp(status.next_packet->timestamp,
                                         status.target_timestamp,
                                         five_seconds_samples)) {
    return FuturePacketAvailable(status);
  }
  // This implies that available_timestamp < target_timestamp, which can
  // happen when a new stream or codec is received. Signal for a reset.
  return NetEq::Operation::kUndefined;
}

void DecisionLogic::NotifyMutedState() {
  ++num_consecutive_expands_;
}

int DecisionLogic::TargetLevelMs() const {
  int target_delay_ms = delay_manager_->TargetDelayMs();
  if (!config_.enable_stable_playout_delay) {
    target_delay_ms =
        std::max(target_delay_ms,
                 static_cast<int>(packet_length_samples_ / sample_rate_khz_));
  }
  return target_delay_ms;
}

int DecisionLogic::UnlimitedTargetLevelMs() const {
  return delay_manager_->UnlimitedTargetLevelMs();
}

int DecisionLogic::GetFilteredBufferLevel() const {
  if (config_.enable_stable_playout_delay) {
    return last_playout_delay_ms_ * sample_rate_khz_;
  }
  return buffer_level_filter_->filtered_current_level();
}

absl::optional<int> DecisionLogic::PacketArrived(
    int fs_hz,
    bool should_update_stats,
    const PacketArrivedInfo& info) {
  buffer_flush_ = buffer_flush_ || info.buffer_flush;
  if (!should_update_stats || info.is_cng_or_dtmf) {
    return absl::nullopt;
  }
  if (info.packet_length_samples > 0 && fs_hz > 0 &&
      info.packet_length_samples != packet_length_samples_) {
    packet_length_samples_ = info.packet_length_samples;
    delay_manager_->SetPacketAudioLength(packet_length_samples_ * 1000 / fs_hz);
  }
  int64_t time_now_ms = tick_timer_->ticks() * tick_timer_->ms_per_tick();
  packet_arrival_history_.Insert(info.main_timestamp, time_now_ms);
  if (packet_arrival_history_.size() < 2) {
    // No meaningful delay estimate unless at least 2 packets have arrived.
    return absl::nullopt;
  }
  int arrival_delay_ms =
      packet_arrival_history_.GetDelayMs(info.main_timestamp, time_now_ms);
  bool reordered =
      !packet_arrival_history_.IsNewestRtpTimestamp(info.main_timestamp);
  delay_manager_->Update(arrival_delay_ms, reordered);
  return arrival_delay_ms;
}

void DecisionLogic::FilterBufferLevel(size_t buffer_size_samples) {
  buffer_level_filter_->SetTargetBufferLevel(TargetLevelMs());

  int time_stretched_samples = time_stretched_cn_samples_;
  if (prev_time_scale_) {
    time_stretched_samples += sample_memory_;
  }

  if (buffer_flush_) {
    buffer_level_filter_->SetFilteredBufferLevel(buffer_size_samples);
    buffer_flush_ = false;
  } else {
    buffer_level_filter_->Update(buffer_size_samples, time_stretched_samples);
  }
  prev_time_scale_ = false;
  time_stretched_cn_samples_ = 0;
}

NetEq::Operation DecisionLogic::CngOperation(
    NetEqController::NetEqStatus status) {
  // Signed difference between target and available timestamp.
  int32_t timestamp_diff = static_cast<int32_t>(
      static_cast<uint32_t>(status.generated_noise_samples +
                            status.target_timestamp) -
      status.next_packet->timestamp);
  int optimal_level_samp = TargetLevelMs() * sample_rate_khz_;
  const int64_t excess_waiting_time_samp =
      -static_cast<int64_t>(timestamp_diff) - optimal_level_samp;

  if (excess_waiting_time_samp > optimal_level_samp / 2) {
    // The waiting time for this packet will be longer than 1.5
    // times the wanted buffer delay. Apply fast-forward to cut the
    // waiting time down to the optimal.
    noise_fast_forward_ = rtc::saturated_cast<size_t>(noise_fast_forward_ +
                                                      excess_waiting_time_samp);
    timestamp_diff =
        rtc::saturated_cast<int32_t>(timestamp_diff + excess_waiting_time_samp);
  }

  if (timestamp_diff < 0 && status.last_mode == NetEq::Mode::kRfc3389Cng) {
    // Not time to play this packet yet. Wait another round before using this
    // packet. Keep on playing CNG from previous CNG parameters.
    return NetEq::Operation::kRfc3389CngNoPacket;
  } else {
    // Otherwise, go for the CNG packet now.
    noise_fast_forward_ = 0;
    return NetEq::Operation::kRfc3389Cng;
  }
}

NetEq::Operation DecisionLogic::NoPacket(NetEqController::NetEqStatus status) {
  if (cng_state_ == kCngRfc3389On) {
    // Keep on playing comfort noise.
    return NetEq::Operation::kRfc3389CngNoPacket;
  } else if (cng_state_ == kCngInternalOn) {
    // Keep on playing codec internal comfort noise.
    return NetEq::Operation::kCodecInternalCng;
  } else if (status.play_dtmf) {
    return NetEq::Operation::kDtmf;
  } else {
    // Nothing to play, do expand.
    return NetEq::Operation::kExpand;
  }
}

NetEq::Operation DecisionLogic::ExpectedPacketAvailable(
    NetEqController::NetEqStatus status) {
  if (!disallow_time_stretching_ && status.last_mode != NetEq::Mode::kExpand &&
      !status.play_dtmf) {
    if (config_.enable_stable_playout_delay) {
      const int playout_delay_ms = GetPlayoutDelayMs(status);
      if (playout_delay_ms >= HighThreshold() << 2) {
        return NetEq::Operation::kFastAccelerate;
      }
      if (TimescaleAllowed()) {
        if (playout_delay_ms >= HighThreshold()) {
          return NetEq::Operation::kAccelerate;
        }
        if (playout_delay_ms < LowThreshold()) {
          return NetEq::Operation::kPreemptiveExpand;
        }
      }
    } else {
      const int target_level_samples = TargetLevelMs() * sample_rate_khz_;
      const int low_limit = std::max(
          target_level_samples * 3 / 4,
          target_level_samples -
              config_.deceleration_target_level_offset_ms * sample_rate_khz_);
      const int high_limit = std::max(
          target_level_samples,
          low_limit + kDelayAdjustmentGranularityMs * sample_rate_khz_);

      const int buffer_level_samples =
          buffer_level_filter_->filtered_current_level();
      if (buffer_level_samples >= high_limit << 2)
        return NetEq::Operation::kFastAccelerate;
      if (TimescaleAllowed()) {
        if (buffer_level_samples >= high_limit)
          return NetEq::Operation::kAccelerate;
        if (buffer_level_samples < low_limit)
          return NetEq::Operation::kPreemptiveExpand;
      }
    }
  }
  return NetEq::Operation::kNormal;
}

NetEq::Operation DecisionLogic::FuturePacketAvailable(
    NetEqController::NetEqStatus status) {
  // Required packet is not available, but a future packet is.
  // Check if we should continue with an ongoing expand because the new packet
  // is too far into the future.
  if (IsExpand(status.last_mode) && ShouldContinueExpand(status)) {
    if (status.play_dtmf) {
      // Still have DTMF to play, so do not do expand.
      return NetEq::Operation::kDtmf;
    } else {
      // Nothing to play.
      return NetEq::Operation::kExpand;
    }
  }

  if (status.last_mode == NetEq::Mode::kCodecPlc) {
    return NetEq::Operation::kNormal;
  }

  // If previous was comfort noise, then no merge is needed.
  if (IsCng(status.last_mode)) {
    uint32_t timestamp_leap =
        status.next_packet->timestamp - status.target_timestamp;
    const bool generated_enough_noise =
        status.generated_noise_samples >= timestamp_leap;

    int playout_delay_ms = GetNextPacketDelayMs(status);
    const bool above_target_delay = playout_delay_ms > HighThresholdCng();
    const bool below_target_delay = playout_delay_ms < LowThresholdCng();
    // Keep the delay same as before CNG, but make sure that it is within the
    // target window.
    if ((generated_enough_noise && !below_target_delay) || above_target_delay) {
      time_stretched_cn_samples_ =
          timestamp_leap - status.generated_noise_samples;
      return NetEq::Operation::kNormal;
    }

    if (status.last_mode == NetEq::Mode::kRfc3389Cng) {
      return NetEq::Operation::kRfc3389CngNoPacket;
    }
    return NetEq::Operation::kCodecInternalCng;
  }

  // Do not merge unless we have done an expand before.
  if (status.last_mode == NetEq::Mode::kExpand) {
    return NetEq::Operation::kMerge;
  } else if (status.play_dtmf) {
    // Play DTMF instead of expand.
    return NetEq::Operation::kDtmf;
  } else {
    return NetEq::Operation::kExpand;
  }
}

bool DecisionLogic::UnderTargetLevel() const {
  return buffer_level_filter_->filtered_current_level() <
         TargetLevelMs() * sample_rate_khz_;
}

bool DecisionLogic::ReinitAfterExpands(uint32_t timestamp_leap) const {
  return timestamp_leap >= static_cast<uint32_t>(output_size_samples_ *
                                                 config_.reinit_after_expands);
}

bool DecisionLogic::PacketTooEarly(uint32_t timestamp_leap) const {
  return timestamp_leap >
         static_cast<uint32_t>(output_size_samples_ * num_consecutive_expands_);
}

bool DecisionLogic::MaxWaitForPacket() const {
  return num_consecutive_expands_ >= kMaxWaitForPacketTicks;
}

bool DecisionLogic::ShouldContinueExpand(
    NetEqController::NetEqStatus status) const {
  uint32_t timestamp_leap =
      status.next_packet->timestamp - status.target_timestamp;
  if (config_.enable_stable_playout_delay) {
    return GetNextPacketDelayMs(status) < HighThreshold() &&
           PacketTooEarly(timestamp_leap);
  }
  return !ReinitAfterExpands(timestamp_leap) && !MaxWaitForPacket() &&
         PacketTooEarly(timestamp_leap) && UnderTargetLevel();
}

int DecisionLogic::GetNextPacketDelayMs(
    NetEqController::NetEqStatus status) const {
  if (config_.enable_stable_playout_delay) {
    return packet_arrival_history_.GetDelayMs(
        status.next_packet->timestamp,
        tick_timer_->ticks() * tick_timer_->ms_per_tick());
  }
  return status.packet_buffer_info.span_samples / sample_rate_khz_;
}

int DecisionLogic::GetPlayoutDelayMs(
    NetEqController::NetEqStatus status) const {
  uint32_t playout_timestamp =
      status.target_timestamp - status.sync_buffer_samples;
  return packet_arrival_history_.GetDelayMs(
      playout_timestamp, tick_timer_->ticks() * tick_timer_->ms_per_tick());
}

int DecisionLogic::LowThreshold() const {
  int target_delay_ms = TargetLevelMs();
  return std::max(
      target_delay_ms * 3 / 4,
      target_delay_ms - config_.deceleration_target_level_offset_ms);
}

int DecisionLogic::HighThreshold() const {
  if (config_.enable_stable_playout_delay) {
    return std::max(TargetLevelMs(), packet_arrival_history_.GetMaxDelayMs()) +
           kDelayAdjustmentGranularityMs;
  }
  return std::max(TargetLevelMs(),
                  LowThreshold() + kDelayAdjustmentGranularityMs);
}

int DecisionLogic::LowThresholdCng() const {
  if (config_.enable_stable_playout_delay) {
    return LowThreshold();
  }
  return std::max(0, TargetLevelMs() - kTargetLevelWindowMs / 2);
}

int DecisionLogic::HighThresholdCng() const {
  if (config_.enable_stable_playout_delay) {
    return HighThreshold();
  }
  return TargetLevelMs() + kTargetLevelWindowMs / 2;
}

}  // namespace webrtc
