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
 * fft.c
 *
 * Fast Fourier Transform
 *
 */


#include "modules/audio_coding/codecs/isac/fix/source/fft.h"

static const int16_t kSortTabFft[240] = {
  0, 60, 120, 180, 20, 80, 140, 200, 40, 100, 160, 220,
  4, 64, 124, 184, 24, 84, 144, 204, 44, 104, 164, 224,
  8, 68, 128, 188, 28, 88, 148, 208, 48, 108, 168, 228,
  12, 72, 132, 192, 32, 92, 152, 212, 52, 112, 172, 232,
  16, 76, 136, 196, 36, 96, 156, 216, 56, 116, 176, 236,
  1, 61, 121, 181, 21, 81, 141, 201, 41, 101, 161, 221,
  5, 65, 125, 185, 25, 85, 145, 205, 45, 105, 165, 225,
  9, 69, 129, 189, 29, 89, 149, 209, 49, 109, 169, 229,
  13, 73, 133, 193, 33, 93, 153, 213, 53, 113, 173, 233,
  17, 77, 137, 197, 37, 97, 157, 217, 57, 117, 177, 237,
  2, 62, 122, 182, 22, 82, 142, 202, 42, 102, 162, 222,
  6, 66, 126, 186, 26, 86, 146, 206, 46, 106, 166, 226,
  10, 70, 130, 190, 30, 90, 150, 210, 50, 110, 170, 230,
  14, 74, 134, 194, 34, 94, 154, 214, 54, 114, 174, 234,
  18, 78, 138, 198, 38, 98, 158, 218, 58, 118, 178, 238,
  3, 63, 123, 183, 23, 83, 143, 203, 43, 103, 163, 223,
  7, 67, 127, 187, 27, 87, 147, 207, 47, 107, 167, 227,
  11, 71, 131, 191, 31, 91, 151, 211, 51, 111, 171, 231,
  15, 75, 135, 195, 35, 95, 155, 215, 55, 115, 175, 235,
  19, 79, 139, 199, 39, 99, 159, 219, 59, 119, 179, 239
};

/* Cosine table in Q14 */
static const int16_t kCosTabFfftQ14[240] = {
  16384,  16378, 16362,   16333,  16294,  16244,  16182,  16110,  16026,  15931,  15826,  15709,
  15582,  15444, 15296,   15137,  14968,  14788,  14598,  14399,  14189,  13970,  13741,  13502,
  13255,  12998, 12733,   12458,  12176,  11885,  11585,  11278,  10963,  10641,  10311,   9974,
  9630,   9280,  8923,    8561,   8192,   7818,   7438,   7053,   6664,   6270,   5872,   5469,
  5063,   4653,  4240,    3825,   3406,   2986,   2563,   2139,   1713,   1285,    857,    429,
  0,   -429,  -857,   -1285,  -1713,  -2139,  -2563,  -2986,  -3406,  -3825,  -4240,  -4653,
  -5063,  -5469, -5872,   -6270,  -6664,  -7053,  -7438,  -7818,  -8192,  -8561,  -8923,  -9280,
  -9630,  -9974, -10311, -10641, -10963, -11278, -11585, -11885, -12176, -12458, -12733, -12998,
  -13255, -13502, -13741, -13970, -14189, -14399, -14598, -14788, -14968, -15137, -15296, -15444,
  -15582, -15709, -15826, -15931, -16026, -16110, -16182, -16244, -16294, -16333, -16362, -16378,
  -16384, -16378, -16362, -16333, -16294, -16244, -16182, -16110, -16026, -15931, -15826, -15709,
  -15582, -15444, -15296, -15137, -14968, -14788, -14598, -14399, -14189, -13970, -13741, -13502,
  -13255, -12998, -12733, -12458, -12176, -11885, -11585, -11278, -10963, -10641, -10311,  -9974,
  -9630,  -9280,  -8923,  -8561,  -8192,  -7818,  -7438,  -7053,  -6664,  -6270,  -5872,  -5469,
  -5063,  -4653,  -4240,  -3825,  -3406,  -2986,  -2563,  -2139,  -1713,  -1285,   -857,   -429,
  0,    429,    857,   1285,   1713,   2139,   2563,   2986,   3406,   3825,   4240,   4653,
  5063,   5469,   5872,   6270,   6664,   7053,   7438,   7818,   8192,   8561,   8923,   9280,
  9630,   9974,  10311,  10641,  10963,  11278,  11585,  11885,  12176,  12458,  12733,  12998,
  13255,  13502,  13741,  13970,  14189,  14399,  14598,  14788,  14968,  15137,  15296,  15444,
  15582,  15709,  15826,  15931,  16026,  16110,  16182,  16244,  16294,  16333,  16362,  16378
};



