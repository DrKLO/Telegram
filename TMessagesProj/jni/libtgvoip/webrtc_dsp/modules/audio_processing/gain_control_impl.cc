/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/gain_control_impl.h"

#include <cstdint>

#include "absl/types/optional.h"
#include "modules/audio_processing/agc/legacy/gain_control.h"
#include "modules/audio_processing/audio_buffer.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/checks.h"
#include "rtc_base/constructormagic.h"

namespace webrtc {

typedef void Handle;

namespace {
int16_t MapSetting(GainControl::Mode mode) {
  switch (mode) {
    case GainControl::kAdaptiveAnalog:
      return kAgcModeAdaptiveAnalog;
    case GainControl::kAdaptiveDigital:
      return kAgcModeAdaptiveDigital;
    case GainControl::kFixedDigital:
      return kAgcModeFixedDigital;
  }
  RTC_NOTREACHED();
  return -1;
}

}  // namespace

class GainControlImpl::GainController {
 public:
  explicit GainController() {
    state_ = WebRtcAgc_Create();
    RTC_CHECK(state_);
  }

  ~GainController() {
    RTC_DCHECK(state_);
    WebRtcAgc_Free(state_);
  }

  Handle* state() {
    RTC_DCHECK(state_);
    return state_;
  }

  void Initialize(int minimum_capture_level,
                  int maximum_capture_level,
                  Mode mode,
                  int sample_rate_hz,
                  int capture_level) {
    RTC_DCHECK(state_);
    int error =
        WebRtcAgc_Init(state_, minimum_capture_level, maximum_capture_level,
                       MapSetting(mode), sample_rate_hz);
    RTC_DCHECK_EQ(0, error);

    set_capture_level(capture_level);
  }

  void set_capture_level(int capture_level) { capture_level_ = capture_level; }

  int get_capture_level() {
    RTC_DCHECK(capture_level_);
    return *capture_level_;
  }

 private:
  Handle* state_;
  // TODO(peah): Remove the optional once the initialization is moved into the
  // ctor.
  absl::optional<int> capture_level_;

