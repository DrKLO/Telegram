/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/audio_processing/aec3/echo_canceller3.h"

#include <algorithm>
#include <utility>

#include "modules/audio_processing/aec3/aec3_common.h"
#include "modules/audio_processing/high_pass_filter.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/atomic_ops.h"
#include "rtc_base/experiments/field_trial_parser.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {

namespace {

enum class EchoCanceller3ApiCall { kCapture, kRender };

bool DetectSaturation(rtc::ArrayView<const float> y) {
  for (size_t k = 0; k < y.size(); ++k) {
    if (y[k] >= 32700.0f || y[k] <= -32700.0f) {
      return true;
    }
  }
  return false;
}

// Retrieves a value from a field trial if it is available. If no value is
// present, the default value is returned. If the retrieved value is beyond the
// specified limits, the default value is returned instead.
void RetrieveFieldTrialValue(const char* trial_name,
                             float min,
                             float max,
                             float* value_to_update) {
  const std::string field_trial_str = field_trial::FindFullName(trial_name);

  FieldTrialParameter<double> field_trial_param(/*key=*/"", *value_to_update);

  ParseFieldTrial({&field_trial_param}, field_trial_str);
  float field_trial_value = static_cast<float>(field_trial_param.Get());

  if (field_trial_value >= min && field_trial_value <= max &&
      field_trial_value != *value_to_update) {
    RTC_LOG(LS_INFO) << "Key " << trial_name
                     << " changing AEC3 parameter value from "
                     << *value_to_update << " to " << field_trial_value;
    *value_to_update = field_trial_value;
  }
}

void RetrieveFieldTrialValue(const char* trial_name,
                             int min,
                             int max,
                             int* value_to_update) {
  const std::string field_trial_str = field_trial::FindFullName(trial_name);

  FieldTrialParameter<int> field_trial_param(/*key=*/"", *value_to_update);

  ParseFieldTrial({&field_trial_param}, field_trial_str);
  float field_trial_value = field_trial_param.Get();

  if (field_trial_value >= min && field_trial_value <= max &&
      field_trial_value != *value_to_update) {
    RTC_LOG(LS_INFO) << "Key " << trial_name
                     << " changing AEC3 parameter value from "
                     << *value_to_update << " to " << field_trial_value;
    *value_to_update = field_trial_value;
  }
}

void FillSubFrameView(
    AudioBuffer* frame,
    size_t sub_frame_index,
    std::vector<std::vector<rtc::ArrayView<float>>>* sub_frame_view) {
  RTC_DCHECK_GE(1, sub_frame_index);
  RTC_DCHECK_LE(0, sub_frame_index);
  RTC_DCHECK_EQ(frame->num_bands(), sub_frame_view->size());
  RTC_DCHECK_EQ(frame->num_channels(), (*sub_frame_view)[0].size());
  for (size_t band = 0; band < sub_frame_view->size(); ++band) {
    for (size_t channel = 0; channel < (*sub_frame_view)[0].size(); ++channel) {
      (*sub_frame_view)[band][channel] = rtc::ArrayView<float>(
          &frame->split_bands(channel)[band][sub_frame_index * kSubFrameLength],
          kSubFrameLength);
    }
  }
}

void FillSubFrameView(
    std::vector<std::vector<std::vector<float>>>* frame,
    size_t sub_frame_index,
    std::vector<std::vector<rtc::ArrayView<float>>>* sub_frame_view) {
  RTC_DCHECK_GE(1, sub_frame_index);
  RTC_DCHECK_EQ(frame->size(), sub_frame_view->size());
  RTC_DCHECK_EQ((*frame)[0].size(), (*sub_frame_view)[0].size());
  for (size_t band = 0; band < frame->size(); ++band) {
    for (size_t channel = 0; channel < (*frame)[band].size(); ++channel) {
      (*sub_frame_view)[band][channel] = rtc::ArrayView<float>(
          &(*frame)[band][channel][sub_frame_index * kSubFrameLength],
          kSubFrameLength);
    }
  }
}

void ProcessCaptureFrameContent(
    AudioBuffer* linear_output,
    AudioBuffer* capture,
    bool level_change,
    bool saturated_microphone_signal,
    size_t sub_frame_index,
    FrameBlocker* capture_blocker,
    BlockFramer* linear_output_framer,
    BlockFramer* output_framer,
    BlockProcessor* block_processor,
    std::vector<std::vector<std::vector<float>>>* linear_output_block,
    std::vector<std::vector<rtc::ArrayView<float>>>*
        linear_output_sub_frame_view,
    std::vector<std::vector<std::vector<float>>>* capture_block,
    std::vector<std::vector<rtc::ArrayView<float>>>* capture_sub_frame_view) {
  FillSubFrameView(capture, sub_frame_index, capture_sub_frame_view);

  if (linear_output) {
    RTC_DCHECK(linear_output_framer);
    RTC_DCHECK(linear_output_block);
    RTC_DCHECK(linear_output_sub_frame_view);
    FillSubFrameView(linear_output, sub_frame_index,
                     linear_output_sub_frame_view);
  }

  capture_blocker->InsertSubFrameAndExtractBlock(*capture_sub_frame_view,
                                                 capture_block);
  block_processor->ProcessCapture(level_change, saturated_microphone_signal,
                                  linear_output_block, capture_block);
  output_framer->InsertBlockAndExtractSubFrame(*capture_block,
                                               capture_sub_frame_view);

  if (linear_output) {
    RTC_DCHECK(linear_output_framer);
    linear_output_framer->InsertBlockAndExtractSubFrame(
        *linear_output_block, linear_output_sub_frame_view);
  }
}

void ProcessRemainingCaptureFrameContent(
    bool level_change,
    bool saturated_microphone_signal,
    FrameBlocker* capture_blocker,
    BlockFramer* linear_output_framer,
    BlockFramer* output_framer,
    BlockProcessor* block_processor,
    std::vector<std::vector<std::vector<float>>>* linear_output_block,
    std::vector<std::vector<std::vector<float>>>* block) {
  if (!capture_blocker->IsBlockAvailable()) {
    return;
  }

  capture_blocker->ExtractBlock(block);
  block_processor->ProcessCapture(level_change, saturated_microphone_signal,
                                  linear_output_block, block);
  output_framer->InsertBlock(*block);

  if (linear_output_framer) {
    RTC_DCHECK(linear_output_block);
    linear_output_framer->InsertBlock(*linear_output_block);
  }
}

void BufferRenderFrameContent(
    std::vector<std::vector<std::vector<float>>>* render_frame,
    size_t sub_frame_index,
    FrameBlocker* render_blocker,
    BlockProcessor* block_processor,
    std::vector<std::vector<std::vector<float>>>* block,
    std::vector<std::vector<rtc::ArrayView<float>>>* sub_frame_view) {
  FillSubFrameView(render_frame, sub_frame_index, sub_frame_view);
  render_blocker->InsertSubFrameAndExtractBlock(*sub_frame_view, block);
  block_processor->BufferRender(*block);
}

void BufferRemainingRenderFrameContent(
    FrameBlocker* render_blocker,
    BlockProcessor* block_processor,
    std::vector<std::vector<std::vector<float>>>* block) {
  if (!render_blocker->IsBlockAvailable()) {
    return;
  }
  render_blocker->ExtractBlock(block);
  block_processor->BufferRender(*block);
}

void CopyBufferIntoFrame(const AudioBuffer& buffer,
                         size_t num_bands,
                         size_t num_channels,
                         std::vector<std::vector<std::vector<float>>>* frame) {
  RTC_DCHECK_EQ(num_bands, frame->size());
  RTC_DCHECK_EQ(num_channels, (*frame)[0].size());
  RTC_DCHECK_EQ(AudioBuffer::kSplitBandSize, (*frame)[0][0].size());
  for (size_t band = 0; band < num_bands; ++band) {
    for (size_t channel = 0; channel < num_channels; ++channel) {
      rtc::ArrayView<const float> buffer_view(
          &buffer.split_bands_const(channel)[band][0],
          AudioBuffer::kSplitBandSize);
      std::copy(buffer_view.begin(), buffer_view.end(),
                (*frame)[band][channel].begin());
    }
  }
}

}  // namespace

