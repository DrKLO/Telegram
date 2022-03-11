/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_audio/vad/include/vad.h"

#include <memory>

#include "common_audio/vad/include/webrtc_vad.h"
#include "rtc_base/checks.h"

namespace webrtc {

namespace {

class VadImpl final : public Vad {
 public:
  explicit VadImpl(Aggressiveness aggressiveness)
      : handle_(nullptr), aggressiveness_(aggressiveness) {
    Reset();
  }

  ~VadImpl() override { WebRtcVad_Free(handle_); }

  Activity VoiceActivity(const int16_t* audio,
                         size_t num_samples,
                         int sample_rate_hz) override {
    int ret = WebRtcVad_Process(handle_, sample_rate_hz, audio, num_samples);
    switch (ret) {
      case 0:
        return kPassive;
      case 1:
        return kActive;
      default:
        RTC_DCHECK_NOTREACHED() << "WebRtcVad_Process returned an error.";
        return kError;
    }
  }

  void Reset() override {
    if (handle_)
      WebRtcVad_Free(handle_);
    handle_ = WebRtcVad_Create();
    RTC_CHECK(handle_);
    RTC_CHECK_EQ(WebRtcVad_Init(handle_), 0);
    RTC_CHECK_EQ(WebRtcVad_set_mode(handle_, aggressiveness_), 0);
  }

 private:
  VadInst* handle_;
  Aggressiveness aggressiveness_;
};

}  // namespace

std::unique_ptr<Vad> CreateVad(Vad::Aggressiveness aggressiveness) {
  return std::unique_ptr<Vad>(new VadImpl(aggressiveness));
}

}  // namespace webrtc
