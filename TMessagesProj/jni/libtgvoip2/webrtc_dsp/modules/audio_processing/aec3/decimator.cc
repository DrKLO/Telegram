/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/audio_processing/aec3/decimator.h"

#include <array>
#include <vector>

#include "modules/audio_processing/aec3/aec3_common.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {

// signal.butter(2, 3400/8000.0, 'lowpass', analog=False)
const std::vector<CascadedBiQuadFilter::BiQuadParam> GetLowPassFilterDS2() {
  return std::vector<CascadedBiQuadFilter::BiQuadParam>{
      {{-1.f, 0.f}, {0.13833231f, 0.40743176f}, 0.22711796393486466f},
      {{-1.f, 0.f}, {0.13833231f, 0.40743176f}, 0.22711796393486466f},
      {{-1.f, 0.f}, {0.13833231f, 0.40743176f}, 0.22711796393486466f}};
}

// signal.ellip(6, 1, 40, 1800/8000, btype='lowpass', analog=False)
const std::vector<CascadedBiQuadFilter::BiQuadParam> GetLowPassFilterDS4() {
  return std::vector<CascadedBiQuadFilter::BiQuadParam>{
      {{-0.08873842f, 0.99605496f}, {0.75916227f, 0.23841065f}, 0.26250696827f},
      {{0.62273832f, 0.78243018f}, {0.74892112f, 0.5410152f}, 0.26250696827f},
      {{0.71107693f, 0.70311421f}, {0.74895534f, 0.63924616f}, 0.26250696827f}};
}

// signal.cheby1(1, 6, [1000/8000, 2000/8000], btype='bandpass', analog=False)
const std::vector<CascadedBiQuadFilter::BiQuadParam> GetBandPassFilterDS8() {
  return std::vector<CascadedBiQuadFilter::BiQuadParam>{
      {{1.f, 0.f}, {0.7601815f, 0.46423542f}, 0.10330478266505948f, true},
      {{1.f, 0.f}, {0.7601815f, 0.46423542f}, 0.10330478266505948f, true},
      {{1.f, 0.f}, {0.7601815f, 0.46423542f}, 0.10330478266505948f, true},
      {{1.f, 0.f}, {0.7601815f, 0.46423542f}, 0.10330478266505948f, true},
      {{1.f, 0.f}, {0.7601815f, 0.46423542f}, 0.10330478266505948f, true}};
}

// signal.butter(2, 1000/8000.0, 'highpass', analog=False)
const std::vector<CascadedBiQuadFilter::BiQuadParam> GetHighPassFilter() {
  return std::vector<CascadedBiQuadFilter::BiQuadParam>{
      {{1.f, 0.f}, {0.72712179f, 0.21296904f}, 0.7570763753338849f}};
}

const std::vector<CascadedBiQuadFilter::BiQuadParam> GetPassThroughFilter() {
  return std::vector<CascadedBiQuadFilter::BiQuadParam>{};
}
}  // namespace

Decimator::Decimator(size_t down_sampling_factor)
    : down_sampling_factor_(down_sampling_factor),
      anti_aliasing_filter_(down_sampling_factor_ == 4
                                ? GetLowPassFilterDS4()
                                : (down_sampling_factor_ == 8
                                       ? GetBandPassFilterDS8()
                                       : GetLowPassFilterDS2())),
      noise_reduction_filter_(down_sampling_factor_ == 8
                                  ? GetPassThroughFilter()
                                  : GetHighPassFilter()) {
  RTC_DCHECK(down_sampling_factor_ == 2 || down_sampling_factor_ == 4 ||
             down_sampling_factor_ == 8);
}

void Decimator::Decimate(rtc::ArrayView<const float> in,
                         rtc::ArrayView<float> out) {
  RTC_DCHECK_EQ(kBlockSize, in.size());
  RTC_DCHECK_EQ(kBlockSize / down_sampling_factor_, out.size());
  std::array<float, kBlockSize> x;

  // Limit the frequency content of the signal to avoid aliasing.
  anti_aliasing_filter_.Process(in, x);

  // Reduce the impact of near-end noise.
  noise_reduction_filter_.Process(x);

  // Downsample the signal.
  for (size_t j = 0, k = 0; j < out.size(); ++j, k += down_sampling_factor_) {
    RTC_DCHECK_GT(kBlockSize, k);
    out[j] = x[k];
  }
}

}  // namespace webrtc
