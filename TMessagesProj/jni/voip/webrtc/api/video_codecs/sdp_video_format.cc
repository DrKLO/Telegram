/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video_codecs/sdp_video_format.h"

#include "absl/strings/match.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/video_codecs/av1_profile.h"
#include "api/video_codecs/h264_profile_level_id.h"
#ifdef RTC_ENABLE_H265
#include "api/video_codecs/h265_profile_tier_level.h"
#endif
#include "api/video_codecs/video_codec.h"
#include "api/video_codecs/vp9_profile.h"
#include "media/base/media_constants.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"

namespace webrtc {

namespace {

std::string GetFmtpParameterOrDefault(const CodecParameterMap& params,
                                      const std::string& name,
                                      const std::string& default_value) {
  const auto it = params.find(name);
  if (it != params.end()) {
    return it->second;
  }
  return default_value;
}

std::string H264GetPacketizationModeOrDefault(const CodecParameterMap& params) {
  // If packetization-mode is not present, default to "0".
  // https://tools.ietf.org/html/rfc6184#section-6.2
  return GetFmtpParameterOrDefault(params, cricket::kH264FmtpPacketizationMode,
                                   "0");
}

bool H264IsSamePacketizationMode(const CodecParameterMap& left,
                                 const CodecParameterMap& right) {
  return H264GetPacketizationModeOrDefault(left) ==
         H264GetPacketizationModeOrDefault(right);
}

std::string AV1GetTierOrDefault(const CodecParameterMap& params) {
  // If the parameter is not present, the tier MUST be inferred to be 0.
  // https://aomediacodec.github.io/av1-rtp-spec/#72-sdp-parameters
  return GetFmtpParameterOrDefault(params, cricket::kAv1FmtpTier, "0");
}

bool AV1IsSameTier(const CodecParameterMap& left,
                   const CodecParameterMap& right) {
  return AV1GetTierOrDefault(left) == AV1GetTierOrDefault(right);
}

std::string AV1GetLevelIdxOrDefault(const CodecParameterMap& params) {
  // If the parameter is not present, it MUST be inferred to be 5 (level 3.1).
  // https://aomediacodec.github.io/av1-rtp-spec/#72-sdp-parameters
  return GetFmtpParameterOrDefault(params, cricket::kAv1FmtpLevelIdx, "5");
}

bool AV1IsSameLevelIdx(const CodecParameterMap& left,
                       const CodecParameterMap& right) {
  return AV1GetLevelIdxOrDefault(left) == AV1GetLevelIdxOrDefault(right);
}

// Some (video) codecs are actually families of codecs and rely on parameters
// to distinguish different incompatible family members.
bool IsSameCodecSpecific(const SdpVideoFormat& format1,
                         const SdpVideoFormat& format2) {
  // The assumption when calling this function is that the two formats have the
  // same name.
  RTC_DCHECK(absl::EqualsIgnoreCase(format1.name, format2.name));

  VideoCodecType codec_type = PayloadStringToCodecType(format1.name);
  switch (codec_type) {
    case kVideoCodecH264:
      return H264IsSameProfile(format1.parameters, format2.parameters) &&
             H264IsSamePacketizationMode(format1.parameters,
                                         format2.parameters);
    case kVideoCodecVP9:
      return VP9IsSameProfile(format1.parameters, format2.parameters);
    case kVideoCodecAV1:
      return AV1IsSameProfile(format1.parameters, format2.parameters) &&
             AV1IsSameTier(format1.parameters, format2.parameters) &&
             AV1IsSameLevelIdx(format1.parameters, format2.parameters);
#ifdef RTC_ENABLE_H265
    case kVideoCodecH265:
      return H265IsSameProfileTierLevel(format1.parameters, format2.parameters);
#endif
    default:
      return true;
  }
}

}  // namespace

SdpVideoFormat::SdpVideoFormat(const std::string& name) : name(name) {}

SdpVideoFormat::SdpVideoFormat(const std::string& name,
                               const CodecParameterMap& parameters)
    : name(name), parameters(parameters) {}

SdpVideoFormat::SdpVideoFormat(
    const std::string& name,
    const CodecParameterMap& parameters,
    const absl::InlinedVector<ScalabilityMode, kScalabilityModeCount>&
        scalability_modes)
    : name(name),
      parameters(parameters),
      scalability_modes(scalability_modes) {}

SdpVideoFormat::SdpVideoFormat(const SdpVideoFormat&) = default;
SdpVideoFormat::SdpVideoFormat(SdpVideoFormat&&) = default;
SdpVideoFormat& SdpVideoFormat::operator=(const SdpVideoFormat&) = default;
SdpVideoFormat& SdpVideoFormat::operator=(SdpVideoFormat&&) = default;

SdpVideoFormat::~SdpVideoFormat() = default;

std::string SdpVideoFormat::ToString() const {
  rtc::StringBuilder builder;
  builder << "Codec name: " << name << ", parameters: {";
  for (const auto& kv : parameters) {
    builder << " " << kv.first << "=" << kv.second;
  }

  builder << " }";
  if (!scalability_modes.empty()) {
    builder << ", scalability_modes: [";
    bool first = true;
    for (const auto scalability_mode : scalability_modes) {
      if (first) {
        first = false;
      } else {
        builder << ", ";
      }
      builder << ScalabilityModeToString(scalability_mode);
    }
    builder << "]";
  }

  return builder.str();
}

bool SdpVideoFormat::IsSameCodec(const SdpVideoFormat& other) const {
  // Two codecs are considered the same if the name matches (case insensitive)
  // and certain codec-specific parameters match.
  return absl::EqualsIgnoreCase(name, other.name) &&
         IsSameCodecSpecific(*this, other);
}

bool SdpVideoFormat::IsCodecInList(
    rtc::ArrayView<const webrtc::SdpVideoFormat> formats) const {
  for (const auto& format : formats) {
    if (IsSameCodec(format)) {
      return true;
    }
  }
  return false;
}

bool operator==(const SdpVideoFormat& a, const SdpVideoFormat& b) {
  return a.name == b.name && a.parameters == b.parameters &&
         a.scalability_modes == b.scalability_modes;
}

absl::optional<SdpVideoFormat> FuzzyMatchSdpVideoFormat(
    rtc::ArrayView<const SdpVideoFormat> supported_formats,
    const SdpVideoFormat& format) {
  absl::optional<SdpVideoFormat> res;
  int best_parameter_match = 0;
  for (const auto& supported_format : supported_formats) {
    if (absl::EqualsIgnoreCase(supported_format.name, format.name)) {
      int matching_parameters = 0;
      for (const auto& kv : supported_format.parameters) {
        auto it = format.parameters.find(kv.first);
        if (it != format.parameters.end() && it->second == kv.second) {
          matching_parameters += 1;
        }
      }

      if (!res || matching_parameters > best_parameter_match) {
        res = supported_format;
        best_parameter_match = matching_parameters;
      }
    }
  }

  if (!res) {
    RTC_LOG(LS_INFO) << "Failed to match SdpVideoFormat " << format.ToString();
  } else if (*res != format) {
    RTC_LOG(LS_INFO) << "Matched SdpVideoFormat " << format.ToString()
                     << " with " << res->ToString();
  }

  return res;
}

}  // namespace webrtc
