/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/include/audio_generator_factory.h"

#include "absl/memory/memory.h"
#include "common_audio/wav_file.h"
#include "modules/audio_processing/audio_generator/file_audio_generator.h"

namespace webrtc {

std::unique_ptr<AudioGenerator> AudioGeneratorFactory::Create(
    const std::string& file_name) {
  std::unique_ptr<WavReader> input_audio_file(new WavReader(file_name));
  return absl::make_unique<FileAudioGenerator>(std::move(input_audio_file));
}

}  // namespace webrtc
