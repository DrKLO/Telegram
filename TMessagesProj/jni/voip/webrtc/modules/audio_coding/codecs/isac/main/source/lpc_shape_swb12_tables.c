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
 * SWB_KLT_Tables.c
 *
 * This file defines tables used for entropy coding of LPC shape of
 * upper-band signal if the bandwidth is 12 kHz.
 *
 */

#include "modules/audio_coding/codecs/isac/main/source/lpc_shape_swb12_tables.h"
#include "modules/audio_coding/codecs/isac/main/source/settings.h"

/*
* Mean value of LAR
*/
const double WebRtcIsac_kMeanLarUb12[UB_LPC_ORDER] =
{
  0.03748928306641, 0.09453441192543, -0.01112522344398, 0.03800237516842
};

/*
* A rotation matrix to decorrelate intra-vector correlation,
* i.e. correlation among components of LAR vector.
*/
const double WebRtcIsac_kIntraVecDecorrMatUb12[UB_LPC_ORDER][UB_LPC_ORDER] =
{
    {-0.00075365493856,  -0.05809964887743,  -0.23397966154116,   0.97050367376411},
    { 0.00625021257734,  -0.17299965610679,   0.95977735920651,   0.22104179375008},
    { 0.20543384258374,  -0.96202143495696,  -0.15301870801552,  -0.09432375099565},
    {-0.97865075648479,  -0.20300322280841,  -0.02581111653779,  -0.01913568980258}
};

/*
* A rotation matrix to remove correlation among LAR coefficients
* of different LAR vectors. One might guess that decorrelation matrix
* for the first component should differ from the second component
* but we haven't observed a significant benefit of having different
* decorrelation matrices for different components.
*/
const double WebRtcIsac_kInterVecDecorrMatUb12
[UB_LPC_VEC_PER_FRAME][UB_LPC_VEC_PER_FRAME] =
{
    { 0.70650597970460,  -0.70770707262373},
    {-0.70770707262373,  -0.70650597970460}
};

/*
* LAR quantization step-size.
*/
const double WebRtcIsac_kLpcShapeQStepSizeUb12 = 0.150000;

/*
* The smallest reconstruction points for quantiztion of LAR coefficients.
*/
const double WebRtcIsac_kLpcShapeLeftRecPointUb12
[UB_LPC_ORDER*UB_LPC_VEC_PER_FRAME] =
{
    -0.900000, -1.050000, -1.350000, -1.800000, -1.350000, -1.650000,
    -2.250000, -3.450000
};

/*
* Number of reconstruction points of quantizers for LAR coefficients.
*/
const int16_t WebRtcIsac_kLpcShapeNumRecPointUb12
[UB_LPC_ORDER * UB_LPC_VEC_PER_FRAME] =
{
    13, 15, 19, 27, 19, 24, 32, 48
};

/*
* Starting index for entropy decoder to search for the right interval,
* one entry per LAR coefficient
*/
const uint16_t WebRtcIsac_kLpcShapeEntropySearchUb12
[UB_LPC_ORDER * UB_LPC_VEC_PER_FRAME] =
{
     6,  7,  9, 13,  9, 12, 16, 24
};

/*
* The following 8 vectors define CDF of 8 decorrelated LAR
* coefficients.
*/
const uint16_t WebRtcIsac_kLpcShapeCdfVec0Ub12[14] =
{
     0,    13,    95,   418,  1687,  6498, 21317, 44200, 59029, 63849, 65147,
 65449, 65525, 65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec1Ub12[16] =
{
     0,    10,    59,   255,   858,  2667,  8200, 22609, 42988, 57202, 62947,
 64743, 65308, 65476, 65522, 65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec2Ub12[20] =
{
     0,    18,    40,   118,   332,   857,  2017,  4822, 11321, 24330, 41279,
 54342, 60637, 63394, 64659, 65184, 65398, 65482, 65518, 65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec3Ub12[28] =
{
     0,    21,    38,    90,   196,   398,   770,  1400,  2589,  4650,  8211,
 14933, 26044, 39592, 50814, 57452, 60971, 62884, 63995, 64621, 65019, 65273,
 65410, 65480, 65514, 65522, 65531, 65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec4Ub12[20] =
{
     0,     7,    46,   141,   403,   969,  2132,  4649, 10633, 24902, 43254,
 54665, 59928, 62674, 64173, 64938, 65293, 65464, 65523, 65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec5Ub12[25] =
{
     0,     7,    22,    72,   174,   411,   854,  1737,  3545,  6774, 13165,
 25221, 40980, 52821, 58714, 61706, 63472, 64437, 64989, 65287, 65430, 65503,
 65525, 65529, 65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec6Ub12[33] =
{
     0,    11,    21,    36,    65,   128,   228,   401,   707,  1241,  2126,
  3589,  6060, 10517, 18853, 31114, 42477, 49770, 54271, 57467, 59838, 61569,
 62831, 63772, 64433, 64833, 65123, 65306, 65419, 65466, 65499, 65519, 65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec7Ub12[49] =
{
     0,    14,    34,    67,   107,   167,   245,   326,   449,   645,   861,
  1155,  1508,  2003,  2669,  3544,  4592,  5961,  7583,  9887, 13256, 18765,
 26519, 34077, 40034, 44349, 47795, 50663, 53262, 55473, 57458, 59122, 60592,
 61742, 62690, 63391, 63997, 64463, 64794, 65045, 65207, 65309, 65394, 65443,
 65478, 65504, 65514, 65523, 65535
};

/*
* An array of pointers to CDFs of decorrelated LARs
*/
const uint16_t* WebRtcIsac_kLpcShapeCdfMatUb12
[UB_LPC_ORDER * UB_LPC_VEC_PER_FRAME] =
{
    WebRtcIsac_kLpcShapeCdfVec0Ub12, WebRtcIsac_kLpcShapeCdfVec1Ub12,
    WebRtcIsac_kLpcShapeCdfVec2Ub12, WebRtcIsac_kLpcShapeCdfVec3Ub12,
    WebRtcIsac_kLpcShapeCdfVec4Ub12, WebRtcIsac_kLpcShapeCdfVec5Ub12,
    WebRtcIsac_kLpcShapeCdfVec6Ub12, WebRtcIsac_kLpcShapeCdfVec7Ub12
};
