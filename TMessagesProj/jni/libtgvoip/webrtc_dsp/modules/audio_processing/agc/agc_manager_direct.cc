/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc/agc_manager_direct.h"

#include <algorithm>
#include <cmath>

#ifdef WEBRTC_AGC_DEBUG_DUMP
#include <cstdio>
#endif

#include "modules/audio_processing/agc/gain_map_internal.h"
#include "modules/audio_processing/agc2/adaptive_mode_level_estimator_agc.h"
#include "modules/audio_processing/include/gain_control.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_minmax.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {

int AgcManagerDirect::instance_counter_ = 0;

namespace {

// Amount the microphone level is lowered with every clipping event.
const int kClippedLevelStep = 15;
// Proportion of clipped samples required to declare a clipping event.
const float kClippedRatioThreshold = 0.1f;
// Time in frames to wait after a clipping event before checking again.
const int kClippedWaitFrames = 300;

// Amount of error we tolerate in the microphone level (presumably due to OS
// quantization) before we assume the user has manually adjusted the microphone.
const int kLevelQuantizationSlack = 25;

const int kDefaultCompressionGain = 7;
const int kMaxCompressionGain = 12;
const int kMinCompressionGain = 2;
// Controls the rate of compression changes towards the target.
const float kCompressionGainStep = 0.05f;

const int kMaxMicLevel = 255;
static_assert(kGainMapSize > kMaxMicLevel, "gain map too small");
const int kMinMicLevel = 12;

// Prevent very large microphone level changes.
const int kMaxResidualGainChange = 15;

// Maximum additional gain allowed to compensate for microphone level
// restrictions from clipping events.
const int kSurplusCompressionGain = 6;

int ClampLevel(int mic_level) {
  return rtc::SafeClamp(mic_level, kMinMicLevel, kMaxMicLevel);
}

int LevelFromGainError(int gain_error, int level) {
  RTC_DCHECK_GE(level, 0);
  RTC_DCHECK_LE(level, kMaxMicLevel);
  if (gain_error == 0) {
    return level;
  }
  // TODO(ajm): Could be made more efficient with a binary search.
  int new_level = level;
  if (gain_error > 0) {
    while (kGainMap[new_level] - kGainMap[level] < gain_error &&
           new_level < kMaxMicLevel) {
      ++new_level;
    }
  } else {
    while (kGainMap[new_level] - kGainMap[level] > gain_error &&
           new_level > kMinMicLevel) {
      --new_level;
    }
  }
  return new_level;
}

int InitializeGainControl(GainControl* gain_control,
                          bool disable_digital_adaptive) {
  if (gain_control->set_mode(GainControl::kFixedDigital) != 0) {
    RTC_LOG(LS_ERROR) << "set_mode(GainControl::kFixedDigital) failed.";
    return -1;
  }
  const int target_level_dbfs = disable_digital_adaptive ? 0 : 2;
  if (gain_control->set_target_level_dbfs(target_level_dbfs) != 0) {
    RTC_LOG(LS_ERROR) << "set_target_level_dbfs() failed.";
    return -1;
  }
  const int compression_gain_db =
      disable_digital_adaptive ? 0 : kDefaultCompressionGain;
  if (gain_control->set_compression_gain_db(compression_gain_db) != 0) {
    RTC_LOG(LS_ERROR) << "set_compression_gain_db() failed.";
    return -1;
  }
  const bool enable_limiter = !disable_digital_adaptive;
  if (gain_control->enable_limiter(enable_limiter) != 0) {
    RTC_LOG(LS_ERROR) << "enable_limiter() failed.";
    return -1;
  }
  return 0;
}

}  // namespace

// Facility for dumping debug audio files. All methods are no-ops in the
// default case where WEBRTC_AGC_DEBUG_DUMP is undefined.
class DebugFile {
#ifdef WEBRTC_AGC_DEBUG_DUMP
 public:
  explicit DebugFile(const char* filename) : file_(fopen(filename, "wb")) {
    RTC_DCHECK(file_);
  }
  ~DebugFile() { fclose(file_); }
  void Write(const int16_t* data, size_t length_samples) {
    fwrite(data, 1, length_samples * sizeof(int16_t), file_);
  }

 private:
  FILE* file_;
#else
 public:
  explicit DebugFile(const char* filename) {}
  ~DebugFile() {}
  void Write(const int16_t* data, size_t length_samples) {}
#endif  // WEBRTC_AGC_DEBUG_DUMP
};

