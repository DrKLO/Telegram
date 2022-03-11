/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/transient/transient_suppressor_impl.h"

#include <string.h>

#include <algorithm>
#include <cmath>
#include <complex>
#include <deque>
#include <limits>
#include <set>

#include "common_audio/include/audio_util.h"
#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "common_audio/third_party/ooura/fft_size_256/fft4g.h"
#include "modules/audio_processing/transient/common.h"
#include "modules/audio_processing/transient/transient_detector.h"
#include "modules/audio_processing/transient/transient_suppressor.h"
#include "modules/audio_processing/transient/windows_private.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

static const float kMeanIIRCoefficient = 0.5f;
static const float kVoiceThreshold = 0.02f;

// TODO(aluebs): Check if these values work also for 48kHz.
static const size_t kMinVoiceBin = 3;
static const size_t kMaxVoiceBin = 60;

namespace {

float ComplexMagnitude(float a, float b) {
  return std::abs(a) + std::abs(b);
}

}  // namespace

TransientSuppressorImpl::TransientSuppressorImpl()
    : data_length_(0),
      detection_length_(0),
      analysis_length_(0),
      buffer_delay_(0),
      complex_analysis_length_(0),
      num_channels_(0),
      window_(NULL),
      detector_smoothed_(0.f),
      keypress_counter_(0),
      chunks_since_keypress_(0),
      detection_enabled_(false),
      suppression_enabled_(false),
      use_hard_restoration_(false),
      chunks_since_voice_change_(0),
      seed_(182),
      using_reference_(false) {}

TransientSuppressorImpl::~TransientSuppressorImpl() {}

int TransientSuppressorImpl::Initialize(int sample_rate_hz,
                                        int detection_rate_hz,
                                        int num_channels) {
  switch (sample_rate_hz) {
    case ts::kSampleRate8kHz:
      analysis_length_ = 128u;
      window_ = kBlocks80w128;
      break;
    case ts::kSampleRate16kHz:
      analysis_length_ = 256u;
      window_ = kBlocks160w256;
      break;
    case ts::kSampleRate32kHz:
      analysis_length_ = 512u;
      window_ = kBlocks320w512;
      break;
    case ts::kSampleRate48kHz:
      analysis_length_ = 1024u;
      window_ = kBlocks480w1024;
      break;
    default:
      return -1;
  }
  if (detection_rate_hz != ts::kSampleRate8kHz &&
      detection_rate_hz != ts::kSampleRate16kHz &&
      detection_rate_hz != ts::kSampleRate32kHz &&
      detection_rate_hz != ts::kSampleRate48kHz) {
    return -1;
  }
  if (num_channels <= 0) {
    return -1;
  }

  detector_.reset(new TransientDetector(detection_rate_hz));
  data_length_ = sample_rate_hz * ts::kChunkSizeMs / 1000;
  if (data_length_ > analysis_length_) {
    RTC_DCHECK_NOTREACHED();
    return -1;
  }
  buffer_delay_ = analysis_length_ - data_length_;

  complex_analysis_length_ = analysis_length_ / 2 + 1;
  RTC_DCHECK_GE(complex_analysis_length_, kMaxVoiceBin);
  num_channels_ = num_channels;
  in_buffer_.reset(new float[analysis_length_ * num_channels_]);
  memset(in_buffer_.get(), 0,
         analysis_length_ * num_channels_ * sizeof(in_buffer_[0]));
  detection_length_ = detection_rate_hz * ts::kChunkSizeMs / 1000;
  detection_buffer_.reset(new float[detection_length_]);
  memset(detection_buffer_.get(), 0,
         detection_length_ * sizeof(detection_buffer_[0]));
  out_buffer_.reset(new float[analysis_length_ * num_channels_]);
  memset(out_buffer_.get(), 0,
         analysis_length_ * num_channels_ * sizeof(out_buffer_[0]));
  // ip[0] must be zero to trigger initialization using rdft().
  size_t ip_length = 2 + sqrtf(analysis_length_);
  ip_.reset(new size_t[ip_length]());
  memset(ip_.get(), 0, ip_length * sizeof(ip_[0]));
  wfft_.reset(new float[complex_analysis_length_ - 1]);
  memset(wfft_.get(), 0, (complex_analysis_length_ - 1) * sizeof(wfft_[0]));
  spectral_mean_.reset(new float[complex_analysis_length_ * num_channels_]);
  memset(spectral_mean_.get(), 0,
         complex_analysis_length_ * num_channels_ * sizeof(spectral_mean_[0]));
  fft_buffer_.reset(new float[analysis_length_ + 2]);
  memset(fft_buffer_.get(), 0, (analysis_length_ + 2) * sizeof(fft_buffer_[0]));
  magnitudes_.reset(new float[complex_analysis_length_]);
  memset(magnitudes_.get(), 0,
         complex_analysis_length_ * sizeof(magnitudes_[0]));
  mean_factor_.reset(new float[complex_analysis_length_]);

  static const float kFactorHeight = 10.f;
  static const float kLowSlope = 1.f;
  static const float kHighSlope = 0.3f;
  for (size_t i = 0; i < complex_analysis_length_; ++i) {
    mean_factor_[i] =
        kFactorHeight /
            (1.f + std::exp(kLowSlope * static_cast<int>(i - kMinVoiceBin))) +
        kFactorHeight /
            (1.f + std::exp(kHighSlope * static_cast<int>(kMaxVoiceBin - i)));
  }
  detector_smoothed_ = 0.f;
  keypress_counter_ = 0;
  chunks_since_keypress_ = 0;
  detection_enabled_ = false;
  suppression_enabled_ = false;
  use_hard_restoration_ = false;
  chunks_since_voice_change_ = 0;
  seed_ = 182;
  using_reference_ = false;
  return 0;
}

