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

#include "common_audio/include/audio_util.h"
#include "modules/audio_processing/agc/gain_control.h"
#include "modules/audio_processing/agc/gain_map_internal.h"
#include "modules/audio_processing/include/audio_frame_view.h"
#include "rtc_base/atomic_ops.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_minmax.h"
#include "system_wrappers/include/field_trial.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {

namespace {

// Amount of error we tolerate in the microphone level (presumably due to OS
// quantization) before we assume the user has manually adjusted the microphone.
constexpr int kLevelQuantizationSlack = 25;

constexpr int kDefaultCompressionGain = 7;
constexpr int kMaxCompressionGain = 12;
constexpr int kMinCompressionGain = 2;
// Controls the rate of compression changes towards the target.
constexpr float kCompressionGainStep = 0.05f;

constexpr int kMaxMicLevel = 255;
static_assert(kGainMapSize > kMaxMicLevel, "gain map too small");
constexpr int kMinMicLevel = 12;

// Prevent very large microphone level changes.
constexpr int kMaxResidualGainChange = 15;

// Maximum additional gain allowed to compensate for microphone level
// restrictions from clipping events.
constexpr int kSurplusCompressionGain = 6;

// History size for the clipping predictor evaluator (unit: number of 10 ms
// frames).
constexpr int kClippingPredictorEvaluatorHistorySize = 32;

using ClippingPredictorConfig = AudioProcessing::Config::GainController1::
    AnalogGainController::ClippingPredictor;

// Returns whether a fall-back solution to choose the maximum level should be
// chosen.
bool UseMaxAnalogChannelLevel() {
  return field_trial::IsEnabled("WebRTC-UseMaxAnalogAgcChannelLevel");
}

// Returns kMinMicLevel if no field trial exists or if it has been disabled.
// Returns a value between 0 and 255 depending on the field-trial string.
// Example: 'WebRTC-Audio-AgcMinMicLevelExperiment/Enabled-80' => returns 80.
int GetMinMicLevel() {
  RTC_LOG(LS_INFO) << "[agc] GetMinMicLevel";
  constexpr char kMinMicLevelFieldTrial[] =
      "WebRTC-Audio-AgcMinMicLevelExperiment";
  if (!webrtc::field_trial::IsEnabled(kMinMicLevelFieldTrial)) {
    RTC_LOG(LS_INFO) << "[agc] Using default min mic level: " << kMinMicLevel;
    return kMinMicLevel;
  }
  const auto field_trial_string =
      webrtc::field_trial::FindFullName(kMinMicLevelFieldTrial);
  int min_mic_level = -1;
  sscanf(field_trial_string.c_str(), "Enabled-%d", &min_mic_level);
  if (min_mic_level >= 0 && min_mic_level <= 255) {
    RTC_LOG(LS_INFO) << "[agc] Experimental min mic level: " << min_mic_level;
    return min_mic_level;
  } else {
    RTC_LOG(LS_WARNING) << "[agc] Invalid parameter for "
                        << kMinMicLevelFieldTrial << ", ignored.";
    return kMinMicLevel;
  }
}

int ClampLevel(int mic_level, int min_mic_level) {
  return rtc::SafeClamp(mic_level, min_mic_level, kMaxMicLevel);
}

int LevelFromGainError(int gain_error, int level, int min_mic_level) {
  RTC_DCHECK_GE(level, 0);
  RTC_DCHECK_LE(level, kMaxMicLevel);
  if (gain_error == 0) {
    return level;
  }

  int new_level = level;
  if (gain_error > 0) {
    while (kGainMap[new_level] - kGainMap[level] < gain_error &&
           new_level < kMaxMicLevel) {
      ++new_level;
    }
  } else {
    while (kGainMap[new_level] - kGainMap[level] > gain_error &&
           new_level > min_mic_level) {
      --new_level;
    }
  }
  return new_level;
}

// Returns the proportion of samples in the buffer which are at full-scale
// (and presumably clipped).
float ComputeClippedRatio(const float* const* audio,
                          size_t num_channels,
                          size_t samples_per_channel) {
  RTC_DCHECK_GT(samples_per_channel, 0);
  int num_clipped = 0;
  for (size_t ch = 0; ch < num_channels; ++ch) {
    int num_clipped_in_ch = 0;
    for (size_t i = 0; i < samples_per_channel; ++i) {
      RTC_DCHECK(audio[ch]);
      if (audio[ch][i] >= 32767.f || audio[ch][i] <= -32768.f) {
        ++num_clipped_in_ch;
      }
    }
    num_clipped = std::max(num_clipped, num_clipped_in_ch);
  }
  return static_cast<float>(num_clipped) / (samples_per_channel);
}

void LogClippingPredictorMetrics(const ClippingPredictorEvaluator& evaluator) {
  absl::optional<ClippingPredictionMetrics> metrics =
      ComputeClippingPredictionMetrics(evaluator.counters());
  if (metrics.has_value()) {
    RTC_LOG(LS_INFO) << "Clipping predictor metrics: P " << metrics->precision
                     << " R " << metrics->recall << " F1 score "
                     << metrics->f1_score;
    RTC_DCHECK_GE(metrics->f1_score, 0.0f);
    RTC_DCHECK_LE(metrics->f1_score, 1.0f);
    RTC_DCHECK_GE(metrics->precision, 0.0f);
    RTC_DCHECK_LE(metrics->precision, 1.0f);
    RTC_DCHECK_GE(metrics->recall, 0.0f);
    RTC_DCHECK_LE(metrics->recall, 1.0f);
    RTC_HISTOGRAM_COUNTS_LINEAR(
        /*name=*/"WebRTC.Audio.Agc.ClippingPredictor.F1Score",
        /*sample=*/std::round(metrics->f1_score * 100.0f),
        /*min=*/0,
        /*max=*/100,
        /*bucket_count=*/50);
    RTC_HISTOGRAM_COUNTS_LINEAR(
        /*name=*/"WebRTC.Audio.Agc.ClippingPredictor.Precision",
        /*sample=*/std::round(metrics->precision * 100.0f),
        /*min=*/0,
        /*max=*/100,
        /*bucket_count=*/50);
    RTC_HISTOGRAM_COUNTS_LINEAR(
        /*name=*/"WebRTC.Audio.Agc.ClippingPredictor.Recall",
        /*sample=*/std::round(metrics->recall * 100.0f),
        /*min=*/0,
        /*max=*/100,
        /*bucket_count=*/50);
  }
}

void LogClippingMetrics(int clipping_rate) {
  RTC_LOG(LS_INFO) << "Input clipping rate: " << clipping_rate << "%";
  RTC_HISTOGRAM_COUNTS_LINEAR(/*name=*/"WebRTC.Audio.Agc.InputClippingRate",
                              /*sample=*/clipping_rate, /*min=*/0, /*max=*/100,
                              /*bucket_count=*/50);
}

}  // namespace