AgcManagerDirect::AgcManagerDirect(GainControl* gctrl,
                                   VolumeCallbacks* volume_callbacks,
                                   int startup_min_level,
                                   int clipped_level_min,
                                   bool use_agc2_level_estimation,
                                   bool disable_digital_adaptive)
    : AgcManagerDirect(use_agc2_level_estimation ? nullptr : new Agc(),
                       gctrl,
                       volume_callbacks,
                       startup_min_level,
                       clipped_level_min,
                       use_agc2_level_estimation,
                       disable_digital_adaptive) {
  RTC_DCHECK(agc_);
}

AgcManagerDirect::AgcManagerDirect(Agc* agc,
                                   GainControl* gctrl,
                                   VolumeCallbacks* volume_callbacks,
                                   int startup_min_level,
                                   int clipped_level_min)
    : AgcManagerDirect(agc,
                       gctrl,
                       volume_callbacks,
                       startup_min_level,
                       clipped_level_min,
                       false,
                       false) {
  RTC_DCHECK(agc_);
}

AgcManagerDirect::AgcManagerDirect(Agc* agc,
                                   GainControl* gctrl,
                                   VolumeCallbacks* volume_callbacks,
                                   int startup_min_level,
                                   int clipped_level_min,
                                   bool use_agc2_level_estimation,
                                   bool disable_digital_adaptive)
    : data_dumper_(new ApmDataDumper(instance_counter_)),
      agc_(agc),
      gctrl_(gctrl),
      volume_callbacks_(volume_callbacks),
      frames_since_clipped_(kClippedWaitFrames),
      level_(0),
      max_level_(kMaxMicLevel),
      max_compression_gain_(kMaxCompressionGain),
      target_compression_(kDefaultCompressionGain),
      compression_(target_compression_),
      compression_accumulator_(compression_),
      capture_muted_(false),
      check_volume_on_next_process_(true),  // Check at startup.
      startup_(true),
      use_agc2_level_estimation_(use_agc2_level_estimation),
      disable_digital_adaptive_(disable_digital_adaptive),
      startup_min_level_(ClampLevel(startup_min_level)),
      clipped_level_min_(clipped_level_min),
      file_preproc_(new DebugFile("agc_preproc.pcm")),
      file_postproc_(new DebugFile("agc_postproc.pcm")) {
  instance_counter_++;
  if (use_agc2_level_estimation_) {
    RTC_DCHECK(!agc);
    agc_.reset(new AdaptiveModeLevelEstimatorAgc(data_dumper_.get()));
  } else {
    RTC_DCHECK(agc);
  }
}

AgcManagerDirect::~AgcManagerDirect() {}

int AgcManagerDirect::Initialize() {
  max_level_ = kMaxMicLevel;
  max_compression_gain_ = kMaxCompressionGain;
  target_compression_ = disable_digital_adaptive_ ? 0 : kDefaultCompressionGain;
  compression_ = disable_digital_adaptive_ ? 0 : target_compression_;
  compression_accumulator_ = compression_;
  capture_muted_ = false;
  check_volume_on_next_process_ = true;
  // TODO(bjornv): Investigate if we need to reset |startup_| as well. For
  // example, what happens when we change devices.

  data_dumper_->InitiateNewSetOfRecordings();

  return InitializeGainControl(gctrl_, disable_digital_adaptive_);
}

void AgcManagerDirect::AnalyzePreProcess(int16_t* audio,
                                         int num_channels,
                                         size_t samples_per_channel) {
  size_t length = num_channels * samples_per_channel;
  if (capture_muted_) {
    return;
  }

  file_preproc_->Write(audio, length);

  if (frames_since_clipped_ < kClippedWaitFrames) {
    ++frames_since_clipped_;
    return;
  }

  // Check for clipped samples, as the AGC has difficulty detecting pitch
  // under clipping distortion. We do this in the preprocessing phase in order
  // to catch clipped echo as well.
  //
  // If we find a sufficiently clipped frame, drop the current microphone level
  // and enforce a new maximum level, dropped the same amount from the current
  // maximum. This harsh treatment is an effort to avoid repeated clipped echo
  // events. As compensation for this restriction, the maximum compression
  // gain is increased, through SetMaxLevel().
  float clipped_ratio = agc_->AnalyzePreproc(audio, length);
  if (clipped_ratio > kClippedRatioThreshold) {
    RTC_DLOG(LS_INFO) << "[agc] Clipping detected. clipped_ratio="
                      << clipped_ratio;
    // Always decrease the maximum level, even if the current level is below
    // threshold.
    SetMaxLevel(std::max(clipped_level_min_, max_level_ - kClippedLevelStep));
    RTC_HISTOGRAM_BOOLEAN("WebRTC.Audio.AgcClippingAdjustmentAllowed",
                          level_ - kClippedLevelStep >= clipped_level_min_);
    if (level_ > clipped_level_min_) {
      // Don't try to adjust the level if we're already below the limit. As
      // a consequence, if the user has brought the level above the limit, we
      // will still not react until the postproc updates the level.
      SetLevel(std::max(clipped_level_min_, level_ - kClippedLevelStep));
      // Reset the AGC since the level has changed.
      agc_->Reset();
    }
    frames_since_clipped_ = 0;
  }
}

