/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_PROCESSING_VIDEO_DENOISER_H_
#define MODULES_VIDEO_PROCESSING_VIDEO_DENOISER_H_

#include <memory>

#include "api/scoped_refptr.h"
#include "api/video/video_frame_buffer.h"
#include "common_video/include/i420_buffer_pool.h"
#include "modules/video_processing/util/denoiser_filter.h"
#include "modules/video_processing/util/noise_estimation.h"
#include "modules/video_processing/util/skin_detection.h"

namespace webrtc {

class VideoDenoiser {
 public:
  explicit VideoDenoiser(bool runtime_cpu_detection);

  rtc::scoped_refptr<I420BufferInterface> DenoiseFrame(
      rtc::scoped_refptr<I420BufferInterface> frame,
      bool noise_estimation_enabled);

 private:
  void DenoiserReset(rtc::scoped_refptr<I420BufferInterface> frame);

  // Check the mb position, return 1: close to the frame center (between 1/8
  // and 7/8 of width/height), 3: close to the border (out of 1/16 and 15/16
  // of width/height), 2: in between.
  int PositionCheck(int mb_row, int mb_col, int noise_level);

  // To reduce false detection in moving object detection (MOD).
  void ReduceFalseDetection(const std::unique_ptr<uint8_t[]>& d_status,
                            std::unique_ptr<uint8_t[]>* d_status_red,
                            int noise_level);

  // Return whether a block might cause trailing artifact by checking if one of
  // its neighbor blocks is a moving edge block.
  bool IsTrailingBlock(const std::unique_ptr<uint8_t[]>& d_status,
                       int mb_row,
                       int mb_col);

  // Copy input blocks to dst buffer on moving object blocks (MOB).
  void CopySrcOnMOB(const uint8_t* y_src,
                    int stride_src,
                    uint8_t* y_dst,
                    int stride_dst);

  // Copy luma margin blocks when frame width/height not divisible by 16.
  void CopyLumaOnMargin(const uint8_t* y_src,
                        int stride_src,
                        uint8_t* y_dst,
                        int stride_dst);

  int width_;
  int height_;
  int mb_rows_;
  int mb_cols_;
  CpuType cpu_type_;
  std::unique_ptr<DenoiserFilter> filter_;
  std::unique_ptr<NoiseEstimation> ne_;
  // 1 for moving edge block, 0 for static block.
  std::unique_ptr<uint8_t[]> moving_edge_;
  // 1 for moving object block, 0 for static block.
  std::unique_ptr<uint8_t[]> moving_object_;
  // x_density_ and y_density_ are used in MOD process.
  std::unique_ptr<uint8_t[]> x_density_;
  std::unique_ptr<uint8_t[]> y_density_;
  // Save the return values by MbDenoise for each block.
  std::unique_ptr<DenoiserDecision[]> mb_filter_decision_;
  I420BufferPool buffer_pool_;
  rtc::scoped_refptr<I420BufferInterface> prev_buffer_;
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_PROCESSING_VIDEO_DENOISER_H_
