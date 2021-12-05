/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/audio_network_adaptor/bitrate_controller.h"

#include <algorithm>

#include "rtc_base/checks.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace audio_network_adaptor {

BitrateController::Config::Config(int initial_bitrate_bps,
                                  int initial_frame_length_ms,
                                  int fl_increase_overhead_offset,
                                  int fl_decrease_overhead_offset)
    : initial_bitrate_bps(initial_bitrate_bps),
      initial_frame_length_ms(initial_frame_length_ms),
      fl_increase_overhead_offset(fl_increase_overhead_offset),
      fl_decrease_overhead_offset(fl_decrease_overhead_offset) {}

BitrateController::Config::~Config() = default;

BitrateController::BitrateController(const Config& config)
    : config_(config),
      bitrate_bps_(config_.initial_bitrate_bps),
      frame_length_ms_(config_.initial_frame_length_ms) {
  RTC_DCHECK_GT(bitrate_bps_, 0);
  RTC_DCHECK_GT(frame_length_ms_, 0);
}

BitrateController::~BitrateController() = default;

void BitrateController::UpdateNetworkMetrics(
    const NetworkMetrics& network_metrics) {
  if (network_metrics.target_audio_bitrate_bps)
    target_audio_bitrate_bps_ = network_metrics.target_audio_bitrate_bps;
  if (network_metrics.overhead_bytes_per_packet) {
    RTC_DCHECK_GT(*network_metrics.overhead_bytes_per_packet, 0);
    overhead_bytes_per_packet_ = network_metrics.overhead_bytes_per_packet;
  }
}

void BitrateController::MakeDecision(AudioEncoderRuntimeConfig* config) {
  // Decision on |bitrate_bps| should not have been made.
  RTC_DCHECK(!config->bitrate_bps);
  if (target_audio_bitrate_bps_ && overhead_bytes_per_packet_) {
    if (config->frame_length_ms)
      frame_length_ms_ = *config->frame_length_ms;
    int offset = config->last_fl_change_increase
                     ? config_.fl_increase_overhead_offset
                     : config_.fl_decrease_overhead_offset;
    // Check that
    // -(*overhead_bytes_per_packet_) <= offset <= (*overhead_bytes_per_packet_)
    RTC_DCHECK_GE(*overhead_bytes_per_packet_, -offset);
    RTC_DCHECK_LE(offset, *overhead_bytes_per_packet_);
    int overhead_rate_bps = static_cast<int>(
        (*overhead_bytes_per_packet_ + offset) * 8 * 1000 / frame_length_ms_);
    bitrate_bps_ = std::max(0, *target_audio_bitrate_bps_ - overhead_rate_bps);
  }
  config->bitrate_bps = bitrate_bps_;
}

}  // namespace audio_network_adaptor
}  // namespace webrtc
