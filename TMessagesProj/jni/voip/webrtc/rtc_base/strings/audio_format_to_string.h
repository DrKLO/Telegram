/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_STRINGS_AUDIO_FORMAT_TO_STRING_H_
#define RTC_BASE_STRINGS_AUDIO_FORMAT_TO_STRING_H_

#include <string>

#include "api/audio_codecs/audio_format.h"

namespace rtc {
std::string ToString(const webrtc::SdpAudioFormat& saf);
std::string ToString(const webrtc::AudioCodecInfo& saf);
std::string ToString(const webrtc::AudioCodecSpec& acs);
}  // namespace rtc

#endif  // RTC_BASE_STRINGS_AUDIO_FORMAT_TO_STRING_H_
