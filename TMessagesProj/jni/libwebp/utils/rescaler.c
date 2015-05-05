// Copyright 2012 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// Rescaling functions
//
// Author: Skal (pascal.massimino@gmail.com)

#include <assert.h>
#include <stdlib.h>
#include "./rescaler.h"
#include "../dsp/dsp.h"

//------------------------------------------------------------------------------
// Implementations of critical functions ImportRow / ExportRow

void (*WebPRescalerImportRow)(WebPRescaler* const wrk,
                              const uint8_t* const src, int channel) = NULL;
void (*WebPRescalerExportRow)(WebPRescaler* const wrk, int x_out) = NULL;

#define RFIX 30
#define MULT_FIX(x, y) (((int64_t)(x) * (y) + (1 << (RFIX - 1))) >> RFIX)

static void ImportRowC(WebPRescaler* const wrk,
                       const uint8_t* const src, int channel) {
  const int x_stride = wrk->num_channels;
  const int x_out_max = wrk->dst_width * wrk->num_channels;
  int x_in = channel;
  int x_out;
  int accum = 0;
  if (!wrk->x_expand) {
    int sum = 0;
    for (x_out = channel; x_out < x_out_max; x_out += x_stride) {
      accum += wrk->x_add;
      for (; accum > 0; accum -= wrk->x_sub) {
        sum += src[x_in];
        x_in += x_stride;
      }
      {        // Emit next horizontal pixel.
        const int32_t base = src[x_in];
        const int32_t frac = base * (-accum);
        x_in += x_stride;
        wrk->frow[x_out] = (sum + base) * wrk->x_sub - frac;
        // fresh fractional start for next pixel
        sum = (int)MULT_FIX(frac, wrk->fx_scale);
      }
    }
  } else {        // simple bilinear interpolation
    int left = src[channel], right = src[channel];
    for (x_out = channel; x_out < x_out_max; x_out += x_stride) {
      if (accum < 0) {
        left = right;
        x_in += x_stride;
        right = src[x_in];
        accum += wrk->x_add;
      }
      wrk->frow[x_out] = right * wrk->x_add + (left - right) * accum;
      accum -= wrk->x_sub;
    }
  }
  // Accumulate the contribution of the new row.
  for (x_out = channel; x_out < x_out_max; x_out += x_stride) {
    wrk->irow[x_out] += wrk->frow[x_out];
  }
}

static void ExportRowC(WebPRescaler* const wrk, int x_out) {
  if (wrk->y_accum <= 0) {
    uint8_t* const dst = wrk->dst;
    int32_t* const irow = wrk->irow;
    const int32_t* const frow = wrk->frow;
    const int yscale = wrk->fy_scale * (-wrk->y_accum);
    const int x_out_max = wrk->dst_width * wrk->num_channels;
    for (; x_out < x_out_max; ++x_out) {
      const int frac = (int)MULT_FIX(frow[x_out], yscale);
      const int v = (int)MULT_FIX(irow[x_out] - frac, wrk->fxy_scale);
      dst[x_out] = (!(v & ~0xff)) ? v : (v < 0) ? 0 : 255;
      irow[x_out] = frac;   // new fractional start
    }
    wrk->y_accum += wrk->y_add;
    wrk->dst += wrk->dst_stride;
  }
}

//------------------------------------------------------------------------------
// MIPS version

#if defined(WEBP_USE_MIPS32)

