/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef SDK_ANDROID_SRC_JNI_PC_AUDIO_H_
#define SDK_ANDROID_SRC_JNI_PC_AUDIO_H_

#include "api/scoped_refptr.h"
#include "modules/audio_processing/include/audio_processing.h"

namespace webrtc {
namespace jni {

rtc::scoped_refptr<AudioProcessing> CreateAudioProcessing();

}  // namespace jni
}  // namespace webrtc

#endif  // SDK_ANDROID_SRC_JNI_PC_AUDIO_H_
