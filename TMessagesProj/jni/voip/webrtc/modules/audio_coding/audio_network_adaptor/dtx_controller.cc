/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/audio_network_adaptor/dtx_controller.h"

#include "rtc_base/checks.h"

namespace webrtc {

DtxController::Config::Config(bool initial_dtx_enabled,
                              int dtx_enabling_bandwidth_bps,
                              int dtx_disabling_bandwidth_bps)
    : initial_dtx_enabled(initial_dtx_enabled),
      dtx_enabling_bandwidth_bps(dtx_enabling_bandwidth_bps),
      dtx_disabling_bandwidth_bps(dtx_disabling_bandwidth_bps) {}

DtxController::DtxController(const Config& config)
    : config_(config), dtx_enabled_(config_.initial_dtx_enabled) {}

DtxController::~DtxController() = default;

void DtxController::UpdateNetworkMetrics(
    const NetworkMetrics& network_metrics) {
  if (network_metrics.uplink_bandwidth_bps)
    uplink_bandwidth_bps_ = network_metrics.uplink_bandwidth_bps;
}

void DtxController::MakeDecision(AudioEncoderRuntimeConfig* config) {
  // Decision on |enable_dtx| should not have been made.
  RTC_DCHECK(!config->enable_dtx);

  if (uplink_bandwidth_bps_) {
    if (dtx_enabled_ &&
        *uplink_bandwidth_bps_ >= config_.dtx_disabling_bandwidth_bps) {
      dtx_enabled_ = false;
    } else if (!dtx_enabled_ &&
               *uplink_bandwidth_bps_ <= config_.dtx_enabling_bandwidth_bps) {
      dtx_enabled_ = true;
    }
  }
  config->enable_dtx = dtx_enabled_;
}

}  // namespace webrtc
