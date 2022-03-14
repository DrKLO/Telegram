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
#include "rtc_base/logging.h"
#include "system_wrappers/include/field_trial.h"

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
  RTC_DCHECK_NOTREACHED();
  return -1;
}

// Checks whether the legacy digital gain application should be used.
bool UseLegacyDigitalGainApplier() {
  return field_trial::IsEnabled("WebRTC-UseLegacyDigitalGainApplier");
}

// Floating point variant of WebRtcAgc_Process.
void ApplyDigitalGain(const int32_t gains[11],
                      size_t num_bands,
                      float* const* out) {
  constexpr float kScaling = 1.f / 65536.f;
  constexpr int kNumSubSections = 16;
  constexpr float kOneByNumSubSections = 1.f / kNumSubSections;

  float gains_scaled[11];
  for (int k = 0; k < 11; ++k) {
    gains_scaled[k] = gains[k] * kScaling;
  }

  for (size_t b = 0; b < num_bands; ++b) {
    float* out_band = out[b];
    for (int k = 0, sample = 0; k < 10; ++k) {
      const float delta =
          (gains_scaled[k + 1] - gains_scaled[k]) * kOneByNumSubSections;
      float gain = gains_scaled[k];
      for (int n = 0; n < kNumSubSections; ++n, ++sample) {
        RTC_DCHECK_EQ(k * kNumSubSections + n, sample);
        out_band[sample] *= gain;
        out_band[sample] =
            std::min(32767.f, std::max(-32768.f, out_band[sample]));
        gain += delta;
      }
    }
  }
}

}  // namespace

struct GainControlImpl::MonoAgcState {
  MonoAgcState() {
    state = WebRtcAgc_Create();
    RTC_CHECK(state);
  }

  ~MonoAgcState() {
    RTC_DCHECK(state);
    WebRtcAgc_Free(state);
  }

  MonoAgcState(const MonoAgcState&) = delete;
  MonoAgcState& operator=(const MonoAgcState&) = delete;
  int32_t gains[11];
  Handle* state;
};

int GainControlImpl::instance_counter_ = 0;

GainControlImpl::GainControlImpl()
    : data_dumper_(new ApmDataDumper(instance_counter_)),
      use_legacy_gain_applier_(UseLegacyDigitalGainApplier()),
      mode_(kAdaptiveAnalog),
      minimum_capture_level_(0),
      maximum_capture_level_(255),
      limiter_enabled_(true),
      target_level_dbfs_(3),
      compression_gain_db_(9),
      analog_capture_level_(0),
      was_analog_level_set_(false),
      stream_is_saturated_(false) {}

GainControlImpl::~GainControlImpl() = default;

void GainControlImpl::ProcessRenderAudio(
    rtc::ArrayView<const int16_t> packed_render_audio) {
  for (size_t ch = 0; ch < mono_agcs_.size(); ++ch) {
    WebRtcAgc_AddFarend(mono_agcs_[ch]->state, packed_render_audio.data(),
                        packed_render_audio.size());
  }
}

void GainControlImpl::PackRenderAudioBuffer(
    const AudioBuffer& audio,
    std::vector<int16_t>* packed_buffer) {
  RTC_DCHECK_GE(AudioBuffer::kMaxSplitFrameLength, audio.num_frames_per_band());
  std::array<int16_t, AudioBuffer::kMaxSplitFrameLength>
      mixed_16_kHz_render_data;
  rtc::ArrayView<const int16_t> mixed_16_kHz_render(
      mixed_16_kHz_render_data.data(), audio.num_frames_per_band());
  if (audio.num_channels() == 1) {
    FloatS16ToS16(audio.split_bands_const(0)[kBand0To8kHz],
                  audio.num_frames_per_band(), mixed_16_kHz_render_data.data());
  } else {
    const int num_channels = static_cast<int>(audio.num_channels());
    for (size_t i = 0; i < audio.num_frames_per_band(); ++i) {
      int32_t sum = 0;
      for (int ch = 0; ch < num_channels; ++ch) {
        sum += FloatS16ToS16(audio.split_channels_const(kBand0To8kHz)[ch][i]);
      }
      mixed_16_kHz_render_data[i] = sum / num_channels;
    }
  }

  packed_buffer->clear();
  packed_buffer->insert(
      packed_buffer->end(), mixed_16_kHz_render.data(),
      (mixed_16_kHz_render.data() + audio.num_frames_per_band()));
}

