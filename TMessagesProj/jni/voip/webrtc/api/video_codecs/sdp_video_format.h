/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_VIDEO_CODECS_SDP_VIDEO_FORMAT_H_
#define API_VIDEO_CODECS_SDP_VIDEO_FORMAT_H_

#include <map>
#include <string>

#include "absl/container/inlined_vector.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/video_codecs/scalability_mode.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

// SDP specification for a single video codec.
// NOTE: This class is still under development and may change without notice.
struct RTC_EXPORT SdpVideoFormat {
  using Parameters = std::map<std::string, std::string>;

  explicit SdpVideoFormat(const std::string& name);
  SdpVideoFormat(const std::string& name, const Parameters& parameters);
  SdpVideoFormat(
      const std::string& name,
      const Parameters& parameters,
      const absl::InlinedVector<ScalabilityMode, kScalabilityModeCount>&
          scalability_modes);
  SdpVideoFormat(const SdpVideoFormat&);
  SdpVideoFormat(SdpVideoFormat&&);
  SdpVideoFormat& operator=(const SdpVideoFormat&);
  SdpVideoFormat& operator=(SdpVideoFormat&&);

  ~SdpVideoFormat();

  // Returns true if the SdpVideoFormats have the same names as well as codec
  // specific parameters. Please note that two SdpVideoFormats can represent the
  // same codec even though not all parameters are the same.
  bool IsSameCodec(const SdpVideoFormat& other) const;
  bool IsCodecInList(
      rtc::ArrayView<const webrtc::SdpVideoFormat> formats) const;

  std::string ToString() const;

  friend RTC_EXPORT bool operator==(const SdpVideoFormat& a,
                                    const SdpVideoFormat& b);
  friend RTC_EXPORT bool operator!=(const SdpVideoFormat& a,
                                    const SdpVideoFormat& b) {
    return !(a == b);
  }

  std::string name;
  Parameters parameters;
  absl::InlinedVector<ScalabilityMode, kScalabilityModeCount> scalability_modes;
};

// For not so good reasons sometimes additional parameters are added to an
// SdpVideoFormat, which makes instances that should compare equal to not match
// anymore. Until we stop misusing SdpVideoFormats provide this convenience
// function to perform fuzzy matching.
absl::optional<SdpVideoFormat> FuzzyMatchSdpVideoFormat(
    rtc::ArrayView<const SdpVideoFormat> supported_formats,
    const SdpVideoFormat& format);

}  // namespace webrtc

#endif  // API_VIDEO_CODECS_SDP_VIDEO_FORMAT_H_
