/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/adaptation/encoder_settings.h"

#include <utility>

namespace webrtc {

EncoderSettings::EncoderSettings(VideoEncoder::EncoderInfo encoder_info,
                                 VideoEncoderConfig encoder_config,
                                 VideoCodec video_codec)
    : encoder_info_(std::move(encoder_info)),
      encoder_config_(std::move(encoder_config)),
      video_codec_(std::move(video_codec)) {}

EncoderSettings::EncoderSettings(const EncoderSettings& other)
    : encoder_info_(other.encoder_info_),
      encoder_config_(other.encoder_config_.Copy()),
      video_codec_(other.video_codec_) {}

EncoderSettings& EncoderSettings::operator=(const EncoderSettings& other) {
  encoder_info_ = other.encoder_info_;
  encoder_config_ = other.encoder_config_.Copy();
  video_codec_ = other.video_codec_;
  return *this;
}

const VideoEncoder::EncoderInfo& EncoderSettings::encoder_info() const {
  return encoder_info_;
}

const VideoEncoderConfig& EncoderSettings::encoder_config() const {
  return encoder_config_;
}

const VideoCodec& EncoderSettings::video_codec() const {
  return video_codec_;
}

VideoCodecType GetVideoCodecTypeOrGeneric(
    const absl::optional<EncoderSettings>& settings) {
  return settings.has_value() ? settings->encoder_config().codec_type
                              : kVideoCodecGeneric;
}

}  // namespace webrtc
