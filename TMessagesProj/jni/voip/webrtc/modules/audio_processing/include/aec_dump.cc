/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/include/aec_dump.h"

namespace webrtc {
InternalAPMConfig::InternalAPMConfig() = default;
InternalAPMConfig::InternalAPMConfig(const InternalAPMConfig&) = default;
InternalAPMConfig::InternalAPMConfig(InternalAPMConfig&&) = default;
InternalAPMConfig& InternalAPMConfig::operator=(const InternalAPMConfig&) =
    default;

bool InternalAPMConfig::operator==(const InternalAPMConfig& other) const {
  return aec_enabled == other.aec_enabled &&
         aec_delay_agnostic_enabled == other.aec_delay_agnostic_enabled &&
         aec_drift_compensation_enabled ==
             other.aec_drift_compensation_enabled &&
         aec_extended_filter_enabled == other.aec_extended_filter_enabled &&
         aec_suppression_level == other.aec_suppression_level &&
         aecm_enabled == other.aecm_enabled &&
         aecm_comfort_noise_enabled == other.aecm_comfort_noise_enabled &&
         aecm_routing_mode == other.aecm_routing_mode &&
         agc_enabled == other.agc_enabled && agc_mode == other.agc_mode &&
         agc_limiter_enabled == other.agc_limiter_enabled &&
         hpf_enabled == other.hpf_enabled && ns_enabled == other.ns_enabled &&
         ns_level == other.ns_level &&
         transient_suppression_enabled == other.transient_suppression_enabled &&
         noise_robust_agc_enabled == other.noise_robust_agc_enabled &&
         pre_amplifier_enabled == other.pre_amplifier_enabled &&
         pre_amplifier_fixed_gain_factor ==
             other.pre_amplifier_fixed_gain_factor &&
         experiments_description == other.experiments_description;
}
}  // namespace webrtc
