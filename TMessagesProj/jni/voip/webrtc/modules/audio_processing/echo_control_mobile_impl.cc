/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/echo_control_mobile_impl.h"

#include <string.h>

#include <cstdint>

#include "modules/audio_processing/aecm/echo_control_mobile.h"
#include "modules/audio_processing/audio_buffer.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "rtc_base/checks.h"
#include "rtc_base/constructor_magic.h"

namespace webrtc {

namespace {
int16_t MapSetting(EchoControlMobileImpl::RoutingMode mode) {
  switch (mode) {
    case EchoControlMobileImpl::kQuietEarpieceOrHeadset:
      return 0;
    case EchoControlMobileImpl::kEarpiece:
      return 1;
    case EchoControlMobileImpl::kLoudEarpiece:
      return 2;
    case EchoControlMobileImpl::kSpeakerphone:
      return 3;
    case EchoControlMobileImpl::kLoudSpeakerphone:
      return 4;
  }
  RTC_DCHECK_NOTREACHED();
  return -1;
}

AudioProcessing::Error MapError(int err) {
  switch (err) {
    case AECM_UNSUPPORTED_FUNCTION_ERROR:
      return AudioProcessing::kUnsupportedFunctionError;
    case AECM_NULL_POINTER_ERROR:
      return AudioProcessing::kNullPointerError;
    case AECM_BAD_PARAMETER_ERROR:
      return AudioProcessing::kBadParameterError;
    case AECM_BAD_PARAMETER_WARNING:
      return AudioProcessing::kBadStreamParameterWarning;
    default:
      // AECM_UNSPECIFIED_ERROR
      // AECM_UNINITIALIZED_ERROR
      return AudioProcessing::kUnspecifiedError;
  }
}

}  // namespace

struct EchoControlMobileImpl::StreamProperties {
  StreamProperties() = delete;
  StreamProperties(int sample_rate_hz,
                   size_t num_reverse_channels,
                   size_t num_output_channels)
      : sample_rate_hz(sample_rate_hz),
        num_reverse_channels(num_reverse_channels),
        num_output_channels(num_output_channels) {}

  int sample_rate_hz;
  size_t num_reverse_channels;
  size_t num_output_channels;
};

class EchoControlMobileImpl::Canceller {
 public:
  Canceller() {
    state_ = WebRtcAecm_Create();
    RTC_CHECK(state_);
  }

  ~Canceller() {
    RTC_DCHECK(state_);
    WebRtcAecm_Free(state_);
  }

  void* state() {
    RTC_DCHECK(state_);
    return state_;
  }

  void Initialize(int sample_rate_hz) {
    RTC_DCHECK(state_);
    int error = WebRtcAecm_Init(state_, sample_rate_hz);
    RTC_DCHECK_EQ(AudioProcessing::kNoError, error);
  }

