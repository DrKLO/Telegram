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

#include <assert.h>
#include <stdio.h>

#include <string>

#include "absl/types/optional.h"
#include "modules/audio_coding/neteq/packet_buffer.h"
#include "rtc_base/checks.h"
#include "rtc_base/experiments/field_trial_parser.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "system_wrappers/include/field_trial.h"

namespace {

constexpr int kPostponeDecodingLevel = 50;
constexpr int kDefaultTargetLevelWindowMs = 100;

}  // namespace

namespace webrtc {

DecisionLogic::DecisionLogic(NetEqController::Config config)
    : delay_manager_(DelayManager::Create(config.max_packets_in_buffer,
                                          config.base_min_delay_ms,
                                          config.enable_rtx_handling,
                                          config.tick_timer)),
      tick_timer_(config.tick_timer),
      disallow_time_stretching_(!config.allow_time_stretching),
      timescale_countdown_(
          tick_timer_->GetNewCountdown(kMinTimescaleInterval + 1)),
      estimate_dtx_delay_("estimate_dtx_delay", false),
      time_stretch_cn_("time_stretch_cn", false),
      target_level_window_ms_("target_level_window",
                              kDefaultTargetLevelWindowMs,
                              0,
                              absl::nullopt) {
  const std::string field_trial_name =
      field_trial::FindFullName("WebRTC-Audio-NetEqDecisionLogicSettings");
  ParseFieldTrial(
      {&estimate_dtx_delay_, &time_stretch_cn_, &target_level_window_ms_},
      field_trial_name);
  RTC_LOG(LS_INFO) << "NetEq decision logic settings:"
                      " estimate_dtx_delay="
                   << estimate_dtx_delay_
                   << " time_stretch_cn=" << time_stretch_cn_
                   << " target_level_window_ms=" << target_level_window_ms_;
}

DecisionLogic::~DecisionLogic() = default;

void DecisionLogic::Reset() {
  cng_state_ = kCngOff;
  noise_fast_forward_ = 0;
  packet_length_samples_ = 0;
  sample_memory_ = 0;
  prev_time_scale_ = false;
  timescale_countdown_.reset();
  num_consecutive_expands_ = 0;
  time_stretched_cn_samples_ = 0;
}

void DecisionLogic::SoftReset() {
  packet_length_samples_ = 0;
  sample_memory_ = 0;
  prev_time_scale_ = false;
  timescale_countdown_ =
      tick_timer_->GetNewCountdown(kMinTimescaleInterval + 1);
  time_stretched_cn_samples_ = 0;
  delay_manager_->Reset();
  buffer_level_filter_.Reset();
}

void DecisionLogic::SetSampleRate(int fs_hz, size_t output_size_samples) {
  // TODO(hlundin): Change to an enumerator and skip assert.
  assert(fs_hz == 8000 || fs_hz == 16000 || fs_hz == 32000 || fs_hz == 48000);
  sample_rate_ = fs_hz;
  output_size_samples_ = output_size_samples;
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

  size_t cur_size_samples = estimate_dtx_delay_
                                ? status.packet_buffer_info.span_samples
                                : status.packet_buffer_info.num_samples;
  prev_time_scale_ =
      prev_time_scale_ &&
      (status.last_mode == NetEq::Mode::kAccelerateSuccess ||
       status.last_mode == NetEq::Mode::kAccelerateLowEnergy ||
       status.last_mode == NetEq::Mode::kPreemptiveExpandSuccess ||
       status.last_mode == NetEq::Mode::kPreemptiveExpandLowEnergy);

  // Do not update buffer history if currently playing CNG since it will bias
  // the filtered buffer level.
  if (status.last_mode != NetEq::Mode::kRfc3389Cng &&
      status.last_mode != NetEq::Mode::kCodecInternalCng &&
      !(status.next_packet && status.next_packet->is_dtx &&
        !estimate_dtx_delay_)) {
    FilterBufferLevel(cur_size_samples);
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
    return CngOperation(status.last_mode, status.target_timestamp,
                        status.next_packet->timestamp,
                        status.generated_noise_samples);
  }

  // Handle the case with no packet at all available (except maybe DTMF).
  if (!status.next_packet) {
    return NoPacket(status.play_dtmf);
  }

  // If the expand period was very long, reset NetEQ since it is likely that the
  // sender was restarted.
  if (num_consecutive_expands_ > kReinitAfterExpands) {
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
  const size_t current_span =
      estimate_dtx_delay_ ? status.packet_buffer_info.span_samples
                          : status.packet_buffer_info.span_samples_no_dtx;
  if ((status.last_mode == NetEq::Mode::kExpand ||
       status.last_mode == NetEq::Mode::kCodecPlc) &&
      status.expand_mutefactor < 16384 / 2 &&
      current_span<static_cast<size_t>(delay_manager_->TargetLevel() *
                                       packet_length_samples_ *
                                       kPostponeDecodingLevel / 100)>> 8 &&
      !status.packet_buffer_info.dtx_or_cng) {
    return NetEq::Operation::kExpand;
  }

  const uint32_t five_seconds_samples = static_cast<uint32_t>(5 * sample_rate_);
  // Check if the required packet is available.
  if (status.target_timestamp == status.next_packet->timestamp) {
    return ExpectedPacketAvailable(status.last_mode, status.play_dtmf);
  } else if (!PacketBuffer::IsObsoleteTimestamp(status.next_packet->timestamp,
                                                status.target_timestamp,
                                                five_seconds_samples)) {
    return FuturePacketAvailable(
        status.last_packet_samples, status.last_mode, status.target_timestamp,
        status.next_packet->timestamp, status.play_dtmf,
        status.generated_noise_samples, status.packet_buffer_info.span_samples,
        status.packet_buffer_info.num_packets);
  } else {
    // This implies that available_timestamp < target_timestamp, which can
    // happen when a new stream or codec is received. Signal for a reset.
    return NetEq::Operation::kUndefined;
  }
}

void DecisionLogic::ExpandDecision(NetEq::Operation operation) {
  if (operation == NetEq::Operation::kExpand) {
    num_consecutive_expands_++;
  } else {
    num_consecutive_expands_ = 0;
  }
}

absl::optional<int> DecisionLogic::PacketArrived(bool last_cng_or_dtmf,
                                                 size_t packet_length_samples,
                                                 bool should_update_stats,
                                                 uint16_t main_sequence_number,
                                                 uint32_t main_timestamp,
                                                 int fs_hz) {
  delay_manager_->LastDecodedWasCngOrDtmf(last_cng_or_dtmf);
  absl::optional<int> relative_delay;
  if (delay_manager_->last_pack_cng_or_dtmf() == 0) {
    // Calculate the total speech length carried in each packet.
    if (packet_length_samples > 0 &&
        packet_length_samples != packet_length_samples_) {
      packet_length_samples_ = packet_length_samples;
      delay_manager_->SetPacketAudioLength(
          rtc::dchecked_cast<int>((1000 * packet_length_samples) / fs_hz));
    }

    // Update statistics.
    if (should_update_stats) {
      relative_delay =
          delay_manager_->Update(main_sequence_number, main_timestamp, fs_hz);
    }
  } else if (delay_manager_->last_pack_cng_or_dtmf() == -1) {
    // This is first "normal" packet after CNG or DTMF.
    // Reset packet time counter and measure time until next packet,
    // but don't update statistics.
    delay_manager_->set_last_pack_cng_or_dtmf(0);
    delay_manager_->ResetPacketIatCount();
  }
  return relative_delay;
}

void DecisionLogic::FilterBufferLevel(size_t buffer_size_samples) {
  buffer_level_filter_.SetTargetBufferLevel(
      delay_manager_->base_target_level());

  int time_stretched_samples = time_stretched_cn_samples_;
  if (prev_time_scale_) {
    time_stretched_samples += sample_memory_;
    timescale_countdown_ = tick_timer_->GetNewCountdown(kMinTimescaleInterval);
  }

  buffer_level_filter_.Update(buffer_size_samples, time_stretched_samples);
  prev_time_scale_ = false;
  time_stretched_cn_samples_ = 0;
}

NetEq::Operation DecisionLogic::CngOperation(NetEq::Mode prev_mode,
                                             uint32_t target_timestamp,
                                             uint32_t available_timestamp,
                                             size_t generated_noise_samples) {
  // Signed difference between target and available timestamp.
  int32_t timestamp_diff = static_cast<int32_t>(
      static_cast<uint32_t>(generated_noise_samples + target_timestamp) -
      available_timestamp);
  int32_t optimal_level_samp = static_cast<int32_t>(
      (delay_manager_->TargetLevel() * packet_length_samples_) >> 8);
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

  if (timestamp_diff < 0 && prev_mode == NetEq::Mode::kRfc3389Cng) {
    // Not time to play this packet yet. Wait another round before using this
    // packet. Keep on playing CNG from previous CNG parameters.
    return NetEq::Operation::kRfc3389CngNoPacket;
  } else {
    // Otherwise, go for the CNG packet now.
    noise_fast_forward_ = 0;
    return NetEq::Operation::kRfc3389Cng;
  }
}

NetEq::Operation DecisionLogic::NoPacket(bool play_dtmf) {
  if (cng_state_ == kCngRfc3389On) {
    // Keep on playing comfort noise.
    return NetEq::Operation::kRfc3389CngNoPacket;
  } else if (cng_state_ == kCngInternalOn) {
    // Keep on playing codec internal comfort noise.
    return NetEq::Operation::kCodecInternalCng;
  } else if (play_dtmf) {
    return NetEq::Operation::kDtmf;
  } else {
    // Nothing to play, do expand.
    return NetEq::Operation::kExpand;
  }
}

NetEq::Operation DecisionLogic::ExpectedPacketAvailable(NetEq::Mode prev_mode,
                                                        bool play_dtmf) {
  if (!disallow_time_stretching_ && prev_mode != NetEq::Mode::kExpand &&
      !play_dtmf) {
    // Check criterion for time-stretching. The values are in number of packets
    // in Q8.
    int low_limit, high_limit;
    delay_manager_->BufferLimits(&low_limit, &high_limit);
    int buffer_level_packets = 0;
    if (packet_length_samples_ > 0) {
      buffer_level_packets =
          ((1 << 8) * buffer_level_filter_.filtered_current_level()) /
          packet_length_samples_;
    }
    if (buffer_level_packets >= high_limit << 2)
      return NetEq::Operation::kFastAccelerate;
    if (TimescaleAllowed()) {
      if (buffer_level_packets >= high_limit)
        return NetEq::Operation::kAccelerate;
      if (buffer_level_packets < low_limit)
        return NetEq::Operation::kPreemptiveExpand;
    }
  }
  return NetEq::Operation::kNormal;
}

NetEq::Operation DecisionLogic::FuturePacketAvailable(
    size_t decoder_frame_length,
    NetEq::Mode prev_mode,
    uint32_t target_timestamp,
    uint32_t available_timestamp,
    bool play_dtmf,
    size_t generated_noise_samples,
    size_t span_samples_in_packet_buffer,
    size_t num_packets_in_packet_buffer) {
  // Required packet is not available, but a future packet is.
  // Check if we should continue with an ongoing expand because the new packet
  // is too far into the future.
  uint32_t timestamp_leap = available_timestamp - target_timestamp;
  if ((prev_mode == NetEq::Mode::kExpand ||
       prev_mode == NetEq::Mode::kCodecPlc) &&
      !ReinitAfterExpands(timestamp_leap) && !MaxWaitForPacket() &&
      PacketTooEarly(timestamp_leap) && UnderTargetLevel()) {
    if (play_dtmf) {
      // Still have DTMF to play, so do not do expand.
      return NetEq::Operation::kDtmf;
    } else {
      // Nothing to play.
      return NetEq::Operation::kExpand;
    }
  }

  if (prev_mode == NetEq::Mode::kCodecPlc) {
    return NetEq::Operation::kNormal;
  }

  // If previous was comfort noise, then no merge is needed.
  if (prev_mode == NetEq::Mode::kRfc3389Cng ||
      prev_mode == NetEq::Mode::kCodecInternalCng) {
    size_t cur_size_samples =
        estimate_dtx_delay_
            ? cur_size_samples = span_samples_in_packet_buffer
            : num_packets_in_packet_buffer * decoder_frame_length;
    // Target level is in number of packets in Q8.
    const size_t target_level_samples =
        (delay_manager_->TargetLevel() * packet_length_samples_) >> 8;
    const bool generated_enough_noise =
        static_cast<uint32_t>(generated_noise_samples + target_timestamp) >=
        available_timestamp;

    if (time_stretch_cn_) {
      const size_t target_threshold_samples =
          target_level_window_ms_ / 2 * (sample_rate_ / 1000);
      const bool above_target_window =
          cur_size_samples > target_level_samples + target_threshold_samples;
      const bool below_target_window =
          target_level_samples > target_threshold_samples &&
          cur_size_samples < target_level_samples - target_threshold_samples;
      // Keep the delay same as before CNG, but make sure that it is within the
      // target window.
      if ((generated_enough_noise && !below_target_window) ||
          above_target_window) {
        time_stretched_cn_samples_ = timestamp_leap - generated_noise_samples;
        return NetEq::Operation::kNormal;
      }
    } else {
      // Keep the same delay as before the CNG, but make sure that the number of
      // samples in buffer is no higher than 4 times the optimal level.
      if (generated_enough_noise ||
          cur_size_samples > target_level_samples * 4) {
        // Time to play this new packet.
        return NetEq::Operation::kNormal;
      }
    }

    // Too early to play this new packet; keep on playing comfort noise.
    if (prev_mode == NetEq::Mode::kRfc3389Cng) {
      return NetEq::Operation::kRfc3389CngNoPacket;
    }
    // prevPlayMode == kModeCodecInternalCng.
    return NetEq::Operation::kCodecInternalCng;
  }

  // Do not merge unless we have done an expand before.
  if (prev_mode == NetEq::Mode::kExpand) {
    return NetEq::Operation::kMerge;
  } else if (play_dtmf) {
    // Play DTMF instead of expand.
    return NetEq::Operation::kDtmf;
  } else {
    return NetEq::Operation::kExpand;
  }
}

bool DecisionLogic::UnderTargetLevel() const {
  int buffer_level_packets = 0;
  if (packet_length_samples_ > 0) {
    buffer_level_packets =
        ((1 << 8) * buffer_level_filter_.filtered_current_level()) /
        packet_length_samples_;
  }
  return buffer_level_packets <= delay_manager_->TargetLevel();
}

bool DecisionLogic::ReinitAfterExpands(uint32_t timestamp_leap) const {
  return timestamp_leap >=
         static_cast<uint32_t>(output_size_samples_ * kReinitAfterExpands);
}

bool DecisionLogic::PacketTooEarly(uint32_t timestamp_leap) const {
  return timestamp_leap >
         static_cast<uint32_t>(output_size_samples_ * num_consecutive_expands_);
}

bool DecisionLogic::MaxWaitForPacket() const {
  return num_consecutive_expands_ >= kMaxWaitForPacket;
}

}  // namespace webrtc
