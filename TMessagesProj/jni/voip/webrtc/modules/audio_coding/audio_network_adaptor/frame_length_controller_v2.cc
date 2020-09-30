/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/audio_network_adaptor/frame_length_controller_v2.h"

#include <algorithm>

#include "absl/algorithm/container.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {

int OverheadBps(int overhead_bytes_per_packet, int frame_length_ms) {
  return overhead_bytes_per_packet * 8 * 1000 / frame_length_ms;
}

}  // namespace

FrameLengthControllerV2::FrameLengthControllerV2(
    rtc::ArrayView<const int> encoder_frame_lengths_ms,
    int min_payload_bitrate_bps,
    bool use_slow_adaptation)
    : encoder_frame_lengths_ms_(encoder_frame_lengths_ms.begin(),
                                encoder_frame_lengths_ms.end()),
      min_payload_bitrate_bps_(min_payload_bitrate_bps),
      use_slow_adaptation_(use_slow_adaptation) {
  RTC_CHECK(!encoder_frame_lengths_ms_.empty());
  absl::c_sort(encoder_frame_lengths_ms_);
}

void FrameLengthControllerV2::UpdateNetworkMetrics(
    const NetworkMetrics& network_metrics) {
  if (network_metrics.target_audio_bitrate_bps) {
    target_bitrate_bps_ = network_metrics.target_audio_bitrate_bps;
  }
  if (network_metrics.overhead_bytes_per_packet) {
    overhead_bytes_per_packet_ = network_metrics.overhead_bytes_per_packet;
  }
  if (network_metrics.uplink_bandwidth_bps) {
    uplink_bandwidth_bps_ = network_metrics.uplink_bandwidth_bps;
  }
}

void FrameLengthControllerV2::MakeDecision(AudioEncoderRuntimeConfig* config) {
  if (!target_bitrate_bps_ || !overhead_bytes_per_packet_ ||
      !uplink_bandwidth_bps_) {
    return;
  }

  auto it =
      absl::c_find_if(encoder_frame_lengths_ms_, [&](int frame_length_ms) {
        int target = use_slow_adaptation_ ? *uplink_bandwidth_bps_
                                          : *target_bitrate_bps_;
        return target -
                   OverheadBps(*overhead_bytes_per_packet_, frame_length_ms) >
               min_payload_bitrate_bps_;
      });

  // Longest frame length is chosen if none match our criteria.
  config->frame_length_ms = it != encoder_frame_lengths_ms_.end()
                                ? *it
                                : encoder_frame_lengths_ms_.back();
}

}  // namespace webrtc
