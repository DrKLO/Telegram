/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AUDIO_GENERATOR_FILE_AUDIO_GENERATOR_H_
#define MODULES_AUDIO_PROCESSING_AUDIO_GENERATOR_FILE_AUDIO_GENERATOR_H_

#include <memory>

#include "common_audio/wav_file.h"
#include "modules/audio_processing/include/audio_generator.h"
#include "rtc_base/constructormagic.h"

namespace webrtc {

// Provides looping audio from a file. The file is read in its entirety on
// construction and then closed. This class wraps a webrtc::WavReader, and is
// hence unsuitable for non-diagnostic code.
class FileAudioGenerator : public AudioGenerator {
 public:
  // Reads the playout audio from a given WAV file.
  explicit FileAudioGenerator(std::unique_ptr<WavReader> input_audio_file);

  ~FileAudioGenerator() override;

  // Fill |audio| with audio from a file.
  void FillFrame(AudioFrameView<float> audio) override;

  size_t NumChannels() override;

  size_t SampleRateHz() override;

 private:
  size_t num_channels_;
  size_t sample_rate_hz_;

  RTC_DISALLOW_COPY_AND_ASSIGN(FileAudioGenerator);
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AUDIO_GENERATOR_FILE_AUDIO_GENERATOR_H_