  RTC_DISALLOW_COPY_AND_ASSIGN(GainController);
};

int GainControlImpl::instance_counter_ = 0;

GainControlImpl::GainControlImpl(rtc::CriticalSection* crit_render,
                                 rtc::CriticalSection* crit_capture)
    : crit_render_(crit_render),
      crit_capture_(crit_capture),
      data_dumper_(new ApmDataDumper(instance_counter_)),
      mode_(kAdaptiveAnalog),
      minimum_capture_level_(0),
      maximum_capture_level_(255),
      limiter_enabled_(true),
      target_level_dbfs_(3),
      compression_gain_db_(9),
      analog_capture_level_(0),
      was_analog_level_set_(false),
      stream_is_saturated_(false) {
  RTC_DCHECK(crit_render);
  RTC_DCHECK(crit_capture);
}

GainControlImpl::~GainControlImpl() {}

void GainControlImpl::ProcessRenderAudio(
    rtc::ArrayView<const int16_t> packed_render_audio) {
  rtc::CritScope cs_capture(crit_capture_);
  if (!enabled_) {
    return;
  }

  for (auto& gain_controller : gain_controllers_) {
    WebRtcAgc_AddFarend(gain_controller->state(), packed_render_audio.data(),
                        packed_render_audio.size());
  }
}

void GainControlImpl::PackRenderAudioBuffer(
    AudioBuffer* audio,
    std::vector<int16_t>* packed_buffer) {
  RTC_DCHECK_GE(160, audio->num_frames_per_band());

  packed_buffer->clear();
  packed_buffer->insert(
      packed_buffer->end(), audio->mixed_low_pass_data(),
      (audio->mixed_low_pass_data() + audio->num_frames_per_band()));
}

int GainControlImpl::AnalyzeCaptureAudio(AudioBuffer* audio) {
  rtc::CritScope cs(crit_capture_);

  if (!enabled_) {
    return AudioProcessing::kNoError;
  }

  RTC_DCHECK(num_proc_channels_);
  RTC_DCHECK_GE(160, audio->num_frames_per_band());
  RTC_DCHECK_EQ(audio->num_channels(), *num_proc_channels_);
  RTC_DCHECK_LE(*num_proc_channels_, gain_controllers_.size());

  if (mode_ == kAdaptiveAnalog) {
    int capture_channel = 0;
    for (auto& gain_controller : gain_controllers_) {
      gain_controller->set_capture_level(analog_capture_level_);
      int err = WebRtcAgc_AddMic(
          gain_controller->state(), audio->split_bands(capture_channel),
          audio->num_bands(), audio->num_frames_per_band());

      if (err != AudioProcessing::kNoError) {
        return AudioProcessing::kUnspecifiedError;
      }
      ++capture_channel;
    }
  } else if (mode_ == kAdaptiveDigital) {
    int capture_channel = 0;
    for (auto& gain_controller : gain_controllers_) {
      int32_t capture_level_out = 0;
      int err = WebRtcAgc_VirtualMic(
          gain_controller->state(), audio->split_bands(capture_channel),
          audio->num_bands(), audio->num_frames_per_band(),
          analog_capture_level_, &capture_level_out);

      gain_controller->set_capture_level(capture_level_out);

      if (err != AudioProcessing::kNoError) {
        return AudioProcessing::kUnspecifiedError;
      }
      ++capture_channel;
    }
  }

  return AudioProcessing::kNoError;
}

int GainControlImpl::ProcessCaptureAudio(AudioBuffer* audio,
                                         bool stream_has_echo) {
  rtc::CritScope cs(crit_capture_);

  if (!enabled_) {
    return AudioProcessing::kNoError;
  }

  if (mode_ == kAdaptiveAnalog && !was_analog_level_set_) {
    return AudioProcessing::kStreamParameterNotSetError;
  }

  RTC_DCHECK(num_proc_channels_);
  RTC_DCHECK_GE(160, audio->num_frames_per_band());
  RTC_DCHECK_EQ(audio->num_channels(), *num_proc_channels_);

  stream_is_saturated_ = false;
  int capture_channel = 0;
  for (auto& gain_controller : gain_controllers_) {
    int32_t capture_level_out = 0;
    uint8_t saturation_warning = 0;

    // The call to stream_has_echo() is ok from a deadlock perspective
    // as the capture lock is allready held.
    int err = WebRtcAgc_Process(
        gain_controller->state(), audio->split_bands_const(capture_channel),
        audio->num_bands(), audio->num_frames_per_band(),
        audio->split_bands(capture_channel),
        gain_controller->get_capture_level(), &capture_level_out,
        stream_has_echo, &saturation_warning);

    if (err != AudioProcessing::kNoError) {
      return AudioProcessing::kUnspecifiedError;
    }

    gain_controller->set_capture_level(capture_level_out);
    if (saturation_warning == 1) {
      stream_is_saturated_ = true;
    }

    ++capture_channel;
  }

  RTC_DCHECK_LT(0ul, *num_proc_channels_);
  if (mode_ == kAdaptiveAnalog) {
    // Take the analog level to be the average across the handles.
    analog_capture_level_ = 0;
    for (auto& gain_controller : gain_controllers_) {
      analog_capture_level_ += gain_controller->get_capture_level();
    }

    analog_capture_level_ /= (*num_proc_channels_);
  }

  was_analog_level_set_ = false;
  return AudioProcessing::kNoError;
}

int GainControlImpl::compression_gain_db() const {
  rtc::CritScope cs(crit_capture_);
  return compression_gain_db_;
}

// TODO(ajm): ensure this is called under kAdaptiveAnalog.
int GainControlImpl::set_stream_analog_level(int level) {
  rtc::CritScope cs(crit_capture_);
  data_dumper_->DumpRaw("gain_control_set_stream_analog_level", 1, &level);

  was_analog_level_set_ = true;
  if (level < minimum_capture_level_ || level > maximum_capture_level_) {
    return AudioProcessing::kBadParameterError;
  }
  analog_capture_level_ = level;

  return AudioProcessing::kNoError;
}

int GainControlImpl::stream_analog_level() {
  rtc::CritScope cs(crit_capture_);
  data_dumper_->DumpRaw("gain_control_stream_analog_level", 1,
                        &analog_capture_level_);
  // TODO(ajm): enable this assertion?
  // RTC_DCHECK_EQ(kAdaptiveAnalog, mode_);

  return analog_capture_level_;
}

int GainControlImpl::Enable(bool enable) {
  rtc::CritScope cs_render(crit_render_);
  rtc::CritScope cs_capture(crit_capture_);
  if (enable && !enabled_) {
    enabled_ = enable;  // Must be set before Initialize() is called.

    RTC_DCHECK(num_proc_channels_);
    RTC_DCHECK(sample_rate_hz_);
    Initialize(*num_proc_channels_, *sample_rate_hz_);
  } else {
    enabled_ = enable;
  }
  return AudioProcessing::kNoError;
}

bool GainControlImpl::is_enabled() const {
  rtc::CritScope cs(crit_capture_);
  return enabled_;
}

int GainControlImpl::set_mode(Mode mode) {
  rtc::CritScope cs_render(crit_render_);
  rtc::CritScope cs_capture(crit_capture_);
  if (MapSetting(mode) == -1) {
    return AudioProcessing::kBadParameterError;
  }

  mode_ = mode;
  RTC_DCHECK(num_proc_channels_);
  RTC_DCHECK(sample_rate_hz_);
  Initialize(*num_proc_channels_, *sample_rate_hz_);
  return AudioProcessing::kNoError;
}

GainControl::Mode GainControlImpl::mode() const {
  rtc::CritScope cs(crit_capture_);
  return mode_;
}

int GainControlImpl::set_analog_level_limits(int minimum, int maximum) {
  if (minimum < 0) {
    return AudioProcessing::kBadParameterError;
  }

  if (maximum > 65535) {
    return AudioProcessing::kBadParameterError;
  }

  if (maximum < minimum) {
    return AudioProcessing::kBadParameterError;
  }

  size_t num_proc_channels_local = 0u;
  int sample_rate_hz_local = 0;
  {
    rtc::CritScope cs(crit_capture_);

    minimum_capture_level_ = minimum;
    maximum_capture_level_ = maximum;

    RTC_DCHECK(num_proc_channels_);
    RTC_DCHECK(sample_rate_hz_);
    num_proc_channels_local = *num_proc_channels_;
    sample_rate_hz_local = *sample_rate_hz_;
  }
  Initialize(num_proc_channels_local, sample_rate_hz_local);
  return AudioProcessing::kNoError;
}

int GainControlImpl::analog_level_minimum() const {
  rtc::CritScope cs(crit_capture_);
  return minimum_capture_level_;
}

int GainControlImpl::analog_level_maximum() const {
  rtc::CritScope cs(crit_capture_);
  return maximum_capture_level_;
}

bool GainControlImpl::stream_is_saturated() const {
  rtc::CritScope cs(crit_capture_);
  return stream_is_saturated_;
}

int GainControlImpl::set_target_level_dbfs(int level) {
  if (level > 31 || level < 0) {
    return AudioProcessing::kBadParameterError;
  }
  {
    rtc::CritScope cs(crit_capture_);
    target_level_dbfs_ = level;
  }
  return Configure();
}

int GainControlImpl::target_level_dbfs() const {
  rtc::CritScope cs(crit_capture_);
  return target_level_dbfs_;
}

int GainControlImpl::set_compression_gain_db(int gain) {
  if (gain < 0 || gain > 90) {
    return AudioProcessing::kBadParameterError;
  }
  {
    rtc::CritScope cs(crit_capture_);
    compression_gain_db_ = gain;
  }
  return Configure();
}

int GainControlImpl::enable_limiter(bool enable) {
  {
    rtc::CritScope cs(crit_capture_);
    limiter_enabled_ = enable;
  }
  return Configure();
}

bool GainControlImpl::is_limiter_enabled() const {
  rtc::CritScope cs(crit_capture_);
  return limiter_enabled_;
}

void GainControlImpl::Initialize(size_t num_proc_channels, int sample_rate_hz) {
  rtc::CritScope cs_render(crit_render_);
  rtc::CritScope cs_capture(crit_capture_);
  data_dumper_->InitiateNewSetOfRecordings();

  num_proc_channels_ = num_proc_channels;
  sample_rate_hz_ = sample_rate_hz;

  if (!enabled_) {
    return;
  }

  gain_controllers_.resize(*num_proc_channels_);
  for (auto& gain_controller : gain_controllers_) {
    if (!gain_controller) {
      gain_controller.reset(new GainController());
    }
    gain_controller->Initialize(minimum_capture_level_, maximum_capture_level_,
                                mode_, *sample_rate_hz_, analog_capture_level_);
  }

  Configure();
}

int GainControlImpl::Configure() {
  rtc::CritScope cs_render(crit_render_);
  rtc::CritScope cs_capture(crit_capture_);
  WebRtcAgcConfig config;
  // TODO(ajm): Flip the sign here (since AGC expects a positive value) if we
  //            change the interface.
  // RTC_DCHECK_LE(target_level_dbfs_, 0);
  // config.targetLevelDbfs = static_cast<int16_t>(-target_level_dbfs_);
  config.targetLevelDbfs = static_cast<int16_t>(target_level_dbfs_);
  config.compressionGaindB = static_cast<int16_t>(compression_gain_db_);
  config.limiterEnable = limiter_enabled_;

  int error = AudioProcessing::kNoError;
  for (auto& gain_controller : gain_controllers_) {
    const int handle_error =
        WebRtcAgc_set_config(gain_controller->state(), config);
    if (handle_error != AudioProcessing::kNoError) {
      error = handle_error;
    }
  }
  return error;
}
}  // namespace webrtc
