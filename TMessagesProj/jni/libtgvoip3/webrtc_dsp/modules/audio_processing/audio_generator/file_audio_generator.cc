/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/audio_generator/file_audio_generator.h"

namespace webrtc {

FileAudioGenerator::FileAudioGenerator(
    std::unique_ptr<WavReader> input_audio_file) {
  // TODO(bugs.webrtc.org/8882) Stub.
  // Read audio from file into internal buffer.
}

FileAudioGenerator::~FileAudioGenerator() = default;

void FileAudioGenerator::FillFrame(AudioFrameView<float> audio) {
  // TODO(bugs.webrtc.org/8882) Stub.
  // Fill |audio| from internal buffer.
}

size_t FileAudioGenerator::NumChannels() {
  return num_channels_;
}

size_t FileAudioGenerator::SampleRateHz() {
  return sample_rate_hz_;
}

}  // namespace webrtc
