/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_COMMON_H_
#define MODULES_AUDIO_PROCESSING_COMMON_H_

#include "modules/audio_processing/include/audio_processing.h"
#include "rtc_base/checks.h"

namespace webrtc {

constexpr int RuntimeSettingQueueSize() {
  return 100;
}

static inline size_t ChannelsFromLayout(AudioProcessing::ChannelLayout layout) {
  switch (layout) {
    case AudioProcessing::kMono:
    case AudioProcessing::kMonoAndKeyboard:
      return 1;
    case AudioProcessing::kStereo:
    case AudioProcessing::kStereoAndKeyboard:
      return 2;
  }
  RTC_DCHECK_NOTREACHED();
  return 0;
}

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_COMMON_H_
