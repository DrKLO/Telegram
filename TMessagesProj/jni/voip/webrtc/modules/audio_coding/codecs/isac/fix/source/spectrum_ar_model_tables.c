/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * spectrum_ar_model_tables.c
 *
 * This file contains tables with AR coefficients, Gain coefficients
 * and cosine tables.
 *
 */

#include "modules/audio_coding/codecs/isac/fix/source/spectrum_ar_model_tables.h"
#include "modules/audio_coding/codecs/isac/fix/source/settings.h"

/********************* AR Coefficient Tables ************************/

/* cdf for quantized reflection coefficient 1 */
const uint16_t WebRtcIsacfix_kRc1Cdf[12] = {
  0,  2,  4,  129,  7707,  57485,  65495,  65527,  65529,  65531,
  65533,  65535
};

/* cdf for quantized reflection coefficient 2 */
const uint16_t WebRtcIsacfix_kRc2Cdf[12] = {
  0,  2,  4,  7,  531,  25298,  64525,  65526,  65529,  65531,
  65533,  65535
};

/* cdf for quantized reflection coefficient 3 */
const uint16_t WebRtcIsacfix_kRc3Cdf[12] = {
  0,  2,  4,  6,  620,  22898,  64843,  65527,  65529,  65531,
  65533,  65535
};

/* cdf for quantized reflection coefficient 4 */
const uint16_t WebRtcIsacfix_kRc4Cdf[12] = {
  0,  2,  4,  6,  35,  10034,  60733,  65506,  65529,  65531,
  65533,  65535
};

/* cdf for quantized reflection coefficient 5 */
const uint16_t WebRtcIsacfix_kRc5Cdf[12] = {
  0,  2,  4,  6,  36,  7567,  56727,  65385,  65529,  65531,
  65533,  65535
};

/* cdf for quantized reflection coefficient 6 */
const uint16_t WebRtcIsacfix_kRc6Cdf[12] = {
  0,  2,  4,  6,  14,  6579,  57360,  65409,  65529,  65531,
  65533,  65535
};

/* representation levels for quantized reflection coefficient 1 */
const int16_t WebRtcIsacfix_kRc1Levels[11] = {
  -32104, -29007, -23202, -15496, -9279, -2577, 5934, 17535, 24512, 29503, 32104
};

/* representation levels for quantized reflection coefficient 2 */
const int16_t WebRtcIsacfix_kRc2Levels[11] = {
  -32104, -29503, -23494, -15261, -7309, -1399, 6158, 16381, 24512, 29503, 32104
};

/* representation levels for quantized reflection coefficient 3 */
const int16_t WebRtcIsacfix_kRc3Levels[11] = {
  -32104, -29503, -23157, -15186, -7347, -1359, 5829, 17535, 24512, 29503, 32104
};

/* representation levels for quantized reflection coefficient 4 */
const int16_t WebRtcIsacfix_kRc4Levels[11] = {
  -32104, -29503, -24512, -15362, -6665, -342, 6596, 14585, 24512, 29503, 32104
};

/* representation levels for quantized reflection coefficient 5 */
const int16_t WebRtcIsacfix_kRc5Levels[11] = {
  -32104, -29503, -24512, -15005, -6564, -106, 7123, 14920, 24512, 29503, 32104
};

/* representation levels for quantized reflection coefficient 6 */
const int16_t WebRtcIsacfix_kRc6Levels[11] = {
  -32104, -29503, -24512, -15096, -6656, -37, 7036, 14847, 24512, 29503, 32104
};

/* quantization boundary levels for reflection coefficients */
const int16_t WebRtcIsacfix_kRcBound[12] = {
  -32768, -31441, -27566, -21458, -13612, -4663,
  4663, 13612, 21458, 27566, 31441, 32767
};

/* initial index for AR reflection coefficient quantizer and cdf table search */
const uint16_t WebRtcIsacfix_kRcInitInd[6] = {
  5,  5,  5,  5,  5,  5
};

/* pointers to AR cdf tables */
const uint16_t *WebRtcIsacfix_kRcCdfPtr[AR_ORDER] = {
  WebRtcIsacfix_kRc1Cdf,
  WebRtcIsacfix_kRc2Cdf,
  WebRtcIsacfix_kRc3Cdf,
  WebRtcIsacfix_kRc4Cdf,
  WebRtcIsacfix_kRc5Cdf,
  WebRtcIsacfix_kRc6Cdf
};

/* pointers to AR representation levels tables */
const int16_t *WebRtcIsacfix_kRcLevPtr[AR_ORDER] = {
  WebRtcIsacfix_kRc1Levels,
  WebRtcIsacfix_kRc2Levels,
  WebRtcIsacfix_kRc3Levels,
  WebRtcIsacfix_kRc4Levels,
  WebRtcIsacfix_kRc5Levels,
  WebRtcIsacfix_kRc6Levels
};