void AgcManagerDirect::Process(const int16_t* audio,
                               size_t length,
                               int sample_rate_hz) {
  if (capture_muted_) {
    return;
  }

  if (check_volume_on_next_process_) {
    check_volume_on_next_process_ = false;
    // We have to wait until the first process call to check the volume,
    // because Chromium doesn't guarantee it to be valid any earlier.
    CheckVolumeAndReset();
  }

  agc_->Process(audio, length, sample_rate_hz);

  UpdateGain();
  if (!disable_digital_adaptive_) {
    UpdateCompressor();
  }

  file_postproc_->Write(audio, length);

  data_dumper_->DumpRaw("experimental_gain_control_compression_gain_db", 1,
                        &compression_);
}

void AgcManagerDirect::SetLevel(int new_level) {
  int voe_level = volume_callbacks_->GetMicVolume();
  if (voe_level == 0) {
    RTC_DLOG(LS_INFO)
        << "[agc] VolumeCallbacks returned level=0, taking no action.";
    return;
  }
  if (voe_level < 0 || voe_level > kMaxMicLevel) {
    RTC_LOG(LS_ERROR) << "VolumeCallbacks returned an invalid level="
                      << voe_level;
    return;
  }

  if (voe_level > level_ + kLevelQuantizationSlack ||
      voe_level < level_ - kLevelQuantizationSlack) {
    RTC_DLOG(LS_INFO) << "[agc] Mic volume was manually adjusted. Updating "
                         "stored level from "
                      << level_ << " to " << voe_level;
    level_ = voe_level;
    // Always allow the user to increase the volume.
    if (level_ > max_level_) {
      SetMaxLevel(level_);
    }
    // Take no action in this case, since we can't be sure when the volume
    // was manually adjusted. The compressor will still provide some of the
    // desired gain change.
    agc_->Reset();
    return;
  }

  new_level = std::min(new_level, max_level_);
  if (new_level == level_) {
    return;
  }

  volume_callbacks_->SetMicVolume(new_level);
  RTC_DLOG(LS_INFO) << "[agc] voe_level=" << voe_level << ", "
                    << "level_=" << level_ << ", "
                    << "new_level=" << new_level;
  level_ = new_level;
}

void AgcManagerDirect::SetMaxLevel(int level) {
  RTC_DCHECK_GE(level, clipped_level_min_);
  max_level_ = level;
  // Scale the |kSurplusCompressionGain| linearly across the restricted
  // level range.
  max_compression_gain_ =
      kMaxCompressionGain + std::floor((1.f * kMaxMicLevel - max_level_) /
                                           (kMaxMicLevel - clipped_level_min_) *
                                           kSurplusCompressionGain +
                                       0.5f);
  RTC_DLOG(LS_INFO) << "[agc] max_level_=" << max_level_
                    << ", max_compression_gain_=" << max_compression_gain_;
}

void AgcManagerDirect::SetCaptureMuted(bool muted) {
  if (capture_muted_ == muted) {
    return;
  }
  capture_muted_ = muted;

  if (!muted) {
    // When we unmute, we should reset things to be safe.
    check_volume_on_next_process_ = true;
  }
}

float AgcManagerDirect::voice_probability() {
  return agc_->voice_probability();
}

int AgcManagerDirect::CheckVolumeAndReset() {
  int level = volume_callbacks_->GetMicVolume();
  // Reasons for taking action at startup:
  // 1) A person starting a call is expected to be heard.
  // 2) Independent of interpretation of |level| == 0 we should raise it so the
  // AGC can do its job properly.
  if (level == 0 && !startup_) {
    RTC_DLOG(LS_INFO)
        << "[agc] VolumeCallbacks returned level=0, taking no action.";
    return 0;
  }
  if (level < 0 || level > kMaxMicLevel) {
    RTC_LOG(LS_ERROR) << "[agc] VolumeCallbacks returned an invalid level="
                      << level;
    return -1;
  }
  RTC_DLOG(LS_INFO) << "[agc] Initial GetMicVolume()=" << level;

  int minLevel = startup_ ? startup_min_level_ : kMinMicLevel;
  if (level < minLevel) {
    level = minLevel;
    RTC_DLOG(LS_INFO) << "[agc] Initial volume too low, raising to " << level;
    volume_callbacks_->SetMicVolume(level);
  }
  agc_->Reset();
  level_ = level;
  startup_ = false;
  return 0;
}

