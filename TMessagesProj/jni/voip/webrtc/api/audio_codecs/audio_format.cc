/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_codecs/audio_format.h"

#include <utility>

#include "absl/strings/match.h"

namespace webrtc {

SdpAudioFormat::SdpAudioFormat(const SdpAudioFormat&) = default;
SdpAudioFormat::SdpAudioFormat(SdpAudioFormat&&) = default;

SdpAudioFormat::SdpAudioFormat(absl::string_view name,
                               int clockrate_hz,
                               size_t num_channels)
    : name(name), clockrate_hz(clockrate_hz), num_channels(num_channels) {}

SdpAudioFormat::SdpAudioFormat(absl::string_view name,
                               int clockrate_hz,
                               size_t num_channels,
                               const Parameters& param)
    : name(name),
      clockrate_hz(clockrate_hz),
      num_channels(num_channels),
      parameters(param) {}

SdpAudioFormat::SdpAudioFormat(absl::string_view name,
                               int clockrate_hz,
                               size_t num_channels,
                               Parameters&& param)
    : name(name),
      clockrate_hz(clockrate_hz),
      num_channels(num_channels),
      parameters(std::move(param)) {}

bool SdpAudioFormat::Matches(const SdpAudioFormat& o) const {
  return absl::EqualsIgnoreCase(name, o.name) &&
         clockrate_hz == o.clockrate_hz && num_channels == o.num_channels;
}

SdpAudioFormat::~SdpAudioFormat() = default;
SdpAudioFormat& SdpAudioFormat::operator=(const SdpAudioFormat&) = default;
SdpAudioFormat& SdpAudioFormat::operator=(SdpAudioFormat&&) = default;

bool operator==(const SdpAudioFormat& a, const SdpAudioFormat& b) {
  return absl::EqualsIgnoreCase(a.name, b.name) &&
         a.clockrate_hz == b.clockrate_hz && a.num_channels == b.num_channels &&
         a.parameters == b.parameters;
}

AudioCodecInfo::AudioCodecInfo(int sample_rate_hz,
                               size_t num_channels,
                               int bitrate_bps)
    : AudioCodecInfo(sample_rate_hz,
                     num_channels,
                     bitrate_bps,
                     bitrate_bps,
                     bitrate_bps) {}

AudioCodecInfo::AudioCodecInfo(int sample_rate_hz,
                               size_t num_channels,
                               int default_bitrate_bps,
                               int min_bitrate_bps,
                               int max_bitrate_bps)
    : sample_rate_hz(sample_rate_hz),
      num_channels(num_channels),
      default_bitrate_bps(default_bitrate_bps),
      min_bitrate_bps(min_bitrate_bps),
      max_bitrate_bps(max_bitrate_bps) {
  RTC_DCHECK_GT(sample_rate_hz, 0);
  RTC_DCHECK_GT(num_channels, 0);
  RTC_DCHECK_GE(min_bitrate_bps, 0);
  RTC_DCHECK_LE(min_bitrate_bps, default_bitrate_bps);
  RTC_DCHECK_GE(max_bitrate_bps, default_bitrate_bps);
}

}  // namespace webrtc
