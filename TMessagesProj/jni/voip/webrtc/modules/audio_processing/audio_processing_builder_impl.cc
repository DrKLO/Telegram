/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>

#include "api/make_ref_counted.h"
#include "modules/audio_processing/audio_processing_impl.h"
#include "modules/audio_processing/include/audio_processing.h"

namespace webrtc {

AudioProcessingBuilder::AudioProcessingBuilder() = default;
AudioProcessingBuilder::~AudioProcessingBuilder() = default;

rtc::scoped_refptr<AudioProcessing> AudioProcessingBuilder::Create() {
#ifdef WEBRTC_EXCLUDE_AUDIO_PROCESSING_MODULE
  // Return a null pointer when the APM is excluded from the build.
  return nullptr;
#else  // WEBRTC_EXCLUDE_AUDIO_PROCESSING_MODULE
  return rtc::make_ref_counted<AudioProcessingImpl>(
      config_, std::move(capture_post_processing_),
      std::move(render_pre_processing_), std::move(echo_control_factory_),
      std::move(echo_detector_), std::move(capture_analyzer_));
#endif
}

}  // namespace webrtc
