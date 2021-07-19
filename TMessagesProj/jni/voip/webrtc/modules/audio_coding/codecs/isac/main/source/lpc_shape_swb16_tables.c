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
 * SWB16_KLT_Tables.c
 *
 * This file defines tables used for entropy coding of LPC shape of
 * upper-band signal if the bandwidth is 16 kHz.
 *
 */

#include "modules/audio_coding/codecs/isac/main/source/lpc_shape_swb16_tables.h"
#include "modules/audio_coding/codecs/isac/main/source/settings.h"

/*
* Mean value of LAR
*/
const double WebRtcIsac_kMeanLarUb16[UB_LPC_ORDER] =
{
0.454978, 0.364747, 0.102999, 0.104523
};

/*
* A rotation matrix to decorrelate intra-vector correlation,
* i.e. correlation among components of LAR vector.
*/
const double WebRtcIsac_kIintraVecDecorrMatUb16[UB_LPC_ORDER][UB_LPC_ORDER] =
{
    {-0.020528, -0.085858, -0.002431,  0.996093},
    {-0.033155,  0.036102,  0.998786,  0.004866},
    { 0.202627,  0.974853, -0.028940,  0.088132},
    {-0.978479,  0.202454, -0.039785, -0.002811}
};

/*
* A rotation matrix to remove correlation among LAR coefficients
* of different LAR vectors. One might guess that decorrelation matrix
* for the first component should differ from the second component
* but we haven't observed a significant benefit of having different
* decorrelation matrices for different components.
*/
const double WebRtcIsac_kInterVecDecorrMatUb16
[UB16_LPC_VEC_PER_FRAME][UB16_LPC_VEC_PER_FRAME] =
{
    { 0.291675, -0.515786,  0.644927,  0.482658},
    {-0.647220,  0.479712,  0.289556,  0.516856},
    { 0.643084,  0.485489, -0.289307,  0.516763},
    {-0.287185, -0.517823, -0.645389,  0.482553}
};

