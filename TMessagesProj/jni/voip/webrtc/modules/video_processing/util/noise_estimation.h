/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_PROCESSING_UTIL_NOISE_ESTIMATION_H_
#define MODULES_VIDEO_PROCESSING_UTIL_NOISE_ESTIMATION_H_

#include <cstdint>
#include <memory>

#include "modules/video_processing/util/denoiser_filter.h"

namespace webrtc {

#define DISPLAY 0      // Rectangle diagnostics
#define DISPLAYNEON 0  // Rectangle diagnostics on NEON

const int kNoiseThreshold = 150;
const int kNoiseThresholdNeon = 70;
const int kConsecLowVarFrame = 6;
const int kAverageLumaMin = 20;
const int kAverageLumaMax = 220;
const int kBlockSelectionVarMax = kNoiseThreshold << 1;

// TODO(jackychen): To test different sampling strategy.
// Collect noise data every NOISE_SUBSAMPLE_INTERVAL blocks.
#define NOISE_SUBSAMPLE_INTERVAL 41

class NoiseEstimation {
 public:
  void Init(int width, int height, CpuType cpu_type);
  // Collect noise data from one qualified block.
  void GetNoise(int mb_index, uint32_t var, uint32_t luma);
  // Reset the counter for consecutive low-var blocks.
  void ResetConsecLowVar(int mb_index);
  // Update noise level for current frame.
  void UpdateNoiseLevel();
  // 0: low noise, 1: high noise
  uint8_t GetNoiseLevel();

 private:
  int width_;
  int height_;
  int mb_rows_;
  int mb_cols_;
  int num_noisy_block_;
  int num_static_block_;
  CpuType cpu_type_;
  uint32_t noise_var_;
  double noise_var_accum_;
  double percent_static_block_;
  std::unique_ptr<uint32_t[]> consec_low_var_;
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_PROCESSING_UTIL_NOISE_ESTIMATION_H_