// TODO(webrtc:5298): Move this to a separate file.
EchoCanceller3Config AdjustConfig(const EchoCanceller3Config& config) {
  EchoCanceller3Config adjusted_cfg = config;

  if (field_trial::IsEnabled("WebRTC-Aec3AntiHowlingMinimizationKillSwitch")) {
    adjusted_cfg.suppressor.high_bands_suppression
        .anti_howling_activation_threshold = 25.f;
    adjusted_cfg.suppressor.high_bands_suppression.anti_howling_gain = 0.01f;
  }

  if (field_trial::IsEnabled("WebRTC-Aec3UseShortConfigChangeDuration")) {
    adjusted_cfg.filter.config_change_duration_blocks = 10;
  }

  if (field_trial::IsEnabled("WebRTC-Aec3UseZeroInitialStateDuration")) {
    adjusted_cfg.filter.initial_state_seconds = 0.f;
  } else if (field_trial::IsEnabled(
                 "WebRTC-Aec3UseDot1SecondsInitialStateDuration")) {
    adjusted_cfg.filter.initial_state_seconds = .1f;
  } else if (field_trial::IsEnabled(
                 "WebRTC-Aec3UseDot2SecondsInitialStateDuration")) {
    adjusted_cfg.filter.initial_state_seconds = .2f;
  } else if (field_trial::IsEnabled(
                 "WebRTC-Aec3UseDot3SecondsInitialStateDuration")) {
    adjusted_cfg.filter.initial_state_seconds = .3f;
  } else if (field_trial::IsEnabled(
                 "WebRTC-Aec3UseDot6SecondsInitialStateDuration")) {
    adjusted_cfg.filter.initial_state_seconds = .6f;
  } else if (field_trial::IsEnabled(
                 "WebRTC-Aec3UseDot9SecondsInitialStateDuration")) {
    adjusted_cfg.filter.initial_state_seconds = .9f;
  } else if (field_trial::IsEnabled(
                 "WebRTC-Aec3Use1Dot2SecondsInitialStateDuration")) {
    adjusted_cfg.filter.initial_state_seconds = 1.2f;
  } else if (field_trial::IsEnabled(
                 "WebRTC-Aec3Use1Dot6SecondsInitialStateDuration")) {
    adjusted_cfg.filter.initial_state_seconds = 1.6f;
  } else if (field_trial::IsEnabled(
                 "WebRTC-Aec3Use2Dot0SecondsInitialStateDuration")) {
    adjusted_cfg.filter.initial_state_seconds = 2.0f;
  }

  if (field_trial::IsEnabled("WebRTC-Aec3HighPassFilterEchoReference")) {
    adjusted_cfg.filter.high_pass_filter_echo_reference = true;
  }

  if (field_trial::IsEnabled("WebRTC-Aec3EchoSaturationDetectionKillSwitch")) {
    adjusted_cfg.ep_strength.echo_can_saturate = false;
  }

  const std::string use_nearend_reverb_len_tunings =
      field_trial::FindFullName("WebRTC-Aec3UseNearendReverbLen");
  FieldTrialParameter<double> nearend_reverb_default_len(
      "default_len", adjusted_cfg.ep_strength.default_len);
  FieldTrialParameter<double> nearend_reverb_nearend_len(
      "nearend_len", adjusted_cfg.ep_strength.nearend_len);

  ParseFieldTrial({&nearend_reverb_default_len, &nearend_reverb_nearend_len},
                  use_nearend_reverb_len_tunings);
  float default_len = static_cast<float>(nearend_reverb_default_len.Get());
  float nearend_len = static_cast<float>(nearend_reverb_nearend_len.Get());
  if (default_len > -1 && default_len < 1 && nearend_len > -1 &&
      nearend_len < 1) {
    adjusted_cfg.ep_strength.default_len =
        static_cast<float>(nearend_reverb_default_len.Get());
    adjusted_cfg.ep_strength.nearend_len =
        static_cast<float>(nearend_reverb_nearend_len.Get());
  }

  if (field_trial::IsEnabled("WebRTC-Aec3ConservativeTailFreqResponse")) {
    adjusted_cfg.ep_strength.use_conservative_tail_frequency_response = true;
  }

  if (field_trial::IsEnabled("WebRTC-Aec3ShortHeadroomKillSwitch")) {
    // Two blocks headroom.
    adjusted_cfg.delay.delay_headroom_samples = kBlockSize * 2;
  }

  if (field_trial::IsEnabled("WebRTC-Aec3ClampInstQualityToZeroKillSwitch")) {
    adjusted_cfg.erle.clamp_quality_estimate_to_zero = false;
  }

  if (field_trial::IsEnabled("WebRTC-Aec3ClampInstQualityToOneKillSwitch")) {
    adjusted_cfg.erle.clamp_quality_estimate_to_one = false;
  }

  if (field_trial::IsEnabled("WebRTC-Aec3OnsetDetectionKillSwitch")) {
    adjusted_cfg.erle.onset_detection = false;
  }

  if (field_trial::IsEnabled(
          "WebRTC-Aec3EnforceRenderDelayEstimationDownmixing")) {
    adjusted_cfg.delay.render_alignment_mixing.downmix = true;
    adjusted_cfg.delay.render_alignment_mixing.adaptive_selection = false;
  }

  if (field_trial::IsEnabled(
          "WebRTC-Aec3EnforceCaptureDelayEstimationDownmixing")) {
    adjusted_cfg.delay.capture_alignment_mixing.downmix = true;
    adjusted_cfg.delay.capture_alignment_mixing.adaptive_selection = false;
  }

  if (field_trial::IsEnabled(
          "WebRTC-Aec3EnforceCaptureDelayEstimationLeftRightPrioritization")) {
    adjusted_cfg.delay.capture_alignment_mixing.prefer_first_two_channels =
        true;
  }

  if (field_trial::IsEnabled(
          "WebRTC-"
          "Aec3RenderDelayEstimationLeftRightPrioritizationKillSwitch")) {
    adjusted_cfg.delay.capture_alignment_mixing.prefer_first_two_channels =
        false;
  }

  if (field_trial::IsEnabled("WebRTC-Aec3SensitiveDominantNearendActivation")) {
    adjusted_cfg.suppressor.dominant_nearend_detection.enr_threshold = 0.5f;
  } else if (field_trial::IsEnabled(
                 "WebRTC-Aec3VerySensitiveDominantNearendActivation")) {
    adjusted_cfg.suppressor.dominant_nearend_detection.enr_threshold = 0.75f;
  }

  if (field_trial::IsEnabled("WebRTC-Aec3TransparentAntiHowlingGain")) {
    adjusted_cfg.suppressor.high_bands_suppression.anti_howling_gain = 1.f;
  }

  if (field_trial::IsEnabled(
          "WebRTC-Aec3EnforceMoreTransparentNormalSuppressorTuning")) {
    adjusted_cfg.suppressor.normal_tuning.mask_lf.enr_transparent = 0.4f;
    adjusted_cfg.suppressor.normal_tuning.mask_lf.enr_suppress = 0.5f;
  }

  if (field_trial::IsEnabled(
          "WebRTC-Aec3EnforceMoreTransparentNearendSuppressorTuning")) {
    adjusted_cfg.suppressor.nearend_tuning.mask_lf.enr_transparent = 1.29f;
    adjusted_cfg.suppressor.nearend_tuning.mask_lf.enr_suppress = 1.3f;
  }

  if (field_trial::IsEnabled(
          "WebRTC-Aec3EnforceMoreTransparentNormalSuppressorHfTuning")) {
    adjusted_cfg.suppressor.normal_tuning.mask_hf.enr_transparent = 0.3f;
    adjusted_cfg.suppressor.normal_tuning.mask_hf.enr_suppress = 0.4f;
  }

  if (field_trial::IsEnabled(
          "WebRTC-Aec3EnforceMoreTransparentNearendSuppressorHfTuning")) {
    adjusted_cfg.suppressor.nearend_tuning.mask_hf.enr_transparent = 1.09f;
    adjusted_cfg.suppressor.nearend_tuning.mask_hf.enr_suppress = 1.1f;
  }

  if (field_trial::IsEnabled(
          "WebRTC-Aec3EnforceRapidlyAdjustingNormalSuppressorTunings")) {
    adjusted_cfg.suppressor.normal_tuning.max_inc_factor = 2.5f;
  }

  if (field_trial::IsEnabled(
          "WebRTC-Aec3EnforceRapidlyAdjustingNearendSuppressorTunings")) {
    adjusted_cfg.suppressor.nearend_tuning.max_inc_factor = 2.5f;
  }

  if (field_trial::IsEnabled(
          "WebRTC-Aec3EnforceSlowlyAdjustingNormalSuppressorTunings")) {
    adjusted_cfg.suppressor.normal_tuning.max_dec_factor_lf = .2f;
  }

  if (field_trial::IsEnabled(
          "WebRTC-Aec3EnforceSlowlyAdjustingNearendSuppressorTunings")) {
    adjusted_cfg.suppressor.nearend_tuning.max_dec_factor_lf = .2f;
  }

  if (field_trial::IsEnabled("WebRTC-Aec3EnforceConservativeHfSuppression")) {
    adjusted_cfg.suppressor.conservative_hf_suppression = true;
  }

  if (field_trial::IsEnabled("WebRTC-Aec3EnforceStationarityProperties")) {
    adjusted_cfg.echo_audibility.use_stationarity_properties = true;
  }

  if (field_trial::IsEnabled(
          "WebRTC-Aec3EnforceStationarityPropertiesAtInit")) {
    adjusted_cfg.echo_audibility.use_stationarity_properties_at_init = true;
  }

  if (field_trial::IsEnabled("WebRTC-Aec3EnforceLowActiveRenderLimit")) {
    adjusted_cfg.render_levels.active_render_limit = 50.f;
  } else if (field_trial::IsEnabled(
                 "WebRTC-Aec3EnforceVeryLowActiveRenderLimit")) {
    adjusted_cfg.render_levels.active_render_limit = 30.f;
  }

  if (field_trial::IsEnabled("WebRTC-Aec3NonlinearModeReverbKillSwitch")) {
    adjusted_cfg.echo_model.model_reverb_in_nonlinear_mode = false;
  }

  // Field-trial based override for the whole suppressor tuning.
  const std::string suppressor_tuning_override_trial_name =
      field_trial::FindFullName("WebRTC-Aec3SuppressorTuningOverride");

  FieldTrialParameter<double> nearend_tuning_mask_lf_enr_transparent(
      "nearend_tuning_mask_lf_enr_transparent",
      adjusted_cfg.suppressor.nearend_tuning.mask_lf.enr_transparent);
  FieldTrialParameter<double> nearend_tuning_mask_lf_enr_suppress(
      "nearend_tuning_mask_lf_enr_suppress",
      adjusted_cfg.suppressor.nearend_tuning.mask_lf.enr_suppress);
  FieldTrialParameter<double> nearend_tuning_mask_hf_enr_transparent(
      "nearend_tuning_mask_hf_enr_transparent",
      adjusted_cfg.suppressor.nearend_tuning.mask_hf.enr_transparent);
  FieldTrialParameter<double> nearend_tuning_mask_hf_enr_suppress(
      "nearend_tuning_mask_hf_enr_suppress",
      adjusted_cfg.suppressor.nearend_tuning.mask_hf.enr_suppress);
  FieldTrialParameter<double> nearend_tuning_max_inc_factor(
      "nearend_tuning_max_inc_factor",
      adjusted_cfg.suppressor.nearend_tuning.max_inc_factor);
  FieldTrialParameter<double> nearend_tuning_max_dec_factor_lf(
      "nearend_tuning_max_dec_factor_lf",
      adjusted_cfg.suppressor.nearend_tuning.max_dec_factor_lf);
  FieldTrialParameter<double> normal_tuning_mask_lf_enr_transparent(
      "normal_tuning_mask_lf_enr_transparent",
      adjusted_cfg.suppressor.normal_tuning.mask_lf.enr_transparent);
  FieldTrialParameter<double> normal_tuning_mask_lf_enr_suppress(
      "normal_tuning_mask_lf_enr_suppress",
      adjusted_cfg.suppressor.normal_tuning.mask_lf.enr_suppress);
  FieldTrialParameter<double> normal_tuning_mask_hf_enr_transparent(
      "normal_tuning_mask_hf_enr_transparent",
      adjusted_cfg.suppressor.normal_tuning.mask_hf.enr_transparent);
  FieldTrialParameter<double> normal_tuning_mask_hf_enr_suppress(
      "normal_tuning_mask_hf_enr_suppress",
      adjusted_cfg.suppressor.normal_tuning.mask_hf.enr_suppress);
  FieldTrialParameter<double> normal_tuning_max_inc_factor(
      "normal_tuning_max_inc_factor",
      adjusted_cfg.suppressor.normal_tuning.max_inc_factor);
  FieldTrialParameter<double> normal_tuning_max_dec_factor_lf(
      "normal_tuning_max_dec_factor_lf",
      adjusted_cfg.suppressor.normal_tuning.max_dec_factor_lf);
  FieldTrialParameter<double> dominant_nearend_detection_enr_threshold(
      "dominant_nearend_detection_enr_threshold",
      adjusted_cfg.suppressor.dominant_nearend_detection.enr_threshold);
  FieldTrialParameter<double> dominant_nearend_detection_enr_exit_threshold(
      "dominant_nearend_detection_enr_exit_threshold",
      adjusted_cfg.suppressor.dominant_nearend_detection.enr_exit_threshold);
  FieldTrialParameter<double> dominant_nearend_detection_snr_threshold(
      "dominant_nearend_detection_snr_threshold",
      adjusted_cfg.suppressor.dominant_nearend_detection.snr_threshold);
  FieldTrialParameter<int> dominant_nearend_detection_hold_duration(
      "dominant_nearend_detection_hold_duration",
      adjusted_cfg.suppressor.dominant_nearend_detection.hold_duration);
  FieldTrialParameter<int> dominant_nearend_detection_trigger_threshold(
      "dominant_nearend_detection_trigger_threshold",
      adjusted_cfg.suppressor.dominant_nearend_detection.trigger_threshold);

  ParseFieldTrial(
      {&nearend_tuning_mask_lf_enr_transparent,
       &nearend_tuning_mask_lf_enr_suppress,
       &nearend_tuning_mask_hf_enr_transparent,
       &nearend_tuning_mask_hf_enr_suppress, &nearend_tuning_max_inc_factor,
       &nearend_tuning_max_dec_factor_lf,
       &normal_tuning_mask_lf_enr_transparent,
       &normal_tuning_mask_lf_enr_suppress,
       &normal_tuning_mask_hf_enr_transparent,
       &normal_tuning_mask_hf_enr_suppress, &normal_tuning_max_inc_factor,
       &normal_tuning_max_dec_factor_lf,
       &dominant_nearend_detection_enr_threshold,
       &dominant_nearend_detection_enr_exit_threshold,
       &dominant_nearend_detection_snr_threshold,
       &dominant_nearend_detection_hold_duration,
       &dominant_nearend_detection_trigger_threshold},
      suppressor_tuning_override_trial_name);

  adjusted_cfg.suppressor.nearend_tuning.mask_lf.enr_transparent =
      static_cast<float>(nearend_tuning_mask_lf_enr_transparent.Get());
  adjusted_cfg.suppressor.nearend_tuning.mask_lf.enr_suppress =
      static_cast<float>(nearend_tuning_mask_lf_enr_suppress.Get());
  adjusted_cfg.suppressor.nearend_tuning.mask_hf.enr_transparent =
      static_cast<float>(nearend_tuning_mask_hf_enr_transparent.Get());
  adjusted_cfg.suppressor.nearend_tuning.mask_hf.enr_suppress =
      static_cast<float>(nearend_tuning_mask_hf_enr_suppress.Get());
  adjusted_cfg.suppressor.nearend_tuning.max_inc_factor =
      static_cast<float>(nearend_tuning_max_inc_factor.Get());
  adjusted_cfg.suppressor.nearend_tuning.max_dec_factor_lf =
      static_cast<float>(nearend_tuning_max_dec_factor_lf.Get());
  adjusted_cfg.suppressor.normal_tuning.mask_lf.enr_transparent =
      static_cast<float>(normal_tuning_mask_lf_enr_transparent.Get());
  adjusted_cfg.suppressor.normal_tuning.mask_lf.enr_suppress =
      static_cast<float>(normal_tuning_mask_lf_enr_suppress.Get());
  adjusted_cfg.suppressor.normal_tuning.mask_hf.enr_transparent =
      static_cast<float>(normal_tuning_mask_hf_enr_transparent.Get());
  adjusted_cfg.suppressor.normal_tuning.mask_hf.enr_suppress =
      static_cast<float>(normal_tuning_mask_hf_enr_suppress.Get());
  adjusted_cfg.suppressor.normal_tuning.max_inc_factor =
      static_cast<float>(normal_tuning_max_inc_factor.Get());
  adjusted_cfg.suppressor.normal_tuning.max_dec_factor_lf =
      static_cast<float>(normal_tuning_max_dec_factor_lf.Get());
  adjusted_cfg.suppressor.dominant_nearend_detection.enr_threshold =
      static_cast<float>(dominant_nearend_detection_enr_threshold.Get());
  adjusted_cfg.suppressor.dominant_nearend_detection.enr_exit_threshold =
      static_cast<float>(dominant_nearend_detection_enr_exit_threshold.Get());
  adjusted_cfg.suppressor.dominant_nearend_detection.snr_threshold =
      static_cast<float>(dominant_nearend_detection_snr_threshold.Get());
  adjusted_cfg.suppressor.dominant_nearend_detection.hold_duration =
      dominant_nearend_detection_hold_duration.Get();
  adjusted_cfg.suppressor.dominant_nearend_detection.trigger_threshold =
      dominant_nearend_detection_trigger_threshold.Get();

  // Field trial-based overrides of individual suppressor parameters.
  RetrieveFieldTrialValue(
      "WebRTC-Aec3SuppressorNearendLfMaskTransparentOverride", 0.f, 10.f,
      &adjusted_cfg.suppressor.nearend_tuning.mask_lf.enr_transparent);
  RetrieveFieldTrialValue(
      "WebRTC-Aec3SuppressorNearendLfMaskSuppressOverride", 0.f, 10.f,
      &adjusted_cfg.suppressor.nearend_tuning.mask_lf.enr_suppress);
  RetrieveFieldTrialValue(
      "WebRTC-Aec3SuppressorNearendHfMaskTransparentOverride", 0.f, 10.f,
      &adjusted_cfg.suppressor.nearend_tuning.mask_hf.enr_transparent);
  RetrieveFieldTrialValue(
      "WebRTC-Aec3SuppressorNearendHfMaskSuppressOverride", 0.f, 10.f,
      &adjusted_cfg.suppressor.nearend_tuning.mask_hf.enr_suppress);
  RetrieveFieldTrialValue(
      "WebRTC-Aec3SuppressorNearendMaxIncFactorOverride", 0.f, 10.f,
      &adjusted_cfg.suppressor.nearend_tuning.max_inc_factor);
  RetrieveFieldTrialValue(
      "WebRTC-Aec3SuppressorNearendMaxDecFactorLfOverride", 0.f, 10.f,
      &adjusted_cfg.suppressor.nearend_tuning.max_dec_factor_lf);

  RetrieveFieldTrialValue(
      "WebRTC-Aec3SuppressorNormalLfMaskTransparentOverride", 0.f, 10.f,
      &adjusted_cfg.suppressor.normal_tuning.mask_lf.enr_transparent);
  RetrieveFieldTrialValue(
      "WebRTC-Aec3SuppressorNormalLfMaskSuppressOverride", 0.f, 10.f,
      &adjusted_cfg.suppressor.normal_tuning.mask_lf.enr_suppress);
  RetrieveFieldTrialValue(
      "WebRTC-Aec3SuppressorNormalHfMaskTransparentOverride", 0.f, 10.f,
      &adjusted_cfg.suppressor.normal_tuning.mask_hf.enr_transparent);
  RetrieveFieldTrialValue(
      "WebRTC-Aec3SuppressorNormalHfMaskSuppressOverride", 0.f, 10.f,
      &adjusted_cfg.suppressor.normal_tuning.mask_hf.enr_suppress);
  RetrieveFieldTrialValue(
      "WebRTC-Aec3SuppressorNormalMaxIncFactorOverride", 0.f, 10.f,
      &adjusted_cfg.suppressor.normal_tuning.max_inc_factor);
  RetrieveFieldTrialValue(
      "WebRTC-Aec3SuppressorNormalMaxDecFactorLfOverride", 0.f, 10.f,
      &adjusted_cfg.suppressor.normal_tuning.max_dec_factor_lf);

  RetrieveFieldTrialValue(
      "WebRTC-Aec3SuppressorDominantNearendEnrThresholdOverride", 0.f, 100.f,
      &adjusted_cfg.suppressor.dominant_nearend_detection.enr_threshold);
  RetrieveFieldTrialValue(
      "WebRTC-Aec3SuppressorDominantNearendEnrExitThresholdOverride", 0.f,
      100.f,
      &adjusted_cfg.suppressor.dominant_nearend_detection.enr_exit_threshold);
  RetrieveFieldTrialValue(
      "WebRTC-Aec3SuppressorDominantNearendSnrThresholdOverride", 0.f, 100.f,
      &adjusted_cfg.suppressor.dominant_nearend_detection.snr_threshold);
  RetrieveFieldTrialValue(
      "WebRTC-Aec3SuppressorDominantNearendHoldDurationOverride", 0, 1000,
      &adjusted_cfg.suppressor.dominant_nearend_detection.hold_duration);
  RetrieveFieldTrialValue(
      "WebRTC-Aec3SuppressorDominantNearendTriggerThresholdOverride", 0, 1000,
      &adjusted_cfg.suppressor.dominant_nearend_detection.trigger_threshold);

  RetrieveFieldTrialValue(
      "WebRTC-Aec3SuppressorAntiHowlingGainOverride", 0.f, 10.f,
      &adjusted_cfg.suppressor.high_bands_suppression.anti_howling_gain);

  // Field trial-based overrides of individual delay estimator parameters.
  RetrieveFieldTrialValue("WebRTC-Aec3DelayEstimateSmoothingOverride", 0.f, 1.f,
                          &adjusted_cfg.delay.delay_estimate_smoothing);
  RetrieveFieldTrialValue(
      "WebRTC-Aec3DelayEstimateSmoothingDelayFoundOverride", 0.f, 1.f,
      &adjusted_cfg.delay.delay_estimate_smoothing_delay_found);

  return adjusted_cfg;
}