/******************** GAIN Coefficient Tables ***********************/

/* cdf for Gain coefficient */
const uint16_t WebRtcIsacfix_kGainCdf[19] = {
  0,  2,  4,  6,  8,  10,  12,  14,  16,  1172,
  11119,  29411,  51699,  64445,  65527,  65529,  65531,  65533,  65535
};

/* representation levels for quantized squared Gain coefficient */
const int32_t WebRtcIsacfix_kGain2Lev[18] = {
  128, 128, 128, 128, 128, 215, 364, 709, 1268,
  1960, 3405, 6078, 11286, 17827, 51918, 134498, 487432, 2048000
};

/* quantization boundary levels for squared Gain coefficient */
const int32_t WebRtcIsacfix_kGain2Bound[19] = {
  0, 21, 35, 59, 99, 166, 280, 475, 815, 1414,
  2495, 4505, 8397, 16405, 34431, 81359, 240497, 921600, 0x7FFFFFFF
};

/* pointers to Gain cdf table */
const uint16_t *WebRtcIsacfix_kGainPtr[1] = {
  WebRtcIsacfix_kGainCdf
};

/* gain initial index for gain quantizer and cdf table search */
const uint16_t WebRtcIsacfix_kGainInitInd[1] = {
  11
};


/************************* Cosine Tables ****************************/

/* cosine table */
const int16_t WebRtcIsacfix_kCos[6][60] = {
  { 512,   512,   511,   510,   508,   507,   505,   502,   499,   496,
        493,   489,   485,   480,   476,   470,   465,   459,   453,   447,
 440,   433,   426,   418,   410,   402,   394,   385,   376,   367,
        357,   348,   338,   327,   317,   306,   295,   284,   273,   262,
 250,   238,   226,   214,   202,   190,   177,   165,   152,   139,
        126,   113,   100,   87,   73,   60,   47,   33,   20,   7       },
  { 512,   510,   508,   503,   498,   491,   483,   473,   462,   450,
        437,   422,   406,   389,   371,   352,   333,   312,   290,   268,
 244,   220,   196,   171,   145,   120,   93,   67,   40,   13,
        -13,   -40,   -67,   -93,   -120,   -145,   -171,   -196,   -220,   -244,
 -268,   -290,   -312,   -333,   -352,   -371,   -389,   -406,   -422,   -437,
        -450,   -462,   -473,   -483,   -491,   -498,   -503,   -508,   -510,   -512    },
  { 512,   508,   502,   493,   480,   465,   447,   426,   402,   376,
        348,   317,   284,   250,   214,   177,   139,   100,   60,   20,
 -20,   -60,   -100,   -139,   -177,   -214,   -250,   -284,   -317,   -348,
        -376,   -402,   -426,   -447,   -465,   -480,   -493,   -502,   -508,   -512,
 -512,   -508,   -502,   -493,   -480,   -465,   -447,   -426,   -402,   -376,
        -348,   -317,   -284,   -250,   -214,   -177,   -139,   -100,   -60,   -20     },
  { 511,   506,   495,   478,   456,   429,   398,   362,   322,   279,
        232,   183,   133,   80,   27,   -27,   -80,   -133,   -183,   -232,
 -279,   -322,   -362,   -398,   -429,   -456,   -478,   -495,   -506,   -511,
        -511,   -506,   -495,   -478,   -456,   -429,   -398,   -362,   -322,   -279,
 -232,   -183,   -133,   -80,   -27,   27,   80,   133,   183,   232,
        279,   322,   362,   398,   429,   456,   478,   495,   506,   511     },
  { 511,   502,   485,   459,   426,   385,   338,   284,   226,   165,
        100,   33,   -33,   -100,   -165,   -226,   -284,   -338,   -385,   -426,
 -459,   -485,   -502,   -511,   -511,   -502,   -485,   -459,   -426,   -385,
        -338,   -284,   -226,   -165,   -100,   -33,   33,   100,   165,   226,
 284,   338,   385,   426,   459,   485,   502,   511,   511,   502,
        485,   459,   426,   385,   338,   284,   226,   165,   100,   33      },
  { 510,   498,   473,   437,   389,   333,   268,   196,   120,   40,
        -40,   -120,   -196,   -268,   -333,   -389,   -437,   -473,   -498,   -510,
 -510,   -498,   -473,   -437,   -389,   -333,   -268,   -196,   -120,   -40,
        40,   120,   196,   268,   333,   389,   437,   473,   498,   510,
 510,   498,   473,   437,   389,   333,   268,   196,   120,   40,
        -40,   -120,   -196,   -268,   -333,   -389,   -437,   -473,   -498,   -510    }
};
