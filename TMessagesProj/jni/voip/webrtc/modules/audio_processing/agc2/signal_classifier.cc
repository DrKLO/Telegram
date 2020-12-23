/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/signal_classifier.h"

#include <algorithm>
#include <numeric>
#include <vector>

#include "api/array_view.h"
#include "modules/audio_processing/agc2/down_sampler.h"
#include "modules/audio_processing/agc2/noise_spectrum_estimator.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/checks.h"
#include "system_wrappers/include/cpu_features_wrapper.h"

namespace webrtc {
namespace {

bool IsSse2Available() {
#if defined(WEBRTC_ARCH_X86_FAMILY)
  return GetCPUInfo(kSSE2) != 0;
#else
  return false;
#endif
}

void RemoveDcLevel(rtc::ArrayView<float> x) {
  RTC_DCHECK_LT(0, x.size());
  float mean = std::accumulate(x.data(), x.data() + x.size(), 0.f);
  mean /= x.size();

  for (float& v : x) {
    v -= mean;
  }
}

void PowerSpectrum(const OouraFft* ooura_fft,
                   rtc::ArrayView<const float> x,
                   rtc::ArrayView<float> spectrum) {
  RTC_DCHECK_EQ(65, spectrum.size());
  RTC_DCHECK_EQ(128, x.size());
  float X[128];
  std::copy(x.data(), x.data() + x.size(), X);
  ooura_fft->Fft(X);

  float* X_p = X;
  RTC_DCHECK_EQ(X_p, &X[0]);
  spectrum[0] = (*X_p) * (*X_p);
  ++X_p;
  RTC_DCHECK_EQ(X_p, &X[1]);
  spectrum[64] = (*X_p) * (*X_p);
  for (int k = 1; k < 64; ++k) {
    ++X_p;
    RTC_DCHECK_EQ(X_p, &X[2 * k]);
    spectrum[k] = (*X_p) * (*X_p);
    ++X_p;
    RTC_DCHECK_EQ(X_p, &X[2 * k + 1]);
    spectrum[k] += (*X_p) * (*X_p);
  }
}

webrtc::SignalClassifier::SignalType ClassifySignal(
    rtc::ArrayView<const float> signal_spectrum,
    rtc::ArrayView<const float> noise_spectrum,
    ApmDataDumper* data_dumper) {
  int num_stationary_bands = 0;
  int num_highly_nonstationary_bands = 0;

  // Detect stationary and highly nonstationary bands.
  for (size_t k = 1; k < 40; k++) {
    if (signal_spectrum[k] < 3 * noise_spectrum[k] &&
        signal_spectrum[k] * 3 > noise_spectrum[k]) {
      ++num_stationary_bands;
    } else if (signal_spectrum[k] > 9 * noise_spectrum[k]) {
      ++num_highly_nonstationary_bands;
    }
  }

  data_dumper->DumpRaw("lc_num_stationary_bands", 1, &num_stationary_bands);
  data_dumper->DumpRaw("lc_num_highly_nonstationary_bands", 1,
                       &num_highly_nonstationary_bands);

  // Use the detected number of bands to classify the overall signal
  // stationarity.
  if (num_stationary_bands > 15) {
    return SignalClassifier::SignalType::kStationary;
  } else {
    return SignalClassifier::SignalType::kNonStationary;
  }
}

}  // namespace

SignalClassifier::FrameExtender::FrameExtender(size_t frame_size,
                                               size_t extended_frame_size)
    : x_old_(extended_frame_size - frame_size, 0.f) {}

SignalClassifier::FrameExtender::~FrameExtender() = default;

void SignalClassifier::FrameExtender::ExtendFrame(
    rtc::ArrayView<const float> x,
    rtc::ArrayView<float> x_extended) {
  RTC_DCHECK_EQ(x_old_.size() + x.size(), x_extended.size());
  std::copy(x_old_.data(), x_old_.data() + x_old_.size(), x_extended.data());
  std::copy(x.data(), x.data() + x.size(), x_extended.data() + x_old_.size());
  std::copy(x_extended.data() + x_extended.size() - x_old_.size(),
            x_extended.data() + x_extended.size(), x_old_.data());
}

SignalClassifier::SignalClassifier(ApmDataDumper* data_dumper)
    : data_dumper_(data_dumper),
      down_sampler_(data_dumper_),
      noise_spectrum_estimator_(data_dumper_),
      ooura_fft_(IsSse2Available()) {
  Initialize(48000);
}
SignalClassifier::~SignalClassifier() {}

void SignalClassifier::Initialize(int sample_rate_hz) {
  down_sampler_.Initialize(sample_rate_hz);
  noise_spectrum_estimator_.Initialize();
  frame_extender_.reset(new FrameExtender(80, 128));
  sample_rate_hz_ = sample_rate_hz;
  initialization_frames_left_ = 2;
  consistent_classification_counter_ = 3;
  last_signal_type_ = SignalClassifier::SignalType::kNonStationary;
}

SignalClassifier::SignalType SignalClassifier::Analyze(
    rtc::ArrayView<const float> signal) {
  RTC_DCHECK_EQ(signal.size(), sample_rate_hz_ / 100);

  // Compute the signal power spectrum.
  float downsampled_frame[80];
  down_sampler_.DownSample(signal, downsampled_frame);
  float extended_frame[128];
  frame_extender_->ExtendFrame(downsampled_frame, extended_frame);
  RemoveDcLevel(extended_frame);
  float signal_spectrum[65];
  PowerSpectrum(&ooura_fft_, extended_frame, signal_spectrum);

  // Classify the signal based on the estimate of the noise spectrum and the
  // signal spectrum estimate.
  const SignalType signal_type = ClassifySignal(
      signal_spectrum, noise_spectrum_estimator_.GetNoiseSpectrum(),
      data_dumper_);

  // Update the noise spectrum based on the signal spectrum.
  noise_spectrum_estimator_.Update(signal_spectrum,
                                   initialization_frames_left_ > 0);

  // Update the number of frames until a reliable signal spectrum is achieved.
  initialization_frames_left_ = std::max(0, initialization_frames_left_ - 1);

  if (last_signal_type_ == signal_type) {
    consistent_classification_counter_ =
        std::max(0, consistent_classification_counter_ - 1);
  } else {
    last_signal_type_ = signal_type;
    consistent_classification_counter_ = 3;
  }

  if (consistent_classification_counter_ > 0) {
    return SignalClassifier::SignalType::kNonStationary;
  }
  return signal_type;
}

}  // namespace webrtc