int GainControlImpl::AnalyzeCaptureAudio(const AudioBuffer& audio) {
  RTC_DCHECK(num_proc_channels_);
  RTC_DCHECK_GE(AudioBuffer::kMaxSplitFrameLength, audio.num_frames_per_band());
  RTC_DCHECK_EQ(audio.num_channels(), *num_proc_channels_);
  RTC_DCHECK_LE(*num_proc_channels_, mono_agcs_.size());

  int16_t split_band_data[AudioBuffer::kMaxNumBands]
                         [AudioBuffer::kMaxSplitFrameLength];
  int16_t* split_bands[AudioBuffer::kMaxNumBands] = {
      split_band_data[0], split_band_data[1], split_band_data[2]};

  if (mode_ == kAdaptiveAnalog) {
    for (size_t ch = 0; ch < mono_agcs_.size(); ++ch) {
      capture_levels_[ch] = analog_capture_level_;

      audio.ExportSplitChannelData(ch, split_bands);

      int err =
          WebRtcAgc_AddMic(mono_agcs_[ch]->state, split_bands,
                           audio.num_bands(), audio.num_frames_per_band());

      if (err != AudioProcessing::kNoError) {
        return AudioProcessing::kUnspecifiedError;
      }
    }
  } else if (mode_ == kAdaptiveDigital) {
    for (size_t ch = 0; ch < mono_agcs_.size(); ++ch) {
      int32_t capture_level_out = 0;

      audio.ExportSplitChannelData(ch, split_bands);

      int err =
          WebRtcAgc_VirtualMic(mono_agcs_[ch]->state, split_bands,
                               audio.num_bands(), audio.num_frames_per_band(),
                               analog_capture_level_, &capture_level_out);

      capture_levels_[ch] = capture_level_out;

      if (err != AudioProcessing::kNoError) {
        return AudioProcessing::kUnspecifiedError;
      }
    }
  }

  return AudioProcessing::kNoError;
}

int GainControlImpl::ProcessCaptureAudio(AudioBuffer* audio,
                                         bool stream_has_echo) {
  if (mode_ == kAdaptiveAnalog && !was_analog_level_set_) {
    return AudioProcessing::kStreamParameterNotSetError;
  }

  RTC_DCHECK(num_proc_channels_);
  RTC_DCHECK_GE(AudioBuffer::kMaxSplitFrameLength,
                audio->num_frames_per_band());
  RTC_DCHECK_EQ(audio->num_channels(), *num_proc_channels_);

  stream_is_saturated_ = false;
  bool error_reported = false;
  for (size_t ch = 0; ch < mono_agcs_.size(); ++ch) {
    int16_t split_band_data[AudioBuffer::kMaxNumBands]
                           [AudioBuffer::kMaxSplitFrameLength];
    int16_t* split_bands[AudioBuffer::kMaxNumBands] = {
        split_band_data[0], split_band_data[1], split_band_data[2]};
    audio->ExportSplitChannelData(ch, split_bands);

    // The call to stream_has_echo() is ok from a deadlock perspective
    // as the capture lock is allready held.
    int32_t new_capture_level = 0;
    uint8_t saturation_warning = 0;
    int err_analyze = WebRtcAgc_Analyze(
        mono_agcs_[ch]->state, split_bands, audio->num_bands(),
        audio->num_frames_per_band(), capture_levels_[ch], &new_capture_level,
        stream_has_echo, &saturation_warning, mono_agcs_[ch]->gains);
    capture_levels_[ch] = new_capture_level;

    error_reported = error_reported || err_analyze != AudioProcessing::kNoError;

    stream_is_saturated_ = stream_is_saturated_ || saturation_warning == 1;
  }

  // Choose the minimun gain for application
  size_t index_to_apply = 0;
  for (size_t ch = 1; ch < mono_agcs_.size(); ++ch) {
    if (mono_agcs_[index_to_apply]->gains[10] < mono_agcs_[ch]->gains[10]) {
      index_to_apply = ch;
    }
  }

  if (use_legacy_gain_applier_) {
    for (size_t ch = 0; ch < mono_agcs_.size(); ++ch) {
      int16_t split_band_data[AudioBuffer::kMaxNumBands]
                             [AudioBuffer::kMaxSplitFrameLength];
      int16_t* split_bands[AudioBuffer::kMaxNumBands] = {
          split_band_data[0], split_band_data[1], split_band_data[2]};
      audio->ExportSplitChannelData(ch, split_bands);

      int err_process = WebRtcAgc_Process(
          mono_agcs_[ch]->state, mono_agcs_[index_to_apply]->gains, split_bands,
          audio->num_bands(), split_bands);
      RTC_DCHECK_EQ(err_process, 0);

      audio->ImportSplitChannelData(ch, split_bands);
    }
  } else {
    for (size_t ch = 0; ch < mono_agcs_.size(); ++ch) {
      ApplyDigitalGain(mono_agcs_[index_to_apply]->gains, audio->num_bands(),
                       audio->split_bands(ch));
    }
  }

  RTC_DCHECK_LT(0ul, *num_proc_channels_);
  if (mode_ == kAdaptiveAnalog) {
    // Take the analog level to be the minimum accross all channels.
    analog_capture_level_ = capture_levels_[0];
    for (size_t ch = 1; ch < mono_agcs_.size(); ++ch) {
      analog_capture_level_ =
          std::min(analog_capture_level_, capture_levels_[ch]);
    }
  }

  if (error_reported) {
    return AudioProcessing::kUnspecifiedError;
  }

  was_analog_level_set_ = false;

  return AudioProcessing::kNoError;
}


