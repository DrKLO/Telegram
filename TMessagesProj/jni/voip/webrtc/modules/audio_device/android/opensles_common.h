/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_DEVICE_ANDROID_OPENSLES_COMMON_H_
#define MODULES_AUDIO_DEVICE_ANDROID_OPENSLES_COMMON_H_

#include <SLES/OpenSLES.h>
#include <stddef.h>

#include "rtc_base/checks.h"

namespace webrtc {

// Returns a string representation given an integer SL_RESULT_XXX code.
// The mapping can be found in <SLES/OpenSLES.h>.
const char* GetSLErrorString(size_t code);

// Configures an SL_DATAFORMAT_PCM structure based on native audio parameters.
SLDataFormat_PCM CreatePCMConfiguration(size_t channels,
                                        int sample_rate,
                                        size_t bits_per_sample);

// Helper class for using SLObjectItf interfaces.
template <typename SLType, typename SLDerefType>
class ScopedSLObject {
 public:
  ScopedSLObject() : obj_(nullptr) {}

  ~ScopedSLObject() { Reset(); }

  SLType* Receive() {
    RTC_DCHECK(!obj_);
    return &obj_;
  }

  SLDerefType operator->() { return *obj_; }

  SLType Get() const { return obj_; }

  void Reset() {
    if (obj_) {
      (*obj_)->Destroy(obj_);
      obj_ = nullptr;
    }
  }

 private:
  SLType obj_;
};

typedef ScopedSLObject<SLObjectItf, const SLObjectItf_*> ScopedSLObjectItf;

}  // namespace webrtc

#endif  // MODULES_AUDIO_DEVICE_ANDROID_OPENSLES_COMMON_H_
