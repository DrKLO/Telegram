/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/audio_network_adaptor/event_log_writer.h"

#include <math.h>

#include <algorithm>
#include <cstdlib>
#include <memory>
#include <utility>

#include "absl/types/optional.h"
#include "api/rtc_event_log/rtc_event.h"
#include "api/rtc_event_log/rtc_event_log.h"
#include "logging/rtc_event_log/events/rtc_event_audio_network_adaptation.h"
#include "rtc_base/checks.h"

namespace webrtc {

EventLogWriter::EventLogWriter(RtcEventLog* event_log,
                               int min_bitrate_change_bps,
                               float min_bitrate_change_fraction,
                               float min_packet_loss_change_fraction)
    : event_log_(event_log),
      min_bitrate_change_bps_(min_bitrate_change_bps),
      min_bitrate_change_fraction_(min_bitrate_change_fraction),
      min_packet_loss_change_fraction_(min_packet_loss_change_fraction) {
  RTC_DCHECK(event_log_);
}

EventLogWriter::~EventLogWriter() = default;

void EventLogWriter::MaybeLogEncoderConfig(
    const AudioEncoderRuntimeConfig& config) {
  if (last_logged_config_.num_channels != config.num_channels)
    return LogEncoderConfig(config);
  if (last_logged_config_.enable_dtx != config.enable_dtx)
    return LogEncoderConfig(config);
  if (last_logged_config_.enable_fec != config.enable_fec)
    return LogEncoderConfig(config);
  if (last_logged_config_.frame_length_ms != config.frame_length_ms)
    return LogEncoderConfig(config);
  if ((!last_logged_config_.bitrate_bps && config.bitrate_bps) ||
      (last_logged_config_.bitrate_bps && config.bitrate_bps &&
       std::abs(*last_logged_config_.bitrate_bps - *config.bitrate_bps) >=
           std::min(static_cast<int>(*last_logged_config_.bitrate_bps *
                                     min_bitrate_change_fraction_),
                    min_bitrate_change_bps_))) {
    return LogEncoderConfig(config);
  }
  if ((!last_logged_config_.uplink_packet_loss_fraction &&
       config.uplink_packet_loss_fraction) ||
      (last_logged_config_.uplink_packet_loss_fraction &&
       config.uplink_packet_loss_fraction &&
       fabs(*last_logged_config_.uplink_packet_loss_fraction -
            *config.uplink_packet_loss_fraction) >=
           min_packet_loss_change_fraction_ *
               *last_logged_config_.uplink_packet_loss_fraction)) {
    return LogEncoderConfig(config);
  }
}

void EventLogWriter::LogEncoderConfig(const AudioEncoderRuntimeConfig& config) {
  auto config_copy = std::make_unique<AudioEncoderRuntimeConfig>(config);
  event_log_->Log(
      std::make_unique<RtcEventAudioNetworkAdaptation>(std::move(config_copy)));
  last_logged_config_ = config;
}

}  // namespace webrtc
