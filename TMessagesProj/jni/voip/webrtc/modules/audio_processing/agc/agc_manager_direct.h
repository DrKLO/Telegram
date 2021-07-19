/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AGC_AGC_MANAGER_DIRECT_H_
#define MODULES_AUDIO_PROCESSING_AGC_AGC_MANAGER_DIRECT_H_

#include <memory>

#include "absl/types/optional.h"
#include "modules/audio_processing/agc/agc.h"
#include "modules/audio_processing/audio_buffer.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/gtest_prod_util.h"

namespace webrtc {

class MonoAgc;
class GainControl;

// Direct interface to use AGC to set volume and compression values.
// AudioProcessing uses this interface directly to integrate the callback-less
// AGC.
//
// This class is not thread-safe.
class AgcManagerDirect final {
 public:
  // AgcManagerDirect will configure GainControl internally. The user is
  // responsible for processing the audio using it after the call to Process.
  // The operating range of startup_min_level is [12, 255] and any input value
  // outside that range will be clamped. `clipped_level_step` is the amount
  // the microphone level is lowered with every clipping event, limited to
  // (0, 255]. `clipped_ratio_threshold` is the proportion of clipped
  // samples required to declare a clipping event, limited to (0.f, 1.f).
  // `clipped_wait_frames` is the time in frames to wait after a clipping event
  // before checking again, limited to values higher than 0.
  AgcManagerDirect(int num_capture_channels,
                   int startup_min_level,
                   int clipped_level_min,
                   bool disable_digital_adaptive,
                   int sample_rate_hz,
                   int clipped_level_step,
                   float clipped_ratio_threshold,
                   int clipped_wait_frames);

  ~AgcManagerDirect();
  AgcManagerDirect(const AgcManagerDirect&) = delete;
  AgcManagerDirect& operator=(const AgcManagerDirect&) = delete;

  void Initialize();
  void SetupDigitalGainControl(GainControl* gain_control) const;

  void AnalyzePreProcess(const AudioBuffer* audio);
  void Process(const AudioBuffer* audio);

  // Call when the capture stream output has been flagged to be used/not-used.
  // If unused, the manager  disregards all incoming audio.
  void HandleCaptureOutputUsedChange(bool capture_output_used);
  float voice_probability() const;

  int stream_analog_level() const { return stream_analog_level_; }
  void set_stream_analog_level(int level);
  int num_channels() const { return num_capture_channels_; }
  int sample_rate_hz() const { return sample_rate_hz_; }

  // If available, returns a new compression gain for the digital gain control.
  absl::optional<int> GetDigitalComressionGain();

 private:
  friend class AgcManagerDirectTest;

  FRIEND_TEST_ALL_PREFIXES(AgcManagerDirectStandaloneTest,
                           DisableDigitalDisablesDigital);
  FRIEND_TEST_ALL_PREFIXES(AgcManagerDirectStandaloneTest,
                           AgcMinMicLevelExperiment);
  FRIEND_TEST_ALL_PREFIXES(AgcManagerDirectStandaloneTest,
                           AgcMinMicLevelExperimentDisabled);
  FRIEND_TEST_ALL_PREFIXES(AgcManagerDirectStandaloneTest,
                           AgcMinMicLevelExperimentOutOfRangeAbove);
  FRIEND_TEST_ALL_PREFIXES(AgcManagerDirectStandaloneTest,
                           AgcMinMicLevelExperimentOutOfRangeBelow);
  FRIEND_TEST_ALL_PREFIXES(AgcManagerDirectStandaloneTest,
                           AgcMinMicLevelExperimentEnabled50);
  FRIEND_TEST_ALL_PREFIXES(AgcManagerDirectStandaloneTest,
                           AgcMinMicLevelExperimentEnabledAboveStartupLevel);
  FRIEND_TEST_ALL_PREFIXES(AgcManagerDirectStandaloneTest,
                           ClippingParametersVerified);

  // Dependency injection for testing. Don't delete |agc| as the memory is owned
  // by the manager.
  AgcManagerDirect(Agc* agc,
                   int startup_min_level,
                   int clipped_level_min,
                   int sample_rate_hz,
                   int clipped_level_step,
                   float clipped_ratio_threshold,
                   int clipped_wait_frames);

  void AnalyzePreProcess(const float* const* audio, size_t samples_per_channel);

  void AggregateChannelLevels();

  std::unique_ptr<ApmDataDumper> data_dumper_;
  static int instance_counter_;
  const bool use_min_channel_level_;
  const int sample_rate_hz_;
  const int num_capture_channels_;
  const bool disable_digital_adaptive_;

  int frames_since_clipped_;
  int stream_analog_level_ = 0;
  bool capture_output_used_;
  int channel_controlling_gain_ = 0;

  const int clipped_level_step_;
  const float clipped_ratio_threshold_;
  const int clipped_wait_frames_;

  std::vector<std::unique_ptr<MonoAgc>> channel_agcs_;
  std::vector<absl::optional<int>> new_compressions_to_set_;
};

class MonoAgc {
 public:
  MonoAgc(ApmDataDumper* data_dumper,
          int startup_min_level,
          int clipped_level_min,
          bool disable_digital_adaptive,
          int min_mic_level);
  ~MonoAgc();
  MonoAgc(const MonoAgc&) = delete;
  MonoAgc& operator=(const MonoAgc&) = delete;

  void Initialize();
  void HandleCaptureOutputUsedChange(bool capture_output_used);

  void HandleClipping(int clipped_level_step);

  void Process(const int16_t* audio,
               size_t samples_per_channel,
               int sample_rate_hz);

  void set_stream_analog_level(int level) { stream_analog_level_ = level; }
  int stream_analog_level() const { return stream_analog_level_; }
  float voice_probability() const { return agc_->voice_probability(); }
  void ActivateLogging() { log_to_histograms_ = true; }
  absl::optional<int> new_compression() const {
    return new_compression_to_set_;
  }

  // Only used for testing.
  void set_agc(Agc* agc) { agc_.reset(agc); }
  int min_mic_level() const { return min_mic_level_; }
  int startup_min_level() const { return startup_min_level_; }

 private:
  // Sets a new microphone level, after first checking that it hasn't been
  // updated by the user, in which case no action is taken.
  void SetLevel(int new_level);

  // Set the maximum level the AGC is allowed to apply. Also updates the
  // maximum compression gain to compensate. The level must be at least
  // |kClippedLevelMin|.
  void SetMaxLevel(int level);

  int CheckVolumeAndReset();
  void UpdateGain();
  void UpdateCompressor();

  const int min_mic_level_;
  const bool disable_digital_adaptive_;
  std::unique_ptr<Agc> agc_;
  int level_ = 0;
  int max_level_;
  int max_compression_gain_;
  int target_compression_;
  int compression_;
  float compression_accumulator_;
  bool capture_output_used_ = true;
  bool check_volume_on_next_process_ = true;
  bool startup_ = true;
  int startup_min_level_;
  int calls_since_last_gain_log_ = 0;
  int stream_analog_level_ = 0;
  absl::optional<int> new_compression_to_set_;
  bool log_to_histograms_ = false;
  const int clipped_level_min_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AGC_AGC_MANAGER_DIRECT_H_