int TransientSuppressorImpl::Suppress(float* data,
                                      size_t data_length,
                                      int num_channels,
                                      const float* detection_data,
                                      size_t detection_length,
                                      const float* reference_data,
                                      size_t reference_length,
                                      float voice_probability,
                                      bool key_pressed) {
  if (!data || data_length != data_length_ || num_channels != num_channels_ ||
      detection_length != detection_length_ || voice_probability < 0 ||
      voice_probability > 1) {
    return -1;
  }

  UpdateKeypress(key_pressed);
  UpdateBuffers(data);

  int result = 0;
  if (detection_enabled_) {
    UpdateRestoration(voice_probability);

    if (!detection_data) {
      // Use the input data  of the first channel if special detection data is
      // not supplied.
      detection_data = &in_buffer_[buffer_delay_];
    }

    float detector_result = detector_->Detect(detection_data, detection_length,
                                              reference_data, reference_length);
    if (detector_result < 0) {
      return -1;
    }

    using_reference_ = detector_->using_reference();

    // `detector_smoothed_` follows the `detector_result` when this last one is
    // increasing, but has an exponential decaying tail to be able to suppress
    // the ringing of keyclicks.
    float smooth_factor = using_reference_ ? 0.6 : 0.1;
    detector_smoothed_ = detector_result >= detector_smoothed_
                             ? detector_result
                             : smooth_factor * detector_smoothed_ +
                                   (1 - smooth_factor) * detector_result;

    for (int i = 0; i < num_channels_; ++i) {
      Suppress(&in_buffer_[i * analysis_length_],
               &spectral_mean_[i * complex_analysis_length_],
               &out_buffer_[i * analysis_length_]);
    }
  }

  // If the suppression isn't enabled, we use the in buffer to delay the signal
  // appropriately. This also gives time for the out buffer to be refreshed with
  // new data between detection and suppression getting enabled.
  for (int i = 0; i < num_channels_; ++i) {
    memcpy(&data[i * data_length_],
           suppression_enabled_ ? &out_buffer_[i * analysis_length_]
                                : &in_buffer_[i * analysis_length_],
           data_length_ * sizeof(*data));
  }
  return result;
}