class EchoCanceller3::RenderWriter {
 public:
  RenderWriter(ApmDataDumper* data_dumper,
               const EchoCanceller3Config& config,
               SwapQueue<std::vector<std::vector<std::vector<float>>>,
                         Aec3RenderQueueItemVerifier>* render_transfer_queue,
               size_t num_bands,
               size_t num_channels);

  RenderWriter() = delete;
  RenderWriter(const RenderWriter&) = delete;
  RenderWriter& operator=(const RenderWriter&) = delete;

  ~RenderWriter();
  void Insert(const AudioBuffer& input);

 private:
  ApmDataDumper* data_dumper_;
  const size_t num_bands_;
  const size_t num_channels_;
  std::unique_ptr<HighPassFilter> high_pass_filter_;
  std::vector<std::vector<std::vector<float>>> render_queue_input_frame_;
  SwapQueue<std::vector<std::vector<std::vector<float>>>,
            Aec3RenderQueueItemVerifier>* render_transfer_queue_;
};

EchoCanceller3::RenderWriter::RenderWriter(
    ApmDataDumper* data_dumper,
    const EchoCanceller3Config& config,
    SwapQueue<std::vector<std::vector<std::vector<float>>>,
              Aec3RenderQueueItemVerifier>* render_transfer_queue,
    size_t num_bands,
    size_t num_channels)
    : data_dumper_(data_dumper),
      num_bands_(num_bands),
      num_channels_(num_channels),
      render_queue_input_frame_(
          num_bands_,
          std::vector<std::vector<float>>(
              num_channels_,
              std::vector<float>(AudioBuffer::kSplitBandSize, 0.f))),
      render_transfer_queue_(render_transfer_queue) {
  RTC_DCHECK(data_dumper);
  if (config.filter.high_pass_filter_echo_reference) {
    high_pass_filter_ = std::make_unique<HighPassFilter>(16000, num_channels);
  }
}