/*
* The following 16 vectors define CDF of 16 decorrelated LAR
* coefficients.
*/
const uint16_t WebRtcIsac_kLpcShapeCdfVec01Ub16[14] =
{
     0,      2,     20,    159,   1034,   5688,  20892,  44653,
 59849,  64485,  65383,  65518,  65534,  65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec1Ub16[16] =
{
     0,      1,      7,     43,    276,   1496,   6681,  21653,
 43891,  58859,  64022,  65248,  65489,  65529,  65534,  65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec2Ub16[18] =
{
     0,      1,      9,     54,    238,    933,   3192,   9461,
 23226,  42146,  56138,  62413,  64623,  65300,  65473,  65521,
 65533,  65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec3Ub16[30] =
{
     0,      2,      4,      8,     17,     36,     75,    155,
   329,    683,   1376,   2662,   5047,   9508,  17526,  29027,
 40363,  48997,  55096,  59180,  61789,  63407,  64400,  64967,
 65273,  65429,  65497,  65526,  65534,  65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec4Ub16[16] =
{
     0,      1,     10,     63,    361,   1785,   7407,  22242,
 43337,  58125,  63729,  65181,  65472,  65527,  65534,  65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec5Ub16[17] =
{
     0,      1,      7,     29,    134,    599,   2443,   8590,
 22962,  42635,  56911,  63060,  64940,  65408,  65513,  65531,
 65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec6Ub16[21] =
{
     0,      1,      5,     16,     57,    191,    611,   1808,
  4847,  11755,  24612,  40910,  53789,  60698,  63729,  64924,
 65346,  65486,  65523,  65532,  65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec7Ub16[36] =
{
     0,      1,      4,     12,     25,     55,    104,    184,
   314,    539,    926,   1550,   2479,   3861,   5892,   8845,
 13281,  20018,  29019,  38029,  45581,  51557,  56057,  59284,
 61517,  63047,  64030,  64648,  65031,  65261,  65402,  65480,
 65518,  65530,  65534,  65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec8Ub16[21] =
{
     0,      1,      2,      7,     26,    103,    351,   1149,
  3583,  10204,  23846,  41711,  55361,  61917,  64382,  65186,
 65433,  65506,  65528,  65534,  65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec01Ub160[21] =
{
     0,      6,     19,     63,    205,    638,   1799,   4784,
 11721,  24494,  40803,  53805,  60886,  63822,  64931,  65333,
 65472,  65517,  65530,  65533,  65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec01Ub161[28] =
{
     0,      1,      3,     11,     31,     86,    221,    506,
  1101,   2296,   4486,   8477,  15356,  26079,  38941,  49952,
 57165,  61257,  63426,  64549,  65097,  65351,  65463,  65510,
 65526,  65532,  65534,  65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec01Ub162[55] =
{
     0,      3,     12,     23,     42,     65,     89,    115,
   150,    195,    248,    327,    430,    580,    784,   1099,
  1586,   2358,   3651,   5899,   9568,  14312,  19158,  23776,
 28267,  32663,  36991,  41153,  45098,  48680,  51870,  54729,
 57141,  59158,  60772,  62029,  63000,  63761,  64322,  64728,
 65000,  65192,  65321,  65411,  65463,  65496,  65514,  65523,
 65527,  65529,  65531,  65532,  65533,  65534,  65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec01Ub163[26] =
{
     0,      2,      4,     10,     21,     48,    114,    280,
   701,   1765,   4555,  11270,  24267,  41213,  54285,  61003,
 63767,  64840,  65254,  65421,  65489,  65514,  65526,  65532,
 65534,  65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec01Ub164[28] =
{
     0,      1,      3,      6,     15,     36,     82,    196,
   453,   1087,   2557,   5923,  13016,  25366,  40449,  52582,
 59539,  62896,  64389,  65033,  65316,  65442,  65494,  65519,
 65529,  65533,  65534,  65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec01Ub165[34] =
{
     0,      2,      4,      8,     18,     35,     73,    146,
   279,    524,    980,   1789,   3235,   5784,  10040,  16998,
 27070,  38543,  48499,  55421,  59712,  62257,  63748,  64591,
 65041,  65278,  65410,  65474,  65508,  65522,  65530,  65533,
 65534,  65535
};

const uint16_t WebRtcIsac_kLpcShapeCdfVec01Ub166[71] =
{
     0,      1,      2,      6,     13,     26,     55,     92,
   141,    191,    242,    296,    355,    429,    522,    636,
   777,    947,   1162,   1428,   1753,   2137,   2605,   3140,
  3743,   4409,   5164,   6016,   6982,   8118,   9451,  10993,
 12754,  14810,  17130,  19780,  22864,  26424,  30547,  35222,
 40140,  44716,  48698,  52056,  54850,  57162,  59068,  60643,
 61877,  62827,  63561,  64113,  64519,  64807,  65019,  65167,
 65272,  65343,  65399,  65440,  65471,  65487,  65500,  65509,
 65518,  65524,  65527,  65531,  65533,  65534,  65535
};

/*
* An array of pointers to CDFs of decorrelated LARs
*/
const uint16_t* WebRtcIsac_kLpcShapeCdfMatUb16
[UB_LPC_ORDER * UB16_LPC_VEC_PER_FRAME] = {
     WebRtcIsac_kLpcShapeCdfVec01Ub16,
     WebRtcIsac_kLpcShapeCdfVec1Ub16,
     WebRtcIsac_kLpcShapeCdfVec2Ub16,
     WebRtcIsac_kLpcShapeCdfVec3Ub16,
     WebRtcIsac_kLpcShapeCdfVec4Ub16,
     WebRtcIsac_kLpcShapeCdfVec5Ub16,
     WebRtcIsac_kLpcShapeCdfVec6Ub16,
     WebRtcIsac_kLpcShapeCdfVec7Ub16,
     WebRtcIsac_kLpcShapeCdfVec8Ub16,
     WebRtcIsac_kLpcShapeCdfVec01Ub160,
     WebRtcIsac_kLpcShapeCdfVec01Ub161,
     WebRtcIsac_kLpcShapeCdfVec01Ub162,
     WebRtcIsac_kLpcShapeCdfVec01Ub163,
     WebRtcIsac_kLpcShapeCdfVec01Ub164,
     WebRtcIsac_kLpcShapeCdfVec01Ub165,
     WebRtcIsac_kLpcShapeCdfVec01Ub166
};

/*
* The smallest reconstruction points for quantiztion of LAR coefficients.
*/
const double WebRtcIsac_kLpcShapeLeftRecPointUb16
[UB_LPC_ORDER * UB16_LPC_VEC_PER_FRAME] =
{
 -0.8250,  -0.9750,  -1.1250,  -2.1750,  -0.9750,  -1.1250,  -1.4250,
 -2.6250,  -1.4250,  -1.2750,  -1.8750,  -3.6750,  -1.7250,  -1.8750,
 -2.3250,  -5.4750
};

/*
* Number of reconstruction points of quantizers for LAR coefficients.
*/
const int16_t WebRtcIsac_kLpcShapeNumRecPointUb16
[UB_LPC_ORDER * UB16_LPC_VEC_PER_FRAME] =
{
   13,    15,    17,    29,    15,    16,    20,    35,    20,
   20,    27,    54,    25,    27,    33,    70
};

/*
* Starting index for entropy decoder to search for the right interval,
* one entry per LAR coefficient
*/
const uint16_t WebRtcIsac_kLpcShapeEntropySearchUb16
[UB_LPC_ORDER * UB16_LPC_VEC_PER_FRAME] =
{
    6,     7,     8,    14,     7,     8,    10,    17,    10,
   10,    13,    27,    12,    13,    16,    35
};

/*
* LAR quantization step-size.
*/
const double WebRtcIsac_kLpcShapeQStepSizeUb16 = 0.150000;
