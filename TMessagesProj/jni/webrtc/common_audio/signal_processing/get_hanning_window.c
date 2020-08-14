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
 * This file contains the function WebRtcSpl_GetHanningWindow().
 * The description header can be found in signal_processing_library.h
 *
 */

#include "common_audio/signal_processing/include/signal_processing_library.h"

// Hanning table with 256 entries
static const int16_t kHanningTable[] = {
    1,      2,      6,     10,     15,     22,     30,     39,
   50,     62,     75,     89,    104,    121,    138,    157,
  178,    199,    222,    246,    271,    297,    324,    353,
  383,    413,    446,    479,    513,    549,    586,    624,
  663,    703,    744,    787,    830,    875,    920,    967,
 1015,   1064,   1114,   1165,   1218,   1271,   1325,   1381,
 1437,   1494,   1553,   1612,   1673,   1734,   1796,   1859,
 1924,   1989,   2055,   2122,   2190,   2259,   2329,   2399,
 2471,   2543,   2617,   2691,   2765,   2841,   2918,   2995,
 3073,   3152,   3232,   3312,   3393,   3475,   3558,   3641,
 3725,   3809,   3895,   3980,   4067,   4154,   4242,   4330,
 4419,   4509,   4599,   4689,   4781,   4872,   4964,   5057,
 5150,   5244,   5338,   5432,   5527,   5622,   5718,   5814,
 5910,   6007,   6104,   6202,   6299,   6397,   6495,   6594,
 6693,   6791,   6891,   6990,   7090,   7189,   7289,   7389,
 7489,   7589,   7690,   7790,   7890,   7991,   8091,   8192,
 8293,   8393,   8494,   8594,   8694,   8795,   8895,   8995,
 9095,   9195,   9294,   9394,   9493,   9593,   9691,   9790,
 9889,   9987,  10085,  10182,  10280,  10377,  10474,  10570,
10666,  10762,  10857,  10952,  11046,  11140,  11234,  11327,
11420,  11512,  11603,  11695,  11785,  11875,  11965,  12054,
12142,  12230,  12317,  12404,  12489,  12575,  12659,  12743,
12826,  12909,  12991,  13072,  13152,  13232,  13311,  13389,
13466,  13543,  13619,  13693,  13767,  13841,  13913,  13985,
14055,  14125,  14194,  14262,  14329,  14395,  14460,  14525,
14588,  14650,  14711,  14772,  14831,  14890,  14947,  15003,
15059,  15113,  15166,  15219,  15270,  15320,  15369,  15417,
15464,  15509,  15554,  15597,  15640,  15681,  15721,  15760,
15798,  15835,  15871,  15905,  15938,  15971,  16001,  16031,
16060,  16087,  16113,  16138,  16162,  16185,  16206,  16227,
16246,  16263,  16280,  16295,  16309,  16322,  16334,  16345,
16354,  16362,  16369,  16374,  16378,  16382,  16383,  16384
};

void WebRtcSpl_GetHanningWindow(int16_t *v, size_t size)
{
    size_t jj;
    int16_t *vptr1;

    int32_t index;
    int32_t factor = ((int32_t)0x40000000);

    factor = WebRtcSpl_DivW32W16(factor, (int16_t)size);
    if (size < 513)
        index = (int32_t)-0x200000;
    else
        index = (int32_t)-0x100000;
    vptr1 = v;

    for (jj = 0; jj < size; jj++)
    {
        index += factor;
        (*vptr1++) = kHanningTable[index >> 22];
    }

}
