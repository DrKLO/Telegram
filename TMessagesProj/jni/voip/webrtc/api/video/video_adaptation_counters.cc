/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video/video_adaptation_counters.h"

#include "rtc_base/strings/string_builder.h"

namespace webrtc {

bool VideoAdaptationCounters::operator==(
    const VideoAdaptationCounters& rhs) const {
  return fps_adaptations == rhs.fps_adaptations &&
         resolution_adaptations == rhs.resolution_adaptations;
}

bool VideoAdaptationCounters::operator!=(
    const VideoAdaptationCounters& rhs) const {
  return !(rhs == *this);
}

VideoAdaptationCounters VideoAdaptationCounters::operator+(
    const VideoAdaptationCounters& other) const {
  return VideoAdaptationCounters(
      resolution_adaptations + other.resolution_adaptations,
      fps_adaptations + other.fps_adaptations);
}

std::string VideoAdaptationCounters::ToString() const {
  rtc::StringBuilder ss;
  ss << "{ res=" << resolution_adaptations << " fps=" << fps_adaptations
     << " }";
  return ss.Release();
}

}  // namespace webrtc
