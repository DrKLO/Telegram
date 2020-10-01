/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef SDK_ANDROID_SRC_JNI_ANDROID_MEDIA_CODEC_COMMON_H_
#define SDK_ANDROID_SRC_JNI_ANDROID_MEDIA_CODEC_COMMON_H_

#include <string>

#include "rtc_base/logging.h"
#include "rtc_base/thread.h"
#include "sdk/android/src/jni/jni_helpers.h"

namespace webrtc {
namespace jni {

// Uncomment this define to enable verbose logging for every encoded/decoded
// video frame.
//#define TRACK_BUFFER_TIMING

#define TAG_COMMON "MediaCodecVideo"

// Color formats supported by encoder or decoder - should include all
// colors from supportedColorList in MediaCodecVideoEncoder.java and
// MediaCodecVideoDecoder.java. Supported color format set in encoder
// and decoder could be different.
enum COLOR_FORMATTYPE {
  COLOR_FormatYUV420Planar = 0x13,
  COLOR_FormatYUV420SemiPlanar = 0x15,
  COLOR_QCOM_FormatYUV420SemiPlanar = 0x7FA30C00,
  // NV12 color format supported by QCOM codec, but not declared in MediaCodec -
  // see /hardware/qcom/media/mm-core/inc/OMX_QCOMExtns.h
  // This format is presumably similar to COLOR_FormatYUV420SemiPlanar,
  // but requires some (16, 32?) byte alignment.
  COLOR_QCOM_FORMATYVU420PackedSemiPlanar32m4ka = 0x7FA30C01,
  COLOR_QCOM_FORMATYVU420PackedSemiPlanar16m4ka = 0x7FA30C02,
  COLOR_QCOM_FORMATYVU420PackedSemiPlanar64x32Tile2m8ka = 0x7FA30C03,
  COLOR_QCOM_FORMATYUV420PackedSemiPlanar32m = 0x7FA30C04
};

// Arbitrary interval to poll the codec for new outputs.
enum { kMediaCodecPollMs = 10 };
// Arbitrary interval to poll at when there should be no more frames.
enum { kMediaCodecPollNoFramesMs = 100 };
// Media codec maximum output buffer ready timeout.
enum { kMediaCodecTimeoutMs = 1000 };
// Interval to print codec statistics (bitrate, fps, encoding/decoding time).
enum { kMediaCodecStatisticsIntervalMs = 3000 };
// Maximum amount of pending frames for VP8 decoder.
enum { kMaxPendingFramesVp8 = 1 };
// Maximum amount of pending frames for VP9 decoder.
enum { kMaxPendingFramesVp9 = 1 };
// Maximum amount of pending frames for H.264 decoder.
enum { kMaxPendingFramesH264 = 4 };
// TODO(zhanghe): update the value after checking more devices if needed.
// Maximum amount of pending frames for H.265 decoder.
enum { kMaxPendingFramesH265 = 4 };
// Maximum amount of decoded frames for which per-frame logging is enabled.
enum { kMaxDecodedLogFrames = 10 };
// Maximum amount of encoded frames for which per-frame logging is enabled.
enum { kMaxEncodedLogFrames = 10 };

static inline void AllowBlockingCalls() {
  rtc::Thread* current_thread = rtc::Thread::Current();
  if (current_thread != NULL)
    current_thread->DEPRECATED_AllowBlockingCalls();
}

// Checks for any Java exception, prints stack backtrace and clears
// currently thrown exception.
static inline bool CheckException(JNIEnv* jni) {
  if (jni->ExceptionCheck()) {
    RTC_LOG_TAG(rtc::LS_ERROR, TAG_COMMON) << "Java JNI exception.";
    jni->ExceptionDescribe();
    jni->ExceptionClear();
    return true;
  }
  return false;
}

}  // namespace jni
}  // namespace webrtc

#endif  // SDK_ANDROID_SRC_JNI_ANDROID_MEDIA_CODEC_COMMON_H_
