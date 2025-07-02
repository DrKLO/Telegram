/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/base/sdp_video_format_utils.h"

#include <cstring>
#include <map>
#include <utility>

#include "api/video_codecs/h264_profile_level_id.h"
#ifdef RTC_ENABLE_H265
#include "api/video_codecs/h265_profile_tier_level.h"
#endif
#include "rtc_base/checks.h"
#include "rtc_base/string_to_number.h"

namespace webrtc {
namespace {
const char kProfileLevelId[] = "profile-level-id";
const char kH264LevelAsymmetryAllowed[] = "level-asymmetry-allowed";
// Max frame rate for VP8 and VP9 video.
const char kVPxFmtpMaxFrameRate[] = "max-fr";
// Max frame size for VP8 and VP9 video.
const char kVPxFmtpMaxFrameSize[] = "max-fs";
const int kVPxFmtpFrameSizeSubBlockPixels = 256;
#ifdef RTC_ENABLE_H265
constexpr char kH265ProfileId[] = "profile-id";
constexpr char kH265TierFlag[] = "tier-flag";
constexpr char kH265LevelId[] = "level-id";
#endif

bool IsH264LevelAsymmetryAllowed(const CodecParameterMap& params) {
  const auto it = params.find(kH264LevelAsymmetryAllowed);
  return it != params.end() && strcmp(it->second.c_str(), "1") == 0;
}

// Compare H264 levels and handle the level 1b case.
bool H264LevelIsLess(H264Level a, H264Level b) {
  if (a == H264Level::kLevel1_b)
    return b != H264Level::kLevel1 && b != H264Level::kLevel1_b;
  if (b == H264Level::kLevel1_b)
    return a == H264Level::kLevel1;
  return a < b;
}

H264Level H264LevelMin(H264Level a, H264Level b) {
  return H264LevelIsLess(a, b) ? a : b;
}

absl::optional<int> ParsePositiveNumberFromParams(
    const CodecParameterMap& params,
    const char* parameter_name) {
  const auto max_frame_rate_it = params.find(parameter_name);
  if (max_frame_rate_it == params.end())
    return absl::nullopt;

  const absl::optional<int> i =
      rtc::StringToNumber<int>(max_frame_rate_it->second);
  if (!i.has_value() || i.value() <= 0)
    return absl::nullopt;
  return i;
}

#ifdef RTC_ENABLE_H265
// Compares two H265Level and return the smaller.
H265Level H265LevelMin(H265Level a, H265Level b) {
  return a <= b ? a : b;
}

// Returns true if none of profile-id/tier-flag/level-id is specified
// explicitly in the param.
bool IsDefaultH265PTL(const CodecParameterMap& params) {
  return !params.count(kH265ProfileId) && !params.count(kH265TierFlag) &&
         !params.count(kH265LevelId);
}
#endif

}  // namespace

#ifdef RTC_ENABLE_H265
// Set level according to https://tools.ietf.org/html/rfc7798#section-7.1
void H265GenerateProfileTierLevelForAnswer(
    const CodecParameterMap& local_supported_params,
    const CodecParameterMap& remote_offered_params,
    CodecParameterMap* answer_params) {
  // If local and remote haven't set profile-id/tier-flag/level-id, they
  // are both using the default PTL In this case, don't set PTL in answer
  // either.
  if (IsDefaultH265PTL(local_supported_params) &&
      IsDefaultH265PTL(remote_offered_params)) {
    return;
  }

  // Parse profile-tier-level.
  const absl::optional<H265ProfileTierLevel> local_profile_tier_level =
      ParseSdpForH265ProfileTierLevel(local_supported_params);
  const absl::optional<H265ProfileTierLevel> remote_profile_tier_level =
      ParseSdpForH265ProfileTierLevel(remote_offered_params);
  // Profile and tier for local and remote codec must be valid and equal.
  RTC_DCHECK(local_profile_tier_level);
  RTC_DCHECK(remote_profile_tier_level);
  RTC_DCHECK_EQ(local_profile_tier_level->profile,
                remote_profile_tier_level->profile);
  RTC_DCHECK_EQ(local_profile_tier_level->tier,
                remote_profile_tier_level->tier);

  const H265Level answer_level = H265LevelMin(local_profile_tier_level->level,
                                              remote_profile_tier_level->level);

  // Level-id in answer is changable as long as the highest level indicated by
  // the answer is not higher than that indicated by the offer. See
  // https://tools.ietf.org/html/rfc7798#section-7.2.2, sub-clause 2.
  (*answer_params)[kH265LevelId] = H265LevelToString(answer_level);
}
#endif

// Set level according to https://tools.ietf.org/html/rfc6184#section-8.2.2.
void H264GenerateProfileLevelIdForAnswer(
    const CodecParameterMap& local_supported_params,
    const CodecParameterMap& remote_offered_params,
    CodecParameterMap* answer_params) {
  // If both local and remote haven't set profile-level-id, they are both using
  // the default profile. In this case, don't set profile-level-id in answer
  // either.
  if (!local_supported_params.count(kProfileLevelId) &&
      !remote_offered_params.count(kProfileLevelId)) {
    return;
  }

  // Parse profile-level-ids.
  const absl::optional<H264ProfileLevelId> local_profile_level_id =
      ParseSdpForH264ProfileLevelId(local_supported_params);
  const absl::optional<H264ProfileLevelId> remote_profile_level_id =
      ParseSdpForH264ProfileLevelId(remote_offered_params);
  // The local and remote codec must have valid and equal H264 Profiles.
  RTC_DCHECK(local_profile_level_id);
  RTC_DCHECK(remote_profile_level_id);
  RTC_DCHECK_EQ(local_profile_level_id->profile,
                remote_profile_level_id->profile);

  // Parse level information.
  const bool level_asymmetry_allowed =
      IsH264LevelAsymmetryAllowed(local_supported_params) &&
      IsH264LevelAsymmetryAllowed(remote_offered_params);
  const H264Level local_level = local_profile_level_id->level;
  const H264Level remote_level = remote_profile_level_id->level;
  const H264Level min_level = H264LevelMin(local_level, remote_level);

  // Determine answer level. When level asymmetry is not allowed, level upgrade
  // is not allowed, i.e., the level in the answer must be equal to or lower
  // than the level in the offer.
  const H264Level answer_level =
      level_asymmetry_allowed ? local_level : min_level;

  // Set the resulting profile-level-id in the answer parameters.
  (*answer_params)[kProfileLevelId] = *H264ProfileLevelIdToString(
      H264ProfileLevelId(local_profile_level_id->profile, answer_level));
}

absl::optional<int> ParseSdpForVPxMaxFrameRate(
    const CodecParameterMap& params) {
  return ParsePositiveNumberFromParams(params, kVPxFmtpMaxFrameRate);
}

absl::optional<int> ParseSdpForVPxMaxFrameSize(
    const CodecParameterMap& params) {
  const absl::optional<int> i =
      ParsePositiveNumberFromParams(params, kVPxFmtpMaxFrameSize);
  return i ? absl::make_optional(i.value() * kVPxFmtpFrameSizeSubBlockPixels)
           : absl::nullopt;
}

}  // namespace webrtc