MonoAgc::MonoAgc(ApmDataDumper* data_dumper,
                 int startup_min_level,
                 int clipped_level_min,
                 bool disable_digital_adaptive,
                 int min_mic_level)
    : min_mic_level_(min_mic_level),
      disable_digital_adaptive_(disable_digital_adaptive),
      agc_(std::make_unique<Agc>()),
      max_level_(kMaxMicLevel),
      max_compression_gain_(kMaxCompressionGain),
      target_compression_(kDefaultCompressionGain),
      compression_(target_compression_),
      compression_accumulator_(compression_),
      startup_min_level_(ClampLevel(startup_min_level, min_mic_level_)),
      clipped_level_min_(clipped_level_min) {}

MonoAgc::~MonoAgc() = default;

void MonoAgc::Initialize() {
  max_level_ = kMaxMicLevel;
  max_compression_gain_ = kMaxCompressionGain;
  target_compression_ = disable_digital_adaptive_ ? 0 : kDefaultCompressionGain;
  compression_ = disable_digital_adaptive_ ? 0 : target_compression_;
  compression_accumulator_ = compression_;
  capture_output_used_ = true;
  check_volume_on_next_process_ = true;
}

void MonoAgc::Process(const int16_t* audio,
                      size_t samples_per_channel,
                      int sample_rate_hz) {
  new_compression_to_set_ = absl::nullopt;

  if (check_volume_on_next_process_) {
    check_volume_on_next_process_ = false;
    // We have to wait until the first process call to check the volume,
    // because Chromium doesn't guarantee it to be valid any earlier.
    CheckVolumeAndReset();
  }

  agc_->Process(audio, samples_per_channel, sample_rate_hz);

  UpdateGain();
  if (!disable_digital_adaptive_) {
    UpdateCompressor();
  }
}

