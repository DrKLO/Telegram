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

#include "modules/audio_processing/agc/agc.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/constructormagic.h"
#include "rtc_base/gtest_prod_util.h"

namespace webrtc {

class AudioFrame;
class DebugFile;
class GainControl;

// Callbacks that need to be injected into AgcManagerDirect to read and control
// the volume values. This is done to remove the VoiceEngine dependency in
// AgcManagerDirect.
// TODO(aluebs): Remove VolumeCallbacks.
class VolumeCallbacks {
 public:
  virtual ~VolumeCallbacks() {}
  virtual void SetMicVolume(int volume) = 0;
  virtual int GetMicVolume() = 0;
};

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
  // outside that range will be clamped.
  AgcManagerDirect(GainControl* gctrl,
                   VolumeCallbacks* volume_callbacks,
                   int startup_min_level,
                   int clipped_level_min,
                   bool use_agc2_level_estimation,
                   bool disable_digital_adaptive);

  ~AgcManagerDirect();

  int Initialize();
  void AnalyzePreProcess(int16_t* audio,
                         int num_channels,
                         size_t samples_per_channel);
  void Process(const int16_t* audio, size_t length, int sample_rate_hz);

  // Call when the capture stream has been muted/unmuted. This causes the
  // manager to disregard all incoming audio; chances are good it's background
  // noise to which we'd like to avoid adapting.
  void SetCaptureMuted(bool muted);
  bool capture_muted() { return capture_muted_; }

  float voice_probability();

 private:
  friend class AgcManagerDirectTest;

  FRIEND_TEST_ALL_PREFIXES(AgcManagerDirectStandaloneTest,
                           DisableDigitalDisablesDigital);

  // Dependency injection for testing. Don't delete |agc| as the memory is owned
  // by the manager.
  AgcManagerDirect(Agc* agc,
                   GainControl* gctrl,
                   VolumeCallbacks* volume_callbacks,
                   int startup_min_level,
                   int clipped_level_min);

  // Most general c-tor.
  AgcManagerDirect(Agc* agc,
                   GainControl* gctrl,
                   VolumeCallbacks* volume_callbacks,
                   int startup_min_level,
                   int clipped_level_min,
                   bool use_agc2_level_estimation,
                   bool disable_digital_adaptive);

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

  std::unique_ptr<ApmDataDumper> data_dumper_;
  static int instance_counter_;

  std::unique_ptr<Agc> agc_;
  GainControl* gctrl_;
  VolumeCallbacks* volume_callbacks_;

  int frames_since_clipped_;
  int level_;
  int max_level_;
  int max_compression_gain_;
  int target_compression_;
  int compression_;
  float compression_accumulator_;
  bool capture_muted_;
  bool check_volume_on_next_process_;
  bool startup_;
  const bool use_agc2_level_estimation_;
  const bool disable_digital_adaptive_;
  int startup_min_level_;
  const int clipped_level_min_;
  int calls_since_last_gain_log_ = 0;

  std::unique_ptr<DebugFile> file_preproc_;
  std::unique_ptr<DebugFile> file_postproc_;

  RTC_DISALLOW_COPY_AND_ASSIGN(AgcManagerDirect);
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AGC_AGC_MANAGER_DIRECT_H_
