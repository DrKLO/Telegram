/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video_codecs/video_codec.h"

#include <string.h>

#include <string>

#include "absl/strings/match.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {
constexpr char kPayloadNameVp8[] = "VP8";
constexpr char kPayloadNameVp9[] = "VP9";
constexpr char kPayloadNameAv1[] = "AV1";
// TODO(bugs.webrtc.org/13166): Remove AV1X when backwards compatibility is not
// needed.
constexpr char kPayloadNameAv1x[] = "AV1X";
constexpr char kPayloadNameH264[] = "H264";
#ifndef DISABLE_H265
constexpr char kPayloadNameH265[] = "H265";
#endif
constexpr char kPayloadNameGeneric[] = "Generic";
constexpr char kPayloadNameMultiplex[] = "Multiplex";
}  // namespace

bool VideoCodecVP8::operator==(const VideoCodecVP8& other) const {
  return (numberOfTemporalLayers == other.numberOfTemporalLayers &&
          denoisingOn == other.denoisingOn &&
          automaticResizeOn == other.automaticResizeOn &&
          keyFrameInterval == other.keyFrameInterval);
}

bool VideoCodecVP9::operator==(const VideoCodecVP9& other) const {
  return (numberOfTemporalLayers == other.numberOfTemporalLayers &&
          denoisingOn == other.denoisingOn &&
          keyFrameInterval == other.keyFrameInterval &&
          adaptiveQpMode == other.adaptiveQpMode &&
          automaticResizeOn == other.automaticResizeOn &&
          numberOfSpatialLayers == other.numberOfSpatialLayers &&
          flexibleMode == other.flexibleMode);
}

bool VideoCodecH264::operator==(const VideoCodecH264& other) const {
  return (keyFrameInterval == other.keyFrameInterval &&
          numberOfTemporalLayers == other.numberOfTemporalLayers);
}

#ifndef DISABLE_H265
bool VideoCodecH265::operator==(const VideoCodecH265& other) const {
  return (frameDroppingOn == other.frameDroppingOn &&
          keyFrameInterval == other.keyFrameInterval &&
          vpsLen == other.vpsLen && spsLen == other.spsLen &&
          ppsLen == other.ppsLen &&
          (spsLen == 0 || memcmp(spsData, other.spsData, spsLen) == 0) &&
          (ppsLen == 0 || memcmp(ppsData, other.ppsData, ppsLen) == 0));
}
#endif

VideoCodec::VideoCodec()
    : codecType(kVideoCodecGeneric),
      width(0),
      height(0),
      startBitrate(0),
      maxBitrate(0),
      minBitrate(0),
      maxFramerate(0),
      active(true),
      qpMax(0),
      numberOfSimulcastStreams(0),
      simulcastStream(),
      spatialLayers(),
      mode(VideoCodecMode::kRealtimeVideo),
      expect_encode_from_texture(false),
      timing_frame_thresholds({0, 0}),
      legacy_conference_mode(false),
      codec_specific_(),
      complexity_(VideoCodecComplexity::kComplexityNormal) {}

VideoCodecVP8* VideoCodec::VP8() {
  RTC_DCHECK_EQ(codecType, kVideoCodecVP8);
  return &codec_specific_.VP8;
}

const VideoCodecVP8& VideoCodec::VP8() const {
  RTC_DCHECK_EQ(codecType, kVideoCodecVP8);
  return codec_specific_.VP8;
}

VideoCodecVP9* VideoCodec::VP9() {
  RTC_DCHECK_EQ(codecType, kVideoCodecVP9);
  return &codec_specific_.VP9;
}

const VideoCodecVP9& VideoCodec::VP9() const {
  RTC_DCHECK_EQ(codecType, kVideoCodecVP9);
  return codec_specific_.VP9;
}

VideoCodecH264* VideoCodec::H264() {
  RTC_DCHECK_EQ(codecType, kVideoCodecH264);
  return &codec_specific_.H264;
}

const VideoCodecH264& VideoCodec::H264() const {
  RTC_DCHECK_EQ(codecType, kVideoCodecH264);
  return codec_specific_.H264;
}

VideoCodecH265* VideoCodec::H265() {
  RTC_DCHECK_EQ(codecType, kVideoCodecH265);
  return &codec_specific_.H265;
}

const VideoCodecH265& VideoCodec::H265() const {
  RTC_DCHECK_EQ(codecType, kVideoCodecH265);
  return codec_specific_.H265;
}

const char* CodecTypeToPayloadString(VideoCodecType type) {
  switch (type) {
    case kVideoCodecVP8:
      return kPayloadNameVp8;
    case kVideoCodecVP9:
      return kPayloadNameVp9;
    case kVideoCodecAV1:
      return kPayloadNameAv1;
    case kVideoCodecH264:
      return kPayloadNameH264;
    case kVideoCodecH265:
      return kPayloadNameH265;
    case kVideoCodecMultiplex:
      return kPayloadNameMultiplex;
    case kVideoCodecGeneric:
      return kPayloadNameGeneric;
  }
  RTC_CHECK_NOTREACHED();
}

VideoCodecType PayloadStringToCodecType(const std::string& name) {
  if (absl::EqualsIgnoreCase(name, kPayloadNameVp8))
    return kVideoCodecVP8;
  if (absl::EqualsIgnoreCase(name, kPayloadNameVp9))
    return kVideoCodecVP9;
  if (absl::EqualsIgnoreCase(name, kPayloadNameAv1) ||
      absl::EqualsIgnoreCase(name, kPayloadNameAv1x))
    return kVideoCodecAV1;
  if (absl::EqualsIgnoreCase(name, kPayloadNameH264))
    return kVideoCodecH264;
  if (absl::EqualsIgnoreCase(name, kPayloadNameH265))
    return kVideoCodecH265;
  if (absl::EqualsIgnoreCase(name, kPayloadNameMultiplex))
    return kVideoCodecMultiplex;
  return kVideoCodecGeneric;
}

VideoCodecComplexity VideoCodec::GetVideoEncoderComplexity() const {
  return complexity_;
}

void VideoCodec::SetVideoEncoderComplexity(
    VideoCodecComplexity complexity_setting) {
  complexity_ = complexity_setting;
}

bool VideoCodec::GetFrameDropEnabled() const {
  return frame_drop_enabled_;
}

void VideoCodec::SetFrameDropEnabled(bool enabled) {
  frame_drop_enabled_ = enabled;
}

}  // namespace webrtc
