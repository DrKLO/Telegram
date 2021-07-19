/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/include/audio_processing.h"

#include <memory>

#include "modules/audio_processing/audio_processing_impl.h"
#include "rtc_base/ref_counted_object.h"

namespace webrtc {

AudioProcessingBuilder::AudioProcessingBuilder() = default;
AudioProcessingBuilder::~AudioProcessingBuilder() = default;

AudioProcessing* AudioProcessingBuilder::Create() {
  webrtc::Config config;
  return Create(config);
}

AudioProcessing* AudioProcessingBuilder::Create(const webrtc::Config& config) {
#ifdef WEBRTC_EXCLUDE_AUDIO_PROCESSING_MODULE

  // Implementation returning a null pointer for using when the APM is excluded
  // from the build..
  return nullptr;

#else

  // Standard implementation.
  return new rtc::RefCountedObject<AudioProcessingImpl>(
      config, std::move(capture_post_processing_),
      std::move(render_pre_processing_), std::move(echo_control_factory_),
      std::move(echo_detector_), std::move(capture_analyzer_));
#endif
}

}  // namespace webrtc