static void ImportRowMIPS(WebPRescaler* const wrk,
                          const uint8_t* const src, int channel) {
  const int x_stride = wrk->num_channels;
  const int x_out_max = wrk->dst_width * wrk->num_channels;
  const int fx_scale = wrk->fx_scale;
  const int x_add = wrk->x_add;
  const int x_sub = wrk->x_sub;
  int* frow = wrk->frow + channel;
  int* irow = wrk->irow + channel;
  const uint8_t* src1 = src + channel;
  int temp1, temp2, temp3;
  int base, frac, sum;
  int accum, accum1;
  const int x_stride1 = x_stride << 2;
  int loop_c = x_out_max - channel;

  if (!wrk->x_expand) {
    __asm__ volatile (
      "li     %[temp1],   0x8000                    \n\t"
      "li     %[temp2],   0x10000                   \n\t"
      "li     %[sum],     0                         \n\t"
      "li     %[accum],   0                         \n\t"
    "1:                                             \n\t"
      "addu   %[accum],   %[accum],   %[x_add]      \n\t"
      "blez   %[accum],   3f                        \n\t"
    "2:                                             \n\t"
      "lbu    %[temp3],   0(%[src1])                \n\t"
      "subu   %[accum],   %[accum],   %[x_sub]      \n\t"
      "addu   %[src1],    %[src1],    %[x_stride]   \n\t"
      "addu   %[sum],     %[sum],     %[temp3]      \n\t"
      "bgtz   %[accum],   2b                        \n\t"
    "3:                                             \n\t"
      "lbu    %[base],    0(%[src1])                \n\t"
      "addu   %[src1],    %[src1],    %[x_stride]   \n\t"
      "negu   %[accum1],  %[accum]                  \n\t"
      "mul    %[frac],    %[base],    %[accum1]     \n\t"
      "addu   %[temp3],   %[sum],     %[base]       \n\t"
      "mul    %[temp3],   %[temp3],   %[x_sub]      \n\t"
      "lw     %[base],    0(%[irow])                \n\t"
      "subu   %[loop_c],  %[loop_c],  %[x_stride]   \n\t"
      "sll    %[accum1],  %[frac],    2             \n\t"
      "mult   %[temp1],   %[temp2]                  \n\t"
      "madd   %[accum1],  %[fx_scale]               \n\t"
      "mfhi   %[sum]                                \n\t"
      "subu   %[temp3],   %[temp3],   %[frac]       \n\t"
      "sw     %[temp3],   0(%[frow])                \n\t"
      "add    %[base],    %[base],    %[temp3]      \n\t"
      "sw     %[base],    0(%[irow])                \n\t"
      "addu   %[irow],    %[irow],    %[x_stride1]  \n\t"
      "addu   %[frow],    %[frow],    %[x_stride1]  \n\t"
      "bgtz   %[loop_c],  1b                        \n\t"

      : [accum] "=&r" (accum), [src1] "+r" (src1), [temp3] "=&r" (temp3),
        [sum] "=&r" (sum), [base] "=&r" (base), [frac] "=&r" (frac),
        [frow] "+r" (frow), [irow] "+r" (irow), [accum1] "=&r" (accum1),
        [temp2] "=&r" (temp2), [temp1] "=&r" (temp1)
      : [x_stride] "r" (x_stride), [fx_scale] "r" (fx_scale),
        [x_sub] "r" (x_sub), [x_add] "r" (x_add),
        [loop_c] "r" (loop_c), [x_stride1] "r" (x_stride1)
      : "memory", "hi", "lo"
    );
  } else {
    __asm__ volatile (
      "lbu    %[temp1],   0(%[src1])                \n\t"
      "move   %[temp2],   %[temp1]                  \n\t"
      "li     %[accum],   0                         \n\t"
    "1:                                             \n\t"
      "bgez   %[accum],   2f                        \n\t"
      "move   %[temp2],   %[temp1]                  \n\t"
      "addu   %[src1],    %[x_stride]               \n\t"
      "lbu    %[temp1],   0(%[src1])                \n\t"
      "addu   %[accum],   %[x_add]                  \n\t"
    "2:                                             \n\t"
      "subu   %[temp3],   %[temp2],   %[temp1]      \n\t"
      "mul    %[temp3],   %[temp3],   %[accum]      \n\t"
      "mul    %[base],    %[temp1],   %[x_add]      \n\t"
      "subu   %[accum],   %[accum],   %[x_sub]      \n\t"
      "lw     %[frac],    0(%[irow])                \n\t"
      "subu   %[loop_c],  %[loop_c],  %[x_stride]   \n\t"
      "addu   %[temp3],   %[base],    %[temp3]      \n\t"
      "sw     %[temp3],   0(%[frow])                \n\t"
      "addu   %[frow],    %[x_stride1]              \n\t"
      "addu   %[frac],    %[temp3]                  \n\t"
      "sw     %[frac],    0(%[irow])                \n\t"
      "addu   %[irow],    %[x_stride1]              \n\t"
      "bgtz   %[loop_c],  1b                        \n\t"

      : [src1] "+r" (src1), [accum] "=&r" (accum), [temp1] "=&r" (temp1),
        [temp2] "=&r" (temp2), [temp3] "=&r" (temp3), [base] "=&r" (base),
        [frac] "=&r" (frac), [frow] "+r" (frow), [irow] "+r" (irow)
      : [x_stride] "r" (x_stride), [x_add] "r" (x_add), [x_sub] "r" (x_sub),
        [x_stride1] "r" (x_stride1), [loop_c] "r" (loop_c)
      : "memory", "hi", "lo"
    );
  }
}