// TODO(ajm): ensure this is called under kAdaptiveAnalog.
int GainControlImpl::set_stream_analog_level(int level) {
  data_dumper_->DumpRaw("gain_control_set_stream_analog_level", 1, &level);

  was_analog_level_set_ = true;
  if (level < minimum_capture_level_ || level > maximum_capture_level_) {
    return AudioProcessing::kBadParameterError;
  }
  analog_capture_level_ = level;

  return AudioProcessing::kNoError;
}

int GainControlImpl::stream_analog_level() const {
  data_dumper_->DumpRaw("gain_control_stream_analog_level", 1,
                        &analog_capture_level_);
  return analog_capture_level_;
}

int GainControlImpl::set_mode(Mode mode) {
  if (MapSetting(mode) == -1) {
    return AudioProcessing::kBadParameterError;
  }

  mode_ = mode;
  RTC_DCHECK(num_proc_channels_);
  RTC_DCHECK(sample_rate_hz_);
  Initialize(*num_proc_channels_, *sample_rate_hz_);
  return AudioProcessing::kNoError;
}


int GainControlImpl::set_analog_level_limits(int minimum, int maximum) {
  if (minimum < 0 || maximum > 65535 || maximum < minimum) {
    return AudioProcessing::kBadParameterError;
  }

  minimum_capture_level_ = minimum;
  maximum_capture_level_ = maximum;

  RTC_DCHECK(num_proc_channels_);
  RTC_DCHECK(sample_rate_hz_);
  Initialize(*num_proc_channels_, *sample_rate_hz_);
  return AudioProcessing::kNoError;
}


int GainControlImpl::set_target_level_dbfs(int level) {
  if (level > 31 || level < 0) {
    return AudioProcessing::kBadParameterError;
  }
  target_level_dbfs_ = level;
  return Configure();
}

int GainControlImpl::set_compression_gain_db(int gain) {
  if (gain < 0 || gain > 90) {
    RTC_LOG(LS_ERROR) << "set_compression_gain_db(" << gain << ") failed.";
    return AudioProcessing::kBadParameterError;
  }
  compression_gain_db_ = gain;
  return Configure();
}

int GainControlImpl::enable_limiter(bool enable) {
  limiter_enabled_ = enable;
  return Configure();
}

void GainControlImpl::Initialize(size_t num_proc_channels, int sample_rate_hz) {
  data_dumper_->InitiateNewSetOfRecordings();

  RTC_DCHECK(sample_rate_hz == 16000 || sample_rate_hz == 32000 ||
             sample_rate_hz == 48000);

  num_proc_channels_ = num_proc_channels;
  sample_rate_hz_ = sample_rate_hz;

  mono_agcs_.resize(*num_proc_channels_);
  capture_levels_.resize(*num_proc_channels_);
  for (size_t ch = 0; ch < mono_agcs_.size(); ++ch) {
    if (!mono_agcs_[ch]) {
      mono_agcs_[ch].reset(new MonoAgcState());
    }

    int error = WebRtcAgc_Init(mono_agcs_[ch]->state, minimum_capture_level_,
                               maximum_capture_level_, MapSetting(mode_),
                               *sample_rate_hz_);
    RTC_DCHECK_EQ(error, 0);
    capture_levels_[ch] = analog_capture_level_;
  }

  Configure();
}

int GainControlImpl::Configure() {
  WebRtcAgcConfig config;
  // TODO(ajm): Flip the sign here (since AGC expects a positive value) if we
  //            change the interface.
  // RTC_DCHECK_LE(target_level_dbfs_, 0);
  // config.targetLevelDbfs = static_cast<int16_t>(-target_level_dbfs_);
  config.targetLevelDbfs = static_cast<int16_t>(target_level_dbfs_);
  config.compressionGaindB = static_cast<int16_t>(compression_gain_db_);
  config.limiterEnable = limiter_enabled_;

  int error = AudioProcessing::kNoError;
  for (size_t ch = 0; ch < mono_agcs_.size(); ++ch) {
    int error_ch = WebRtcAgc_set_config(mono_agcs_[ch]->state, config);
    if (error_ch != AudioProcessing::kNoError) {
      error = error_ch;
    }
  }
  return error;
}
}  // namespace webrtc
