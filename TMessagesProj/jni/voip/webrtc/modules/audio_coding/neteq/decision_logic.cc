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

#include "absl/types/optional.h"
#include "api/neteq/neteq.h"
#include "api/neteq/neteq_controller.h"
#include "modules/audio_coding/neteq/packet_arrival_history.h"
#include "modules/audio_coding/neteq/packet_buffer.h"
#include "rtc_base/checks.h"
#include "rtc_base/experiments/struct_parameters_parser.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {

namespace {

constexpr int kPostponeDecodingLevel = 50;
constexpr int kTargetLevelWindowMs = 100;
constexpr int kMaxWaitForPacketMs = 100;
// The granularity of delay adjustments (accelerate/preemptive expand) is 15ms,
// but round up since the clock has a granularity of 10ms.
constexpr int kDelayAdjustmentGranularityMs = 20;
constexpr int kReinitAfterExpandsMs = 1000;

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
      "enable_stable_delay_mode", &enable_stable_delay_mode,          //
      "combine_concealment_decision", &combine_concealment_decision,  //
      "packet_history_size_ms", &packet_history_size_ms,              //
      "cng_timeout_ms", &cng_timeout_ms,                              //
      "deceleration_target_level_offset_ms",
      &deceleration_target_level_offset_ms)
      ->Parse(webrtc::field_trial::FindFullName(
          "WebRTC-Audio-NetEqDecisionLogicConfig"));
  RTC_LOG(LS_INFO) << "NetEq decision logic config:"
                   << " enable_stable_delay_mode=" << enable_stable_delay_mode
                   << " combine_concealment_decision="
                   << combine_concealment_decision
                   << " packet_history_size_ms=" << packet_history_size_ms
                   << " cng_timeout_ms=" << cng_timeout_ms.value_or(-1)
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
    std::unique_ptr<BufferLevelFilter> buffer_level_filter,
    std::unique_ptr<PacketArrivalHistory> packet_arrival_history)
    : delay_manager_(std::move(delay_manager)),
      buffer_level_filter_(std::move(buffer_level_filter)),
      packet_arrival_history_(packet_arrival_history
                                  ? std::move(packet_arrival_history)
                                  : std::make_unique<PacketArrivalHistory>(
                                        config.tick_timer,
                                        config_.packet_history_size_ms)),
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
  packet_arrival_history_->Reset();
}

void DecisionLogic::SetSampleRate(int fs_hz, size_t output_size_samples) {
  // TODO(hlundin): Change to an enumerator and skip assert.
  RTC_DCHECK(fs_hz == 8000 || fs_hz == 16000 || fs_hz == 32000 ||
             fs_hz == 48000);
  sample_rate_khz_ = fs_hz / 1000;
  output_size_samples_ = output_size_samples;
  packet_arrival_history_->set_sample_rate(fs_hz);
}