void MonoAgc::HandleClipping(int clipped_level_step) {
  // Always decrease the maximum level, even if the current level is below
  // threshold.
  SetMaxLevel(std::max(clipped_level_min_, max_level_ - clipped_level_step));
  if (log_to_histograms_) {
    RTC_HISTOGRAM_BOOLEAN("WebRTC.Audio.AgcClippingAdjustmentAllowed",
                          level_ - clipped_level_step >= clipped_level_min_);
  }
  if (level_ > clipped_level_min_) {
    // Don't try to adjust the level if we're already below the limit. As
    // a consequence, if the user has brought the level above the limit, we
    // will still not react until the postproc updates the level.
    SetLevel(std::max(clipped_level_min_, level_ - clipped_level_step));
    // Reset the AGCs for all channels since the level has changed.
    agc_->Reset();
  }
}

void MonoAgc::SetLevel(int new_level) {
  int voe_level = stream_analog_level_;
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

  stream_analog_level_ = new_level;
  RTC_DLOG(LS_INFO) << "[agc] voe_level=" << voe_level << ", level_=" << level_
                    << ", new_level=" << new_level;
  level_ = new_level;
}

void MonoAgc::SetMaxLevel(int level) {
  RTC_DCHECK_GE(level, clipped_level_min_);
  max_level_ = level;
  // Scale the `kSurplusCompressionGain` linearly across the restricted
  // level range.
  max_compression_gain_ =
      kMaxCompressionGain + std::floor((1.f * kMaxMicLevel - max_level_) /
                                           (kMaxMicLevel - clipped_level_min_) *
                                           kSurplusCompressionGain +
                                       0.5f);
  RTC_DLOG(LS_INFO) << "[agc] max_level_=" << max_level_
                    << ", max_compression_gain_=" << max_compression_gain_;
}

void MonoAgc::HandleCaptureOutputUsedChange(bool capture_output_used) {
  if (capture_output_used_ == capture_output_used) {
    return;
  }
  capture_output_used_ = capture_output_used;

  if (capture_output_used) {
    // When we start using the output, we should reset things to be safe.
    check_volume_on_next_process_ = true;
  }
}

