/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <array>
#include <string>
#include <vector>

#include "absl/flags/flag.h"
#include "absl/flags/parse.h"
#include "common_audio/resampler/push_sinc_resampler.h"
#include "common_audio/wav_file.h"
#include "modules/audio_processing/agc2/rnn_vad/common.h"
#include "modules/audio_processing/agc2/rnn_vad/features_extraction.h"
#include "modules/audio_processing/agc2/rnn_vad/rnn.h"
#include "rtc_base/logging.h"

ABSL_FLAG(std::string, i, "", "Path to the input wav file");
ABSL_FLAG(std::string, f, "", "Path to the output features file");
ABSL_FLAG(std::string, o, "", "Path to the output VAD probabilities file");

namespace webrtc {
namespace rnn_vad {
namespace test {

int main(int argc, char* argv[]) {
  absl::ParseCommandLine(argc, argv);
  rtc::LogMessage::LogToDebug(rtc::LS_INFO);

  // Open wav input file and check properties.
  const std::string input_wav_file = absl::GetFlag(FLAGS_i);
  WavReader wav_reader(input_wav_file);
  if (wav_reader.num_channels() != 1) {
    RTC_LOG(LS_ERROR) << "Only mono wav files are supported";
    return 1;
  }
  if (wav_reader.sample_rate() % 100 != 0) {
    RTC_LOG(LS_ERROR) << "The sample rate rate must allow 10 ms frames.";
    return 1;
  }
  RTC_LOG(LS_INFO) << "Input sample rate: " << wav_reader.sample_rate();

  // Init output files.
  const std::string output_vad_probs_file = absl::GetFlag(FLAGS_o);
  FILE* vad_probs_file = fopen(output_vad_probs_file.c_str(), "wb");
  FILE* features_file = nullptr;
  const std::string output_feature_file = absl::GetFlag(FLAGS_f);
  if (!output_feature_file.empty()) {
    features_file = fopen(output_feature_file.c_str(), "wb");
  }

  // Initialize.
  const size_t frame_size_10ms =
      rtc::CheckedDivExact(wav_reader.sample_rate(), 100);
  std::vector<float> samples_10ms;
  samples_10ms.resize(frame_size_10ms);
  std::array<float, kFrameSize10ms24kHz> samples_10ms_24kHz;
  PushSincResampler resampler(frame_size_10ms, kFrameSize10ms24kHz);
  FeaturesExtractor features_extractor;
  std::array<float, kFeatureVectorSize> feature_vector;
  RnnBasedVad rnn_vad;

  // Compute VAD probabilities.
  while (true) {
    // Read frame at the input sample rate.
    const auto read_samples =
        wav_reader.ReadSamples(frame_size_10ms, samples_10ms.data());
    if (read_samples < frame_size_10ms) {
      break;  // EOF.
    }
    // Resample input.
    resampler.Resample(samples_10ms.data(), samples_10ms.size(),
                       samples_10ms_24kHz.data(), samples_10ms_24kHz.size());

    // Extract features and feed the RNN.
    bool is_silence = features_extractor.CheckSilenceComputeFeatures(
        samples_10ms_24kHz, feature_vector);
    float vad_probability =
        rnn_vad.ComputeVadProbability(feature_vector, is_silence);
    // Write voice probability.
    RTC_DCHECK_GE(vad_probability, 0.f);
    RTC_DCHECK_GE(1.f, vad_probability);
    fwrite(&vad_probability, sizeof(float), 1, vad_probs_file);
    // Write features.
    if (features_file) {
      const float float_is_silence = is_silence ? 1.f : 0.f;
      fwrite(&float_is_silence, sizeof(float), 1, features_file);
      if (is_silence) {
        // Do not write uninitialized values.
        feature_vector.fill(0.f);
      }
      fwrite(feature_vector.data(), sizeof(float), kFeatureVectorSize,
             features_file);
    }
  }

  // Close output file(s).
  fclose(vad_probs_file);
  RTC_LOG(LS_INFO) << "VAD probabilities written to " << output_vad_probs_file;
  if (features_file) {
    fclose(features_file);
    RTC_LOG(LS_INFO) << "features written to " << output_feature_file;
  }

  return 0;
}

}  // namespace test
}  // namespace rnn_vad
}  // namespace webrtc

int main(int argc, char* argv[]) {
  return webrtc::rnn_vad::test::main(argc, argv);
}