NetEq::Operation DecisionLogic::GetDecision(const NetEqStatus& status,
                                            bool* reset_decoder) {
  prev_time_scale_ = prev_time_scale_ && IsTimestretch(status.last_mode);
  if (prev_time_scale_) {
    timescale_countdown_ = tick_timer_->GetNewCountdown(kMinTimescaleInterval);
  }
  if (!IsCng(status.last_mode) &&
      !(config_.combine_concealment_decision && IsExpand(status.last_mode))) {
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
  if (!config_.combine_concealment_decision && IsExpand(status.last_mode) &&
      status.generated_noise_samples >
          static_cast<size_t>(kReinitAfterExpandsMs * sample_rate_khz_)) {
    *reset_decoder = true;
    return NetEq::Operation::kNormal;
  }

  if (PostponeDecode(status)) {
    return NoPacket(status);
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

int DecisionLogic::TargetLevelMs() const {
  int target_delay_ms = delay_manager_->TargetDelayMs();
  if (!config_.enable_stable_delay_mode) {
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
  bool inserted = packet_arrival_history_->Insert(info.main_timestamp,
                                                  info.packet_length_samples);
  if (!inserted || packet_arrival_history_->size() < 2) {
    // No meaningful delay estimate unless at least 2 packets have arrived.
    return absl::nullopt;
  }
  int arrival_delay_ms =
      packet_arrival_history_->GetDelayMs(info.main_timestamp);
  bool reordered =
      !packet_arrival_history_->IsNewestRtpTimestamp(info.main_timestamp);
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
  switch (status.last_mode) {
    case NetEq::Mode::kRfc3389Cng:
      return NetEq::Operation::kRfc3389CngNoPacket;
    case NetEq::Mode::kCodecInternalCng: {
      // Stop CNG after a timeout.
      if (config_.cng_timeout_ms &&
          status.generated_noise_samples >
              static_cast<size_t>(*config_.cng_timeout_ms * sample_rate_khz_)) {
        return NetEq::Operation::kExpand;
      }
      return NetEq::Operation::kCodecInternalCng;
    }
    default:
      return status.play_dtmf ? NetEq::Operation::kDtmf
                              : NetEq::Operation::kExpand;
  }
}

NetEq::Operation DecisionLogic::ExpectedPacketAvailable(
    NetEqController::NetEqStatus status) {
  if (!disallow_time_stretching_ && status.last_mode != NetEq::Mode::kExpand &&
      !status.play_dtmf) {
    if (config_.enable_stable_delay_mode) {
      const int playout_delay_ms = GetPlayoutDelayMs(status);
      const int64_t low_limit = TargetLevelMs();
      const int64_t high_limit = low_limit +
                                 packet_arrival_history_->GetMaxDelayMs() +
                                 kDelayAdjustmentGranularityMs;
      if (playout_delay_ms >= high_limit * 4) {
        return NetEq::Operation::kFastAccelerate;
      }
      if (TimescaleAllowed()) {
        if (playout_delay_ms >= high_limit) {
          return NetEq::Operation::kAccelerate;
        }
        if (playout_delay_ms < low_limit) {
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
      if (buffer_level_samples >= high_limit * 4)
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
  // Check if we should continue with an ongoing concealment because the new
  // packet is too far into the future.
  if (config_.combine_concealment_decision || IsCng(status.last_mode)) {
    const int buffer_delay_samples =
        config_.combine_concealment_decision
            ? status.packet_buffer_info.span_samples_wait_time
            : status.packet_buffer_info.span_samples;
    const int buffer_delay_ms = buffer_delay_samples / sample_rate_khz_;
    const int high_limit = TargetLevelMs() + kTargetLevelWindowMs / 2;
    const int low_limit =
        std::max(0, TargetLevelMs() - kTargetLevelWindowMs / 2);
    const bool above_target_delay = buffer_delay_ms > high_limit;
    const bool below_target_delay = buffer_delay_ms < low_limit;
    if ((PacketTooEarly(status) && !above_target_delay) ||
        (below_target_delay && !config_.combine_concealment_decision)) {
      return NoPacket(status);
    }
    uint32_t timestamp_leap =
        status.next_packet->timestamp - status.target_timestamp;
    if (config_.combine_concealment_decision) {
      if (timestamp_leap != status.generated_noise_samples) {
        // The delay was adjusted, reinitialize the buffer level filter.
        buffer_level_filter_->SetFilteredBufferLevel(buffer_delay_samples);
      }
    } else {
      time_stretched_cn_samples_ =
          timestamp_leap - status.generated_noise_samples;
    }
  } else if (IsExpand(status.last_mode) && ShouldContinueExpand(status)) {
    return NoPacket(status);
  }

  // Time to play the next packet.
  switch (status.last_mode) {
    case NetEq::Mode::kExpand:
      return NetEq::Operation::kMerge;
    case NetEq::Mode::kCodecPlc:
    case NetEq::Mode::kRfc3389Cng:
    case NetEq::Mode::kCodecInternalCng:
      return NetEq::Operation::kNormal;
    default:
      return status.play_dtmf ? NetEq::Operation::kDtmf
                              : NetEq::Operation::kExpand;
  }
}

bool DecisionLogic::UnderTargetLevel() const {
  return buffer_level_filter_->filtered_current_level() <
         TargetLevelMs() * sample_rate_khz_;
}

bool DecisionLogic::PostponeDecode(NetEqController::NetEqStatus status) const {
  // Make sure we don't restart audio too soon after CNG or expand to avoid
  // running out of data right away again.
  const size_t min_buffer_level_samples =
      TargetLevelMs() * sample_rate_khz_ * kPostponeDecodingLevel / 100;
  const size_t buffer_level_samples =
      config_.combine_concealment_decision
          ? status.packet_buffer_info.span_samples_wait_time
          : status.packet_buffer_info.span_samples;
  if (buffer_level_samples >= min_buffer_level_samples) {
    return false;
  }
  // Don't postpone decoding if there is a future DTX packet in the packet
  // buffer.
  if (status.packet_buffer_info.dtx_or_cng) {
    return false;
  }
  // Continue CNG until the buffer is at least at the minimum level.
  if (config_.combine_concealment_decision && IsCng(status.last_mode)) {
    return true;
  }
  // Only continue expand if the mute factor is low enough (otherwise the
  // expansion was short enough to not be noticable). Note that the MuteFactor
  // is in Q14, so a value of 16384 corresponds to 1.
  if (IsExpand(status.last_mode) && status.expand_mutefactor < 16384 / 2) {
    return true;
  }
  return false;
}

bool DecisionLogic::ReinitAfterExpands(
    NetEqController::NetEqStatus status) const {
  const uint32_t timestamp_leap =
      status.next_packet->timestamp - status.target_timestamp;
  return timestamp_leap >=
         static_cast<uint32_t>(kReinitAfterExpandsMs * sample_rate_khz_);
}

bool DecisionLogic::PacketTooEarly(NetEqController::NetEqStatus status) const {
  const uint32_t timestamp_leap =
      status.next_packet->timestamp - status.target_timestamp;
  return timestamp_leap > status.generated_noise_samples;
}

bool DecisionLogic::MaxWaitForPacket(
    NetEqController::NetEqStatus status) const {
  return status.generated_noise_samples >=
         static_cast<size_t>(kMaxWaitForPacketMs * sample_rate_khz_);
}

bool DecisionLogic::ShouldContinueExpand(
    NetEqController::NetEqStatus status) const {
  return !ReinitAfterExpands(status) && !MaxWaitForPacket(status) &&
         PacketTooEarly(status) && UnderTargetLevel();
}

int DecisionLogic::GetPlayoutDelayMs(
    NetEqController::NetEqStatus status) const {
  uint32_t playout_timestamp =
      status.target_timestamp - status.sync_buffer_samples;
  return packet_arrival_history_->GetDelayMs(playout_timestamp);
}

}  // namespace webrtc
