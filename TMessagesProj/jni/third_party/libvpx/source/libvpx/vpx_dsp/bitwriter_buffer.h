/*
 *  Copyright (c) 2013 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef VPX_VPX_DSP_BITWRITER_BUFFER_H_
#define VPX_VPX_DSP_BITWRITER_BUFFER_H_

#include "vpx/vpx_integer.h"

#ifdef __cplusplus
extern "C" {
#endif

struct vpx_write_bit_buffer {
  uint8_t *bit_buffer;
  size_t bit_offset;
};

size_t vpx_wb_bytes_written(const struct vpx_write_bit_buffer *wb);

void vpx_wb_write_bit(struct vpx_write_bit_buffer *wb, int bit);

void vpx_wb_write_literal(struct vpx_write_bit_buffer *wb, int data, int bits);

void vpx_wb_write_inv_signed_literal(struct vpx_write_bit_buffer *wb, int data,
                                     int bits);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // VPX_VPX_DSP_BITWRITER_BUFFER_H_
