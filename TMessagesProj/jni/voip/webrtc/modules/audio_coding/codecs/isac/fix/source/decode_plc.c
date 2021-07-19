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
 * decode_plc.c
 *
 * Packet Loss Concealment.
 *
 */

#include <string.h>

#include "modules/audio_coding/codecs/isac/fix/source/settings.h"
#include "modules/audio_coding/codecs/isac/fix/source/entropy_coding.h"
#include "modules/audio_coding/codecs/isac/fix/source/pitch_estimator.h"
#include "modules/audio_coding/codecs/isac/fix/source/bandwidth_estimator.h"
#include "modules/audio_coding/codecs/isac/fix/source/structs.h"
#include "modules/audio_coding/codecs/isac/fix/source/codec.h"


#define NO_OF_PRIMES 8
#define NOISE_FILTER_LEN 30

/*
 * function to decode the bitstream
 * returns the total number of bytes in the stream
 */

static int16_t plc_filterma_Fast(
    int16_t *In,  /* (i)   Vector to be filtered. InOut[-orderCoef+1]
                           to InOut[-1] contains state */
    int16_t *Out,  /* (o)   Filtered vector */
    int16_t *B,   /* (i)   The filter coefficients (in Q0) */
    int16_t Blen,  /* (i)   Number of B coefficients */
    int16_t len,   /* (i)  Number of samples to be filtered */
    int16_t reduceDecay,
    int16_t decay,
    int16_t rshift )
{
  int i, j;
  int32_t o;
  int32_t lim = (1 << (15 + rshift)) - 1;

  for (i = 0; i < len; i++)
  {
    const int16_t *b_ptr = &B[0];
    const int16_t *x_ptr = &In[i];

    o = (int32_t)0;

    for (j = 0;j < Blen; j++)
    {
      o = WebRtcSpl_AddSatW32(o, *b_ptr * *x_ptr);
      b_ptr++;
      x_ptr--;
    }

    /* to round off correctly */
    o = WebRtcSpl_AddSatW32(o, 1 << (rshift - 1));

    /* saturate according to the domain of the filter coefficients */
    o = WEBRTC_SPL_SAT((int32_t)lim, o, (int32_t)-lim);

    /* o should be in the range of int16_t */
    o >>= rshift;

    /* decay the output signal; this is specific to plc */
    *Out++ = (int16_t)((int16_t)o * decay >> 15);

    /* change the decay */
    decay -= reduceDecay;
    if( decay < 0 )
      decay = 0;
  }
  return( decay );
}








static __inline int32_t log2_Q8_T( uint32_t x ) {

  int32_t zeros;
  int16_t frac;

  zeros=WebRtcSpl_NormU32(x);
  frac = (int16_t)(((x << zeros) & 0x7FFFFFFF) >> 23);

  /* log2(magn(i)) */
  return ((31 - zeros) << 8) + frac;
}

static __inline int16_t  exp2_Q10_T(int16_t x) { // Both in and out in Q10

  int16_t tmp16_1, tmp16_2;

  tmp16_2=(int16_t)(0x0400|(x&0x03FF));
  tmp16_1 = -(x >> 10);
  if(tmp16_1>0)
    return tmp16_2 >> tmp16_1;
  else
    return tmp16_2 << -tmp16_1;

}


/*
  This is a fixed-point version of the above code with limLow = 700 and limHigh = 5000,
  hard-coded. The values 700 and 5000 were experimentally obtained.

  The function implements membership values for two sets. The mebership functions are
  of second orders corresponding to half-bell-shapped pulses.
*/
static void MemshipValQ15( int16_t in, int16_t *A, int16_t *B )
{
  int16_t x;

  in -= 700;    /* translate the lowLim to 0, limHigh = 5000 - 700, M = 2150 */

  if( in <= 2150 )
  {
    if( in > 0 )
    {
      /* b = in^2 / (2 * M^2), a = 1 - b in Q0.
         We have to compute in Q15 */

      /* x = in / 2150 {in Q15} = x * 15.2409 {in Q15} =
         x*15 + (x*983)/(2^12); note that 983/2^12 = 0.23999     */

      /* we are sure that x is in the range of int16_t            */
      x = (int16_t)(in * 15 + (in * 983 >> 12));
      /* b = x^2 / 2 {in Q15} so a shift of 16 is required to
         be in correct domain and one more for the division by 2 */
      *B = (int16_t)((x * x + 0x00010000) >> 17);
      *A = WEBRTC_SPL_WORD16_MAX - *B;
    }
    else
    {
      *B = 0;
      *A = WEBRTC_SPL_WORD16_MAX;
    }
  }
  else
  {
    if( in < 4300 )
    {
      /* This is a mirror case of the above */
      in = 4300 - in;
      x = (int16_t)(in * 15 + (in * 983 >> 12));
      /* b = x^2 / 2 {in Q15} so a shift of 16 is required to
         be in correct domain and one more for the division by 2 */
      *A = (int16_t)((x * x + 0x00010000) >> 17);
      *B = WEBRTC_SPL_WORD16_MAX - *A;

    }
    else
    {
      *A = 0;
      *B = WEBRTC_SPL_WORD16_MAX;
    }
  }
}




