/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_options.h"

#include "api/array_view.h"
#include "rtc_base/strings/string_builder.h"

namespace cricket {
namespace {

template <class T>
void ToStringIfSet(rtc::SimpleStringBuilder* result,
                   const char* key,
                   const absl::optional<T>& val) {
  if (val) {
    (*result) << key << ": " << *val << ", ";
  }
}

template <typename T>
void SetFrom(absl::optional<T>* s, const absl::optional<T>& o) {
  if (o) {
    *s = o;
  }
}

}  // namespace

AudioOptions::AudioOptions() = default;
AudioOptions::~AudioOptions() = default;

void AudioOptions::SetAll(const AudioOptions& change) {
  SetFrom(&echo_cancellation, change.echo_cancellation);
#if defined(WEBRTC_IOS)
  SetFrom(&ios_force_software_aec_HACK, change.ios_force_software_aec_HACK);
#endif
  SetFrom(&auto_gain_control, change.auto_gain_control);
  SetFrom(&noise_suppression, change.noise_suppression);
  SetFrom(&highpass_filter, change.highpass_filter);
  SetFrom(&stereo_swapping, change.stereo_swapping);
  SetFrom(&audio_jitter_buffer_max_packets,
          change.audio_jitter_buffer_max_packets);
  SetFrom(&audio_jitter_buffer_fast_accelerate,
          change.audio_jitter_buffer_fast_accelerate);
  SetFrom(&audio_jitter_buffer_min_delay_ms,
          change.audio_jitter_buffer_min_delay_ms);
  SetFrom(&combined_audio_video_bwe, change.combined_audio_video_bwe);
  SetFrom(&audio_network_adaptor, change.audio_network_adaptor);
  SetFrom(&audio_network_adaptor_config, change.audio_network_adaptor_config);
  SetFrom(&init_recording_on_send, change.init_recording_on_send);
}

bool AudioOptions::operator==(const AudioOptions& o) const {
  return echo_cancellation == o.echo_cancellation &&
#if defined(WEBRTC_IOS)
         ios_force_software_aec_HACK == o.ios_force_software_aec_HACK &&
#endif
         auto_gain_control == o.auto_gain_control &&
         noise_suppression == o.noise_suppression &&
         highpass_filter == o.highpass_filter &&
         stereo_swapping == o.stereo_swapping &&
         audio_jitter_buffer_max_packets == o.audio_jitter_buffer_max_packets &&
         audio_jitter_buffer_fast_accelerate ==
             o.audio_jitter_buffer_fast_accelerate &&
         audio_jitter_buffer_min_delay_ms ==
             o.audio_jitter_buffer_min_delay_ms &&
         combined_audio_video_bwe == o.combined_audio_video_bwe &&
         audio_network_adaptor == o.audio_network_adaptor &&
         audio_network_adaptor_config == o.audio_network_adaptor_config &&
         init_recording_on_send == o.init_recording_on_send;
}

std::string AudioOptions::ToString() const {
  char buffer[1024];
  rtc::SimpleStringBuilder result(buffer);
  result << "AudioOptions {";
  ToStringIfSet(&result, "aec", echo_cancellation);
#if defined(WEBRTC_IOS)
  ToStringIfSet(&result, "ios_force_software_aec_HACK",
                ios_force_software_aec_HACK);
#endif
  ToStringIfSet(&result, "agc", auto_gain_control);
  ToStringIfSet(&result, "ns", noise_suppression);
  ToStringIfSet(&result, "hf", highpass_filter);
  ToStringIfSet(&result, "swap", stereo_swapping);
  ToStringIfSet(&result, "audio_jitter_buffer_max_packets",
                audio_jitter_buffer_max_packets);
  ToStringIfSet(&result, "audio_jitter_buffer_fast_accelerate",
                audio_jitter_buffer_fast_accelerate);
  ToStringIfSet(&result, "audio_jitter_buffer_min_delay_ms",
                audio_jitter_buffer_min_delay_ms);
  ToStringIfSet(&result, "combined_audio_video_bwe", combined_audio_video_bwe);
  ToStringIfSet(&result, "audio_network_adaptor", audio_network_adaptor);
  ToStringIfSet(&result, "init_recording_on_send", init_recording_on_send);
  result << "}";
  return result.str();
}

}  // namespace cricket