 private:
  void* state_;
  RTC_DISALLOW_COPY_AND_ASSIGN(Canceller);
};

EchoControlMobileImpl::EchoControlMobileImpl()
    : routing_mode_(kSpeakerphone), comfort_noise_enabled_(false) {}

EchoControlMobileImpl::~EchoControlMobileImpl() {}

void EchoControlMobileImpl::ProcessRenderAudio(
    rtc::ArrayView<const int16_t> packed_render_audio) {
  RTC_DCHECK(stream_properties_);

  size_t buffer_index = 0;
  size_t num_frames_per_band =
      packed_render_audio.size() / (stream_properties_->num_output_channels *
                                    stream_properties_->num_reverse_channels);

  for (auto& canceller : cancellers_) {
    WebRtcAecm_BufferFarend(canceller->state(),
                            &packed_render_audio[buffer_index],
                            num_frames_per_band);

    buffer_index += num_frames_per_band;
  }
}

void EchoControlMobileImpl::PackRenderAudioBuffer(
    const AudioBuffer* audio,
    size_t num_output_channels,
    size_t num_channels,
    std::vector<int16_t>* packed_buffer) {
  RTC_DCHECK_GE(AudioBuffer::kMaxSplitFrameLength,
                audio->num_frames_per_band());
  RTC_DCHECK_EQ(num_channels, audio->num_channels());

  // The ordering convention must be followed to pass to the correct AECM.
  packed_buffer->clear();
  int render_channel = 0;
  for (size_t i = 0; i < num_output_channels; i++) {
    for (size_t j = 0; j < audio->num_channels(); j++) {
      std::array<int16_t, AudioBuffer::kMaxSplitFrameLength> data_to_buffer;
      FloatS16ToS16(audio->split_bands_const(render_channel)[kBand0To8kHz],
                    audio->num_frames_per_band(), data_to_buffer.data());

      // Buffer the samples in the render queue.
      packed_buffer->insert(
          packed_buffer->end(), data_to_buffer.data(),
          data_to_buffer.data() + audio->num_frames_per_band());
      render_channel = (render_channel + 1) % audio->num_channels();
    }
  }
}

size_t EchoControlMobileImpl::NumCancellersRequired(
    size_t num_output_channels,
    size_t num_reverse_channels) {
  return num_output_channels * num_reverse_channels;
}

int EchoControlMobileImpl::ProcessCaptureAudio(AudioBuffer* audio,
                                               int stream_delay_ms) {
  RTC_DCHECK(stream_properties_);
  RTC_DCHECK_GE(160, audio->num_frames_per_band());
  RTC_DCHECK_EQ(audio->num_channels(), stream_properties_->num_output_channels);
  RTC_DCHECK_GE(cancellers_.size(), stream_properties_->num_reverse_channels *
                                        audio->num_channels());

  int err = AudioProcessing::kNoError;

  // The ordering convention must be followed to pass to the correct AECM.
  size_t handle_index = 0;
  for (size_t capture = 0; capture < audio->num_channels(); ++capture) {
    // TODO(ajm): improve how this works, possibly inside AECM.
    //            This is kind of hacked up.
    RTC_DCHECK_LT(capture, low_pass_reference_.size());
    const int16_t* noisy =
        reference_copied_ ? low_pass_reference_[capture].data() : nullptr;

    RTC_DCHECK_GE(AudioBuffer::kMaxSplitFrameLength,
                  audio->num_frames_per_band());

    std::array<int16_t, AudioBuffer::kMaxSplitFrameLength> split_bands_data;
    int16_t* split_bands = split_bands_data.data();
    const int16_t* clean = split_bands_data.data();
    if (audio->split_bands(capture)[kBand0To8kHz]) {
      FloatS16ToS16(audio->split_bands(capture)[kBand0To8kHz],
                    audio->num_frames_per_band(), split_bands_data.data());
    } else {
      clean = nullptr;
      split_bands = nullptr;
    }

    if (noisy == NULL) {
      noisy = clean;
      clean = NULL;
    }
    for (size_t render = 0; render < stream_properties_->num_reverse_channels;
         ++render) {
      err = WebRtcAecm_Process(cancellers_[handle_index]->state(), noisy, clean,
                               split_bands, audio->num_frames_per_band(),
                               stream_delay_ms);

      if (split_bands) {
        S16ToFloatS16(split_bands, audio->num_frames_per_band(),
                      audio->split_bands(capture)[kBand0To8kHz]);
      }

      if (err != AudioProcessing::kNoError) {
        return MapError(err);
      }

      ++handle_index;
    }
    for (size_t band = 1u; band < audio->num_bands(); ++band) {
      memset(audio->split_bands_f(capture)[band], 0,
             audio->num_frames_per_band() *
                 sizeof(audio->split_bands_f(capture)[band][0]));
    }
  }
  return AudioProcessing::kNoError;
}

int EchoControlMobileImpl::set_routing_mode(RoutingMode mode) {
  if (MapSetting(mode) == -1) {
    return AudioProcessing::kBadParameterError;
  }
  routing_mode_ = mode;
  return Configure();
}

EchoControlMobileImpl::RoutingMode EchoControlMobileImpl::routing_mode() const {
  return routing_mode_;
}

int EchoControlMobileImpl::enable_comfort_noise(bool enable) {
  comfort_noise_enabled_ = enable;
  return Configure();
}

bool EchoControlMobileImpl::is_comfort_noise_enabled() const {
  return comfort_noise_enabled_;
}

void EchoControlMobileImpl::Initialize(int sample_rate_hz,
                                       size_t num_reverse_channels,
                                       size_t num_output_channels) {
  low_pass_reference_.resize(num_output_channels);
  for (auto& reference : low_pass_reference_) {
    reference.fill(0);
  }

  stream_properties_.reset(new StreamProperties(
      sample_rate_hz, num_reverse_channels, num_output_channels));

  // AECM only supports 16 kHz or lower sample rates.
  RTC_DCHECK_LE(stream_properties_->sample_rate_hz,
                AudioProcessing::kSampleRate16kHz);

  cancellers_.resize(
      NumCancellersRequired(stream_properties_->num_output_channels,
                            stream_properties_->num_reverse_channels));

  for (auto& canceller : cancellers_) {
    if (!canceller) {
      canceller.reset(new Canceller());
    }
    canceller->Initialize(sample_rate_hz);
  }
  Configure();
}

int EchoControlMobileImpl::Configure() {
  AecmConfig config;
  config.cngMode = comfort_noise_enabled_;
  config.echoMode = MapSetting(routing_mode_);
  int error = AudioProcessing::kNoError;
  for (auto& canceller : cancellers_) {
    int handle_error = WebRtcAecm_set_config(canceller->state(), config);
    if (handle_error != AudioProcessing::kNoError) {
      error = handle_error;
    }
  }
  return error;
}

}  // namespace webrtc
