/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC3_ADAPTIVE_FIR_FILTER_H_
#define MODULES_AUDIO_PROCESSING_AEC3_ADAPTIVE_FIR_FILTER_H_

#include <stddef.h>
#include <array>
#include <vector>

#include "api/array_view.h"
#include "modules/audio_processing/aec3/aec3_common.h"
#include "modules/audio_processing/aec3/aec3_fft.h"
#include "modules/audio_processing/aec3/fft_data.h"
#include "modules/audio_processing/aec3/render_buffer.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/constructormagic.h"
#include "rtc_base/system/arch.h"

namespace webrtc {
namespace aec3 {
// Computes and stores the frequency response of the filter.
void UpdateFrequencyResponse(
    rtc::ArrayView<const FftData> H,
    std::vector<std::array<float, kFftLengthBy2Plus1>>* H2);
#if defined(WEBRTC_HAS_NEON)
void UpdateFrequencyResponse_NEON(
    rtc::ArrayView<const FftData> H,
    std::vector<std::array<float, kFftLengthBy2Plus1>>* H2);
#endif
#if defined(WEBRTC_ARCH_X86_FAMILY)
void UpdateFrequencyResponse_SSE2(
    rtc::ArrayView<const FftData> H,
    std::vector<std::array<float, kFftLengthBy2Plus1>>* H2);
#endif

// Computes and stores the echo return loss estimate of the filter, which is the
// sum of the partition frequency responses.
void UpdateErlEstimator(
    const std::vector<std::array<float, kFftLengthBy2Plus1>>& H2,
    std::array<float, kFftLengthBy2Plus1>* erl);
#if defined(WEBRTC_HAS_NEON)
void UpdateErlEstimator_NEON(
    const std::vector<std::array<float, kFftLengthBy2Plus1>>& H2,
    std::array<float, kFftLengthBy2Plus1>* erl);
#endif
#if defined(WEBRTC_ARCH_X86_FAMILY)
void UpdateErlEstimator_SSE2(
    const std::vector<std::array<float, kFftLengthBy2Plus1>>& H2,
    std::array<float, kFftLengthBy2Plus1>* erl);
#endif

// Adapts the filter partitions.
void AdaptPartitions(const RenderBuffer& render_buffer,
                     const FftData& G,
                     rtc::ArrayView<FftData> H);
#if defined(WEBRTC_HAS_NEON)
void AdaptPartitions_NEON(const RenderBuffer& render_buffer,
                          const FftData& G,
                          rtc::ArrayView<FftData> H);
#endif
#if defined(WEBRTC_ARCH_X86_FAMILY)
void AdaptPartitions_SSE2(const RenderBuffer& render_buffer,
                          const FftData& G,
                          rtc::ArrayView<FftData> H);
#endif

// Produces the filter output.
void ApplyFilter(const RenderBuffer& render_buffer,
                 rtc::ArrayView<const FftData> H,
                 FftData* S);
#if defined(WEBRTC_HAS_NEON)
void ApplyFilter_NEON(const RenderBuffer& render_buffer,
                      rtc::ArrayView<const FftData> H,
                      FftData* S);
#endif
#if defined(WEBRTC_ARCH_X86_FAMILY)
void ApplyFilter_SSE2(const RenderBuffer& render_buffer,
                      rtc::ArrayView<const FftData> H,
                      FftData* S);
#endif

}  // namespace aec3

// Provides a frequency domain adaptive filter functionality.
class AdaptiveFirFilter {
 public:
  AdaptiveFirFilter(size_t max_size_partitions,
                    size_t initial_size_partitions,
                    size_t size_change_duration_blocks,
                    Aec3Optimization optimization,
                    ApmDataDumper* data_dumper);

  ~AdaptiveFirFilter();

  // Produces the output of the filter.
  void Filter(const RenderBuffer& render_buffer, FftData* S) const;

  // Adapts the filter.
  void Adapt(const RenderBuffer& render_buffer, const FftData& G);

  // Receives reports that known echo path changes have occured and adjusts
  // the filter adaptation accordingly.
  void HandleEchoPathChange();

  // Returns the filter size.
  size_t SizePartitions() const { return H_.size(); }

  // Sets the filter size.
  void SetSizePartitions(size_t size, bool immediate_effect);

  // Returns the filter based echo return loss.
  const std::array<float, kFftLengthBy2Plus1>& Erl() const { return erl_; }

  // Returns the frequency responses for the filter partitions.
  const std::vector<std::array<float, kFftLengthBy2Plus1>>&
  FilterFrequencyResponse() const {
    return H2_;
  }

  // Returns the estimate of the impulse response.
  const std::vector<float>& FilterImpulseResponse() const { return h_; }

  void DumpFilter(const char* name_frequency_domain,
                  const char* name_time_domain) {
    size_t current_size = H_.size();
    H_.resize(max_size_partitions_);
    for (auto& H : H_) {
      data_dumper_->DumpRaw(name_frequency_domain, H.re);
      data_dumper_->DumpRaw(name_frequency_domain, H.im);
    }
    H_.resize(current_size);

    current_size = h_.size();
    h_.resize(GetTimeDomainLength(max_size_partitions_));
    data_dumper_->DumpRaw(name_time_domain, h_);
    h_.resize(current_size);
  }

  // Scale the filter impulse response and spectrum by a factor.
  void ScaleFilter(float factor);

  // Set the filter coefficients.
  void SetFilter(const std::vector<FftData>& H);

  // Gets the filter coefficients.
  const std::vector<FftData>& GetFilter() const { return H_; }

 private:
  // Constrain the filter partitions in a cyclic manner.
  void Constrain();

  // Resets the filter buffers to use the current size.
  void ResetFilterBuffersToCurrentSize();

  // Gradually Updates the current filter size towards the target size.
  void UpdateSize();

  ApmDataDumper* const data_dumper_;
  const bool use_partial_filter_reset_;
  const Aec3Fft fft_;
  const Aec3Optimization optimization_;
  const size_t max_size_partitions_;
  const int size_change_duration_blocks_;
  float one_by_size_change_duration_blocks_;
  size_t current_size_partitions_;
  size_t target_size_partitions_;
  size_t old_target_size_partitions_;
  int size_change_counter_ = 0;
  std::vector<FftData> H_;
  std::vector<std::array<float, kFftLengthBy2Plus1>> H2_;
  std::vector<float> h_;
  std::array<float, kFftLengthBy2Plus1> erl_;
  size_t partition_to_constrain_ = 0;

  RTC_DISALLOW_IMPLICIT_CONSTRUCTORS(AdaptiveFirFilter);
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC3_ADAPTIVE_FIR_FILTER_H_