// This should only be called when detection is enabled. UpdateBuffers() must
// have been called. At return, `out_buffer_` will be filled with the
// processed output.
void TransientSuppressorImpl::Suppress(float* in_ptr,
                                       float* spectral_mean,
                                       float* out_ptr) {
  // Go to frequency domain.
  for (size_t i = 0; i < analysis_length_; ++i) {
    // TODO(aluebs): Rename windows
    fft_buffer_[i] = in_ptr[i] * window_[i];
  }

  WebRtc_rdft(analysis_length_, 1, fft_buffer_.get(), ip_.get(), wfft_.get());

  // Since WebRtc_rdft puts R[n/2] in fft_buffer_[1], we move it to the end
  // for convenience.
  fft_buffer_[analysis_length_] = fft_buffer_[1];
  fft_buffer_[analysis_length_ + 1] = 0.f;
  fft_buffer_[1] = 0.f;

  for (size_t i = 0; i < complex_analysis_length_; ++i) {
    magnitudes_[i] =
        ComplexMagnitude(fft_buffer_[i * 2], fft_buffer_[i * 2 + 1]);
  }
  // Restore audio if necessary.
  if (suppression_enabled_) {
    if (use_hard_restoration_) {
      HardRestoration(spectral_mean);
    } else {
      SoftRestoration(spectral_mean);
    }
  }

  // Update the spectral mean.
  for (size_t i = 0; i < complex_analysis_length_; ++i) {
    spectral_mean[i] = (1 - kMeanIIRCoefficient) * spectral_mean[i] +
                       kMeanIIRCoefficient * magnitudes_[i];
  }

  // Back to time domain.
  // Put R[n/2] back in fft_buffer_[1].
  fft_buffer_[1] = fft_buffer_[analysis_length_];

  WebRtc_rdft(analysis_length_, -1, fft_buffer_.get(), ip_.get(), wfft_.get());
  const float fft_scaling = 2.f / analysis_length_;

  for (size_t i = 0; i < analysis_length_; ++i) {
    out_ptr[i] += fft_buffer_[i] * window_[i] * fft_scaling;
  }
}

void TransientSuppressorImpl::UpdateKeypress(bool key_pressed) {
  const int kKeypressPenalty = 1000 / ts::kChunkSizeMs;
  const int kIsTypingThreshold = 1000 / ts::kChunkSizeMs;
  const int kChunksUntilNotTyping = 4000 / ts::kChunkSizeMs;  // 4 seconds.

  if (key_pressed) {
    keypress_counter_ += kKeypressPenalty;
    chunks_since_keypress_ = 0;
    detection_enabled_ = true;
  }
  keypress_counter_ = std::max(0, keypress_counter_ - 1);

  if (keypress_counter_ > kIsTypingThreshold) {
    if (!suppression_enabled_) {
      RTC_LOG(LS_INFO) << "[ts] Transient suppression is now enabled.";
    }
    suppression_enabled_ = true;
    keypress_counter_ = 0;
  }

  if (detection_enabled_ && ++chunks_since_keypress_ > kChunksUntilNotTyping) {
    if (suppression_enabled_) {
      RTC_LOG(LS_INFO) << "[ts] Transient suppression is now disabled.";
    }
    detection_enabled_ = false;
    suppression_enabled_ = false;
    keypress_counter_ = 0;
  }
}

void TransientSuppressorImpl::UpdateRestoration(float voice_probability) {
  const int kHardRestorationOffsetDelay = 3;
  const int kHardRestorationOnsetDelay = 80;

  bool not_voiced = voice_probability < kVoiceThreshold;

  if (not_voiced == use_hard_restoration_) {
    chunks_since_voice_change_ = 0;
  } else {
    ++chunks_since_voice_change_;

    if ((use_hard_restoration_ &&
         chunks_since_voice_change_ > kHardRestorationOffsetDelay) ||
        (!use_hard_restoration_ &&
         chunks_since_voice_change_ > kHardRestorationOnsetDelay)) {
      use_hard_restoration_ = not_voiced;
      chunks_since_voice_change_ = 0;
    }
  }
}