EchoCanceller3::RenderWriter::~RenderWriter() = default;

void EchoCanceller3::RenderWriter::Insert(const AudioBuffer& input) {
  RTC_DCHECK_EQ(AudioBuffer::kSplitBandSize, input.num_frames_per_band());
  RTC_DCHECK_EQ(num_bands_, input.num_bands());
  RTC_DCHECK_EQ(num_channels_, input.num_channels());

  // TODO(bugs.webrtc.org/8759) Temporary work-around.
  if (num_bands_ != input.num_bands())
    return;

  data_dumper_->DumpWav("aec3_render_input", AudioBuffer::kSplitBandSize,
                        &input.split_bands_const(0)[0][0], 16000, 1);

  CopyBufferIntoFrame(input, num_bands_, num_channels_,
                      &render_queue_input_frame_);
  if (high_pass_filter_) {
    high_pass_filter_->Process(&render_queue_input_frame_[0]);
  }

  static_cast<void>(render_transfer_queue_->Insert(&render_queue_input_frame_));
}

int EchoCanceller3::instance_count_ = 0;

EchoCanceller3::EchoCanceller3(const EchoCanceller3Config& config,
                               int sample_rate_hz,
                               size_t num_render_channels,
                               size_t num_capture_channels)
    : EchoCanceller3(AdjustConfig(config),
                     sample_rate_hz,
                     num_render_channels,
                     num_capture_channels,
                     std::unique_ptr<BlockProcessor>(
                         BlockProcessor::Create(AdjustConfig(config),
                                                sample_rate_hz,
                                                num_render_channels,
                                                num_capture_channels))) {}