static void LinearResampler(int16_t* in,
                            int16_t* out,
                            size_t lenIn,
                            size_t lenOut)
{
  size_t n = (lenIn - 1) * RESAMP_RES;
  int16_t resOut, relativePos, diff; /* */
  size_t i, j;
  uint16_t udiff;

  if( lenIn == lenOut )
  {
    WEBRTC_SPL_MEMCPY_W16( out, in, lenIn );
    return;
  }

  resOut = WebRtcSpl_DivW32W16ResW16( (int32_t)n, (int16_t)(lenOut-1) );

  out[0] = in[0];
  for( i = 1, j = 0, relativePos = 0; i < lenOut; i++ )
  {

    relativePos += resOut;
    while( relativePos > RESAMP_RES )
    {
      j++;
      relativePos -= RESAMP_RES;
    }


    /* an overflow may happen and the differce in sample values may
     * require more than 16 bits. We like to avoid 32 bit arithmatic
     * as much as possible */

    if( (in[ j ] > 0) && (in[j + 1] < 0) )
    {
      udiff = (uint16_t)(in[ j ] - in[j + 1]);
      out[ i ] = in[ j ] - (uint16_t)( ((int32_t)( udiff * relativePos )) >> RESAMP_RES_BIT);
    }
    else
    {
      if( (in[j] < 0) && (in[j+1] > 0) )
      {
        udiff = (uint16_t)( in[j + 1] - in[ j ] );
        out[ i ] = in[ j ] + (uint16_t)( ((int32_t)( udiff * relativePos )) >> RESAMP_RES_BIT);
      }
      else
      {
        diff = in[ j + 1 ] - in[ j ];
        out[i] = in[j] + (int16_t)(diff * relativePos >> RESAMP_RES_BIT);
      }
    }
  }
}





