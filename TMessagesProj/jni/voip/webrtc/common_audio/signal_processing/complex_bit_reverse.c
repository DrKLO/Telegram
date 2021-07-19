/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_audio/signal_processing/include/signal_processing_library.h"

/* Tables for data buffer indexes that are bit reversed and thus need to be
 * swapped. Note that, index_7[{0, 2, 4, ...}] are for the left side of the swap
 * operations, while index_7[{1, 3, 5, ...}] are for the right side of the
 * operation. Same for index_8.
 */

/* Indexes for the case of stages == 7. */
static const int16_t index_7[112] = {
  1, 64, 2, 32, 3, 96, 4, 16, 5, 80, 6, 48, 7, 112, 9, 72, 10, 40, 11, 104,
  12, 24, 13, 88, 14, 56, 15, 120, 17, 68, 18, 36, 19, 100, 21, 84, 22, 52,
  23, 116, 25, 76, 26, 44, 27, 108, 29, 92, 30, 60, 31, 124, 33, 66, 35, 98,
  37, 82, 38, 50, 39, 114, 41, 74, 43, 106, 45, 90, 46, 58, 47, 122, 49, 70,
  51, 102, 53, 86, 55, 118, 57, 78, 59, 110, 61, 94, 63, 126, 67, 97, 69,
  81, 71, 113, 75, 105, 77, 89, 79, 121, 83, 101, 87, 117, 91, 109, 95, 125,
  103, 115, 111, 123
};

/* Indexes for the case of stages == 8. */
static const int16_t index_8[240] = {
  1, 128, 2, 64, 3, 192, 4, 32, 5, 160, 6, 96, 7, 224, 8, 16, 9, 144, 10, 80,
  11, 208, 12, 48, 13, 176, 14, 112, 15, 240, 17, 136, 18, 72, 19, 200, 20,
  40, 21, 168, 22, 104, 23, 232, 25, 152, 26, 88, 27, 216, 28, 56, 29, 184,
  30, 120, 31, 248, 33, 132, 34, 68, 35, 196, 37, 164, 38, 100, 39, 228, 41,
  148, 42, 84, 43, 212, 44, 52, 45, 180, 46, 116, 47, 244, 49, 140, 50, 76,
  51, 204, 53, 172, 54, 108, 55, 236, 57, 156, 58, 92, 59, 220, 61, 188, 62,
  124, 63, 252, 65, 130, 67, 194, 69, 162, 70, 98, 71, 226, 73, 146, 74, 82,
  75, 210, 77, 178, 78, 114, 79, 242, 81, 138, 83, 202, 85, 170, 86, 106, 87,
  234, 89, 154, 91, 218, 93, 186, 94, 122, 95, 250, 97, 134, 99, 198, 101,
  166, 103, 230, 105, 150, 107, 214, 109, 182, 110, 118, 111, 246, 113, 142,
  115, 206, 117, 174, 119, 238, 121, 158, 123, 222, 125, 190, 127, 254, 131,
  193, 133, 161, 135, 225, 137, 145, 139, 209, 141, 177, 143, 241, 147, 201,
  149, 169, 151, 233, 155, 217, 157, 185, 159, 249, 163, 197, 167, 229, 171,
  213, 173, 181, 175, 245, 179, 205, 183, 237, 187, 221, 191, 253, 199, 227,
  203, 211, 207, 243, 215, 235, 223, 251, 239, 247
};

void WebRtcSpl_ComplexBitReverse(int16_t* __restrict complex_data, int stages) {
  /* For any specific value of stages, we know exactly the indexes that are
   * bit reversed. Currently (Feb. 2012) in WebRTC the only possible values of
   * stages are 7 and 8, so we use tables to save unnecessary iterations and
   * calculations for these two cases.
   */
  if (stages == 7 || stages == 8) {
    int m = 0;
    int length = 112;
    const int16_t* index = index_7;

    if (stages == 8) {
      length = 240;
      index = index_8;
    }

    /* Decimation in time. Swap the elements with bit-reversed indexes. */
    for (m = 0; m < length; m += 2) {
      /* We declare a int32_t* type pointer, to load both the 16-bit real
       * and imaginary elements from complex_data in one instruction, reducing
       * complexity.
       */
      int32_t* complex_data_ptr = (int32_t*)complex_data;
      int32_t temp = 0;

      temp = complex_data_ptr[index[m]];  /* Real and imaginary */
      complex_data_ptr[index[m]] = complex_data_ptr[index[m + 1]];
      complex_data_ptr[index[m + 1]] = temp;
    }
  }
  else {
    int m = 0, mr = 0, l = 0;
    int n = 1 << stages;
    int nn = n - 1;

    /* Decimation in time - re-order data */
    for (m = 1; m <= nn; ++m) {
      int32_t* complex_data_ptr = (int32_t*)complex_data;
      int32_t temp = 0;

      /* Find out indexes that are bit-reversed. */
      l = n;
      do {
        l >>= 1;
      } while (l > nn - mr);
      mr = (mr & (l - 1)) + l;

      if (mr <= m) {
        continue;
      }

      /* Swap the elements with bit-reversed indexes.
       * This is similar to the loop in the stages == 7 or 8 cases.
       */
      temp = complex_data_ptr[m];  /* Real and imaginary */
      complex_data_ptr[m] = complex_data_ptr[mr];
      complex_data_ptr[mr] = temp;
    }
  }
}