EchoCanceller3::EchoCanceller3(const EchoCanceller3Config& config,
                               int sample_rate_hz,
                               size_t num_render_channels,
                               size_t num_capture_channels,
                               std::unique_ptr<BlockProcessor> block_processor)
    : data_dumper_(
          new ApmDataDumper(rtc::AtomicOps::Increment(&instance_count_))),
      config_(config),
      sample_rate_hz_(sample_rate_hz),
      num_bands_(NumBandsForRate(sample_rate_hz_)),
      num_render_channels_(num_render_channels),
      num_capture_channels_(num_capture_channels),
      output_framer_(num_bands_, num_capture_channels_),
      capture_blocker_(num_bands_, num_capture_channels_),
      render_blocker_(num_bands_, num_render_channels_),
      render_transfer_queue_(
          kRenderTransferQueueSizeFrames,
          std::vector<std::vector<std::vector<float>>>(
              num_bands_,
              std::vector<std::vector<float>>(
                  num_render_channels_,
                  std::vector<float>(AudioBuffer::kSplitBandSize, 0.f))),
          Aec3RenderQueueItemVerifier(num_bands_,
                                      num_render_channels_,
                                      AudioBuffer::kSplitBandSize)),
      block_processor_(std::move(block_processor)),
      render_queue_output_frame_(
          num_bands_,
          std::vector<std::vector<float>>(
              num_render_channels_,
              std::vector<float>(AudioBuffer::kSplitBandSize, 0.f))),
      render_block_(
          num_bands_,
          std::vector<std::vector<float>>(num_render_channels_,
                                          std::vector<float>(kBlockSize, 0.f))),
      capture_block_(
          num_bands_,
          std::vector<std::vector<float>>(num_capture_channels_,
                                          std::vector<float>(kBlockSize, 0.f))),
      render_sub_frame_view_(
          num_bands_,
          std::vector<rtc::ArrayView<float>>(num_render_channels_)),
      capture_sub_frame_view_(
          num_bands_,
          std::vector<rtc::ArrayView<float>>(num_capture_channels_)) {
  RTC_DCHECK(ValidFullBandRate(sample_rate_hz_));

  if (config_.delay.fixed_capture_delay_samples > 0) {
    block_delay_buffer_.reset(new BlockDelayBuffer(
        num_capture_channels_, num_bands_, AudioBuffer::kSplitBandSize,
        config_.delay.fixed_capture_delay_samples));
  }

  render_writer_.reset(new RenderWriter(data_dumper_.get(), config_,
                                        &render_transfer_queue_, num_bands_,
                                        num_render_channels_));

  RTC_DCHECK_EQ(num_bands_, std::max(sample_rate_hz_, 16000) / 16000);
  RTC_DCHECK_GE(kMaxNumBands, num_bands_);

  if (config_.filter.export_linear_aec_output) {
    linear_output_framer_.reset(new BlockFramer(1, num_capture_channels_));
    linear_output_block_ =
        std::make_unique<std::vector<std::vector<std::vector<float>>>>(
            1, std::vector<std::vector<float>>(
                   num_capture_channels_, std::vector<float>(kBlockSize, 0.f)));
    linear_output_sub_frame_view_ =
        std::vector<std::vector<rtc::ArrayView<float>>>(
            1, std::vector<rtc::ArrayView<float>>(num_capture_channels_));
  }

  RTC_LOG(LS_INFO) << "AEC3 created with sample rate: " << sample_rate_hz_
                   << " Hz, num render channels: " << num_render_channels_
                   << ", num capture channels: " << num_capture_channels_;
}

