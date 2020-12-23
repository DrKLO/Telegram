/*
 *  Copyright 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_MEDIA_TYPES_H_
#define API_MEDIA_TYPES_H_

#include <string>

#include "rtc_base/system/rtc_export.h"

// The cricket and webrtc have separate definitions for what a media type is.
// They're not compatible. Watch out for this.

namespace cricket {

enum MediaType {
  MEDIA_TYPE_AUDIO,
  MEDIA_TYPE_VIDEO,
  MEDIA_TYPE_DATA,
  MEDIA_TYPE_UNSUPPORTED
};

extern const char kMediaTypeAudio[];
extern const char kMediaTypeVideo[];
extern const char kMediaTypeData[];

RTC_EXPORT std::string MediaTypeToString(MediaType type);

}  // namespace cricket

namespace webrtc {

enum class MediaType { ANY, AUDIO, VIDEO, DATA };

}  // namespace webrtc

#endif  // API_MEDIA_TYPES_H_