void WebRtcIsacfix_DecodePlcImpl(int16_t *signal_out16,
                                 IsacFixDecoderInstance *ISACdec_obj,
                                 size_t *current_framesamples )
{
  int subframecnt;

  int16_t* Vector_Word16_1;
  int16_t  Vector_Word16_Extended_1[FRAMESAMPLES_HALF + NOISE_FILTER_LEN];
  int16_t* Vector_Word16_2;
  int16_t  Vector_Word16_Extended_2[FRAMESAMPLES_HALF + NOISE_FILTER_LEN];

  int32_t Vector_Word32_1[FRAMESAMPLES_HALF];
  int32_t Vector_Word32_2[FRAMESAMPLES_HALF];

  int16_t lofilt_coefQ15[ORDERLO*SUBFRAMES]; //refl. coeffs
  int16_t hifilt_coefQ15[ORDERHI*SUBFRAMES]; //refl. coeffs

  int16_t pitchLags_Q7[PITCH_SUBFRAMES];
  int16_t pitchGains_Q12[PITCH_SUBFRAMES];

  int16_t tmp_1, tmp_2;
  int32_t tmp32a, tmp32b;
  int16_t gainQ13;

  int16_t myDecayRate;

  /* ---------- PLC variables ------------ */
  size_t lag0, i, k;
  int16_t noiseIndex;
  int16_t stretchPitchLP[PITCH_MAX_LAG + 10], stretchPitchLP1[PITCH_MAX_LAG + 10];

  int32_t gain_lo_hiQ17[2*SUBFRAMES];

  int16_t nLP, pLP, wNoisyLP, wPriodicLP, tmp16;
  size_t minIdx;
  int32_t nHP, pHP, wNoisyHP, wPriodicHP, corr, minCorr, maxCoeff;
  int16_t noise1, rshift;


  int16_t ltpGain, pitchGain, myVoiceIndicator, myAbs, maxAbs;
  int32_t varIn, varOut, logVarIn, logVarOut, Q, logMaxAbs;
  int rightShiftIn, rightShiftOut;


  /* ------------------------------------- */


  myDecayRate = (DECAY_RATE);
  Vector_Word16_1 = &Vector_Word16_Extended_1[NOISE_FILTER_LEN];
  Vector_Word16_2 = &Vector_Word16_Extended_2[NOISE_FILTER_LEN];


  /* ----- Simply Copy Previous LPC parameters ------ */
  for( subframecnt = 0; subframecnt < SUBFRAMES; subframecnt++ )
  {
    /* lower Band */
    WEBRTC_SPL_MEMCPY_W16(&lofilt_coefQ15[ subframecnt * ORDERLO ],
                          (ISACdec_obj->plcstr_obj).lofilt_coefQ15, ORDERLO);
    gain_lo_hiQ17[2*subframecnt] = (ISACdec_obj->plcstr_obj).gain_lo_hiQ17[0];

    /* Upper Band */
    WEBRTC_SPL_MEMCPY_W16(&hifilt_coefQ15[ subframecnt * ORDERHI ],
                          (ISACdec_obj->plcstr_obj).hifilt_coefQ15, ORDERHI);
    gain_lo_hiQ17[2*subframecnt + 1] = (ISACdec_obj->plcstr_obj).gain_lo_hiQ17[1];
  }




  lag0 = (size_t)(((ISACdec_obj->plcstr_obj.lastPitchLag_Q7 + 64) >> 7) + 1);


  if( (ISACdec_obj->plcstr_obj).used != PLC_WAS_USED )
  {
    (ISACdec_obj->plcstr_obj).pitchCycles = 0;

    (ISACdec_obj->plcstr_obj).lastPitchLP =
        &((ISACdec_obj->plcstr_obj).prevPitchInvIn[FRAMESAMPLES_HALF - lag0]);
    minCorr = WEBRTC_SPL_WORD32_MAX;

    if ((FRAMESAMPLES_HALF - 10) > 2 * lag0)
    {
      minIdx = 11;
      for( i = 0; i < 21; i++ )
      {
        corr = 0;
        for( k = 0; k < lag0; k++ )
        {
          corr = WebRtcSpl_AddSatW32(corr, WEBRTC_SPL_ABS_W32(
              WebRtcSpl_SubSatW16(
                  (ISACdec_obj->plcstr_obj).lastPitchLP[k],
                  (ISACdec_obj->plcstr_obj).prevPitchInvIn[
                      FRAMESAMPLES_HALF - 2*lag0 - 10 + i + k ] ) ) );
        }
        if( corr < minCorr )
        {
          minCorr = corr;
          minIdx = i;
        }
      }
      (ISACdec_obj->plcstr_obj).prevPitchLP =
          &( (ISACdec_obj->plcstr_obj).prevPitchInvIn[
              FRAMESAMPLES_HALF - lag0*2 - 10 + minIdx] );
    }
    else
    {
      (ISACdec_obj->plcstr_obj).prevPitchLP =
          (ISACdec_obj->plcstr_obj).lastPitchLP;
    }
    pitchGain = (ISACdec_obj->plcstr_obj).lastPitchGain_Q12;

    WebRtcSpl_AutoCorrelation(
        &(ISACdec_obj->plcstr_obj).prevPitchInvIn[FRAMESAMPLES_HALF - lag0],
        lag0, 0, &varIn, &rightShiftIn);
    WebRtcSpl_AutoCorrelation(
        &(ISACdec_obj->plcstr_obj).prevPitchInvOut[PITCH_MAX_LAG + 10 - lag0],
        lag0, 0, &varOut, &rightShiftOut);

    maxAbs = 0;
    for( i = 0; i< lag0; i++)
    {
      myAbs = WEBRTC_SPL_ABS_W16(
          (ISACdec_obj->plcstr_obj).prevPitchInvOut[
              PITCH_MAX_LAG + 10 - lag0 + i] );
      maxAbs = (myAbs > maxAbs)? myAbs:maxAbs;
    }
    logVarIn = log2_Q8_T( (uint32_t)( varIn ) ) +
        (int32_t)(rightShiftIn << 8);
    logVarOut = log2_Q8_T( (uint32_t)( varOut ) ) +
        (int32_t)(rightShiftOut << 8);
    logMaxAbs = log2_Q8_T( (uint32_t)( maxAbs ) );

    ltpGain = (int16_t)(logVarOut - logVarIn);
    Q = 2 * logMaxAbs - ( logVarOut - 1512 );

    /*
     * ---
     * We are computing sqrt( (VarIn/lag0) / var( noise ) )
     * var( noise ) is almost 256. we have already computed log2( VarIn ) in Q8
     * so we actually compute 2^( 0.5*(log2( VarIn ) - log2( lag0 ) - log2( var(noise ) )  ).
     * Note that put log function is in Q8 but the exponential function is in Q10.
     * --
     */

    logVarIn -= log2_Q8_T( (uint32_t)( lag0 ) );
    tmp16 = (int16_t)((logVarIn<<1) - (4<<10) );
    rightShiftIn = 0;
    if( tmp16 > 4096 )
    {
      tmp16 -= 4096;
      tmp16 = exp2_Q10_T( tmp16 );
      tmp16 >>= 6;
    }
    else
      tmp16 = exp2_Q10_T( tmp16 )>>10;

    (ISACdec_obj->plcstr_obj).std = tmp16 - 4;

    if( (ltpGain < 110) || (ltpGain > 230) )
    {
      if( ltpGain < 100 && (pitchGain < 1800) )
      {
        (ISACdec_obj->plcstr_obj).A = WEBRTC_SPL_WORD16_MAX;
      }
      else
      {
        (ISACdec_obj->plcstr_obj).A = ((ltpGain < 110) && (Q < 800)
                                       )? WEBRTC_SPL_WORD16_MAX:0;
      }
      (ISACdec_obj->plcstr_obj).B = WEBRTC_SPL_WORD16_MAX -
          (ISACdec_obj->plcstr_obj).A;
    }
    else
    {
      if( (pitchGain < 450) || (pitchGain > 1600) )
      {
        (ISACdec_obj->plcstr_obj).A = ((pitchGain < 450)
                                       )? WEBRTC_SPL_WORD16_MAX:0;
        (ISACdec_obj->plcstr_obj).B = WEBRTC_SPL_WORD16_MAX -
            (ISACdec_obj->plcstr_obj).A;
      }
      else
      {
        myVoiceIndicator = ltpGain * 2 + pitchGain;
        MemshipValQ15( myVoiceIndicator,
                       &(ISACdec_obj->plcstr_obj).A, &(ISACdec_obj->plcstr_obj).B );
      }
    }



    myVoiceIndicator = ltpGain * 16 + pitchGain * 2 + (pitchGain >> 8);
    MemshipValQ15( myVoiceIndicator,
                   &(ISACdec_obj->plcstr_obj).A, &(ISACdec_obj->plcstr_obj).B );



    (ISACdec_obj->plcstr_obj).stretchLag = lag0;
    (ISACdec_obj->plcstr_obj).pitchIndex = 0;

  }
  else
  {
    myDecayRate = (DECAY_RATE<<2);
  }

  if( (ISACdec_obj->plcstr_obj).B < 1000 )
  {
    myDecayRate += (DECAY_RATE<<3);
  }

  /* ------------ reconstructing the residual signal ------------------ */

  LinearResampler( (ISACdec_obj->plcstr_obj).lastPitchLP,
                   stretchPitchLP, lag0, (ISACdec_obj->plcstr_obj).stretchLag );
  /* inverse pitch filter */

  pitchLags_Q7[0] = pitchLags_Q7[1] = pitchLags_Q7[2] = pitchLags_Q7[3] =
      (int16_t)((ISACdec_obj->plcstr_obj).stretchLag<<7);
  pitchGains_Q12[3] = ( (ISACdec_obj->plcstr_obj).lastPitchGain_Q12);
  pitchGains_Q12[2] = (int16_t)(pitchGains_Q12[3] * 1010 >> 10);
  pitchGains_Q12[1] = (int16_t)(pitchGains_Q12[2] * 1010 >> 10);
  pitchGains_Q12[0] = (int16_t)(pitchGains_Q12[1] * 1010 >> 10);


  /* most of the time either B or A are zero so seperating */
  if( (ISACdec_obj->plcstr_obj).B == 0 )
  {
    for( i = 0; i < FRAMESAMPLES_HALF; i++ )
    {
      /* --- Low Pass                                             */
      (ISACdec_obj->plcstr_obj).seed = WEBRTC_SPL_RAND(
          (ISACdec_obj->plcstr_obj).seed );
      Vector_Word16_1[i] = (ISACdec_obj->plcstr_obj.seed >> 10) - 16;

      /* --- Highpass                                              */
      (ISACdec_obj->plcstr_obj).seed = WEBRTC_SPL_RAND(
          (ISACdec_obj->plcstr_obj).seed );
      Vector_Word16_2[i] = (ISACdec_obj->plcstr_obj.seed >> 10) - 16;

    }
    for( i = 1; i < NOISE_FILTER_LEN; i++ )
    {
      (ISACdec_obj->plcstr_obj).seed = WEBRTC_SPL_RAND(
          (ISACdec_obj->plcstr_obj).seed );
      Vector_Word16_Extended_1[i] = (ISACdec_obj->plcstr_obj.seed >> 10) - 16;

      (ISACdec_obj->plcstr_obj).seed = WEBRTC_SPL_RAND(
          (ISACdec_obj->plcstr_obj).seed );
      Vector_Word16_Extended_2[i] = (ISACdec_obj->plcstr_obj.seed >> 10) - 16;
    }
    plc_filterma_Fast(Vector_Word16_1, Vector_Word16_Extended_1,
                      &(ISACdec_obj->plcstr_obj).prevPitchInvIn[FRAMESAMPLES_HALF -
                                                                NOISE_FILTER_LEN], (int16_t) NOISE_FILTER_LEN,
                      (int16_t) FRAMESAMPLES_HALF, (int16_t)(5),
                      (ISACdec_obj->plcstr_obj).decayCoeffNoise, (int16_t)(6));

    maxCoeff = WebRtcSpl_MaxAbsValueW32(
        &(ISACdec_obj->plcstr_obj).prevHP[
            PITCH_MAX_LAG + 10 - NOISE_FILTER_LEN], NOISE_FILTER_LEN );

    rshift = 0;
    while( maxCoeff > WEBRTC_SPL_WORD16_MAX )
    {
      maxCoeff >>= 1;
      rshift++;
    }
    for( i = 0; i < NOISE_FILTER_LEN; i++ ) {
      Vector_Word16_1[FRAMESAMPLES_HALF - NOISE_FILTER_LEN + i] =(int16_t)(
          ISACdec_obj->plcstr_obj.prevHP[PITCH_MAX_LAG + 10 - NOISE_FILTER_LEN +
                                         i] >> rshift);
    }
    (ISACdec_obj->plcstr_obj).decayCoeffNoise = plc_filterma_Fast(
        Vector_Word16_2,
        Vector_Word16_Extended_2,
        &Vector_Word16_1[FRAMESAMPLES_HALF - NOISE_FILTER_LEN],
        (int16_t) NOISE_FILTER_LEN,
        (int16_t) FRAMESAMPLES_HALF,
        (int16_t) (5),
        (ISACdec_obj->plcstr_obj).decayCoeffNoise,
        (int16_t) (7) );

    for( i = 0; i < FRAMESAMPLES_HALF; i++ )
      Vector_Word32_2[i] = Vector_Word16_Extended_2[i] << rshift;

    Vector_Word16_1 = Vector_Word16_Extended_1;
  }
  else
  {
    if( (ISACdec_obj->plcstr_obj).A == 0 )
    {
      /* ------ Periodic Vector ---                                */
      for( i = 0, noiseIndex = 0; i < FRAMESAMPLES_HALF; i++, noiseIndex++ )
      {
        /* --- Lowpass                                               */
        pLP = (int16_t)(stretchPitchLP[ISACdec_obj->plcstr_obj.pitchIndex] *
            ISACdec_obj->plcstr_obj.decayCoeffPriodic >> 15);

        /* --- Highpass                                              */
        pHP = (int32_t)WEBRTC_SPL_MUL_16_32_RSFT15(
            (ISACdec_obj->plcstr_obj).decayCoeffPriodic,
            (ISACdec_obj->plcstr_obj).prevHP[PITCH_MAX_LAG + 10 -
                                             (ISACdec_obj->plcstr_obj).stretchLag +
                                             (ISACdec_obj->plcstr_obj).pitchIndex] );

        /* --- lower the muliplier (more decay at next sample) --- */
        (ISACdec_obj->plcstr_obj).decayCoeffPriodic -= (myDecayRate);
        if( (ISACdec_obj->plcstr_obj).decayCoeffPriodic < 0 )
          (ISACdec_obj->plcstr_obj).decayCoeffPriodic = 0;

        (ISACdec_obj->plcstr_obj).pitchIndex++;

        if( (ISACdec_obj->plcstr_obj).pitchIndex ==
            (ISACdec_obj->plcstr_obj).stretchLag )
        {
          (ISACdec_obj->plcstr_obj).pitchIndex = 0;
          (ISACdec_obj->plcstr_obj).pitchCycles++;

          if( (ISACdec_obj->plcstr_obj).stretchLag != (lag0 + 1) )
          {
            (ISACdec_obj->plcstr_obj).stretchLag = lag0 + 1;
          }
          else
          {
            (ISACdec_obj->plcstr_obj).stretchLag = lag0;
          }

          (ISACdec_obj->plcstr_obj).stretchLag = (
              (ISACdec_obj->plcstr_obj).stretchLag > PITCH_MAX_LAG
                                                  )? (PITCH_MAX_LAG):(ISACdec_obj->plcstr_obj).stretchLag;

          LinearResampler( (ISACdec_obj->plcstr_obj).lastPitchLP,
                           stretchPitchLP, lag0, (ISACdec_obj->plcstr_obj).stretchLag );

          LinearResampler( (ISACdec_obj->plcstr_obj).prevPitchLP,
                           stretchPitchLP1, lag0, (ISACdec_obj->plcstr_obj).stretchLag );

          switch( (ISACdec_obj->plcstr_obj).pitchCycles )
          {
            case 1:
              {
                for( k=0; k<(ISACdec_obj->plcstr_obj).stretchLag; k++ )
                {
                  stretchPitchLP[k] = (int16_t)((
                      (int32_t)stretchPitchLP[k]* 3 +
                      (int32_t)stretchPitchLP1[k])>>2);
                }
                break;
              }
            case 2:
              {
                for( k=0; k<(ISACdec_obj->plcstr_obj).stretchLag; k++ )
                {
                  stretchPitchLP[k] = (int16_t)((
                      (int32_t)stretchPitchLP[k] +
                      (int32_t)stretchPitchLP1[k] )>>1);
                }
                break;
              }
            case 3:
              {
                for( k=0; k<(ISACdec_obj->plcstr_obj).stretchLag; k++ )
                {
                  stretchPitchLP[k] = (int16_t)((stretchPitchLP[k] +
                                                       (int32_t)stretchPitchLP1[k]*3 )>>2);
                }
                break;
              }
          }

          if( (ISACdec_obj->plcstr_obj).pitchCycles == 3 )
          {
            myDecayRate += 35; //(myDecayRate>>1);
            (ISACdec_obj->plcstr_obj).pitchCycles = 0;
          }

        }

        /* ------ Sum the noisy and periodic signals  ------ */
        Vector_Word16_1[i] = pLP;
        Vector_Word32_2[i] = pHP;
      }
    }
    else
    {
      for( i = 0, noiseIndex = 0; i < FRAMESAMPLES_HALF; i++, noiseIndex++ )
      {

        (ISACdec_obj->plcstr_obj).seed = WEBRTC_SPL_RAND(
            (ISACdec_obj->plcstr_obj).seed );

        noise1 = (ISACdec_obj->plcstr_obj.seed >> 10) - 16;

        nLP = (int16_t)((int16_t)(noise1 * ISACdec_obj->plcstr_obj.std) *
            ISACdec_obj->plcstr_obj.decayCoeffNoise >> 15);

        /* --- Highpass                                              */
        (ISACdec_obj->plcstr_obj).seed = WEBRTC_SPL_RAND(
            (ISACdec_obj->plcstr_obj).seed );
        noise1 = (ISACdec_obj->plcstr_obj.seed >> 11) - 8;

        nHP = (int32_t)WEBRTC_SPL_MUL_16_32_RSFT15(
            (ISACdec_obj->plcstr_obj).decayCoeffNoise,
            (int32_t)(noise1*(ISACdec_obj->plcstr_obj).std) );

        /* --- lower the muliplier (more decay at next sample) --- */
        (ISACdec_obj->plcstr_obj).decayCoeffNoise -= (myDecayRate);
        if( (ISACdec_obj->plcstr_obj).decayCoeffNoise < 0 )
          (ISACdec_obj->plcstr_obj).decayCoeffNoise = 0;

        /* ------ Periodic Vector ---                                */
        /* --- Lowpass                                               */
        pLP = (int16_t)(stretchPitchLP[ISACdec_obj->plcstr_obj.pitchIndex] *
            ISACdec_obj->plcstr_obj.decayCoeffPriodic >> 15);

        /* --- Highpass                                              */
        pHP = (int32_t)WEBRTC_SPL_MUL_16_32_RSFT15(
            (ISACdec_obj->plcstr_obj).decayCoeffPriodic,
            (ISACdec_obj->plcstr_obj).prevHP[PITCH_MAX_LAG + 10 -
                                             (ISACdec_obj->plcstr_obj).stretchLag +
                                             (ISACdec_obj->plcstr_obj).pitchIndex] );

        /* --- lower the muliplier (more decay at next sample) --- */
        (ISACdec_obj->plcstr_obj).decayCoeffPriodic -= (myDecayRate);
        if( (ISACdec_obj->plcstr_obj).decayCoeffPriodic < 0 )
        {
          (ISACdec_obj->plcstr_obj).decayCoeffPriodic = 0;
        }

        /* ------ Weighting the noisy and periodic vectors -------   */
        wNoisyLP = (int16_t)(ISACdec_obj->plcstr_obj.A * nLP >> 15);
        wNoisyHP = (int32_t)(WEBRTC_SPL_MUL_16_32_RSFT15(
            (ISACdec_obj->plcstr_obj).A, (nHP) ) );

        wPriodicLP = (int16_t)(ISACdec_obj->plcstr_obj.B * pLP >> 15);
        wPriodicHP = (int32_t)(WEBRTC_SPL_MUL_16_32_RSFT15(
            (ISACdec_obj->plcstr_obj).B, pHP));

        (ISACdec_obj->plcstr_obj).pitchIndex++;

        if((ISACdec_obj->plcstr_obj).pitchIndex ==
           (ISACdec_obj->plcstr_obj).stretchLag)
        {
          (ISACdec_obj->plcstr_obj).pitchIndex = 0;
          (ISACdec_obj->plcstr_obj).pitchCycles++;

          if( (ISACdec_obj->plcstr_obj).stretchLag != (lag0 + 1) )
            (ISACdec_obj->plcstr_obj).stretchLag = lag0 + 1;
          else
            (ISACdec_obj->plcstr_obj).stretchLag = lag0;

          (ISACdec_obj->plcstr_obj).stretchLag = (
              (ISACdec_obj->plcstr_obj).stretchLag > PITCH_MAX_LAG
                                                  )? (PITCH_MAX_LAG):(ISACdec_obj->plcstr_obj).stretchLag;
          LinearResampler(
              (ISACdec_obj->plcstr_obj).lastPitchLP,
              stretchPitchLP, lag0, (ISACdec_obj->plcstr_obj).stretchLag );

          LinearResampler((ISACdec_obj->plcstr_obj).prevPitchLP,
                          stretchPitchLP1, lag0, (ISACdec_obj->plcstr_obj).stretchLag );

          switch((ISACdec_obj->plcstr_obj).pitchCycles)
          {
            case 1:
              {
                for( k=0; k<(ISACdec_obj->plcstr_obj).stretchLag; k++ )
                {
                  stretchPitchLP[k] = (int16_t)((
                      (int32_t)stretchPitchLP[k]* 3 +
                      (int32_t)stretchPitchLP1[k] )>>2);
                }
                break;
              }
            case 2:
              {
                for( k=0; k<(ISACdec_obj->plcstr_obj).stretchLag; k++ )
                {
                  stretchPitchLP[k] = (int16_t)((
                      (int32_t)stretchPitchLP[k] +
                      (int32_t)stretchPitchLP1[k])>>1);
                }
                break;
              }
            case 3:
              {
                for( k=0; k<(ISACdec_obj->plcstr_obj).stretchLag; k++ )
                {
                  stretchPitchLP[k] = (int16_t)(
                      (stretchPitchLP[k] +
                       (int32_t)stretchPitchLP1[k]*3 )>>2);
                }
                break;
              }
          }

          if( (ISACdec_obj->plcstr_obj).pitchCycles == 3 )
          {
            myDecayRate += 55; //(myDecayRate>>1);
            (ISACdec_obj->plcstr_obj).pitchCycles = 0;
          }
        }

        /* ------ Sum the noisy and periodic signals  ------ */
        Vector_Word16_1[i] = WebRtcSpl_AddSatW16(wNoisyLP, wPriodicLP);
        Vector_Word32_2[i] = WebRtcSpl_AddSatW32(wNoisyHP, wPriodicHP);
      }
    }
  }
  /* ----------------- residual signal is reconstructed ------------------ */

  k = (ISACdec_obj->plcstr_obj).pitchIndex;
  /* --- Write one pitch cycle for recovery block --- */

  for( i = 0; i < RECOVERY_OVERLAP; i++ )
  {
    ISACdec_obj->plcstr_obj.overlapLP[i] = (int16_t)(
        stretchPitchLP[k] * ISACdec_obj->plcstr_obj.decayCoeffPriodic >> 15);
    k = ( k < ((ISACdec_obj->plcstr_obj).stretchLag - 1) )? (k+1):0;
  }

  (ISACdec_obj->plcstr_obj).lastPitchLag_Q7 =
      (int16_t)((ISACdec_obj->plcstr_obj).stretchLag << 7);


  /* --- Inverse Pitch Filter --- */
  WebRtcIsacfix_PitchFilter(Vector_Word16_1, Vector_Word16_2,
                            &ISACdec_obj->pitchfiltstr_obj, pitchLags_Q7, pitchGains_Q12, 4);

  /* reduce gain to compensate for pitch enhancer */
  /* gain = 1.0f - 0.45f * AvgPitchGain; */
  tmp32a = ISACdec_obj->plcstr_obj.AvgPitchGain_Q12 * 29;  // Q18
  tmp32b = 262144 - tmp32a;  // Q18
  gainQ13 = (int16_t) (tmp32b >> 5); // Q13

  /* perceptual post-filtering (using normalized lattice filter) */
  for (k = 0; k < FRAMESAMPLES_HALF; k++)
    Vector_Word32_1[k] = (Vector_Word16_2[k] * gainQ13) << 3;  // Q25


  WebRtcIsacfix_NormLatticeFilterAr(ORDERLO,
                                    (ISACdec_obj->maskfiltstr_obj).PostStateLoGQ0,
                                    Vector_Word32_1, lofilt_coefQ15, gain_lo_hiQ17, 0, Vector_Word16_1);

  WebRtcIsacfix_NormLatticeFilterAr(ORDERHI,
                                    (ISACdec_obj->maskfiltstr_obj).PostStateHiGQ0,
                                    Vector_Word32_2, hifilt_coefQ15, gain_lo_hiQ17, 1, Vector_Word16_2);

  /* recombine the 2 bands */

  /* Form the polyphase signals, and compensate for DC offset */
  for (k=0;k<FRAMESAMPLES_HALF;k++)
  {
    /* Construct a new upper channel signal*/
    tmp_1 = (int16_t)WebRtcSpl_SatW32ToW16(
                                           ((int32_t)Vector_Word16_1[k]+Vector_Word16_2[k] + 1));
    /* Construct a new lower channel signal*/
    tmp_2 = (int16_t)WebRtcSpl_SatW32ToW16(
                                           ((int32_t)Vector_Word16_1[k]-Vector_Word16_2[k]));
    Vector_Word16_1[k] = tmp_1;
    Vector_Word16_2[k] = tmp_2;
  }


  WebRtcIsacfix_FilterAndCombine1(Vector_Word16_1,
                                  Vector_Word16_2, signal_out16, &ISACdec_obj->postfiltbankstr_obj);

  (ISACdec_obj->plcstr_obj).used = PLC_WAS_USED;
  *current_framesamples = 480;
}
