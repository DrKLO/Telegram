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

#include "rtc_base/system/rtc_export.h"

namespace webrtc {

// SDP specification for a single video codec.
// NOTE: This class is still under development and may change without notice.
struct RTC_EXPORT SdpVideoFormat {
  using Parameters = std::map<std::string, std::string>;

  explicit SdpVideoFormat(const std::string& name);
  SdpVideoFormat(const std::string& name, const Parameters& parameters);
  SdpVideoFormat(const SdpVideoFormat&);
  SdpVideoFormat(SdpVideoFormat&&);
  SdpVideoFormat& operator=(const SdpVideoFormat&);
  SdpVideoFormat& operator=(SdpVideoFormat&&);

  ~SdpVideoFormat();

  std::string ToString() const;

  friend RTC_EXPORT bool operator==(const SdpVideoFormat& a,
                                    const SdpVideoFormat& b);
  friend RTC_EXPORT bool operator!=(const SdpVideoFormat& a,
                                    const SdpVideoFormat& b) {
    return !(a == b);
  }

  std::string name;
  Parameters parameters;
};

}  // namespace webrtc

#endif  // API_VIDEO_CODECS_SDP_VIDEO_FORMAT_H_