EchoCanceller3::~EchoCanceller3() = default;

void EchoCanceller3::AnalyzeRender(const AudioBuffer& render) {
  RTC_DCHECK_RUNS_SERIALIZED(&render_race_checker_);

  RTC_DCHECK_EQ(render.num_channels(), num_render_channels_);
  data_dumper_->DumpRaw("aec3_call_order",
                        static_cast<int>(EchoCanceller3ApiCall::kRender));

  return render_writer_->Insert(render);
}

void EchoCanceller3::AnalyzeCapture(const AudioBuffer& capture) {
  RTC_DCHECK_RUNS_SERIALIZED(&capture_race_checker_);
  data_dumper_->DumpWav("aec3_capture_analyze_input", capture.num_frames(),
                        capture.channels_const()[0], sample_rate_hz_, 1);
  saturated_microphone_signal_ = false;
  for (size_t channel = 0; channel < capture.num_channels(); ++channel) {
    saturated_microphone_signal_ |=
        DetectSaturation(rtc::ArrayView<const float>(
            capture.channels_const()[channel], capture.num_frames()));
    if (saturated_microphone_signal_) {
      break;
    }
  }
}

void EchoCanceller3::ProcessCapture(AudioBuffer* capture, bool level_change) {
  ProcessCapture(capture, nullptr, level_change);
}