// Shift buffers to make way for new data. Must be called after
// `detection_enabled_` is updated by UpdateKeypress().
void TransientSuppressorImpl::UpdateBuffers(float* data) {
  // TODO(aluebs): Change to ring buffer.
  memmove(in_buffer_.get(), &in_buffer_[data_length_],
          (buffer_delay_ + (num_channels_ - 1) * analysis_length_) *
              sizeof(in_buffer_[0]));
  // Copy new chunk to buffer.
  for (int i = 0; i < num_channels_; ++i) {
    memcpy(&in_buffer_[buffer_delay_ + i * analysis_length_],
           &data[i * data_length_], data_length_ * sizeof(*data));
  }
  if (detection_enabled_) {
    // Shift previous chunk in out buffer.
    memmove(out_buffer_.get(), &out_buffer_[data_length_],
            (buffer_delay_ + (num_channels_ - 1) * analysis_length_) *
                sizeof(out_buffer_[0]));
    // Initialize new chunk in out buffer.
    for (int i = 0; i < num_channels_; ++i) {
      memset(&out_buffer_[buffer_delay_ + i * analysis_length_], 0,
             data_length_ * sizeof(out_buffer_[0]));
    }
  }
}

// Restores the unvoiced signal if a click is present.
// Attenuates by a certain factor every peak in the `fft_buffer_` that exceeds
// the spectral mean. The attenuation depends on `detector_smoothed_`.
// If a restoration takes place, the `magnitudes_` are updated to the new value.
void TransientSuppressorImpl::HardRestoration(float* spectral_mean) {
  const float detector_result =
      1.f - std::pow(1.f - detector_smoothed_, using_reference_ ? 200.f : 50.f);
  // To restore, we get the peaks in the spectrum. If higher than the previous
  // spectral mean we adjust them.
  for (size_t i = 0; i < complex_analysis_length_; ++i) {
    if (magnitudes_[i] > spectral_mean[i] && magnitudes_[i] > 0) {
      // RandU() generates values on [0, int16::max()]
      const float phase = 2 * ts::kPi * WebRtcSpl_RandU(&seed_) /
                          std::numeric_limits<int16_t>::max();
      const float scaled_mean = detector_result * spectral_mean[i];

      fft_buffer_[i * 2] = (1 - detector_result) * fft_buffer_[i * 2] +
                           scaled_mean * cosf(phase);
      fft_buffer_[i * 2 + 1] = (1 - detector_result) * fft_buffer_[i * 2 + 1] +
                               scaled_mean * sinf(phase);
      magnitudes_[i] = magnitudes_[i] -
                       detector_result * (magnitudes_[i] - spectral_mean[i]);
    }
  }
}

// Restores the voiced signal if a click is present.
// Attenuates by a certain factor every peak in the `fft_buffer_` that exceeds
// the spectral mean and that is lower than some function of the current block
// frequency mean. The attenuation depends on `detector_smoothed_`.
// If a restoration takes place, the `magnitudes_` are updated to the new value.
void TransientSuppressorImpl::SoftRestoration(float* spectral_mean) {
  // Get the spectral magnitude mean of the current block.
  float block_frequency_mean = 0;
  for (size_t i = kMinVoiceBin; i < kMaxVoiceBin; ++i) {
    block_frequency_mean += magnitudes_[i];
  }
  block_frequency_mean /= (kMaxVoiceBin - kMinVoiceBin);

  // To restore, we get the peaks in the spectrum. If higher than the
  // previous spectral mean and lower than a factor of the block mean
  // we adjust them. The factor is a double sigmoid that has a minimum in the
  // voice frequency range (300Hz - 3kHz).
  for (size_t i = 0; i < complex_analysis_length_; ++i) {
    if (magnitudes_[i] > spectral_mean[i] && magnitudes_[i] > 0 &&
        (using_reference_ ||
         magnitudes_[i] < block_frequency_mean * mean_factor_[i])) {
      const float new_magnitude =
          magnitudes_[i] -
          detector_smoothed_ * (magnitudes_[i] - spectral_mean[i]);
      const float magnitude_ratio = new_magnitude / magnitudes_[i];

      fft_buffer_[i * 2] *= magnitude_ratio;
      fft_buffer_[i * 2 + 1] *= magnitude_ratio;
      magnitudes_[i] = new_magnitude;
    }
  }
}

}  // namespace webrtc