// Requests the RMS error from AGC and distributes the required gain change
// between the digital compression stage and volume slider. We use the
// compressor first, providing a slack region around the current slider
// position to reduce movement.
//
// If the slider needs to be moved, we check first if the user has adjusted
// it, in which case we take no action and cache the updated level.
void AgcManagerDirect::UpdateGain() {
  int rms_error = 0;
  if (!agc_->GetRmsErrorDb(&rms_error)) {
    // No error update ready.
    return;
  }
  // The compressor will always add at least kMinCompressionGain. In effect,
  // this adjusts our target gain upward by the same amount and rms_error
  // needs to reflect that.
  rms_error += kMinCompressionGain;

  // Handle as much error as possible with the compressor first.
  int raw_compression =
      rtc::SafeClamp(rms_error, kMinCompressionGain, max_compression_gain_);

  // Deemphasize the compression gain error. Move halfway between the current
  // target and the newly received target. This serves to soften perceptible
  // intra-talkspurt adjustments, at the cost of some adaptation speed.
  if ((raw_compression == max_compression_gain_ &&
       target_compression_ == max_compression_gain_ - 1) ||
      (raw_compression == kMinCompressionGain &&
       target_compression_ == kMinCompressionGain + 1)) {
    // Special case to allow the target to reach the endpoints of the
    // compression range. The deemphasis would otherwise halt it at 1 dB shy.
    target_compression_ = raw_compression;
  } else {
    target_compression_ =
        (raw_compression - target_compression_) / 2 + target_compression_;
  }

  // Residual error will be handled by adjusting the volume slider. Use the
  // raw rather than deemphasized compression here as we would otherwise
  // shrink the amount of slack the compressor provides.
  const int residual_gain =
      rtc::SafeClamp(rms_error - raw_compression, -kMaxResidualGainChange,
                     kMaxResidualGainChange);
  RTC_DLOG(LS_INFO) << "[agc] rms_error=" << rms_error
                    << ", target_compression=" << target_compression_
                    << ", residual_gain=" << residual_gain;
  if (residual_gain == 0)
    return;

  int old_level = level_;
  SetLevel(LevelFromGainError(residual_gain, level_));
  if (old_level != level_) {
    // level_ was updated by SetLevel; log the new value.
    RTC_HISTOGRAM_COUNTS_LINEAR("WebRTC.Audio.AgcSetLevel", level_, 1,
                                kMaxMicLevel, 50);
    // Reset the AGC since the level has changed.
    agc_->Reset();
  }
}

void AgcManagerDirect::UpdateCompressor() {
  calls_since_last_gain_log_++;
  if (calls_since_last_gain_log_ == 100) {
    calls_since_last_gain_log_ = 0;
    RTC_HISTOGRAM_COUNTS_LINEAR("WebRTC.Audio.Agc.DigitalGainApplied",
                                compression_, 0, kMaxCompressionGain,
                                kMaxCompressionGain + 1);
  }
  if (compression_ == target_compression_) {
    return;
  }

  // Adapt the compression gain slowly towards the target, in order to avoid
  // highly perceptible changes.
  if (target_compression_ > compression_) {
    compression_accumulator_ += kCompressionGainStep;
  } else {
    compression_accumulator_ -= kCompressionGainStep;
  }

  // The compressor accepts integer gains in dB. Adjust the gain when
  // we've come within half a stepsize of the nearest integer.  (We don't
  // check for equality due to potential floating point imprecision).
  int new_compression = compression_;
  int nearest_neighbor = std::floor(compression_accumulator_ + 0.5);
  if (std::fabs(compression_accumulator_ - nearest_neighbor) <
      kCompressionGainStep / 2) {
    new_compression = nearest_neighbor;
  }

  // Set the new compression gain.
  if (new_compression != compression_) {
    RTC_HISTOGRAM_COUNTS_LINEAR("WebRTC.Audio.Agc.DigitalGainUpdated",
                                new_compression, 0, kMaxCompressionGain,
                                kMaxCompressionGain + 1);
    compression_ = new_compression;
    compression_accumulator_ = new_compression;
    if (gctrl_->set_compression_gain_db(compression_) != 0) {
      RTC_LOG(LS_ERROR) << "set_compression_gain_db(" << compression_
                        << ") failed.";
    }
  }
}

}  // namespace webrtc
