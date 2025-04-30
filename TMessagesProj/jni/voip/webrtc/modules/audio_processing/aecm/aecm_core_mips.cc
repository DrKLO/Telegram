/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aecm/aecm_core.h"
#include "modules/audio_processing/aecm/echo_control_mobile.h"
#include "modules/audio_processing/utility/delay_estimator_wrapper.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_conversions.h"

namespace webrtc {

namespace {

static const ALIGN8_BEG int16_t WebRtcAecm_kSqrtHanning[] ALIGN8_END = {
    0,     399,   798,   1196,  1594,  1990,  2386,  2780,  3172,  3562,  3951,
    4337,  4720,  5101,  5478,  5853,  6224,  6591,  6954,  7313,  7668,  8019,
    8364,  8705,  9040,  9370,  9695,  10013, 10326, 10633, 10933, 11227, 11514,
    11795, 12068, 12335, 12594, 12845, 13089, 13325, 13553, 13773, 13985, 14189,
    14384, 14571, 14749, 14918, 15079, 15231, 15373, 15506, 15631, 15746, 15851,
    15947, 16034, 16111, 16179, 16237, 16286, 16325, 16354, 16373, 16384};

static const int16_t kNoiseEstQDomain = 15;
static const int16_t kNoiseEstIncCount = 5;

static int16_t coefTable[] = {
    0,   4,   256, 260, 128, 132, 384, 388, 64,  68,  320, 324, 192, 196, 448,
    452, 32,  36,  288, 292, 160, 164, 416, 420, 96,  100, 352, 356, 224, 228,
    480, 484, 16,  20,  272, 276, 144, 148, 400, 404, 80,  84,  336, 340, 208,
    212, 464, 468, 48,  52,  304, 308, 176, 180, 432, 436, 112, 116, 368, 372,
    240, 244, 496, 500, 8,   12,  264, 268, 136, 140, 392, 396, 72,  76,  328,
    332, 200, 204, 456, 460, 40,  44,  296, 300, 168, 172, 424, 428, 104, 108,
    360, 364, 232, 236, 488, 492, 24,  28,  280, 284, 152, 156, 408, 412, 88,
    92,  344, 348, 216, 220, 472, 476, 56,  60,  312, 316, 184, 188, 440, 444,
    120, 124, 376, 380, 248, 252, 504, 508};

static int16_t coefTable_ifft[] = {
    0,   512, 256, 508, 128, 252, 384, 380, 64,  124, 320, 444, 192, 188, 448,
    316, 32,  60,  288, 476, 160, 220, 416, 348, 96,  92,  352, 412, 224, 156,
    480, 284, 16,  28,  272, 492, 144, 236, 400, 364, 80,  108, 336, 428, 208,
    172, 464, 300, 48,  44,  304, 460, 176, 204, 432, 332, 112, 76,  368, 396,
    240, 140, 496, 268, 8,   12,  264, 500, 136, 244, 392, 372, 72,  116, 328,
    436, 200, 180, 456, 308, 40,  52,  296, 468, 168, 212, 424, 340, 104, 84,
    360, 404, 232, 148, 488, 276, 24,  20,  280, 484, 152, 228, 408, 356, 88,
    100, 344, 420, 216, 164, 472, 292, 56,  36,  312, 452, 184, 196, 440, 324,
    120, 68,  376, 388, 248, 132, 504, 260};

}  // namespace

static void ComfortNoise(AecmCore* aecm,
                         const uint16_t* dfa,
                         ComplexInt16* out,
                         const int16_t* lambda);

static void WindowAndFFT(AecmCore* aecm,
                         int16_t* fft,
                         const int16_t* time_signal,
                         ComplexInt16* freq_signal,
                         int time_signal_scaling) {
  int i, j;
  int32_t tmp1, tmp2, tmp3, tmp4;
  int16_t* pfrfi;
  ComplexInt16* pfreq_signal;
  int16_t f_coef, s_coef;
  int32_t load_ptr, store_ptr1, store_ptr2, shift, shift1;
  int32_t hann, hann1, coefs;

  memset(fft, 0, sizeof(int16_t) * PART_LEN4);

  // FFT of signal
  __asm __volatile(
      ".set        push                                                    \n\t"
      ".set        noreorder                                               \n\t"
      "addiu       %[shift],          %[time_signal_scaling], -14          \n\t"
      "addiu       %[i],              $zero,                  64           \n\t"
      "addiu       %[load_ptr],       %[time_signal],         0            \n\t"
      "addiu       %[hann],           %[hanning],             0            \n\t"
      "addiu       %[hann1],          %[hanning],             128          \n\t"
      "addiu       %[coefs],          %[coefTable],           0            \n\t"
      "bltz        %[shift],          2f                                   \n\t"
      " negu       %[shift1],         %[shift]                             \n\t"
      "1:                                                                   "
      "\n\t"
      "lh          %[tmp1],           0(%[load_ptr])                       \n\t"
      "lh          %[tmp2],           0(%[hann])                           \n\t"
      "lh          %[tmp3],           128(%[load_ptr])                     \n\t"
      "lh          %[tmp4],           0(%[hann1])                          \n\t"
      "addiu       %[i],              %[i],                   -1           \n\t"
      "mul         %[tmp1],           %[tmp1],                %[tmp2]      \n\t"
      "mul         %[tmp3],           %[tmp3],                %[tmp4]      \n\t"
      "lh          %[f_coef],         0(%[coefs])                          \n\t"
      "lh          %[s_coef],         2(%[coefs])                          \n\t"
      "addiu       %[load_ptr],       %[load_ptr],            2            \n\t"
      "addiu       %[hann],           %[hann],                2            \n\t"
      "addiu       %[hann1],          %[hann1],               -2           \n\t"
      "addu        %[store_ptr1],     %[fft],                 %[f_coef]    \n\t"
      "addu        %[store_ptr2],     %[fft],                 %[s_coef]    \n\t"
      "sllv        %[tmp1],           %[tmp1],                %[shift]     \n\t"
      "sllv        %[tmp3],           %[tmp3],                %[shift]     \n\t"
      "sh          %[tmp1],           0(%[store_ptr1])                     \n\t"
      "sh          %[tmp3],           0(%[store_ptr2])                     \n\t"
      "bgtz        %[i],              1b                                   \n\t"
      " addiu      %[coefs],          %[coefs],               4            \n\t"
      "b           3f                                                      \n\t"
      " nop                                                                \n\t"
      "2:                                                                   "
      "\n\t"
      "lh          %[tmp1],           0(%[load_ptr])                       \n\t"
      "lh          %[tmp2],           0(%[hann])                           \n\t"
      "lh          %[tmp3],           128(%[load_ptr])                     \n\t"
      "lh          %[tmp4],           0(%[hann1])                          \n\t"
      "addiu       %[i],              %[i],                   -1           \n\t"
      "mul         %[tmp1],           %[tmp1],                %[tmp2]      \n\t"
      "mul         %[tmp3],           %[tmp3],                %[tmp4]      \n\t"
      "lh          %[f_coef],         0(%[coefs])                          \n\t"
      "lh          %[s_coef],         2(%[coefs])                          \n\t"
      "addiu       %[load_ptr],       %[load_ptr],            2            \n\t"
      "addiu       %[hann],           %[hann],                2            \n\t"
      "addiu       %[hann1],          %[hann1],               -2           \n\t"
      "addu        %[store_ptr1],     %[fft],                 %[f_coef]    \n\t"
      "addu        %[store_ptr2],     %[fft],                 %[s_coef]    \n\t"
      "srav        %[tmp1],           %[tmp1],                %[shift1]    \n\t"
      "srav        %[tmp3],           %[tmp3],                %[shift1]    \n\t"
      "sh          %[tmp1],           0(%[store_ptr1])                     \n\t"
      "sh          %[tmp3],           0(%[store_ptr2])                     \n\t"
      "bgtz        %[i],              2b                                   \n\t"
      " addiu      %[coefs],          %[coefs],               4            \n\t"
      "3:                                                                   "
      "\n\t"
      ".set        pop                                                     \n\t"
      : [load_ptr] "=&r"(load_ptr), [shift] "=&r"(shift), [hann] "=&r"(hann),
        [hann1] "=&r"(hann1), [shift1] "=&r"(shift1), [coefs] "=&r"(coefs),
        [tmp1] "=&r"(tmp1), [tmp2] "=&r"(tmp2), [tmp3] "=&r"(tmp3),
        [tmp4] "=&r"(tmp4), [i] "=&r"(i), [f_coef] "=&r"(f_coef),
        [s_coef] "=&r"(s_coef), [store_ptr1] "=&r"(store_ptr1),
        [store_ptr2] "=&r"(store_ptr2)
      : [time_signal] "r"(time_signal), [coefTable] "r"(coefTable),
        [time_signal_scaling] "r"(time_signal_scaling),
        [hanning] "r"(WebRtcAecm_kSqrtHanning), [fft] "r"(fft)
      : "memory", "hi", "lo");

  WebRtcSpl_ComplexFFT(fft, PART_LEN_SHIFT, 1);
  pfrfi = fft;
  pfreq_signal = freq_signal;

  __asm __volatile(
      ".set        push                                                     "
      "\n\t"
      ".set        noreorder                                                "
      "\n\t"
      "addiu       %[j],              $zero,                 128            "
      "\n\t"
      "1:                                                                    "
      "\n\t"
      "lh          %[tmp1],           0(%[pfrfi])                           "
      "\n\t"
      "lh          %[tmp2],           2(%[pfrfi])                           "
      "\n\t"
      "lh          %[tmp3],           4(%[pfrfi])                           "
      "\n\t"
      "lh          %[tmp4],           6(%[pfrfi])                           "
      "\n\t"
      "subu        %[tmp2],           $zero,                 %[tmp2]        "
      "\n\t"
      "sh          %[tmp1],           0(%[pfreq_signal])                    "
      "\n\t"
      "sh          %[tmp2],           2(%[pfreq_signal])                    "
      "\n\t"
      "subu        %[tmp4],           $zero,                 %[tmp4]        "
      "\n\t"
      "sh          %[tmp3],           4(%[pfreq_signal])                    "
      "\n\t"
      "sh          %[tmp4],           6(%[pfreq_signal])                    "
      "\n\t"
      "lh          %[tmp1],           8(%[pfrfi])                           "
      "\n\t"
      "lh          %[tmp2],           10(%[pfrfi])                          "
      "\n\t"
      "lh          %[tmp3],           12(%[pfrfi])                          "
      "\n\t"
      "lh          %[tmp4],           14(%[pfrfi])                          "
      "\n\t"
      "addiu       %[j],              %[j],                  -8             "
      "\n\t"
      "subu        %[tmp2],           $zero,                 %[tmp2]        "
      "\n\t"
      "sh          %[tmp1],           8(%[pfreq_signal])                    "
      "\n\t"
      "sh          %[tmp2],           10(%[pfreq_signal])                   "
      "\n\t"
      "subu        %[tmp4],           $zero,                 %[tmp4]        "
      "\n\t"
      "sh          %[tmp3],           12(%[pfreq_signal])                   "
      "\n\t"
      "sh          %[tmp4],           14(%[pfreq_signal])                   "
      "\n\t"
      "addiu       %[pfreq_signal],   %[pfreq_signal],       16             "
      "\n\t"
      "bgtz        %[j],              1b                                    "
      "\n\t"
      " addiu      %[pfrfi],          %[pfrfi],              16             "
      "\n\t"
      ".set        pop                                                      "
      "\n\t"
      : [tmp1] "=&r"(tmp1), [tmp2] "=&r"(tmp2), [tmp3] "=&r"(tmp3),
        [j] "=&r"(j), [pfrfi] "+r"(pfrfi), [pfreq_signal] "+r"(pfreq_signal),
        [tmp4] "=&r"(tmp4)
      :
      : "memory");
}

static void InverseFFTAndWindow(AecmCore* aecm,
                                int16_t* fft,
                                ComplexInt16* efw,
                                int16_t* output,
                                const int16_t* nearendClean) {
  int i, outCFFT;
  int32_t tmp1, tmp2, tmp3, tmp4, tmp_re, tmp_im;
  int16_t* pcoefTable_ifft = coefTable_ifft;
  int16_t* pfft = fft;
  int16_t* ppfft = fft;
  ComplexInt16* pefw = efw;
  int32_t out_aecm;
  int16_t* paecm_buf = aecm->outBuf;
  const int16_t* p_kSqrtHanning = WebRtcAecm_kSqrtHanning;
  const int16_t* pp_kSqrtHanning = &WebRtcAecm_kSqrtHanning[PART_LEN];
  int16_t* output1 = output;

  __asm __volatile(
      ".set      push                                                        "
      "\n\t"
      ".set      noreorder                                                   "
      "\n\t"
      "addiu     %[i],                $zero,                   64            "
      "\n\t"
      "1:                                                                     "
      "\n\t"
      "lh        %[tmp1],             0(%[pcoefTable_ifft])                  "
      "\n\t"
      "lh        %[tmp2],             2(%[pcoefTable_ifft])                  "
      "\n\t"
      "lh        %[tmp_re],           0(%[pefw])                             "
      "\n\t"
      "lh        %[tmp_im],           2(%[pefw])                             "
      "\n\t"
      "addu      %[pfft],             %[fft],                  %[tmp2]       "
      "\n\t"
      "sh        %[tmp_re],           0(%[pfft])                             "
      "\n\t"
      "sh        %[tmp_im],           2(%[pfft])                             "
      "\n\t"
      "addu      %[pfft],             %[fft],                  %[tmp1]       "
      "\n\t"
      "sh        %[tmp_re],           0(%[pfft])                             "
      "\n\t"
      "subu      %[tmp_im],           $zero,                   %[tmp_im]     "
      "\n\t"
      "sh        %[tmp_im],           2(%[pfft])                             "
      "\n\t"
      "lh        %[tmp1],             4(%[pcoefTable_ifft])                  "
      "\n\t"
      "lh        %[tmp2],             6(%[pcoefTable_ifft])                  "
      "\n\t"
      "lh        %[tmp_re],           4(%[pefw])                             "
      "\n\t"
      "lh        %[tmp_im],           6(%[pefw])                             "
      "\n\t"
      "addu      %[pfft],             %[fft],                  %[tmp2]       "
      "\n\t"
      "sh        %[tmp_re],           0(%[pfft])                             "
      "\n\t"
      "sh        %[tmp_im],           2(%[pfft])                             "
      "\n\t"
      "addu      %[pfft],             %[fft],                  %[tmp1]       "
      "\n\t"
      "sh        %[tmp_re],           0(%[pfft])                             "
      "\n\t"
      "subu      %[tmp_im],           $zero,                   %[tmp_im]     "
      "\n\t"
      "sh        %[tmp_im],           2(%[pfft])                             "
      "\n\t"
      "lh        %[tmp1],             8(%[pcoefTable_ifft])                  "
      "\n\t"
      "lh        %[tmp2],             10(%[pcoefTable_ifft])                 "
      "\n\t"
      "lh        %[tmp_re],           8(%[pefw])                             "
      "\n\t"
      "lh        %[tmp_im],           10(%[pefw])                            "
      "\n\t"
      "addu      %[pfft],             %[fft],                  %[tmp2]       "
      "\n\t"
      "sh        %[tmp_re],           0(%[pfft])                             "
      "\n\t"
      "sh        %[tmp_im],           2(%[pfft])                             "
      "\n\t"
      "addu      %[pfft],             %[fft],                  %[tmp1]       "
      "\n\t"
      "sh        %[tmp_re],           0(%[pfft])                             "
      "\n\t"
      "subu      %[tmp_im],           $zero,                   %[tmp_im]     "
      "\n\t"
      "sh        %[tmp_im],           2(%[pfft])                             "
      "\n\t"
      "lh        %[tmp1],             12(%[pcoefTable_ifft])                 "
      "\n\t"
      "lh        %[tmp2],             14(%[pcoefTable_ifft])                 "
      "\n\t"
      "lh        %[tmp_re],           12(%[pefw])                            "
      "\n\t"
      "lh        %[tmp_im],           14(%[pefw])                            "
      "\n\t"
      "addu      %[pfft],             %[fft],                  %[tmp2]       "
      "\n\t"
      "sh        %[tmp_re],           0(%[pfft])                             "
      "\n\t"
      "sh        %[tmp_im],           2(%[pfft])                             "
      "\n\t"
      "addu      %[pfft],             %[fft],                  %[tmp1]       "
      "\n\t"
      "sh        %[tmp_re],           0(%[pfft])                             "
      "\n\t"
      "subu      %[tmp_im],           $zero,                   %[tmp_im]     "
      "\n\t"
      "sh        %[tmp_im],           2(%[pfft])                             "
      "\n\t"
      "addiu     %[pcoefTable_ifft],  %[pcoefTable_ifft],      16            "
      "\n\t"
      "addiu     %[i],                %[i],                    -4            "
      "\n\t"
      "bgtz      %[i],                1b                                     "
      "\n\t"
      " addiu    %[pefw],             %[pefw],                 16            "
      "\n\t"
      ".set      pop                                                         "
      "\n\t"
      : [tmp1] "=&r"(tmp1), [tmp2] "=&r"(tmp2), [pfft] "+r"(pfft), [i] "=&r"(i),
        [tmp_re] "=&r"(tmp_re), [tmp_im] "=&r"(tmp_im), [pefw] "+r"(pefw),
        [pcoefTable_ifft] "+r"(pcoefTable_ifft), [fft] "+r"(fft)
      :
      : "memory");

  fft[2] = efw[PART_LEN].real;
  fft[3] = -efw[PART_LEN].imag;

  outCFFT = WebRtcSpl_ComplexIFFT(fft, PART_LEN_SHIFT, 1);
  pfft = fft;

  __asm __volatile(
      ".set       push                                               \n\t"
      ".set       noreorder                                          \n\t"
      "addiu      %[i],            $zero,               128          \n\t"
      "1:                                                             \n\t"
      "lh         %[tmp1],         0(%[ppfft])                       \n\t"
      "lh         %[tmp2],         4(%[ppfft])                       \n\t"
      "lh         %[tmp3],         8(%[ppfft])                       \n\t"
      "lh         %[tmp4],         12(%[ppfft])                      \n\t"
      "addiu      %[i],            %[i],                -4           \n\t"
      "sh         %[tmp1],         0(%[pfft])                        \n\t"
      "sh         %[tmp2],         2(%[pfft])                        \n\t"
      "sh         %[tmp3],         4(%[pfft])                        \n\t"
      "sh         %[tmp4],         6(%[pfft])                        \n\t"
      "addiu      %[ppfft],        %[ppfft],            16           \n\t"
      "bgtz       %[i],            1b                                \n\t"
      " addiu     %[pfft],         %[pfft],             8            \n\t"
      ".set       pop                                                \n\t"
      : [tmp1] "=&r"(tmp1), [tmp2] "=&r"(tmp2), [pfft] "+r"(pfft), [i] "=&r"(i),
        [tmp3] "=&r"(tmp3), [tmp4] "=&r"(tmp4), [ppfft] "+r"(ppfft)
      :
      : "memory");

  pfft = fft;
  out_aecm = (int32_t)(outCFFT - aecm->dfaCleanQDomain);

  __asm __volatile(
      ".set       push                                                       "
      "\n\t"
      ".set       noreorder                                                  "
      "\n\t"
      "addiu      %[i],                $zero,                  64            "
      "\n\t"
      "11:                                                                    "
      "\n\t"
      "lh         %[tmp1],             0(%[pfft])                            "
      "\n\t"
      "lh         %[tmp2],             0(%[p_kSqrtHanning])                  "
      "\n\t"
      "addiu      %[i],                %[i],                   -2            "
      "\n\t"
      "mul        %[tmp1],             %[tmp1],                %[tmp2]       "
      "\n\t"
      "lh         %[tmp3],             2(%[pfft])                            "
      "\n\t"
      "lh         %[tmp4],             2(%[p_kSqrtHanning])                  "
      "\n\t"
      "mul        %[tmp3],             %[tmp3],                %[tmp4]       "
      "\n\t"
      "addiu      %[tmp1],             %[tmp1],                8192          "
      "\n\t"
      "sra        %[tmp1],             %[tmp1],                14            "
      "\n\t"
      "addiu      %[tmp3],             %[tmp3],                8192          "
      "\n\t"
      "sra        %[tmp3],             %[tmp3],                14            "
      "\n\t"
      "bgez       %[out_aecm],         1f                                    "
      "\n\t"
      " negu      %[tmp2],             %[out_aecm]                           "
      "\n\t"
      "srav       %[tmp1],             %[tmp1],                %[tmp2]       "
      "\n\t"
      "b          2f                                                         "
      "\n\t"
      " srav      %[tmp3],             %[tmp3],                %[tmp2]       "
      "\n\t"
      "1:                                                                     "
      "\n\t"
      "sllv       %[tmp1],             %[tmp1],                %[out_aecm]   "
      "\n\t"
      "sllv       %[tmp3],             %[tmp3],                %[out_aecm]   "
      "\n\t"
      "2:                                                                     "
      "\n\t"
      "lh         %[tmp4],             0(%[paecm_buf])                       "
      "\n\t"
      "lh         %[tmp2],             2(%[paecm_buf])                       "
      "\n\t"
      "addu       %[tmp3],             %[tmp3],                %[tmp2]       "
      "\n\t"
      "addu       %[tmp1],             %[tmp1],                %[tmp4]       "
      "\n\t"
#if defined(MIPS_DSP_R1_LE)
      "shll_s.w   %[tmp1],             %[tmp1],                16            "
      "\n\t"
      "sra        %[tmp1],             %[tmp1],                16            "
      "\n\t"
      "shll_s.w   %[tmp3],             %[tmp3],                16            "
      "\n\t"
      "sra        %[tmp3],             %[tmp3],                16            "
      "\n\t"
#else   // #if defined(MIPS_DSP_R1_LE)
      "sra        %[tmp4],             %[tmp1],                31            "
      "\n\t"
      "sra        %[tmp2],             %[tmp1],                15            "
      "\n\t"
      "beq        %[tmp4],             %[tmp2],                3f            "
      "\n\t"
      " ori       %[tmp2],             $zero,                  0x7fff        "
      "\n\t"
      "xor        %[tmp1],             %[tmp2],                %[tmp4]       "
      "\n\t"
      "3:                                                                     "
      "\n\t"
      "sra        %[tmp2],             %[tmp3],                31            "
      "\n\t"
      "sra        %[tmp4],             %[tmp3],                15            "
      "\n\t"
      "beq        %[tmp2],             %[tmp4],                4f            "
      "\n\t"
      " ori       %[tmp4],             $zero,                  0x7fff        "
      "\n\t"
      "xor        %[tmp3],             %[tmp4],                %[tmp2]       "
      "\n\t"
      "4:                                                                     "
      "\n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
      "sh         %[tmp1],             0(%[pfft])                            "
      "\n\t"
      "sh         %[tmp1],             0(%[output1])                         "
      "\n\t"
      "sh         %[tmp3],             2(%[pfft])                            "
      "\n\t"
      "sh         %[tmp3],             2(%[output1])                         "
      "\n\t"
      "lh         %[tmp1],             128(%[pfft])                          "
      "\n\t"
      "lh         %[tmp2],             0(%[pp_kSqrtHanning])                 "
      "\n\t"
      "mul        %[tmp1],             %[tmp1],                %[tmp2]       "
      "\n\t"
      "lh         %[tmp3],             130(%[pfft])                          "
      "\n\t"
      "lh         %[tmp4],             -2(%[pp_kSqrtHanning])                "
      "\n\t"
      "mul        %[tmp3],             %[tmp3],                %[tmp4]       "
      "\n\t"
      "sra        %[tmp1],             %[tmp1],                14            "
      "\n\t"
      "sra        %[tmp3],             %[tmp3],                14            "
      "\n\t"
      "bgez       %[out_aecm],         5f                                    "
      "\n\t"
      " negu      %[tmp2],             %[out_aecm]                           "
      "\n\t"
      "srav       %[tmp3],             %[tmp3],                %[tmp2]       "
      "\n\t"
      "b          6f                                                         "
      "\n\t"
      " srav      %[tmp1],             %[tmp1],                %[tmp2]       "
      "\n\t"
      "5:                                                                     "
      "\n\t"
      "sllv       %[tmp1],             %[tmp1],                %[out_aecm]   "
      "\n\t"
      "sllv       %[tmp3],             %[tmp3],                %[out_aecm]   "
      "\n\t"
      "6:                                                                     "
      "\n\t"
#if defined(MIPS_DSP_R1_LE)
      "shll_s.w   %[tmp1],             %[tmp1],                16            "
      "\n\t"
      "sra        %[tmp1],             %[tmp1],                16            "
      "\n\t"
      "shll_s.w   %[tmp3],             %[tmp3],                16            "
      "\n\t"
      "sra        %[tmp3],             %[tmp3],                16            "
      "\n\t"
#else   // #if defined(MIPS_DSP_R1_LE)
      "sra        %[tmp4],             %[tmp1],                31            "
      "\n\t"
      "sra        %[tmp2],             %[tmp1],                15            "
      "\n\t"
      "beq        %[tmp4],             %[tmp2],                7f            "
      "\n\t"
      " ori       %[tmp2],             $zero,                  0x7fff        "
      "\n\t"
      "xor        %[tmp1],             %[tmp2],                %[tmp4]       "
      "\n\t"
      "7:                                                                     "
      "\n\t"
      "sra        %[tmp2],             %[tmp3],                31            "
      "\n\t"
      "sra        %[tmp4],             %[tmp3],                15            "
      "\n\t"
      "beq        %[tmp2],             %[tmp4],                8f            "
      "\n\t"
      " ori       %[tmp4],             $zero,                  0x7fff        "
      "\n\t"
      "xor        %[tmp3],             %[tmp4],                %[tmp2]       "
      "\n\t"
      "8:                                                                     "
      "\n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
      "sh         %[tmp1],             0(%[paecm_buf])                       "
      "\n\t"
      "sh         %[tmp3],             2(%[paecm_buf])                       "
      "\n\t"
      "addiu      %[output1],          %[output1],             4             "
      "\n\t"
      "addiu      %[paecm_buf],        %[paecm_buf],           4             "
      "\n\t"
      "addiu      %[pfft],             %[pfft],                4             "
      "\n\t"
      "addiu      %[p_kSqrtHanning],   %[p_kSqrtHanning],      4             "
      "\n\t"
      "bgtz       %[i],                11b                                   "
      "\n\t"
      " addiu     %[pp_kSqrtHanning],  %[pp_kSqrtHanning],     -4            "
      "\n\t"
      ".set       pop                                                        "
      "\n\t"
      : [tmp1] "=&r"(tmp1), [tmp2] "=&r"(tmp2), [pfft] "+r"(pfft),
        [output1] "+r"(output1), [tmp3] "=&r"(tmp3), [tmp4] "=&r"(tmp4),
        [paecm_buf] "+r"(paecm_buf), [i] "=&r"(i),
        [pp_kSqrtHanning] "+r"(pp_kSqrtHanning),
        [p_kSqrtHanning] "+r"(p_kSqrtHanning)
      : [out_aecm] "r"(out_aecm), [WebRtcAecm_kSqrtHanning] "r"(
                                      WebRtcAecm_kSqrtHanning)
      : "hi", "lo", "memory");

  // Copy the current block to the old position
  // (aecm->outBuf is shifted elsewhere)
  memcpy(aecm->xBuf, aecm->xBuf + PART_LEN, sizeof(int16_t) * PART_LEN);
  memcpy(aecm->dBufNoisy, aecm->dBufNoisy + PART_LEN,
         sizeof(int16_t) * PART_LEN);
  if (nearendClean != NULL) {
    memcpy(aecm->dBufClean, aecm->dBufClean + PART_LEN,
           sizeof(int16_t) * PART_LEN);
  }
}

void WebRtcAecm_CalcLinearEnergies_mips(AecmCore* aecm,
                                        const uint16_t* far_spectrum,
                                        int32_t* echo_est,
                                        uint32_t* far_energy,
                                        uint32_t* echo_energy_adapt,
                                        uint32_t* echo_energy_stored) {
  int i;
  uint32_t par1 = (*far_energy);
  uint32_t par2 = (*echo_energy_adapt);
  uint32_t par3 = (*echo_energy_stored);
  int16_t* ch_stored_p = &(aecm->channelStored[0]);
  int16_t* ch_adapt_p = &(aecm->channelAdapt16[0]);
  uint16_t* spectrum_p = (uint16_t*)(&(far_spectrum[0]));
  int32_t* echo_p = &(echo_est[0]);
  int32_t temp0, stored0, echo0, adept0, spectrum0;
  int32_t stored1, adept1, spectrum1, echo1, temp1;

  // Get energy for the delayed far end signal and estimated
  // echo using both stored and adapted channels.
  for (i = 0; i < PART_LEN; i += 4) {
    __asm __volatile(
        ".set           push                                            \n\t"
        ".set           noreorder                                       \n\t"
        "lh             %[stored0],     0(%[ch_stored_p])               \n\t"
        "lhu            %[adept0],      0(%[ch_adapt_p])                \n\t"
        "lhu            %[spectrum0],   0(%[spectrum_p])                \n\t"
        "lh             %[stored1],     2(%[ch_stored_p])               \n\t"
        "lhu            %[adept1],      2(%[ch_adapt_p])                \n\t"
        "lhu            %[spectrum1],   2(%[spectrum_p])                \n\t"
        "mul            %[echo0],       %[stored0],     %[spectrum0]    \n\t"
        "mul            %[temp0],       %[adept0],      %[spectrum0]    \n\t"
        "mul            %[echo1],       %[stored1],     %[spectrum1]    \n\t"
        "mul            %[temp1],       %[adept1],      %[spectrum1]    \n\t"
        "addu           %[par1],        %[par1],        %[spectrum0]    \n\t"
        "addu           %[par1],        %[par1],        %[spectrum1]    \n\t"
        "addiu          %[echo_p],      %[echo_p],      16              \n\t"
        "addu           %[par3],        %[par3],        %[echo0]        \n\t"
        "addu           %[par2],        %[par2],        %[temp0]        \n\t"
        "addu           %[par3],        %[par3],        %[echo1]        \n\t"
        "addu           %[par2],        %[par2],        %[temp1]        \n\t"
        "usw            %[echo0],       -16(%[echo_p])                  \n\t"
        "usw            %[echo1],       -12(%[echo_p])                  \n\t"
        "lh             %[stored0],     4(%[ch_stored_p])               \n\t"
        "lhu            %[adept0],      4(%[ch_adapt_p])                \n\t"
        "lhu            %[spectrum0],   4(%[spectrum_p])                \n\t"
        "lh             %[stored1],     6(%[ch_stored_p])               \n\t"
        "lhu            %[adept1],      6(%[ch_adapt_p])                \n\t"
        "lhu            %[spectrum1],   6(%[spectrum_p])                \n\t"
        "mul            %[echo0],       %[stored0],     %[spectrum0]    \n\t"
        "mul            %[temp0],       %[adept0],      %[spectrum0]    \n\t"
        "mul            %[echo1],       %[stored1],     %[spectrum1]    \n\t"
        "mul            %[temp1],       %[adept1],      %[spectrum1]    \n\t"
        "addu           %[par1],        %[par1],        %[spectrum0]    \n\t"
        "addu           %[par1],        %[par1],        %[spectrum1]    \n\t"
        "addiu          %[ch_stored_p], %[ch_stored_p], 8               \n\t"
        "addiu          %[ch_adapt_p],  %[ch_adapt_p],  8               \n\t"
        "addiu          %[spectrum_p],  %[spectrum_p],  8               \n\t"
        "addu           %[par3],        %[par3],        %[echo0]        \n\t"
        "addu           %[par2],        %[par2],        %[temp0]        \n\t"
        "addu           %[par3],        %[par3],        %[echo1]        \n\t"
        "addu           %[par2],        %[par2],        %[temp1]        \n\t"
        "usw            %[echo0],       -8(%[echo_p])                   \n\t"
        "usw            %[echo1],       -4(%[echo_p])                   \n\t"
        ".set           pop                                             \n\t"
        : [temp0] "=&r"(temp0), [stored0] "=&r"(stored0),
          [adept0] "=&r"(adept0), [spectrum0] "=&r"(spectrum0),
          [echo0] "=&r"(echo0), [echo_p] "+r"(echo_p), [par3] "+r"(par3),
          [par1] "+r"(par1), [par2] "+r"(par2), [stored1] "=&r"(stored1),
          [adept1] "=&r"(adept1), [echo1] "=&r"(echo1),
          [spectrum1] "=&r"(spectrum1), [temp1] "=&r"(temp1),
          [ch_stored_p] "+r"(ch_stored_p), [ch_adapt_p] "+r"(ch_adapt_p),
          [spectrum_p] "+r"(spectrum_p)
        :
        : "hi", "lo", "memory");
  }

  echo_est[PART_LEN] = WEBRTC_SPL_MUL_16_U16(aecm->channelStored[PART_LEN],
                                             far_spectrum[PART_LEN]);
  par1 += (uint32_t)(far_spectrum[PART_LEN]);
  par2 += aecm->channelAdapt16[PART_LEN] * far_spectrum[PART_LEN];
  par3 += (uint32_t)echo_est[PART_LEN];

  (*far_energy) = par1;
  (*echo_energy_adapt) = par2;
  (*echo_energy_stored) = par3;
}

#if defined(MIPS_DSP_R1_LE)
void WebRtcAecm_StoreAdaptiveChannel_mips(AecmCore* aecm,
                                          const uint16_t* far_spectrum,
                                          int32_t* echo_est) {
  int i;
  int16_t* temp1;
  uint16_t* temp8;
  int32_t temp0, temp2, temp3, temp4, temp5, temp6;
  int32_t* temp7 = &(echo_est[0]);
  temp1 = &(aecm->channelStored[0]);
  temp8 = (uint16_t*)(&far_spectrum[0]);

  // During startup we store the channel every block.
  memcpy(aecm->channelStored, aecm->channelAdapt16,
         sizeof(int16_t) * PART_LEN1);
  // Recalculate echo estimate
  for (i = 0; i < PART_LEN; i += 4) {
    __asm __volatile(
        "ulw            %[temp0],   0(%[temp8])               \n\t"
        "ulw            %[temp2],   0(%[temp1])               \n\t"
        "ulw            %[temp4],   4(%[temp8])               \n\t"
        "ulw            %[temp5],   4(%[temp1])               \n\t"
        "muleq_s.w.phl  %[temp3],   %[temp2],     %[temp0]    \n\t"
        "muleq_s.w.phr  %[temp0],   %[temp2],     %[temp0]    \n\t"
        "muleq_s.w.phl  %[temp6],   %[temp5],     %[temp4]    \n\t"
        "muleq_s.w.phr  %[temp4],   %[temp5],     %[temp4]    \n\t"
        "addiu          %[temp7],   %[temp7],     16          \n\t"
        "addiu          %[temp1],   %[temp1],     8           \n\t"
        "addiu          %[temp8],   %[temp8],     8           \n\t"
        "sra            %[temp3],   %[temp3],     1           \n\t"
        "sra            %[temp0],   %[temp0],     1           \n\t"
        "sra            %[temp6],   %[temp6],     1           \n\t"
        "sra            %[temp4],   %[temp4],     1           \n\t"
        "usw            %[temp3],   -12(%[temp7])             \n\t"
        "usw            %[temp0],   -16(%[temp7])             \n\t"
        "usw            %[temp6],   -4(%[temp7])              \n\t"
        "usw            %[temp4],   -8(%[temp7])              \n\t"
        : [temp0] "=&r"(temp0), [temp2] "=&r"(temp2), [temp3] "=&r"(temp3),
          [temp4] "=&r"(temp4), [temp5] "=&r"(temp5), [temp6] "=&r"(temp6),
          [temp1] "+r"(temp1), [temp8] "+r"(temp8), [temp7] "+r"(temp7)
        :
        : "hi", "lo", "memory");
  }
  echo_est[i] = WEBRTC_SPL_MUL_16_U16(aecm->channelStored[i], far_spectrum[i]);
}

void WebRtcAecm_ResetAdaptiveChannel_mips(AecmCore* aecm) {
  int i;
  int32_t* temp3;
  int16_t* temp0;
  int32_t temp1, temp2, temp4, temp5;

  temp0 = &(aecm->channelStored[0]);
  temp3 = &(aecm->channelAdapt32[0]);

  // The stored channel has a significantly lower MSE than the adaptive one for
  // two consecutive calculations. Reset the adaptive channel.
  memcpy(aecm->channelAdapt16, aecm->channelStored,
         sizeof(int16_t) * PART_LEN1);

  // Restore the W32 channel
  for (i = 0; i < PART_LEN; i += 4) {
    __asm __volatile(
        "ulw            %[temp1], 0(%[temp0])           \n\t"
        "ulw            %[temp4], 4(%[temp0])           \n\t"
        "preceq.w.phl   %[temp2], %[temp1]              \n\t"
        "preceq.w.phr   %[temp1], %[temp1]              \n\t"
        "preceq.w.phl   %[temp5], %[temp4]              \n\t"
        "preceq.w.phr   %[temp4], %[temp4]              \n\t"
        "addiu          %[temp0], %[temp0], 8           \n\t"
        "usw            %[temp2], 4(%[temp3])           \n\t"
        "usw            %[temp1], 0(%[temp3])           \n\t"
        "usw            %[temp5], 12(%[temp3])          \n\t"
        "usw            %[temp4], 8(%[temp3])           \n\t"
        "addiu          %[temp3], %[temp3], 16          \n\t"
        : [temp1] "=&r"(temp1), [temp2] "=&r"(temp2), [temp4] "=&r"(temp4),
          [temp5] "=&r"(temp5), [temp3] "+r"(temp3), [temp0] "+r"(temp0)
        :
        : "memory");
  }

  aecm->channelAdapt32[i] = (int32_t)aecm->channelStored[i] << 16;
}
#endif  // #if defined(MIPS_DSP_R1_LE)

// Transforms a time domain signal into the frequency domain, outputting the
// complex valued signal, absolute value and sum of absolute values.
//
// time_signal          [in]    Pointer to time domain signal
// freq_signal_real     [out]   Pointer to real part of frequency domain array
// freq_signal_imag     [out]   Pointer to imaginary part of frequency domain
//                              array
// freq_signal_abs      [out]   Pointer to absolute value of frequency domain
//                              array
// freq_signal_sum_abs  [out]   Pointer to the sum of all absolute values in
//                              the frequency domain array
// return value                 The Q-domain of current frequency values
//
static int TimeToFrequencyDomain(AecmCore* aecm,
                                 const int16_t* time_signal,
                                 ComplexInt16* freq_signal,
                                 uint16_t* freq_signal_abs,
                                 uint32_t* freq_signal_sum_abs) {
  int i = 0;
  int time_signal_scaling = 0;

  // In fft_buf, +16 for 32-byte alignment.
  int16_t fft_buf[PART_LEN4 + 16];
  int16_t* fft = (int16_t*)(((uintptr_t)fft_buf + 31) & ~31);

  int16_t tmp16no1;
#if !defined(MIPS_DSP_R2_LE)
  int32_t tmp32no1;
  int32_t tmp32no2;
  int16_t tmp16no2;
#else
  int32_t tmp32no10, tmp32no11, tmp32no12, tmp32no13;
  int32_t tmp32no20, tmp32no21, tmp32no22, tmp32no23;
  int16_t* freqp;
  uint16_t* freqabsp;
  uint32_t freqt0, freqt1, freqt2, freqt3;
  uint32_t freqs;
#endif

#ifdef AECM_DYNAMIC_Q
  tmp16no1 = WebRtcSpl_MaxAbsValueW16(time_signal, PART_LEN2);
  time_signal_scaling = WebRtcSpl_NormW16(tmp16no1);
#endif

  WindowAndFFT(aecm, fft, time_signal, freq_signal, time_signal_scaling);

  // Extract imaginary and real part,
  // calculate the magnitude for all frequency bins
  freq_signal[0].imag = 0;
  freq_signal[PART_LEN].imag = 0;
  freq_signal[PART_LEN].real = fft[PART_LEN2];
  freq_signal_abs[0] = (uint16_t)WEBRTC_SPL_ABS_W16(freq_signal[0].real);
  freq_signal_abs[PART_LEN] =
      (uint16_t)WEBRTC_SPL_ABS_W16(freq_signal[PART_LEN].real);
  (*freq_signal_sum_abs) =
      (uint32_t)(freq_signal_abs[0]) + (uint32_t)(freq_signal_abs[PART_LEN]);

#if !defined(MIPS_DSP_R2_LE)
  for (i = 1; i < PART_LEN; i++) {
    if (freq_signal[i].real == 0) {
      freq_signal_abs[i] = (uint16_t)WEBRTC_SPL_ABS_W16(freq_signal[i].imag);
    } else if (freq_signal[i].imag == 0) {
      freq_signal_abs[i] = (uint16_t)WEBRTC_SPL_ABS_W16(freq_signal[i].real);
    } else {
      // Approximation for magnitude of complex fft output
      // magn = sqrt(real^2 + imag^2)
      // magn ~= alpha * max(`imag`,`real`) + beta * min(`imag`,`real`)
      //
      // The parameters alpha and beta are stored in Q15
      tmp16no1 = WEBRTC_SPL_ABS_W16(freq_signal[i].real);
      tmp16no2 = WEBRTC_SPL_ABS_W16(freq_signal[i].imag);
      tmp32no1 = tmp16no1 * tmp16no1;
      tmp32no2 = tmp16no2 * tmp16no2;
      tmp32no2 = WebRtcSpl_AddSatW32(tmp32no1, tmp32no2);
      tmp32no1 = WebRtcSpl_SqrtFloor(tmp32no2);

      freq_signal_abs[i] = (uint16_t)tmp32no1;
    }
    (*freq_signal_sum_abs) += (uint32_t)freq_signal_abs[i];
  }
#else  // #if !defined(MIPS_DSP_R2_LE)
  freqs =
      (uint32_t)(freq_signal_abs[0]) + (uint32_t)(freq_signal_abs[PART_LEN]);
  freqp = &(freq_signal[1].real);

  __asm __volatile(
      "lw             %[freqt0],      0(%[freqp])             \n\t"
      "lw             %[freqt1],      4(%[freqp])             \n\t"
      "lw             %[freqt2],      8(%[freqp])             \n\t"
      "mult           $ac0,           $zero,      $zero       \n\t"
      "mult           $ac1,           $zero,      $zero       \n\t"
      "mult           $ac2,           $zero,      $zero       \n\t"
      "dpaq_s.w.ph    $ac0,           %[freqt0],  %[freqt0]   \n\t"
      "dpaq_s.w.ph    $ac1,           %[freqt1],  %[freqt1]   \n\t"
      "dpaq_s.w.ph    $ac2,           %[freqt2],  %[freqt2]   \n\t"
      "addiu          %[freqp],       %[freqp],   12          \n\t"
      "extr.w         %[tmp32no20],   $ac0,       1           \n\t"
      "extr.w         %[tmp32no21],   $ac1,       1           \n\t"
      "extr.w         %[tmp32no22],   $ac2,       1           \n\t"
      : [freqt0] "=&r"(freqt0), [freqt1] "=&r"(freqt1), [freqt2] "=&r"(freqt2),
        [freqp] "+r"(freqp), [tmp32no20] "=r"(tmp32no20),
        [tmp32no21] "=r"(tmp32no21), [tmp32no22] "=r"(tmp32no22)
      :
      : "memory", "hi", "lo", "$ac1hi", "$ac1lo", "$ac2hi", "$ac2lo");

  tmp32no10 = WebRtcSpl_SqrtFloor(tmp32no20);
  tmp32no11 = WebRtcSpl_SqrtFloor(tmp32no21);
  tmp32no12 = WebRtcSpl_SqrtFloor(tmp32no22);
  freq_signal_abs[1] = (uint16_t)tmp32no10;
  freq_signal_abs[2] = (uint16_t)tmp32no11;
  freq_signal_abs[3] = (uint16_t)tmp32no12;
  freqs += (uint32_t)tmp32no10;
  freqs += (uint32_t)tmp32no11;
  freqs += (uint32_t)tmp32no12;
  freqabsp = &(freq_signal_abs[4]);
  for (i = 4; i < PART_LEN; i += 4) {
    __asm __volatile(
        "ulw            %[freqt0],      0(%[freqp])                 \n\t"
        "ulw            %[freqt1],      4(%[freqp])                 \n\t"
        "ulw            %[freqt2],      8(%[freqp])                 \n\t"
        "ulw            %[freqt3],      12(%[freqp])                \n\t"
        "mult           $ac0,           $zero,          $zero       \n\t"
        "mult           $ac1,           $zero,          $zero       \n\t"
        "mult           $ac2,           $zero,          $zero       \n\t"
        "mult           $ac3,           $zero,          $zero       \n\t"
        "dpaq_s.w.ph    $ac0,           %[freqt0],      %[freqt0]   \n\t"
        "dpaq_s.w.ph    $ac1,           %[freqt1],      %[freqt1]   \n\t"
        "dpaq_s.w.ph    $ac2,           %[freqt2],      %[freqt2]   \n\t"
        "dpaq_s.w.ph    $ac3,           %[freqt3],      %[freqt3]   \n\t"
        "addiu          %[freqp],       %[freqp],       16          \n\t"
        "addiu          %[freqabsp],    %[freqabsp],    8           \n\t"
        "extr.w         %[tmp32no20],   $ac0,           1           \n\t"
        "extr.w         %[tmp32no21],   $ac1,           1           \n\t"
        "extr.w         %[tmp32no22],   $ac2,           1           \n\t"
        "extr.w         %[tmp32no23],   $ac3,           1           \n\t"
        : [freqt0] "=&r"(freqt0), [freqt1] "=&r"(freqt1),
          [freqt2] "=&r"(freqt2), [freqt3] "=&r"(freqt3),
          [tmp32no20] "=r"(tmp32no20), [tmp32no21] "=r"(tmp32no21),
          [tmp32no22] "=r"(tmp32no22), [tmp32no23] "=r"(tmp32no23),
          [freqabsp] "+r"(freqabsp), [freqp] "+r"(freqp)
        :
        : "memory", "hi", "lo", "$ac1hi", "$ac1lo", "$ac2hi", "$ac2lo",
          "$ac3hi", "$ac3lo");

    tmp32no10 = WebRtcSpl_SqrtFloor(tmp32no20);
    tmp32no11 = WebRtcSpl_SqrtFloor(tmp32no21);
    tmp32no12 = WebRtcSpl_SqrtFloor(tmp32no22);
    tmp32no13 = WebRtcSpl_SqrtFloor(tmp32no23);

    __asm __volatile(
        "sh             %[tmp32no10],   -8(%[freqabsp])                 \n\t"
        "sh             %[tmp32no11],   -6(%[freqabsp])                 \n\t"
        "sh             %[tmp32no12],   -4(%[freqabsp])                 \n\t"
        "sh             %[tmp32no13],   -2(%[freqabsp])                 \n\t"
        "addu           %[freqs],       %[freqs],       %[tmp32no10]    \n\t"
        "addu           %[freqs],       %[freqs],       %[tmp32no11]    \n\t"
        "addu           %[freqs],       %[freqs],       %[tmp32no12]    \n\t"
        "addu           %[freqs],       %[freqs],       %[tmp32no13]    \n\t"
        : [freqs] "+r"(freqs)
        : [tmp32no10] "r"(tmp32no10), [tmp32no11] "r"(tmp32no11),
          [tmp32no12] "r"(tmp32no12), [tmp32no13] "r"(tmp32no13),
          [freqabsp] "r"(freqabsp)
        : "memory");
  }

  (*freq_signal_sum_abs) = freqs;
#endif

  return time_signal_scaling;
}

int WebRtcAecm_ProcessBlock(AecmCore* aecm,
                            const int16_t* farend,
                            const int16_t* nearendNoisy,
                            const int16_t* nearendClean,
                            int16_t* output) {
  int i;
  uint32_t xfaSum;
  uint32_t dfaNoisySum;
  uint32_t dfaCleanSum;
  uint32_t echoEst32Gained;
  uint32_t tmpU32;
  int32_t tmp32no1;

  uint16_t xfa[PART_LEN1];
  uint16_t dfaNoisy[PART_LEN1];
  uint16_t dfaClean[PART_LEN1];
  uint16_t* ptrDfaClean = dfaClean;
  const uint16_t* far_spectrum_ptr = NULL;

  // 32 byte aligned buffers (with +8 or +16).
  int16_t fft_buf[PART_LEN4 + 2 + 16];  // +2 to make a loop safe.
  int32_t echoEst32_buf[PART_LEN1 + 8];
  int32_t dfw_buf[PART_LEN2 + 8];
  int32_t efw_buf[PART_LEN2 + 8];

  int16_t* fft = (int16_t*)(((uint32_t)fft_buf + 31) & ~31);
  int32_t* echoEst32 = (int32_t*)(((uint32_t)echoEst32_buf + 31) & ~31);
  ComplexInt16* dfw = (ComplexInt16*)(((uint32_t)dfw_buf + 31) & ~31);
  ComplexInt16* efw = (ComplexInt16*)(((uint32_t)efw_buf + 31) & ~31);

  int16_t hnl[PART_LEN1];
  int16_t numPosCoef = 0;
  int delay;
  int16_t tmp16no1;
  int16_t tmp16no2;
  int16_t mu;
  int16_t supGain;
  int16_t zeros32, zeros16;
  int16_t zerosDBufNoisy, zerosDBufClean, zerosXBuf;
  int far_q;
  int16_t resolutionDiff, qDomainDiff, dfa_clean_q_domain_diff;

  const int kMinPrefBand = 4;
  const int kMaxPrefBand = 24;
  int32_t avgHnl32 = 0;

  int32_t temp1, temp2, temp3, temp4, temp5, temp6, temp7, temp8;
  int16_t* ptr;
  int16_t* ptr1;
  int16_t* er_ptr;
  int16_t* dr_ptr;

  ptr = &hnl[0];
  ptr1 = &hnl[0];
  er_ptr = &efw[0].real;
  dr_ptr = &dfw[0].real;

  // Determine startup state. There are three states:
  // (0) the first CONV_LEN blocks
  // (1) another CONV_LEN blocks
  // (2) the rest

  if (aecm->startupState < 2) {
    aecm->startupState =
        (aecm->totCount >= CONV_LEN) + (aecm->totCount >= CONV_LEN2);
  }
  // END: Determine startup state

  // Buffer near and far end signals
  memcpy(aecm->xBuf + PART_LEN, farend, sizeof(int16_t) * PART_LEN);
  memcpy(aecm->dBufNoisy + PART_LEN, nearendNoisy, sizeof(int16_t) * PART_LEN);
  if (nearendClean != NULL) {
    memcpy(aecm->dBufClean + PART_LEN, nearendClean,
           sizeof(int16_t) * PART_LEN);
  }

  // Transform far end signal from time domain to frequency domain.
  far_q = TimeToFrequencyDomain(aecm, aecm->xBuf, dfw, xfa, &xfaSum);

  // Transform noisy near end signal from time domain to frequency domain.
  zerosDBufNoisy =
      TimeToFrequencyDomain(aecm, aecm->dBufNoisy, dfw, dfaNoisy, &dfaNoisySum);
  aecm->dfaNoisyQDomainOld = aecm->dfaNoisyQDomain;
  aecm->dfaNoisyQDomain = (int16_t)zerosDBufNoisy;

  if (nearendClean == NULL) {
    ptrDfaClean = dfaNoisy;
    aecm->dfaCleanQDomainOld = aecm->dfaNoisyQDomainOld;
    aecm->dfaCleanQDomain = aecm->dfaNoisyQDomain;
    dfaCleanSum = dfaNoisySum;
  } else {
    // Transform clean near end signal from time domain to frequency domain.
    zerosDBufClean = TimeToFrequencyDomain(aecm, aecm->dBufClean, dfw, dfaClean,
                                           &dfaCleanSum);
    aecm->dfaCleanQDomainOld = aecm->dfaCleanQDomain;
    aecm->dfaCleanQDomain = (int16_t)zerosDBufClean;
  }

  // Get the delay
  // Save far-end history and estimate delay
  WebRtcAecm_UpdateFarHistory(aecm, xfa, far_q);

  if (WebRtc_AddFarSpectrumFix(aecm->delay_estimator_farend, xfa, PART_LEN1,
                               far_q) == -1) {
    return -1;
  }
  delay = WebRtc_DelayEstimatorProcessFix(aecm->delay_estimator, dfaNoisy,
                                          PART_LEN1, zerosDBufNoisy);
  if (delay == -1) {
    return -1;
  } else if (delay == -2) {
    // If the delay is unknown, we assume zero.
    // NOTE: this will have to be adjusted if we ever add lookahead.
    delay = 0;
  }

  if (aecm->fixedDelay >= 0) {
    // Use fixed delay
    delay = aecm->fixedDelay;
  }

  // Get aligned far end spectrum
  far_spectrum_ptr = WebRtcAecm_AlignedFarend(aecm, &far_q, delay);
  zerosXBuf = (int16_t)far_q;

  if (far_spectrum_ptr == NULL) {
    return -1;
  }

  // Calculate log(energy) and update energy threshold levels
  WebRtcAecm_CalcEnergies(aecm, far_spectrum_ptr, zerosXBuf, dfaNoisySum,
                          echoEst32);
  // Calculate stepsize
  mu = WebRtcAecm_CalcStepSize(aecm);

  // Update counters
  aecm->totCount++;

  // This is the channel estimation algorithm.
  // It is base on NLMS but has a variable step length,
  // which was calculated above.
  WebRtcAecm_UpdateChannel(aecm, far_spectrum_ptr, zerosXBuf, dfaNoisy, mu,
                           echoEst32);

  supGain = WebRtcAecm_CalcSuppressionGain(aecm);

  // Calculate Wiener filter hnl[]
  for (i = 0; i < PART_LEN1; i++) {
    // Far end signal through channel estimate in Q8
    // How much can we shift right to preserve resolution
    tmp32no1 = echoEst32[i] - aecm->echoFilt[i];
    aecm->echoFilt[i] +=
        rtc::dchecked_cast<int32_t>((int64_t{tmp32no1} * 50) >> 8);

    zeros32 = WebRtcSpl_NormW32(aecm->echoFilt[i]) + 1;
    zeros16 = WebRtcSpl_NormW16(supGain) + 1;
    if (zeros32 + zeros16 > 16) {
      // Multiplication is safe
      // Result in
      // Q(RESOLUTION_CHANNEL+RESOLUTION_SUPGAIN+aecm->xfaQDomainBuf[diff])
      echoEst32Gained =
          WEBRTC_SPL_UMUL_32_16((uint32_t)aecm->echoFilt[i], (uint16_t)supGain);
      resolutionDiff = 14 - RESOLUTION_CHANNEL16 - RESOLUTION_SUPGAIN;
      resolutionDiff += (aecm->dfaCleanQDomain - zerosXBuf);
    } else {
      tmp16no1 = 17 - zeros32 - zeros16;
      resolutionDiff =
          14 + tmp16no1 - RESOLUTION_CHANNEL16 - RESOLUTION_SUPGAIN;
      resolutionDiff += (aecm->dfaCleanQDomain - zerosXBuf);
      if (zeros32 > tmp16no1) {
        echoEst32Gained = WEBRTC_SPL_UMUL_32_16((uint32_t)aecm->echoFilt[i],
                                                supGain >> tmp16no1);
      } else {
        // Result in Q-(RESOLUTION_CHANNEL+RESOLUTION_SUPGAIN-16)
        echoEst32Gained = (aecm->echoFilt[i] >> tmp16no1) * supGain;
      }
    }

    zeros16 = WebRtcSpl_NormW16(aecm->nearFilt[i]);
    RTC_DCHECK_GE(zeros16, 0);  // `zeros16` is a norm, hence non-negative.
    dfa_clean_q_domain_diff = aecm->dfaCleanQDomain - aecm->dfaCleanQDomainOld;
    if (zeros16 < dfa_clean_q_domain_diff && aecm->nearFilt[i]) {
      tmp16no1 = aecm->nearFilt[i] << zeros16;
      qDomainDiff = zeros16 - dfa_clean_q_domain_diff;
      tmp16no2 = ptrDfaClean[i] >> -qDomainDiff;
    } else {
      tmp16no1 = dfa_clean_q_domain_diff < 0
                     ? aecm->nearFilt[i] >> -dfa_clean_q_domain_diff
                     : aecm->nearFilt[i] << dfa_clean_q_domain_diff;
      qDomainDiff = 0;
      tmp16no2 = ptrDfaClean[i];
    }

    tmp32no1 = (int32_t)(tmp16no2 - tmp16no1);
    tmp16no2 = (int16_t)(tmp32no1 >> 4);
    tmp16no2 += tmp16no1;
    zeros16 = WebRtcSpl_NormW16(tmp16no2);
    if ((tmp16no2) & (-qDomainDiff > zeros16)) {
      aecm->nearFilt[i] = WEBRTC_SPL_WORD16_MAX;
    } else {
      aecm->nearFilt[i] =
          qDomainDiff < 0 ? tmp16no2 << -qDomainDiff : tmp16no2 >> qDomainDiff;
    }

    // Wiener filter coefficients, resulting hnl in Q14
    if (echoEst32Gained == 0) {
      hnl[i] = ONE_Q14;
      numPosCoef++;
    } else if (aecm->nearFilt[i] == 0) {
      hnl[i] = 0;
    } else {
      // Multiply the suppression gain
      // Rounding
      echoEst32Gained += (uint32_t)(aecm->nearFilt[i] >> 1);
      tmpU32 =
          WebRtcSpl_DivU32U16(echoEst32Gained, (uint16_t)aecm->nearFilt[i]);

      // Current resolution is
      // Q-(RESOLUTION_CHANNEL + RESOLUTION_SUPGAIN
      //    - max(0, 17 - zeros16 - zeros32))
      // Make sure we are in Q14
      tmp32no1 = (int32_t)WEBRTC_SPL_SHIFT_W32(tmpU32, resolutionDiff);
      if (tmp32no1 > ONE_Q14) {
        hnl[i] = 0;
      } else if (tmp32no1 < 0) {
        hnl[i] = ONE_Q14;
        numPosCoef++;
      } else {
        // 1-echoEst/dfa
        hnl[i] = ONE_Q14 - (int16_t)tmp32no1;
        if (hnl[i] <= 0) {
          hnl[i] = 0;
        } else {
          numPosCoef++;
        }
      }
    }
  }

  // Only in wideband. Prevent the gain in upper band from being larger than
  // in lower band.
  if (aecm->mult == 2) {
    // TODO(bjornv): Investigate if the scaling of hnl[i] below can cause
    //               speech distortion in double-talk.
    for (i = 0; i < (PART_LEN1 >> 3); i++) {
      __asm __volatile(
          "lh         %[temp1],       0(%[ptr1])                  \n\t"
          "lh         %[temp2],       2(%[ptr1])                  \n\t"
          "lh         %[temp3],       4(%[ptr1])                  \n\t"
          "lh         %[temp4],       6(%[ptr1])                  \n\t"
          "lh         %[temp5],       8(%[ptr1])                  \n\t"
          "lh         %[temp6],       10(%[ptr1])                 \n\t"
          "lh         %[temp7],       12(%[ptr1])                 \n\t"
          "lh         %[temp8],       14(%[ptr1])                 \n\t"
          "mul        %[temp1],       %[temp1],       %[temp1]    \n\t"
          "mul        %[temp2],       %[temp2],       %[temp2]    \n\t"
          "mul        %[temp3],       %[temp3],       %[temp3]    \n\t"
          "mul        %[temp4],       %[temp4],       %[temp4]    \n\t"
          "mul        %[temp5],       %[temp5],       %[temp5]    \n\t"
          "mul        %[temp6],       %[temp6],       %[temp6]    \n\t"
          "mul        %[temp7],       %[temp7],       %[temp7]    \n\t"
          "mul        %[temp8],       %[temp8],       %[temp8]    \n\t"
          "sra        %[temp1],       %[temp1],       14          \n\t"
          "sra        %[temp2],       %[temp2],       14          \n\t"
          "sra        %[temp3],       %[temp3],       14          \n\t"
          "sra        %[temp4],       %[temp4],       14          \n\t"
          "sra        %[temp5],       %[temp5],       14          \n\t"
          "sra        %[temp6],       %[temp6],       14          \n\t"
          "sra        %[temp7],       %[temp7],       14          \n\t"
          "sra        %[temp8],       %[temp8],       14          \n\t"
          "sh         %[temp1],       0(%[ptr1])                  \n\t"
          "sh         %[temp2],       2(%[ptr1])                  \n\t"
          "sh         %[temp3],       4(%[ptr1])                  \n\t"
          "sh         %[temp4],       6(%[ptr1])                  \n\t"
          "sh         %[temp5],       8(%[ptr1])                  \n\t"
          "sh         %[temp6],       10(%[ptr1])                 \n\t"
          "sh         %[temp7],       12(%[ptr1])                 \n\t"
          "sh         %[temp8],       14(%[ptr1])                 \n\t"
          "addiu      %[ptr1],        %[ptr1],        16          \n\t"
          : [temp1] "=&r"(temp1), [temp2] "=&r"(temp2), [temp3] "=&r"(temp3),
            [temp4] "=&r"(temp4), [temp5] "=&r"(temp5), [temp6] "=&r"(temp6),
            [temp7] "=&r"(temp7), [temp8] "=&r"(temp8), [ptr1] "+r"(ptr1)
          :
          : "memory", "hi", "lo");
    }
    for (i = 0; i < (PART_LEN1 & 7); i++) {
      __asm __volatile(
          "lh         %[temp1],       0(%[ptr1])                  \n\t"
          "mul        %[temp1],       %[temp1],       %[temp1]    \n\t"
          "sra        %[temp1],       %[temp1],       14          \n\t"
          "sh         %[temp1],       0(%[ptr1])                  \n\t"
          "addiu      %[ptr1],        %[ptr1],        2           \n\t"
          : [temp1] "=&r"(temp1), [ptr1] "+r"(ptr1)
          :
          : "memory", "hi", "lo");
    }

    for (i = kMinPrefBand; i <= kMaxPrefBand; i++) {
      avgHnl32 += (int32_t)hnl[i];
    }

    RTC_DCHECK_GT(kMaxPrefBand - kMinPrefBand + 1, 0);
    avgHnl32 /= (kMaxPrefBand - kMinPrefBand + 1);

    for (i = kMaxPrefBand; i < PART_LEN1; i++) {
      if (hnl[i] > (int16_t)avgHnl32) {
        hnl[i] = (int16_t)avgHnl32;
      }
    }
  }

  // Calculate NLP gain, result is in Q14
  if (aecm->nlpFlag) {
    if (numPosCoef < 3) {
      for (i = 0; i < PART_LEN1; i++) {
        efw[i].real = 0;
        efw[i].imag = 0;
        hnl[i] = 0;
      }
    } else {
      for (i = 0; i < PART_LEN1; i++) {
#if defined(MIPS_DSP_R1_LE)
        __asm __volatile(
            ".set       push                                        \n\t"
            ".set       noreorder                                   \n\t"
            "lh         %[temp1],       0(%[ptr])                   \n\t"
            "lh         %[temp2],       0(%[dr_ptr])                \n\t"
            "slti       %[temp4],       %[temp1],       0x4001      \n\t"
            "beqz       %[temp4],       3f                          \n\t"
            " lh        %[temp3],       2(%[dr_ptr])                \n\t"
            "slti       %[temp5],       %[temp1],       3277        \n\t"
            "bnez       %[temp5],       2f                          \n\t"
            " addiu     %[dr_ptr],      %[dr_ptr],      4           \n\t"
            "mul        %[temp2],       %[temp2],       %[temp1]    \n\t"
            "mul        %[temp3],       %[temp3],       %[temp1]    \n\t"
            "shra_r.w   %[temp2],       %[temp2],       14          \n\t"
            "shra_r.w   %[temp3],       %[temp3],       14          \n\t"
            "b          4f                                          \n\t"
            " nop                                                   \n\t"
            "2:                                                      \n\t"
            "addu       %[temp1],       $zero,          $zero       \n\t"
            "addu       %[temp2],       $zero,          $zero       \n\t"
            "addu       %[temp3],       $zero,          $zero       \n\t"
            "b          1f                                          \n\t"
            " nop                                                   \n\t"
            "3:                                                      \n\t"
            "addiu      %[temp1],       $0,             0x4000      \n\t"
            "1:                                                      \n\t"
            "sh         %[temp1],       0(%[ptr])                   \n\t"
            "4:                                                      \n\t"
            "sh         %[temp2],       0(%[er_ptr])                \n\t"
            "sh         %[temp3],       2(%[er_ptr])                \n\t"
            "addiu      %[ptr],         %[ptr],         2           \n\t"
            "addiu      %[er_ptr],      %[er_ptr],      4           \n\t"
            ".set       pop                                         \n\t"
            : [temp1] "=&r"(temp1), [temp2] "=&r"(temp2), [temp3] "=&r"(temp3),
              [temp4] "=&r"(temp4), [temp5] "=&r"(temp5), [ptr] "+r"(ptr),
              [er_ptr] "+r"(er_ptr), [dr_ptr] "+r"(dr_ptr)
            :
            : "memory", "hi", "lo");
#else
        __asm __volatile(
            ".set       push                                        \n\t"
            ".set       noreorder                                   \n\t"
            "lh         %[temp1],       0(%[ptr])                   \n\t"
            "lh         %[temp2],       0(%[dr_ptr])                \n\t"
            "slti       %[temp4],       %[temp1],       0x4001      \n\t"
            "beqz       %[temp4],       3f                          \n\t"
            " lh        %[temp3],       2(%[dr_ptr])                \n\t"
            "slti       %[temp5],       %[temp1],       3277        \n\t"
            "bnez       %[temp5],       2f                          \n\t"
            " addiu     %[dr_ptr],      %[dr_ptr],      4           \n\t"
            "mul        %[temp2],       %[temp2],       %[temp1]    \n\t"
            "mul        %[temp3],       %[temp3],       %[temp1]    \n\t"
            "addiu      %[temp2],       %[temp2],       0x2000      \n\t"
            "addiu      %[temp3],       %[temp3],       0x2000      \n\t"
            "sra        %[temp2],       %[temp2],       14          \n\t"
            "sra        %[temp3],       %[temp3],       14          \n\t"
            "b          4f                                          \n\t"
            " nop                                                   \n\t"
            "2:                                                      \n\t"
            "addu       %[temp1],       $zero,          $zero       \n\t"
            "addu       %[temp2],       $zero,          $zero       \n\t"
            "addu       %[temp3],       $zero,          $zero       \n\t"
            "b          1f                                          \n\t"
            " nop                                                   \n\t"
            "3:                                                      \n\t"
            "addiu      %[temp1],       $0,             0x4000      \n\t"
            "1:                                                      \n\t"
            "sh         %[temp1],       0(%[ptr])                   \n\t"
            "4:                                                      \n\t"
            "sh         %[temp2],       0(%[er_ptr])                \n\t"
            "sh         %[temp3],       2(%[er_ptr])                \n\t"
            "addiu      %[ptr],         %[ptr],         2           \n\t"
            "addiu      %[er_ptr],      %[er_ptr],      4           \n\t"
            ".set       pop                                         \n\t"
            : [temp1] "=&r"(temp1), [temp2] "=&r"(temp2), [temp3] "=&r"(temp3),
              [temp4] "=&r"(temp4), [temp5] "=&r"(temp5), [ptr] "+r"(ptr),
              [er_ptr] "+r"(er_ptr), [dr_ptr] "+r"(dr_ptr)
            :
            : "memory", "hi", "lo");
#endif
      }
    }
  } else {
    // multiply with Wiener coefficients
    for (i = 0; i < PART_LEN1; i++) {
      efw[i].real = (int16_t)(WEBRTC_SPL_MUL_16_16_RSFT_WITH_ROUND(dfw[i].real,
                                                                   hnl[i], 14));
      efw[i].imag = (int16_t)(WEBRTC_SPL_MUL_16_16_RSFT_WITH_ROUND(dfw[i].imag,
                                                                   hnl[i], 14));
    }
  }

  if (aecm->cngMode == AecmTrue) {
    ComfortNoise(aecm, ptrDfaClean, efw, hnl);
  }

  InverseFFTAndWindow(aecm, fft, efw, output, nearendClean);

  return 0;
}

// Generate comfort noise and add to output signal.
static void ComfortNoise(AecmCore* aecm,
                         const uint16_t* dfa,
                         ComplexInt16* out,
                         const int16_t* lambda) {
  int16_t i;
  int16_t tmp16, tmp161, tmp162, tmp163, nrsh1, nrsh2;
  int32_t tmp32, tmp321, tnoise, tnoise1;
  int32_t tmp322, tmp323, *tmp1;
  int16_t* dfap;
  int16_t* lambdap;
  const int32_t c2049 = 2049;
  const int32_t c359 = 359;
  const int32_t c114 = ONE_Q14;

  int16_t randW16[PART_LEN];
  int16_t uReal[PART_LEN1];
  int16_t uImag[PART_LEN1];
  int32_t outLShift32;

  int16_t shiftFromNearToNoise = kNoiseEstQDomain - aecm->dfaCleanQDomain;
  int16_t minTrackShift = 9;

  RTC_DCHECK_GE(shiftFromNearToNoise, 0);
  RTC_DCHECK_LT(shiftFromNearToNoise, 16);

  if (aecm->noiseEstCtr < 100) {
    // Track the minimum more quickly initially.
    aecm->noiseEstCtr++;
    minTrackShift = 6;
  }

  // Generate a uniform random array on [0 2^15-1].
  WebRtcSpl_RandUArray(randW16, PART_LEN, &aecm->seed);
  int16_t* randW16p = (int16_t*)randW16;
#if defined(MIPS_DSP_R1_LE)
  int16_t* kCosTablep = (int16_t*)WebRtcAecm_kCosTable;
  int16_t* kSinTablep = (int16_t*)WebRtcAecm_kSinTable;
#endif  // #if defined(MIPS_DSP_R1_LE)
  tmp1 = (int32_t*)aecm->noiseEst + 1;
  dfap = (int16_t*)dfa + 1;
  lambdap = (int16_t*)lambda + 1;
  // Estimate noise power.
  for (i = 1; i < PART_LEN1; i += 2) {
    // Shift to the noise domain.
    __asm __volatile(
        "lh     %[tmp32],       0(%[dfap])                              \n\t"
        "lw     %[tnoise],      0(%[tmp1])                              \n\t"
        "sllv   %[outLShift32], %[tmp32],   %[shiftFromNearToNoise]     \n\t"
        : [tmp32] "=&r"(tmp32), [outLShift32] "=r"(outLShift32),
          [tnoise] "=&r"(tnoise)
        : [tmp1] "r"(tmp1), [dfap] "r"(dfap),
          [shiftFromNearToNoise] "r"(shiftFromNearToNoise)
        : "memory");

    if (outLShift32 < tnoise) {
      // Reset "too low" counter
      aecm->noiseEstTooLowCtr[i] = 0;
      // Track the minimum.
      if (tnoise < (1 << minTrackShift)) {
        // For small values, decrease noiseEst[i] every
        // `kNoiseEstIncCount` block. The regular approach below can not
        // go further down due to truncation.
        aecm->noiseEstTooHighCtr[i]++;
        if (aecm->noiseEstTooHighCtr[i] >= kNoiseEstIncCount) {
          tnoise--;
          aecm->noiseEstTooHighCtr[i] = 0;  // Reset the counter
        }
      } else {
        __asm __volatile(
            "subu   %[tmp32],       %[tnoise],      %[outLShift32]      \n\t"
            "srav   %[tmp32],       %[tmp32],       %[minTrackShift]    \n\t"
            "subu   %[tnoise],      %[tnoise],      %[tmp32]            \n\t"
            : [tmp32] "=&r"(tmp32), [tnoise] "+r"(tnoise)
            : [outLShift32] "r"(outLShift32), [minTrackShift] "r"(
                                                  minTrackShift));
      }
    } else {
      // Reset "too high" counter
      aecm->noiseEstTooHighCtr[i] = 0;
      // Ramp slowly upwards until we hit the minimum again.
      if ((tnoise >> 19) <= 0) {
        if ((tnoise >> 11) > 0) {
          // Large enough for relative increase
          __asm __volatile(
              "mul    %[tnoise],  %[tnoise],  %[c2049]    \n\t"
              "sra    %[tnoise],  %[tnoise],  11          \n\t"
              : [tnoise] "+r"(tnoise)
              : [c2049] "r"(c2049)
              : "hi", "lo");
        } else {
          // Make incremental increases based on size every
          // `kNoiseEstIncCount` block
          aecm->noiseEstTooLowCtr[i]++;
          if (aecm->noiseEstTooLowCtr[i] >= kNoiseEstIncCount) {
            __asm __volatile(
                "sra    %[tmp32],   %[tnoise],  9           \n\t"
                "addi   %[tnoise],  %[tnoise],  1           \n\t"
                "addu   %[tnoise],  %[tnoise],  %[tmp32]    \n\t"
                : [tnoise] "+r"(tnoise), [tmp32] "=&r"(tmp32)
                :);
            aecm->noiseEstTooLowCtr[i] = 0;  // Reset counter
          }
        }
      } else {
        // Avoid overflow.
        // Multiplication with 2049 will cause wrap around. Scale
        // down first and then multiply
        __asm __volatile(
            "sra    %[tnoise],  %[tnoise],  11          \n\t"
            "mul    %[tnoise],  %[tnoise],  %[c2049]    \n\t"
            : [tnoise] "+r"(tnoise)
            : [c2049] "r"(c2049)
            : "hi", "lo");
      }
    }

    // Shift to the noise domain.
    __asm __volatile(
        "lh     %[tmp32],       2(%[dfap])                              \n\t"
        "lw     %[tnoise1],     4(%[tmp1])                              \n\t"
        "addiu  %[dfap],        %[dfap],    4                           \n\t"
        "sllv   %[outLShift32], %[tmp32],   %[shiftFromNearToNoise]     \n\t"
        : [tmp32] "=&r"(tmp32), [dfap] "+r"(dfap),
          [outLShift32] "=r"(outLShift32), [tnoise1] "=&r"(tnoise1)
        : [tmp1] "r"(tmp1), [shiftFromNearToNoise] "r"(shiftFromNearToNoise)
        : "memory");

    if (outLShift32 < tnoise1) {
      // Reset "too low" counter
      aecm->noiseEstTooLowCtr[i + 1] = 0;
      // Track the minimum.
      if (tnoise1 < (1 << minTrackShift)) {
        // For small values, decrease noiseEst[i] every
        // `kNoiseEstIncCount` block. The regular approach below can not
        // go further down due to truncation.
        aecm->noiseEstTooHighCtr[i + 1]++;
        if (aecm->noiseEstTooHighCtr[i + 1] >= kNoiseEstIncCount) {
          tnoise1--;
          aecm->noiseEstTooHighCtr[i + 1] = 0;  // Reset the counter
        }
      } else {
        __asm __volatile(
            "subu   %[tmp32],       %[tnoise1],     %[outLShift32]      \n\t"
            "srav   %[tmp32],       %[tmp32],       %[minTrackShift]    \n\t"
            "subu   %[tnoise1],     %[tnoise1],     %[tmp32]            \n\t"
            : [tmp32] "=&r"(tmp32), [tnoise1] "+r"(tnoise1)
            : [outLShift32] "r"(outLShift32), [minTrackShift] "r"(
                                                  minTrackShift));
      }
    } else {
      // Reset "too high" counter
      aecm->noiseEstTooHighCtr[i + 1] = 0;
      // Ramp slowly upwards until we hit the minimum again.
      if ((tnoise1 >> 19) <= 0) {
        if ((tnoise1 >> 11) > 0) {
          // Large enough for relative increase
          __asm __volatile(
              "mul    %[tnoise1], %[tnoise1], %[c2049]   \n\t"
              "sra    %[tnoise1], %[tnoise1], 11         \n\t"
              : [tnoise1] "+r"(tnoise1)
              : [c2049] "r"(c2049)
              : "hi", "lo");
        } else {
          // Make incremental increases based on size every
          // `kNoiseEstIncCount` block
          aecm->noiseEstTooLowCtr[i + 1]++;
          if (aecm->noiseEstTooLowCtr[i + 1] >= kNoiseEstIncCount) {
            __asm __volatile(
                "sra    %[tmp32],   %[tnoise1], 9           \n\t"
                "addi   %[tnoise1], %[tnoise1], 1           \n\t"
                "addu   %[tnoise1], %[tnoise1], %[tmp32]    \n\t"
                : [tnoise1] "+r"(tnoise1), [tmp32] "=&r"(tmp32)
                :);
            aecm->noiseEstTooLowCtr[i + 1] = 0;  // Reset counter
          }
        }
      } else {
        // Avoid overflow.
        // Multiplication with 2049 will cause wrap around. Scale
        // down first and then multiply
        __asm __volatile(
            "sra    %[tnoise1], %[tnoise1], 11          \n\t"
            "mul    %[tnoise1], %[tnoise1], %[c2049]    \n\t"
            : [tnoise1] "+r"(tnoise1)
            : [c2049] "r"(c2049)
            : "hi", "lo");
      }
    }

    __asm __volatile(
        "lh     %[tmp16],   0(%[lambdap])                           \n\t"
        "lh     %[tmp161],  2(%[lambdap])                           \n\t"
        "sw     %[tnoise],  0(%[tmp1])                              \n\t"
        "sw     %[tnoise1], 4(%[tmp1])                              \n\t"
        "subu   %[tmp16],   %[c114],        %[tmp16]                \n\t"
        "subu   %[tmp161],  %[c114],        %[tmp161]               \n\t"
        "srav   %[tmp32],   %[tnoise],      %[shiftFromNearToNoise] \n\t"
        "srav   %[tmp321],  %[tnoise1],     %[shiftFromNearToNoise] \n\t"
        "addiu  %[lambdap], %[lambdap],     4                       \n\t"
        "addiu  %[tmp1],    %[tmp1],        8                       \n\t"
        : [tmp16] "=&r"(tmp16), [tmp161] "=&r"(tmp161), [tmp1] "+r"(tmp1),
          [tmp32] "=&r"(tmp32), [tmp321] "=&r"(tmp321), [lambdap] "+r"(lambdap)
        : [tnoise] "r"(tnoise), [tnoise1] "r"(tnoise1), [c114] "r"(c114),
          [shiftFromNearToNoise] "r"(shiftFromNearToNoise)
        : "memory");

    if (tmp32 > 32767) {
      tmp32 = 32767;
      aecm->noiseEst[i] = tmp32 << shiftFromNearToNoise;
    }
    if (tmp321 > 32767) {
      tmp321 = 32767;
      aecm->noiseEst[i + 1] = tmp321 << shiftFromNearToNoise;
    }

    __asm __volatile(
        "mul    %[tmp32],   %[tmp32],       %[tmp16]                \n\t"
        "mul    %[tmp321],  %[tmp321],      %[tmp161]               \n\t"
        "sra    %[nrsh1],   %[tmp32],       14                      \n\t"
        "sra    %[nrsh2],   %[tmp321],      14                      \n\t"
        : [nrsh1] "=&r"(nrsh1), [nrsh2] "=r"(nrsh2)
        : [tmp16] "r"(tmp16), [tmp161] "r"(tmp161), [tmp32] "r"(tmp32),
          [tmp321] "r"(tmp321)
        : "memory", "hi", "lo");

    __asm __volatile(
        "lh     %[tmp32],       0(%[randW16p])              \n\t"
        "lh     %[tmp321],      2(%[randW16p])              \n\t"
        "addiu  %[randW16p],    %[randW16p],    4           \n\t"
        "mul    %[tmp32],       %[tmp32],       %[c359]     \n\t"
        "mul    %[tmp321],      %[tmp321],      %[c359]     \n\t"
        "sra    %[tmp16],       %[tmp32],       15          \n\t"
        "sra    %[tmp161],      %[tmp321],      15          \n\t"
        : [randW16p] "+r"(randW16p), [tmp32] "=&r"(tmp32), [tmp16] "=r"(tmp16),
          [tmp161] "=r"(tmp161), [tmp321] "=&r"(tmp321)
        : [c359] "r"(c359)
        : "memory", "hi", "lo");

#if !defined(MIPS_DSP_R1_LE)
    tmp32 = WebRtcAecm_kCosTable[tmp16];
    tmp321 = WebRtcAecm_kSinTable[tmp16];
    tmp322 = WebRtcAecm_kCosTable[tmp161];
    tmp323 = WebRtcAecm_kSinTable[tmp161];
#else
    __asm __volatile(
        "sll    %[tmp16],       %[tmp16],                   1           \n\t"
        "sll    %[tmp161],      %[tmp161],                  1           \n\t"
        "lhx    %[tmp32],       %[tmp16](%[kCosTablep])                 \n\t"
        "lhx    %[tmp321],      %[tmp16](%[kSinTablep])                 \n\t"
        "lhx    %[tmp322],      %[tmp161](%[kCosTablep])                \n\t"
        "lhx    %[tmp323],      %[tmp161](%[kSinTablep])                \n\t"
        : [tmp32] "=&r"(tmp32), [tmp321] "=&r"(tmp321), [tmp322] "=&r"(tmp322),
          [tmp323] "=&r"(tmp323)
        : [kCosTablep] "r"(kCosTablep), [tmp16] "r"(tmp16),
          [tmp161] "r"(tmp161), [kSinTablep] "r"(kSinTablep)
        : "memory");
#endif
    __asm __volatile(
        "mul    %[tmp32],       %[tmp32],                   %[nrsh1]    \n\t"
        "negu   %[tmp162],      %[nrsh1]                                \n\t"
        "mul    %[tmp322],      %[tmp322],                  %[nrsh2]    \n\t"
        "negu   %[tmp163],      %[nrsh2]                                \n\t"
        "sra    %[tmp32],       %[tmp32],                   13          \n\t"
        "mul    %[tmp321],      %[tmp321],                  %[tmp162]   \n\t"
        "sra    %[tmp322],      %[tmp322],                  13          \n\t"
        "mul    %[tmp323],      %[tmp323],                  %[tmp163]   \n\t"
        "sra    %[tmp321],      %[tmp321],                  13          \n\t"
        "sra    %[tmp323],      %[tmp323],                  13          \n\t"
        : [tmp32] "+r"(tmp32), [tmp321] "+r"(tmp321), [tmp162] "=&r"(tmp162),
          [tmp322] "+r"(tmp322), [tmp323] "+r"(tmp323), [tmp163] "=&r"(tmp163)
        : [nrsh1] "r"(nrsh1), [nrsh2] "r"(nrsh2)
        : "hi", "lo");
    // Tables are in Q13.
    uReal[i] = (int16_t)tmp32;
    uImag[i] = (int16_t)tmp321;
    uReal[i + 1] = (int16_t)tmp322;
    uImag[i + 1] = (int16_t)tmp323;
  }

  int32_t tt, sgn;
  tt = out[0].real;
  sgn = ((int)tt) >> 31;
  out[0].real = sgn == (int16_t)(tt >> 15) ? (int16_t)tt : (16384 ^ sgn);
  tt = out[0].imag;
  sgn = ((int)tt) >> 31;
  out[0].imag = sgn == (int16_t)(tt >> 15) ? (int16_t)tt : (16384 ^ sgn);
  for (i = 1; i < PART_LEN; i++) {
    tt = out[i].real + uReal[i];
    sgn = ((int)tt) >> 31;
    out[i].real = sgn == (int16_t)(tt >> 15) ? (int16_t)tt : (16384 ^ sgn);
    tt = out[i].imag + uImag[i];
    sgn = ((int)tt) >> 31;
    out[i].imag = sgn == (int16_t)(tt >> 15) ? (int16_t)tt : (16384 ^ sgn);
  }
  tt = out[PART_LEN].real + uReal[PART_LEN];
  sgn = ((int)tt) >> 31;
  out[PART_LEN].real = sgn == (int16_t)(tt >> 15) ? (int16_t)tt : (16384 ^ sgn);
  tt = out[PART_LEN].imag;
  sgn = ((int)tt) >> 31;
  out[PART_LEN].imag = sgn == (int16_t)(tt >> 15) ? (int16_t)tt : (16384 ^ sgn);
}

}  // namespace webrtc
