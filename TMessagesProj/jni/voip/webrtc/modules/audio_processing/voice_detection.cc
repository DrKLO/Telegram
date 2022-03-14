/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/voice_detection.h"

#include "common_audio/vad/include/webrtc_vad.h"
#include "modules/audio_processing/audio_buffer.h"
#include "rtc_base/checks.h"

namespace webrtc {
class VoiceDetection::Vad {
 public:
  Vad() {
    state_ = WebRtcVad_Create();
    RTC_CHECK(state_);
    int error = WebRtcVad_Init(state_);
    RTC_DCHECK_EQ(0, error);
  }
  ~Vad() { WebRtcVad_Free(state_); }

  Vad(Vad&) = delete;
  Vad& operator=(Vad&) = delete;

  VadInst* state() { return state_; }

 private:
  VadInst* state_ = nullptr;
};

VoiceDetection::VoiceDetection(int sample_rate_hz, Likelihood likelihood)
    : sample_rate_hz_(sample_rate_hz),
      frame_size_samples_(static_cast<size_t>(sample_rate_hz_ / 100)),
      likelihood_(likelihood),
      vad_(new Vad()) {
  int mode = 2;
  switch (likelihood) {
    case VoiceDetection::kVeryLowLikelihood:
      mode = 3;
      break;
    case VoiceDetection::kLowLikelihood:
      mode = 2;
      break;
    case VoiceDetection::kModerateLikelihood:
      mode = 1;
      break;
    case VoiceDetection::kHighLikelihood:
      mode = 0;
      break;
    default:
      RTC_DCHECK_NOTREACHED();
      break;
  }
  int error = WebRtcVad_set_mode(vad_->state(), mode);
  RTC_DCHECK_EQ(0, error);
}

VoiceDetection::~VoiceDetection() {}

bool VoiceDetection::ProcessCaptureAudio(AudioBuffer* audio) {
  RTC_DCHECK_GE(AudioBuffer::kMaxSplitFrameLength,
                audio->num_frames_per_band());
  std::array<int16_t, AudioBuffer::kMaxSplitFrameLength> mixed_low_pass_data;
  rtc::ArrayView<const int16_t> mixed_low_pass(mixed_low_pass_data.data(),
                                               audio->num_frames_per_band());
  if (audio->num_channels() == 1) {
    FloatS16ToS16(audio->split_bands_const(0)[kBand0To8kHz],
                  audio->num_frames_per_band(), mixed_low_pass_data.data());
  } else {
    const int num_channels = static_cast<int>(audio->num_channels());
    for (size_t i = 0; i < audio->num_frames_per_band(); ++i) {
      int32_t value =
          FloatS16ToS16(audio->split_channels_const(kBand0To8kHz)[0][i]);
      for (int j = 1; j < num_channels; ++j) {
        value += FloatS16ToS16(audio->split_channels_const(kBand0To8kHz)[j][i]);
      }
      mixed_low_pass_data[i] = value / num_channels;
    }
  }

  int vad_ret = WebRtcVad_Process(vad_->state(), sample_rate_hz_,
                                  mixed_low_pass.data(), frame_size_samples_);
  RTC_DCHECK(vad_ret == 0 || vad_ret == 1);
  return vad_ret == 0 ? false : true;
}
}  // namespace webrtc
