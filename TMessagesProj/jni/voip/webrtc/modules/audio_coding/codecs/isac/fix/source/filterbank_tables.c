/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * filterbank_tables.c
 *
 * This file contains variables that are used in
 * filterbanks.c
 *
 */

#include "modules/audio_coding/codecs/isac/fix/source/filterbank_tables.h"

/* HPstcoeff_in_Q14 = {a1, a2, b1 - b0 * a1, b2 - b0 * a2};
 * In float, they are: {-1.94895953203325f, 0.94984516000000f,
 * -0.05101826139794f, 0.05015484000000f};
 */
const int16_t WebRtcIsacfix_kHpStCoeffInQ30[8] = {
  16189, -31932,  /* Q30 lo/hi pair */
  17243, 15562,  /* Q30 lo/hi pair */
  -17186, -26748,  /* Q35 lo/hi pair */
  -27476, 26296  /* Q35 lo/hi pair */
};

/* HPstcoeff_out_1_Q14 = {a1, a2, b1 - b0 * a1, b2 - b0 * a2};
 * In float, they are: {-1.99701049409000f, 0.99714204490000f,
 * 0.01701049409000f, -0.01704204490000f};
 */
const int16_t WebRtcIsacfix_kHPStCoeffOut1Q30[8] = {
  -1306, -32719,  /* Q30 lo/hi pair */
  11486, 16337,  /* Q30 lo/hi pair */
  26078, 8918,  /* Q35 lo/hi pair */
  3956, -8935  /* Q35 lo/hi pair */
};

/* HPstcoeff_out_2_Q14 = {a1, a2, b1 - b0 * a1, b2 - b0 * a2};
 * In float, they are: {-1.98645294509837f, 0.98672435560000f,
 * 0.00645294509837f, -0.00662435560000f};
 */
const int16_t WebRtcIsacfix_kHPStCoeffOut2Q30[8] = {
  -2953, -32546,  /* Q30 lo/hi pair */
  32233, 16166,  /* Q30 lo/hi pair */
  13217, 3383,  /* Q35 lo/hi pair */
  -4597, -3473  /* Q35 lo/hi pair */
};

/* The upper channel all-pass filter factors */
const int16_t WebRtcIsacfix_kUpperApFactorsQ15[2] = {
  1137, 12537
};

/* The lower channel all-pass filter factors */
const int16_t WebRtcIsacfix_kLowerApFactorsQ15[2] = {
  5059, 24379
};