int MonoAgc::CheckVolumeAndReset() {
  int level = stream_analog_level_;
  // Reasons for taking action at startup:
  // 1) A person starting a call is expected to be heard.
  // 2) Independent of interpretation of `level` == 0 we should raise it so the
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

  int minLevel = startup_ ? startup_min_level_ : min_mic_level_;
  if (level < minLevel) {
    level = minLevel;
    RTC_DLOG(LS_INFO) << "[agc] Initial volume too low, raising to " << level;
    stream_analog_level_ = level;
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
void MonoAgc::UpdateGain() {
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
  SetLevel(LevelFromGainError(residual_gain, level_, min_mic_level_));
  if (old_level != level_) {
    // level_ was updated by SetLevel; log the new value.
    RTC_HISTOGRAM_COUNTS_LINEAR("WebRTC.Audio.AgcSetLevel", level_, 1,
                                kMaxMicLevel, 50);
    // Reset the AGC since the level has changed.
    agc_->Reset();
  }
}

void MonoAgc::UpdateCompressor() {
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
    new_compression_to_set_ = compression_;
  }
}

int AgcManagerDirect::instance_counter_ = 0;

AgcManagerDirect::AgcManagerDirect(
    Agc* agc,
    int startup_min_level,
    int clipped_level_min,
    int sample_rate_hz,
    int clipped_level_step,
    float clipped_ratio_threshold,
    int clipped_wait_frames,
    const ClippingPredictorConfig& clipping_config)
    : AgcManagerDirect(/*num_capture_channels*/ 1,
                       startup_min_level,
                       clipped_level_min,
                       /*disable_digital_adaptive*/ false,
                       sample_rate_hz,
                       clipped_level_step,
                       clipped_ratio_threshold,
                       clipped_wait_frames,
                       clipping_config) {
  RTC_DCHECK(channel_agcs_[0]);
  RTC_DCHECK(agc);
  channel_agcs_[0]->set_agc(agc);
}

AgcManagerDirect::AgcManagerDirect(
    int num_capture_channels,
    int startup_min_level,
    int clipped_level_min,
    bool disable_digital_adaptive,
    int sample_rate_hz,
    int clipped_level_step,
    float clipped_ratio_threshold,
    int clipped_wait_frames,
    const ClippingPredictorConfig& clipping_config)
    : data_dumper_(
          new ApmDataDumper(rtc::AtomicOps::Increment(&instance_counter_))),
      use_min_channel_level_(!UseMaxAnalogChannelLevel()),
      sample_rate_hz_(sample_rate_hz),
      num_capture_channels_(num_capture_channels),
      disable_digital_adaptive_(disable_digital_adaptive),
      frames_since_clipped_(clipped_wait_frames),
      capture_output_used_(true),
      clipped_level_step_(clipped_level_step),
      clipped_ratio_threshold_(clipped_ratio_threshold),
      clipped_wait_frames_(clipped_wait_frames),
      channel_agcs_(num_capture_channels),
      new_compressions_to_set_(num_capture_channels),
      clipping_predictor_(
          CreateClippingPredictor(num_capture_channels, clipping_config)),
      use_clipping_predictor_step_(!!clipping_predictor_ &&
                                   clipping_config.use_predicted_step),
      clipping_predictor_evaluator_(kClippingPredictorEvaluatorHistorySize),
      clipping_predictor_log_counter_(0),
      clipping_rate_log_(0.0f),
      clipping_rate_log_counter_(0) {
  const int min_mic_level = GetMinMicLevel();
  for (size_t ch = 0; ch < channel_agcs_.size(); ++ch) {
    ApmDataDumper* data_dumper_ch = ch == 0 ? data_dumper_.get() : nullptr;

    channel_agcs_[ch] = std::make_unique<MonoAgc>(
        data_dumper_ch, startup_min_level, clipped_level_min,
        disable_digital_adaptive_, min_mic_level);
  }
  RTC_DCHECK(!channel_agcs_.empty());
  RTC_DCHECK_GT(clipped_level_step, 0);
  RTC_DCHECK_LE(clipped_level_step, 255);
  RTC_DCHECK_GT(clipped_ratio_threshold, 0.f);
  RTC_DCHECK_LT(clipped_ratio_threshold, 1.f);
  RTC_DCHECK_GT(clipped_wait_frames, 0);
  channel_agcs_[0]->ActivateLogging();
}

AgcManagerDirect::~AgcManagerDirect() {}

void AgcManagerDirect::Initialize() {
  RTC_DLOG(LS_INFO) << "AgcManagerDirect::Initialize";
  data_dumper_->InitiateNewSetOfRecordings();
  for (size_t ch = 0; ch < channel_agcs_.size(); ++ch) {
    channel_agcs_[ch]->Initialize();
  }
  capture_output_used_ = true;

  AggregateChannelLevels();
  clipping_predictor_evaluator_.Reset();
  clipping_predictor_log_counter_ = 0;
  clipping_rate_log_ = 0.0f;
  clipping_rate_log_counter_ = 0;
}

void AgcManagerDirect::SetupDigitalGainControl(
    GainControl* gain_control) const {
  RTC_DCHECK(gain_control);
  if (gain_control->set_mode(GainControl::kFixedDigital) != 0) {
    RTC_LOG(LS_ERROR) << "set_mode(GainControl::kFixedDigital) failed.";
  }
  const int target_level_dbfs = disable_digital_adaptive_ ? 0 : 2;
  if (gain_control->set_target_level_dbfs(target_level_dbfs) != 0) {
    RTC_LOG(LS_ERROR) << "set_target_level_dbfs() failed.";
  }
  const int compression_gain_db =
      disable_digital_adaptive_ ? 0 : kDefaultCompressionGain;
  if (gain_control->set_compression_gain_db(compression_gain_db) != 0) {
    RTC_LOG(LS_ERROR) << "set_compression_gain_db() failed.";
  }
  const bool enable_limiter = !disable_digital_adaptive_;
  if (gain_control->enable_limiter(enable_limiter) != 0) {
    RTC_LOG(LS_ERROR) << "enable_limiter() failed.";
  }
}

void AgcManagerDirect::AnalyzePreProcess(const AudioBuffer* audio) {
  RTC_DCHECK(audio);
  AnalyzePreProcess(audio->channels_const(), audio->num_frames());
}

void AgcManagerDirect::AnalyzePreProcess(const float* const* audio,
                                         size_t samples_per_channel) {
  RTC_DCHECK(audio);
  AggregateChannelLevels();
  if (!capture_output_used_) {
    return;
  }

  if (!!clipping_predictor_) {
    AudioFrameView<const float> frame = AudioFrameView<const float>(
        audio, num_capture_channels_, static_cast<int>(samples_per_channel));
    clipping_predictor_->Analyze(frame);
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
  float clipped_ratio =
      ComputeClippedRatio(audio, num_capture_channels_, samples_per_channel);
  clipping_rate_log_ = std::max(clipped_ratio, clipping_rate_log_);
  clipping_rate_log_counter_++;
  constexpr int kNumFramesIn30Seconds = 3000;
  if (clipping_rate_log_counter_ == kNumFramesIn30Seconds) {
    LogClippingMetrics(std::round(100.0f * clipping_rate_log_));
    clipping_rate_log_ = 0.0f;
    clipping_rate_log_counter_ = 0;
  }

  if (frames_since_clipped_ < clipped_wait_frames_) {
    ++frames_since_clipped_;
    return;
  }

  const bool clipping_detected = clipped_ratio > clipped_ratio_threshold_;
  bool clipping_predicted = false;
  int predicted_step = 0;
  if (!!clipping_predictor_) {
    for (int channel = 0; channel < num_capture_channels_; ++channel) {
      const auto step = clipping_predictor_->EstimateClippedLevelStep(
          channel, stream_analog_level_, clipped_level_step_,
          channel_agcs_[channel]->min_mic_level(), kMaxMicLevel);
      if (step.has_value()) {
        predicted_step = std::max(predicted_step, step.value());
        clipping_predicted = true;
      }
    }
    // Clipping prediction evaluation.
    absl::optional<int> prediction_interval =
        clipping_predictor_evaluator_.Observe(clipping_detected,
                                              clipping_predicted);
    if (prediction_interval.has_value()) {
      RTC_HISTOGRAM_COUNTS_LINEAR(
          "WebRTC.Audio.Agc.ClippingPredictor.PredictionInterval",
          prediction_interval.value(), /*min=*/0,
          /*max=*/49, /*bucket_count=*/50);
    }
    clipping_predictor_log_counter_++;
    if (clipping_predictor_log_counter_ == kNumFramesIn30Seconds) {
      LogClippingPredictorMetrics(clipping_predictor_evaluator_);
      clipping_predictor_log_counter_ = 0;
    }
  }
  if (clipping_detected) {
    RTC_DLOG(LS_INFO) << "[agc] Clipping detected. clipped_ratio="
                      << clipped_ratio;
  }
  int step = clipped_level_step_;
  if (clipping_predicted) {
    predicted_step = std::max(predicted_step, clipped_level_step_);
    RTC_DLOG(LS_INFO) << "[agc] Clipping predicted. step=" << predicted_step;
    if (use_clipping_predictor_step_) {
      step = predicted_step;
    }
  }
  if (clipping_detected ||
      (clipping_predicted && use_clipping_predictor_step_)) {
    for (auto& state_ch : channel_agcs_) {
      state_ch->HandleClipping(step);
    }
    frames_since_clipped_ = 0;
    if (!!clipping_predictor_) {
      clipping_predictor_->Reset();
      clipping_predictor_evaluator_.RemoveExpectations();
    }
  }
  AggregateChannelLevels();
}

void AgcManagerDirect::Process(const AudioBuffer* audio) {
  AggregateChannelLevels();

  if (!capture_output_used_) {
    return;
  }

  for (size_t ch = 0; ch < channel_agcs_.size(); ++ch) {
    int16_t* audio_use = nullptr;
    std::array<int16_t, AudioBuffer::kMaxSampleRate / 100> audio_data;
    int num_frames_per_band;
    if (audio) {
      FloatS16ToS16(audio->split_bands_const_f(ch)[0],
                    audio->num_frames_per_band(), audio_data.data());
      audio_use = audio_data.data();
      num_frames_per_band = audio->num_frames_per_band();
    } else {
      // Only used for testing.
      // TODO(peah): Change unittests to only allow on non-null audio input.
      num_frames_per_band = 320;
    }
    channel_agcs_[ch]->Process(audio_use, num_frames_per_band, sample_rate_hz_);
    new_compressions_to_set_[ch] = channel_agcs_[ch]->new_compression();
  }

  AggregateChannelLevels();
}

absl::optional<int> AgcManagerDirect::GetDigitalComressionGain() {
  return new_compressions_to_set_[channel_controlling_gain_];
}

void AgcManagerDirect::HandleCaptureOutputUsedChange(bool capture_output_used) {
  for (size_t ch = 0; ch < channel_agcs_.size(); ++ch) {
    channel_agcs_[ch]->HandleCaptureOutputUsedChange(capture_output_used);
  }
  capture_output_used_ = capture_output_used;
}

float AgcManagerDirect::voice_probability() const {
  float max_prob = 0.f;
  for (const auto& state_ch : channel_agcs_) {
    max_prob = std::max(max_prob, state_ch->voice_probability());
  }

  return max_prob;
}

void AgcManagerDirect::set_stream_analog_level(int level) {
  for (size_t ch = 0; ch < channel_agcs_.size(); ++ch) {
    channel_agcs_[ch]->set_stream_analog_level(level);
  }

  AggregateChannelLevels();
}

void AgcManagerDirect::AggregateChannelLevels() {
  stream_analog_level_ = channel_agcs_[0]->stream_analog_level();
  channel_controlling_gain_ = 0;
  if (use_min_channel_level_) {
    for (size_t ch = 1; ch < channel_agcs_.size(); ++ch) {
      int level = channel_agcs_[ch]->stream_analog_level();
      if (level < stream_analog_level_) {
        stream_analog_level_ = level;
        channel_controlling_gain_ = static_cast<int>(ch);
      }
    }
  } else {
    for (size_t ch = 1; ch < channel_agcs_.size(); ++ch) {
      int level = channel_agcs_[ch]->stream_analog_level();
      if (level > stream_analog_level_) {
        stream_analog_level_ = level;
        channel_controlling_gain_ = static_cast<int>(ch);
      }
    }
  }
}

}  // namespace webrtc
