/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_INCLUDE_AUDIO_GENERATOR_FACTORY_H_
#define MODULES_AUDIO_PROCESSING_INCLUDE_AUDIO_GENERATOR_FACTORY_H_

#include <memory>
#include <string>
#include <utility>

#include "modules/audio_processing/include/audio_generator.h"

namespace webrtc {

class AudioGeneratorFactory {
 public:
  // Creates an AudioGenerator that reads the playout audio from a given 16-bit
  // int-encoded WAV file.
  static std::unique_ptr<AudioGenerator> Create(const std::string& file_name);
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_INCLUDE_AUDIO_GENERATOR_FACTORY_H_
