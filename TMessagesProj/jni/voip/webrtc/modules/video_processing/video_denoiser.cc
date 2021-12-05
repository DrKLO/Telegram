/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_processing/video_denoiser.h"

#include <stdint.h>
#include <string.h>

#include "api/video/i420_buffer.h"
#include "third_party/libyuv/include/libyuv/planar_functions.h"

namespace webrtc {

#if DISPLAY || DISPLAYNEON
static void ShowRect(const std::unique_ptr<DenoiserFilter>& filter,
                     const std::unique_ptr<uint8_t[]>& d_status,
                     const std::unique_ptr<uint8_t[]>& moving_edge_red,
                     const std::unique_ptr<uint8_t[]>& x_density,
                     const std::unique_ptr<uint8_t[]>& y_density,
                     const uint8_t* u_src,
                     int stride_u_src,
                     const uint8_t* v_src,
                     int stride_v_src,
                     uint8_t* u_dst,
                     int stride_u_dst,
                     uint8_t* v_dst,
                     int stride_v_dst,
                     int mb_rows_,
                     int mb_cols_) {
  for (int mb_row = 0; mb_row < mb_rows_; ++mb_row) {
    for (int mb_col = 0; mb_col < mb_cols_; ++mb_col) {
      int mb_index = mb_row * mb_cols_ + mb_col;
      const uint8_t* mb_src_u =
          u_src + (mb_row << 3) * stride_u_src + (mb_col << 3);
      const uint8_t* mb_src_v =
          v_src + (mb_row << 3) * stride_v_src + (mb_col << 3);
      uint8_t* mb_dst_u = u_dst + (mb_row << 3) * stride_u_dst + (mb_col << 3);
      uint8_t* mb_dst_v = v_dst + (mb_row << 3) * stride_v_dst + (mb_col << 3);
      uint8_t uv_tmp[8 * 8];
      memset(uv_tmp, 200, 8 * 8);
      if (d_status[mb_index] == 1) {
        // Paint to red.
        libyuv::CopyPlane(mb_src_u, stride_u_src, mb_dst_u, stride_u_dst, 8, 8);
        libyuv::CopyPlane(uv_tmp, 8, mb_dst_v, stride_v_dst, 8, 8);
      } else if (moving_edge_red[mb_row * mb_cols_ + mb_col] &&
                 x_density[mb_col] * y_density[mb_row]) {
        // Paint to blue.
        libyuv::CopyPlane(uv_tmp, 8, mb_dst_u, stride_u_dst, 8, 8);
        libyuv::CopyPlane(mb_src_v, stride_v_src, mb_dst_v, stride_v_dst, 8, 8);
      } else {
        libyuv::CopyPlane(mb_src_u, stride_u_src, mb_dst_u, stride_u_dst, 8, 8);
        libyuv::CopyPlane(mb_src_v, stride_v_src, mb_dst_v, stride_v_dst, 8, 8);
      }
    }
  }
}
#endif

VideoDenoiser::VideoDenoiser(bool runtime_cpu_detection)
    : width_(0),
      height_(0),
      filter_(DenoiserFilter::Create(runtime_cpu_detection, &cpu_type_)),
      ne_(new NoiseEstimation()) {}

void VideoDenoiser::DenoiserReset(
    rtc::scoped_refptr<I420BufferInterface> frame) {
  width_ = frame->width();
  height_ = frame->height();
  mb_cols_ = width_ >> 4;
  mb_rows_ = height_ >> 4;

  // Init noise estimator and allocate buffers.
  ne_->Init(width_, height_, cpu_type_);
  moving_edge_.reset(new uint8_t[mb_cols_ * mb_rows_]);
  mb_filter_decision_.reset(new DenoiserDecision[mb_cols_ * mb_rows_]);
  x_density_.reset(new uint8_t[mb_cols_]);
  y_density_.reset(new uint8_t[mb_rows_]);
  moving_object_.reset(new uint8_t[mb_cols_ * mb_rows_]);
}

int VideoDenoiser::PositionCheck(int mb_row, int mb_col, int noise_level) {
  if (noise_level == 0)
    return 1;
  if ((mb_row <= (mb_rows_ >> 4)) || (mb_col <= (mb_cols_ >> 4)) ||
      (mb_col >= (15 * mb_cols_ >> 4)))
    return 3;
  else if ((mb_row <= (mb_rows_ >> 3)) || (mb_col <= (mb_cols_ >> 3)) ||
           (mb_col >= (7 * mb_cols_ >> 3)))
    return 2;
  else
    return 1;
}

void VideoDenoiser::ReduceFalseDetection(
    const std::unique_ptr<uint8_t[]>& d_status,
    std::unique_ptr<uint8_t[]>* moving_edge_red,
    int noise_level) {
  // From up left corner.
  int mb_col_stop = mb_cols_ - 1;
  for (int mb_row = 0; mb_row <= mb_rows_ - 1; ++mb_row) {
    for (int mb_col = 0; mb_col <= mb_col_stop; ++mb_col) {
      if (d_status[mb_row * mb_cols_ + mb_col]) {
        mb_col_stop = mb_col - 1;
        break;
      }
      (*moving_edge_red)[mb_row * mb_cols_ + mb_col] = 0;
    }
  }
  // From bottom left corner.
  mb_col_stop = mb_cols_ - 1;
  for (int mb_row = mb_rows_ - 1; mb_row >= 0; --mb_row) {
    for (int mb_col = 0; mb_col <= mb_col_stop; ++mb_col) {
      if (d_status[mb_row * mb_cols_ + mb_col]) {
        mb_col_stop = mb_col - 1;
        break;
      }
      (*moving_edge_red)[mb_row * mb_cols_ + mb_col] = 0;
    }
  }
  // From up right corner.
  mb_col_stop = 0;
  for (int mb_row = 0; mb_row <= mb_rows_ - 1; ++mb_row) {
    for (int mb_col = mb_cols_ - 1; mb_col >= mb_col_stop; --mb_col) {
      if (d_status[mb_row * mb_cols_ + mb_col]) {
        mb_col_stop = mb_col + 1;
        break;
      }
      (*moving_edge_red)[mb_row * mb_cols_ + mb_col] = 0;
    }
  }
  // From bottom right corner.
  mb_col_stop = 0;
  for (int mb_row = mb_rows_ - 1; mb_row >= 0; --mb_row) {
    for (int mb_col = mb_cols_ - 1; mb_col >= mb_col_stop; --mb_col) {
      if (d_status[mb_row * mb_cols_ + mb_col]) {
        mb_col_stop = mb_col + 1;
        break;
      }
      (*moving_edge_red)[mb_row * mb_cols_ + mb_col] = 0;
    }
  }
}

bool VideoDenoiser::IsTrailingBlock(const std::unique_ptr<uint8_t[]>& d_status,
                                    int mb_row,
                                    int mb_col) {
  bool ret = false;
  int mb_index = mb_row * mb_cols_ + mb_col;
  if (!mb_row || !mb_col || mb_row == mb_rows_ - 1 || mb_col == mb_cols_ - 1)
    ret = false;
  else
    ret = d_status[mb_index + 1] || d_status[mb_index - 1] ||
          d_status[mb_index + mb_cols_] || d_status[mb_index - mb_cols_];
  return ret;
}

void VideoDenoiser::CopySrcOnMOB(const uint8_t* y_src,
                                 int stride_src,
                                 uint8_t* y_dst,
                                 int stride_dst) {
  // Loop over to copy src block if the block is marked as moving object block
  // or if the block may cause trailing artifacts.
  for (int mb_row = 0; mb_row < mb_rows_; ++mb_row) {
    const int mb_index_base = mb_row * mb_cols_;
    const uint8_t* mb_src_base = y_src + (mb_row << 4) * stride_src;
    uint8_t* mb_dst_base = y_dst + (mb_row << 4) * stride_dst;
    for (int mb_col = 0; mb_col < mb_cols_; ++mb_col) {
      const int mb_index = mb_index_base + mb_col;
      const uint32_t offset_col = mb_col << 4;
      const uint8_t* mb_src = mb_src_base + offset_col;
      uint8_t* mb_dst = mb_dst_base + offset_col;
      // Check if the block is a moving object block or may cause a trailing
      // artifacts.
      if (mb_filter_decision_[mb_index] != FILTER_BLOCK ||
          IsTrailingBlock(moving_edge_, mb_row, mb_col) ||
          (x_density_[mb_col] * y_density_[mb_row] &&
           moving_object_[mb_row * mb_cols_ + mb_col])) {
        // Copy y source.
        libyuv::CopyPlane(mb_src, stride_src, mb_dst, stride_dst, 16, 16);
      }
    }
  }
}

void VideoDenoiser::CopyLumaOnMargin(const uint8_t* y_src,
                                     int stride_src,
                                     uint8_t* y_dst,
                                     int stride_dst) {
  int height_margin = height_ - (mb_rows_ << 4);
  if (height_margin > 0) {
    const uint8_t* margin_y_src = y_src + (mb_rows_ << 4) * stride_src;
    uint8_t* margin_y_dst = y_dst + (mb_rows_ << 4) * stride_dst;
    libyuv::CopyPlane(margin_y_src, stride_src, margin_y_dst, stride_dst,
                      width_, height_margin);
  }
  int width_margin = width_ - (mb_cols_ << 4);
  if (width_margin > 0) {
    const uint8_t* margin_y_src = y_src + (mb_cols_ << 4);
    uint8_t* margin_y_dst = y_dst + (mb_cols_ << 4);
    libyuv::CopyPlane(margin_y_src, stride_src, margin_y_dst, stride_dst,
                      width_ - (mb_cols_ << 4), mb_rows_ << 4);
  }
}

rtc::scoped_refptr<I420BufferInterface> VideoDenoiser::DenoiseFrame(
    rtc::scoped_refptr<I420BufferInterface> frame,
    bool noise_estimation_enabled) {
  // If previous width and height are different from current frame's, need to
  // reallocate the buffers and no denoising for the current frame.
  if (!prev_buffer_ || width_ != frame->width() || height_ != frame->height()) {
    DenoiserReset(frame);
    prev_buffer_ = frame;
    return frame;
  }

  // Set buffer pointers.
  const uint8_t* y_src = frame->DataY();
  int stride_y_src = frame->StrideY();
  rtc::scoped_refptr<I420Buffer> dst =
      buffer_pool_.CreateI420Buffer(width_, height_);

  uint8_t* y_dst = dst->MutableDataY();
  int stride_y_dst = dst->StrideY();

  const uint8_t* y_dst_prev = prev_buffer_->DataY();
  int stride_prev = prev_buffer_->StrideY();

  memset(x_density_.get(), 0, mb_cols_);
  memset(y_density_.get(), 0, mb_rows_);
  memset(moving_object_.get(), 1, mb_cols_ * mb_rows_);

  uint8_t noise_level = noise_estimation_enabled ? ne_->GetNoiseLevel() : 0;
  int thr_var_base = 16 * 16 * 2;
  // Loop over blocks to accumulate/extract noise level and update x/y_density
  // factors for moving object detection.
  for (int mb_row = 0; mb_row < mb_rows_; ++mb_row) {
    const int mb_index_base = mb_row * mb_cols_;
    const uint8_t* mb_src_base = y_src + (mb_row << 4) * stride_y_src;
    uint8_t* mb_dst_base = y_dst + (mb_row << 4) * stride_y_dst;
    const uint8_t* mb_dst_prev_base = y_dst_prev + (mb_row << 4) * stride_prev;
    for (int mb_col = 0; mb_col < mb_cols_; ++mb_col) {
      const int mb_index = mb_index_base + mb_col;
      const bool ne_enable = (mb_index % NOISE_SUBSAMPLE_INTERVAL == 0);
      const int pos_factor = PositionCheck(mb_row, mb_col, noise_level);
      const uint32_t thr_var_adp = thr_var_base * pos_factor;
      const uint32_t offset_col = mb_col << 4;
      const uint8_t* mb_src = mb_src_base + offset_col;
      uint8_t* mb_dst = mb_dst_base + offset_col;
      const uint8_t* mb_dst_prev = mb_dst_prev_base + offset_col;

      // TODO(jackychen): Need SSE2/NEON opt.
      int luma = 0;
      if (ne_enable) {
        for (int i = 4; i < 12; ++i) {
          for (int j = 4; j < 12; ++j) {
            luma += mb_src[i * stride_y_src + j];
          }
        }
      }

      // Get the filtered block and filter_decision.
      mb_filter_decision_[mb_index] =
          filter_->MbDenoise(mb_dst_prev, stride_prev, mb_dst, stride_y_dst,
                             mb_src, stride_y_src, 0, noise_level);

      // If filter decision is FILTER_BLOCK, no need to check moving edge.
      // It is unlikely for a moving edge block to be filtered in current
      // setting.
      if (mb_filter_decision_[mb_index] == FILTER_BLOCK) {
        uint32_t sse_t = 0;
        if (ne_enable) {
          // The variance used in noise estimation is based on the src block in
          // time t (mb_src) and filtered block in time t-1 (mb_dist_prev).
          uint32_t noise_var = filter_->Variance16x8(
              mb_dst_prev, stride_y_dst, mb_src, stride_y_src, &sse_t);
          ne_->GetNoise(mb_index, noise_var, luma);
        }
        moving_edge_[mb_index] = 0;  // Not a moving edge block.
      } else {
        uint32_t sse_t = 0;
        // The variance used in MOD is based on the filtered blocks in time
        // T (mb_dst) and T-1 (mb_dst_prev).
        uint32_t noise_var = filter_->Variance16x8(
            mb_dst_prev, stride_prev, mb_dst, stride_y_dst, &sse_t);
        if (noise_var > thr_var_adp) {  // Moving edge checking.
          if (ne_enable) {
            ne_->ResetConsecLowVar(mb_index);
          }
          moving_edge_[mb_index] = 1;  // Mark as moving edge block.
          x_density_[mb_col] += (pos_factor < 3);
          y_density_[mb_row] += (pos_factor < 3);
        } else {
          moving_edge_[mb_index] = 0;
          if (ne_enable) {
            // The variance used in noise estimation is based on the src block
            // in time t (mb_src) and filtered block in time t-1 (mb_dist_prev).
            uint32_t noise_var = filter_->Variance16x8(
                mb_dst_prev, stride_prev, mb_src, stride_y_src, &sse_t);
            ne_->GetNoise(mb_index, noise_var, luma);
          }
        }
      }
    }  // End of for loop
  }    // End of for loop

  ReduceFalseDetection(moving_edge_, &moving_object_, noise_level);

  CopySrcOnMOB(y_src, stride_y_src, y_dst, stride_y_dst);

  // When frame width/height not divisible by 16, copy the margin to
  // denoised_frame.
  if ((mb_rows_ << 4) != height_ || (mb_cols_ << 4) != width_)
    CopyLumaOnMargin(y_src, stride_y_src, y_dst, stride_y_dst);

  // Copy u/v planes.
  libyuv::CopyPlane(frame->DataU(), frame->StrideU(), dst->MutableDataU(),
                    dst->StrideU(), (width_ + 1) >> 1, (height_ + 1) >> 1);
  libyuv::CopyPlane(frame->DataV(), frame->StrideV(), dst->MutableDataV(),
                    dst->StrideV(), (width_ + 1) >> 1, (height_ + 1) >> 1);

#if DISPLAY || DISPLAYNEON
  // Show rectangular region
  ShowRect(filter_, moving_edge_, moving_object_, x_density_, y_density_,
           frame->DataU(), frame->StrideU(), frame->DataV(), frame->StrideV(),
           dst->MutableDataU(), dst->StrideU(), dst->MutableDataV(),
           dst->StrideV(), mb_rows_, mb_cols_);
#endif
  prev_buffer_ = dst;
  return dst;
}

}  // namespace webrtc
