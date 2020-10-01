/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_processing/util/noise_estimation.h"
#if DISPLAYNEON
#include <android/log.h>
#endif

namespace webrtc {

void NoiseEstimation::Init(int width, int height, CpuType cpu_type) {
  int mb_cols = width >> 4;
  int mb_rows = height >> 4;
  consec_low_var_.reset(new uint32_t[mb_cols * mb_rows]());
  width_ = width;
  height_ = height;
  mb_cols_ = width_ >> 4;
  mb_rows_ = height_ >> 4;
  cpu_type_ = cpu_type;
}

void NoiseEstimation::GetNoise(int mb_index, uint32_t var, uint32_t luma) {
  consec_low_var_[mb_index]++;
  num_static_block_++;
  if (consec_low_var_[mb_index] >= kConsecLowVarFrame &&
      (luma >> 6) < kAverageLumaMax && (luma >> 6) > kAverageLumaMin) {
    // Normalized var by the average luma value, this gives more weight to
    // darker blocks.
    int nor_var = var / (luma >> 10);
    noise_var_ +=
        nor_var > kBlockSelectionVarMax ? kBlockSelectionVarMax : nor_var;
    num_noisy_block_++;
  }
}

void NoiseEstimation::ResetConsecLowVar(int mb_index) {
  consec_low_var_[mb_index] = 0;
}

void NoiseEstimation::UpdateNoiseLevel() {
  // TODO(jackychen): Tune a threshold for numb_noisy_block > T to make the
  // condition more reasonable.
  // No enough samples implies the motion of the camera or too many moving
  // objects in the frame.
  if (num_static_block_ <
          (0.65 * mb_cols_ * mb_rows_ / NOISE_SUBSAMPLE_INTERVAL) ||
      !num_noisy_block_) {
#if DISPLAY
    printf("Not enough samples. %d \n", num_static_block_);
#elif DISPLAYNEON
    __android_log_print(ANDROID_LOG_DEBUG, "DISPLAY",
                        "Not enough samples. %d \n", num_static_block_);
#endif
    noise_var_ = 0;
    noise_var_accum_ = 0;
    num_noisy_block_ = 0;
    num_static_block_ = 0;
    return;
  } else {
#if DISPLAY
    printf("%d %d fraction = %.3f\n", num_static_block_,
           mb_cols_ * mb_rows_ / NOISE_SUBSAMPLE_INTERVAL,
           percent_static_block_);
#elif DISPLAYNEON
    __android_log_print(ANDROID_LOG_DEBUG, "DISPLAY", "%d %d fraction = %.3f\n",
                        num_static_block_,
                        mb_cols_ * mb_rows_ / NOISE_SUBSAMPLE_INTERVAL,
                        percent_static_block_);
#endif
    // Normalized by the number of noisy blocks.
    noise_var_ /= num_noisy_block_;
    // Get the percentage of static blocks.
    percent_static_block_ = static_cast<double>(num_static_block_) /
                            (mb_cols_ * mb_rows_ / NOISE_SUBSAMPLE_INTERVAL);
    num_noisy_block_ = 0;
    num_static_block_ = 0;
  }
  // For the first frame just update the value with current noise_var_,
  // otherwise, use the averaging window.
  if (noise_var_accum_ == 0) {
    noise_var_accum_ = noise_var_;
  } else {
    noise_var_accum_ = (noise_var_accum_ * 15 + noise_var_) / 16;
  }
#if DISPLAY
  printf("noise_var_accum_ = %.1f, noise_var_ = %d.\n", noise_var_accum_,
         noise_var_);
#elif DISPLAYNEON
  __android_log_print(ANDROID_LOG_DEBUG, "DISPLAY",
                      "noise_var_accum_ = %.1f, noise_var_ = %d.\n",
                      noise_var_accum_, noise_var_);
#endif
  // Reset noise_var_ for the next frame.
  noise_var_ = 0;
}

uint8_t NoiseEstimation::GetNoiseLevel() {
  int noise_thr = cpu_type_ ? kNoiseThreshold : kNoiseThresholdNeon;
  UpdateNoiseLevel();
  if (noise_var_accum_ > noise_thr) {
    return 1;
  }
  return 0;
}

}  // namespace webrtc
