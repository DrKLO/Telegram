/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/alignment_adjuster.h"

#include <algorithm>
#include <limits>

#include "absl/algorithm/container.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {
// Round each scale factor to the closest rational in form alignment/i where i
// is a multiple of |requested_alignment|. Each resolution divisible by
// |alignment| will be divisible by |requested_alignment| after the scale factor
// is applied.
double RoundToMultiple(int alignment,
                       int requested_alignment,
                       VideoEncoderConfig* config,
                       bool update_config) {
  double diff = 0.0;
  for (auto& layer : config->simulcast_layers) {
    double min_dist = std::numeric_limits<double>::max();
    double new_scale = 1.0;
    for (int i = requested_alignment; i <= alignment;
         i += requested_alignment) {
      double dist = std::abs(layer.scale_resolution_down_by -
                             alignment / static_cast<double>(i));
      if (dist <= min_dist) {
        min_dist = dist;
        new_scale = alignment / static_cast<double>(i);
      }
    }
    diff += std::abs(layer.scale_resolution_down_by - new_scale);
    if (update_config) {
      RTC_LOG(LS_INFO) << "scale_resolution_down_by "
                       << layer.scale_resolution_down_by << " -> " << new_scale;
      layer.scale_resolution_down_by = new_scale;
    }
  }
  return diff;
}
}  // namespace

// Input: encoder_info.requested_resolution_alignment (K)
// Input: encoder_info.apply_alignment_to_all_simulcast_layers (B)
// Input: vector config->simulcast_layers.scale_resolution_down_by (S[i])
// Output:
// If B is false, returns K and does not adjust scaling factors.
// Otherwise, returns adjusted alignment (A), adjusted scaling factors (S'[i])
// are written in |config| such that:
//
// A / S'[i] are integers divisible by K
// sum abs(S'[i] - S[i]) -> min
// A integer <= 16
//
// Solution chooses closest S'[i] in a form A / j where j is a multiple of K.

int AlignmentAdjuster::GetAlignmentAndMaybeAdjustScaleFactors(
    const VideoEncoder::EncoderInfo& encoder_info,
    VideoEncoderConfig* config) {
  const int requested_alignment = encoder_info.requested_resolution_alignment;
  if (!encoder_info.apply_alignment_to_all_simulcast_layers) {
    return requested_alignment;
  }

  if (requested_alignment < 1 || config->number_of_streams <= 1 ||
      config->simulcast_layers.size() <= 1) {
    return requested_alignment;
  }

  // Update alignment to also apply to simulcast layers.
  const bool has_scale_resolution_down_by = absl::c_any_of(
      config->simulcast_layers, [](const webrtc::VideoStream& layer) {
        return layer.scale_resolution_down_by >= 1.0;
      });

  if (!has_scale_resolution_down_by) {
    // Default resolution downscaling used (scale factors: 1, 2, 4, ...).
    return requested_alignment * (1 << (config->simulcast_layers.size() - 1));
  }

  // Get alignment for downscaled layers.
  // Adjust |scale_resolution_down_by| to a common multiple to limit the
  // alignment value (to avoid largely cropped frames and possibly with an
  // aspect ratio far from the original).
  const int kMaxAlignment = 16;

  for (auto& layer : config->simulcast_layers) {
    layer.scale_resolution_down_by =
        std::max(layer.scale_resolution_down_by, 1.0);
    layer.scale_resolution_down_by =
        std::min(layer.scale_resolution_down_by, 10000.0);
  }

  // Decide on common multiple to use.
  double min_diff = std::numeric_limits<double>::max();
  int best_alignment = 1;
  for (int alignment = requested_alignment; alignment <= kMaxAlignment;
       ++alignment) {
    double diff = RoundToMultiple(alignment, requested_alignment, config,
                                  /*update_config=*/false);
    if (diff < min_diff) {
      min_diff = diff;
      best_alignment = alignment;
    }
  }
  RoundToMultiple(best_alignment, requested_alignment, config,
                  /*update_config=*/true);

  return std::max(best_alignment, requested_alignment);
}
}  // namespace webrtc