void EchoCanceller3::ProcessCapture(AudioBuffer* capture,
                                    AudioBuffer* linear_output,
                                    bool level_change) {
  RTC_DCHECK_RUNS_SERIALIZED(&capture_race_checker_);
  RTC_DCHECK(capture);
  RTC_DCHECK_EQ(num_bands_, capture->num_bands());
  RTC_DCHECK_EQ(AudioBuffer::kSplitBandSize, capture->num_frames_per_band());
  RTC_DCHECK_EQ(capture->num_channels(), num_capture_channels_);
  data_dumper_->DumpRaw("aec3_call_order",
                        static_cast<int>(EchoCanceller3ApiCall::kCapture));

  if (linear_output && !linear_output_framer_) {
    RTC_LOG(LS_ERROR) << "Trying to retrieve the linear AEC output without "
                         "properly configuring AEC3.";
    RTC_DCHECK_NOTREACHED();
  }

  // Report capture call in the metrics and periodically update API call
  // metrics.
  api_call_metrics_.ReportCaptureCall();

  // Optionally delay the capture signal.
  if (config_.delay.fixed_capture_delay_samples > 0) {
    RTC_DCHECK(block_delay_buffer_);
    block_delay_buffer_->DelaySignal(capture);
  }

  rtc::ArrayView<float> capture_lower_band = rtc::ArrayView<float>(
      &capture->split_bands(0)[0][0], AudioBuffer::kSplitBandSize);

  data_dumper_->DumpWav("aec3_capture_input", capture_lower_band, 16000, 1);

  EmptyRenderQueue();

  ProcessCaptureFrameContent(linear_output, capture, level_change,
                             saturated_microphone_signal_, 0, &capture_blocker_,
                             linear_output_framer_.get(), &output_framer_,
                             block_processor_.get(), linear_output_block_.get(),
                             &linear_output_sub_frame_view_, &capture_block_,
                             &capture_sub_frame_view_);

  ProcessCaptureFrameContent(linear_output, capture, level_change,
                             saturated_microphone_signal_, 1, &capture_blocker_,
                             linear_output_framer_.get(), &output_framer_,
                             block_processor_.get(), linear_output_block_.get(),
                             &linear_output_sub_frame_view_, &capture_block_,
                             &capture_sub_frame_view_);

  ProcessRemainingCaptureFrameContent(
      level_change, saturated_microphone_signal_, &capture_blocker_,
      linear_output_framer_.get(), &output_framer_, block_processor_.get(),
      linear_output_block_.get(), &capture_block_);

  data_dumper_->DumpWav("aec3_capture_output", AudioBuffer::kSplitBandSize,
                        &capture->split_bands(0)[0][0], 16000, 1);
}

