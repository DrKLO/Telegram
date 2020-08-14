/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/audio_device/opensles_common.h"

#include <SLES/OpenSLES.h>

#include "rtc_base/arraysize.h"
#include "rtc_base/checks.h"

namespace webrtc {

namespace jni {

// Returns a string representation given an integer SL_RESULT_XXX code.
// The mapping can be found in <SLES/OpenSLES.h>.
const char* GetSLErrorString(size_t code) {
  static const char* sl_error_strings[] = {
      "SL_RESULT_SUCCESS",                 // 0
      "SL_RESULT_PRECONDITIONS_VIOLATED",  // 1
      "SL_RESULT_PARAMETER_INVALID",       // 2
      "SL_RESULT_MEMORY_FAILURE",          // 3
      "SL_RESULT_RESOURCE_ERROR",          // 4
      "SL_RESULT_RESOURCE_LOST",           // 5
      "SL_RESULT_IO_ERROR",                // 6
      "SL_RESULT_BUFFER_INSUFFICIENT",     // 7
      "SL_RESULT_CONTENT_CORRUPTED",       // 8
      "SL_RESULT_CONTENT_UNSUPPORTED",     // 9
      "SL_RESULT_CONTENT_NOT_FOUND",       // 10
      "SL_RESULT_PERMISSION_DENIED",       // 11
      "SL_RESULT_FEATURE_UNSUPPORTED",     // 12
      "SL_RESULT_INTERNAL_ERROR",          // 13
      "SL_RESULT_UNKNOWN_ERROR",           // 14
      "SL_RESULT_OPERATION_ABORTED",       // 15
      "SL_RESULT_CONTROL_LOST",            // 16
  };

  if (code >= arraysize(sl_error_strings)) {
    return "SL_RESULT_UNKNOWN_ERROR";
  }
  return sl_error_strings[code];
}

SLDataFormat_PCM CreatePCMConfiguration(size_t channels,
                                        int sample_rate,
                                        size_t bits_per_sample) {
  RTC_CHECK_EQ(bits_per_sample, SL_PCMSAMPLEFORMAT_FIXED_16);
  SLDataFormat_PCM format;
  format.formatType = SL_DATAFORMAT_PCM;
  format.numChannels = static_cast<SLuint32>(channels);
  // Note that, the unit of sample rate is actually in milliHertz and not Hertz.
  switch (sample_rate) {
    case 8000:
      format.samplesPerSec = SL_SAMPLINGRATE_8;
      break;
    case 16000:
      format.samplesPerSec = SL_SAMPLINGRATE_16;
      break;
    case 22050:
      format.samplesPerSec = SL_SAMPLINGRATE_22_05;
      break;
    case 32000:
      format.samplesPerSec = SL_SAMPLINGRATE_32;
      break;
    case 44100:
      format.samplesPerSec = SL_SAMPLINGRATE_44_1;
      break;
    case 48000:
      format.samplesPerSec = SL_SAMPLINGRATE_48;
      break;
    case 64000:
      format.samplesPerSec = SL_SAMPLINGRATE_64;
      break;
    case 88200:
      format.samplesPerSec = SL_SAMPLINGRATE_88_2;
      break;
    case 96000:
      format.samplesPerSec = SL_SAMPLINGRATE_96;
      break;
    default:
      RTC_CHECK(false) << "Unsupported sample rate: " << sample_rate;
      break;
  }
  format.bitsPerSample = SL_PCMSAMPLEFORMAT_FIXED_16;
  format.containerSize = SL_PCMSAMPLEFORMAT_FIXED_16;
  format.endianness = SL_BYTEORDER_LITTLEENDIAN;
  if (format.numChannels == 1) {
    format.channelMask = SL_SPEAKER_FRONT_CENTER;
  } else if (format.numChannels == 2) {
    format.channelMask = SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT;
  } else {
    RTC_CHECK(false) << "Unsupported number of channels: "
                     << format.numChannels;
  }
  return format;
}

OpenSLEngineManager::OpenSLEngineManager() {
  thread_checker_.Detach();
}

OpenSLEngineManager::~OpenSLEngineManager() = default;

SLObjectItf OpenSLEngineManager::GetOpenSLEngine() {
  RTC_LOG(INFO) << "GetOpenSLEngine";
  RTC_DCHECK(thread_checker_.IsCurrent());
  // OpenSL ES for Android only supports a single engine per application.
  // If one already has been created, return existing object instead of
  // creating a new.
  if (engine_object_.Get() != nullptr) {
    RTC_LOG(WARNING) << "The OpenSL ES engine object has already been created";
    return engine_object_.Get();
  }
  // Create the engine object in thread safe mode.
  const SLEngineOption option[] = {
      {SL_ENGINEOPTION_THREADSAFE, static_cast<SLuint32>(SL_BOOLEAN_TRUE)}};
  SLresult result =
      slCreateEngine(engine_object_.Receive(), 1, option, 0, NULL, NULL);
  if (result != SL_RESULT_SUCCESS) {
    RTC_LOG(LS_ERROR) << "slCreateEngine() failed: "
                      << GetSLErrorString(result);
    engine_object_.Reset();
    return nullptr;
  }
  // Realize the SL Engine in synchronous mode.
  result = engine_object_->Realize(engine_object_.Get(), SL_BOOLEAN_FALSE);
  if (result != SL_RESULT_SUCCESS) {
    RTC_LOG(LS_ERROR) << "Realize() failed: " << GetSLErrorString(result);
    engine_object_.Reset();
    return nullptr;
  }
  // Finally return the SLObjectItf interface of the engine object.
  return engine_object_.Get();
}

}  // namespace jni

}  // namespace webrtc