static void ExportRowMIPS(WebPRescaler* const wrk, int x_out) {
  if (wrk->y_accum <= 0) {
    uint8_t* const dst = wrk->dst;
    int32_t* const irow = wrk->irow;
    const int32_t* const frow = wrk->frow;
    const int yscale = wrk->fy_scale * (-wrk->y_accum);
    const int x_out_max = wrk->dst_width * wrk->num_channels;
    // if wrk->fxy_scale can fit into 32 bits use optimized code,
    // otherwise use C code
    if ((wrk->fxy_scale >> 32) == 0) {
      int temp0, temp1, temp3, temp4, temp5, temp6, temp7, loop_end;
      const int temp2 = (int)(wrk->fxy_scale);
      const int temp8 = x_out_max << 2;
      uint8_t* dst_t = (uint8_t*)dst;
      int32_t* irow_t = (int32_t*)irow;
      const int32_t* frow_t = (const int32_t*)frow;

      __asm__ volatile(
        "addiu    %[temp6],    $zero,       -256          \n\t"
        "addiu    %[temp7],    $zero,       255           \n\t"
        "li       %[temp3],    0x10000                    \n\t"
        "li       %[temp4],    0x8000                     \n\t"
        "addu     %[loop_end], %[frow_t],   %[temp8]      \n\t"
      "1:                                                 \n\t"
        "lw       %[temp0],    0(%[frow_t])               \n\t"
        "mult     %[temp3],    %[temp4]                   \n\t"
        "addiu    %[frow_t],   %[frow_t],   4             \n\t"
        "sll      %[temp0],    %[temp0],    2             \n\t"
        "madd     %[temp0],    %[yscale]                  \n\t"
        "mfhi     %[temp1]                                \n\t"
        "lw       %[temp0],    0(%[irow_t])               \n\t"
        "addiu    %[dst_t],    %[dst_t],    1             \n\t"
        "addiu    %[irow_t],   %[irow_t],   4             \n\t"
        "subu     %[temp0],    %[temp0],    %[temp1]      \n\t"
        "mult     %[temp3],    %[temp4]                   \n\t"
        "sll      %[temp0],    %[temp0],    2             \n\t"
        "madd     %[temp0],    %[temp2]                   \n\t"
        "mfhi     %[temp5]                                \n\t"
        "sw       %[temp1],    -4(%[irow_t])              \n\t"
        "and      %[temp0],    %[temp5],    %[temp6]      \n\t"
        "slti     %[temp1],    %[temp5],    0             \n\t"
        "beqz     %[temp0],    2f                         \n\t"
        "xor      %[temp5],    %[temp5],    %[temp5]      \n\t"
        "movz     %[temp5],    %[temp7],    %[temp1]      \n\t"
      "2:                                                 \n\t"
        "sb       %[temp5],    -1(%[dst_t])               \n\t"
        "bne      %[frow_t],   %[loop_end], 1b            \n\t"

        : [temp0]"=&r"(temp0), [temp1]"=&r"(temp1), [temp3]"=&r"(temp3),
          [temp4]"=&r"(temp4), [temp5]"=&r"(temp5), [temp6]"=&r"(temp6),
          [temp7]"=&r"(temp7), [frow_t]"+r"(frow_t), [irow_t]"+r"(irow_t),
          [dst_t]"+r"(dst_t), [loop_end]"=&r"(loop_end)
        : [temp2]"r"(temp2), [yscale]"r"(yscale), [temp8]"r"(temp8)
        : "memory", "hi", "lo"
      );
      wrk->y_accum += wrk->y_add;
      wrk->dst += wrk->dst_stride;
    } else {
      ExportRowC(wrk, x_out);
    }
  }
}
#endif   // WEBP_USE_MIPS32