/* Uses 16x16 mul, without rounding, which is faster. Uses WEBRTC_SPL_MUL_16_16_RSFT */
int16_t WebRtcIsacfix_FftRadix16Fastest(int16_t RexQx[], int16_t ImxQx[], int16_t iSign) {

  int16_t dd, ee, ff, gg, hh, ii;
  int16_t k0, k1, k2, k3, k4, kk;
  int16_t tmp116, tmp216;

  int16_t ccc1Q14, ccc2Q14, ccc3Q14, sss1Q14, sss2Q14, sss3Q14;
  int16_t sss60Q14, ccc72Q14, sss72Q14;
  int16_t aaQx, ajQx, akQx, ajmQx, ajpQx, akmQx, akpQx;
  int16_t bbQx, bjQx, bkQx, bjmQx, bjpQx, bkmQx, bkpQx;

  int16_t ReDATAQx[240],  ImDATAQx[240];

  sss60Q14 = kCosTabFfftQ14[20];
  ccc72Q14 = kCosTabFfftQ14[48];
  sss72Q14 = kCosTabFfftQ14[12];

  if (iSign < 0) {
    sss72Q14 = -sss72Q14;
    sss60Q14 = -sss60Q14;
  }
  /* Complexity is: 10 cycles */

  /* compute fourier transform */

  // transform for factor of 4
  for (kk=0; kk<60; kk++) {
    k0 = kk;
    k1 = k0 + 60;
    k2 = k1 + 60;
    k3 = k2 + 60;

    akpQx = RexQx[k0] + RexQx[k2];
    akmQx = RexQx[k0] - RexQx[k2];
    ajpQx = RexQx[k1] + RexQx[k3];
    ajmQx = RexQx[k1] - RexQx[k3];
    bkpQx = ImxQx[k0] + ImxQx[k2];
    bkmQx = ImxQx[k0] - ImxQx[k2];
    bjpQx = ImxQx[k1] + ImxQx[k3];
    bjmQx = ImxQx[k1] - ImxQx[k3];

    RexQx[k0] = akpQx + ajpQx;
    ImxQx[k0] = bkpQx + bjpQx;
    ajpQx = akpQx - ajpQx;
    bjpQx = bkpQx - bjpQx;
    if (iSign < 0) {
      akpQx = akmQx + bjmQx;
      bkpQx = bkmQx - ajmQx;
      akmQx -= bjmQx;
      bkmQx += ajmQx;
    } else {
      akpQx = akmQx - bjmQx;
      bkpQx = bkmQx + ajmQx;
      akmQx += bjmQx;
      bkmQx -= ajmQx;
    }

    ccc1Q14 = kCosTabFfftQ14[kk];
    ccc2Q14 = kCosTabFfftQ14[2 * kk];
    ccc3Q14 = kCosTabFfftQ14[3 * kk];
    sss1Q14 = kCosTabFfftQ14[kk + 60];
    sss2Q14 = kCosTabFfftQ14[2 * kk + 60];
    sss3Q14 = kCosTabFfftQ14[3 * kk + 60];
    if (iSign==1) {
      sss1Q14 = -sss1Q14;
      sss2Q14 = -sss2Q14;
      sss3Q14 = -sss3Q14;
    }

    //Do several multiplications like Q14*Q16>>14 = Q16
    // RexQ16[k1] = akpQ16 * ccc1Q14 - bkpQ16 * sss1Q14;
    // RexQ16[k2] = ajpQ16 * ccc2Q14 - bjpQ16 * sss2Q14;
    // RexQ16[k3] = akmQ16 * ccc3Q14 - bkmQ16 * sss3Q14;
    // ImxQ16[k1] = akpQ16 * sss1Q14 + bkpQ16 * ccc1Q14;
    // ImxQ16[k2] = ajpQ16 * sss2Q14 + bjpQ16 * ccc2Q14;
    // ImxQ16[k3] = akmQ16 * sss3Q14 + bkmQ16 * ccc3Q14;

    RexQx[k1] = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(ccc1Q14, akpQx, 14) -
        (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(sss1Q14, bkpQx, 14); // 6 non-mul + 2 mul cycles, i.e. 8 cycles (6+2*7=20 cycles if 16x32mul)
    RexQx[k2] = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(ccc2Q14, ajpQx, 14) -
        (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(sss2Q14, bjpQx, 14);
    RexQx[k3] = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(ccc3Q14, akmQx, 14) -
        (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(sss3Q14, bkmQx, 14);
    ImxQx[k1] = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(sss1Q14, akpQx, 14) +
        (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(ccc1Q14, bkpQx, 14);
    ImxQx[k2] = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(sss2Q14, ajpQx, 14) +
        (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(ccc2Q14, bjpQx, 14);
    ImxQx[k3] = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(sss3Q14, akmQx, 14) +
        (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(ccc3Q14, bkmQx, 14);
    //This mul segment needs 6*8 = 48 cycles for 16x16 muls, but 6*20 = 120 cycles for 16x32 muls


  }
  /* Complexity is: 51+48 = 99 cycles for 16x16 muls, but 51+120 = 171 cycles for 16x32 muls*/

  // transform for factor of 3
  kk=0;
  k1=20;
  k2=40;

  for (hh=0; hh<4; hh++) {
    for (ii=0; ii<20; ii++) {
      akQx = RexQx[kk];
      bkQx = ImxQx[kk];
      ajQx = RexQx[k1] + RexQx[k2];
      bjQx = ImxQx[k1] + ImxQx[k2];
      RexQx[kk] = akQx + ajQx;
      ImxQx[kk] = bkQx + bjQx;
      tmp116 = ajQx >> 1;
      tmp216 = bjQx >> 1;
      akQx = akQx - tmp116;
      bkQx = bkQx - tmp216;
      tmp116 = RexQx[k1] - RexQx[k2];
      tmp216 = ImxQx[k1] - ImxQx[k2];

      ajQx = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(sss60Q14, tmp116, 14); // Q14*Qx>>14 = Qx
      bjQx = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(sss60Q14, tmp216, 14); // Q14*Qx>>14 = Qx
      RexQx[k1] = akQx - bjQx;
      RexQx[k2] = akQx + bjQx;
      ImxQx[k1] = bkQx + ajQx;
      ImxQx[k2] = bkQx - ajQx;

      kk++;
      k1++;
      k2++;
    }
    /* Complexity : (31+6)*20 = 740 cycles for 16x16 muls, but (31+18)*20 = 980 cycles for 16x32 muls*/
    kk=kk+40;
    k1=k1+40;
    k2=k2+40;
  }
  /* Complexity : 4*(740+3) = 2972 cycles for 16x16 muls, but 4*(980+3) = 3932 cycles for 16x32 muls*/

  /* multiply by rotation factor for odd factor 3 or 5 (not for 4)
     Same code (duplicated) for both ii=2 and ii=3 */
  kk = 1;
  ee = 0;
  ff = 0;

  for (gg=0; gg<19; gg++) {
    kk += 20;
    ff = ff+4;
    for (hh=0; hh<2; hh++) {
      ee = ff + hh * ff;
      dd = ee + 60;
      ccc2Q14 = kCosTabFfftQ14[ee];
      sss2Q14 = kCosTabFfftQ14[dd];
      if (iSign==1) {
        sss2Q14 = -sss2Q14;
      }
      for (ii=0; ii<4; ii++) {
        akQx = RexQx[kk];
        bkQx = ImxQx[kk];
        RexQx[kk] = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(ccc2Q14, akQx, 14) - // Q14*Qx>>14 = Qx
            (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(sss2Q14, bkQx, 14);
        ImxQx[kk] = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(sss2Q14, akQx, 14) + // Q14*Qx>>14 = Qx
            (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(ccc2Q14, bkQx, 14);


        kk += 60;
      }
      kk = kk - 220;
    }
    // Complexity: 2*(13+5+4*13+2) = 144 for 16x16 muls, but 2*(13+5+4*33+2) = 304 cycles for 16x32 muls
    kk = kk - 59;
  }
  // Complexity: 19*144 = 2736 for 16x16 muls, but 19*304 = 5776 cycles for 16x32 muls

  // transform for factor of 5
  kk = 0;
  ccc2Q14 = kCosTabFfftQ14[96];
  sss2Q14 = kCosTabFfftQ14[84];
  if (iSign==1) {
    sss2Q14 = -sss2Q14;
  }

  for (hh=0; hh<4; hh++) {
    for (ii=0; ii<12; ii++) {
      k1 = kk + 4;
      k2 = k1 + 4;
      k3 = k2 + 4;
      k4 = k3 + 4;

      akpQx = RexQx[k1] + RexQx[k4];
      akmQx = RexQx[k1] - RexQx[k4];
      bkpQx = ImxQx[k1] + ImxQx[k4];
      bkmQx = ImxQx[k1] - ImxQx[k4];
      ajpQx = RexQx[k2] + RexQx[k3];
      ajmQx = RexQx[k2] - RexQx[k3];
      bjpQx = ImxQx[k2] + ImxQx[k3];
      bjmQx = ImxQx[k2] - ImxQx[k3];
      aaQx = RexQx[kk];
      bbQx = ImxQx[kk];
      RexQx[kk] = aaQx + akpQx + ajpQx;
      ImxQx[kk] = bbQx + bkpQx + bjpQx;

      akQx = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(ccc72Q14, akpQx, 14) +
          (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(ccc2Q14, ajpQx, 14)  + aaQx;
      bkQx = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(ccc72Q14, bkpQx, 14) +
          (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(ccc2Q14, bjpQx, 14)  + bbQx;
      ajQx = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(sss72Q14, akmQx, 14) +
          (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(sss2Q14, ajmQx, 14);
      bjQx = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(sss72Q14, bkmQx, 14) +
          (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(sss2Q14, bjmQx, 14);
      // 32+4*8=64 or 32+4*20=112

      RexQx[k1] = akQx - bjQx;
      RexQx[k4] = akQx + bjQx;
      ImxQx[k1] = bkQx + ajQx;
      ImxQx[k4] = bkQx - ajQx;

      akQx = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(ccc2Q14, akpQx, 14)  +
          (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(ccc72Q14, ajpQx, 14) + aaQx;
      bkQx = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(ccc2Q14, bkpQx, 14)  +
          (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(ccc72Q14, bjpQx, 14) + bbQx;
      ajQx = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(sss2Q14, akmQx, 14) -
          (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(sss72Q14, ajmQx, 14);
      bjQx = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(sss2Q14, bkmQx, 14) -
          (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(sss72Q14, bjmQx, 14);
      // 8+4*8=40 or 8+4*20=88

      RexQx[k2] = akQx - bjQx;
      RexQx[k3] = akQx + bjQx;
      ImxQx[k2] = bkQx + ajQx;
      ImxQx[k3] = bkQx - ajQx;

      kk = k4 + 4;
    }
    // Complexity: 12*(64+40+10) = 1368 for 16x16 muls, but 12*(112+88+10) = 2520 cycles for 16x32 muls
    kk -= 239;
  }
  // Complexity: 4*1368 = 5472 for 16x16 muls, but 4*2520 = 10080 cycles for 16x32 muls

  /* multiply by rotation factor for odd factor 3 or 5 (not for 4)
     Same code (duplicated) for both ii=2 and ii=3 */
  kk = 1;
  ee=0;

  for (gg=0; gg<3; gg++) {
    kk += 4;
    dd = 12 + 12 * gg;
    ff = 0;
    for (hh=0; hh<4; hh++) {
      ff = ff+dd;
      ee = ff+60;
      for (ii=0; ii<12; ii++) {
        akQx = RexQx[kk];
        bkQx = ImxQx[kk];

        ccc2Q14 = kCosTabFfftQ14[ff];
        sss2Q14 = kCosTabFfftQ14[ee];

        if (iSign==1) {
          sss2Q14 = -sss2Q14;
        }

        RexQx[kk] = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(ccc2Q14, akQx, 14) -
            (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(sss2Q14, bkQx, 14);
        ImxQx[kk] = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(sss2Q14, akQx, 14) +
            (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(ccc2Q14, bkQx, 14);

        kk += 20;
      }
      kk = kk - 236;
      // Complexity: 12*(12+12) = 288 for 16x16 muls, but 12*(12+32) = 528 cycles for 16x32 muls
    }
    kk = kk - 19;
    // Complexity: 4*288+6 for 16x16 muls, but 4*528+6 cycles for 16x32 muls
  }
  // Complexity: 3*4*288+6 = 3462 for 16x16 muls, but 3*4*528+6 = 6342 cycles for 16x32 muls


  // last transform for factor of 4 */
  for (kk=0; kk<240; kk=kk+4) {
    k1 = kk + 1;
    k2 = k1 + 1;
    k3 = k2 + 1;

    akpQx = RexQx[kk] + RexQx[k2];
    akmQx = RexQx[kk] - RexQx[k2];
    ajpQx = RexQx[k1] + RexQx[k3];
    ajmQx = RexQx[k1] - RexQx[k3];
    bkpQx = ImxQx[kk] + ImxQx[k2];
    bkmQx = ImxQx[kk] - ImxQx[k2];
    bjpQx = ImxQx[k1] + ImxQx[k3];
    bjmQx = ImxQx[k1] - ImxQx[k3];
    RexQx[kk] = akpQx + ajpQx;
    ImxQx[kk] = bkpQx + bjpQx;
    ajpQx = akpQx - ajpQx;
    bjpQx = bkpQx - bjpQx;
    if (iSign < 0) {
      akpQx = akmQx + bjmQx;
      bkpQx = bkmQx - ajmQx;
      akmQx -= bjmQx;
      bkmQx += ajmQx;
    } else {
      akpQx = akmQx - bjmQx;
      bkpQx = bkmQx + ajmQx;
      akmQx += bjmQx;
      bkmQx -= ajmQx;
    }
    RexQx[k1] = akpQx;
    RexQx[k2] = ajpQx;
    RexQx[k3] = akmQx;
    ImxQx[k1] = bkpQx;
    ImxQx[k2] = bjpQx;
    ImxQx[k3] = bkmQx;
  }
  // Complexity: 60*45 = 2700 for 16x16 muls, but 60*45 = 2700 cycles for 16x32 muls

  /* permute the results to normal order */
  for (ii=0; ii<240; ii++) {
    ReDATAQx[ii]=RexQx[ii];
    ImDATAQx[ii]=ImxQx[ii];
  }
  // Complexity: 240*2=480 cycles

  for (ii=0; ii<240; ii++) {
    RexQx[ii]=ReDATAQx[kSortTabFft[ii]];
    ImxQx[ii]=ImDATAQx[kSortTabFft[ii]];
  }
  // Complexity: 240*2*2=960 cycles

  // Total complexity:
  //            16x16 16x32
  // Complexity:   10    10
  // Complexity:   99   171
  // Complexity: 2972  3932
  // Complexity: 2736  5776
  // Complexity: 5472 10080
  // Complexity: 3462  6342
  // Complexity: 2700  2700
  // Complexity:  480   480
  // Complexity:  960   960
  // =======================
  //            18891 30451
  //
  // If this FFT is called 2 time each frame, i.e. 67 times per second, it will correspond to
  // a C54 complexity of 67*18891/1000000 = 1.27 MIPS with 16x16-muls, and 67*30451/1000000 =
  // = 2.04 MIPS with 16x32-muls. Note that this routine somtimes is called 6 times during the
  // encoding of a frame, i.e. the max complexity would be 7/2*1.27 = 4.4 MIPS for the 16x16 mul case.


  return 0;
}
