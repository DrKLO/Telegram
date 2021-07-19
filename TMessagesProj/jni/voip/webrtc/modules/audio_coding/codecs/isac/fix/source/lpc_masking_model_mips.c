/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/isac/fix/source/lpc_masking_model.h"

// MIPS DSPR2 optimization for function WebRtcIsacfix_CalculateResidualEnergy
// Bit-exact with WebRtcIsacfix_CalculateResidualEnergyC from file
// lpc_masking_model.c
int32_t WebRtcIsacfix_CalculateResidualEnergyMIPS(int lpc_order,
                                                  int32_t q_val_corr,
                                                  int q_val_polynomial,
                                                  int16_t* a_polynomial,
                                                  int32_t* corr_coeffs,
                                                  int* q_val_residual_energy) {

  int i = 0, j = 0;
  int shift_internal = 0, shift_norm = 0;
  int32_t tmp32 = 0, word32_high = 0, word32_low = 0, residual_energy = 0;
  int32_t tmp_corr_c = corr_coeffs[0];
  int16_t* tmp_a_poly = &a_polynomial[0];
  int32_t sum64_hi = 0;
  int32_t sum64_lo = 0;

  for (j = 0; j <= lpc_order; j++) {
    // For the case of i == 0:
    //   residual_energy +=
    //     a_polynomial[j] * corr_coeffs[i] * a_polynomial[j - i];

    int32_t tmp2, tmp3;
    int16_t sign_1;
    int16_t sign_2;
    int16_t sign_3;

    __asm __volatile (
      ".set      push                                                \n\t"
      ".set      noreorder                                           \n\t"
      "lh        %[tmp2],         0(%[tmp_a_poly])                   \n\t"
      "mul       %[tmp32],        %[tmp2],            %[tmp2]        \n\t"
      "addiu     %[tmp_a_poly],   %[tmp_a_poly],      2              \n\t"
      "sra       %[sign_2],       %[sum64_hi],        31             \n\t"
      "mult      $ac0,            %[tmp32],           %[tmp_corr_c]  \n\t"
      "shilov    $ac0,            %[shift_internal]                  \n\t"
      "mfhi      %[tmp2],         $ac0                               \n\t"
      "mflo      %[tmp3],         $ac0                               \n\t"
      "sra       %[sign_1],       %[tmp2],            31             \n\t"
      "xor       %[sign_3],       %[sign_1],          %[sign_2]      \n\t"
      ".set      pop                                                 \n\t"
      : [tmp2] "=&r" (tmp2), [tmp3] "=&r" (tmp3), [tmp32] "=&r" (tmp32),
        [tmp_a_poly] "+r" (tmp_a_poly), [sign_1] "=&r" (sign_1),
        [sign_3] "=&r" (sign_3), [sign_2] "=&r" (sign_2),
        [sum64_hi] "+r" (sum64_hi), [sum64_lo] "+r" (sum64_lo)
      : [tmp_corr_c] "r" (tmp_corr_c), [shift_internal] "r" (shift_internal)
      : "hi", "lo", "memory"
    );

    if (sign_3 != 0) {
      __asm __volatile (
        ".set      push                                      \n\t"
        ".set      noreorder                                 \n\t"
        "addsc     %[sum64_lo],   %[sum64_lo],    %[tmp3]    \n\t"
        "addwc     %[sum64_hi],   %[sum64_hi],    %[tmp2]    \n\t"
        ".set      pop                                       \n\t"
        : [sum64_hi] "+r" (sum64_hi), [sum64_lo] "+r" (sum64_lo)
        : [tmp2] "r" (tmp2), [tmp3] "r" (tmp3)
        : "hi", "lo", "memory"
      );
    } else {
      if (((!(sign_1 || sign_2)) && (0x7FFFFFFF - sum64_hi < tmp2)) ||
          ((sign_1 && sign_2) && (sum64_hi + tmp2 > 0))) {
        // Shift right for overflow.
        __asm __volatile (
          ".set      push                                             \n\t"
          ".set      noreorder                                        \n\t"
          "addiu     %[shift_internal], %[shift_internal],  1         \n\t"
          "prepend   %[sum64_lo],       %[sum64_hi],        1         \n\t"
          "sra       %[sum64_hi],       %[sum64_hi],        1         \n\t"
          "prepend   %[tmp3],           %[tmp2],            1         \n\t"
          "sra       %[tmp2],           %[tmp2],            1         \n\t"
          "addsc     %[sum64_lo],       %[sum64_lo],        %[tmp3]   \n\t"
          "addwc     %[sum64_hi],       %[sum64_hi],        %[tmp2]   \n\t"
          ".set      pop                                              \n\t"
          : [tmp2] "+r" (tmp2), [tmp3] "+r" (tmp3),
            [shift_internal] "+r" (shift_internal),
            [sum64_hi] "+r" (sum64_hi), [sum64_lo] "+r" (sum64_lo)
          :
          : "hi", "lo", "memory"
        );
      } else {
        __asm __volatile (
          ".set      push                                      \n\t"
          ".set      noreorder                                 \n\t"
          "addsc     %[sum64_lo],   %[sum64_lo],    %[tmp3]    \n\t"
          "addwc     %[sum64_hi],   %[sum64_hi],    %[tmp2]    \n\t"
          ".set      pop                                       \n\t"
          : [sum64_hi] "+r" (sum64_hi), [sum64_lo] "+r" (sum64_lo)
          : [tmp2] "r" (tmp2), [tmp3] "r" (tmp3)
          : "hi", "lo", "memory"
        );
      }
    }
  }

  for (i = 1; i <= lpc_order; i++) {
    tmp_corr_c = corr_coeffs[i];
    int16_t* tmp_a_poly_j = &a_polynomial[i];
    int16_t* tmp_a_poly_j_i = &a_polynomial[0];
    for (j = i; j <= lpc_order; j++) {
      // For the case of i = 1 .. lpc_order:
      //   residual_energy +=
      //     a_polynomial[j] * corr_coeffs[i] * a_polynomial[j - i] * 2;

      int32_t tmp2, tmp3;
      int16_t sign_1;
      int16_t sign_2;
      int16_t sign_3;

      __asm __volatile (
        ".set      push                                                   \n\t"
        ".set      noreorder                                              \n\t"
        "lh        %[tmp3],           0(%[tmp_a_poly_j])                  \n\t"
        "lh        %[tmp2],           0(%[tmp_a_poly_j_i])                \n\t"
        "addiu     %[tmp_a_poly_j],   %[tmp_a_poly_j],    2               \n\t"
        "addiu     %[tmp_a_poly_j_i], %[tmp_a_poly_j_i],  2               \n\t"
        "mul       %[tmp32],          %[tmp3],            %[tmp2]         \n\t"
        "sll       %[tmp32],          %[tmp32],           1               \n\t"
        "mult      $ac0,              %[tmp32],           %[tmp_corr_c]   \n\t"
        "shilov    $ac0,              %[shift_internal]                   \n\t"
        "mfhi      %[tmp2],           $ac0                                \n\t"
        "mflo      %[tmp3],           $ac0                                \n\t"
        "sra       %[sign_1],         %[tmp2],            31              \n\t"
        "sra       %[sign_2],         %[sum64_hi],        31              \n\t"
        "xor       %[sign_3],         %[sign_1],          %[sign_2]       \n\t"
        ".set      pop                                                    \n\t"
        : [tmp2] "=&r" (tmp2), [tmp3] "=&r" (tmp3), [tmp32] "=&r" (tmp32),
          [tmp_a_poly_j] "+r" (tmp_a_poly_j), [sign_1] "=&r" (sign_1),
          [tmp_a_poly_j_i] "+r" (tmp_a_poly_j_i), [sign_2] "=&r" (sign_2),
          [sign_3] "=&r" (sign_3), [sum64_hi] "+r" (sum64_hi),
          [sum64_lo] "+r" (sum64_lo)
        : [tmp_corr_c] "r" (tmp_corr_c), [shift_internal] "r" (shift_internal)
        : "hi", "lo", "memory"
      );
      if (sign_3 != 0) {
        __asm __volatile (
          ".set      push                                     \n\t"
          ".set      noreorder                                \n\t"
          "addsc     %[sum64_lo],   %[sum64_lo],   %[tmp3]    \n\t"
          "addwc     %[sum64_hi],   %[sum64_hi],   %[tmp2]    \n\t"
          ".set      pop                                      \n\t"
          : [tmp2] "+r" (tmp2), [tmp3] "+r" (tmp3), [sum64_hi] "+r" (sum64_hi),
            [sum64_lo] "+r" (sum64_lo)
          :
          :"memory"
        );
      } else {
        // Test overflow and sum the result.
        if (((!(sign_1 || sign_2)) && (0x7FFFFFFF - sum64_hi < tmp2)) ||
            ((sign_1 && sign_2) && (sum64_hi + tmp2 > 0))) {
          // Shift right for overflow.
          __asm __volatile (
            ".set      push                                              \n\t"
            ".set      noreorder                                         \n\t"
            "addiu     %[shift_internal],  %[shift_internal],  1         \n\t"
            "prepend   %[sum64_lo],        %[sum64_hi],        1         \n\t"
            "sra       %[sum64_hi],        %[sum64_hi],        1         \n\t"
            "prepend   %[tmp3],            %[tmp2],            1         \n\t"
            "sra       %[tmp2],            %[tmp2],            1         \n\t"
            "addsc     %[sum64_lo],        %[sum64_lo],        %[tmp3]   \n\t"
            "addwc     %[sum64_hi],        %[sum64_hi],        %[tmp2]   \n\t"
            ".set      pop                                               \n\t"
            : [tmp2] "+r" (tmp2), [tmp3] "+r" (tmp3),
              [shift_internal] "+r" (shift_internal),
              [sum64_hi] "+r" (sum64_hi), [sum64_lo] "+r" (sum64_lo)
            :
            : "hi", "lo", "memory"
          );
        } else {
          __asm __volatile (
            ".set      push                                      \n\t"
            ".set      noreorder                                 \n\t"
            "addsc     %[sum64_lo],    %[sum64_lo],   %[tmp3]    \n\t"
            "addwc     %[sum64_hi],    %[sum64_hi],   %[tmp2]    \n\t"
            ".set      pop                                       \n\t"
            : [tmp2] "+r" (tmp2), [tmp3] "+r" (tmp3),
              [sum64_hi] "+r" (sum64_hi), [sum64_lo] "+r" (sum64_lo)
            :
            : "hi", "lo", "memory"
          );
        }
      }
    }
  }
  word32_high = sum64_hi;
  word32_low = sum64_lo;

  // Calculate the value of shifting (shift_norm) for the 64-bit sum.
  if (word32_high != 0) {
    shift_norm = 32 - WebRtcSpl_NormW32(word32_high);
    int tmp1;
    __asm __volatile (
      ".set    push                                                     \n\t"
      ".set    noreorder                                                \n\t"
      "srl     %[residual_energy],  %[sum64_lo],         %[shift_norm]  \n\t"
      "li      %[tmp1],             32                                  \n\t"
      "subu    %[tmp1],             %[tmp1],             %[shift_norm]  \n\t"
      "sll     %[tmp1],             %[sum64_hi],         %[tmp1]        \n\t"
      "or      %[residual_energy],  %[residual_energy],  %[tmp1]        \n\t"
      ".set    pop                                                      \n\t"
      : [residual_energy] "=&r" (residual_energy), [tmp1]"=&r"(tmp1),
        [sum64_hi] "+r" (sum64_hi), [sum64_lo] "+r" (sum64_lo)
      : [shift_norm] "r" (shift_norm)
      : "memory"
    );
  } else {
    if ((word32_low & 0x80000000) != 0) {
      shift_norm = 1;
      residual_energy = (uint32_t)word32_low >> 1;
    } else {
      shift_norm = WebRtcSpl_NormW32(word32_low);
      residual_energy = word32_low << shift_norm;
      shift_norm = -shift_norm;
    }
  }

  // Q(q_val_polynomial * 2) * Q(q_val_corr) >> shift_internal >> shift_norm
  //   = Q(q_val_corr - shift_internal - shift_norm + q_val_polynomial * 2)
  *q_val_residual_energy =
      q_val_corr - shift_internal - shift_norm + q_val_polynomial * 2;

  return residual_energy;
}