EchoControl::Metrics EchoCanceller3::GetMetrics() const {
  RTC_DCHECK_RUNS_SERIALIZED(&capture_race_checker_);
  Metrics metrics;
  block_processor_->GetMetrics(&metrics);
  return metrics;
}

void EchoCanceller3::SetAudioBufferDelay(int delay_ms) {
  RTC_DCHECK_RUNS_SERIALIZED(&capture_race_checker_);
  block_processor_->SetAudioBufferDelay(delay_ms);
}

void EchoCanceller3::SetCaptureOutputUsage(bool capture_output_used) {
  RTC_DCHECK_RUNS_SERIALIZED(&capture_race_checker_);
  block_processor_->SetCaptureOutputUsage(capture_output_used);
}

bool EchoCanceller3::ActiveProcessing() const {
  return true;
}

EchoCanceller3Config EchoCanceller3::CreateDefaultConfig(
    size_t num_render_channels,
    size_t num_capture_channels) {
  EchoCanceller3Config cfg;
  if (num_render_channels > 1) {
    // Use shorter and more rapidly adapting coarse filter to compensate for
    // thge increased number of total filter parameters to adapt.
    cfg.filter.coarse.length_blocks = 11;
    cfg.filter.coarse.rate = 0.95f;
    cfg.filter.coarse_initial.length_blocks = 11;
    cfg.filter.coarse_initial.rate = 0.95f;

    // Use more concervative suppressor behavior for non-nearend speech.
    cfg.suppressor.normal_tuning.max_dec_factor_lf = 0.35f;
    cfg.suppressor.normal_tuning.max_inc_factor = 1.5f;
  }
  return cfg;
}

void EchoCanceller3::EmptyRenderQueue() {
  RTC_DCHECK_RUNS_SERIALIZED(&capture_race_checker_);
  bool frame_to_buffer =
      render_transfer_queue_.Remove(&render_queue_output_frame_);
  while (frame_to_buffer) {
    // Report render call in the metrics.
    api_call_metrics_.ReportRenderCall();

    BufferRenderFrameContent(&render_queue_output_frame_, 0, &render_blocker_,
                             block_processor_.get(), &render_block_,
                             &render_sub_frame_view_);

    BufferRenderFrameContent(&render_queue_output_frame_, 1, &render_blocker_,
                             block_processor_.get(), &render_block_,
                             &render_sub_frame_view_);

    BufferRemainingRenderFrameContent(&render_blocker_, block_processor_.get(),
                                      &render_block_);

    frame_to_buffer =
        render_transfer_queue_.Remove(&render_queue_output_frame_);
  }
}
}  // namespace webrtc