//------------------------------------------------------------------------------

void WebPRescalerInit(WebPRescaler* const wrk, int src_width, int src_height,
                      uint8_t* const dst, int dst_width, int dst_height,
                      int dst_stride, int num_channels, int x_add, int x_sub,
                      int y_add, int y_sub, int32_t* const work) {
  wrk->x_expand = (src_width < dst_width);
  wrk->src_width = src_width;
  wrk->src_height = src_height;
  wrk->dst_width = dst_width;
  wrk->dst_height = dst_height;
  wrk->dst = dst;
  wrk->dst_stride = dst_stride;
  wrk->num_channels = num_channels;
  // for 'x_expand', we use bilinear interpolation
  wrk->x_add = wrk->x_expand ? (x_sub - 1) : x_add - x_sub;
  wrk->x_sub = wrk->x_expand ? (x_add - 1) : x_sub;
  wrk->y_accum = y_add;
  wrk->y_add = y_add;
  wrk->y_sub = y_sub;
  wrk->fx_scale = (1 << RFIX) / x_sub;
  wrk->fy_scale = (1 << RFIX) / y_sub;
  wrk->fxy_scale = wrk->x_expand ?
      ((int64_t)dst_height << RFIX) / (x_sub * src_height) :
      ((int64_t)dst_height << RFIX) / (x_add * src_height);
  wrk->irow = work;
  wrk->frow = work + num_channels * dst_width;

  if (WebPRescalerImportRow == NULL) {
    WebPRescalerImportRow = ImportRowC;
    WebPRescalerExportRow = ExportRowC;
    if (VP8GetCPUInfo != NULL) {
#if defined(WEBP_USE_MIPS32)
      if (VP8GetCPUInfo(kMIPS32)) {
        WebPRescalerImportRow = ImportRowMIPS;
        WebPRescalerExportRow = ExportRowMIPS;
      }
#endif
    }
  }
}

#undef MULT_FIX
#undef RFIX

//------------------------------------------------------------------------------
// all-in-one calls

int WebPRescaleNeededLines(const WebPRescaler* const wrk, int max_num_lines) {
  const int num_lines = (wrk->y_accum + wrk->y_sub - 1) / wrk->y_sub;
  return (num_lines > max_num_lines) ? max_num_lines : num_lines;
}

int WebPRescalerImport(WebPRescaler* const wrk, int num_lines,
                       const uint8_t* src, int src_stride) {
  int total_imported = 0;
  while (total_imported < num_lines && wrk->y_accum > 0) {
    int channel;
    for (channel = 0; channel < wrk->num_channels; ++channel) {
      WebPRescalerImportRow(wrk, src, channel);
    }
    src += src_stride;
    ++total_imported;
    wrk->y_accum -= wrk->y_sub;
  }
  return total_imported;
}

int WebPRescalerExport(WebPRescaler* const rescaler) {
  int total_exported = 0;
  while (WebPRescalerHasPendingOutput(rescaler)) {
    WebPRescalerExportRow(rescaler, 0);
    ++total_exported;
  }
  return total_exported;
}

//------------------------------------------------------------------------------
