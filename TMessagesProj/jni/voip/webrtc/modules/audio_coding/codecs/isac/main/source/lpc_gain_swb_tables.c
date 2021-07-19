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
 * SWB_KLT_Tables_LPCGain.c
 *
 * This file defines tables used for entropy coding of LPC Gain
 * of upper-band.
 *
 */

#include "modules/audio_coding/codecs/isac/main/source/lpc_gain_swb_tables.h"
#include "modules/audio_coding/codecs/isac/main/source/settings.h"

const double WebRtcIsac_kQSizeLpcGain = 0.100000;

const double WebRtcIsac_kMeanLpcGain = -3.3822;

/*
* The smallest reconstruction points for quantiztion of
* LPC gains.
*/
const double WebRtcIsac_kLeftRecPointLpcGain[SUBFRAMES] =
{
   -0.800000, -1.000000, -1.200000, -2.200000, -3.000000, -12.700000
};

/*
* Number of reconstruction points of quantizers for LPC Gains.
*/
const int16_t WebRtcIsac_kNumQCellLpcGain[SUBFRAMES] =
{
    17,  20,  25,  45,  77, 170
};
/*
* Starting index for entropy decoder to search for the right interval,
* one entry per LAR coefficient
*/
const uint16_t WebRtcIsac_kLpcGainEntropySearch[SUBFRAMES] =
{
     8,  10,  12,  22,  38,  85
};

/*
* The following 6 vectors define CDF of 6 decorrelated LPC
* gains.
*/
const uint16_t WebRtcIsac_kLpcGainCdfVec0[18] =
{
     0,    10,    27,    83,   234,   568,  1601,  4683, 16830, 57534, 63437,
 64767, 65229, 65408, 65483, 65514, 65527, 65535
};

const uint16_t WebRtcIsac_kLpcGainCdfVec1[21] =
{
     0,    15,    33,    84,   185,   385,   807,  1619,  3529,  7850, 19488,
 51365, 62437, 64548, 65088, 65304, 65409, 65484, 65507, 65522, 65535
};

const uint16_t WebRtcIsac_kLpcGainCdfVec2[26] =
{
     0,    15,    29,    54,    89,   145,   228,   380,   652,  1493,  4260,
 12359, 34133, 50749, 57224, 60814, 62927, 64078, 64742, 65103, 65311, 65418,
 65473, 65509, 65521, 65535
};

const uint16_t WebRtcIsac_kLpcGainCdfVec3[46] =
{
     0,     8,    12,    16,    26,    42,    56,    76,   111,   164,   247,
   366,   508,   693,  1000,  1442,  2155,  3188,  4854,  7387, 11249, 17617,
 30079, 46711, 56291, 60127, 62140, 63258, 63954, 64384, 64690, 64891, 65031,
 65139, 65227, 65293, 65351, 65399, 65438, 65467, 65492, 65504, 65510, 65518,
 65523, 65535
};

const uint16_t WebRtcIsac_kLpcGainCdfVec4[78] =
{
     0,    17,    29,    39,    51,    70,   104,   154,   234,   324,   443,
   590,   760,   971,  1202,  1494,  1845,  2274,  2797,  3366,  4088,  4905,
  5899,  7142,  8683, 10625, 12983, 16095, 20637, 28216, 38859, 47237, 51537,
 54150, 56066, 57583, 58756, 59685, 60458, 61103, 61659, 62144, 62550, 62886,
 63186, 63480, 63743, 63954, 64148, 64320, 64467, 64600, 64719, 64837, 64939,
 65014, 65098, 65160, 65211, 65250, 65290, 65325, 65344, 65366, 65391, 65410,
 65430, 65447, 65460, 65474, 65487, 65494, 65501, 65509, 65513, 65518, 65520,
 65535
};

const uint16_t WebRtcIsac_kLpcGainCdfVec5[171] =
{
     0,    10,    12,    14,    16,    18,    23,    29,    35,    42,    51,
    58,    65,    72,    78,    87,    96,   103,   111,   122,   134,   150,
   167,   184,   202,   223,   244,   265,   289,   315,   346,   379,   414,
   450,   491,   532,   572,   613,   656,   700,   751,   802,   853,   905,
   957,  1021,  1098,  1174,  1250,  1331,  1413,  1490,  1565,  1647,  1730,
  1821,  1913,  2004,  2100,  2207,  2314,  2420,  2532,  2652,  2783,  2921,
  3056,  3189,  3327,  3468,  3640,  3817,  3993,  4171,  4362,  4554,  4751,
  4948,  5142,  5346,  5566,  5799,  6044,  6301,  6565,  6852,  7150,  7470,
  7797,  8143,  8492,  8835,  9181,  9547,  9919, 10315, 10718, 11136, 11566,
 12015, 12482, 12967, 13458, 13953, 14432, 14903, 15416, 15936, 16452, 16967,
 17492, 18024, 18600, 19173, 19736, 20311, 20911, 21490, 22041, 22597, 23157,
 23768, 24405, 25034, 25660, 26280, 26899, 27614, 28331, 29015, 29702, 30403,
 31107, 31817, 32566, 33381, 34224, 35099, 36112, 37222, 38375, 39549, 40801,
 42074, 43350, 44626, 45982, 47354, 48860, 50361, 51845, 53312, 54739, 56026,
 57116, 58104, 58996, 59842, 60658, 61488, 62324, 63057, 63769, 64285, 64779,
 65076, 65344, 65430, 65500, 65517, 65535
};

/*
* An array of pointers to CDFs of decorrelated LPC Gains
*/
const uint16_t* WebRtcIsac_kLpcGainCdfMat[SUBFRAMES] =
{
    WebRtcIsac_kLpcGainCdfVec0, WebRtcIsac_kLpcGainCdfVec1,
    WebRtcIsac_kLpcGainCdfVec2, WebRtcIsac_kLpcGainCdfVec3,
    WebRtcIsac_kLpcGainCdfVec4, WebRtcIsac_kLpcGainCdfVec5
};

/*
* A matrix to decorrellate LPC gains of subframes.
*/
const double WebRtcIsac_kLpcGainDecorrMat[SUBFRAMES][SUBFRAMES] =
{
    {-0.150860,  0.327872,  0.367220,  0.504613,  0.559270,  0.409234},
    { 0.457128, -0.613591, -0.289283, -0.029734,  0.393760,  0.418240},
    {-0.626043,  0.136489, -0.439118, -0.448323,  0.135987,  0.420869},
    { 0.526617,  0.480187,  0.242552, -0.488754, -0.158713,  0.411331},
    {-0.302587, -0.494953,  0.588112, -0.063035, -0.404290,  0.387510},
    { 0.086378,  0.147714, -0.428875,  0.548300, -0.570121,  0.401391}
};
