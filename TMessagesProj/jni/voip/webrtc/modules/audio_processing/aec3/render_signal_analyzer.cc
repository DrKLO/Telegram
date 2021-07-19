/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/render_signal_analyzer.h"

#include <math.h>

#include <algorithm>
#include <utility>
#include <vector>

#include "api/array_view.h"
#include "rtc_base/checks.h"

namespace webrtc {

namespace {
constexpr size_t kCounterThreshold = 5;

// Identifies local bands with narrow characteristics.
void IdentifySmallNarrowBandRegions(
    const RenderBuffer& render_buffer,
    const absl::optional<size_t>& delay_partitions,
    std::array<size_t, kFftLengthBy2 - 1>* narrow_band_counters) {
  RTC_DCHECK(narrow_band_counters);

  if (!delay_partitions) {
    narrow_band_counters->fill(0);
    return;
  }

  std::array<size_t, kFftLengthBy2 - 1> channel_counters;
  channel_counters.fill(0);
  rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> X2 =
      render_buffer.Spectrum(*delay_partitions);
  for (size_t ch = 0; ch < X2.size(); ++ch) {
    for (size_t k = 1; k < kFftLengthBy2; ++k) {
      if (X2[ch][k] > 3 * std::max(X2[ch][k - 1], X2[ch][k + 1])) {
        ++channel_counters[k - 1];
      }
    }
  }
  for (size_t k = 1; k < kFftLengthBy2; ++k) {
    (*narrow_band_counters)[k - 1] =
        channel_counters[k - 1] > 0 ? (*narrow_band_counters)[k - 1] + 1 : 0;
  }
}

// Identifies whether the signal has a single strong narrow-band component.
void IdentifyStrongNarrowBandComponent(const RenderBuffer& render_buffer,
                                       int strong_peak_freeze_duration,
                                       absl::optional<int>* narrow_peak_band,
                                       size_t* narrow_peak_counter) {
  RTC_DCHECK(narrow_peak_band);
  RTC_DCHECK(narrow_peak_counter);
  if (*narrow_peak_band &&
      ++(*narrow_peak_counter) >
          static_cast<size_t>(strong_peak_freeze_duration)) {
    *narrow_peak_band = absl::nullopt;
  }

  const std::vector<std::vector<std::vector<float>>>& x_latest =
      render_buffer.Block(0);
  float max_peak_level = 0.f;
  for (size_t channel = 0; channel < x_latest[0].size(); ++channel) {
    rtc::ArrayView<const float, kFftLengthBy2Plus1> X2_latest =
        render_buffer.Spectrum(0)[channel];

    // Identify the spectral peak.
    const int peak_bin =
        static_cast<int>(std::max_element(X2_latest.begin(), X2_latest.end()) -
                         X2_latest.begin());

    // Compute the level around the peak.
    float non_peak_power = 0.f;
    for (int k = std::max(0, peak_bin - 14); k < peak_bin - 4; ++k) {
      non_peak_power = std::max(X2_latest[k], non_peak_power);
    }
    for (int k = peak_bin + 5;
         k < std::min(peak_bin + 15, static_cast<int>(kFftLengthBy2Plus1));
         ++k) {
      non_peak_power = std::max(X2_latest[k], non_peak_power);
    }

    // Assess the render signal strength.
    auto result0 = std::minmax_element(x_latest[0][channel].begin(),
                                       x_latest[0][channel].end());
    float max_abs = std::max(fabs(*result0.first), fabs(*result0.second));

    if (x_latest.size() > 1) {
      const auto result1 = std::minmax_element(x_latest[1][channel].begin(),
                                               x_latest[1][channel].end());
      max_abs =
          std::max(max_abs, static_cast<float>(std::max(
                                fabs(*result1.first), fabs(*result1.second))));
    }

    // Detect whether the spectral peak has as strong narrowband nature.
    const float peak_level = X2_latest[peak_bin];
    if (peak_bin > 0 && max_abs > 100 && peak_level > 100 * non_peak_power) {
      // Store the strongest peak across channels.
      if (peak_level > max_peak_level) {
        max_peak_level = peak_level;
        *narrow_peak_band = peak_bin;
        *narrow_peak_counter = 0;
      }
    }
  }
}

}  // namespace

RenderSignalAnalyzer::RenderSignalAnalyzer(const EchoCanceller3Config& config)
    : strong_peak_freeze_duration_(config.filter.refined.length_blocks) {
  narrow_band_counters_.fill(0);
}
RenderSignalAnalyzer::~RenderSignalAnalyzer() = default;

void RenderSignalAnalyzer::Update(
    const RenderBuffer& render_buffer,
    const absl::optional<size_t>& delay_partitions) {
  // Identify bands of narrow nature.
  IdentifySmallNarrowBandRegions(render_buffer, delay_partitions,
                                 &narrow_band_counters_);

  // Identify the presence of a strong narrow band.
  IdentifyStrongNarrowBandComponent(render_buffer, strong_peak_freeze_duration_,
                                    &narrow_peak_band_, &narrow_peak_counter_);
}

void RenderSignalAnalyzer::MaskRegionsAroundNarrowBands(
    std::array<float, kFftLengthBy2Plus1>* v) const {
  RTC_DCHECK(v);

  // Set v to zero around narrow band signal regions.
  if (narrow_band_counters_[0] > kCounterThreshold) {
    (*v)[1] = (*v)[0] = 0.f;
  }
  for (size_t k = 2; k < kFftLengthBy2 - 1; ++k) {
    if (narrow_band_counters_[k - 1] > kCounterThreshold) {
      (*v)[k - 2] = (*v)[k - 1] = (*v)[k] = (*v)[k + 1] = (*v)[k + 2] = 0.f;
    }
  }
  if (narrow_band_counters_[kFftLengthBy2 - 2] > kCounterThreshold) {
    (*v)[kFftLengthBy2] = (*v)[kFftLengthBy2 - 1] = 0.f;
  }
}

}  // namespace webrtc
