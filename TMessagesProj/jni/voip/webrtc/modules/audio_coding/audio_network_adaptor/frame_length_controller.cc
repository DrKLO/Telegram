/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/audio_network_adaptor/frame_length_controller.h"

#include <algorithm>
#include <iterator>
#include <utility>

#include "rtc_base/checks.h"

namespace webrtc {

namespace {
constexpr int kPreventOveruseMarginBps = 5000;

int OverheadRateBps(size_t overhead_bytes_per_packet, int frame_length_ms) {
  return static_cast<int>(overhead_bytes_per_packet * 8 * 1000 /
                          frame_length_ms);
}
}  // namespace

FrameLengthController::Config::Config(
    const std::set<int>& encoder_frame_lengths_ms,
    int initial_frame_length_ms,
    int min_encoder_bitrate_bps,
    float fl_increasing_packet_loss_fraction,
    float fl_decreasing_packet_loss_fraction,
    int fl_increase_overhead_offset,
    int fl_decrease_overhead_offset,
    std::map<FrameLengthChange, int> fl_changing_bandwidths_bps)
    : encoder_frame_lengths_ms(encoder_frame_lengths_ms),
      initial_frame_length_ms(initial_frame_length_ms),
      min_encoder_bitrate_bps(min_encoder_bitrate_bps),
      fl_increasing_packet_loss_fraction(fl_increasing_packet_loss_fraction),
      fl_decreasing_packet_loss_fraction(fl_decreasing_packet_loss_fraction),
      fl_increase_overhead_offset(fl_increase_overhead_offset),
      fl_decrease_overhead_offset(fl_decrease_overhead_offset),
      fl_changing_bandwidths_bps(std::move(fl_changing_bandwidths_bps)) {}

FrameLengthController::Config::Config(const Config& other) = default;

FrameLengthController::Config::~Config() = default;

FrameLengthController::FrameLengthController(const Config& config)
    : config_(config) {
  frame_length_ms_ = std::find(config_.encoder_frame_lengths_ms.begin(),
                               config_.encoder_frame_lengths_ms.end(),
                               config_.initial_frame_length_ms);
  // `encoder_frame_lengths_ms` must contain `initial_frame_length_ms`.
  RTC_DCHECK(frame_length_ms_ != config_.encoder_frame_lengths_ms.end());
}

FrameLengthController::~FrameLengthController() = default;

void FrameLengthController::UpdateNetworkMetrics(
    const NetworkMetrics& network_metrics) {
  if (network_metrics.uplink_bandwidth_bps)
    uplink_bandwidth_bps_ = network_metrics.uplink_bandwidth_bps;
  if (network_metrics.uplink_packet_loss_fraction)
    uplink_packet_loss_fraction_ = network_metrics.uplink_packet_loss_fraction;
  if (network_metrics.overhead_bytes_per_packet)
    overhead_bytes_per_packet_ = network_metrics.overhead_bytes_per_packet;
}

void FrameLengthController::MakeDecision(AudioEncoderRuntimeConfig* config) {
  // Decision on `frame_length_ms` should not have been made.
  RTC_DCHECK(!config->frame_length_ms);

  if (FrameLengthIncreasingDecision(*config)) {
    prev_decision_increase_ = true;
  } else if (FrameLengthDecreasingDecision(*config)) {
    prev_decision_increase_ = false;
  }
  config->last_fl_change_increase = prev_decision_increase_;
  config->frame_length_ms = *frame_length_ms_;
}

FrameLengthController::Config::FrameLengthChange::FrameLengthChange(
    int from_frame_length_ms,
    int to_frame_length_ms)
    : from_frame_length_ms(from_frame_length_ms),
      to_frame_length_ms(to_frame_length_ms) {}

bool FrameLengthController::Config::FrameLengthChange::operator<(
    const FrameLengthChange& rhs) const {
  return from_frame_length_ms < rhs.from_frame_length_ms ||
         (from_frame_length_ms == rhs.from_frame_length_ms &&
          to_frame_length_ms < rhs.to_frame_length_ms);
}

bool FrameLengthController::FrameLengthIncreasingDecision(
    const AudioEncoderRuntimeConfig& config) {
  // Increase frame length if
  // 1. `uplink_bandwidth_bps` is known to be smaller or equal than
  //    `min_encoder_bitrate_bps` plus `prevent_overuse_margin_bps` plus the
  //    current overhead rate OR all the following:
  // 2. longer frame length is available AND
  // 3. `uplink_bandwidth_bps` is known to be smaller than a threshold AND
  // 4. `uplink_packet_loss_fraction` is known to be smaller than a threshold.

  // Find next frame length to which a criterion is defined to shift from
  // current frame length.
  auto longer_frame_length_ms = std::next(frame_length_ms_);
  auto increase_threshold = config_.fl_changing_bandwidths_bps.end();
  while (longer_frame_length_ms != config_.encoder_frame_lengths_ms.end()) {
    increase_threshold = config_.fl_changing_bandwidths_bps.find(
        Config::FrameLengthChange(*frame_length_ms_, *longer_frame_length_ms));
    if (increase_threshold != config_.fl_changing_bandwidths_bps.end())
      break;
    longer_frame_length_ms = std::next(longer_frame_length_ms);
  }

  if (increase_threshold == config_.fl_changing_bandwidths_bps.end())
    return false;

  // Check that
  // -(*overhead_bytes_per_packet_) <= offset <= (*overhead_bytes_per_packet_)
  RTC_DCHECK(
      !overhead_bytes_per_packet_ ||
      (overhead_bytes_per_packet_ &&
       static_cast<size_t>(std::max(0, -config_.fl_increase_overhead_offset)) <=
           *overhead_bytes_per_packet_ &&
       static_cast<size_t>(std::max(0, config_.fl_increase_overhead_offset)) <=
           *overhead_bytes_per_packet_));

  if (uplink_bandwidth_bps_ && overhead_bytes_per_packet_ &&
      *uplink_bandwidth_bps_ <=
          config_.min_encoder_bitrate_bps + kPreventOveruseMarginBps +
              OverheadRateBps(*overhead_bytes_per_packet_ +
                                  config_.fl_increase_overhead_offset,
                              *frame_length_ms_)) {
    frame_length_ms_ = longer_frame_length_ms;
    return true;
  }

  if ((uplink_bandwidth_bps_ &&
       *uplink_bandwidth_bps_ <= increase_threshold->second) &&
      (uplink_packet_loss_fraction_ &&
       *uplink_packet_loss_fraction_ <=
           config_.fl_increasing_packet_loss_fraction)) {
    frame_length_ms_ = longer_frame_length_ms;
    return true;
  }
  return false;
}

bool FrameLengthController::FrameLengthDecreasingDecision(
    const AudioEncoderRuntimeConfig& config) {
  // Decrease frame length if
  // 1. shorter frame length is available AND
  // 2. `uplink_bandwidth_bps` is known to be bigger than
  // `min_encoder_bitrate_bps` plus `prevent_overuse_margin_bps` plus the
  // overhead which would be produced with the shorter frame length AND
  // one or more of the followings:
  // 3. `uplink_bandwidth_bps` is known to be larger than a threshold,
  // 4. `uplink_packet_loss_fraction` is known to be larger than a threshold,

  // Find next frame length to which a criterion is defined to shift from
  // current frame length.
  auto shorter_frame_length_ms = frame_length_ms_;
  auto decrease_threshold = config_.fl_changing_bandwidths_bps.end();
  while (shorter_frame_length_ms != config_.encoder_frame_lengths_ms.begin()) {
    shorter_frame_length_ms = std::prev(shorter_frame_length_ms);
    decrease_threshold = config_.fl_changing_bandwidths_bps.find(
        Config::FrameLengthChange(*frame_length_ms_, *shorter_frame_length_ms));
    if (decrease_threshold != config_.fl_changing_bandwidths_bps.end())
      break;
  }

  if (decrease_threshold == config_.fl_changing_bandwidths_bps.end())
    return false;

  if (uplink_bandwidth_bps_ && overhead_bytes_per_packet_ &&
      *uplink_bandwidth_bps_ <=
          config_.min_encoder_bitrate_bps + kPreventOveruseMarginBps +
              OverheadRateBps(*overhead_bytes_per_packet_ +
                                  config_.fl_decrease_overhead_offset,
                              *shorter_frame_length_ms)) {
    return false;
  }

  if ((uplink_bandwidth_bps_ &&
       *uplink_bandwidth_bps_ >= decrease_threshold->second) ||
      (uplink_packet_loss_fraction_ &&
       *uplink_packet_loss_fraction_ >=
           config_.fl_decreasing_packet_loss_fraction)) {
    frame_length_ms_ = shorter_frame_length_ms;
    return true;
  }
  return false;
}

}  // namespace webrtc
