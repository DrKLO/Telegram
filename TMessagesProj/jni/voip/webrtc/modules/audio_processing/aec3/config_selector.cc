
/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/config_selector.h"

#include "rtc_base/checks.h"

namespace webrtc {
namespace {

// Validates that the mono and the multichannel configs have compatible fields.
bool CompatibleConfigs(const EchoCanceller3Config& mono_config,
                       const EchoCanceller3Config& multichannel_config) {
  if (mono_config.delay.fixed_capture_delay_samples !=
      multichannel_config.delay.fixed_capture_delay_samples) {
    return false;
  }
  if (mono_config.filter.export_linear_aec_output !=
      multichannel_config.filter.export_linear_aec_output) {
    return false;
  }
  if (mono_config.filter.high_pass_filter_echo_reference !=
      multichannel_config.filter.high_pass_filter_echo_reference) {
    return false;
  }
  if (mono_config.multi_channel.detect_stereo_content !=
      multichannel_config.multi_channel.detect_stereo_content) {
    return false;
  }
  if (mono_config.multi_channel.stereo_detection_timeout_threshold_seconds !=
      multichannel_config.multi_channel
          .stereo_detection_timeout_threshold_seconds) {
    return false;
  }
  return true;
}

}  // namespace

ConfigSelector::ConfigSelector(
    const EchoCanceller3Config& config,
    const absl::optional<EchoCanceller3Config>& multichannel_config,
    int num_render_input_channels)
    : config_(config), multichannel_config_(multichannel_config) {
  if (multichannel_config_.has_value()) {
    RTC_DCHECK(CompatibleConfigs(config_, *multichannel_config_));
  }

  Update(!config_.multi_channel.detect_stereo_content &&
         num_render_input_channels > 1);

  RTC_DCHECK(active_config_);
}

void ConfigSelector::Update(bool multichannel_content) {
  if (multichannel_content && multichannel_config_.has_value()) {
    active_config_ = &(*multichannel_config_);
  } else {
    active_config_ = &config_;
  }
}

}  // namespace webrtc
