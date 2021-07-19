/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <arm_neon.h>

#include "modules/audio_coding/codecs/isac/fix/source/codec.h"
#include "modules/audio_coding/codecs/isac/fix/source/settings.h"

// Contains a function for the core loop in the normalized lattice MA
// filter routine for iSAC codec, optimized for ARM Neon platform.
// It does:
//  for 0 <= n < HALF_SUBFRAMELEN - 1:
//    *ptr2 = input2 * ((*ptr2) + input0 * (*ptr0));
//    *ptr1 = input1 * (*ptr0) + input0 * (*ptr2);
// Output is not bit-exact with the reference C code, due to the replacement
// of WEBRTC_SPL_MUL_16_32_RSFT15 and LATTICE_MUL_32_32_RSFT16 with Neon
// instructions. The difference should not be bigger than 1.
void WebRtcIsacfix_FilterMaLoopNeon(int16_t input0,  // Filter coefficient
                                    int16_t input1,  // Filter coefficient
                                    int32_t input2,  // Inverse coefficient
                                    int32_t* ptr0,   // Sample buffer
                                    int32_t* ptr1,   // Sample buffer
                                    int32_t* ptr2)   // Sample buffer
{
  int n = 0;
  int loop = (HALF_SUBFRAMELEN - 1) >> 3;
  int loop_tail = (HALF_SUBFRAMELEN - 1) & 0x7;

  int32x4_t input0_v = vdupq_n_s32((int32_t)input0 << 16);
  int32x4_t input1_v = vdupq_n_s32((int32_t)input1 << 16);
  int32x4_t input2_v = vdupq_n_s32(input2);
  int32x4_t tmp0a, tmp1a, tmp2a, tmp3a;
  int32x4_t tmp0b, tmp1b, tmp2b, tmp3b;
  int32x4_t ptr0va, ptr1va, ptr2va;
  int32x4_t ptr0vb, ptr1vb, ptr2vb;

  int64x2_t tmp2al_low, tmp2al_high, tmp2bl_low, tmp2bl_high;
  // Unroll to process 8 samples at once.
  for (n = 0; n < loop; n++) {
    ptr0va = vld1q_s32(ptr0);
    ptr0vb = vld1q_s32(ptr0 + 4);
    ptr0 += 8;

    ptr2va = vld1q_s32(ptr2);
    ptr2vb = vld1q_s32(ptr2 + 4);

    // Calculate tmp0 = (*ptr0) * input0.
    tmp0a = vqrdmulhq_s32(ptr0va, input0_v);
    tmp0b = vqrdmulhq_s32(ptr0vb, input0_v);

    // Calculate tmp1 = (*ptr0) * input1.
    tmp1a = vqrdmulhq_s32(ptr0va, input1_v);
    tmp1b = vqrdmulhq_s32(ptr0vb, input1_v);

    // Calculate tmp2 = tmp0 + *(ptr2).
    tmp2a = vaddq_s32(tmp0a, ptr2va);
    tmp2b = vaddq_s32(tmp0b, ptr2vb);

    // Calculate *ptr2 = input2 * tmp2.
    tmp2al_low = vmull_s32(vget_low_s32(tmp2a), vget_low_s32(input2_v));
#if defined(WEBRTC_ARCH_ARM64)
    tmp2al_high = vmull_high_s32(tmp2a, input2_v);
#else
    tmp2al_high = vmull_s32(vget_high_s32(tmp2a), vget_high_s32(input2_v));
#endif
    ptr2va = vcombine_s32(vrshrn_n_s64(tmp2al_low, 16),
                          vrshrn_n_s64(tmp2al_high, 16));

    tmp2bl_low = vmull_s32(vget_low_s32(tmp2b), vget_low_s32(input2_v));
#if defined(WEBRTC_ARCH_ARM64)
    tmp2bl_high = vmull_high_s32(tmp2b, input2_v);
#else
    tmp2bl_high = vmull_s32(vget_high_s32(tmp2b), vget_high_s32(input2_v));
#endif
    ptr2vb = vcombine_s32(vrshrn_n_s64(tmp2bl_low, 16),
                          vrshrn_n_s64(tmp2bl_high, 16));

    vst1q_s32(ptr2, ptr2va);
    vst1q_s32(ptr2 + 4, ptr2vb);
    ptr2 += 8;

    // Calculate tmp3 = ptr2v * input0.
    tmp3a = vqrdmulhq_s32(ptr2va, input0_v);
    tmp3b = vqrdmulhq_s32(ptr2vb, input0_v);

    // Calculate *ptr1 = tmp1 + tmp3.
    ptr1va = vaddq_s32(tmp1a, tmp3a);
    ptr1vb = vaddq_s32(tmp1b, tmp3b);

    vst1q_s32(ptr1, ptr1va);
    vst1q_s32(ptr1 + 4, ptr1vb);
    ptr1 += 8;
  }

  // Process four more samples.
  if (loop_tail & 0x4) {
    ptr0va = vld1q_s32(ptr0);
    ptr2va = vld1q_s32(ptr2);
    ptr0 += 4;

    // Calculate tmp0 = (*ptr0) * input0.
    tmp0a = vqrdmulhq_s32(ptr0va, input0_v);

    // Calculate tmp1 = (*ptr0) * input1.
    tmp1a = vqrdmulhq_s32(ptr0va, input1_v);

    // Calculate tmp2 = tmp0 + *(ptr2).
    tmp2a = vaddq_s32(tmp0a, ptr2va);

    // Calculate *ptr2 = input2 * tmp2.
    tmp2al_low = vmull_s32(vget_low_s32(tmp2a), vget_low_s32(input2_v));

#if defined(WEBRTC_ARCH_ARM64)
    tmp2al_high = vmull_high_s32(tmp2a, input2_v);
#else
    tmp2al_high = vmull_s32(vget_high_s32(tmp2a), vget_high_s32(input2_v));
#endif
    ptr2va = vcombine_s32(vrshrn_n_s64(tmp2al_low, 16),
                          vrshrn_n_s64(tmp2al_high, 16));

    vst1q_s32(ptr2, ptr2va);
    ptr2 += 4;

    // Calculate tmp3 = *(ptr2) * input0.
    tmp3a = vqrdmulhq_s32(ptr2va, input0_v);

    // Calculate *ptr1 = tmp1 + tmp3.
    ptr1va = vaddq_s32(tmp1a, tmp3a);

    vst1q_s32(ptr1, ptr1va);
    ptr1 += 4;
  }

  // Process two more samples.
  if (loop_tail & 0x2) {
    int32x2_t ptr0v_tail, ptr2v_tail, ptr1v_tail;
    int32x2_t tmp0_tail, tmp1_tail, tmp2_tail, tmp3_tail;
    int64x2_t tmp2l_tail;
    ptr0v_tail = vld1_s32(ptr0);
    ptr2v_tail = vld1_s32(ptr2);
    ptr0 += 2;

    // Calculate tmp0 = (*ptr0) * input0.
    tmp0_tail = vqrdmulh_s32(ptr0v_tail, vget_low_s32(input0_v));

    // Calculate tmp1 = (*ptr0) * input1.
    tmp1_tail = vqrdmulh_s32(ptr0v_tail, vget_low_s32(input1_v));

    // Calculate tmp2 = tmp0 + *(ptr2).
    tmp2_tail = vadd_s32(tmp0_tail, ptr2v_tail);

    // Calculate *ptr2 = input2 * tmp2.
    tmp2l_tail = vmull_s32(tmp2_tail, vget_low_s32(input2_v));
    ptr2v_tail = vrshrn_n_s64(tmp2l_tail, 16);

    vst1_s32(ptr2, ptr2v_tail);
    ptr2 += 2;

    // Calculate tmp3 = *(ptr2) * input0.
    tmp3_tail = vqrdmulh_s32(ptr2v_tail, vget_low_s32(input0_v));

    // Calculate *ptr1 = tmp1 + tmp3.
    ptr1v_tail = vadd_s32(tmp1_tail, tmp3_tail);

    vst1_s32(ptr1, ptr1v_tail);
    ptr1 += 2;
  }

  // Process one more sample.
  if (loop_tail & 0x1) {
    int16_t t16a = (int16_t)(input2 >> 16);
    int16_t t16b = (int16_t)input2;
    if (t16b < 0) t16a++;
    int32_t tmp32a;
    int32_t tmp32b;

    // Calculate *ptr2 = input2 * (*ptr2 + input0 * (*ptr0)).
    tmp32a = WEBRTC_SPL_MUL_16_32_RSFT15(input0, *ptr0);
    tmp32b = *ptr2 + tmp32a;
    *ptr2 = (int32_t)(WEBRTC_SPL_MUL(t16a, tmp32b) +
                       (WEBRTC_SPL_MUL_16_32_RSFT16(t16b, tmp32b)));

    // Calculate *ptr1 = input1 * (*ptr0) + input0 * (*ptr2).
    tmp32a = WEBRTC_SPL_MUL_16_32_RSFT15(input1, *ptr0);
    tmp32b = WEBRTC_SPL_MUL_16_32_RSFT15(input0, *ptr2);
    *ptr1 = tmp32a + tmp32b;
  }
}
